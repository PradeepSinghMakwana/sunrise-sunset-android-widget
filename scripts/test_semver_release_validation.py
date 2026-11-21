"""Regression tests for semantic release tag validation."""

import os
import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "semver-release-validation.sh"
GRADLE = ROOT / "app" / "build.gradle.kts"


class SemVerReleaseValidationTest(unittest.TestCase):
    def validate(self, tag, version_name):
        original = GRADLE.read_text(encoding="utf-8")
        try:
            GRADLE.write_text(re.sub(
                r'versionName = "[^"]+"', f'versionName = "{version_name}"', original, count=1
            ), encoding="utf-8")
            env = {"PATH": os.environ["PATH"], "GITHUB_REF_NAME": tag}
            return subprocess.run(
                ["bash", str(SCRIPT)], cwd=ROOT, env=env,
                text=True, capture_output=True,
            )
        finally:
            GRADLE.write_text(original, encoding="utf-8")

    def test_accepts_semver_stable_and_prerelease_tags(self):
        for tag in ("v2.0.0", "v2.1.3", "v3.0.0", "v2.1.0-alpha.1",
                    "v2.1.0-beta.2", "v2.1.0-rc.1"):
            with self.subTest(tag=tag):
                result = self.validate(tag, tag[1:])
                self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_invalid_tags(self):
        for tag in ("v2.0", "2.0.0", "v02.0.0", "v2.0.00",
                    "v2.0.0-preview.1", "v2.0.0-rc", "v2.0.0-rc.01",
                    "v2.0.0+build.4"):
            with self.subTest(tag=tag):
                result = self.validate(tag, tag.lstrip("v"))
                self.assertNotEqual(0, result.returncode)

    def test_rejects_tag_gradle_version_mismatch(self):
        result = self.validate("v2.1.0", "2.0.0")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must match Gradle", result.stderr)


if __name__ == "__main__":
    unittest.main()
