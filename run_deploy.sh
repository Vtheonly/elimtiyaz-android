#!/usr/bin/env bash
set -e

echo "==> [1/5] Checking Android SDK location..."
if [ ! -f local.properties ] || ! grep -q "sdk.dir" local.properties; then
    SDK_DIR=""
    for candidate in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$HOME/Android/Sdk" "/usr/lib/android-sdk" "$HOME/.android/sdk" "/opt/android-sdk"; do
        if [ -n "$candidate" ] && [ -d "$candidate" ]; then
            SDK_DIR="$candidate"
            break
        fi
    done

    if [ -n "$SDK_DIR" ]; then
        echo "sdk.dir=$SDK_DIR" > local.properties
        echo "Created local.properties -> sdk.dir=$SDK_DIR"
    fi
else
    echo "local.properties configured."
fi

echo "==> [2/5] Connecting ADB to Waydroid..."
WAYDROID_IP=$(waydroid shell ip route 2>/dev/null | head -n1 | awk '{print $1}')
if [ -n "$WAYDROID_IP" ]; then
    adb connect "$WAYDROID_IP:5555" 2>/dev/null || true
fi

echo "==> [3/5] Syncing .env environment variables..."
if [ ! -f .env ] && [ -f app/.env ]; then
    cp app/.env .env
elif [ ! -f .env ] && [ -f .env.example ]; then
    cp .env.example .env
fi

if [ -f .env ]; then
    sed -i "s/\r$//" .env
    mkdir -p app
    cp -f .env app/.env
    sed -i "s/\r$//" app/.env
fi

echo "==> [4/5] Compiling debug APK..."
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.aistudio.elimtiyazstaff.bxmzlx"

if [ ! -f "$APK" ]; then
    echo "ERROR: Compilation finished but APK was not found at $APK"
    exit 1
fi

echo "==> [5/5] Installing and Launching in Waydroid..."
waydroid app install "$APK" 2>/dev/null || adb install -r "$APK" 2>/dev/null || true
waydroid app launch "$PKG" 2>/dev/null || true

echo "==> App successfully deployed and launched!"
