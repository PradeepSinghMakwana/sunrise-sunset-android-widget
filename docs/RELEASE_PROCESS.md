# Release process

## Normal release

1. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
2. Update store listing text under `fastlane/metadata/android/en-US/` if the app description or features changed.
3. Add genuine current screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
4. Commit and push the release-ready change to `main`.
5. Create and push a matching version tag, for example `v2.1`:
   ```sh
   git tag v2.1
   git push origin v2.1
   ```

The tag must exactly match `versionName`. The release workflow then audits the version and Android permissions, builds the release APK, records the resolved Gradle dependency tree, generates version-specific F-Droid metadata, packages the exact tagged source, checks for store screenshots, and publishes the assets as a GitHub Release.

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

The GitHub Actions workflows install JDK 17 and Gradle 8.9 automatically. The debug workflow is for installable test builds. The release workflow produces an unsigned release APK because no signing key is stored in the repository.

## Dependency and permission audit

The release report includes the resolved `releaseRuntimeClasspath` dependency tree and the permissions declared in `app/src/main/AndroidManifest.xml`. The release is blocked if precise location permission (`ACCESS_FINE_LOCATION`) is declared or approximate location permission (`ACCESS_COARSE_LOCATION`) is missing.

Review the dependency report for new libraries and their licenses before every public distribution. A dependency tree is an inventory, not a complete legal license-compliance report.

## F-Droid

The release workflow generates a version-specific metadata file as a release asset. Official F-Droid inclusion still requires a submission/merge in the separate `fdroiddata` repository and a successful F-Droid build. The metadata template in `fdroid/metadata/` is a starting point and must be validated against the current F-Droid metadata schema before submission.

The current application ID `com.sunrisesunset.clock` must also be checked for F-Droid package-name policy and developer domain ownership before submission.

## Screenshots

Screenshots must be genuine captures of the running application. Add them to the Fastlane screenshots directory. The release workflow reports whether screenshot files are present; it does not fabricate or synthesize app screenshots.
