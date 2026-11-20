#!/usr/bin/env bash
# Run Android UI tests and collect diagnostics without losing Gradle's exit code.
set +e
gradle :app:connectedDebugAndroidTest
test_status=$?

set -e
mkdir -p android-13-screenshots
# Capture remote directory state before pulling, so a failed pull is diagnosable.
adb shell 'ls -laR /sdcard/Android/data/com.sunrisesunset.clock/files' \
    > android-13-screenshots/device-files-list.txt 2>&1 || true
adb pull /sdcard/Android/data/com.sunrisesunset.clock/files/screenshots \
    android-13-screenshots/app-screenshots/ > android-13-screenshots/pull-screenshots.log 2>&1 || true
adb shell dumpsys appwidget > android-13-screenshots/appwidget-dump.txt 2>&1 || true
adb logcat -d > android-13-screenshots/logcat.txt 2>&1 || true
adb exec-out screencap -p > android-13-screenshots/emulator-final.png 2>/dev/null || true

exit "$test_status"
