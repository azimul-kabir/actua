# Privacy Policy

_Last updated: 2026-09-25_

Actua is an independent, community-built Android client for [Actual
Budget](https://actualbudget.org/). It is not affiliated with or endorsed by
the Actual Budget team. This policy explains what data Actua handles and
where it goes.

## Your budget data

Actua connects directly to the Actual server **you** configure — your own
self-hosted instance. Budget data (accounts, transactions, categories,
budgets, rules, schedules and reports) is synchronized between that server
and your device using Actual's end-to-end encrypted sync protocol, and a copy
is kept in a local, on-device SQLite database so the app works offline.

Actua does not operate a server of its own, and your budget data is never
sent to the developer or to any third party controlled by this project. The
only network destination for your budget data is the Actual server you
provide.

## Credentials and encryption keys

Server credentials and sync/encryption keys are stored using the Android
Keystore and are not readable by other apps. They are never logged or
transmitted anywhere other than to your configured Actual server as part of
authentication and sync.

## Location-aware payees

Actua can optionally suggest payees based on your location while the app is
open. This feature is off by default, requires you to explicitly grant
foreground location permission, and does not run in the background. When
enabled, any saved payee coordinates are stored as part of your budget data
(local SQLite database and your Actual server, subject to the same sync
described above) — not with any separate location or advertising service.
You can inspect or delete saved payee locations at any time from Settings →
Privacy → Payee Locations, or turn the feature off to stop recording new
locations.

## Imports

CSV, XLSX, PDF, SMS and notification imports are processed entirely on your
device to create transactions in your budget. Imported files and their
contents are not uploaded anywhere except as part of the transactions they
produce, which sync to your own Actual server like any other budget data.

## No analytics, ads or tracking

Actua does not include analytics, crash reporting, advertising or other
third-party tracking SDKs, and does not collect usage data about you.

## Backups

Automatic local backups are stored on your device's storage and are not
uploaded anywhere by Actua.

## Contact

Questions about this policy can be sent to
[actua.mobile@gmail.com](mailto:actua.mobile@gmail.com), or raised as an
issue on [GitHub](https://github.com/azimul-kabir/actua/issues).
