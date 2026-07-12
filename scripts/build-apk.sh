#!/bin/bash
# 在 x86_64 机器上本地编译 APK
set -e

echo "📱 Pi Mobile — Build APK"
echo "========================"

# Check prerequisites
if ! command -v java &> /dev/null; then
    echo "❌ JDK 17+ required. Install with: sudo apt install openjdk-17-jdk"
    exit 1
fi

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "❌ ANDROID_HOME not set."
    echo "   Install Android SDK first, then: export ANDROID_HOME=~/android-sdk"
    exit 1
fi

cd "$(dirname "$0")/android"

echo "✓ Building APK..."
gradle assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    SIZE=$(stat -c%s "$APK" 2>/dev/null || stat -f%z "$APK")
    echo ""
    echo "✅ APK built: $APK ($(( SIZE / 1024 )) KB)"
    echo "   adb install $APK"
else
    echo "❌ Build failed"
    exit 1
fi
