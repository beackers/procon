# procon

Android IME designed to be used with Bluetooth Nintendo Switch controllers.

## Bootstrap

This repository currently contains a minimal Android input method editor (IME)
that can be installed and enabled as **Procon Controller Overlay**. The input
view remains non-visual while controller input drives a radial character selector:
press a mapped group button to keep that letter, number, or punctuation group
selected, tilt the left stick toward the desired sector, and release the stick to
commit the highlighted character.

Current controller mappings:

- **A/B/X/Y** select sticky lowercase letter groups (`a-f`, `g-m`, `n-s`, `t-z`).
- **R** acts as Shift while held, uppercasing the selected letter group.
- **+** selects common punctuation.
- **-** selects numbers.
- **L** deletes the previous character.
- **ZL/ZR** move the cursor left and right.
- **Home** sends Enter.

## Build

Install the Android SDK with API 36, then build the debug APK with Gradle:

```sh
gradle :app:assembleDebug
```

The generated debug APK will be written to `app/build/outputs/apk/debug/`.
