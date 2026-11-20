"""Regression tests for AVD hardware-profile availability checks."""

import unittest
from pathlib import Path

from avd_profile_validation import parse_device_profile_ids, require_profile_exists


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

    def test_release_workflow_uses_committed_phone_screenshots_without_emulator(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertNotIn("reactivecircus/android-emulator-runner", workflow)
        self.assertNotRegex(workflow, r"(?m)^\\s*adb\\s")
        self.assertNotIn("Enable KVM", workflow)
        self.assertIn("Validate committed physical-device store screenshots", workflow)
        self.assertIn("01-app-setup.png", workflow)
        self.assertIn("02-home-screen-widget.png", workflow)

    def test_ci_workflows_do_not_pin_unverified_hardware_profiles(self):
        root = Path(__file__).resolve().parents[1]
        workflow_paths = [
            root / ".github/workflows/build-apk.yml",
            root / ".github/workflows/release.yml",
        ]
        for workflow in workflow_paths:
            with self.subTest(workflow=workflow.name):
                content = workflow.read_text(encoding="utf-8")
                self.assertNotRegex(
                    content,
                    r"(?m)^\s*profile\s*:",
                    "Do not add an AVD profile without a preflight existence check",
                )


if __name__ == "__main__":
    unittest.main()
