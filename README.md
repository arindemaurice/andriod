# MAURICE AI for Android

**A lightweight Android-native personal assistant by Maurice Arinde.**

MAURICE AI is designed for Android 9 (API 28) and newer, including the Samsung Galaxy A04 (Android 12, ARM64, 3 GB RAM, 32 GB storage). It is a small native Kotlin app, not a WebView wrapper and not a desktop/server client.

## What works in v1.0.0

- Native Android chat interface.
- Gemini API integration using `gemini-3.8-flash`.
- API key stored encrypted with Android Keystore; no key is hardcoded in the source or APK.
- On-device SQLite conversation history and saved memory notes.
- Offline commands: `help`, `remember [text]`, `note [text]`, `show memory`, `time`, `date`, and `clear history`.
- Saved notes are supplied as context to Gemini when online.
- Android speech-recognition handoff when a compatible speech service is installed.
- No desktop server, Python runtime, Node.js, ads, or paid third-party SDK is required.

## Honest limitations

- Gemini-generated answers require internet access and a Gemini API key. Google's free tier has quotas and can change; it is not unlimited. See [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing).
- Offline mode provides local memory, saved chat history, date/time and the listed commands. It is not a fully offline generative AI model.
- Voice recognition depends on the speech-recognition service available on the phone and may itself need internet.
- This initial version does not implement wake-word listening, background assistant service, SMS sending, WhatsApp control, reading notifications, flashlight control, or arbitrary phone automation.
- The API key is encrypted at rest on the phone. A key embedded in an APK would be extractable, so the app deliberately asks each user to enter their own key.

## Build the APK

The recommended method is the included GitHub Actions workflow. Push this repository to `arindemaurice/andriod`, then run **Actions → Build MAURICE AI Android APK → Run workflow**. Download the `maurice-ai-debug-apk` artifact and install `app-debug.apk`.

The workflow builds the APK with Java 17, Gradle 8.9 and Android SDK 35. No Node.js, Python, Android Studio or desktop backend is involved.

## Install using Termux

See [INSTALLATION_GUIDE.md](INSTALLATION_GUIDE.md) for the complete commands to clone the GitHub repository, trigger the build, download the APK artifact and open Android's package installer from Termux.

## Local build (computer with Android SDK)

Install Java 17, Android SDK platform 35, build-tools 35.0.0 and Gradle 8.9. From the repository root:

```bash
gradle :android:app:assembleDebug
```

APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Gemini setup

1. Create a Gemini API key in [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Install and open MAURICE AI.
3. Open **Settings**, paste the key and save.
4. When internet is available, chat normally. Without internet, use the offline commands.

Never commit your API key, screenshots containing it, or a signing key to GitHub. Free-tier availability, model access and quotas depend on Google's current account and regional policies.

## Project structure

```text
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/mauricearinde/mauriceai/MainActivity.kt
android/app/src/main/res/drawable/ic_launcher.xml
android/app/src/main/res/values/styles.xml
.github/workflows/android.yml
build.gradle
settings.gradle
gradle.properties
INSTALLATION_GUIDE.md
```

## Licence and attribution

This Android-only implementation is maintained by Maurice Arinde. The uploaded source package included OpenJarvis upstream materials; the applicable Apache 2.0 licence, NOTICE and upstream attribution record are retained in this distribution. The MAURICE AI Android implementation in this repository is a separate lightweight native app and does not bundle the upstream desktop framework.
