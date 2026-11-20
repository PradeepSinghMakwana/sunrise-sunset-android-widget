#!/usr/bin/env python3
"""Validate Android AVD hardware-profile IDs against avdmanager output."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys


PROFILE_ID = re.compile(r'^\s*id:\s*\d+\s+or\s+"([^"]+)"\s*$', re.MULTILINE)


def parse_device_profile_ids(output: str) -> set[str]:
    """Extract exact AVD profile IDs from `avdmanager list device` output."""
    return set(PROFILE_ID.findall(output))


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
    parser.add_argument("profile_id", help="Exact profile ID, e.g. pixel_7_pro")
    args = parser.parse_args()

    try:
        result = subprocess.run(
            ["avdmanager", "list", "device"],
            check=True,
            capture_output=True,
            text=True,
        )
        require_profile_exists(args.profile_id, result.stdout + result.stderr)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"AVD profile preflight failed: {error}", file=sys.stderr)
        return 1

    print(f"AVD profile preflight passed: {args.profile_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
