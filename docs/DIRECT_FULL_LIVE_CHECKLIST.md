# Direct Full: sandbox and go-live checklist

Website purchase activation is sandbox-only. This lists what is still unverified
and what must be done before live sales. See [DIRECT_FULL.md](DIRECT_FULL.md) for
how the sandbox flow works today.

Verified so far (sandbox, 2026-11-20): the pinned public key in
`SandboxEntitlementVerifier.kt` matches the published sandbox key `sandbox-1`;
Paddle delivered every subscribed webhook on the first attempt; the backend recorded
each purchase and the one refund; the production `actua` Pages project has no Paddle
configuration. A Play install is signed with Google's app signing key, which differs
from the release key on both GitHub APKs, so Play and GitHub installs cannot update
each other.

## A. Finish sandbox verification

### 1. Email recovery
Never exercised: the recovery tables are empty.

- [ ] Request recovery for a sandbox purchase email on the sandbox site.
- [ ] The email arrives from the configured sender and the link or code works once.
- [ ] Redeeming on a device yields a new recovery credential and the supporter thank-you.
- [ ] Reusing the same link is rejected (the challenge is consumed).
- [ ] Exceeding the hourly request limit is throttled.
- [ ] The database shows the challenge consumed and redeemed, and no email address or
      credential appears in plain text.

### 2. Refund to device
The webhook and the revocation row are proven; the device side is not.

- [ ] Activate a fresh sandbox purchase on a device.
- [ ] Refund it in the Paddle sandbox.
- [ ] A revocation row with `action=refund` appears.
- [ ] A forced refresh on the device gets no new proof, and supporter status ends once the
      cached proof expires (up to 24 hours).
- [ ] A late `transaction.completed` after the refund does not restore access.

### 3. Proof expiry and offline use
- [ ] Offline, extras keep working until the proof expires.
- [ ] Moving the device clock backwards makes the proof rejected.
- [ ] Restarting the app offline reloads the cached proof without crashing.

### 4. Webhook hardening
- [ ] A bad signature and a stale timestamp are rejected and leave no processed event.
- [ ] The same valid event sent twice has one effect.
- [ ] An event for another product or price is ignored.

### 5. Stuck checkout
- [ ] Decide what to do with the one checkout attempt left in `reconcile` (created
      2026-11-17 09:35 UTC). Paddle never created a transaction for it, so nothing is owed or
      charged, but its recovery code can never start a checkout. Delete the row or leave it.

## B. Device and signing gaps

- [ ] Direct to Direct upgrade: install the previous published `Actua-v<version>.apk`, add a
      budget, install the new release-signed APK over it, and confirm the budget survives.
- [ ] Play to GitHub (and back): confirm the backup, uninstall and restore path works with a
      real budget, since a direct update is not possible.
- [ ] Run `SandboxPurchaseStoreTest` on an API 28 device or emulator.
- [ ] Check the Support page and the locked pickers at large font scale and on small screens.
- [ ] Measure with the physical-device Macrobenchmark before and after, if a performance
      claim is wanted.
- [ ] Check the Play Support page (prices, restore, manage subscription) on a device with
      Play services.

## C. Before going live

These depend on the Paddle dashboard and are not visible from the repository.

- [ ] Paddle live account approved and verified, with the intended payout details.
- [ ] Live product and one-time price created, and their IDs recorded.
- [ ] Live webhook destination for the production host, subscribed to the same events.
- [ ] A new live signing key (not `sandbox-1`) and a live issuer, audience and environment
      in the verifier. This needs an Android change and a release.
- [ ] A separate production backend: its own Pages project, database, secrets and Turnstile
      configuration. Do not reuse the sandbox project.
- [ ] A key rotation and revocation plan.
- [ ] `PRIVACY.md`, the Play Data safety answers and the website policy pages updated for
      live purchases and email recovery.
- [ ] Decide what Direct users see until the above is done; keep the "coming soon" screen
      until then.
