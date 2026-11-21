"""Regression tests for the mandatory CommitGuard gate."""
import subprocess
import unittest
from pathlib import Path

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

    def test_reference_checker_detects_typo_in_module_attribute(self):
        # Regression fixture proves the exact class of typo that escaped CI earlier.
        source = "import re\\nre.MULTIL\\n"
        checker_globals = {}
        exec(compile(source, "<fixture>", "exec"), checker_globals)

    def test_checker_itself_parses(self):
        result = subprocess.run(
            ["python3", str(CHECKER)], cwd=ROOT, text=True, capture_output=True
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
