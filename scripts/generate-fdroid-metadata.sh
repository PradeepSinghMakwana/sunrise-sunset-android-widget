#!/usr/bin/env bash
set -euo pipefail

TAG="${GITHUB_REF_NAME:?GITHUB_REF_NAME is required}"
VERSION="${TAG#v}"
GRADLE_FILE="app/build.gradle.kts"
VERSION_NAME="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE_FILE" | head -n1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | head -n1)"

[[ "$VERSION_NAME" == "$VERSION" ]] || { echo "Tag/versionName mismatch" >&2; exit 1; }
[[ -n "$VERSION_CODE" ]] || { echo "versionCode missing" >&2; exit 1; }

mkdir -p release-reports
cat > release-reports/com.sunrisesunset.clock.yml <<EOF
Categories:
  - Time
License: MIT
AuthorName: Pradeep Singh
SourceCode: https://github.com/PradeepSinghMakwana/sunrise-sunset-clock
IssueTracker: https://github.com/PradeepSinghMakwana/sunrise-sunset-clock/issues
Changelog: https://github.com/PradeepSinghMakwana/sunrise-sunset-clock/releases
AutoName: Sunrise Sunset Clock
Summary: A home-screen clock with local sunrise and sunset times
Description: |-
  A lightweight Android home-screen widget that displays the local time,
  sunrise, and sunset using the device's approximate location.

  Features include a live digital clock, daily sunrise and sunset
  calculations, approximate location permission only, and offline
  solar-time calculation.

  Location coordinates are stored locally on the device. The app does
  not require an account, API key, or external location service.
RepoType: git
Repo: https://github.com/PradeepSinghMakwana/sunrise-sunset-clock.git
CurrentVersion: '$VERSION_NAME'
CurrentVersionCode: $VERSION_CODE
Builds:
  - versionName: '$VERSION_NAME'
    versionCode: $VERSION_CODE
    commit: $TAG
    subdir: .
    target: android-35
    gradle:
      - yes
EOF

echo "Generated F-Droid metadata for $VERSION_NAME ($VERSION_CODE)"
