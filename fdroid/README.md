# F-Droid submission

This directory contains a submission template for the official `fdroiddata` repository. It is not consumed by the Actua Android build.

## First submission target

Actua `1.0.0-beta.9` (`versionCode 26`) is the first intended F-Droid submission target. The GitHub release APK continues to use Actua's persistent signing identity.

Release/build commit:

`bdd92875ea07c3ff2759b708c80076a402d3f0a0`

Published APK SHA-256:

`d5a9ee7c52a595b8d032b8342fccc5c806bbb69978c1e1c5a90faf8cb3d3abc0`

Expected SHA-256 signing-certificate fingerprint:

`2a37719a0770ce4e03f3c080bd9d780c22300e28db678823b5595c9ad55753cc`

The fdroiddata template uses `Binaries`/`binary` plus `AllowedAPKSigningKeys` so F-Droid can publish the upstream Actua-signed APK only after its own source rebuild verifies as reproducible.

## Verification completed

- The `v1.0.0-beta.9` release and GitHub Actions release job both point to the exact commit above.
- The release workflow verified the APK successfully with Android `apksigner` using APK Signature Scheme v2.
- The release certificate SHA-256 fingerprint matches the expected persistent Actua signing identity above.
- The published APK was downloaded independently by the `F-Droid Verify` GitHub Actions workflow and passed `fdroid scanner --exit-code` with a clean result.
- `com.azimulkabir.actua.yml` now contains the exact full commit SHA for version 26.

## Remaining submission checks

1. Copy `com.azimulkabir.actua.yml` to `fdroiddata/metadata/com.azimulkabir.actua.yml` in a fork of the official `fdroiddata` repository.
2. In the fdroiddata checkout, run the normal metadata/build checks, including `fdroid readmeta`, `fdroid lint`, `fdroid checkupdates`, and an F-Droid build-server build/reproducibility verification.
3. Submit the fdroiddata merge request only after those checks pass.

## Build-recipe note

The root `settings.gradle.kts` uses the Foojay toolchain-resolver plugin. The F-Droid recipe removes that root `plugins` block during `prebuild`; the app itself does not depend on Foojay at runtime. F-Droid's normal Android build preparation handles release signing configuration, while reproducible verification compares its rebuilt APK with the developer-signed GitHub binary.
