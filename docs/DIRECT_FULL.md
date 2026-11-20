# Full distributions

The `playstore` build uses Google Play Billing. The `direct` build includes the
same on-device receipt scanner and cosmetic settings, with sandbox website
purchase activation in debug/instrumented builds only. Both retain the existing
application ID. The two GitHub APKs are signed with the release key (certificate
SHA-256 `2a37719a0770ce4e03f3c080bd9d780c22300e28db678823b5595c9ad55753cc`, also the
Play upload key). Google Play re-signs the Play build with its own app signing key
(`e3a3731c83f4b09aa8da181735379dd7e4ab8993a3d775220e50d8189bc66a21`, checked in Play
Console), so a Play install and a GitHub APK cannot update each other: Android rejects
the update, and switching means uninstalling after a backup. This follows from the
certificates; an actual cross-install was not run.

Existing GitHub asset names remain: `Actua-v<version>.apk` is Direct Full and
`Actua-v<version>-full.apk` is Play Full. F-Droid verification automation is retired;
historical F-Droid submission metadata is retained for reference.

## Sandbox testing

Build Direct debug, open Settings → Support Actua, and enter a saved sandbox
purchase recovery credential. The app calls the isolated sandbox service and
verifies ES256 with key ID `sandbox-1`, the pinned P-256 public key, exact sandbox
issuer/audience/environment, and a maximum 24-hour validity. A signature from the
actual deployment still needs an end-to-end device check.

Credentials and proofs are AES-GCM encrypted with an Android Keystore key and
stored in the app's no-backup directory. They do not enter budget sync or backups.
A valid cached proof permits offline cosmetics until expiry, bounded by monotonic
time in the running process. Wall time earlier than the persisted verification
time rejects the proof. Restart requires reloading/rechecking on a background
thread. A refund prevents renewal; existing signed proofs may remain valid for
up to 24 hours. Sandbox and Play supporter caches are separate.

Release Direct builds have no live activation yet and reject sandbox ownership.
Live keys, live sales, release-device upgrade checks, and physical-device
Macrobenchmark measurements remain outside the verified sandbox boundary.

## Checkout return and locked extras

The sandbox website completion screen links to the separate test package with a
fixed `actua-supporter-sandbox://return` intent. Only Direct debug/instrumented
builds register that route. Query parameters, fragments and release/Play builds
cannot turn it into an activation request. Returning opens Support Actua and
refreshes an existing saved credential; otherwise enter the saved purchase key.

Settings shows the supporter thank-you after verified activation. Non-supporters
can discover all extras, including greeting and nickname customization, as locked
rows with a Support Actua action. Free Material You/Violet accent choices remain
available. Android follow-up: #1698; website follow-up: actua-website#35 / PR #36.
