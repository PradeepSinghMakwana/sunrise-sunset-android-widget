#!/usr/bin/env bash
# Mandatory pre-commit gate: fail closed before a commit is created.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "[CommitGuard] Git whitespace and patch-integrity validation"
# Check staged and unstaged changes for local pre-commit use, and HEAD for CI.
git diff --check
git diff --cached --check
git show --check --oneline HEAD

echo "[CommitGuard] Shell syntax validation"
bash -n scripts/*.sh

echo "[CommitGuard] Python syntax and imported-module reference validation"
python3 scripts/check_python_references.py

echo "[CommitGuard] Complete Python regression suite"
python3 -m unittest discover -s scripts -p 'test_*.py' -v

if ! command -v gradle >/dev/null 2>&1; then
  echo "[CommitGuard] ERROR: Gradle not installed; refusing to pass without JVM tests" >&2
  exit 1
fi

echo "[CommitGuard] JVM unit tests"
gradle :app:testDebugUnitTest

echo "[CommitGuard] PASS — all configured checks passed"
