#!/usr/bin/env python3
"""Validate Android AVD hardware-profile IDs against avdmanager output."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

PROFILE_ID = re.compile(r'^\s*id:\s*\d+\s+or\s+"([^"]+)"\s*$', re.MULTILINE)


def parse_device_profile_ids(output: str) -> set[str]:
    """Extract exact AVD profile IDs from `avdmanager list device` output."""
    return set(PROFILE_ID.findall(output))


def find_avdmanager() -> str:
    """Resolve avdmanager across PATH and standard Android SDK cmdline-tools layouts."""
    executable = shutil.which("avdmanager")
    if executable:
        return executable
    sdk_roots = {
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        str(Path.home() / "Android" / "Sdk"),
    }
    candidates = []
    for root in filter(None, sdk_roots):
        base = Path(root) / "cmdline-tools"
        candidates.extend([base / "latest" / "bin" / "avdmanager",
                         base / "bin" / "avdmanager"])
        if base.is_dir():
            candidates.extend(sorted(base.glob("*/bin/avdmanager"), reverse=True))
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise FileNotFoundError(
        "avdmanager not found on PATH or in ANDROID_HOME/ANDROID_SDK_ROOT cmdline-tools"
    )


def require_profile_exists(profile_id: str, output: str) -> None:
    """Fail closed unless the exact requested profile ID is listed."""
    available = parse_device_profile_ids(output)
    if profile_id not in available:
        choices = ", ".join(sorted(available)) or "(none found)"
        raise ValueError(
            f"AVD profile {profile_id!r} is unavailable. "
            f"Exact IDs reported by avdmanager: {choices}"
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile_id", help="Exact AVD profile ID, e.g. Nexus 5X")
    args = parser.parse_args()

    try:
        executable = find_avdmanager()
        result = subprocess.run(
            [executable, "list", "device"],
            check=True, capture_output=True, text=True,
        )
        require_profile_exists(args.profile_id, result.stdout + result.stderr)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"AVD profile preflight failed: {error}", file=sys.stderr)
        return 1

    print(f"AVD profile preflight passed: {args.profile_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
