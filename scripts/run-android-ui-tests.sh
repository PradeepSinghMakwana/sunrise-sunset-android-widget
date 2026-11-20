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

# Evidence completeness is part of this job: screenshots without their UI
# hierarchies are not sufficient to diagnose Launcher3 picker failures.
required_screenshots=(
    "01-app-setup.png"
    "02-launcher-before.png"
    "02-home-screen-widget.png"
    "04-app-setup-ready.png"
    "05-launcher-edit-menu.png"
    "06-widget-picker-open.png"
    "07-widget-row-selected.png"
    "08-widget-after-placement.png"
)
required_hierarchies=(
    "01-app-setup.xml"
    "02-launcher-before.xml"
    "02-home-screen-widget.xml"
    "04-app-setup-ready.xml"
    "05-launcher-edit-menu.xml"
    "06-widget-picker-open.xml"
    "07-widget-row-selected.xml"
    "08-widget-after-placement.xml"
)
evidence_status=0
for file in "${required_screenshots[@]}"; do
    if [[ ! -s "android-13-screenshots/$file" ]]; then
        echo "Missing or empty Android 13 screenshot: $file"
        evidence_status=1
    fi
done
for file in "${required_hierarchies[@]}"; do
    if [[ ! -s "android-13-screenshots/$file" ]]; then
        echo "Missing or empty Android 13 UI hierarchy: $file"
        evidence_status=1
    fi
done
if [[ "$test_status" -eq 0 && "$evidence_status" -ne 0 ]]; then
    test_status=1
fi

exit "$test_status"
