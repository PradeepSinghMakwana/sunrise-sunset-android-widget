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
Sunrise and sunset are calculated using an approximation and may differ slightly from published almanacs. The clock follows the device's current time zone. The widget refreshes periodically and when Android reports date, time, or time-zone changes.

## Sunrise and sunset calculation tests

The following cases should be tested to check that sunrise and sunset are calculated correctly. This list describes what we expect to test; it does **not** mean that all these tests have already passed.

- **Seasonal changes:** Test dates around the March and September equinoxes and the June and December solstices. Check that daylight changes in the expected direction in both hemispheres.
- **Different places:** Test places near the equator and at middle and high latitudes. Include places east and west of the prime meridian. Check that location affects the calculated times and length of the day as expected.
- **Both hemispheres:** Test places in the Northern and Southern Hemispheres. Check that their seasons have opposite patterns.
- **Polar day and night:** Test places and dates where the Sun does not rise or does not set. Show this clearly instead of displaying a made-up sunrise or sunset time.
- **Date changes:** Test events close to midnight. Check that sunrise and sunset appear on the correct date and are not moved to the day before or after.
- **Incorrect location values:** Test missing location values and values outside the valid ranges: latitude from -90 to 90 degrees and longitude from -180 to 180 degrees. The app should handle these safely and must not use an invented location.
- **Time zones:** Check that the same sunrise or sunset is shown at the correct local time. Include daylight-saving changes and cases where the local date is different from the date in UTC.

### What counts as tested

- Add automated tests for every case listed above.
- Tests must check the expected result, not just run the calculation.
- For normal locations, compare results with a trusted sunrise/sunset source. Allow for the small differences expected from an approximate calculation.
- For polar locations, check that the app correctly reports when the Sun does not rise or does not set.
- Show test results in GitHub Actions. Do not say a case has passed until its test has run successfully.
- Measure how much of the calculation code is covered by tests, but remember that a high coverage number alone does not prove the results are correct.
- Run the relevant tests again whenever the calculation, date handling, or time-zone handling changes.

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

## Branch synchronization and integration policy

**`main` is the source of truth.** Feature branches are short-lived workspaces, not independent long-term lines of development. They may differ while work is in progress, but they must be brought back into alignment regularly.

### Required workflow for every feature branch

1. **Start from current `main`.** Before integrating or continuing substantial feature work, merge the latest `main` into that feature branch.
2. **Preserve both sides.** Resolve merge conflicts by understanding the intent of each change. Do not blindly choose `main` or the feature branch, discard working behaviour, or replace one implementation with another merely to silence a conflict. Keep both intended changes and add or update tests where their interaction requires it.
3. **Run that branch's independent CI checks.** Run all checks assigned to that branch and let them report independently. A failure in one job must not prevent unrelated jobs from running or hide their results. Fix real failures; do not weaken assertions, disable checks, add broad suppressions, or create baselines to obtain a green result.
4. **Verify before integration.** Review the diff, confirm the intended behaviour is retained, and require the relevant CI checks to pass. Do not merge an unverified feature branch into `main`.
5. **Merge feature branch into `main`.** Once verified, integrate it and confirm the resulting `main` build/checks.
6. **Repeat from the newly updated `main`.** Before integrating the next feature branch, merge that latest `main` back into it, resolve conflicts while preserving intent, run its checks, and only then integrate it.

Keep branches synchronized frequently. Do not allow a branch to accumulate a large behind-count or drift for weeks. If two branches touch the same code, coordinate their order and reconcile them early. Branch-specific CI scope may differ where intentional (for example, a unit-test branch need not run emulator UI tests), but the checks that do apply must remain independent.

### Integration queue snapshot — 17 November 2026

The counts below compare each named branch with `main` at the time this section was written. They are a snapshot, not a substitute for checking the live branch state before each merge.

| Branch / PR | Difference from `main` | Known work / action |
|---|---:|---|
| `feature/emulator-ci-isolation` (#8) | 54 ahead, 0 behind | Contains the MVVM/Compose location-setup refactor and emulator screenshot automation. Detekt had a maximum-line-length failure; emulator CI stopped because the emulator remained locked. Fix both and rerun the independent checks before integration. |
| `feature/unit-tests` (#10) | 55 ahead, 0 behind | Contains JVM solar-calculation tests and four independent CI jobs. It does not yet contain the MVVM location-setup refactor. Keep emulator UI testing out of this branch's CI. Reconcile the MVVM implementation with this branch before it is integrated so `main` does not regress. |
| `docs/solar-calculation-acceptance` (#9) | 2 ahead, 0 behind | Documentation-only changes. Review the documentation diff and resolve its reported CI failure before integration. |
| `feature/widget-runtime-fix` (#6) | 1 ahead, 44 behind | Diverged substantially. Do not merge as-is. First merge current `main` into this branch, resolve conflicts while preserving the widget-runtime fix, and run its relevant checks. |
| `feature/solar-ui-test-parity` (no open PR at snapshot) | 11 ahead, 0 behind | Review the branch's UI-test changes, bring it up to date with `main`, run the relevant checks, and open or update a PR before integration. |

**Important:** The queue is not a statement that any branch is already approved to merge. The current PR head, live comparison, conflict state, and latest CI results must be rechecked immediately before every integration. Merge one verified branch at a time, then use the resulting `main` as the base for the next branch.

