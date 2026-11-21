#!/usr/bin/env python3
"""Syntax-check Python and detect misspelled attributes on explicitly imported modules."""
from __future__ import annotations

import ast
import difflib
import importlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_DIR = ROOT / "scripts"


def check_file(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except (SyntaxError, UnicodeDecodeError) as exc:
        return [f"{path.relative_to(ROOT)}: syntax error: {exc}"]

    aliases: dict[str, object] = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for item in node.names:
                try:
                    module = importlib.import_module(item.name)
                    aliases[item.asname or item.name.split(".")[0]] = module
                except ImportError:
                    # Third-party/project modules may not be installed in the lightweight gate.
                    continue
        elif isinstance(node, ast.ImportFrom) and node.module:
            try:
                module = importlib.import_module(node.module)
            except ImportError:
                continue
            for item in node.names:
                if item.name == "*":
                    continue
                if not hasattr(module, item.name):
                    # Allow valid package submodules (e.g. from unittest import mock).
                    try:
                        importlib.import_module(f"{node.module}.{item.name}")
                    except ImportError:
                        suggestions = difflib.get_close_matches(item.name, dir(module), n=1, cutoff=0.72)
                        hint = f"; did you mean {suggestions[0]!r}?" if suggestions else ""
                        errors.append(f"{path.relative_to(ROOT)}:{node.lineno}: imported reference {node.module}.{item.name} does not exist{hint}")

    for node in ast.walk(tree):
        if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name):
            module = aliases.get(node.value.id)
            if module is not None and not hasattr(module, node.attr):
                suggestions = difflib.get_close_matches(node.attr, dir(module), n=1, cutoff=0.72)
                hint = f"; did you mean {suggestions[0]!r}?" if suggestions else ""
                errors.append(f"{path.relative_to(ROOT)}:{node.lineno}: reference {node.value.id}.{node.attr} does not exist in imported module{hint}")
    return errors


def main() -> int:
    files = sorted(SCRIPT_DIR.glob("*.py"))
    problems = [issue for file in files for issue in check_file(file)]
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"Python syntax/reference checks passed for {len(files)} files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
