#!/usr/bin/env bash
# Mandatory, fail-closed repository validation gate.
set -Eeuo pipefail
IFS=$'\n\t'
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT"

fail() { printf '[CommitGuard] ERROR: %s\n' "$*" >&2; exit 1; }
command -v git >/dev/null 2>&1 || fail "git is required"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "run inside a Git working tree"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"
command -v bash >/dev/null 2>&1 || fail "bash is required"

echo "[CommitGuard] Git patch-integrity checks"
git diff --check || fail "unstaged patch contains whitespace errors"
git diff --cached --check || fail "staged patch contains whitespace errors"
git show --check --oneline HEAD >/dev/null || fail "HEAD commit contains whitespace errors"

echo "[CommitGuard] Word-by-word intention and necessity audit"
if git diff --cached --quiet; then
  python3 scripts/commit_intent_audit.py --head || fail "committed change lacks a complete intention/word audit"
else
  python3 scripts/commit_intent_audit.py --staged || fail "staged change lacks a complete intention/word audit"
fi

echo "[CommitGuard] Shell syntax checks"
shopt -s nullglob
shell_files=(scripts/*.sh)
(("${#shell_files[@]}" > 0)) || fail "no shell scripts found under scripts/"
bash -n "${shell_files[@]}" || fail "shell syntax validation failed"

echo "[CommitGuard] Python syntax and standard-library reference checks"
python3 scripts/check_python_references.py || fail "Python syntax/reference validation failed"

echo "[CommitGuard] Complete Python regression suite"
python3 -m unittest discover -s scripts -p 'test_*.py' -v || fail "Python regression tests failed"

echo "[CommitGuard] JVM unit tests"
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :app:testDebugUnitTest || fail "JVM unit tests failed"
elif [[ -x ./gradlew ]]; then
  ./gradlew --no-daemon :app:testDebugUnitTest || fail "JVM unit tests failed"
else
  fail "Gradle or executable ./gradlew is required; refusing to skip JVM tests"
fi

echo "[CommitGuard] PASS — every configured check passed"
