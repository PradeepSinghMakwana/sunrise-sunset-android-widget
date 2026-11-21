"""Regression tests for AVD hardware-profile availability checks."""

import os
import tempfile
import unittest
from unittest import mock
from pathlib import Path

from avd_profile_validation import find_avdmanager, parse_device_profile_ids, require_profile_exists


SAMPLE_AVDMANAGER_OUTPUT = """
Available devices definitions:
id: 0 or "pixel_7_pro"
    Name: Pixel 7 Pro
    OEM: Google
id: 1 or "Nexus 5X"
    Name: Nexus 5X
"""


class AvdProfileValidationTest(unittest.TestCase):
    def test_parses_exact_profile_ids(self):
        self.assertEqual(
            {"pixel_7_pro", "Nexus 5X"},
            parse_device_profile_ids(SAMPLE_AVDMANAGER_OUTPUT),
        )

    def test_accepts_profile_only_when_exact_id_is_listed(self):
        require_profile_exists("pixel_7_pro", SAMPLE_AVDMANAGER_OUTPUT)

    def test_rejects_missing_pixel_2_profile(self):
        with self.assertRaisesRegex(ValueError, "Pixel_2.*unavailable"):
            require_profile_exists("Pixel_2", SAMPLE_AVDMANAGER_OUTPUT)

    def test_does_not_accept_case_insensitive_or_partial_matches(self):
        with self.assertRaises(ValueError):
            require_profile_exists("PIXEL_7_PRO", SAMPLE_AVDMANAGER_OUTPUT)
        with self.assertRaises(ValueError):
            require_profile_exists("pixel_7", SAMPLE_AVDMANAGER_OUTPUT)

    def test_rejects_empty_or_unrecognized_avdmanager_output(self):
        with self.assertRaisesRegex(ValueError, r"\(none found\)"):
            require_profile_exists("Pixel_2", "No device definitions found")


    def test_resolves_avdmanager_from_android_sdk_cmdline_tools_latest(self):
        with tempfile.TemporaryDirectory() as temp:
            executable = Path(temp) / "cmdline-tools" / "latest" / "bin" / "avdmanager"
            executable.parent.mkdir(parents=True)
            executable.write_text("#!/bin/sh\\nexit 0\\n", encoding="utf-8")
            with mock.patch("avd_profile_validation.shutil.which", return_value=None):
                with mock.patch.dict(os.environ, {"ANDROID_HOME": temp, "ANDROID_SDK_ROOT": ""}):
                    self.assertEqual(str(executable), find_avdmanager())

    def test_fails_with_actionable_message_when_avdmanager_missing(self):
        with tempfile.TemporaryDirectory() as temp:
            with mock.patch("avd_profile_validation.shutil.which", return_value=None):
                with mock.patch.dict(os.environ, {"ANDROID_HOME": temp, "ANDROID_SDK_ROOT": ""}):
                    with self.assertRaisesRegex(FileNotFoundError, "ANDROID_HOME"):
                        find_avdmanager()

    def test_release_workflow_captures_and_publishes_android_13_emulator_screenshots(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("reactivecircus/android-emulator-runner@v2", workflow)
        self.assertIn("api-level: 33", workflow)
        self.assertIn("Enable KVM", workflow)
        self.assertIn("script: bash scripts/run-android-ui-tests.sh", workflow)
        self.assertIn("Validate Android 13 emulator screenshots", workflow)
        self.assertIn("android-13-screenshots/01-app-setup.png", workflow)
        self.assertIn("android-13-screenshots/02-home-screen-widget.png", workflow)
        self.assertIn("Android-13-emulator-release-screenshots-and-diagnostics", workflow)
        self.assertNotIn("physical-device", workflow.lower())

    def test_ci_workflows_preflight_and_pin_the_non_pixel_nexus_profile(self):
        root = Path(__file__).resolve().parents[1]
        workflow_paths = [
            root / ".github/workflows/build-apk.yml",
            root / ".github/workflows/release.yml",
        ]
        for workflow in workflow_paths:
            with self.subTest(workflow=workflow.name):
                content = workflow.read_text(encoding="utf-8")
                self.assertIn("avd_profile_validation.py 'Nexus 5X'", content)
                self.assertRegex(content, r"(?m)^\s*profile: Nexus 5X$")
                self.assertNotIn("profile: Pixel", content)


if __name__ == "__main__":
    unittest.main()
