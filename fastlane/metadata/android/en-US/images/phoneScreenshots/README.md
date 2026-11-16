# Store screenshots

The Android emulator UI test captures genuine screenshots from the running app and Android launcher. The release workflow stores them here and attaches them to the GitHub Release.

Automated captures:
- `01-app-setup.png`: the actual app setup screen rendered by Android on an emulator.
- `02-home-screen-widget.png`: the Sunrise Sunset Clock widget placed on the emulator launcher home screen. The test verifies the widget's SUNRISE and SUNSET labels before saving the image.

The widget test seeds demo coordinates only in the disposable emulator test profile. It does not add a default location to the app or change the user's saved location. Review screenshots before store submission.
