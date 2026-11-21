# Semantic Versioning policy

All public stable releases follow Semantic Versioning (SemVer 2.0.0) and use Git tags prefixed with `v`.

## Format

- Stable: `vMAJOR.MINOR.PATCH` (e.g. `v2.0.0`)
- Pre-release: `vMAJOR.MINOR.PATCH-alpha.N`, `-beta.N`, or `-rc.N`
- Numeric components cannot contain leading zeroes.
- Build metadata is not used for release tags.

## Increment rules

- **PATCH**: compatible bug fixes, stability improvements, internal corrections.
- **MINOR**: backward-compatible user-facing functionality.
- **MAJOR**: breaking changes or significant product/API changes.
- Pre-release suffixes identify candidates; the stable tag omits the suffix.

Android `versionName` must exactly match the tag without the leading `v`. Android `versionCode` is a positive integer and must strictly increase for each published APK. Never reuse a versionCode.

## Automated gates

Release tags trigger release workflow validation. Invalid tags and tag/versionName mismatches are rejected. The workflow must complete required script tests, JVM tests, Android 13 emulator UI/screenshot checks, permission/dependency audits, and APK build before publishing.

Release signing is mandatory for consumer-installable APKs. The current workflow produces an unsigned APK; do not distribute it as a consumer release until signing and signature verification are implemented and configured. GitHub Actions secrets are the intended credential storage.

## Changelog

GitHub-generated release notes are used initially. Notes should cover user-facing changes and fixes since the previous release, and pre-release versions must be clearly labeled as candidates.
