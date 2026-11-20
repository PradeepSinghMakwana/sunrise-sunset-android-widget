# Android 13 (API 33) emulator release validation

The release UI test and store screenshot environment is an Android 13 emulator (API level 33), using the AOSP `default` x86_64 system image and a Pixel 2 profile. This deliberately avoids Google Play services and avoids paid AWS Device Farm sessions.

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
- Pixel 2 profile
- Headless rendering with SwiftShader
- Animations disabled
- No boot snapshots

Do not use Google Play images or AWS Device Farm for this release pass. The AOSP image provides the Launcher3 package expected by the widget-placement UI test.

## Automated Android 13 checks

The instrumentation suite:
1. Unlocks the emulator and launches the actual setup Activity.
2. Waits for the “Use approximate location” action and captures the actual app screen.
3. Seeds a test-only approximate coordinate in app preferences.
4. Opens the Android Launcher3 home screen and widget picker.
5. Places the actual app widget on the launcher.
6. Verifies both SUNRISE and SUNSET labels are visible.
7. Captures the actual launcher home screen with the widget.

The test writes screenshots to:
`/sdcard/Android/data/com.sunrisesunset.clock/files/screenshots/`

CI pulls the two final PNGs into:
`fastlane/metadata/android/en-US/images/phoneScreenshots/`

Intermediate PNGs and UI hierarchy XML files are retained as CI artifacts for diagnosis. If a UI test fails, inspect these artifacts before changing coordinates, timings, or launcher selectors.

## Screenshot acceptance

Required store files:
- `01-app-setup.png`: the real app setup screen with the approximate-location action visible.
- `02-home-screen-widget.png`: the real Android 13 launcher home screen with the app widget placed and both SUNRISE and SUNSET labels visible.

The release workflow validates PNG signatures, dimensions, file size, and distinct content. These structural checks do not prove visual quality. Review the downloaded artifact images visually before accepting the release.

The emulator is used for repeatable UI testing and screenshot generation; it is not a substitute for broader testing on OEM launchers and physical devices. If launcher behavior differs on a physical device, treat that as separate device-compatibility testing, not as a reason to use a paid cloud device for this release.

## Permissions

The manifest declares only `ACCESS_COARSE_LOCATION`. Do not add `ACCESS_FINE_LOCATION` just to satisfy tests. The widget-placement test seeds coordinates in test app preferences and must not request the user's location permission.
