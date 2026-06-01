# procon

Android IME designed to be used with Bluetooth Nintendo Switch controllers.

## Bootstrap

This repository currently contains a minimal Android input method editor (IME)
that can be installed and enabled as **Procon Controller Overlay**. The input
view is a compact Kotlin-based overlay above the navigation bar that shows which
controller button is currently being pressed.

## Build

Install the Android SDK with API 36, then build the debug APK with Gradle:

```sh
gradle :app:assembleDebug
```

The generated debug APK will be written to `app/build/outputs/apk/debug/`.
