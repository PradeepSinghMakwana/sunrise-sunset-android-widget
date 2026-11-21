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
TOKEN_PATTERN = r"[A-Za-z0-9_]+([.-][A-Za-z0-9_]+)*"
TOKEN_RE = re.compile(r"[A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*", re.UNICODE)
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
        "diff", "HEAD^", "HEAD", "--name-only", "--no-renames"
    ]
    paths = sorted(set(git(*args).splitlines()))
    if not staged and paths and set(paths) == {AUDIT_PATH}:
        return sorted(set(git("diff", "HEAD^^", "HEAD^", "--name-only", "--no-renames").splitlines()))
    return paths


def word_occurrences(staged: bool) -> collections.Counter[tuple[str, str]]:
    common = ["--no-ext-diff", "--no-renames", "--word-diff=porcelain",
              "--word-diff-regex=" + TOKEN_PATTERN, "--unified=0", "--no-color"]
    if staged:
        output = git("diff", "--cached", *common, "--", ".", ":(exclude)" + AUDIT_PATH)
    else:
        current = set(git("diff", "HEAD^", "HEAD", "--name-only", "--no-renames").splitlines())
        base, target = ("HEAD^^", "HEAD^") if current == {AUDIT_PATH} else ("HEAD^", "HEAD")
        output = git("diff", base, target, *common, "--", ".", ":(exclude)" + AUDIT_PATH)
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
        current_files = set(git("diff", "HEAD^", "HEAD", "--name-only", "--no-renames").splitlines())
        audited_ref = "HEAD^" if current_files == {AUDIT_PATH} else "HEAD"
        subject = git("log", "-1", "--format=%s", audited_ref).strip()
        proposed = manifest.get("commit_subject")
        if proposed != subject:
            errors.append(f"commit_subject does not match audited commit subject {subject!r}")

    if errors:
        return errors
    print(f"[CommitGuard] Word audit PASS: {sum(actual.values())} changed word/punctuation occurrence(s), "
          f"{len(expected_files)} changed file(s), {len(req_ids)} requirement(s).")
    print("[CommitGuard] Human review attestation recorded; semantic truth of intent/rationales still requires human judgment.")
    return []



def commit_parent(sha: str) -> str | None:
    fields = git("rev-list", "--parents", "-n", "1", sha).split()
    return fields[1] if len(fields) > 1 else None


def commit_files(sha: str) -> list[str]:
    parent = commit_parent(sha)
    if parent is None:
        output = git("diff-tree", "--root", "--no-commit-id", "--name-only", "--no-renames", "-r", sha)
    else:
        output = git("diff", "--name-only", "--no-renames", parent, sha)
    return sorted(set(output.splitlines()))


def commit_words(sha: str) -> collections.Counter[tuple[str, str]]:
    parent = commit_parent(sha)
    if parent is None:
        raise AuditError(f"root commit {sha} has no auditable parent; use a branch merge-base")
    common = ["--no-ext-diff", "--no-renames", "--word-diff=porcelain",
              "--word-diff-regex=" + TOKEN_PATTERN, "--unified=0", "--no-color"]
    output = git("diff", parent, sha, *common, "--", ".", ":(exclude)" + AUDIT_PATH)
    counts: collections.Counter[tuple[str, str]] = collections.Counter()
    in_hunk = False
    for line in output.splitlines():
        if line.startswith("@@"):
            in_hunk = True
            continue
        if in_hunk and line and line[0] in "+-":
            direction = "added" if line[0] == "+" else "removed"
            for token in TOKEN_RE.findall(line[1:]):
                counts[(direction, token)] += 1
    return counts


def validate_commit_record(sha: str, record: dict) -> list[str]:
    errors: list[str] = []
    for field in ("intent", "reviewed_by", "commit_subject"):
        if not nonempty(record.get(field)):
            errors.append(f"commit {sha}: {field} must be non-empty")
    subject = git("log", "-1", "--format=%s", sha).strip()
    if record.get("commit_subject") != subject:
        errors.append(f"commit {sha}: reviewed subject does not match {subject!r}")
    requirements = record.get("requirements")
    req_ids = set()
    if not isinstance(requirements, list) or not requirements:
        errors.append(f"commit {sha}: requirements must be a non-empty list")
        requirements = []
    for item in requirements:
        if isinstance(item, dict) and nonempty(item.get("id")) and nonempty(item.get("text")):
            req_ids.add(item["id"])
        else:
            errors.append(f"commit {sha}: each requirement needs id and text")
    files = set(commit_files(sha)) - {AUDIT_PATH}
    reviews = record.get("file_reviews")
    if not isinstance(reviews, dict):
        errors.append(f"commit {sha}: file_reviews must be an object")
        reviews = {}
    for path in sorted(files):
        review = reviews.get(path)
        if not isinstance(review, dict) or not nonempty(review.get("necessity")):
            errors.append(f"commit {sha}: changed file lacks necessity review: {path}")
            continue
        mapped = review.get("requirement_ids")
        if not isinstance(mapped, list) or not mapped or any(value not in req_ids for value in mapped):
            errors.append(f"commit {sha}: {path} has invalid requirement references")
    for path in sorted(set(reviews) - files):
        errors.append(f"commit {sha}: stale file review: {path}")
    actual = commit_words(sha)
    declared: collections.Counter[tuple[str, str]] = collections.Counter()
    entries = record.get("word_reviews")
    if not isinstance(entries, list):
        errors.append(f"commit {sha}: word_reviews must be a list")
        entries = []
    for index, item in enumerate(entries):
        if not isinstance(item, dict):
            errors.append(f"commit {sha}: word_reviews[{index}] must be an object")
            continue
        direction, token = item.get("direction"), item.get("token")
        count, req_id = item.get("count"), item.get("requirement_id")
        if direction not in ("added", "removed") or not nonempty(token):
            errors.append(f"commit {sha}: word_reviews[{index}] has invalid direction/token")
            continue
        if not isinstance(count, int) or isinstance(count, bool) or count < 1:
            errors.append(f"commit {sha}: word_reviews[{index}] count must be positive")
            continue
        if req_id not in req_ids or not nonempty(item.get("rationale")):
            errors.append(f"commit {sha}: word_reviews[{index}] lacks a valid requirement or rationale")
        declared[(direction, token)] += count
    for key in sorted(set(actual) | set(declared)):
        if actual[key] != declared[key]:
            errors.append(f"commit {sha}: {key[0]} word {key[1]!r}: diff has {actual[key]}, reviewed {declared[key]}")
    return errors


def validate_range(base: str, head: str) -> list[str]:
    if not nonempty(base) or not nonempty(head):
        raise AuditError("range base and head SHAs are required")
    git("cat-file", "-e", base + "^{commit}")
    git("cat-file", "-e", head + "^{commit}")
    manifest = load_manifest()
    records = manifest.get("commit_audits")
    if not isinstance(records, dict):
        raise AuditError("commit_audits must map each changed commit SHA to its audit record")
    audit_start = manifest.get("audit_start_sha")
    if audit_start:
        git("cat-file", "-e", audit_start + "^{commit}")
        is_start_ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", audit_start, head],
            cwd=ROOT, capture_output=True, check=False
        ).returncode == 0
        is_start_ancestor_of_base = subprocess.run(
            ["git", "merge-base", "--is-ancestor", audit_start, base],
            cwd=ROOT, capture_output=True, check=False
        ).returncode == 0
        if is_start_ancestor and not is_start_ancestor_of_base:
            base = audit_start
    commits = git("rev-list", "--reverse", base + ".." + head).splitlines()
    if not commits:
        raise AuditError(f"no commits found in range {base}..{head}")
    errors: list[str] = []
    audited = 0
    for sha in commits:
        files = commit_files(sha)
        if not files:
            continue
        if files == [AUDIT_PATH]:
            parent = commit_parent(sha)
            while parent is not None and commit_files(parent) == [AUDIT_PATH]:
                parent = commit_parent(parent)
            if parent is None or parent not in records:
                errors.append(f"manifest-only commit {sha} lacks an audit record for the preceding code commit")
            continue
        record = records.get(sha)
        if record is None:
            errors.append(f"commit {sha} has no per-commit audit record")
            actual = commit_words(sha)
            diagnostic = [{"direction": d, "token": t, "count": n} for (d, t), n in sorted(actual.items())]
            print(f"[CommitGuard] REQUIRED_AUDIT {sha} files={json.dumps([p for p in files if p != AUDIT_PATH])} words={json.dumps(diagnostic)}")
            continue
        audited += 1
        errors.extend(validate_commit_record(sha, record))
    if not audited:
        errors.append("range contains no audited code commits")
    if errors:
        return errors
    print(f"[CommitGuard] Range audit PASS: {audited} individual commit(s), {len(commits)} total commit(s).")
    print("[CommitGuard] Human review attestations are recorded; semantic truth still requires human judgment.")
    return []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--staged", action="store_true", help="audit the staged diff (pre-commit)")
    mode.add_argument("--head", action="store_true", help="audit the latest committed change")
    parser.add_argument("--range", nargs=2, metavar=("BASE", "HEAD"),
                        help="audit every individual commit between BASE and HEAD")
    args = parser.parse_args()
    try:
        if args.range:
            errors = validate_range(args.range[0], args.range[1])
        else:
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
