# Release process

## Normal release

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Update store listing text under `fastlane/metadata/android/en-US/` if the app description or features changed.
3. Run JVM unit tests and the Android 13 (API 33) emulator UI test. The release workflow does this automatically on the tagged source.
4. The Android 13 UI test captures:
   - `01-app-setup.png`: the actual app setup screen, with the setup action visible.
   - `02-home-screen-widget.png`: the actual Android 13 emulator launcher home screen with Sunrise Sunset Clock placed and both SUNRISE and SUNSET values visible.
5. Inspect the two PNGs in the release workflow artifact. The images are genuine emulator screenshots, not physical-device captures or generated mockups.
6. Commit and push the release-ready change to `main`.
7. Create and push a matching version tag, for example `v2.0`:
   ```sh
   git tag v2.0
   git push origin v2.0
   ```

The tag must exactly match `versionName`. The release workflow validates the version/tag and Android permissions, runs JVM tests and the Android 13 emulator UI test, validates the captured screenshot files, builds the release APK, records the resolved Gradle dependency tree, generates F-Droid metadata, packages the exact tagged source, and publishes the assets as a GitHub Release. Emulator screenshots are explicitly identified as emulator captures; they do not prove compatibility with every physical device or OEM launcher.

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

## Screenshot evidence

The two screenshot assets are captured by the real Android UI on an Android 13 (API 33) emulator. The tests must verify that the setup action is visible and that the launcher widget is actually placed, settled, unclipped, and displaying both sunrise and sunset values before screenshots are accepted. Blank screens, placement placeholders, resize handles, and generated/composited images are not acceptable.

The release workflow also checks PNG signatures, dimensions, file size, and distinct hashes. These structural checks supplement, but do not replace, the UI assertions. Review the screenshots visually from the release run artifact. Clearly identify the images as Android 13 emulator screenshots wherever their origin is described.

Emulator screenshots are suitable for this project's automated release evidence under this policy, but they do not establish compatibility with all physical devices, launchers, screen sizes, or OEM customizations. Physical-device testing remains useful additional coverage, not a release prerequisite.

The app user is expected to grant **approximate location** (`ACCESS_COARSE_LOCATION`) when choosing **Use approximate location**, and to have Android location services enabled for a location fix. No other runtime permission is currently required. Test-only instrumentation permissions are not requested from installed app users.

## Android 13 emulator validation

See [Android 13 emulator validation](ANDROID_13_PHYSICAL_DEVICE_TEST.md) for the API 33 AOSP image, runner configuration, deterministic test timezone, screenshot checkpoints, and evidence requirements.
