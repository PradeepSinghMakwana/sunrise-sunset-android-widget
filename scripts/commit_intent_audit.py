#!/usr/bin/env python3
"""Fail-closed, occurrence-by-occurrence audit of words changed by a commit.

The audit manifest is an explicit human review record. This tool verifies that
every added/removed word occurrence and every changed file is accounted for;
it cannot infer human intent or prove that a supplied rationale is truthful.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / ".commitguard" / "intent-audit.json"
TOKEN_RE = re.compile(r"[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*|[^\w\s]", re.UNICODE)
AUDIT_PATH = ".commitguard/intent-audit.json"


class AuditError(Exception):
    pass


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=ROOT, text=True, encoding="utf-8",
        errors="replace", capture_output=True, check=False,
    )
    if result.returncode:
        raise AuditError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def changed_files(staged: bool) -> list[str]:
    args = ["diff", "--cached", "--name-only", "--no-renames"] if staged else [
        "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"
    ]
    return sorted(set(git(*args).splitlines()))


def word_occurrences(staged: bool) -> collections.Counter[tuple[str, str]]:
    if staged:
        output = git("diff", "--cached", "--no-ext-diff", "--no-renames",
                     "--word-diff=porcelain", "--unified=0", "--no-color")
    else:
        output = git("show", "--format=", "--no-ext-diff", "--no-renames",
                     "--word-diff=porcelain", "--unified=0", "--no-color", "HEAD")
    counts: collections.Counter[tuple[str, str]] = collections.Counter()
    in_hunk = False
    for line in output.splitlines():
        if line.startswith("@@"):
            in_hunk = True
            continue
        if not in_hunk or not line:
            continue
        if line[0] in "+-":
            direction = "added" if line[0] == "+" else "removed"
            for token in TOKEN_RE.findall(line[1:]):
                counts[(direction, token)] += 1
        # A porcelain "~" marks a newline and is not a word.
    return counts


def load_manifest() -> dict:
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise AuditError(f"required audit manifest missing: {MANIFEST.relative_to(ROOT)}") from exc
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise AuditError(f"invalid audit manifest: {exc}") from exc
    if not isinstance(data, dict):
        raise AuditError("audit manifest root must be a JSON object")
    return data


def nonempty(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate(staged: bool) -> list[str]:
    manifest = load_manifest()
    errors: list[str] = []
    intent = manifest.get("intent")
    reviewer = manifest.get("reviewed_by")
    requirements = manifest.get("requirements")
    if not nonempty(intent):
        errors.append("intent must state the user's task in plain language")
    if not nonempty(reviewer):
        errors.append("reviewed_by must identify the person who reviewed the diff")
    if not isinstance(requirements, list) or not requirements:
        errors.append("requirements must be a non-empty list")
        requirements = []
    req_ids = set()
    for item in requirements:
        if not isinstance(item, dict) or not nonempty(item.get("id")) or not nonempty(item.get("text")):
            errors.append("each requirement needs non-empty id and text")
        else:
            req_ids.add(item["id"])

    files = changed_files(staged)
    expected_files = set(files) - {AUDIT_PATH}
    reviews = manifest.get("file_reviews")
    if not isinstance(reviews, dict):
        errors.append("file_reviews must be an object mapping every changed file to its review")
        reviews = {}
    for path in sorted(expected_files):
        review = reviews.get(path)
        if not isinstance(review, dict):
            errors.append(f"changed file has no review: {path}")
            continue
        if not nonempty(review.get("necessity")):
            errors.append(f"{path}: necessity/rationale is missing")
        mapped = review.get("requirement_ids")
        if not isinstance(mapped, list) or not mapped:
            errors.append(f"{path}: requirement_ids must not be empty")
        elif any(value not in req_ids for value in mapped):
            errors.append(f"{path}: references an undeclared requirement")
    for path in sorted(set(reviews) - expected_files):
        errors.append(f"file review is stale or unrelated to this diff: {path}")

    actual = word_occurrences(staged)
    declared: collections.Counter[tuple[str, str]] = collections.Counter()
    entries = manifest.get("word_reviews")
    if not isinstance(entries, list):
        errors.append("word_reviews must list every changed word occurrence")
        entries = []
    for index, item in enumerate(entries):
        if not isinstance(item, dict):
            errors.append(f"word_reviews[{index}] must be an object")
            continue
        direction, token = item.get("direction"), item.get("token")
        count, requirement_id, rationale = item.get("count"), item.get("requirement_id"), item.get("rationale")
        if direction not in ("added", "removed") or not nonempty(token):
            errors.append(f"word_reviews[{index}] needs direction added/removed and a token")
            continue
        if not isinstance(count, int) or isinstance(count, bool) or count < 1:
            errors.append(f"word_reviews[{index}] count must be a positive integer")
            continue
        if requirement_id not in req_ids:
            errors.append(f"word_reviews[{index}] references an undeclared requirement")
        if not nonempty(rationale):
            errors.append(f"word_reviews[{index}] has no intention/meaning rationale")
        declared[(direction, token)] += count

    for key in sorted(set(actual) | set(declared)):
        got, reviewed = actual[key], declared[key]
        if got != reviewed:
            errors.append(
                f"{key[0]} word {key[1]!r}: diff has {got} occurrence(s), "
                f"manifest reviews {reviewed}"
            )

    if staged:
        subject = git("log", "-1", "--format=%s").strip()
        # Before commit, the staged message is not available; require a reviewed proposed subject.
        proposed = manifest.get("commit_subject")
        if not nonempty(proposed):
            errors.append("commit_subject must state the proposed commit's purpose")
    else:
        subject = git("log", "-1", "--format=%s").strip()
        proposed = manifest.get("commit_subject")
        if proposed != subject:
            errors.append(f"commit_subject does not match actual HEAD subject {subject!r}")

    if errors:
        return errors
    print(f"[CommitGuard] Word audit PASS: {sum(actual.values())} changed word/punctuation occurrence(s), "
          f"{len(expected_files)} changed file(s), {len(req_ids)} requirement(s).")
    print("[CommitGuard] Human review attestation recorded; semantic truth of intent/rationales still requires human judgment.")
    return []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--staged", action="store_true", help="audit the staged diff (pre-commit)")
    mode.add_argument("--head", action="store_true", help="audit the latest committed change (CI)")
    args = parser.parse_args()
    try:
        staged = not args.head
        errors = validate(staged)
    except AuditError as exc:
        print(f"[CommitGuard] ERROR: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"[CommitGuard] ERROR: {error}", file=sys.stderr)
        print("[CommitGuard] BLOCKED: intention/word audit incomplete.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
