# F-Droid submission

This directory contains a submission template for the official `fdroiddata` repository. It is not consumed by the Actua Android build.

## Submission approach

Actua is built entirely from source by F-Droid's own build server. The metadata template no longer uses `Binaries`/`binary` or `AllowedAPKSigningKeys`; F-Droid compiles the app itself and signs the resulting APK with its own repository key, the standard model for F-Droid inclusion.

The release build type's signing config (`app/build.gradle.kts`) only applies a keystore when the `ACTUA_KEYSTORE_*` environment variables are present (used by Actua's own GitHub Actions release job). When those variables are absent, as on F-Droid's build server, `assembleRelease` produces an unsigned APK that F-Droid signs itself.

## First submission target

Actua `1.0.0-beta.23` (`versionCode 40`) is the current intended F-Droid submission target.

Release/build commit (`v1.0.0-beta.23`):

`1eed7a1b67dc18cbd73bd99da17f14d1b7ae6abe`

## Remaining submission checks

1. Copy `com.azimulkabir.actua.yml` to `fdroiddata/metadata/com.azimulkabir.actua.yml` in a fork of the official `fdroiddata` repository.
2. In the fdroiddata checkout, run the normal metadata/build checks, including `fdroid readmeta`, `fdroid lint`, `fdroid checkupdates`, and an F-Droid build-server build/reproducibility verification.
3. Submit the fdroiddata merge request only after those checks pass.

## Build-recipe note

The root `settings.gradle.kts` uses the Foojay toolchain-resolver plugin. The F-Droid recipe removes that root `plugins` block during `prebuild`; the app itself does not depend on Foojay at runtime.
