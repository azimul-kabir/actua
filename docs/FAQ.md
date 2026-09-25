# Frequently Asked Questions

## What is Actua?

Actua is an independent, native Android client for [Actual
Budget](https://actualbudget.org/), built with Kotlin and Jetpack Compose. It
connects directly to your own self-hosted Actual server, keeps a local copy
of your budget on your device for offline use, and synchronizes changes back
to Actual. Actua is not affiliated with or endorsed by the Actual Budget
team — see [README.md](../README.md).

## Do I need an Actual server to use Actua?

Yes, for real budgets. Actua is a client for [Actual
Budget](https://actualbudget.org/) — it doesn't host budgets itself, so you
need your own Actual server (self-hosted or otherwise) to sync against.

If you just want to try the app first, open **Manage → Connection & Data →
Try demo budget** to explore a fully local, pre-seeded sample budget with no
server required.

## What Android versions are supported?

Actua requires **Android 9 (API 28) or newer**.

## Where do I download Actua?

- [GitHub Releases](https://github.com/azimul-kabir/actua/releases) for the
  latest APK
- [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/azimul-kabir/actua)
  for update notifications and in-place upgrades

See the README for current details on other distribution channels.

## Is Actua safe to connect to my real budget?

Actua can write changes to your synchronized Actual budget, the same as any
other Actual client. As with any new client, back up important budgets
before connecting Actua to them. See [BACKEND_PARITY.md](BACKEND_PARITY.md)
for what's implemented and tested against Actual.

## What login methods does Actua support?

Password login and OpenID/OIDC login through Actual's own login flow,
including servers that support both methods at once.

## Does Actua work offline?

Yes. Actua keeps a local, on-device copy of your budget so you can view and
edit it without a connection. Offline edits are queued and synchronized with
your Actual server the next time you're online, using Actual's own
CRDT-based sync protocol for convergence.

## Can I connect to a self-hosted server with a self-signed certificate?

Yes. Actua trusts manually installed user CAs (not just the system trust
store), so a self-signed or private-CA certificate on a self-hosted server
works without rooting the device. You can also trust a single server
certificate explicitly, per host, after reviewing its fingerprint —
hostname verification still applies. Private-LAN HTTP addresses are also
supported, with automatic failover between a primary and fallback server
address.

## Does Actua support bank sync?

Yes, through Actual's server-hosted bank sync providers: SimpleFIN and
GoCardless are supported for account discovery, linking and transaction
download. Pluggy.ai credential storage exists on the server side, but
account discovery/linking/download for it isn't implemented yet. See
[BACKEND_PARITY.md](BACKEND_PARITY.md) for the current status.

## Can I import transactions from a file, SMS, or notifications?

Yes. Actua supports CSV, XLSX, and text-based PDF imports with configurable
column/date/sign mapping and duplicate detection, plus on-device parsing of
pasted/shared SMS text and opt-in notifications from an explicit allow-list
of apps. All of this parsing happens on-device.

## What data does Actua collect?

None beyond what's needed to sync your budget with your own Actual server.
Actua has no analytics, ads, crash reporting, or third-party tracking SDKs.
See [PRIVACY.md](../PRIVACY.md) for the full policy, including how
credentials, encryption keys, backups, and the optional location-aware
payee feature are handled.

## Where are my credentials and encryption keys stored?

In the Android Keystore, which isn't readable by other apps. They're never
logged and are only ever sent to the Actual server you configure, as part
of authentication and sync.

## How do backups and restore work?

Actua automatically creates local backups of your budget on-device. Backups
are not uploaded anywhere. You can restore from a backup from Actua's data
management screens.

## What's the relationship between Actua and Actuali?

Actua was originally based on and informed by
[Actuali](https://github.com/MattFaz/actuali), the open-source iOS client by
Matt Farrell. Actua is an independent reimplementation in Kotlin and Jetpack
Compose (not a shared-code port), aiming for behavioral and protocol
compatibility with Actual Budget while using native Android UI and
platform integrations. See [BACKEND_PARITY.md](BACKEND_PARITY.md) and
[NOTICE.md](../NOTICE.md) for details and credits.

## I found a bug or have a feature request — where do I report it?

[Open an issue on GitHub](https://github.com/azimul-kabir/actua/issues/new/choose).
Please don't include sensitive data (real budget contents, credentials,
tokens) in issues or screenshots.

## Where can I discuss Actua or get help?

Join the [Discord server](https://discord.gg/FyGxRjmhw) to discuss Actua,
test beta builds, or get involved with development.
