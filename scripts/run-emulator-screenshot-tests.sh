#!/usr/bin/env bash
set +e

DIAG_DIR="screenshots/diagnostics"
mkdir -p "$DIAG_DIR"

collect_diagnostics() {
  status=$?
  trap - EXIT
  mkdir -p "$DIAG_DIR"
  printf '%s\n' "$status" > "$DIAG_DIR/instrumentation-exit-code.txt"

  adb devices -l > "$DIAG_DIR/adb-devices.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$DIAG_DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$DIAG_DIR/activities.txt" 2>&1 || true
  adb shell dumpsys window > "$DIAG_DIR/windows.txt" 2>&1 || true
  adb shell dumpsys package com.sunrisesunset.clock > "$DIAG_DIR/app-package.txt" 2>&1 || true

  adb shell screencap -p /sdcard/ci-final-screen.png >/dev/null 2>&1 || true
  adb pull /sdcard/ci-final-screen.png "$DIAG_DIR/emulator-final-screen.png" >/dev/null 2>&1 || true

  adb exec-out run-as com.sunrisesunset.clock tar -cf - files/screenshots > "$DIAG_DIR/app-private-files.tar" 2> "$DIAG_DIR/run-as-error.txt" || true
  if [ -s "$DIAG_DIR/app-private-files.tar" ]; then
    tar -xf "$DIAG_DIR/app-private-files.tar" -C "$DIAG_DIR" || true
  fi

  adb shell ls -la /sdcard/Android/data/com.sunrisesunset.clock/files/screenshots > "$DIAG_DIR/external-files.txt" 2>&1 || true
  cp -R app/build/outputs/androidTest-results "$DIAG_DIR/" 2>/dev/null || true
  cp -R app/build/reports/androidTests "$DIAG_DIR/" 2>/dev/null || true
  exit "$status"
}
trap collect_diagnostics EXIT

# android-emulator-runner starts the AVD and waits for boot before invoking this script.
# Verify readiness once instead of adding a second polling window of up to five minutes.
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

gradle :app:connectedDebugAndroidTest
exit $?
