# Privacy Policy

_Last updated: 2026-10-13_

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

## Bank notifications and automation apps

Capturing bank notifications is off by default. It works only after you turn
it on, choose which apps to read, and grant Actua notification access in
Android's settings. Actua then reads the title and text of new notifications
from those apps only, on your device. It keeps just the transaction details it
recognizes (date, amount, payee, reference, a card or account suffix, the
source app's name) in a review queue on your device; it does not store the
notification itself and ignores notifications from every other app.

Transactions sent by Tasker or other automation apps are accepted only while
you have turned that option on, only with the private token Actua shows you,
and they join the same queue. Nothing in the queue is written to your budget
until you approve it.

To pre-fill accounts, Actua remembers on your device the account you chose
for each card or source and an optional default account, per budget. Deleting
notification data on the import screen removes the queue, those choices, the
parser settings and the token.

## Notifications

Actua can post notifications only when you turn them on: credit-card payment
reminders, scheduled-transaction reminders, and a notification for each
captured transaction. They are created on your device from your budget data
and never sent through a notification service. Scheduled-transaction
reminders and capture notifications hide their details on a secure lock
screen.

## Local network access

On Android 17 and later, Actua asks for Nearby devices (local network) access
only when your Actual server's address is on your local network. It is used
only to reach that server.

## No analytics, ads or tracking

Actua does not include analytics, crash reporting, advertising or other
third-party tracking SDKs, and does not collect usage data about you.

## Diagnostics

Actua keeps a short log of recent app activity on your device (Settings →
About → Diagnostics) to help troubleshoot problems: crashes, failed actions,
screens opened, syncs and server requests. It records error types, code
locations, request paths, status codes, timings and counts only. It never
records error text, your server address, passwords, tokens, encryption keys or
budget data.
Entries older than seven days are deleted, and you can clear the log at any
time. The log never leaves your device unless you copy, save or email the
report yourself.

## Backups and Google Drive

Automatic local backups stay in private app storage on your device and are not
uploaded anywhere unless you configure an optional destination.

If you choose to enable Google Drive backup:
- Actua connects directly to the Google Drive API to upload encrypted backup
  archives to your personal Google Drive account.
- Actua requests least-privilege access strictly to files it creates
  (`https://www.googleapis.com/auth/drive.file`). It cannot access your other
  Google Drive files or folders.
- Backup archives are encrypted on-device before transmission using client-side
  authenticated AES-256-GCM encryption with a key derived from your backup
  password. Google cannot decrypt or view your budget contents.
- OAuth tokens and encryption keys are stored using the Android Keystore in
  private app storage and are explicitly excluded from Android cloud backups
  and device-to-device transfers.
- You can disconnect Google Drive and revoke access at any time from Settings →
  Backups.

## Other on-device data

Actua keeps app settings, which bank-synced transactions you haven't viewed
yet, and similar display state on your device. None of it is sent anywhere.

## Contact

Questions about this policy can be sent to
[actua@azimulkabir.com](mailto:actua@azimulkabir.com), or raised as an
issue on [GitHub](https://github.com/azimul-kabir/actua/issues).
