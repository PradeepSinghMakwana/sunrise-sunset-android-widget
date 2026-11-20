#!/usr/bin/env bash
# Run Android UI tests and collect diagnostics without losing Gradle's exit code.
# The NOAA calculation returns local civil time in the device zone. Configure it
# through AlarmManager (setprop is blocked on modern emulator builds), then verify
# the effective zone before launching instrumentation.
adb shell settings put global auto_time_zone 0
adb shell cmd alarm set-timezone America/Los_Angeles
device_timezone="$(adb shell date +%Z%z | tr -d '\r')"
if [[ "$device_timezone" != "PST-0800" && "$device_timezone" != "PDT-0700" ]]; then
    echo "Android 13 emulator timezone is '$device_timezone'; expected a California PST/PDT offset"
    exit 1
fi
adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone America/Los_Angeles >/dev/null 2>&1 || true

# Use a generic tall-phone display, not a Pixel hardware profile or OEM skin.
# Apply before launching the app so UI layout and screenshot evidence use it.
adb shell wm size 1080x2400
adb shell wm density 420
display_size="$(adb shell wm size | tr -d '\r')"
display_density="$(adb shell wm density | tr -d '\r')"
if [[ "$display_size" != *"Override size: 1080x2400"* ]]; then
    echo "Emulator display size was not applied: $display_size"
    exit 1
fi
if [[ "$display_density" != *"Override density: 420"* ]]; then
    echo "Emulator display density was not applied: $display_density"
    exit 1
fi

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
