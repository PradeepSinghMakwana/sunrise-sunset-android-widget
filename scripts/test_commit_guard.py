"""Regression tests for CommitGuard and its static Python reference checker."""
import subprocess
import tempfile
import unittest
from pathlib import Path

from check_python_references import check_file

ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts" / "commit-guard.sh"
CHECKER = ROOT / "scripts" / "check_python_references.py"
WORKFLOW = ROOT / ".github" / "workflows" / "build-apk.yml"


class CommitGuardTest(unittest.TestCase):
    def fixture(self, source):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "fixture.py"
        path.write_text(source, encoding="utf-8")
        return path

    def test_guard_exists_and_is_fail_fast(self):
        content = GUARD.read_text(encoding="utf-8")
        self.assertIn("set -Eeuo pipefail", content)
        self.assertIn("check_python_references.py", content)
        self.assertIn("unittest discover", content)
        self.assertIn(":app:testDebugUnitTest", content)

    def test_guard_checks_staged_unstaged_and_committed_patch_integrity(self):
        content = GUARD.read_text(encoding="utf-8")
        for check in ("git diff --check", "git diff --cached --check", "git show --check --oneline HEAD"):
            self.assertIn(check, content)

    def test_guard_validates_all_shell_scripts_and_requires_git_repo(self):
        content = GUARD.read_text(encoding="utf-8")
        self.assertIn("scripts/*.sh", content)
        self.assertIn("git rev-parse --is-inside-work-tree", content)
        self.assertIn("no shell scripts found", content)

    def test_guard_fails_closed_without_gradle_or_wrapper(self):
        content = GUARD.read_text(encoding="utf-8")
        self.assertIn("refusing to skip JVM tests", content)
        self.assertIn("./gradlew", content)

    def test_checker_catches_misspelled_module_attribute_with_hint(self):
        errors = check_file(self.fixture("import re\nre.MULTIL\n"))
        self.assertTrue(any("re.MULTIL" in error and "MULTILINE" in error for error in errors))

    def test_checker_accepts_correct_module_attribute_and_alias(self):
        self.assertEqual([], check_file(self.fixture("import re as regex\nregex.MULTILINE\n")))

    def test_checker_catches_nonexistent_standard_library_import(self):
        errors = check_file(self.fixture("import json\njson.loads_typo\n"))
        self.assertTrue(any("loads_typo" in error for error in errors))

    def test_checker_accepts_valid_package_submodule_import(self):
        self.assertEqual([], check_file(self.fixture("from unittest import mock\n")))

    def test_checker_catches_nonexistent_imported_symbol(self):
        errors = check_file(self.fixture("from pathlib import Pathh\n"))
        self.assertTrue(any("Pathh" in error for error in errors))

    def test_checker_reports_syntax_errors(self):
        errors = check_file(self.fixture("def broken(:\n    pass\n"))
        self.assertEqual(1, len(errors))
        self.assertIn("syntax error", errors[0])

    def test_checker_reports_invalid_utf8(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "invalid.py"
            path.write_bytes(b"import re\n\xff")
            self.assertTrue(any("cannot read" in error for error in check_file(path)))

    def test_ci_executes_commitguard(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("Run CommitGuard (mandatory pre-commit checks)", workflow)
        self.assertIn("bash scripts/commit-guard.sh", workflow)
        release = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
        self.assertIn("Run CommitGuard (mandatory release validation)", release)
        self.assertIn("bash scripts/commit-guard.sh", release)

    def test_checker_self_validates_repository_scripts(self):
        result = subprocess.run(
            ["python3", str(CHECKER)], cwd=ROOT, text=True, capture_output=True
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_guard_shell_syntax(self):
        result = subprocess.run(["bash", "-n", str(GUARD)], cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
