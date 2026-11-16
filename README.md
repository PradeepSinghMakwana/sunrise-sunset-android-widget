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

The troubleshooting included approximately 14 technical commits focused on emulator tests, launcher-widget screenshot capture, CI behaviour, and diagnostics. Several manual emulator workflow attempts failed or were cancelled while the tests were being developed. Ordinary push builds were configured to skip the emulator tests; emulator testing is reserved for pull requests and manual workflow runs.

### Cost assumptions

- No paid cloud VM or physical test device was used for the GitHub Actions emulator runs described in this assessment.
- GitHub Actions usage for this public repository was treated as **₹0 direct runner cost**, assuming standard public-repository hosted-runner eligibility and no separately billed services.
- Mobile data usage and any other indirect costs were not measured.
- Labour has no recorded cash payment. For opportunity-cost illustration only, valuing the 3.18-hour elapsed window at ₹300, ₹500, or ₹1,000 per hour gives approximately ₹955, ₹1,590, or ₹3,180 respectively. These are hypothetical valuations, not actual expenditure.

### Expected return

The intended return is repeatable, automated evidence that the app setup screen and the real home-screen widget render correctly, reducing manual release checks and helping catch regressions.

If the automation eventually saves 15–30 minutes per release, it would recover an estimated 3.18-hour effort after roughly 7–13 releases. This is a planning scenario, not a measured saving. The calculation excludes the additional value of catching defects before release.

**Acceptance status at this assessment:** the required genuine app-setup and launcher-widget screenshots had not yet both been successfully captured and visually verified. Therefore, the automation's delivery ROI was not yet demonstrated. Do not treat the estimated future savings as realised benefits. Update this section when the screenshot tests pass reliably and actual CI usage or time savings can be measured.

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
