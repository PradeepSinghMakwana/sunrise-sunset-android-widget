# Android 13 (API 33) emulator release validation

The release UI test and screenshot environment is an Android 13 emulator (API level 33), using the AOSP `default` x86_64 system image. The Android Emulator Runner action creates the AVD without a named hardware profile: its `profile` input is optional, and the action documentation explicitly says to use `avdmanager list device` to enumerate profile IDs before specifying one. We omit it rather than assuming a Pixel profile exists on the hosted runner.

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
- No hard-coded AVD hardware profile; let avdmanager create its default profile
- KVM enabled on the GitHub-hosted Linux runner
- Headless rendering with SwiftShader
- Animations disabled
- No boot snapshots

The API 33 x86_64 system image was confirmed downloadable in the failed run logs. The failure occurred after image installation, at the AVD profile selection step. No Google Play image or AWS Device Farm is needed for this pass.

## Automated Android 13 checks

The instrumentation suite:
1. Unlocks the emulator and launches the actual setup Activity.
2. Waits for the “Use approximate location” action and captures the actual app screen.
3. Seeds a test-only approximate coordinate in app preferences.
4. Opens the Android launcher and widget picker.
5. Places the actual app widget on the launcher.
6. Verifies both SUNRISE and SUNSET labels are visible.
7. Captures the actual launcher home screen with the widget.

The test writes screenshots to:
`/sdcard/Android/data/com.sunrisesunset.clock/files/screenshots/`

CI pulls the two final PNGs into:
`fastlane/metadata/android/en-US/images/phoneScreenshots/`

Intermediate PNGs and UI hierarchy XML files are retained as CI artifacts for diagnosis. If a UI test fails after the emulator boots, inspect these artifacts before changing coordinates, timings, or launcher selectors.

## Screenshot acceptance

Required store files:
- `01-app-setup.png`: the real app setup screen with the approximate-location action visible.
- `02-home-screen-widget.png`: the real Android 13 launcher home screen with the app widget placed and both SUNRISE and SUNSET labels visible.

The release workflow validates PNG signatures, dimensions, file size, and distinct content. These structural checks do not prove visual quality. Review the downloaded artifact images visually before accepting the release.

The emulator is used for repeatable UI testing and screenshot generation; it is not a substitute for broader testing on OEM launchers and physical devices.

## Permissions

The manifest declares only `ACCESS_COARSE_LOCATION`. Do not add `ACCESS_FINE_LOCATION` just to satisfy tests. The widget-placement test seeds coordinates in test app preferences and must not request the user's location permission.
