# Android 13 physical-device release validation

The release test device is a **Samsung Galaxy A51 running Android 13 (API level 33)**. Use this real phone for runtime checks and store screenshots. Do not start an Android emulator, Gradle Managed Device, or cloud emulator. AWS Device Farm is not required for this single-device release pass.

## SDK configuration

The app intentionally keeps:
- `minSdk = 26` (Android 8.0 and later)
- `compileSdk = 35`
- `targetSdk = 35`

Do **not** set `targetSdk = 33` just because the test phone runs Android 13. `targetSdk` controls compatibility behavior and Play policy; it does not select the OS on the test device. A target-35 APK can be installed and tested on Android 13 (API 33). Testing it on API 33 exercises the Android 13 runtime behavior while preserving the newer target for distribution.

Use JDK 17 and Gradle 8.9 in CI. Build the candidate with `gradle :app:assembleDebug`; use the debug APK for device validation. Do not use the unsigned release APK as a consumer-installable package.

## Prepare the physical phone

1. Confirm **Settings → About phone → Software information → Android version** says Android 13.
2. Ensure Location is enabled and Wi-Fi or mobile data is available.
3. Remove any previous test installation if you want to test first-run permission behavior from a clean state. This also clears app-saved coordinates.
4. Install the CI debug APK. If using a computer with Android Platform Tools:
   ```sh
   adb devices
   adb install -r app-debug.apk
   adb shell getprop ro.build.version.sdk
   ```
   The final command must print `33`.
5. Launch Sunrise Sunset Clock.

## Required Android 13 checks

Record pass/fail and capture genuine screenshots only after the relevant check succeeds.

| Check | Expected result |
|---|---|
| First launch | Setup screen opens without crash |
| Permission disclosure | Says approximate location; does not claim precise location |
| Permission prompt | Requests only approximate location; no precise-location option is requested by the app |
| Deny permission | Clear denial message; app remains usable and does not crash |
| Grant approximate location | Location lookup completes and reports saved location |
| Location disabled | Clear retry guidance; no crash or indefinite spinner |
| Widget picker | Sunrise Sunset Clock appears in the Samsung launcher widget picker |
| Add widget | Widget can be placed and resized on the home screen |
| Widget values | Date, sunrise and sunset appear for the device's local date and timezone |
| Widget tap | Opens the app setup screen |
| Refresh location | Updates saved approximate coordinates and refreshes widget |
| Reboot | Existing widget remains and can update after boot/unlock |
| Date/time/timezone change | Widget refreshes for the new local date/timezone |
| Offline calculation | With saved coordinates, sunrise/sunset calculation does not require a network request |

The manifest currently declares only `ACCESS_COARSE_LOCATION`. Android 13's approximate-location permission flow is therefore the expected permission flow. Do not add `ACCESS_FINE_LOCATION` merely to make the test pass.

## Store screenshots

Use the Samsung phone's native screenshot function (Power + Volume Down). Do not edit, composite, generate, or emulate the captures.

1. Complete setup and capture the real app screen as `01-app-setup.png`.
2. Add the widget to the actual Samsung launcher home screen. Ensure both SUNRISE and SUNSET labels and their values are visible, then capture `02-home-screen-widget.png`.
3. Ensure the status bar and screenshot do not expose private notifications, personal messages, or account information.
4. Copy the original PNGs to:
   `fastlane/metadata/android/en-US/images/phoneScreenshots/`
5. Open both PNGs and visually confirm they are legible and accurately represent the app before committing.

The automated release gate checks PNG signature, dimensions, file size, and that the two files differ. Those checks do not prove device provenance or visual quality; human review on the physical phone is mandatory.

## Evidence to record

Record:
- Device: Samsung Galaxy A51 (SM-A515F)
- Android version: 13
- API level: 33
- App version name/code from the candidate APK
- Pass/fail for every check above
- Original screenshot files

If a check fails, record the exact screen/message and stop before creating the release tag. Do not fall back to an emulator.