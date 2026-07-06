# Context: TV Wheel Keyboard — Standalone Android TV System Keyboard (IME)

## 0. Why This Replaces the Streamflix-Embedded Version

The original version of this concept lived inside a Streamflix fork and tried to replace that app's own search input screen. That's the wrong layer to build this at — you were fighting Streamflix's internal ViewModel/search-fragment wiring instead of the actual problem, which is "D-pad text entry on Android TV is bad everywhere."

The fix is to stop building a *feature inside one app* and instead build a **system-wide Input Method Editor (IME)** — the same category of software as Gboard, SwiftKey, or Android TV's own stock keyboard. Once installed and enabled, an IME is offered by the OS to **any** app with a text field: Streamflix, YouTube, Netflix, a browser, TV settings, literally anything. This is strictly more ambitious than the original goal and, importantly, it's *architecturally simpler* — you stop needing to reverse-engineer another app's internals and instead build against one stable, documented Android system.

**Important scope correction:** "any Smart TV" needs to be narrowed. This approach works on **Android TV / Google TV devices only** (Chromecast with Google TV, Google TV Streamer, NVIDIA Shield, Sony/TCL/Hisense/etc. Android TV models, and — with minor caveats around sideloading UI — Amazon Fire TV, since Fire OS is an Android fork that keeps the same IME framework). It will **not** run on Samsung Tizen TVs or LG webOS TVs — those are not Android at all, don't run APKs, and have no concept of an Android IME. There is no cross-platform path here; this is an Android-TV-specific keyboard.

---

## 1. What an IME Actually Is on Android (the mechanism that makes "detectable, installable as default keyboard" true)

An Android IME is not a special app category with extra permissions — it's a regular APK that contains one specific kind of `Service`. The system already knows how to discover, list, and let the user pick any correctly-declared IME from Settings. There is no registry to submit to, no approval process, nothing Google-specific to unlock — the moment the manifest is correct, Android's Settings → Keyboard screen will list it like any other installed keyboard.

The four required pieces:

1. **A class extending `InputMethodService`** — this is the actual keyboard "engine." It owns the IME lifecycle (`onCreate`, `onStartInput`, `onStartInputView`, `onCreateInputView`, `onFinishInput`, etc.) and is where hardware key events (D-pad) and text commits happen.
2. **A manifest `<service>` declaration** with:
   - `android:permission="android.permission.BIND_INPUT_METHOD"` (mandatory — without this the system will refuse to bind to it)
   - an `<intent-filter>` matching `android.view.InputMethod`
   - `<meta-data android:name="android.view.im" android:resource="@xml/method" />` pointing at a subtype-definition XML resource
3. **A `res/xml/method.xml`** file declaring the IME's `<subtype>`(s) — even a single default subtype is enough to be listed and selectable.
4. **The user manually enabling it once**, via system Settings. This is a hard OS security boundary, not a limitation of your app — no app on stock Android is allowed to silently make itself the system default keyboard, IME or otherwise. Your app *can*, however, deep-link the user straight to the right settings screen so "enable" is a two-click flow instead of a hunt through menus (see §7).

This exact pattern — a plain `InputMethodService`, manifest-declared, nothing exotic — is precisely how Android TV's **own built-in stock keyboard** is implemented. Google's own Android Open Source Project repository for it is literally named `LeanbackIME`, and its own commit history describes it plainly as a D-pad-aware input method built for Android TV devices. That's useful for you in two ways: (a) it proves this architecture is the correct and only architecture for what you want, and (b) it's a real, buildable, AOSP-licensed reference implementation you can read for patterns if you get stuck (`android.googlesource.com/platform/packages/inputmethods/LeanbackIME`).

### Reference: minimal manifest shape (adapt package/class names)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" /> <!-- only if shipping voice search -->

<application
    android:label="@string/app_name"
    android:banner="@drawable/tv_banner"
    android:icon="@mipmap/ic_launcher">

    <!-- Optional but recommended: a launcher entry so the app is reachable
         from the TV home screen without digging through Settings > Apps -->
    <activity
        android:name=".settings.SetupActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>

    <service
        android:name=".service.WheelKeyboardService"
        android:label="@string/keyboard_label"
        android:permission="android.permission.BIND_INPUT_METHOD"
        android:exported="true">
        <intent-filter>
            <action android:name="android.view.InputMethod" />
        </intent-filter>
        <meta-data
            android:name="android.view.im"
            android:resource="@xml/method" />
    </service>
</application>

<uses-feature android:name="android.software.leanback" android:required="false" />
```

### Reference: `res/xml/method.xml`

```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
    <subtype
        android:label="@string/subtype_label"
        android:imeSubtypeLocale="en_US"
        android:imeSubtypeMode="keyboard"
        android:isAsciiCapable="true" />
</input-method>
```

You don't need multiple subtypes/locales for v1 — one is enough to be listed and to work.

---

## 2. Stack

- **Language:** Kotlin
- **UI for the keyboard surface:** a custom `View` with Canvas drawing (`onDraw`), returned from `onCreateInputView()`. Not Compose, not Leanback's `SearchSupportFragment` — those are for building screens *inside* an app, not for the IME window itself. (Compose can technically be hosted inside an `InputMethodService` via `AbstractComposeView`, but it adds lifecycle/`SavedStateRegistry` plumbing for no real benefit here — a Canvas view is simpler and matches how every serious Android keyboard, including Google's own, actually does it.)
- **Text I/O:** `InputConnection` (see §4) — no Retrofit, no ExoPlayer, no networking at all. This app has zero dependency on Streamflix's stack; it's a much smaller, more self-contained project than the original.
- **Voice input:** `android.speech.SpeechRecognizer` (see §5)
- **Build:** Gradle (Kotlin DSL), single `app` module. No native/JNI code anywhere, which matters later — it means there's no CPU-architecture (`arm64-v8a` vs `armeabi-v7a`) build-variant concern; a single universal APK runs on any Android TV device regardless of chipset.
- **CI:** GitHub Actions (see §8) — this replaces your local Android SDK entirely.

---

## 3. Core Interaction Model (carried over from the original design — still correct, still non-negotiable)

| Remote button | Action |
|---|---|
| Up | Rotate wheel one position backward |
| Down | Rotate wheel one position forward |
| Left | Move text cursor left in the currently focused field |
| Right | Move text cursor right in the currently focused field |
| OK / D-pad Center | Activate current wheel item at the current cursor position |

**Wheel order (fixed):**
```
A → B → ... → Z → 0 → 1 → ... → 9 → Space → Delete → Search → Voice Search → Done → Clear Text → (wraps to A)
```

This part of the original spec doesn't change at all — it was never actually coupled to Streamflix. What changes is *where the text lives* (§4) and *how key events are captured* (§3.1).

### 3.1 Where D-pad events are actually intercepted

This is the one piece of Android plumbing that's easy to get subtly wrong, so it's worth stating precisely: `InputMethodService` itself implements `KeyEvent.Callback` and exposes `onKeyDown(keyCode, event)` / `onKeyUp(keyCode, event)` at the **service** level — override these directly on `WheelKeyboardService`, not just on the custom `View`. This is the same hook point Android TV's own stock keyboard uses to be "D-pad aware." Returning `true` from these consumes the event (the underlying app/launcher never sees it); returning `false` lets it fall through to normal navigation. Your overrides should consume `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_DOWN`, `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT`, and `KEYCODE_DPAD_CENTER`/`KEYCODE_ENTER` unconditionally while the IME's input view is showing, and let everything else pass through untouched.

Practical implication: the wheel view should be the **only** focusable element in the IME's window (this already matches the original "no secondary focusable buttons" non-goal), so there's no separate Android focus-traversal system fighting with your manual key handling.

---

## 4. Text I/O: Why "the wheel doesn't own the text" is actually the better design

In the Streamflix-embedded version, the wheel component held its own `text` state and pushed it into Streamflix's search ViewModel. A system IME **cannot** work that way, because it doesn't know in advance which app's field it's editing — could be Streamflix's search box today, the TV's own Wi-Fi password field tomorrow. The text buffer, cursor, and selection always live in **whatever `EditText`-equivalent view currently has focus in the foreground app**. Your job is to read and mutate that remote state through `InputConnection`, obtained via `InputMethodService.getCurrentInputConnection()`.

This turns out to simplify several things the original open-questions list was uncertain about:

- **Knowing the current cursor position:** call `InputConnection.getExtractedText(ExtractedTextRequest(), 0)` — the returned `ExtractedText` has `selectionStart`/`selectionEnd` directly. No manual character counting needed.
- **Moving the cursor (Left/Right):** `InputConnection.setSelection(newPos, newPos)`.
- **Inserting/replacing at the wheel's activated item (OK press):** `InputConnection.commitText(charSequence, 1)` inserts at the current cursor position and advances the cursor past it. This is *insert*, not *overwrite* — see the open decision in §6 about whether OK should insert or overwrite the character already under the cursor (the original design's "correction" example implies overwrite; that needs to be an explicit, deliberate choice, not an accident of whichever `InputConnection` call felt natural to reach for).
- **Delete:** `InputConnection.deleteSurroundingText(1, 0)` deletes one character before the cursor.
- **Clear Text:** delete a very large count in both directions (`deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)`) — a common idiom for "wipe the whole field" without needing to know its exact length first.
- **Search / Done / Next wheel items:** don't hardcode per-app behavior. Read `EditorInfo.imeOptions`/`actionId` (passed into `onStartInputView(info, restarting)` and stored) and call `InputConnection.performEditorAction(actionId)`. Every well-behaved Android text field already declares what its "action button" should do (search, go, next, done) — this is the same mechanism Gboard's checkmark/magnifying-glass key uses, and it's what makes "Search" and "Done" work correctly across arbitrary host apps instead of just Streamflix.
- **Cursor-to-wheel sync (§5 of the original design, now easier):** after every Left/Right move, read one character adjacent to the new cursor position (`getTextBeforeCursor(1, 0)`), reverse-look-up that character in the fixed wheel order, and snap `wheelIndex` to match. Because the wheel never owned the text buffer, this is a cheap read-only query rather than a state-sync problem.

---

## 5. Voice Search Integration

Two viable approaches; recommend the first:

**A. `SpeechRecognizer` directly from the service (no separate Activity needed for the recognition itself).** `android.speech.SpeechRecognizer.createSpeechRecognizer(context)` just binds to a system recognition service — it doesn't require an `Activity`, so it can be driven straight from `WheelKeyboardService` with a `RecognitionListener`, and on `onResults()` you commit the recognized text via `InputConnection.commitText(...)` exactly like a typed character.

**The catch:** it needs `RECORD_AUDIO` permission, and **runtime permission prompts cannot be shown from a `Service`** — only from an `Activity`. So you still need a one-time setup path: the launcher `SetupActivity` (§7) requests `RECORD_AUDIO` once via `ActivityCompat.requestPermissions`. After the user grants it once, the service can use `SpeechRecognizer` freely on every later "Voice Search" wheel activation without any further Activity involvement.

**B. Defer to the platform's existing voice IME/assistant** via `RecognizerIntent` — more moving parts (needs a transparent trampoline `Activity` since `startActivity` from a `Service` requires `FLAG_ACTIVITY_NEW_TASK` and behaves awkwardly layered on top of another IME's window). Only worth it if you want to reuse whatever voice engine is already configured on the device rather than bundling your own recognition flow. Treat as a v2 fallback, not the default.

---

## 6. Open Decisions to Make Explicit Before Coding (carried over + updated)

- **Insert vs. overwrite semantics for OK/Select:** does activating a wheel item at a cursor that's sitting *inside* existing text insert a new character (shifting everything right) or overwrite the character currently under the cursor? The original MAREZ→MARES correction example implies overwrite is the intended feel for "fixing a typo," but standard insert is what every other text field trains users to expect. Pick one and document it in code — don't let it be decided implicitly by whichever `InputConnection` method got called first.
- **Visual layout of the ring:** literal circular ring vs. windowed strip showing ~7–9 items centered on the current selection. The windowed-strip approach remains the practical answer on a TV screen; this doesn't change from the original design.
- **Whether the IME renders its own text/cursor preview at all.** Since the host app's own field already shows the live text and (usually) its own cursor caret, the wheel surface itself may only need to show the ring — not a duplicate text field. Decide this per visual-polish priorities, not because it's technically required.
- **Rotation acceleration tiers, overshoot/reverse-cancels-ramp, haptic/audio feedback, number-key fast travel** — all identical in spirit to the original spec's §6; none of this logic needs to change now that it's a system IME instead of an embedded component. Same priority order as before: acceleration first, cursor-to-wheel sync second, overshoot handling third, feedback fourth, number fast-travel last (and still explicitly v2/optional).
- **Back button:** `InputMethodService`'s default `onKeyUp` already intercepts `KEYCODE_BACK` to hide the IME when it's showing — decide whether to keep that default (probably yes) or override it for something wheel-specific (e.g., "Back cancels an in-progress voice search" first, then falls through to the default hide behavior).

---

## 7. Making "Enable This Keyboard" a Two-Click Flow

Since the OS won't let any app silently become the default IME, the `SetupActivity` (already declared in the manifest above as the TV launcher entry) should do exactly two things:

1. A button that fires `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)` — jumps straight to the system's "manage keyboards" screen, where the user flips your IME's toggle on. This is a stable, documented `Settings` action, not a private API.
2. A button that fires `((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker()` — pops the system's keyboard-switcher picker so the user can select the wheel keyboard as the active one for the current session, once it's enabled.

This is the same two-step flow every third-party keyboard app on the Play Store walks users through (enable → select), just condensed into your own onboarding screen instead of leaving the user to find Settings on their own.

---

## 8. Suggested File Structure

```
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/.../wheelkeyboard/
      service/
        WheelKeyboardService.kt     // InputMethodService: lifecycle, onKeyDown/onKeyUp overrides, InputConnection calls
      view/
        WheelKeyboardView.kt        // Custom Canvas View: renders the ring/strip, owns wheelIndex + acceleration state
      model/
        WheelKeyboardItem.kt        // sealed class: Letter, Digit, Space, Delete, Search, VoiceSearch, Done, ClearText
        WheelRotationState.kt       // speed-tier timing state only — NOT the text (text lives in the host field)
      voice/
        VoiceInputHelper.kt         // wraps SpeechRecognizer + RecognitionListener
      settings/
        SetupActivity.kt            // TV launcher entry: onboarding + jumps to system IME settings/picker
    res/
      xml/method.xml
      layout/activity_setup.xml
      drawable/tv_banner.xml (or .png, 320x180 required for a TV launcher banner)
      values/strings.xml, colors.xml
.github/
  workflows/
    build-debug.yml
    release.yml
gradle/wrapper/
build.gradle.kts
settings.gradle.kts
gradle.properties
```

---

## 9. GitHub Actions: Building Without a Local Android SDK

This is the part that actually solves your stated blocker. Two workflows, two different jobs:

### 9.1 Debug build on every push — no secrets required (use this first)

Android's build tooling **auto-signs debug builds** with a throwaway debug keystore that Gradle generates on the fly. That means you can get an installable APK with zero keystore/secrets setup at all — this is the fastest path to something you can sideload today.

```yaml
# .github/workflows/build-debug.yml
name: Build Debug APK

on:
  push:
    branches: [ main ]
  workflow_dispatch: {}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: wheel-keyboard-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` produces a downloadable artifact under the workflow run's "Artifacts" section — download it, transfer it to the TV (Downloader app, or `adb install`, see §10), done.

### 9.2 Signed release build on a version tag — for a stable, shareable APK

A signed release keystore is worth setting up once you're past initial iteration, mainly because Android treats a signing key as the app's permanent identity — if you ever reinstall over an existing install, the signature has to match, and a consistent release key avoids "app not installed" conflicts down the line.

Generating the keystore doesn't require Android Studio — `keytool` ships with any JDK (including the one GitHub Actions installs). If you have no JDK locally either, run this exact command once inside a throwaway GitHub Actions job (`workflow_dispatch`, print/upload the resulting file as an artifact, then delete the job) — or install a JDK locally just for this one command:

```bash
keytool -genkeypair -v -keystore release.jks -alias wheelkeyboard \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then base64-encode it and store it as a repo secret:

```bash
base64 -w0 release.jks > release.jks.base64.txt
# paste the contents of that file into
