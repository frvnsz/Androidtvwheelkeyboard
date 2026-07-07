# Context: TV Wheel Keyboard — A System-Wide Android TV Keyboard (IME)

## 0. The Vision

Every Android TV device ships with the same broken input experience: a grid keyboard you steer with a D-pad, one letter at a time, with a separate mode-switch to reach numbers, and no forgiving way to fix a typo. It's slow, and it's the single worst moment of using a TV app that requires typing — searching for a show, logging into a streaming service, entering a Wi-Fi password.

The Wheel Keyboard fixes this by replacing multi-directional grid navigation with a single continuous loop. Every character and action — A–Z, 0–9, Space, Delete, Search, Voice Search, Done, Clear — sits on one circular "wheel." The D-pad's Up/Down rotates through that loop; Left/Right independently moves the text cursor; OK activates whatever the wheel is currently sitting on. Two clean, separate axes of control instead of one tangled grid.

The goal is not to ship this inside one streaming app. The goal is to build it as a **system-wide keyboard app** — an Android Input Method Editor (IME) — that, once installed and enabled, becomes available to *every* app on the TV: search boxes, login screens, Wi-Fi setup, browsers, anything with a text field. That's the actual product: a keyboard, not a feature.

It's built in Kotlin, targets Android TV / Google TV hardware, and is built entirely through GitHub Actions — no local Android SDK or Android Studio installation required. Every push produces a downloadable APK; tagging a release produces a signed, stable one.

---

## 1. Scope: What "Any Smart TV" Actually Means Here

This is worth being precise about upfront, because it shapes every decision below.

This app targets **Android TV / Google TV devices**: Chromecast with Google TV, the Google TV Streamer, NVIDIA Shield, and the many Sony/TCL/Hisense/Philips/etc. TVs and boxes that run Android TV OS, plus — with minor differences in the sideloading UI — Amazon Fire TV, since Fire OS is an Android fork that keeps the same Input Method framework underneath.

It will **not** run on Samsung TVs (Tizen OS) or LG TVs (webOS). Those platforms are not Android, cannot install APKs, and have no concept of an Android IME at all. There's no code change that bridges that gap — it's a different operating system with a completely different app model. "Any Smart TV" in practice means "any Android-TV-based device," which is still the majority of non-Samsung/LG smart TV and streaming-box hardware on the market — just not literally every TV with a screen.

---

## 2. The Interaction Model

This is the entire product surface. Everything else in this document exists to make this table true on real hardware.

| Remote button | Action |
|---|---|
| Up | Rotate the wheel one position backward |
| Down | Rotate the wheel one position forward |
| Left | Move the text cursor left in the field being edited |
| Right | Move the text cursor right in the field being edited |
| OK / D-pad Center | Activate whatever the wheel is currently on, at the current cursor position |

**Wheel order (fixed, not user-configurable, not frequency-adaptive):**

```
A → B → ... → Z → 0 → 1 → ... → 9 → Space → Delete → Search → Voice Search → Done → Clear Text → (wraps back to A)
```

This ordering is deliberate: alphabetical, then numeric, then actions, with zero hidden logic. It trades a small amount of raw typing speed for a genuinely zero-learning-curve mental model — the user never has to guess where a letter "usually" is, because it's always in the one place it belongs, every time. Any speed gains should come from *how fast you move along this fixed order* (see §7), never from reshuffling the order itself. That constraint isn't up for revisiting mid-build — it's the one opinionated design choice everything else is built around.

### 2.1 Why the wheel beats a grid on a D-pad specifically

A standard on-screen keyboard is a 2D grid: the D-pad has to steer in two axes just to reach a letter, and a common typo-correction move (jump to a completely different part of the alphabet, fix something, jump back) means repeated multi-directional traversal. Collapsing the character set into a single loop turns "reach any letter" into "hold one direction for a while" — a much more forgiving, much less error-prone motion for a remote control, especially once acceleration (§7) is layered in.

---

## 3. Foundation: How Android Input Methods Actually Work

This is the mechanism that makes the whole vision possible, so it's worth being exact about it rather than hand-wavy.

An Android IME is not a special app category with elevated permissions or a submission process — it's an ordinary APK containing one specific kind of Android `Service`. The moment that service is correctly declared, Android's own system Settings will list the app as an installable, selectable keyboard, exactly the same way it lists Gboard, SwiftKey, or the TV's own stock keyboard. There's nothing to register with Google, nothing proprietary to unlock — just four pieces, done correctly:

1. **A class extending `InputMethodService`.** This is the actual keyboard engine — it owns the IME lifecycle (`onCreate`, `onStartInput`, `onStartInputView`, `onCreateInputView`, `onFinishInput`), and it's where hardware key events and text commits happen.
2. **A manifest `<service>` declaration**, permissioned so only the system can bind to it, with an intent filter identifying it as an input method, and metadata pointing at a subtype-definition resource.
3. **A `res/xml/method.xml`** declaring at least one `<subtype>` — a single default subtype is enough to be listed and usable.
4. **A one-time manual enable step by the user**, through system Settings. This is a hard OS security boundary that exists on every Android device for every keyboard app ever made — no app is allowed to silently make itself the system default input method. What the app *can* do is make that one-time step a two-click flow instead of a menu hunt (§9).

This exact shape — a plain `InputMethodService`, manifest-declared, no exotic tricks — is precisely how Android TV's own built-in keyboard is built. Google's own AOSP source tree for it is literally named `LeanbackIME`, and its commit history describes it as a D-pad-aware input method purpose-built for Android TV hardware. That's a genuinely useful thing to know two ways: it confirms this is the right and only architecture for the goal, and it's a real, license-open reference implementation to read if any specific plumbing question comes up (`android.googlesource.com/platform/packages/inputmethods/LeanbackIME`).

### 3.1 Reference manifest shape

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" /> <!-- needed only for Voice Search -->

<application
    android:label="@string/app_name"
    android:banner="@drawable/tv_banner"
    android:icon="@mipmap/ic_launcher">

    <!-- Launcher entry: gives the app a visible icon on the TV home screen
         and hosts the one-time "enable this keyboard" onboarding flow -->
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
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

Two manifest details worth calling out explicitly rather than discovering by trial and error: `android:exported="true"` is mandatory on both the activity and the service above from Android 12 (API 31) onward — any component declaring an `<intent-filter>` must state `exported` explicitly, or the manifest merge fails at build time. And the touchscreen `uses-feature` line matters because Android otherwise infers a *required* touchscreen feature by default; without explicitly marking it `false`, some TV devices' package manager will treat the app as hardware-incompatible and refuse to install it, even though nothing else about sideloading is restricted.

### 3.2 Reference `res/xml/method.xml`

```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
    <subtype
        android:label="@string/subtype_label"
        android:imeSubtypeLocale="en_US"
        android:imeSubtypeMode="keyboard"
        android:isAsciiCapable="true" />
</input-method>
```

One subtype is sufficient for a first working version — no need to model multiple locales yet.

---

## 4. Stack and Architecture Choices

- **Language:** Kotlin, end to end.
- **Keyboard surface rendering:** a custom `View` with Canvas drawing (`onDraw`), returned from `onCreateInputView()`. Not Jetpack Compose, not a Leanback `SearchSupportFragment` — those solve for building screens *inside* an app, which isn't the problem here. Compose *can* be hosted inside an `InputMethodService` via `AbstractComposeView`, but it drags in `LifecycleOwner`/`SavedStateRegistryOwner` wiring for no real payoff; a Canvas view is simpler and matches how essentially every real-world Android keyboard, including Android TV's own stock one, is actually built.
- **Text I/O:** exclusively through `InputConnection` — see §5. No Retrofit, no networking of any kind, no persistence layer. This is a genuinely small, self-contained app.
- **Voice input:** `android.speech.SpeechRecognizer` — see §8.
- **Build system:** Gradle with Kotlin DSL, a single `app` module.
- **No native/JNI code anywhere** — which quietly removes an entire category of build concerns. There's no CPU-architecture-specific (`arm64-v8a` vs `armeabi-v7a`) variant to worry about; a single universal APK runs on any Android TV chipset without special ABI splitting.
- **CI/CD:** GitHub Actions builds every APK — see §10. There is no dependency on a local Android SDK or Android Studio at any point in this project's lifecycle.

---

## 5. Text I/O: The Wheel Doesn't Own the Text

This is the most important architectural fact in the whole project, and it's worth internalizing before writing any code: **a system keyboard never owns the text buffer it's editing.** The actual characters, cursor position, and selection always live in whatever text field currently has focus in the foreground app — could be a TV app's search box today, the TV's own Wi-Fi password field tomorrow. The keyboard's job is to read and mutate that remote state through `InputConnection`, obtained via `InputMethodService.getCurrentInputConnection()`.

This turns out to make several things simpler than they'd otherwise be:

- **Knowing the current cursor position:** call `InputConnection.getExtractedText(ExtractedTextRequest(), 0)`. The returned `ExtractedText` carries `selectionStart`/`selectionEnd` directly — no manual character counting needed.
- **Moving the cursor (Left/Right):** `InputConnection.setSelection(newPos, newPos)`.
- **Activating a wheel item at the cursor (OK press):** `InputConnection.commitText(charSequence, 1)` inserts the character at the current cursor position and advances the cursor past it.
- **Delete:** `InputConnection.deleteSurroundingText(1, 0)` removes one character immediately before the cursor.
- **Clear Text:** `InputConnection.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)` — a standard idiom for wiping an entire field without first needing to know its exact length.
- **Search / Done / Next:** don't hardcode per-app behavior — every well-behaved Android text field declares what its own "action button" should do via `EditorInfo.imeOptions`, delivered into `onStartInputView(info, restarting)`. The actual action code lives in a masked-out portion of that field, **not** in `EditorInfo.actionId` (that field is only populated when a text field sets a custom action label, which almost none do) — extract it with `val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION`, then have the wheel's Search/Done items call `InputConnection.performEditorAction(action)`. Reading `actionId` directly instead of masking `imeOptions` is a real, silent bug: Search/Done would appear to simply do nothing on the overwhelming majority of real-world fields. This masking approach is the exact mechanism Gboard's own checkmark/magnifying-glass key uses, and it's what makes Search and Done behave correctly across arbitrary apps instead of needing app-specific wiring.
- **Cursor-to-wheel sync** (a genuinely high-value addition, not just a nice-to-have): after every Left/Right cursor move, read the single character adjacent to the new position with `getTextBeforeCursor(1, 0)`, reverse-look-up that character in the fixed wheel order, and snap the wheel's current index to match. This turns "fix one wrong letter" into "move the cursor there, the wheel is already sitting on roughly the right spot" instead of a full manual re-spin — and because the wheel never owned the buffer in the first place, this is just a cheap read-only query rather than a state-synchronization problem.

### 5.1 Insert vs. overwrite — a decision to make explicitly, not by accident

Does activating a wheel item at a cursor sitting *inside* existing text insert a new character (shifting everything after it to the right), or overwrite whatever character is currently under the cursor? Both are legitimate typing models, but they feel very different, and it should be a deliberate choice baked into the OK handler rather than whatever `InputConnection` call happened to be reached for first. A reasonable default: standard *insert* behavior everywhere, since that matches every other text field a user has ever touched — but if the cursor-to-wheel sync feature (above) is implemented, an *overwrite* mode becomes genuinely attractive specifically for a "spin to the right letter, stomp the typo" correction workflow. Worth prototyping both before committing.

---

## 6. Capturing D-Pad Input Correctly

This is the one piece of Android plumbing that's easy to get subtly wrong. `InputMethodService` itself implements `KeyEvent.Callback` and exposes `onKeyDown(keyCode, event)` / `onKeyUp(keyCode, event)` at the **service** level — override these directly on the service class, not just on the custom `View`. This is the same hook point Android TV's own stock keyboard uses to be D-pad-aware in the first place. Returning `true` consumes the event so the app or launcher behind the keyboard never sees it; returning `false` lets it fall through to ordinary navigation.

The overrides should unconditionally consume `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_DOWN`, `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT`, and `KEYCODE_DPAD_CENTER`/`KEYCODE_ENTER` while the keyboard's input view is on screen, and let everything else pass through untouched (most importantly `KEYCODE_BACK`, whose default handling in `InputMethodService` already hides the keyboard — worth keeping rather than fighting).

Practically, this also means the wheel view should be the **only** focusable element in the keyboard's window — there should be no second focusable button or control competing with it, both because the design doesn't call for one and because it would create a conflict between Android's normal focus-traversal system and this manual key handling.

One more override worth setting explicitly rather than leaving at its default: **`onEvaluateFullscreenMode()` should return `false` unconditionally.** `InputMethodService`'s default fullscreen behavior — the mode a stock software keyboard switches into on a phone in landscape — takes over the entire screen and starts consuming D-pad movement itself, to drive the cursor inside its own built-in extracted-text view. That's a direct, silent conflict with the manual D-pad handling described above: instead of a clean crash, the symptom would be "cursor movement works, except sometimes it doesn't," depending on fullscreen state. Forcing fullscreen mode off removes that failure mode entirely, and also keeps the keyboard rendered as a small bar rather than a full-screen takeover — the correct visual for this design regardless.

---

## 7. Making a Fixed Wheel Feel Fast

The wheel order stays fixed — this section is entirely about how quickly you can move along that fixed order, never about changing it.

### 7.1 Hold-to-accelerate rotation (the highest-value piece)

Instead of one Up/Down press moving one position forever, ramp speed the longer the button stays held:

- **0.0–0.4s held:** normal speed, roughly 2–3 positions/second
- **0.4–1.2s held:** medium speed, roughly 6–8 positions/second
- **1.2s+ held:** fast/"spin" speed, roughly 15–20 positions/second

This is a repeating timer tied to the key-down event, with the delay between ticks shrinking the longer the key stays down — the same acceleration pattern used by scroll wheels and long-list navigation elsewhere on TV UIs. Releasing the key resets the ramp for next time. This single change is probably the majority of the perceived speed improvement, and it's invisible in the sense that the user doesn't need to know it exists — holding the button just naturally feels rewarding instead of tedious.

### 7.2 Overshoot correction

At high spin speed, flying past the target is the main risk. Pressing the *opposite* direction while already spinning fast should immediately reverse/decelerate rather than requiring a full release-and-restart in the new direction — removes the "now I have to spin all the way back around" frustration. It's also worth capping top speed just below the point where letters become unreadable mid-spin; acceleration that outruns legibility works against the user instead of for them.

### 7.3 Cursor-to-wheel sync

Already described in §5 as a text-I/O mechanism — it's also, separately, a speed feature, arguably the single highest-value addition for the *editing/correction* use case specifically, as distinct from raw first-pass typing speed.

### 7.4 Number-key fast-travel (optional, v2)

If the remote has actual numeral buttons, pressing one could jump the wheel to roughly that fraction of the alphabet (5 → near "M", 9 → near the end) as a fast-travel shortcut into letter territory — not inserting the digit itself. This adds a small learning curve, so it should be flagged clearly in code as an optional v2 feature rather than treated as core.

### 7.5 Haptic/audio feedback

A subtle tick per position at normal speed, and a softer continuous texture (or no per-position tick at all) above the fast-spin threshold, so acceleration feels satisfying rather than like a rattling mess. Pure polish — sequence it after the acceleration logic itself actually works.

**Priority order if building incrementally:** hold-to-accelerate (7.1) → cursor-to-wheel sync (7.3) → overshoot/reverse-cancels-ramp (7.2) → haptic/audio feedback (7.5) → number fast-travel (7.4, genuinely optional).

---

## 8. Voice Search

Two viable approaches; the first is recommended.

**A. `SpeechRecognizer` driven directly from the service.** `android.speech.SpeechRecognizer.createSpeechRecognizer(context)` simply binds to a system recognition service — it doesn't require an `Activity` to operate, so it can be driven straight from the IME service with a `RecognitionListener`, committing recognized text via `InputConnection.commitText(...)` on `onResults()`, exactly like any typed character.

**The one catch:** it requires `RECORD_AUDIO` permission, and runtime permission prompts cannot be shown from a `Service` — only from an `Activity`. So a one-time setup path is still needed: the launcher `SetupActivity` (§9) requests `RECORD_AUDIO` once via the standard runtime-permission flow. Once granted, the service can use `SpeechRecognizer` freely on every later Voice Search activation without any further `Activity` involvement.

**B. Defer to `RecognizerIntent`** and whatever voice engine is already configured on the device. More moving parts — starting an activity from a `Service` needs `FLAG_ACTIVITY_NEW_TASK` and behaves awkwardly layered on top of another window that's itself an IME. Worth it only if reusing an already-configured voice assistant matters more than owning the recognition flow directly; treat as a v2 fallback rather than the default path.

---

## 9. Onboarding: Making "Enable This Keyboard" a Two-Click Flow

Since the OS will never let an app silently become the default input method, the `SetupActivity` declared as the TV launcher entry (§3.1) should do exactly two things:

1. A button that fires `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)` — jumps straight to the system's "manage keyboards" screen, where the user flips this keyboard's toggle on. A stable, documented `Settings` action, not a private API.
2. A button that fires `(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()` — pops the system's keyboard-switcher picker so the user can select the wheel keyboard as active, once it's enabled.

This is the identical two-step flow every third-party keyboard app walks users through, just condensed into a purpose-built onboarding screen instead of leaving the user to hunt through Settings unassisted.

---

## 10. File Structure

```
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/.../wheelkeyboard/
      service/
        WheelKeyboardService.kt     // InputMethodService: lifecycle, onKeyDown/onKeyUp, InputConnection calls
      view/
        WheelKeyboardView.kt        // Custom Canvas View: renders the circular ring, owns wheelIndex + acceleration state
      model/
        WheelKeyboardItem.kt        // sealed class: Letter, Digit, Space, Delete, Search, VoiceSearch, Done, ClearText
        WheelRotationState.kt       // speed-tier timing state only — the text itself always lives in the host app's field
      voice/
        VoiceInputHelper.kt         // wraps SpeechRecognizer + RecognitionListener
      settings/
        SetupActivity.kt            // TV launcher entry: onboarding + jumps to system IME settings/picker
    res/
      xml/method.xml
      layout/activity_setup.xml
      drawable/tv_banner.xml        // (or .png — 320x180, required for a TV launcher banner)
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

Keep it to this small, focused set of files rather than one giant service class — the keyboard-rendering concerns (`WheelKeyboardView`), the Android lifecycle/plumbing concerns (`WheelKeyboardService`), and the onboarding concerns (`SetupActivity`) are genuinely separate responsibilities.

---

## 11. Building Without a Local Android SDK: GitHub Actions

This is the part that makes the whole project buildable from anywhere with no Android Studio installation at all. Two workflows, two different purposes.

### 11.1 Debug build on every push — no secrets required, use this first

Android's build tooling auto-signs debug builds with a throwaway debug keystore that Gradle generates on the fly — meaning an installable APK requires zero keystore/secrets setup. This is the fastest path to something sideloadable today.

**Deliberately no committed Gradle wrapper.** A typical Android project ships a `gradlew`/`gradlew.bat` pair plus a binary `gradle/wrapper/gradle-wrapper.jar`. That jar is compiled bytes, not text — something written by hand or by a text-editing tool can only ever produce a corrupted copy of it, and a broken wrapper jar fails immediately with something like "Error: Could not find or load main class," before any real build step even runs. The fix is to skip committing a wrapper entirely and let CI provide Gradle directly via `gradle/actions/setup-gradle`, which installs the exact requested Gradle version onto the runner's `PATH`. Every step below calls `gradle` directly instead of `./gradlew`.

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

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'

      - name: Build debug APK
        run: gradle assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: wheel-keyboard-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` produces a downloadable artifact under that workflow run's "Artifacts" section — download it, get it onto the TV (§12), done.

### 11.2 Signed release build on a version tag — for a stable, shareable APK

A signed release keystore is worth setting up once past initial iteration, mainly because Android treats the signing key as an app's permanent identity — reinstalling over an existing install requires a matching signature, and a consistent release key avoids "app not installed" conflicts later.

Generating the keystore doesn't require Android Studio — `keytool` ships with any JDK, including the one GitHub Actions installs. Run this once, wherever a JDK is available (locally, or as a one-off `workflow_dispatch` job whose only output is the file):

```bash
keytool -genkeypair -v -keystore release.jks -alias wheelkeyboard \
  -keyalg RSA -keysize 2048 -validity 10000
```

Base64-encode it and store the result as a repository secret:

```bash
base64 -w0 release.jks > release.jks.base64.txt
# paste the contents of that file into a GitHub secret named SIGNING_KEY_BASE64
```

Add matching secrets under the repo's Settings → Secrets and variables → Actions: `SIGNING_KEY_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`.

In `app/build.gradle.kts`, read them from environment variables rather than hardcoding anything:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEY_STORE_PATH") ?: "release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}
```

```yaml
# .github/workflows/release.yml
name: Signed Release Build

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'

      - name: Decode keystore
        env:
          SIGNING_KEY_BASE64: ${{ secrets.SIGNING_KEY_BASE64 }}
        run: echo "$SIGNING_KEY_BASE64" | base64 -d > release.jks

      - name: Build signed release APK
        env:
          SIGNING_KEY_STORE_PATH: release.jks
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
        run: gradle assembleRelease

      - name: Create GitHub Release with APK attached
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/app-release.apk
```

Pushing a tag like `v1.0.0` builds a signed APK and attaches it directly to a GitHub Release page — a stable public download link, openable straight from the TV's browser or pasted by URL into a sideloading app, with none of the login/expiry quirks that plain workflow-run artifacts have (those expire after a retention window; Release attachments don't).

**Version notes worth locking in:** `actions/upload-artifact@v3` is deprecated and will fail outright — use `v4`. `distribution: temurin` with Java 17 matches what the pinned Android Gradle Plugin version below expects. One filename gotcha worth knowing in advance: `assembleRelease` only produces `app-release.apk` when a `signingConfig` is actually attached to the `release` build type, exactly as wired above. If the signing secrets are ever missing or that wiring gets dropped, Gradle silently produces `app-release-unsigned.apk` instead — a different filename — and the release-upload step will fail on a file-not-found error rather than an obviously-signing-related one. If that happens, check the build-type wiring before suspecting the secrets themselves.

### 11.3 Pinned Toolchain Versions and Complete Gradle Files

Everything below is one mutually-compatible, known-good set of versions and complete file contents, written out in full rather than described — guessing a slightly-wrong version number or plugin syntax from memory is exactly the kind of small mistake that silently breaks a first build with no way to interactively debug it.

| Component | Version |
|---|---|
| Android Gradle Plugin (AGP) | 8.5.0 |
| Gradle | 8.7 |
| Kotlin | 1.9.24 |
| JDK | 17 (Temurin) |
| `compileSdk` / `targetSdk` | 34 |
| `minSdk` | 23 |

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WheelKeyboard"
include(":app")
```

Root `build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.wheelkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wheelkeyboard"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

Two details baked into these files deliberately, worth not "fixing" back to older habits: `namespace` inside `android {}` (not a manifest `package` attribute) is mandatory as of AGP 8 — omitting it fails the build outright with a namespace-not-specified error, not a warning. And `sourceCompatibility` / `targetCompatibility` / `jvmTarget` are all pinned to the same value (17) on purpose — a mismatch between the Java and Kotlin compiler targets is one of the most common "works in one config, not another" Android build errors, surfacing as an inconsistent-JVM-target-compatibility failure.

---

## 12. Getting the APK Onto the TV

1. **Enable Developer Options** on the TV: Settings → About/Device Preferences → click the build-number-equivalent field about 7 times until "You are now a developer" appears.
2. **Easiest path:** install "Downloader by AFTVnews" from the Play Store on the TV, allow it under Settings → Apps → Security & Restrictions (or Settings → Privacy → Security → Unknown apps, depending on the OS version) to install unknown apps, then paste the GitHub Release APK URL straight into Downloader.
3. **Alternative:** with the TV's Wireless/USB Debugging enabled, `adb connect <tv-ip>:5555` from any PC, then `adb install app-release.apk`.
4. Sideloaded apps don't always show an icon on the TV's home row unless they declare `LEANBACK_LAUNCHER` (already included in the manifest in §3.1) — that's exactly why keeping `SetupActivity` as a real, visible launcher entry matters: it doubles as the app's home-screen icon and its onboarding screen in one.

---

## 13. Open Decisions to Resolve Before Coding

- **Insert vs. overwrite at the cursor on OK** (§5.1) — pick one deliberately, don't let it fall out by accident.
- **Ring rendering:** the keyboard shape is a circular ring, not a horizontal or vertical strip. Keep the ring-based layout as a fixed requirement throughout implementation.
- **Whether the keyboard renders its own text/cursor preview at all.** The host app's own field already shows the live text and usually its own cursor caret — the wheel surface may only need to render the ring itself, not a duplicate text display. Decide based on visual-polish priorities, not because either option is technically required.
- **Back button behavior:** the default `InputMethodService` handling already hides the keyboard on Back — decide whether to keep that as-is, or add a wheel-specific first step (e.g., Back cancels an in-progress voice search before falling through to the default hide behavior).
- **Rotation acceleration tiers, overshoot handling, haptic/audio feedback, and number-key fast-travel** all need concrete timing/threshold values chosen through actual hands-on testing with a real remote rather than guessed in the abstract — the ranges in §7 are a reasonable starting point, not final numbers.

---

## 14. Known Failure Modes (Read Before Debugging)

A quick-reference table for the specific, non-obvious ways this exact project tends to fail, each tied back to the section that explains the fix in full:

| Symptom | Cause | Fix |
|---|---|---|
| Build fails immediately, before any real compile step, with a class-not-found-style error | Hand-authored or corrupted `gradle-wrapper.jar` (a binary file that can only be written correctly as bytes, not text) | Don't commit a wrapper at all — use `gradle/actions/setup-gradle` in CI (§11.1, §11.3) |
| "Namespace not specified" build error | Missing `namespace` under `android {}` in `app/build.gradle.kts` | Add it — mandatory as of AGP 8, not optional (§11.3) |
| Manifest merger failure mentioning `exported` | A `<service>` or `<activity>` declares an `<intent-filter>` without an explicit `android:exported` | Set it explicitly on both components (§3.1) — mandatory since API 31 |
| App refuses to install on some TV hardware, or is flagged incompatible | Android's implicit default `uses-feature` requirement for a touchscreen | Add `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />` (§3.1) |
| Cursor movement behaves inconsistently, or seems to fight the wheel's own Left/Right handling | `InputMethodService` fullscreen mode intercepting D-pad to drive its own built-in extracted-text cursor | Override `onEvaluateFullscreenMode()` to always return `false` (§6) |
| Search/Done wheel items appear to do nothing on most real apps | Reading `EditorInfo.actionId` directly instead of masking `imeOptions` | Use `editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION` (§5) |
| Random `NullPointerException` when a wheel item is activated | `getCurrentInputConnection()` returned `null` (happens between input sessions) and was used without a check | Null-check before every `InputConnection` call |
| Release workflow fails on a file-not-found error for the APK path | `assembleRelease` fell back to producing `app-release-unsigned.apk` because the signing config didn't actually apply | Confirm all four `SIGNING_*` secrets are set and `signingConfig` is attached to the `release` build type (§11.2, §11.3) |
| Inconsistent-JVM-target-compatibility build error | Java and Kotlin compiler targets set to different versions | Pin both `compileOptions` and `kotlinOptions.jvmTarget` to the same JDK version — 17 throughout this project (§11.3) |
| Upload-artifact step fails outright | Using the deprecated `actions/upload-artifact@v3` | Use `v4` (§11.1, §11.2) |

---

## 15. Non-Goals

- No frequency-based or predictive reordering of the wheel — this would undercut the entire zero-learning-curve premise the design is built on.
- No secondary focusable UI element competing with the wheel for D-pad focus. The wheel is the only focusable component during text entry, full stop.
- No per-app special-casing anywhere in the keyboard's logic. The entire value of building this as a system IME rather than a feature bolted onto one app is that it works identically everywhere a text field exists — any app-specific behavior would quietly undermine that.
- No attempt to support non-Android smart TV platforms (Tizen, webOS). Wrong operating system entirely — out of scope, not a future phase.
