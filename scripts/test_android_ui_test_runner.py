"""Regression tests for the Android emulator test/diagnostic runner."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "run-android-ui-tests.sh"
WORKFLOW = ROOT / ".github" / "workflows" / "build-apk.yml"


class AndroidUiTestRunnerTest(unittest.TestCase):
    def _run_with_stubs(self, gradle_status, evidence_mode="missing", timezone="PST-0800"):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            calls = temp / "adb-calls.txt"
            bin_dir = temp / "bin"
            bin_dir.mkdir()

            gradle = bin_dir / "gradle"
            gradle.write_text(
                "#!/usr/bin/env bash\nexit \"$GRADLE_TEST_EXIT\"\n",
                encoding="utf-8",
            )
            adb = bin_dir / "adb"
            adb.write_text(
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' \"$*\" >> \"$ADB_CALLS\"\n"
                "if [[ \"$*\" == \"shell date +%Z%z\" ]]; then echo \"$DEVICE_TIMEZONE\"; fi\n"
                "if [[ \"$*\" == \"shell wm size\" ]]; then echo \"Override size: 1080x2400\"; fi\n"
                "if [[ \"$*\" == \"shell wm density\" ]]; then echo \"Override density: 420\"; fi\n"
                "exit 0\n",
                encoding="utf-8",
            )
            gradle.chmod(0o755)
            adb.chmod(0o755)

            screenshots = [
                "01-app-setup.png",
                "02-launcher-before.png",
                "02-home-screen-widget.png",
                "04-app-setup-ready.png",
                "05-launcher-edit-menu.png",
                "06-widget-picker-open.png",
                "07-widget-row-selected.png",
                "08-widget-after-placement.png",
            ]
            hierarchies = [
                "01-app-setup.xml",
                "02-launcher-before.xml",
                "02-home-screen-widget.xml",
                "04-app-setup-ready.xml",
                "05-launcher-edit-menu.xml",
                "06-widget-picker-open.xml",
                "07-widget-row-selected.xml",
                "08-widget-after-placement.xml",
            ]
            if evidence_mode in ("screenshots", "complete"):
                evidence_dir = temp / "android-13-screenshots"
                evidence_dir.mkdir()
                for name in screenshots:
                    (evidence_dir / name).write_text("screenshot", encoding="utf-8")
            if evidence_mode == "complete":
                evidence_dir = temp / "android-13-screenshots"
                for name in hierarchies:
                    (evidence_dir / name).write_text("hierarchy", encoding="utf-8")

            env = os.environ.copy()
            env.update({
                "PATH": f"{bin_dir}{os.pathsep}{env['PATH']}",
                "GRADLE_TEST_EXIT": str(gradle_status),
                "ADB_CALLS": str(calls),
                "DEVICE_TIMEZONE": timezone,
            })
            result = subprocess.run(
                ["bash", str(RUNNER)],
                cwd=temp,
                env=env,
                capture_output=True,
                text=True,
                check=False,
            )
            return result, calls.read_text(encoding="utf-8").splitlines()

    def test_preserves_gradle_failure_after_collecting_all_diagnostics(self):
        result, adb_calls = self._run_with_stubs(7, evidence_mode="complete")
        self.assertEqual(7, result.returncode)
        self.assertEqual(
            [
                "shell settings put global auto_time_zone 0",
                "shell cmd alarm set-timezone America/Los_Angeles",
                "shell date +%Z%z",
                "shell am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone America/Los_Angeles",
                "shell wm size 1080x2400",
                "shell wm density 420",
                "shell wm size",
                "shell wm density",
                "pull /data/local/tmp/sunrise-sunset-ci/. android-13-screenshots/",
                "shell dumpsys appwidget",
                "logcat -d",
                "exec-out screencap -p",
            ],
            adb_calls,
        )

    def test_fails_before_gradle_when_effective_timezone_is_not_california(self):
        result, adb_calls = self._run_with_stubs(0, timezone="UTC+0000")
        self.assertEqual(1, result.returncode)
        self.assertIn("expected a California PST/PDT offset", result.stdout)
        self.assertEqual(
            [
                "shell settings put global auto_time_zone 0",
                "shell cmd alarm set-timezone America/Los_Angeles",
                "shell date +%Z%z",
            ],
            adb_calls,
        )

    def test_accepts_california_daylight_saving_offset(self):
        result, _ = self._run_with_stubs(0, evidence_mode="complete", timezone="PDT-0700")
        self.assertEqual(0, result.returncode)
        self.assertNotIn("expected a California PST/PDT offset", result.stdout)

    def test_fails_when_gradle_succeeds_but_evidence_is_missing(self):
        result, _ = self._run_with_stubs(0)
        self.assertEqual(1, result.returncode)
        self.assertIn("Missing or empty Android 13 screenshot:", result.stdout)
        self.assertIn("Missing or empty Android 13 UI hierarchy:", result.stdout)

    def test_fails_when_screenshots_exist_but_hierarchies_are_missing(self):
        result, _ = self._run_with_stubs(0, evidence_mode="screenshots")
        self.assertEqual(1, result.returncode)
        self.assertNotIn("Missing or empty Android 13 screenshot:", result.stdout)
        self.assertIn("Missing or empty Android 13 UI hierarchy:", result.stdout)

    def test_returns_success_when_gradle_and_evidence_succeed(self):
        result, _ = self._run_with_stubs(0, evidence_mode="complete")
        self.assertEqual(0, result.returncode)
        self.assertNotIn("Missing or empty Android 13 screenshot:", result.stdout)
        self.assertNotIn("Missing or empty Android 13 UI hierarchy:", result.stdout)

    def test_collects_shell_owned_screenshots_after_gradle_cleanup(self):
        script = RUNNER.read_text(encoding="utf-8")
        self.assertIn(
            "adb pull /data/local/tmp/sunrise-sunset-ci/. android-13-screenshots/",
            script,
        )
        self.assertIn("Gradle connected-test task uninstalls the target app", script)
        self.assertIn("android-13-screenshots/appwidget-dump-after-test-cleanup.txt", script)
        self.assertIn("required_hierarchies=(", script)
        self.assertIn("Missing or empty Android 13 UI hierarchy:", script)

    def test_runner_applies_and_verifies_generic_tall_phone_display(self):
        script = RUNNER.read_text(encoding="utf-8")
        self.assertIn("wm size 1080x2400", script)
        self.assertIn("wm density 420", script)
        self.assertIn("Override size: 1080x2400", script)
        self.assertIn("Override density: 420", script)
        self.assertIn("not a Pixel hardware profile or OEM skin", script)

    def test_both_workflows_use_generic_aosp_without_named_pixel_profile(self):
        root = Path(__file__).resolve().parents[1]
        for name in ("build-apk.yml", "release.yml"):
            workflow = (root / ".github/workflows" / name).read_text(encoding="utf-8")
            self.assertIn("target: default", workflow)
            self.assertIn("profile: Nexus 5X", workflow)
            self.assertIn("avd_profile_validation.py 'Nexus 5X'", workflow)
            self.assertNotIn("pixel_2", workflow.lower())
            self.assertNotIn("pixel_7", workflow.lower())

    def test_workflow_does_not_pull_after_emulator_runner_stops(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("script: bash scripts/run-android-ui-tests.sh", workflow)
        self.assertNotIn("Collect Android 13 screenshot evidence", workflow)
        self.assertNotIn("adb pull", workflow)

    def test_workflow_invokes_runner_as_one_process(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("script: bash scripts/run-android-ui-tests.sh", workflow)
        runner = RUNNER.read_text(encoding="utf-8")
        self.assertIn("cmd alarm set-timezone America/Los_Angeles", runner)
        self.assertIn("date +%Z%z", runner)
        self.assertIn('expected a California PST/PDT offset', runner)
        self.assertIn("TIMEZONE_CHANGED", runner)
        self.assertNotIn("test_status=$?", workflow)


if __name__ == "__main__":
    unittest.main()
