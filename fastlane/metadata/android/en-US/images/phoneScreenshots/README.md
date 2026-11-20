# Store screenshots

The CI release workflow captures these screenshots using the Android 13 (API 33) AOSP emulator and the actual running app:

- `01-app-setup.png`: the app setup screen with the approximate-location action visible.
- `02-home-screen-widget.png`: the Android Launcher3 home screen with Sunrise Sunset Clock placed and both SUNRISE and SUNSET labels visible.

The images are captured by UI Automator from the running application and launcher, not generated or composited. Intermediate screenshots and UI hierarchy XML files are uploaded as a separate CI artifact for diagnosis. Visually review the final PNGs before publishing a release.
