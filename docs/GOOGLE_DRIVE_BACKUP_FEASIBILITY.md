# Google Drive Backup Feasibility & Architecture Decision Record

- **Issue Reference:** Closes [#1171](https://github.com/azimul-kabir/actua/issues/1171) (Technical Feasibility Spike).
- **Target Feature:** Architecture contract for [#1168](https://github.com/azimul-kabir/actua/issues/1168) (Optional Google Drive Backup and Restore).
- **Status:** Validated & Accepted.

---

## 1. Executive Summary

This investigation validates the feasibility of adding optional Google Drive backup and restore to Actua without introducing proprietary dependencies, compromising user financial privacy, or turning Google Drive into a real-time sync engine.

### Core Verdict
1. **Scope:** `https://www.googleapis.com/auth/drive.file` is fully sufficient for creating, listing, downloading, updating, and pruning Actua backup archives within a dedicated "Actua Backups" folder. It avoids the invasive and costly CASA Tier 2 verification required by full Drive scopes.
2. **F-Droid Feasibility:** Google Drive backup **can be 100% compliant with F-Droid** without adding proprietary Google Play Services client SDKs (`play-services-auth`). By adopting standard RFC 8252 OAuth 2.0 with PKCE via Chrome Custom Tabs / system browser and the standard Google Drive REST API v3 over HTTPS, both the `playstore` and `fdroid` flavors share the same clean, open-source codebase.
3. **Encryption Architecture:** The existing CRDT server-sync encryption format (`SyncEncryption.kt`) is **not** suitable for multi-hundred-megabyte backup archives due to monolithic memory buffering. We specify and implement a versioned, streaming authenticated encryption container (`ACTUABKP` v1) using chunked AES-256-GCM, PBKDF2-HMAC-SHA512 (100,000 iterations), and a header-based canary token for instant passphrase validation.
4. **Token & Credential Safety:** OAuth tokens are encrypted at rest using the Android Keystore (`AES/GCM/NoPadding`) in `actua_gdrive_auth.xml`, which is explicitly excluded from Android cloud backups and device-to-device transfers in `data_extraction_rules.xml` and `backup_rules.xml`.

---

## 2. API Scope & Limitations

### 2.1 Scope Comparison

| Scope | Description | User Visibility | Verification Complexity | Feasibility |
|---|---|---|---|---|
| `https://www.googleapis.com/auth/drive.file` | Per-file access to files created or opened by the app | Visible in Google Drive UI (e.g. `Actua Backups/`) | Sensitive scope (standard Google verification; **no CASA Tier 2**) | **Recommended** |
| `https://www.googleapis.com/auth/drive.appdata` | Isolated hidden Application Data folder | Invisible to user in Drive UI | Sensitive scope | Not recommended (users cannot export or verify backups) |
| `https://www.googleapis.com/auth/drive` | Full read/write access to user's entire Google Drive | Full | Restricted scope (Mandatory annual CASA Tier 2 security assessment, \$5k–\$15k/yr) | **Rejected** (violates least-privilege) |

### 2.2 Behavior and Capabilities under `drive.file`
- **Creation:** Actua creates an "Actua Backups" root folder in the user's Drive root and subfolders per budget (e.g. `Actua Backups/{budgetId}/`).
- **Listing:** Actua can query all files it has created (`trashed = false and '{folderId}' in parents`).
- **Re-installation & Cross-device Access:** Files created by Actua's registered OAuth client ID remain fully accessible to Actua after app reinstall or when installed on a second device, provided the user signs into the same Google account.
- **Limitation:** Actua **cannot** discover or list arbitrary zip files uploaded by the user through the Google Drive web browser or third-party apps unless the user explicitly selects the file using Android's system document picker (`ACTION_OPEN_DOCUMENT`). This is an intentional security sandbox and fits Actua's restore flow.

---

## 3. OAuth Architecture & Distribution Requirements

### 3.1 Zero-Proprietary-SDK OAuth 2.0 with PKCE (RFC 8252 / RFC 7636)
To keep the `fdroid` flavor free of non-free binaries, Actua uses standard browser-based OAuth with PKCE:
1. **Initiation:** Actua generates a cryptographically secure random `code_verifier` and derives `code_challenge = Base64Url(SHA-256(code_verifier))`.
2. **Consent Request:** Actua launches a Chrome Custom Tab (falling back to the default system browser) pointing to:
   ```text
   https://accounts.google.com/o/oauth2/v2/auth?
     client_id={CLIENT_ID}&
     redirect_uri=com.azimulkabir.actua:/oauth2redirect&
     response_type=code&
     scope=https://www.googleapis.com/auth/drive.file&
     code_challenge={CHALLENGE}&
     code_challenge_method=S256&
     access_type=offline&
     prompt=consent
   ```
3. **Authorization Capture:** An activity with intent filter handles `com.azimulkabir.actua:/oauth2redirect` and extracts the authorization `code`.
4. **Token Exchange:** Actua exchanges the `code` and `code_verifier` directly with `https://oauth2.googleapis.com/token` via standard HTTPS `POST` (`HttpURLConnection`), receiving `access_token` and `refresh_token`.
5. **Token Refresh:** When `isExpired` is true, the refresh token is exchanged via `grant_type=refresh_token`.

### 3.2 Distribution Matrix

| Flavor / Channel | Signing Fingerprint | OAuth Client Type | Play Services Dependency | Status |
|---|---|---|---|---|
| **Play Store** (`playstore`) | Google Play App Signing SHA-1 | Android Client / Web Client PKCE | None required | **Supported** |
| **GitHub Releases** (`fdroid`) | Actua Release Keystore SHA-1 | Android Client / Web Client PKCE | None | **Supported** |
| **F-Droid** (`fdroid`) | F-Droid Signing Key SHA-1 | Web Client PKCE / Custom Client ID | None (Zero proprietary code) | **Supported** |

> [!NOTE]
> Because F-Droid builds are signed with F-Droid's own keys and reproducible build requirements, using the Web Client / PKCE redirect flow allows identical functionality across all builds without requiring Google Play Services binaries.

---

## 4. Versioned Client-Side Authenticated Encryption (`ACTUABKP` v1)

### 4.1 Why Server-Sync Encryption Cannot Be Reused As-Is
Actua's existing `SyncEncryption.kt` was designed for small CRDT message batches:
- Loads entire payload into contiguous JVM `ByteArray` in memory. On large archives (up to 500 MB), this triggers `OutOfMemoryError` on 32-bit runtimes or devices with constrained heap.
- Uses 10,000 PBKDF2 iterations, which is lower than recommended for long-term at-rest financial backups stored on third-party cloud infrastructure.
- Lacks a fast verification canary, requiring full decompression/decryption before detecting an invalid passphrase.

### 4.2 The `ACTUABKP` Version 1 Container Format

```
+---------------------------------------------------------------------------------+
| Magic: "ACTUABKP" (8 bytes)                                                    |
+---------------------------------------------------------------------------------+
| Version: 0x01 (1 byte)                                                          |
+---------------------------------------------------------------------------------+
| Salt: 16 bytes (Cryptographically secure random)                                |
+---------------------------------------------------------------------------------+
| KDF Iterations: 4 bytes big-endian Int (100,000)                                |
+---------------------------------------------------------------------------------+
| Canary IV: 12 bytes                                                             |
+---------------------------------------------------------------------------------+
| Canary Ciphertext + GCM Tag: 32 bytes (Encrypted "ACTUA_CANARY_OK!")            |
+---------------------------------------------------------------------------------+
| Streaming Chunks (repeated until EOF):                                          |
|   - Chunk length: 4 bytes Int (length of ciphertext + tag)                      |
|   - Final flag: 1 byte (1 if last chunk, 0 otherwise)                           |
|   - Chunk IV: 12 bytes (incorporates 64-bit monotonic chunk counter)            |
|   - Ciphertext + AES-GCM Tag (16 bytes)                                         |
+---------------------------------------------------------------------------------+
```

### 4.3 Key Derivation & Recovery Key Flow
- **Key Derivation:** `PBKDF2WithHmacSHA512`, 100,000 iterations, 256-bit output key.
- **Canary Validation:** `EncryptedBackupArchive.validatePassphrase(stream, passphrase)` reads only the first 73 bytes of the header and immediately verifies password correctness without reading the multi-megabyte backup payload.
- **Chunk Nonce Security:** Every chunk receives a unique 12-byte IV consisting of 4 random bytes concatenated with an 8-byte big-endian chunk index. This strictly guarantees IV uniqueness and protects against chunk reordering, insertion, or truncation attacks.
- **Recovery Key:** Users can either choose a custom passphrase or generate a high-entropy 128-bit passphrase. The passphrase is never transmitted to Google Drive.

---

## 5. Token Storage, Revocation & Backup Exclusion

### 5.1 Keystore Storage
Tokens are stored in `actua_gdrive_auth.xml` encrypted with `AES/GCM/NoPadding` using the Android Keystore key alias `actua_gdrive_tokens`.

### 5.2 Android Backup & Device Transfer Rules
Both `data_extraction_rules.xml` (Android 12+) and `backup_rules.xml` (Android 11-) are configured:
```xml
<exclude domain="sharedpref" path="actua_gdrive_auth.xml" />
<exclude domain="sharedpref" path="gdrive_backup_encryption.xml" />
```
Because Android Keystore keys never leave the hardware-backed secure enclave, excluding these files prevents unreadable ciphertext from persisting onto a transferred device or cloud backup.

### 5.3 Revocation & Disconnect
When the user selects "Disconnect Google Drive":
1. Actua issues `POST https://oauth2.googleapis.com/revoke?token={token}` to revoke Google authorization on the server.
2. The local encrypted token store is wiped.
3. Any active Drive upload WorkManager jobs are canceled.

---

## 6. Privacy & Google Cloud Verification Prerequisites

### 6.1 Google Cloud Console Configuration
- **Application Type:** Android and Web Application (for PKCE redirect).
- **Authorized Redirect URIs:** `com.azimulkabir.actua:/oauth2redirect`.
- **Scopes:** `https://www.googleapis.com/auth/drive.file`.
- **Verification Tier:** Sensitive scope verification.
  - Requires: Privacy policy URL, YouTube demo video illustrating the backup/restore flow, explanation of least-privilege necessity.
  - Does **not** require CASA Tier 2 assessment (which applies only to restricted scopes like `drive` or `drive.readonly`).

### 6.2 Privacy Policy Additions (`PRIVACY.md`)
Add section:
> **Google Drive Backup (Optional)**:
> If you choose to enable Google Drive backup, Actua connects directly to the Google Drive API to upload encrypted backup archives to your personal Google Drive account. Actua requests access strictly to files it creates (`drive.file`). Backup archives are encrypted on-device before transmission using a key derived from your backup password; Google cannot decrypt or view your budget contents. You can disconnect and revoke access at any time.

---

## 7. Implementation Recommendations for Issue #1168

1. **Local-First Isolation:** Local backup creation in `BackupService.makeBackup()` must always succeed first and remain fully independent of Google Drive or network status.
2. **WorkManager Upload Pipeline:** Implement `GoogleDriveUploadWorker` configured with `NetworkType.CONNECTED`. It reads the local `.zip`, encrypts it to a temporary staging file via `EncryptedBackupArchive`, uploads via `GoogleDriveRestClient.uploadMultipart()`, verifies upload confirmation, and deletes the temporary file.
3. **Retention Safety:** Remote retention pruning must run only **after** a new backup is successfully uploaded and verified on Drive. Never delete local backups when remote uploads fail.
4. **Restore Workflow:**
   - In `BackupsScreen.kt`: Browse remote backups, download via `downloadFile()`, decrypt via `EncryptedBackupArchive`, validate archive structure using `ActualBudgetDatabase.validate()`, and restore.
   - In `NoBudgetScreen.kt` / `ConnectionScreen.kt`: Provide "Restore standalone budget from Google Drive" on fresh installations.
