#!/usr/bin/env bash
# Run Android UI tests and collect diagnostics without losing Gradle's exit code.
set +e
gradle :app:connectedDebugAndroidTest
test_status=$?
set -e

mkdir -p android-13-screenshots
adb pull /storage/emulated/0/Android/data/com.sunrisesunset.clock/files/screenshots/. android-13-screenshots/ || true
adb shell dumpsys appwidget > android-13-screenshots/appwidget-dump.txt 2>&1 || true
adb logcat -d > android-13-screenshots/logcat.txt 2>&1 || true
adb exec-out screencap -p > android-13-screenshots/emulator-final.png 2>/dev/null || true

exit "$test_status"
