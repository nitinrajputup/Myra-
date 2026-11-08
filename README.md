# MYRA AI — Android personal AI assistant

Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore · OkHttp · WorkManager · Gemini

## Build (Termux)
1. `unzip MyraAI.zip && cd MyraAI`
2. Install an ARM-compatible Android SDK (platform 34, build-tools 34.0.0) and `export ANDROID_HOME=...`
3. `bash build_termux.sh`
4. APK: `app/build/outputs/apk/debug/app-debug.apk`

**Termux problems?**
- `aapt2` / "Syntax error: word unexpected": Termux is ARM; pass `-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2` (the script does).
- AGP 8.5.2 needs Gradle >= 8.7 (`gradle --version`). Older: lower AGP to 8.2.2 and Kotlin/Compose versions accordingly.
- Out of memory: close apps, keep `--no-daemon`, lower `org.gradle.jvmargs` in gradle.properties.
- Easiest reliable alternative: push this folder to a GitHub repo from Termux (`git push`); `.github/workflows/build.yml`
  builds the APK in the cloud and uploads it as an artifact. Android Studio on a PC also works (open folder → Run).

## First run
Settings → paste your Gemini API key → Save → Test connection.
Then: Voice tab (mic), Chat, Automation (enable Accessibility manually), Settings → Voice → Start service (wake word + battery).

## What is real / limits
- Wake word = SpeechRecognizer loop inside a visible foreground service. Uses the mic continuously, may beep on some phones,
  can be stopped by battery-saver/OEM restrictions, and does not auto-start after reboot.
- Launching apps from the background can be blocked by Android 10+; grant "Display over other apps" in Settings.
- YouTube: opens YouTube search/video. In-app results need your own YouTube Data API key. No background playback/Premium claims.
- Accessibility: user must enable manually; sensitive actions ask confirmation; password fields are never read; STOP button/voice "ruko".
- Coding Studio: editor, AI tools, files, zip import/export, terminal (app sandbox). Real Gradle builds normally need Termux/PC;
  the Build tab runs `gradle assembleDebug` only if available in the app sandbox, otherwise shows the real error.
- Gemini replies are not streamed (full response). Female voice depends on installed Hindi TTS voices.
