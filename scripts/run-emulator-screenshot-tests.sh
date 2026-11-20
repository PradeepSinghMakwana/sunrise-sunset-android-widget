#!/usr/bin/env bash
set +e

DIAG_DIR="screenshots/diagnostics"
mkdir -p "$DIAG_DIR"

capture_host_screenshot() {
  local name="$1"
  adb exec-out screencap -p > "$DIAG_DIR/$name" 2> "$DIAG_DIR/$name.stderr" || true
}

upload_checkpoint() {
  local name="$1"
  local files=("$DIAG_DIR/$name")
  local hierarchy="${name%.png}.xml"
  if [ -s "$DIAG_DIR/$hierarchy" ]; then
    files+=("$DIAG_DIR/$hierarchy")
  fi
  if [ ! -s "$DIAG_DIR/$name" ]; then
    echo "Screenshot checkpoint missing; not uploading: $name" >&2
    return 0
  fi
  echo "Uploading live screenshot checkpoint: $name"
  timeout 45s node scripts/upload-screenshot-artifact.cjs "$name" "${files[@]}" || {
    echo "Live upload failed for $name; continuing emulator tests." >&2
  }
}

collect_diagnostics() {
  status=$?
  trap - EXIT
  mkdir -p "$DIAG_DIR"
  # Pull the same external-files directory used by StoreScreenshotTest.
  mkdir -p screenshots/test-captures
  adb pull "/sdcard/Android/data/com.sunrisesunset.clock/files/screenshots/." screenshots/test-captures/ > "$DIAG_DIR/screenshot-pull.txt" 2>&1 || true

  # A successful instrumentation result is rejected unless both genuine
  # screenshots were collected and are non-empty.
  if [ "$status" -eq 0 ]; then
    for required in 01-app-setup.png 02-home-screen-widget.png; do
      if [ ! -s "screenshots/test-captures/$required" ]; then
        echo "Required genuine screenshot missing: $required" >&2
        status=1
      fi
    done
  fi
  printf '%s\n' "$status" > "$DIAG_DIR/instrumentation-exit-code.txt"

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

# Capture the actual booted display before waiting for the unlock transition.
# This uses ADB screencap only; no UI Automation connection is opened here.
capture_host_screenshot "00-emulator-boot-screen.png"
upload_checkpoint "00-emulator-boot-screen.png"

# sys.boot_completed can become 1 while Android is still unlocking user 0.
# Do not touch keyguard or start UI Automator until the user is RUNNING_UNLOCKED.
unlocked=false
for attempt in $(seq 1 60); do
  if adb shell dumpsys user 2>/dev/null | grep -q 'RUNNING_UNLOCKED'; then
    unlocked=true
    break
  fi
  sleep 2
done
if [ "$unlocked" != "true" ]; then
  echo "Android user 0 did not reach RUNNING_UNLOCKED within 120 seconds." >&2
  adb shell dumpsys user > "$DIAG_DIR/user-state-before-tests.txt" 2>&1 || true
  exit 1
fi
# Let keyguard/SystemUI finish its unlock transition before interacting.
sleep 10

adb devices -l > "$DIAG_DIR/adb-devices-before-tests.txt" 2>&1
adb shell getprop > "$DIAG_DIR/emulator-properties.txt" 2>&1 || true
adb shell wm size > "$DIAG_DIR/emulator-size.txt" 2>&1 || true
adb shell wm density > "$DIAG_DIR/emulator-density.txt" 2>&1 || true

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
upload_checkpoint "00b-emulator-after-wake-and-dismiss-1s.png"

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
upload_checkpoint "01-app-setup-after-1s.png"

adb shell input keyevent KEYCODE_HOME
sleep 1
capture_host_screenshot "02-home-screen-before-widget-after-1s.png"
upload_checkpoint "02-home-screen-before-widget-after-1s.png"

# Keep test failures as failures; the EXIT trap preserves every screenshot and log.
timeout --signal=TERM --kill-after=30s 15m gradle :app:connectedDebugAndroidTest
exit $?
