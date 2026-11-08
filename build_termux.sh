#!/data/data/com.termux/files/usr/bin/bash
# MYRA AI — Termux build helper
set -e
pkg update -y
pkg install -y openjdk-17 gradle aapt2 git unzip wget

echo "== Gradle version (AGP 8.5.2 needs >= 8.7) =="
gradle --version | head -8

if [ -z "$ANDROID_HOME" ]; then
  echo "ERROR: ANDROID_HOME not set."
  echo "Install an Android SDK that works on ARM (platform android-34 + build-tools 34.0.0)"
  echo "and run:  export ANDROID_HOME=/path/to/sdk"
  exit 1
fi

echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug --no-daemon -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2"

APK=app/build/outputs/apk/debug/app-debug.apk
ls -lh "$APK"
if [ -d "$HOME/storage/downloads" ]; then
  cp "$APK" "$HOME/storage/downloads/MyraAI.apk" && echo "Copied to Downloads/MyraAI.apk"
else
  echo "Run 'termux-setup-storage' once, then copy: cp $APK ~/storage/downloads/MyraAI.apk"
fi
