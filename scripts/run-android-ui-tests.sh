#!/usr/bin/env bash
# Run Android UI tests and collect diagnostics without losing Gradle's exit code.
set +e
gradle :app:connectedDebugAndroidTest
test_status=$?
set -e

mkdir -p android-13-screenshots
# The Gradle connected-test task uninstalls the target app after instrumentation.
# Checkpoints are copied into shell-owned tmp during tests, so they survive cleanup.
adb pull /data/local/tmp/sunrise-sunset-ci/. android-13-screenshots/ || true
adb shell dumpsys appwidget > android-13-screenshots/appwidget-dump-after-test-cleanup.txt 2>&1 || true
adb logcat -d > android-13-screenshots/logcat.txt 2>&1 || true
adb exec-out screencap -p > android-13-screenshots/emulator-final.png 2>/dev/null || true

exit "$test_status"
