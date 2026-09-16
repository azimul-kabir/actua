# F-Droid submission

This directory contains a submission template for the official `fdroiddata` repository. It is not consumed by the Actua Android build.

## Submission approach

Actua is built entirely from source by F-Droid's own build server, which signs the resulting APK with its own repository key, the standard model for F-Droid inclusion. The template's `Binaries`/`AllowedAPKSigningKeys` fields let F-Droid additionally verify that its from-source build reproduces the APK Actua's own GitHub Actions release job publishes, signed with the `ACTUA_KEYSTORE_*` keystore.

The release build type's signing config (`app/build.gradle.kts`) only applies that keystore when the `ACTUA_KEYSTORE_*` environment variables are present (used by Actua's own GitHub Actions release job). When those variables are absent, as on F-Droid's build server, `assembleRelease` produces an unsigned APK that F-Droid signs itself before comparing it against the pinned `Binaries` release for reproducibility.

## Submission status

Actua has been accepted into `fdroiddata`. This template is kept in sync with what's merged there; `com.azimulkabir.actua.yml` in this directory reflects the `1.0.0-beta.25` (`versionCode 42`) build recipe as accepted.

## Updating the fdroiddata submission for a new release

1. The `android-release.yml` workflow updates the version/commit fields here automatically on each release and asserts the hand-added fields (`Categories`, `AuthorName`, `Binaries`, `AllowedAPKSigningKeys`, etc.) are still present.
2. Copy `com.azimulkabir.actua.yml` to `fdroiddata/metadata/com.azimulkabir.actua.yml` in a fork of the official `fdroiddata` repository and open a merge request with the update.
3. Run the normal metadata/build checks in the fdroiddata checkout, including `fdroid readmeta`, `fdroid lint`, `fdroid checkupdates`, and an F-Droid build-server build/reproducibility verification, before submitting.

## Build-recipe note

The root `settings.gradle.kts` uses the Foojay toolchain-resolver plugin. The F-Droid recipe removes that root `plugins` block during `prebuild`; the app itself does not depend on Foojay at runtime.
