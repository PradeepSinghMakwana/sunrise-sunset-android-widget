#!/usr/bin/env bash
set -euo pipefail

tag="${GITHUB_REF_NAME:-}"
if [[ -z "$tag" ]]; then
  echo "GITHUB_REF_NAME is required." >&2
  exit 1
fi
if [[ ! "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.(0|[1-9][0-9]*))?$ ]]; then
  echo "Invalid release tag '$tag'. Use vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-(alpha|beta|rc).N." >&2
  exit 1
fi
version="${tag#v}"
gradle_file="app/build.gradle.kts"
version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$gradle_file" | head -n1)"
if [[ "$version_name" != "$version" ]]; then
  echo "Tag $tag must match Gradle versionName $version_name." >&2
  exit 1
fi
echo "SemVer tag validated: $tag"
