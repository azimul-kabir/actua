# F-Droid submission

This directory contains a submission template for the official `fdroiddata` repository. It is not consumed by the Actua Android build.

## First submission target

Actua `1.0.0-beta.9` (`versionCode 26`) is the first intended F-Droid submission target. The GitHub release APK continues to use Actua's persistent signing identity.

The expected SHA-256 signing-certificate fingerprint is:

`2a37719a0770ce4e03f3c080bd9d780c22300e28db678823b5595c9ad55753cc`

The fdroiddata template uses `Binaries`/`binary` plus `AllowedAPKSigningKeys` so F-Droid can publish the upstream Actua-signed APK only after its own source rebuild verifies as reproducible.

## After beta.9 is published

1. Confirm the `v1.0.0-beta.9` GitHub release targets the exact commit that produced the APK. Do not reuse or move an older tag.
2. Verify `Actua-v1.0.0-beta.9.apk` with `apksigner verify --verbose --print-certs` and confirm the certificate SHA-256 fingerprint above.
3. Run the F-Droid binary scanner against the published APK and require a clean result.
4. Replace `__BETA9_COMMIT_SHA__` in `com.azimulkabir.actua.yml` with the full release/build commit SHA. F-Droid build metadata should use a full commit hash, not a branch or tag name.
5. Copy the resulting file to `fdroiddata/metadata/com.azimulkabir.actua.yml` in a fork of the official `fdroiddata` repository.
6. In the fdroiddata checkout, run the normal metadata/build checks, including `fdroid readmeta`, `fdroid lint`, `fdroid checkupdates`, `fdroid scanner`, and an F-Droid build-server build/verification.
7. Submit the fdroiddata merge request only after those checks pass.

## Build-recipe note

The root `settings.gradle.kts` uses the Foojay toolchain-resolver plugin. The F-Droid recipe removes that root `plugins` block during `prebuild`; the app itself does not depend on Foojay at runtime. F-Droid's normal Android build preparation handles release signing configuration, while reproducible verification compares its rebuilt APK with the developer-signed GitHub binary.
