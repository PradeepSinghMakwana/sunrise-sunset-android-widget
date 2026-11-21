#!/usr/bin/env bash
# Mandatory pre-commit gate: fail closed before any commit is created.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "[CommitGuard] Shell syntax validation"
bash -n scripts/*.sh

echo "[CommitGuard] Python syntax and imported-module reference validation"
python3 scripts/check_python_references.py

echo "[CommitGuard] Complete Python regression suite"
python3 -m unittest discover -s scripts -p 'test_*.py' -v

if command -v gradle >/dev/null 2>&1; then
  echo "[CommitGuard] JVM unit tests"
  gradle :app:testDebugUnitTest
else
  echo "[CommitGuard] ERROR: Gradle not installed; refusing commit without JVM tests" >&2
  exit 1
fi

echo "[CommitGuard] PASS — all pre-commit gates passed"
