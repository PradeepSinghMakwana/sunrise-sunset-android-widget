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
    def _run_with_stubs(self, gradle_status):
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
                "#!/usr/bin/env bash\nprintf '%s\\n' \"$*\" >> \"$ADB_CALLS\"\nexit 0\n",
                encoding="utf-8",
            )
            gradle.chmod(0o755)
            adb.chmod(0o755)

            env = os.environ.copy()
            env.update({
                "PATH": f"{bin_dir}{os.pathsep}{env['PATH']}",
                "GRADLE_TEST_EXIT": str(gradle_status),
                "ADB_CALLS": str(calls),
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
        result, adb_calls = self._run_with_stubs(7)
        self.assertEqual(7, result.returncode)
        self.assertEqual(
            [
                "pull /data/local/tmp/sunrise-sunset-ci/. android-13-screenshots/",
                "shell dumpsys appwidget",
                "logcat -d",
                "exec-out screencap -p",
            ],
            adb_calls,
        )

    def test_returns_success_when_gradle_succeeds(self):
        result, _ = self._run_with_stubs(0)
        self.assertEqual(0, result.returncode)

    def test_collects_shell_owned_screenshots_after_gradle_cleanup(self):
        script = RUNNER.read_text(encoding="utf-8")
        self.assertIn(
            "adb pull /data/local/tmp/sunrise-sunset-ci/. android-13-screenshots/",
            script,
        )
        self.assertIn("Gradle connected-test task uninstalls the target app", script)
        self.assertIn("android-13-screenshots/appwidget-dump-after-test-cleanup.txt", script)

    def test_workflow_does_not_pull_after_emulator_runner_stops(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("script: bash scripts/run-android-ui-tests.sh", workflow)
        self.assertNotIn("Collect Android 13 screenshot evidence", workflow)
        self.assertNotIn("adb pull", workflow)

    def test_workflow_invokes_runner_as_one_process(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("script: bash scripts/run-android-ui-tests.sh", workflow)
        self.assertNotIn("test_status=$?", workflow)


if __name__ == "__main__":
    unittest.main()
