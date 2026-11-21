# Android 13 (API 33) emulator validation

The project's automated UI validation and release screenshot environment is an Android 13 emulator (API level 33), using the AOSP `default` x86_64 system image. This is an explicit emulator-based release evidence policy; screenshots must not be described as physical-device captures.

The Android Emulator Runner action creates the AVD without a named hardware profile. We deliberately omit `profile`: this avoids selecting any Google Pixel hardware profile. The system image is generic AOSP (`target: default`), not a Pixel/Google Play image or an OEM skin. After boot, `scripts/run-android-ui-tests.sh` configures and verifies a modern tall-phone display at 1080 × 2400 and 420 dpi.

The previous CI failure was not an Android 13 image availability problem. The runner successfully downloaded `system-images;android-33;default;x86_64`, then `avdmanager` rejected the unverified `Pixel_2` device ID with `No device found matching --device Pixel_2`. That prevented AVD creation and the emulator never started.

References:
- [Android Emulator Runner documentation and supported inputs](https://github.com/ReactiveCircus/android-emulator-runner#configurations)
- [Android Emulator Runner AVD creation implementation](https://github.com/ReactiveCircus/android-emulator-runner/blob/main/src/emulator-manager.ts)
- [Android Virtual Device management](https://developer.android.com/studio/run/managing-avds)

## SDK configuration

Keep the app SDK configuration:
- `minSdk = 26`
- `compileSdk = 35`
- `targetSdk = 35`

The emulator's API level is the runtime under test. Do not lower `targetSdk` to 33: that setting controls compatibility behavior and distribution policy, not which Android version runs the app. A target-35 build is installable and testable on Android 13 / API 33.

Use JDK 17 and Gradle 8.9. The CI pipeline must run `gradle :app:testDebugUnitTest` successfully before starting the emulator job. The emulator job then runs `gradle :app:connectedDebugAndroidTest`.

## CI emulator configuration

The workflows use:
- `reactivecircus/android-emulator-runner@v2`
- API level 33
- AOSP `default` system image
- x86_64 architecture
- No named AVD hardware profile; do not use Google Pixel profiles
- Generic AOSP launcher/system image (`target: default`)
- Display override: 1080 × 2400 pixels at 420 dpi, verified through `adb shell wm`
- KVM enabled on the GitHub-hosted Linux runner
- Headless rendering with SwiftShader
- Animations disabled
- No boot snapshots

The API 33 x86_64 system image was confirmed downloadable in the failed run logs. The earlier failure occurred after image installation, at AVD profile selection. No Pixel profile, Google Play image, or AWS Device Farm is needed for this pass.

## Automated Android 13 checks

The instrumentation suite:
1. Unlocks the emulator and launches the actual setup Activity.
2. Waits for the “Use approximate location” action and captures the actual app screen.
3. Seeds a test-only approximate coordinate in app preferences.
4. Opens the Android launcher and widget picker.
5. Places the actual app widget on the launcher.
6. Verifies both SUNRISE and SUNSET labels are visible.
7. Captures the actual launcher home screen with the widget.

The test captures checkpoint PNGs and UI hierarchy XML into shell-owned emulator storage. `scripts/run-android-ui-tests.sh` pulls them into `android-13-screenshots/` and fails if any required PNG or hierarchy XML is missing or empty.

## Screenshot acceptance

The release workflow publishes:
- `01-app-setup.png`: the app setup screen with the approximate-location action visible.
- `02-home-screen-widget.png`: the Android 13 emulator launcher home screen with the app widget placed and both SUNRISE and SUNSET labels visible.

These are actual emulator UI captures, not mockups or physical-device screenshots. UI assertions and PNG/evidence checks are mandatory. Review the images visually from the release workflow artifact. The emulator is repeatable release evidence for this configured AOSP Android 13 environment; it does not establish behavior on all OEM launchers or physical devices.

## Permissions

The manifest declares only `ACCESS_COARSE_LOCATION`. Do not add `ACCESS_FINE_LOCATION` just to satisfy tests. The widget-placement test seeds coordinates in test app preferences and must not request the user's location permission.

## AVD profile regression tests

Run the fast, host-only checks with:

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

The regression suite includes the exact historical failure: a `Pixel_2` ID absent from `avdmanager list device` output must be rejected. Matching is exact and case-sensitive. Additional tests require both CI workflows to omit named profiles and verify that the runner applies and checks the 1080 × 2400 / 420 dpi display override.
