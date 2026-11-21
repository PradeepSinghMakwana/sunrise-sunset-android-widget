"""Regression tests for CommitGuard and its static Python reference checker."""
import subprocess
import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from check_python_references import check_file
import commit_intent_audit

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
        self.assertIn("Run CommitGuard (mandatory per-commit checks)", workflow)
        self.assertIn("bash scripts/commit-guard.sh", workflow)
        self.assertIn("--range", (ROOT / "scripts" / "commit-guard.sh").read_text(encoding="utf-8"))
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

    def test_word_audit_requires_every_added_and_removed_occurrence(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "guard@example.invalid"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "CommitGuard Test"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("old label\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("new label\n", encoding="utf-8")
            (repo / ".commitguard").mkdir()
            manifest_path = repo / ".commitguard" / "intent-audit.json"
            manifest = {
                "intent": "Rename the displayed label as requested",
                "reviewed_by": "repository owner",
                "commit_subject": "Rename displayed label",
                "requirements": [{"id": "R1", "text": "Rename old label to new label"}],
                "file_reviews": {"sample.txt": {
                    "requirement_ids": ["R1"], "necessity": "Required visible label correction"
                }},
                "word_reviews": [
                    {"direction": "removed", "token": "old", "count": 1, "requirement_id": "R1",
                     "rationale": "Remove superseded label"},
                    {"direction": "added", "token": "new", "count": 1, "requirement_id": "R1",
                     "rationale": "Use requested label"}
                ]
            }
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt", ".commitguard/intent-audit.json"], cwd=repo, check=True)
            with patch.object(commit_intent_audit, "ROOT", repo), patch.object(
                commit_intent_audit, "MANIFEST", manifest_path
            ):
                self.assertEqual([], commit_intent_audit.validate(staged=True))
                manifest["word_reviews"].pop()
                manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
                errors = commit_intent_audit.validate(staged=True)
                self.assertTrue(any("added word 'new'" in error for error in errors))

    def test_word_audit_rejects_unreviewed_changed_file(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "guard@example.invalid"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "CommitGuard Test"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("before\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("after\n", encoding="utf-8")
            (repo / ".commitguard").mkdir()
            manifest_path = repo / ".commitguard" / "intent-audit.json"
            manifest_path.write_text(json.dumps({
                "intent": "Update sample", "reviewed_by": "owner", "commit_subject": "Update sample",
                "requirements": [{"id": "R1", "text": "Update sample"}],
                "file_reviews": {}, "word_reviews": []
            }), encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt", ".commitguard/intent-audit.json"], cwd=repo, check=True)
            with patch.object(commit_intent_audit, "ROOT", repo), patch.object(
                commit_intent_audit, "MANIFEST", manifest_path
            ):
                errors = commit_intent_audit.validate(staged=True)
                self.assertTrue(any("no review" in error for error in errors))


    def test_word_audit_tokenizer_handles_hyphens_apostrophes_and_unchanged_words(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "guard@example.invalid"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "CommitGuard Test"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("GitHub's pre-commit hook\\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("GitHub's commit hook\\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            with patch.object(commit_intent_audit, "ROOT", repo):
                actual = commit_intent_audit.word_occurrences(staged=True)
            self.assertEqual(1, actual[("removed", "pre-commit")])
            self.assertEqual(1, actual[("added", "commit")])
            self.assertNotIn(("added", "GitHub"), actual)
            self.assertNotIn(("removed", "GitHub"), actual)
            self.assertNotIn(("added", "s"), actual)
            self.assertNotIn(("removed", "s"), actual)

    def test_head_audit_checks_subject_of_commit_before_manifest_only_followup(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "guard@example.invalid"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "CommitGuard Test"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("before\\n", encoding="utf-8")
            (repo / ".commitguard").mkdir()
            manifest_path = repo / ".commitguard" / "intent-audit.json"
            manifest_path.write_text("{}", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("after\\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "Change sample"], cwd=repo, check=True)
            manifest = {
                "intent": "Change sample as required",
                "reviewed_by": "repository owner",
                "commit_subject": "Change sample",
                "requirements": [{"id": "R1", "text": "Change sample value"}],
                "file_reviews": {"sample.txt": {
                    "requirement_ids": ["R1"], "necessity": "Required sample correction"
                }},
                "word_reviews": [
                    {"direction": "removed", "token": "before", "count": 1,
                     "requirement_id": "R1", "rationale": "Remove old sample value"},
                    {"direction": "added", "token": "after", "count": 1,
                     "requirement_id": "R1", "rationale": "Use new sample value"}
                ]
            }
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            subprocess.run(["git", "add", ".commitguard/intent-audit.json"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "Record sample intention audit"], cwd=repo, check=True)
            with patch.object(commit_intent_audit, "ROOT", repo), patch.object(
                commit_intent_audit, "MANIFEST", manifest_path
            ):
                self.assertEqual([], commit_intent_audit.validate(staged=False))

    def test_range_audit_checks_each_commit_even_when_changes_net_to_zero(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "guard@example.invalid"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "CommitGuard Test"], cwd=repo, check=True)
            (repo / "sample.txt").write_text("alpha\n", encoding="utf-8")
            (repo / ".commitguard").mkdir()
            manifest_path = repo / ".commitguard" / "intent-audit.json"
            manifest_path.write_text(json.dumps({"commit_audits": {}}), encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "baseline"], cwd=repo, check=True)
            base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()

            def make_record(subject):
                sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
                with patch.object(commit_intent_audit, "ROOT", repo):
                    words = commit_intent_audit.commit_words(sha)
                return sha, {
                    "intent": "Change the sample value as required",
                    "reviewed_by": "repository owner",
                    "commit_subject": subject,
                    "requirements": [{"id": "R1", "text": "Change sample value"}],
                    "file_reviews": {"sample.txt": {
                        "requirement_ids": ["R1"], "necessity": "Required sample correction"
                    }},
                    "word_reviews": [
                        {"direction": direction, "token": token, "count": count,
                         "requirement_id": "R1", "rationale": "Review this exact changed token"}
                        for (direction, token), count in words.items()
                    ]
                }

            (repo / "sample.txt").write_text("beta\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "Change alpha to beta"], cwd=repo, check=True)
            first, first_record = make_record("Change alpha to beta")
            (repo / "sample.txt").write_text("alpha\n", encoding="utf-8")
            subprocess.run(["git", "add", "sample.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "Restore alpha"], cwd=repo, check=True)
            second, second_record = make_record("Restore alpha")
            manifest_path.write_text(json.dumps({"commit_audits": {
                first: first_record, second: second_record
            }}), encoding="utf-8")
            subprocess.run(["git", "add", ".commitguard/intent-audit.json"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "Record per-commit audits"], cwd=repo, check=True)
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["audit_note"] = "second metadata-only follow-up"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            subprocess.run(["git", "add", ".commitguard/intent-audit.json"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "Record audit ledger follow-up"], cwd=repo, check=True)
            head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            with patch.object(commit_intent_audit, "ROOT", repo), patch.object(
                commit_intent_audit, "MANIFEST", manifest_path
            ):
                self.assertEqual([], commit_intent_audit.validate_range(base, head))
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                del manifest["commit_audits"][second]
                manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
                errors = commit_intent_audit.validate_range(base, head)
                self.assertTrue(any(second in error and "no per-commit audit" in error for error in errors))

if __name__ == "__main__":
    unittest.main()
