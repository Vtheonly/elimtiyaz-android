#!/usr/bin/env bash
set -e

echo "==> [1/6] Configuring Android SDK location..."
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
    else
        echo "ERROR: Android SDK directory could not be found automatically."
        echo "Please run: echo 'sdk.dir=/path/to/your/sdk' > local.properties"
        exit 1
    fi
else
    echo "local.properties already configured."
fi

echo "==> [2/6] Configuring Host Network & Waydroid DNS..."
sudo sysctl -w net.ipv4.ip_forward=1 >/dev/null
DEFAULT_IF=$(ip route show default | awk '{print $5}' | head -n1)
if [ -n "$DEFAULT_IF" ]; then
    sudo iptables -t nat -C POSTROUTING -o "$DEFAULT_IF" -j MASQUERADE 2>/dev/null || sudo iptables -t nat -A POSTROUTING -o "$DEFAULT_IF" -j MASQUERADE
    sudo iptables -C FORWARD -i waydroid0 -j ACCEPT 2>/dev/null || sudo iptables -A FORWARD -i waydroid0 -j ACCEPT
    sudo iptables -C FORWARD -o waydroid0 -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || sudo iptables -A FORWARD -o waydroid0 -m state --state RELATED,ESTABLISHED -j ACCEPT
fi
adb shell setprop net.dns1 8.8.8.8 2>/dev/null || true

echo "==> [3/6] Sanitizing line endings and syncing .env..."
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

echo "==> [4/6] Stopping Gradle daemons and compiling debug APK..."
./gradlew --stop
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.aistudio.elimtiyazstaff.bxmzlx"

if [ ! -f "$APK" ]; then
    echo "ERROR: Compilation finished but APK was not found at $APK"
    exit 1
fi

echo "==> [5/6] Clearing old container data and installing..."
adb shell pm clear "$PKG" 2>/dev/null || true
waydroid app install "$APK" 2>/dev/null || adb install -r "$APK"

echo "==> [6/6] Launching El-Imtiyaz Staff in Waydroid..."
waydroid app launch "$PKG" 2>/dev/null || adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1

echo "==> Done! App is compiled, installed, and running."
rm -f run_deploy.sh
