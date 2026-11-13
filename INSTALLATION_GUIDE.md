# MAURICE AI v1.0.0 - Android installation guide

This guide builds the APK with GitHub Actions and installs it from Termux. Termux is the command-line controller and installer; GitHub's build runner compiles the Android app. This avoids installing the very large Android SDK on a phone with limited storage.

## A. Put the project in your GitHub repository (computer)

1. Extract the `MAURICE-AI-Android` folder.
2. Open your repository: https://github.com/arindemaurice/andriod
3. Upload the **contents** of `MAURICE-AI-Android` into the repository root, not an extra nested `MAURICE-AI-Android/` folder.
4. Ensure `README.md`, `settings.gradle`, `build.gradle`, `android/` and `.github/workflows/android.yml` are visible at the repository root.
5. Commit the files to the `main` branch.

Do not upload a Gemini API key. Do not add `.env` files or private signing keys.

## B. Prepare Termux on the Android phone

Install Termux from its official F-Droid or GitHub distribution. Open Termux and run these commands in order:

```bash
pkg update -y && pkg upgrade -y
pkg install -y git gh jq
termux-setup-storage
```

Sign in to GitHub CLI. It will show an authentication flow:

```bash
gh auth login
```

Choose **GitHub.com**, **HTTPS**, and authenticate in the browser when prompted. Confirm:

```bash
gh auth status
```

## C. Clone your repository

```bash
cd ~
git clone https://github.com/arindemaurice/andriod.git MAURICE-AI
cd ~/MAURICE-AI
```

If the repository is private, the GitHub CLI authentication above is required before cloning. If you already cloned it, use `cd ~/MAURICE-AI && git pull` instead.

Check that the Android source exists:

```bash
ls -la
ls -la android/app/src/main
```

## D. Start the APK build from Termux

```bash
gh workflow run android.yml --repo arindemaurice/andriod --ref main
```

Wait briefly, then list the latest run:

```bash
sleep 10
gh run list --repo arindemaurice/andriod --workflow android.yml --limit 5
```

Get the latest run ID and wait for the build to finish:

```bash
RUN_ID=$(gh run list --repo arindemaurice/andriod --workflow android.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "$RUN_ID"
gh run watch "$RUN_ID" --repo arindemaurice/andriod --exit-status
```

If the workflow fails, open the repository's **Actions** tab, select the failed run and inspect the first failed build step. Do not try to install an APK unless the workflow completed successfully.

## E. Download the APK artifact

```bash
mkdir -p ~/MAURICE-AI/apk
gh run download "$RUN_ID" --repo arindemaurice/andriod --name maurice-ai-debug-apk --dir ~/MAURICE-AI/apk
ls -lh ~/MAURICE-AI/apk/app-debug.apk
```

The last command should show `app-debug.apk`. If it says the file does not exist, the artifact was not downloaded; check the workflow run and artifact name.

## F. Install the APK

Open Android's package installer:

```bash
termux-open ~/MAURICE-AI/apk/app-debug.apk
```

Android may ask you to allow Termux to install unknown apps. Open the Android setting shown in the prompt, enable **Allow from this source**, return to the installer and tap **Install**.

If Android blocks the installation, check that the phone is running Android 9 or newer and that you are not trying to downgrade over a newer MAURICE AI installation signed with a different key. Uninstall the older test build first if necessary; uninstalling clears its local chat and memory database.

## G. Connect Gemini (one-time setup)

1. On the phone or computer, open https://aistudio.google.com/app/apikey.
2. Sign in and create a Gemini API key.
3. Open **MAURICE AI → Settings**.
4. Paste the key and tap **SAVE KEY**.
5. Connect to the internet and send a test message.

The app uses Gemini 3.8 Flash. Google's free tier is subject to quotas, eligibility and policy changes. Google may process free-tier prompts under its data-use terms; do not send passwords, banking credentials, or sensitive private information.

## H. Use MAURICE AI offline

These commands run locally and do not need Gemini or internet:

```text
help
remember I prefer concise answers
note Buy a USB cable
show memory
time
date
clear history
```

Your saved notes and chat history are stored in the app's private SQLite database on the phone. They remain available without internet. `clear history` clears conversations but keeps saved memory. The Memory screen's **CLEAR MEMORY** button clears saved notes.

## I. Update the app later

After uploading new source changes to the `main` branch, GitHub Actions automatically builds a new APK. Or trigger it manually:

```bash
cd ~/MAURICE-AI
git pull
gh workflow run android.yml --repo arindemaurice/andriod --ref main
```

Repeat sections D-F to download and install the new APK. Keep the same Android application ID and signing configuration for future signed release updates. The included debug APK is for personal testing, not Play Store publishing.

## Troubleshooting

- **`gh: command not found`:** run `pkg install gh`.
- **Authentication error:** run `gh auth login`, then `gh auth status`.
- **Workflow not found:** verify `.github/workflows/android.yml` is in the repository root and pushed to `main`.
- **Build failed:** inspect the first error in GitHub Actions. Confirm SDK platform 35, Java 17 and Gradle 8.9 setup steps succeeded.
- **Artifact not found:** ensure the workflow succeeded and the artifact is named `maurice-ai-debug-apk`.
- **Gemini error:** check internet, API key, model availability and your Google AI Studio quota.
- **No Gemini key:** offline commands and saved memory still work. Normal AI answers need a configured key and internet.
- **Voice button does nothing:** install or enable a compatible speech-recognition service. Speech recognition may need internet even though MAURICE AI's saved memory works offline.

## Device target

The first confirmed target is Samsung Galaxy A04: Android 12, ARM64, 3 GB RAM and 32 GB storage. The app's minimum supported Android version is Android 9 (API 28). The exact vivo model and Android version have not yet been confirmed, so compatibility with that particular vivo device remains to be checked.
