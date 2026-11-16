# Sunrise Sunset Clock — Android home-screen widget

A native Android widget featuring:
- A live digital clock using the device's local time zone.
- Daily sunrise and sunset calculated for the device's approximate location.
- Approximate location permission only (`ACCESS_COARSE_LOCATION`).
- Offline solar-time calculation; no API key or external location service required by the app.

Built with ChatGPT, guided by the repository owner.

## Build with GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Build Android APK** and run the workflow.
3. Download the `Sunrise-Sunset-Clock-debug` artifact.
4. Extract and install `app-debug.apk`.

## Setup
1. Open Sunrise Sunset Clock.
2. Allow approximate location when Android asks.
3. Keep the app open briefly while it obtains a location fix.
4. Add the Sunrise Sunset Clock widget from your launcher's Widgets screen.

Tap the widget to reopen setup.

## Privacy
The app requests approximate location only. It does not declare or request precise location permission. Coordinates are stored locally on the device and used for the sunrise/sunset calculation.

## Notes
Sunrise and sunset use a NOAA-style approximation and may differ slightly from published almanacs. The clock follows the device's current time zone. The widget refreshes periodically and when Android reports date, time, or time-zone changes.

## Engineering effort and automation ROI

**Assessment snapshot: 16 November 2026.** The emulator screenshot automation and its diagnostics required a concentrated troubleshooting effort. The relevant work spanned approximately 3 hours 11 minutes of elapsed clock time (about 04:25–07:36 UTC). This is a wall-clock window across multiple work and CI events, **not a measured timesheet or 3 hours 11 minutes of continuous hands-on work**.

The troubleshooting included approximately 14 technical commits focused on emulator tests, launcher-widget screenshot capture, CI behaviour, and diagnostics. Several manual emulator workflow attempts failed or were cancelled while the tests were being developed. At the time of this assessment, ordinary push builds skipped emulator tests. The workflow has since been changed to run screenshot validation for push, pull-request, and manual runs.

### Cost assumptions

- No paid cloud VM or physical test device was used for the GitHub Actions emulator runs described in this assessment.
- GitHub Actions usage for this public repository was treated as **₹0 direct runner cost**, assuming standard public-repository hosted-runner eligibility and no separately billed services.
- Mobile data usage and any other indirect costs were not measured.
- Labour has no recorded cash payment. For opportunity-cost illustration only, valuing the 3.18-hour elapsed window at ₹300, ₹500, or ₹1,000 per hour gives approximately ₹955, ₹1,590, or ₹3,180 respectively. These are hypothetical valuations, not actual expenditure.

### Expected return

The intended return is repeatable, automated evidence that the app setup screen and the real home-screen widget render correctly, reducing manual release checks and helping catch regressions.

If the automation eventually saves 15–30 minutes per release, it would recover an estimated 3.18-hour effort after roughly 7–13 releases. This is a planning scenario, not a measured saving. The calculation excludes the additional value of catching defects before release.

**Acceptance status at this assessment:** the required genuine app-setup and launcher-widget screenshots had not yet both been successfully captured and visually verified. Therefore, the automation's delivery ROI was not yet demonstrated. Do not treat the estimated future savings as realised benefits. Update this section when the screenshot tests pass reliably and actual CI usage or time savings can be measured.

## Emulator CI architecture and review findings

The emulator screenshot workflow is designed to validate the real app setup screen and the real home-screen widget on a Pixel 5 API 34 emulator, while preserving diagnostics when a test fails.

### Workflow sequence

1. GitHub Actions builds the debug APK and prepares the GitHub Actions artifact runtime.
2. The emulator runner boots the Pixel 5 image, waits for Android to finish booting, verifies the user is unlocked, and dismisses the keyguard.
3. The host-side script captures and uploads these early checkpoints as separate artifacts:
   - `00-emulator-boot-screen.png`
   - `00b-emulator-after-wake-and-dismiss-1s.png`
   - `01-app-setup-after-1s.png`
   - `02-home-screen-before-widget-after-1s.png`
4. The connected UI tests capture the actual app setup screen and the actual launcher widget, and assert that the expected screen content is present.
5. The exit handler collects test screenshots and UI hierarchies from the emulator, plus ADB/device, package, activity, window, logcat, and test-report diagnostics. The final emulator screen is diagnostic evidence only; it is not a substitute for either required app screenshot.
6. The workflow uploads the collected screenshots/diagnostics and debug APK even when the emulator test step fails.

### Screenshot evidence and failure handling

- The required genuine UI captures are `01-app-setup.png` and `02-home-screen-widget.png`. A successful test run must produce both; a blank final emulator capture does not satisfy either requirement.
- The four early checkpoints are uploaded live during the emulator run. Screenshots produced later by the UI tests are collected into the final workflow artifact after the test process exits; they are not currently streamed as individual live uploads.
- A failure to upload an individual live checkpoint is logged but does not stop the UI test run. The final artifact collection remains the recovery path for diagnostics.
- The UI tests use the app's external-files screenshot directory so the host can retrieve files with ADB. Keep the test output path and the host collection path in sync if either is changed.
- Keep host-side ADB diagnostics separate from the active UI Automator session. Invoking a second `uiautomator dump` from inside an active UI Automator test can fail with `UiAutomationService already registered`.

### Review scope

The emulator-isolation branch history was reviewed across all 33 commits, including the workflow, emulator runner script, live artifact uploader, screenshot UI tests, app setup activity, build configuration, README, and emulator diagnostics guide. The history shows the progression from basic emulator execution to boot/unlock checks, Pixel 5 coverage, genuine launcher-widget capture, pre-assertion checkpoints, live artifact uploads, and end-of-run diagnostic collection.

This section records the current implementation and its known boundaries; it is not a claim that every historical CI run passed. Keep the acceptance criteria in [Emulator UI test diagnostics](docs/EMULATOR_DIAGNOSTICS.md) authoritative when changing screenshot tests or collection behaviour.

## License

This project is licensed under the MIT License.
See the [LICENSE](LICENSE) file for details.

## Releases

Tagged releases use the automated GitHub Actions pipeline. See [Release process](docs/RELEASE_PROCESS.md) for versioning, Gradle build instructions, dependency and permission audits, F-Droid metadata generation, source archives, and store screenshot requirements. See [Emulator UI test diagnostics](docs/EMULATOR_DIAGNOSTICS.md) for failure investigation, CI evidence, and the distinction between app permissions and test-only emulator access.

Push a tag matching the app's `versionName` (for example, `v2.1`) to start a release. The workflow publishes a GitHub Release with the APK, tagged source archive, and audit reports.
## AWS Spot test infrastructure

The repository has a separate, manually triggered **Verify AWS GitHub OIDC** workflow. It checks that GitHub Actions can assume the AWS IAM role without storing long-lived AWS access keys in GitHub, and confirms the configured region is **US East (Ohio) (`us-east-2`)**.

- AWS account: `447001318269`
- IAM role: `GitHub-Sunrise-Sunset-EC2-Spot`
- GitHub branch allowed by the current IAM trust policy: `feature/emulator-ci-isolation`
- Workflow: [Verify AWS GitHub OIDC](.github/workflows/aws-oidc-check.yml)

To run it, open **Actions → Verify AWS GitHub OIDC → Run workflow** and select `feature/emulator-ci-isolation`. A successful run confirms authentication only; it does **not** launch an EC2 instance or incur EC2 compute charges. The Android build and emulator screenshot workflow remains separate.
