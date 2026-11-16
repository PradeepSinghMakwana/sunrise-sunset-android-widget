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
  adb shell pm list packages > "$DIAG_DIR/installed-packages.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$DIAG_DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$DIAG_DIR/activities.txt" 2>&1 || true
  adb shell dumpsys window > "$DIAG_DIR/windows.txt" 2>&1 || true
  adb shell dumpsys package com.sunrisesunset.clock > "$DIAG_DIR/app-package.txt" 2>&1 || true

  adb shell screencap -p /sdcard/ci-final-screen.png >/dev/null 2>&1 || true
  adb pull /sdcard/ci-final-screen.png "$DIAG_DIR/emulator-final-screen.png" >/dev/null 2>&1 || true

  # Instrumentation writes screenshots into the target app's private files directory.
  # Read from the same package and validate the archive before extracting it.
  if adb exec-out run-as com.sunrisesunset.clock tar -cf - -C files screenshots > "$DIAG_DIR/app-screenshots.tar" 2> "$DIAG_DIR/run-as-error.txt"; then
    if tar -tf "$DIAG_DIR/app-screenshots.tar" > "$DIAG_DIR/app-screenshots-list.txt" 2>/dev/null; then
      mkdir -p "$DIAG_DIR/app-screenshots"
      tar -xf "$DIAG_DIR/app-screenshots.tar" -C "$DIAG_DIR/app-screenshots"
    else
      echo "App screenshot archive was not a valid tar archive." >> "$DIAG_DIR/run-as-error.txt"
      rm -f "$DIAG_DIR/app-screenshots.tar"
    fi
  fi

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

# Disable the emulator lock screen using the same command used by Android's
# own CTS test suite. Dismissing the keyguard alone did not unlock this AVD.
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
sleep 2

# Verify the lock screen is actually gone before spending time on UI tests.
adb shell dumpsys power > "$DIAG_DIR/pretest-power.txt" 2>&1 || true
adb shell dumpsys window > "$DIAG_DIR/pretest-windows.txt" 2>&1 || true
if ! grep -q 'isKeyguardShowing=false' "$DIAG_DIR/pretest-windows.txt"; then
  echo "Emulator is still showing its lock screen; skipping UI tests." >&2
  exit 1
fi

gradle :app:connectedDebugAndroidTest
exit $?
