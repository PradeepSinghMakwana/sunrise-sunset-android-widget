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
  # Primary retrieval path: external app files are accessible through adb pull.
  adb shell ls -la /sdcard/Android/data/com.sunrisesunset.clock/files/screenshots > screenshots/diagnostics/external-files.txt 2>&1 || true
  adb pull /sdcard/Android/data/com.sunrisesunset.clock/files/screenshots/. screenshots/ > screenshots/diagnostics/adb-pull-screenshots.txt 2>&1 || true

  # Keep a private-files fallback for emulator images where external storage is unavailable.
  if [ ! -s screenshots/01-app-setup.png ] || [ ! -s screenshots/02-home-screen-widget.png ]; then
    adb exec-out run-as com.sunrisesunset.clock tar -cf - files/screenshots > screenshots/diagnostics/app-private-files.tar 2> screenshots/diagnostics/run-as-error.txt || true
    if [ -s screenshots/diagnostics/app-private-files.tar ]; then
      mkdir -p screenshots/private-fallback
      tar -xf screenshots/diagnostics/app-private-files.tar -C screenshots/private-fallback || true
      while IFS= read -r png; do cp "$png" screenshots/; done < <(find screenshots/private-fallback -type f -name '*.png')
    fi
  fi

  # Regression guard: a green instrumentation run is not enough if the two real
  # app/widget captures were not retrieved for the artifact.
  missing_screenshots=0
  for expected in 01-app-setup.png 02-home-screen-widget.png; do
    if [ ! -s "screenshots/$expected" ]; then
      echo "Required screenshot missing or empty: $expected" | tee -a screenshots/diagnostics/screenshot-regression-check.txt
      missing_screenshots=1
    else
      size=$(wc -c < "screenshots/$expected")
      echo "Captured $expected ($size bytes)" | tee -a screenshots/diagnostics/screenshot-regression-check.txt
    fi
  done

  cp -R app/build/outputs/androidTest-results screenshots/diagnostics/ 2>/dev/null || true
  cp -R app/build/reports/androidTests screenshots/diagnostics/ 2>/dev/null || true

  final_status=$test_status
  if [ "$missing_screenshots" -ne 0 ] && [ "$final_status" -eq 0 ]; then
    final_status=1
  fi
  exit "$final_status"
}
trap finish_diagnostics EXIT

echo "=== Emulator boot state ===" > screenshots/diagnostics/emulator-state.txt
adb shell getprop >> screenshots/diagnostics/emulator-state.txt 2>&1 || true
adb shell wm size >> screenshots/diagnostics/emulator-state.txt 2>&1 || true
adb shell wm density >> screenshots/diagnostics/emulator-state.txt 2>&1 || true

gradle :app:connectedDebugAndroidTest
test_status=$?
exit "$test_status"
