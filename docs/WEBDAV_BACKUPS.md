# WebDAV backups

Actua can keep encrypted backups on a WebDAV server you control, such as Nextcloud, ownCloud or a NAS, as an
alternative to Google Drive. Nothing is sent unencrypted: each backup is encrypted on the phone in the same
ACTUABKP v1 container used for Google Drive backups, with a backup password you choose, before it is uploaded.

This is not an Actual Budget feature. Actual has no WebDAV backup, so it is an Actua-only convenience and does
not touch the budget database, sync or the message log.

## Set up

Backups → WebDAV backup → Connect.

| Field | What to enter |
| --- | --- |
| Server address | The WebDAV address of your files, for example `https://cloud.example.com/remote.php/dav/files/you/` |
| Username | Your account name |
| App password | An app password (Nextcloud: Settings → Security → Devices & sessions). Do not use your account password. |
| Backup folder | A folder created below the address, default `Actua Backups` |

Actua checks the address and credentials, creates the folder, and only then saves them. Set a backup password
afterwards: without it nothing can be uploaded, and without it an encrypted backup cannot be restored on another
phone, so keep it somewhere safe.

Layout on the server: `<address>/<folder>/<budget id>/<date>.actuabkp`. Only `.actuabkp` files in that folder are
ever listed, downloaded or deleted; retention removes old backups there and nothing else.

## Security notes

- **HTTPS is required.** Plain `http://` is refused unless you turn on "Allow unencrypted HTTP", which warns that
  your username and app password could be read on the network.
- **Self-signed certificates** (common on home NAS devices) can be trusted for that one server after you compare
  its SHA-256 fingerprint. Hostname checking stays on. This uses the same trusted-certificate store as Actual
  servers.
- Redirects between `http` and `https` are not followed, so credentials are never resent to another scheme. If the
  server redirects, use the address it redirects to.
- The app password and the backup password are encrypted with an Android Keystore key and are excluded from Android
  backup and device transfer. Disconnecting removes them from the phone; it does not delete server backups.
- Uploads go to `<name>.actuabkp.part` first and are renamed when complete; the stored size is then compared with
  what was sent. A dropped connection or a truncated file is never listed as a backup. A server that cannot rename
  files (WebDAV `MOVE`) is refused with an explanation.

## What is covered

Back up now, list, and restore into the open budget (with the usual confirmation and safety copy). Restoring on a
fresh install, and scheduled automatic backups, are separate slices of
[#1345](https://github.com/azimul-kabir/actua/issues/1345).

Only standard WebDAV is supported. SMB, SFTP, S3 and vendor sync protocols (Synology Drive, Qsync, UGREEN's own
sync) are not.

## Tested servers

Compatibility is listed only for servers someone actually ran. "Untested" means it has not been tried, not that
it is known to fail. Please add a row (server, version, connection, what you checked) when you try one.

| Server | Version | Connection | Connect | Back up | List | Restore | Delete | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| In-memory fake server (automated tests) | n/a | HTTP, no TLS | Yes | Yes | Yes | Yes | Yes | Covers the request sequence only, not any real server's quirks. |
| Nextcloud | | | Untested | Untested | Untested | Untested | Untested | Uses `/remote.php/dav/files/<user>/` and an app password. |
| ownCloud | | | Untested | Untested | Untested | Untested | Untested | |
| Synology DSM (WebDAV Server package) | | | Untested | Untested | Untested | Untested | Untested | The package must be installed and enabled. |
| QNAP QTS / QuTS hero (WebDAV) | | | Untested | Untested | Untested | Untested | Untested | The WebDAV service must be switched on. |
| UGREEN UGOS | | | Untested | Untested | Untested | Untested | Untested | Not confirmed to offer WebDAV on every model. |
| Apache `mod_dav`, nginx, rclone `serve webdav` | | | Untested | Untested | Untested | Untested | Untested | |
