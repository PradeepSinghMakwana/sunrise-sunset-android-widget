# Emulator UI test diagnostics

This guide documents the evidence collected by the Android emulator workflow when UI screenshot tests fail. Keep this diagnostic path in place across future releases: emulator and launcher behavior can change independently of the app.

## What the workflow tests

The emulator UI tests must produce two **genuine screenshots**:

- `01-app-setup.png`: the running app setup screen, including the approximate-location action.
- `02-home-screen-widget.png`: the actual widget placed on the Android launcher's home screen, with its sunrise/sunset content visible.

The tests must not generate, draw, or substitute mock screenshots. A screenshot is accepted only after the corresponding UI has been found and verified.

## Evidence collected on failure

The workflow uploads the `Sunrise-Sunset-Clock-genuine-screenshots-and-diagnostics` artifact even when instrumentation tests fail. Depending on how far the test gets, it contains:

- Screenshots of the failing app, launcher, widget picker, or widget placement state.
- Text reports recording the explicit app launch result, resumed activity, focused window, and UI Automator hierarchy.
- Emulator properties, display size/density, Android activity and window dumps, and the installed app package details.
- A full Android `logcat` snapshot and the instrumentation exit code.
- All screenshots and UI hierarchy XML files saved by instrumentation in the app's external-files `screenshots` directory, retrieved by ADB from `/sdcard/Android/data/com.sunrisesunset.clock/files/screenshots/`.

The test writes screenshot and hierarchy files into the app's external-files directory. The host collector pulls that exact directory after instrumentation exits; this avoids relying on `run-as` and requires no storage permission. The test uses `UiDevice.dumpWindowHierarchy` instead of launching a second shell `uiautomator dump` inside the active UI Automator session. The workflow also attempts to capture the final emulator display. That final display capture is diagnostic evidence only; it is not a substitute for either required screenshot.

### Investigating a failed run

1. Open **Actions → Build Android APK** and select the failed run.
2. Download and extract `Sunrise-Sunset-Clock-genuine-screenshots-and-diagnostics`.
3. Check `diagnostics/instrumentation-exit-code.txt`, the relevant `*-debug.png` screenshots, and matching `*-debug.txt` reports first.
4. Check `diagnostics/activities.txt`, `diagnostics/windows.txt`, and `diagnostics/logcat.txt` to distinguish an app launch failure, launcher interaction failure, or emulator boot/display problem.
5. Fix the underlying cause. Do not make a test pass by skipping the UI check, accepting a blank screenshot, or fabricating an image.
6. Rerun the workflow and inspect both required screenshots. A green APK build alone does not mean screenshot validation passed.

The emulator UI suite runs for push, pull-request, and manually dispatched builds. Its screenshot and diagnostic artifact is uploaded even when the tests fail. This keeps real app and launcher evidence available for every build trigger.

## Permissions and privacy

### Permissions requested from an installed app user

The app declares only:

- `android.permission.ACCESS_COARSE_LOCATION` — Android runtime permission used to obtain an approximate location for sunrise/sunset calculations.

The app does **not** request precise location (`ACCESS_FINE_LOCATION`), background location, camera, microphone, contacts, phone, notification, or storage/media permissions. It does not need internet permission for its offline solar calculation.

Android displays its normal location permission prompt when the user taps **Use approximate location** and has not already granted the permission. The user can choose approximate location. If permission is denied, the app explains that approximate location is needed; sunrise/sunset cannot be calculated for the user's area until permission is granted. The user can change the permission later in Android Settings → Apps → Sunrise Sunset Clock → Permissions.

Location services must also be enabled and Android must be able to provide a network/passive location fix. The app may need the setup screen to remain open briefly while it waits. Coordinates are saved locally in app preferences for widget updates.

### Permissions used only by automated tests / CI

The GitHub Actions workflow runs tests on a disposable Android emulator. Its test harness uses Android instrumentation, UI Automator, and emulator ADB shell commands to launch the activity, inspect the launcher, take screenshots, and collect system logs. These are CI/test capabilities, not permissions requested from people installing the app.

The test seeds deterministic demo coordinates into the disposable emulator's app preferences so screenshots do not depend on a real location fix. Those coordinates are test data only; they are not release defaults and are not copied to a user's device.

No real user's location or device is accessed by the GitHub Actions emulator tests. The app's manifest must continue to declare coarse location only. Review any proposed manifest permission change before merging it, and update the privacy documentation and release permission audit if behavior changes.

## Handling diagnostic artifacts

Artifacts can contain Android system logs, package details, UI text, and screenshots. They are generated from a disposable CI emulator, not from a user's personal phone. Keep them within the repository's intended CI retention/access controls, and do not add real user logs, location coordinates, or personal screenshots to the repository.
