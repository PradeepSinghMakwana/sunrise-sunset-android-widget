# Sunrise Sunset Clock — Android home-screen widget

A native Android home-screen widget featuring:

- A live digital clock that follows the device's local time zone.
- Daily sunrise and sunset calculated from the device's approximate location.
- Approximate location permission only (`ACCESS_COARSE_LOCATION`).
- Offline solar-time calculation; no API key or external location service is required.

Built with ChatGPT, guided by the repository owner.

## Build a debug APK

1. Open the repository's **Actions** tab.
2. Select **Build Android APK** and run the workflow (or download the artifact from a completed run).
3. Download the `Sunrise-Sunset-Clock-debug` artifact.
4. Extract and install `app-debug.apk`.

You can also build locally with Java 17 and Gradle 8.9:

```bash
gradle :app:assembleDebug
```

## Setup

1. Open Sunrise Sunset Clock.
2. Allow approximate location when Android asks.
3. Keep the app open briefly while it obtains a location fix.
4. Add the Sunrise Sunset Clock widget from your launcher's Widgets screen.

Tap the widget to reopen setup.

## Privacy

The app requests approximate location only. It does not declare or request precise location permission. Coordinates are stored locally on the device and used for sunrise/sunset calculations.

## Solar times and time zones

Sunrise and sunset are calculated offline using a NOAA-style approximation. Results can differ slightly from published almanacs.

The widget displays solar times in the device's current time zone. The location coordinates determine the solar events; the device time zone determines how those events are displayed as local clock times. The Android 13 CI UI-test emulator uses the `America/Los_Angeles` time zone for its California test coordinates, configured before tests run. **Those CI test coordinates and times are test fixtures, not the user's live location or a guarantee of local accuracy.**

The widget refreshes periodically and when Android reports date, time, or time-zone changes.

## CI checks

Pull requests and pushes to `main` run independent checks:

- **Shell validation:** release-script syntax and script regression tests.
- **JVM unit tests:** solar calculations, location validation, and other app logic.
- **Detekt:** Kotlin static analysis.
- **Android lint:** Android-specific static checks.
- **Debug APK build:** assembles and uploads the `app-debug.apk` artifact.
- **Android 13 (API 33) UI tests:** runs instrumented tests on an emulator after JVM unit tests pass. The runner sets the test time zone to `America/Los_Angeles` and collects screenshots, UI hierarchy XML, app-widget dumps, and logcat diagnostics as artifacts.

The same Android 13 emulator UI test runs during release creation. The two published app screenshots are genuine captures of the running emulator UI, explicitly identified as emulator screenshots. They are not physical-device captures and do not prove behavior on every OEM launcher or physical device.

## Kotlin code-quality workflow

For Kotlin code changes, run Detekt before Android lint:

1. Run `gradle :app:detekt`.
2. Fix every finding without weakening rules, adding a baseline, or applying blanket suppressions.
3. Rerun Detekt until it passes.
4. Then run `gradle :app:lintDebug` and fix lint findings.

Keep Detekt and Android lint CI jobs independent so both report results even when one fails.

## Releases and screenshots

Tagged releases use the automated GitHub Actions pipeline. See [Release process](docs/RELEASE_PROCESS.md) for versioning, Gradle build instructions, dependency and permission audits, F-Droid metadata generation, source archives, and screenshot requirements. See [Emulator UI test diagnostics](docs/EMULATOR_DIAGNOSTICS.md) for investigation steps and the distinction between app permissions and test-only emulator access.

Every release requires two genuine Android 13 emulator UI screenshots:

- `01-app-setup.png` — the actual app setup screen.
- `02-home-screen-widget.png` — the actual launcher home screen with the widget and sunrise/sunset values visible.

The release workflow captures these from the emulator during the tagged run, checks their basic PNG structure, and publishes each PNG as a separate release asset. Their origin is documented as emulator captures, never as physical-device screenshots. Do not push a release tag until the Android 13 UI test and screenshot validation pass.

Push a tag matching the app's `versionName` (for example, `v2.0`) to start a release. The workflow publishes a GitHub Release with the APK, tagged source archive, screenshots, and audit reports. F-Droid publication still requires its official submission and maintainer review; generated metadata alone does not mean the app is published.

## Branch integration policy

`main` is the source of truth. Before integrating a feature branch, bring it up to date with `main`, resolve conflicts while preserving the intent of both changes, run applicable CI checks, review the diff, and merge only after verification. After each merge, use the updated `main` as the base for the next branch. Check live branch/PR status rather than relying on a dated queue snapshot.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
