# Store screenshots

The release pipeline uses genuine screenshots captured by the automated Android 13 (API 33) emulator UI test. These are emulator captures, not physical-device photographs, and must be described as such in release notes and documentation.

The release workflow runs the same instrumented UI test used by CI and validates the screenshots produced during that run. It does not require manually committed PNG files in this directory.

The two published screenshot assets are:
- `01-app-setup.png`: the actual running app setup screen with the approximate-location action visible.
- `02-home-screen-widget.png`: the actual Android 13 emulator launcher home screen with the widget placed, placement handles dismissed, and both local sunrise/sunset times visible.

Screenshots must come directly from the running emulator UI. Do not crop, composite, generate, or fabricate them. Review the CI/release artifact visually when diagnosing failures. Emulator captures demonstrate the tested AOSP Android 13 configuration; they do not establish behavior on every physical device or OEM launcher.
