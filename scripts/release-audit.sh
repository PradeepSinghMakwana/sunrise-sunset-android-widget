#!/usr/bin/env bash
set -euo pipefail

TAG="${GITHUB_REF_NAME:-}"
if [[ -z "$TAG" ]]; then
  echo "GITHUB_REF_NAME is required (expected a version tag such as v2.1)." >&2
  exit 1
fi
VERSION="${TAG#v}"
GRADLE_FILE="app/build.gradle.kts"
MANIFEST="app/src/main/AndroidManifest.xml"
mkdir -p release-reports

VERSION_NAME="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE_FILE" | head -n1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | head -n1)"

if [[ "$VERSION_NAME" != "$VERSION" ]]; then
  echo "Tag $TAG does not match app versionName $VERSION_NAME" >&2
  exit 1
fi
if [[ ! "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.(0|[1-9][0-9]*))?$ ]]; then
  echo "FAIL: tag is not valid SemVer (vMAJOR.MINOR.PATCH[-alpha|beta|rc.N])" >&2
  exit 1
fi
if [[ -z "$VERSION_CODE" ]]; then
  echo "Could not read versionCode from $GRADLE_FILE" >&2
  exit 1
fi

{
  echo "Release tag: $TAG"
  echo "Version name: $VERSION_NAME"
  echo "Version code: $VERSION_CODE"
  echo
  echo "Declared Android permissions:"
  grep -oE '<uses-permission[^>]+' "$MANIFEST" | sed -E 's/.*android:name="([^"]+)".*/- \1/' || true
  echo
  echo "Permission policy checks:"
  if grep -q 'android.permission.ACCESS_FINE_LOCATION' "$MANIFEST"; then
    echo "FAIL: precise location permission is declared"
    exit 1
  else
    echo "PASS: no ACCESS_FINE_LOCATION declaration"
  fi
  if ! grep -q 'android.permission.ACCESS_COARSE_LOCATION' "$MANIFEST"; then
    echo "FAIL: approximate location permission is missing"
    exit 1
  else
    echo "PASS: ACCESS_COARSE_LOCATION is declared"
  fi
} | tee release-reports/permissions-and-version.txt

echo "Release audit passed for $TAG"
