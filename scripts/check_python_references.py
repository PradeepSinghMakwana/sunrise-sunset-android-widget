#!/usr/bin/env python3
"""Fail-closed syntax and reference checks for repository Python scripts.

Only standard-library modules are imported for attribute validation. Third-party
imports are deliberately not executed: doing so could run arbitrary package code
or require optional CI dependencies.
"""
from __future__ import annotations

import ast
import difflib
import importlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_DIR = ROOT / "scripts"


def _module_for_import(name: str):
    """Load only standard-library modules; return None for external/project modules."""
    top_level = name.partition(".")[0]
    if top_level not in sys.stdlib_module_names:
        return None
    try:
        return importlib.import_module(name)
    except (ImportError, AttributeError, ValueError):
        return False


def check_file(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        source = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        return [f"{path}: cannot read Python source: {exc}"]

    display_path = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
    try:
        tree = ast.parse(source, filename=str(display_path))
    except (SyntaxError, UnicodeDecodeError) as exc:
        return [f"{display_path}: syntax error: {exc}"]

    aliases: dict[str, object] = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for item in node.names:
                module = _module_for_import(item.name)
                if module is False:
                    errors.append(f"{display_path}:{node.lineno}: standard-library module {item.name!r} cannot be imported")
                elif module is not None:
                    aliases[item.asname or item.name.split(".")[0]] = module
        elif isinstance(node, ast.ImportFrom) and node.module:
            module = _module_for_import(node.module)
            if module is False:
                errors.append(f"{display_path}:{node.lineno}: standard-library module {node.module!r} cannot be imported")
                continue
            if module is None:
                continue
            for item in node.names:
                if item.name == "*":
                    errors.append(f"{display_path}:{node.lineno}: wildcard imports are not statically verifiable")
                    continue
                if not hasattr(module, item.name):
                    try:
                        importlib.import_module(f"{node.module}.{item.name}")
                    except (ImportError, AttributeError, ValueError):
                        suggestions = difflib.get_close_matches(item.name, dir(module), n=1, cutoff=0.72)
                        hint = f"; did you mean {suggestions[0]!r}?" if suggestions else ""
                        errors.append(f"{display_path}:{node.lineno}: imported reference {node.module}.{item.name} does not exist{hint}")

    for node in ast.walk(tree):
        if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name):
            module = aliases.get(node.value.id)
            if module is not None and not hasattr(module, node.attr):
                suggestions = difflib.get_close_matches(node.attr, dir(module), n=1, cutoff=0.72)
                hint = f"; did you mean {suggestions[0]!r}?" if suggestions else ""
                errors.append(f"{display_path}:{node.lineno}: reference {node.value.id}.{node.attr} does not exist in imported module{hint}")

    return errors


def main() -> int:
    files = sorted(SCRIPT_DIR.rglob("*.py"))
    files = [path for path in files if "__pycache__" not in path.parts]
    if not files:
        print(f"ERROR: no Python files found under {SCRIPT_DIR}", file=sys.stderr)
        return 1
    problems = [issue for file in files for issue in check_file(file)]
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"Python syntax/reference checks passed for {len(files)} files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
