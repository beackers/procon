# procon

Android IME designed to be used with Bluetooth Nintendo Switch controllers.

## Bootstrap

This repository currently contains a minimal Android input method editor (IME)
that can be installed and enabled as **Procon Controller Overlay**. The input
view remains non-visual while controller input drives a radial letter selector:
hold a mapped face button, tilt the left stick toward the desired letter sector,
and release the stick to commit the highlighted lowercase letter.

## Build

Install the Android SDK with API 36, then build the debug APK with Gradle:

```sh
gradle :app:assembleDebug
```

The generated debug APK will be written to `app/build/outputs/apk/debug/`.
