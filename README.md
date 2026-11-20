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

The troubleshooting included approximately 14 technical commits focused on emulator tests, launcher-widget screenshot capture, CI behaviour, and diagnostics. Several manual emulator workflow attempts failed or were cancelled while the tests were being developed. Ordinary push builds run lightweight checks; emulator screenshot validation is reserved for pull requests and manual runs.

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

### Live artifact upload design and commit history

Live checkpoint upload is an intentional feature of this workflow. It is not interchangeable with the final `actions/upload-artifact@v4` step.

The implementation was introduced in these historical commits:

| Commit | Responsibility |
|---|---|
| `ff79a90` | Added the live uploader based on `@actions/artifact`. |
| `9d8b823` | Added calls from the emulator runner to upload checkpoints while the emulator job is running. |
| `ed49d24` | Installed the `@actions/artifact` dependency in the workflow. |
| `24cf227` | Exported the GitHub Actions artifact runtime URL and token for the uploader process. |

The current implementation has three cooperating parts:

1. **Workflow setup** — `.github/workflows/build-apk.yml` installs the pinned `@actions/artifact@2.3.2` package and exports `ACTIONS_RESULTS_URL`, `ACTIONS_RUNTIME_URL`, and `ACTIONS_RUNTIME_TOKEN` using `actions/github-script@v7`. These runtime credentials let the Node uploader publish artifacts during the still-running job.
2. **Checkpoint capture and invocation** — `scripts/run-emulator-screenshot-tests.sh` captures host-side emulator screenshots with `adb exec-out screencap -p` and calls `scripts/upload-screenshot-artifact.cjs` immediately after each selected checkpoint.
3. **Artifact creation** — `scripts/upload-screenshot-artifact.cjs` uses `DefaultArtifactClient` to create a separately named artifact for each checkpoint. Names use the `pixel5-` prefix plus a sanitized checkpoint name. Each live artifact has a seven-day retention period and compression level zero; the uploader logs its artifact ID and byte size.

The currently wired live checkpoints are:

| Checkpoint | Capture point |
|---|---|
| `00-emulator-boot-screen.png` | Immediately after the emulator reports boot complete, before waiting for unlock. |
| `00b-emulator-after-wake-and-dismiss-1s.png` | After the user is unlocked, keyguard is dismissed, and the one-second pause completes. |
| `01-app-setup-after-1s.png` | After installing and explicitly launching the real setup activity, followed by one second. |
| `02-home-screen-before-widget-after-1s.png` | After returning to the launcher before widget placement, followed by one second. |

These are four **separate live artifacts**, uploaded during the emulator job. They provide time-ordered evidence even if later UI automation fails or the runner is interrupted. A checkpoint is uploaded only when its PNG exists and is non-empty. If an individual live upload fails, the script reports the error and continues the UI tests; the failure is not silently treated as a successful upload.

### Live uploads versus final artifacts

There are two distinct evidence-delivery paths, and both must be retained:

- **Live checkpoint artifacts:** uploaded during emulator execution by the Node uploader. They are independent artifacts and can be inspected before the job finishes.
- **Final diagnostic artifact:** uploaded by `actions/upload-artifact@v4` with `if: always()` after the emulator step. It contains the collected screenshot directory, test captures, reports, and diagnostics, including evidence generated later in the test sequence.
- **APK artifact:** also uploaded with `if: always()`; this is separate from screenshot evidence.

The UI test checkpoints (setup ready, launcher before widget, launcher edit menu, widget picker, widget preview, and widget after placement) are captured by `StoreScreenshotTest.kt`. They are collected into the final diagnostic artifact after instrumentation exits; the current runner does not individually stream each of those later test captures as a live artifact. The final emulator display image is diagnostic only and must never be substituted for the genuine app setup or placed-widget screenshots.

Do not remove the live uploader, runtime credential export, checkpoint calls, or the final always-run artifact upload as if they were duplicate mechanisms. They serve different recovery and observability purposes.

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


## Kotlin code-quality workflow

For every Kotlin code change, **fix Detekt before working on Android lint**:

1. Run `gradle :app:detekt`.
2. Resolve every Detekt finding in source; do not weaken rules, add a baseline, or use blanket suppressions to hide findings.
3. Rerun Detekt and repeat until it passes.
4. Then run `gradle :app:lintDebug` and resolve any lint findings.

Detekt, Android lint, JVM unit tests, APK compilation, and emulator instrumentation run as independent CI jobs. A failure in one job does not skip the others. The same checks run for pull requests targeting `main`, and Detekt runs again on pushes to `main`.


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

