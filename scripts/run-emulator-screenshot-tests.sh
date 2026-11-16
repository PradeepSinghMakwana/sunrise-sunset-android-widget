#!/usr/bin/env bash
set +e

DIAG_DIR="screenshots/diagnostics"
mkdir -p "$DIAG_DIR"

capture_host_screenshot() {
  local name="$1"
  adb exec-out screencap -p > "$DIAG_DIR/$name" 2> "$DIAG_DIR/$name.stderr" || true
}

collect_diagnostics() {
  status=$?
  trap - EXIT
  mkdir -p "$DIAG_DIR"
  printf '%s\n' "$status" > "$DIAG_DIR/instrumentation-exit-code.txt"

  # UI Automator writes test captures to shared emulator storage so they
  # survive Gradle uninstalling the test and target APKs after the run.
  for name in 03-test-app-setup.png 03-test-app-setup.xml \
              04-test-launcher-before-widget.png 04-test-launcher-before-widget.xml \
              05-launcher-edit-menu.png 05-launcher-edit-menu.xml \
              06-widget-picker-open.png 06-widget-picker-open.xml \
              07-widget-preview.png 07-widget-preview.xml \
              08-test-widget-after-placement.png 08-test-widget-after-placement.xml \
              08-widget-after-placement.png 08-widget-after-placement.xml; do
    adb pull "/sdcard/$name" "$DIAG_DIR/$name" >/dev/null 2>&1 || true
  done

  adb devices -l > "$DIAG_DIR/adb-devices.txt" 2>&1 || true
  adb shell pm list packages > "$DIAG_DIR/installed-packages.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$DIAG_DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$DIAG_DIR/activities.txt" 2>&1 || true
  adb shell dumpsys window > "$DIAG_DIR/windows.txt" 2>&1 || true
  adb shell dumpsys package com.sunrisesunset.clock > "$DIAG_DIR/app-package-after-tests.txt" 2>&1 || true

  capture_host_screenshot "06-emulator-final-screen.png"
  cp -R app/build/outputs/androidTest-results "$DIAG_DIR/" 2>/dev/null || true
  cp -R app/build/reports/androidTests "$DIAG_DIR/" 2>/dev/null || true
  exit "$status"
}
trap collect_diagnostics EXIT

state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
if [ "$state" != "1" ]; then
  echo "Emulator action started the script before sys.boot_completed=1" >&2
  adb devices -l >&2 || true
  exit 1
fi

adb devices -l > "$DIAG_DIR/adb-devices-before-tests.txt" 2>&1
adb shell getprop > "$DIAG_DIR/emulator-properties.txt" 2>&1 || true
adb shell wm size > "$DIAG_DIR/emulator-size.txt" 2>&1 || true
adb shell wm density > "$DIAG_DIR/emulator-density.txt" 2>&1 || true

# Always preserve visual evidence, even if emulator unlock fails.
# Capture the booted screen before attempting keyguard dismissal.
capture_host_screenshot "00-emulator-boot-screen.png"
adb shell uiautomator dump /sdcard/ci-boot-screen.xml > "$DIAG_DIR/boot-hierarchy-command.txt" 2>&1 || true
adb pull /sdcard/ci-boot-screen.xml "$DIAG_DIR/00-emulator-boot-screen.xml" >/dev/null 2>&1 || true

# Disable and dismiss the emulator lock screen before launching the app or tests.
adb shell locksettings set-disabled true
locksettings_status=$(adb shell locksettings get-disabled 2>&1 | tr -d '\r')
printf '%s\n' "$locksettings_status" > "$DIAG_DIR/pretest-locksettings.txt"
if [ "$locksettings_status" != "true" ]; then
  echo "Could not disable the emulator lock screen; skipping UI tests." >&2
  exit 1
fi

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
sleep 1
capture_host_screenshot "00b-emulator-after-wake-and-dismiss-1s.png"
adb shell uiautomator dump /sdcard/ci-after-wake.xml > "$DIAG_DIR/after-wake-hierarchy-command.txt" 2>&1 || true
adb pull /sdcard/ci-after-wake.xml "$DIAG_DIR/00b-emulator-after-wake-and-dismiss-1s.xml" >/dev/null 2>&1 || true

adb shell dumpsys power > "$DIAG_DIR/pretest-power.txt" 2>&1 || true
adb shell dumpsys window > "$DIAG_DIR/pretest-windows.txt" 2>&1 || true
if ! grep -q 'isKeyguardShowing=false' "$DIAG_DIR/pretest-windows.txt"; then
  echo "Emulator is still showing its lock screen; skipping UI tests." >&2
  exit 1
fi

# Capture the real installed app and launcher on every emulator run,
# before assertions can fail. The requested pause is exactly one second.
adb install -r app/build/outputs/apk/debug/app-debug.apk > "$DIAG_DIR/pretest-install.txt" 2>&1
if [ "$?" -ne 0 ]; then
  echo "Could not install the debug APK before screenshot capture." >&2
  exit 1
fi
adb shell am start -W -n com.sunrisesunset.clock/.LocationSetupActivity > "$DIAG_DIR/pretest-app-launch.txt" 2>&1 || true
sleep 1
capture_host_screenshot "01-app-setup-after-1s.png"
adb shell uiautomator dump /sdcard/ci-pretest-app-setup.xml > "$DIAG_DIR/pretest-app-hierarchy-command.txt" 2>&1 || true
adb pull /sdcard/ci-pretest-app-setup.xml "$DIAG_DIR/01-app-setup-after-1s.xml" >/dev/null 2>&1 || true

adb shell input keyevent KEYCODE_HOME
sleep 1
capture_host_screenshot "02-home-screen-before-widget-after-1s.png"
adb shell uiautomator dump /sdcard/ci-pretest-home.xml > "$DIAG_DIR/pretest-home-hierarchy-command.txt" 2>&1 || true
adb pull /sdcard/ci-pretest-home.xml "$DIAG_DIR/02-home-screen-before-widget-after-1s.xml" >/dev/null 2>&1 || true

# Keep test failures as failures; the EXIT trap preserves every screenshot and log.
gradle :app:connectedDebugAndroidTest
exit $?
