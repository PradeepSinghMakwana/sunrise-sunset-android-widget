#!/usr/bin/env bash
set +e

mkdir -p screenshots/diagnostics

finish_diagnostics() {
  test_status=$?
  trap - EXIT
  echo "$test_status" > screenshots/diagnostics/instrumentation-exit-code.txt
  adb logcat -d -v threadtime > screenshots/diagnostics/logcat.txt 2>&1 || true
  adb shell dumpsys activity activities > screenshots/diagnostics/activities.txt 2>&1 || true
  adb shell dumpsys window > screenshots/diagnostics/windows.txt 2>&1 || true
  adb shell dumpsys package com.sunrisesunset.clock > screenshots/diagnostics/app-package.txt 2>&1 || true
  adb shell screencap -p /sdcard/ci-final-screen.png >/dev/null 2>&1 || true
  adb pull /sdcard/ci-final-screen.png screenshots/diagnostics/emulator-final-screen.png >/dev/null 2>&1 || true
  adb exec-out run-as com.sunrisesunset.clock tar -cf - files/screenshots > screenshots/diagnostics/app-private-files.tar 2> screenshots/diagnostics/run-as-error.txt || true
  if [ -s screenshots/diagnostics/app-private-files.tar ]; then
    tar -xf screenshots/diagnostics/app-private-files.tar -C screenshots/diagnostics || true
  fi
  adb shell ls -la /sdcard/Android/data/com.sunrisesunset.clock/files/screenshots > screenshots/diagnostics/external-files.txt 2>&1 || true
  cp -R app/build/outputs/androidTest-results screenshots/diagnostics/ 2>/dev/null || true
  cp -R app/build/reports/androidTests screenshots/diagnostics/ 2>/dev/null || true
  exit "$test_status"
}
trap finish_diagnostics EXIT

echo "=== Emulator boot state ===" > screenshots/diagnostics/emulator-state.txt
adb shell getprop >> screenshots/diagnostics/emulator-state.txt 2>&1 || true
adb shell wm size >> screenshots/diagnostics/emulator-state.txt 2>&1 || true
adb shell wm density >> screenshots/diagnostics/emulator-state.txt 2>&1 || true

gradle :app:connectedDebugAndroidTest
test_status=$?
exit "$test_status"
