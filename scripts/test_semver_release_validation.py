"""Regression tests for SemVer release gate. Any failure must block a release."""

import os
import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "semver-release-validation.sh"
GRADLE = ROOT / "app" / "build.gradle.kts"
RELEASE_WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"


class SemVerReleaseValidationTest(unittest.TestCase):
    def validate(self, tag, version_name=None):
        original = GRADLE.read_text(encoding="utf-8")
        try:
            if version_name is not None:
                changed = re.sub(
                    r'versionName = "[^"]+"',
                    f'versionName = "{version_name}"',
                    original, count=1,
                )
                GRADLE.write_text(changed, encoding="utf-8")
            env = os.environ.copy()
            env["GITHUB_REF_NAME"] = tag
            return subprocess.run(
                ["bash", str(SCRIPT)], cwd=ROOT, env=env,
                text=True, capture_output=True,
            )
        finally:
            GRADLE.write_text(original, encoding="utf-8")

    def test_accepts_stable_semver(self):
        for tag in ("v0.0.0", "v2.0.0", "v2.1.3", "v10.20.30"):
            with self.subTest(tag=tag):
                self.assertEqual(0, self.validate(tag, tag[1:]).returncode)

    def test_accepts_supported_prereleases(self):
        for tag in ("v2.1.0-alpha.1", "v2.1.0-alpha.9", "v2.1.0-beta.2",
                    "v2.1.0-rc.1", "v0.0.1-rc.10"):
            with self.subTest(tag=tag):
                self.assertEqual(0, self.validate(tag, tag[1:]).returncode)

    def test_rejects_malformed_tags(self):
        invalid = (
            "", "2.0.0", "v2.0", "v2", "v02.0.0", "v2.00.0", "v2.0.00",
            "v-1.0.0", "v2.0.0-preview.1", "v2.0.0-rc", "v2.0.0-rc.01",
            "v2.0.0-beta.-1", "v2.0.0+build.4",
            "v2.0.0-alpha.1.extra", "vv2.0.0",
        )
        for tag in invalid:
            with self.subTest(tag=tag):
                self.assertNotEqual(0, self.validate(tag, "2.0.0").returncode)

    def test_rejects_tag_gradle_version_mismatch(self):
        result = self.validate("v2.1.0", "2.0.0")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must match Gradle", result.stderr)

    def test_rejects_missing_tag_environment(self):
        result = subprocess.run(
            ["bash", str(SCRIPT)], cwd=ROOT,
            env={**os.environ, "GITHUB_REF_NAME": ""},
            text=True, capture_output=True,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("GITHUB_REF_NAME is required", result.stderr)

    def test_release_workflow_makes_semver_validation_mandatory(self):
        workflow = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("scripts/semver-release-validation.sh", workflow)
        self.assertIn("Verify Semantic Version tag matches Gradle versionName", workflow)
        self.assertIn("Run CommitGuard (mandatory release validation)", workflow)
        self.assertIn("bash scripts/commit-guard.sh", workflow)
        guard = (ROOT / "scripts" / "commit-guard.sh").read_text(encoding="utf-8")
        self.assertIn(":app:testDebugUnitTest", guard)
        self.assertIn("Capture release screenshots on Android 13 emulator", workflow)

    def test_validator_is_not_accidentally_skipped_by_shell_syntax_checks(self):
        for workflow_path in (
            ROOT / ".github" / "workflows" / "build-apk.yml",
            RELEASE_WORKFLOW,
        ):
            workflow = workflow_path.read_text(encoding="utf-8")
            self.assertIn("scripts/semver-release-validation.sh", workflow)


if __name__ == "__main__":
    unittest.main()
