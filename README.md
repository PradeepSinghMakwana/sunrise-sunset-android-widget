# Indore Sun Clock — Android home-screen widget

A native Android App Widget with:
- Live digital clock (12-hour display on the widget; Android may use 24-hour if configured)
- Daily sunrise and sunset for Indore, Madhya Pradesh
- Asia/Kolkata time zone
- Offline solar-time calculation; no location permission or API key

## Build an APK using GitHub Actions (phone-friendly route)
1. Create a GitHub repository and upload the contents of this folder to its `main` branch.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and tap **Run workflow** (or wait for the first push build).
4. Open the completed workflow run, find **Artifacts**, and download `Indore-Sun-Clock-debug`.
5. Extract the downloaded artifact ZIP and install `app-debug.apk`. Android may ask you to allow installs from that browser/file manager.

## Add the widget
1. Long-press an empty area on the Android home screen.
2. Choose **Widgets**.
3. Find **Indore Sun Clock** and drag it onto the home screen.
4. Resize it if desired.

## Notes
- Sunrise/sunset use a standard solar-position approximation and can differ by roughly a minute or two from published local almanacs.
- The clock itself updates live through Android's `TextClock`.
- Sunrise/sunset refresh when Android updates the widget (at least every 30 minutes) and when the date/time/time zone changes.
- Default coordinates are fixed to central Indore. This build intentionally does not request GPS permission.
