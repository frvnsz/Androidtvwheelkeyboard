# TV Wheel Keyboard

TV Wheel Keyboard is a system-wide Android TV / Google TV input method. It presents letters, digits, and actions on one circular ring controlled by a D-pad remote.

## Build

This repository intentionally does not commit a Gradle wrapper. CI installs Gradle 8.7 and builds with JDK 17.

```bash
gradle assembleDebug
```

## Install and enable

1. Install the debug or release APK on an Android TV / Google TV device.
2. Open **TV Wheel Keyboard** from the launcher.
3. Select **1. Enable keyboard** to open Android input-method settings and enable TV Wheel Keyboard.
4. Return to the app and select **2. Choose keyboard** to open the system input-method picker.
5. Select TV Wheel Keyboard.
6. Select **Grant microphone permission for Voice Search** if you want the wheel's Voice action to commit speech-recognized text.

## Controls

| Remote button | Action |
|---|---|
| Up | Rotate the wheel backward |
| Down | Rotate the wheel forward |
| Left | Move the host text cursor left |
| Right | Move the host text cursor right |
| OK / Enter | Activate the selected wheel item |

Wheel order is fixed: A-Z, 0-9, Space, Delete, Search, Voice, Done, Clear.
