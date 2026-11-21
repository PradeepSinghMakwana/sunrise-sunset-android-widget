"""Regression tests for the mandatory CommitGuard gate."""
import tempfile
import unittest
from pathlib import Path

from check_python_references import check_file

ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts" / "commit-guard.sh"
CHECKER = ROOT / "scripts" / "check_python_references.py"


class CommitGuardTest(unittest.TestCase):
    def test_guard_exists_and_is_fail_fast(self):
        self.assertTrue(GUARD.is_file())
        content = GUARD.read_text(encoding="utf-8")
        self.assertIn("set -euo pipefail", content)
        self.assertIn("check_python_references.py", content)
        self.assertIn("unittest discover", content)
        self.assertIn(":app:testDebugUnitTest", content)

    def test_reference_checker_catches_typo_in_imported_module_attribute(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = Path(temp) / "typo_fixture.py"
            fixture.write_text("import re\nre.MULTIL\n", encoding="utf-8")
            errors = check_file(fixture)
            self.assertTrue(errors)
            self.assertIn("re.MULTIL", errors[0])
            self.assertIn("MULTILINE", errors[0])

    def test_reference_checker_passes_correct_reference(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = Path(temp) / "correct_fixture.py"
            fixture.write_text("import re\nre.MULTILINE\n", encoding="utf-8")
            self.assertEqual([], check_file(fixture))

    def test_ci_executes_commitguard(self):
        workflow = (ROOT / ".github" / "workflows" / "build-apk.yml").read_text(encoding="utf-8")
        self.assertIn("Run CommitGuard (mandatory pre-commit checks)", workflow)
        self.assertIn("bash scripts/commit-guard.sh", workflow)
        for path in (ROOT / ".github" / "workflows" / "build-apk.yml",
                     ROOT / ".github" / "workflows" / "release.yml"):
            self.assertIn("scripts/check_python_references.py", path.read_text(encoding="utf-8"))

    def test_checker_accepts_valid_python_submodule_import(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = Path(temp) / "valid_submodule.py"
            fixture.write_text("from unittest import mock\n", encoding="utf-8")
            self.assertEqual([], check_file(fixture))

    def test_checker_itself_parses_and_checks_all_script_files(self):
        import subprocess
        result = subprocess.run(
            ["python3", str(CHECKER)], cwd=ROOT, text=True, capture_output=True
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
