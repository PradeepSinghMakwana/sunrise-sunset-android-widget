# Release process

## Normal release

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Update store listing text under `fastlane/metadata/android/en-US/` if the app description or features changed.
3. Install the candidate build on a **physical Android phone**. Do not use an emulator for release screenshots. Capture:
   - `01-app-setup.png`: the real app setup screen, with the setup action visible.
   - `02-home-screen-widget.png`: the real Android launcher home screen with Sunrise Sunset Clock placed and both SUNRISE and SUNSET values visible.
4. Save both original PNG captures in `fastlane/metadata/android/en-US/images/phoneScreenshots/`. Review them visually before committing. Do not substitute mockups, generated images, or emulator captures.
5. Commit and push the release-ready change to `main`.
6. Create and push a matching version tag, for example `v2.0`:
   ```sh
   git tag v2.0
   git push origin v2.0
   ```

The tag must exactly match `versionName`. The release workflow validates the two physical-device screenshots, audits the version and Android permissions, builds the release APK, records the resolved Gradle dependency tree, generates version-specific F-Droid metadata, packages the exact tagged source, and publishes the assets as a GitHub Release. **The release workflow does not start an emulator or run Android emulator UI tests.**

## Gradle build

Requirements: JDK 17 and Gradle 8.9.

From the repository root:
```sh
gradle --version
gradle clean
gradle :app:assembleDebug
gradle :app:assembleRelease
gradle :app:dependencies --configuration releaseRuntimeClasspath
```

The GitHub Actions workflows install JDK 17 and Gradle 8.9 automatically. The debug workflow is for installable test builds. The release workflow currently produces an unsigned release APK because no signing key is stored in the repository. F-Droid performs its own build and signing after accepting the metadata submission; do not distribute the unsigned APK as an installable consumer release.

## Dependency and permission audit

The release report includes the resolved `releaseRuntimeClasspath` dependency tree and the permissions declared in `app/src/main/AndroidManifest.xml`. The release is blocked if precise location permission (`ACCESS_FINE_LOCATION`) is declared or approximate location permission (`ACCESS_COARSE_LOCATION`) is missing.

Review the dependency report for new libraries and their licenses before every public distribution. A dependency tree is an inventory, not a complete legal license-compliance report.

## F-Droid

The release workflow generates a version-specific metadata file as a release asset. Official F-Droid inclusion requires a separate submission to the F-Droid Data project on GitLab, successful metadata CI, review/acceptance by F-Droid maintainers, and a successful F-Droid build. Publishing a GitHub Release or generating YAML does not publish the app on F-Droid.

For a first-time app, F-Droid recommends opening a Request for Packaging (RFP). Otherwise, prepare `metadata/com.sunrisesunset.clock.yml` in a fork of `fdroiddata`, validate it with the F-Droid CI pipeline, and open a merge request. The GitHub mirror of fdroiddata is read-only; submissions are made through GitLab.

Before submission, confirm the application ID `com.sunrisesunset.clock` is acceptable under F-Droid's package-name policy and that the reverse-domain namespace is appropriate for the developer.

## Screenshots

Screenshots must be genuine captures from the running app on a physical Android device. Use the phone's native screenshot function. The setup image must show the actual setup screen; the widget image must show the widget placed on the real launcher with its sunrise and sunset values. Keep both images unaltered and free of private notifications or unrelated personal information.

The release workflow performs structural PNG checks (file signature, dimensions, file size, and distinct images). These checks cannot establish visual correctness or prove which device captured the image; a human must review both images before the release tag is pushed.

The app user is expected to grant **approximate location** (`ACCESS_COARSE_LOCATION`) when choosing **Use approximate location**, and to have Android location services enabled for a location fix. No other runtime permission is currently required. Test-only instrumentation permissions are not requested from installed app users.

## Legacy emulator diagnostics

`StoreScreenshotTest.kt` and `docs/EMULATOR_DIAGNOSTICS.md` describe the earlier emulator-based screenshot investigation. They are not part of the release workflow and must not be used to produce release screenshots.


## Android 13 physical-device validation

Before publishing store screenshots or creating a release tag, follow [Android 13 physical-device release validation](ANDROID_13_PHYSICAL_DEVICE_TEST.md) on a Samsung Galaxy A51 running Android 13 (API 33). The app keeps `targetSdk = 35`; this does not prevent installation or testing on Android 13. Do not use an emulator or AWS Device Farm for this release pass. Build and install the CI debug APK on the physical phone, complete the checklist, and capture the two original store screenshots using the phone's native screenshot function.
