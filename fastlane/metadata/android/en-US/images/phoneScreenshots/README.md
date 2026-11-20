# Store screenshots

Release screenshots must be genuine captures from a physical Android phone. The Android 13 emulator artifact is for CI diagnostics only and must never be copied into store metadata or represented as a physical-device capture.

Commit these reviewed physical-device images before tagging a release:

- `01-app-setup.png`: the actual app setup screen with the approximate-location action visible.
- `02-home-screen-widget.png`: the actual launcher home screen with the widget placed, placement handles dismissed, and both local sunrise/sunset times visible.

Review the images visually on the target device. CI emulator screenshots and generated/composited images are not acceptable substitutes.
