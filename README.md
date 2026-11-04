# Deto Phone 📞

An iPhone-style dialer for Android with a **crystal-glass** interface. The UI is a
single polished web page (`app/src/main/assets/index.html`) hosted in a full-screen
WebView, and it places **real cellular calls** through Android telephony.

## Features

- Keypad, Favourites, Recents, Contacts and Voicemail tabs (iPhone layout)
- Liquid "crystal glass" surfaces — translucent, refractive, with bright rim light
- DTMF dial tones, long-press `0` for `+`, backspace / clear
- **Real calling:** the green call button and any contact row place an actual call
  via `Intent.ACTION_CALL` (needs the `CALL_PHONE` permission, requested on first use)
- Falls back to the system dialer (`ACTION_DIAL`) if permission is denied

> This is a *companion* dialer (it places calls). It is **not** a full default-dialer
> replacement — it does not take over the system incoming-call screen. Android shows
> its own in-call UI once a call connects.

## Get the APK (no Android Studio needed)

This repo builds the APK automatically in GitHub Actions:

1. Push to `main` (or run the **Build APK** workflow from the Actions tab).
2. When it finishes, download `app-debug.apk` from either the workflow
   **Artifacts** or the auto-created **`latest` Release**.
3. On your Android phone, open the APK, allow *install from this source*, install.
4. Open **Deto Phone**, dial a number, tap the green button, and **Allow** the
   Phone permission the first time.

## Build locally

Requires Android Studio (or the Android SDK + JDK 17):

```bash
# from this folder
gradle assembleDebug          # or: ./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Then install over USB: `adb install app/build/outputs/apk/debug/app-debug.apk`

## Preview the UI in a browser

Open `app/src/main/assets/index.html` directly in Chrome/Safari. Without the Android
bridge it runs in demo mode: tapping call shows a mock in-call screen instead of
dialing. Use Chrome/Edge/Safari so `backdrop-filter` renders the glass effect.

## Project layout

```
app/src/main/assets/index.html   → the crystal-glass dialer UI (HTML/CSS/JS)
app/src/main/java/.../MainActivity.kt → WebView host + telephony bridge
app/src/main/AndroidManifest.xml  → CALL_PHONE permission, launcher activity
.github/workflows/android.yml     → cloud APK build
```

## Tech

Kotlin · Android WebView · AGP 8.5 · Gradle 8.9 · minSdk 26 / targetSdk 34
