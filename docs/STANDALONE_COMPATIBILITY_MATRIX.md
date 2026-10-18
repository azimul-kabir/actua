# Standalone-Mode Compatibility Matrix and Audit

This document audits standalone (on-device, local-only) budget compatibility in Actua,
defining the regression test matrix and tracking architectural gaps. It supports
[Issue #1167](https://github.com/azimul-kabir/actua/issues/1167) and satisfies the
audit and test-matrix requirements of [Issue #1170](https://github.com/azimul-kabir/actua/issues/1170).

- **Implementation Foundation:** [Issue #1166](https://github.com/azimul-kabir/actua/issues/1166)
  ([PR #1176](https://github.com/azimul-kabir/actua/pull/1176)) established the baseline:
  budget classification (`isDemo`, `isServerBacked`, `isUserLocalOnly`), preservation of
  standalone budgets and their backups on server disconnect/reset, and local schedule processing
  without server credentials.
- **Scope & Boundaries:** Standalone mode is an optional, independent operational mode.
  Actua remains primarily an Actual Budget client. Existing server-connected workflows,
  CRDT wire sync, Merkle trees, and credentials are preserved without modification.

---

## 1. Subsystem Server-Dependency Trace

| Subsystem | Components & Files | Server-Dependent Assumptions | Standalone Status | Compatibility Finding / Action |
| --- | --- | --- | --- | --- |
| **Budget Metadata & Classification** | `BudgetMetadata.kt` | Previously checked `DemoBudgetManager.isDemoBudget(id)` exclusively. Non-demo was assumed server-backed. | **Compatible** (Fixed in #1166) | `isDemo`, `isServerBacked`, and `isUserLocalOnly` properties centralized. Demo flags in `metadata.json` supported. |
| **Budget Loading & Repository Initialization** | `ActuaRepository.kt`, `ActiveBudgetStore.kt` | 1. Automatically opens candidates from `listLocalBudgets()`.<br>2. `discardUnreadableBudget` silently deletes corrupt budgets. | **Gap Identified** (GAP-1) | 1. Works offline with any local budget.<br>2. **Risk:** Silently deleting unreadable standalone budgets causes permanent data loss. Must quarantine rather than delete. |
| **Transaction CRUD & Balances** | `ActualTransactionWriter.kt`, `ActualBudgetDatabase.kt` | None. Generates HLC timestamps and writes local `messages_crdt` rows into SQLite directly. | **Compatible** | Full offline CRUD, split transactions, and account balances work without network or server config. |
| **Transfers & Payees** | `TransferLegRules.kt`, `ActualPayeeWriter.kt`, `PayeeMapping` | None. Transfers create paired local transaction rows and update mapping tables within local transactions. | **Compatible** | Two-leg transfers, transfer payees, and category mappings operate entirely on SQLite. |
| **Allocations & Budgets** | `ActualBudgetWriter.kt`, `ActualBudgetDatabase.kt` | None. Writes to `zero_budgets` or `reflect_budgets` and records CRDT messages locally. | **Compatible** | Envelope and Tracking budgeting, carryover, monthly targets, and template calculations work offline. |
| **Reconciliation** | `TransactionsScreen.kt`, `ActuaRepository.kt` | None. Updates `reconciled` flags and account `last_reconciled` timestamp in SQLite. | **Compatible** | Reconciled locks, balance calculations, and confirmation workflows function with no remote dependencies. |
| **Schedules & Auto-Posting** | `SchedulePoster.kt`, `ActualSyncWorker.kt` | `ActualSyncRunner.run` previously required server URL, token, and cloud identity to run `SchedulePoster`. | **Compatible** (Fixed in #1166) | `ActualSyncRunner.run` branches for `isUserLocalOnly` budgets: runs `SchedulePoster.runIfNeeded`, triggers `SyncSignals.dataChanged()`, and makes backups. |
| **Rules Engine** | `RulesEngine.kt`, `RuleChangeGuard.kt` | None. Evaluates conditions and applies actions in-memory during transaction creation. | **Compatible** | Bulk rule execution, live matching preview, and transaction generation are completely local. |
| **Statement Imports** | `ImportTransactionsScreen.kt`, `data/importing/*` | None. Parses OFX, QFX, QIF, CAMT.053, and CSV and writes through `ActuaRepository`. | **Compatible** | Statement file imports and deduplication function seamlessly on standalone budgets. |
| **Notification Imports** | `CapturedTransactionNotifications.kt`, `NotificationImportPreferences.kt` | None. Taps resolve active budget via `ActiveBudgetStore` and write via `ActuaRepository`. | **Compatible** | Captured transaction notifications and Tasker receiver work with standalone budgets. |
| **Reports & Dashboards** | `ReportsScreen.kt`, `CoreReportEngine.kt`, `SavedReportEngine.kt` | None. Computes all chart series, tables, Monte Carlo, and custom report queries locally from SQLite. | **Compatible** | All dashboard widgets and reports work offline with no external network calls. |
| **Widgets** | `ActuaWidgets.kt`, `WidgetConfigurationActivity.kt` | None. Queries active budget database directly via `BudgetFileManager` and `ActuaRepository`. | **Compatible** | Home screen widgets and quick-entry shortcuts function for standalone budgets. |
| **Local Backups & Retention** | `BackupService.kt`, `LocalBackupWorker.kt` | `BackupService.importArchive` requires `metadata.id == budgetId` and an already open budget. | **Gap Identified** (GAP-3) | Backup creation and retention work locally. New standalone budget creation from archive requires dedicated import API. |
| **Server Disconnect & Reset** | `DisconnectResetActivity.kt`, `DisconnectResetManager.kt` | Reset previously removed all non-demo budgets and cleared active selection. | **Compatible** (Fixed in #1166) | Preserves standalone budgets, their local backups, and active budget selection on server disconnect/reset. |
| **Sync Scheduling & WorkManager** | `ActualSyncWorker.kt`, `ActualSyncScheduler.kt` | `ActualSyncScheduler.scheduleMutation` has a `CONNECTED` network constraint. | **Acceptable** | Local mutations update SQLite immediately. Schedule auto-posting is handled on app open/foreground and periodic background. |
| **UI Banners & Status Messaging** | `AppNavigation.kt`, `ConnectionScreen.kt` | `SessionExpiredBanner` checks `!DemoBudgetManager.isDemoBudget`, showing server expiration on standalone budgets. | **Gap Identified** (GAP-2, GAP-5) | Banners and sync buttons must respect `isServerBacked` vs `isUserLocalOnly`. |

---

## 2. Lifecycle Scenarios Matrix

| Scenario | Server-Backed Budget | Standalone Budget | Validation Method |
| --- | --- | --- | --- |
| **Fresh Install / First Run** | User enters server URL and credentials on `ConnectionScreen`, then downloads a budget. | User chooses "Create local budget" or "Import backup archive" from `NoBudgetScreen` or `ConnectionScreen`. | Manual + UI integration tests (Issue #1167). |
| **Creation** | Created on server via Actual REST API, uploaded, and assigned `cloudFileId` and `groupId`. | Created locally on device with `BlankBudgetFactory.create`. No server registration or cloud fields. | Instrumented test (`BudgetCreationTest`). |
| **Active Selection & Switching** | Selection recorded in `ActiveBudgetStore`. Opening re-syncs pending changes. | Selection recorded in `ActiveBudgetStore`. Local database opened immediately; no server sync triggered. | Instrumented test (`DisconnectResetManagerTest`). |
| **Offline Operation** | Edits stored locally as CRDT messages; queued for sync when network is restored. | Edits stored locally as CRDT messages; processed entirely on device without sync queues. | Instrumented test (`StandaloneSchedulePostingTest`). |
| **App Restart & Process Recreation** | Reloads active budget from `ActiveBudgetStore`; initiates foreground sync if credentials valid. | Reloads active budget from `ActiveBudgetStore`; runs local `SchedulePoster` and skips network calls. | Unit + Instrumented lifecycle tests. |
| **Server Disconnect / Reset** | Downloaded database, encryption keys, and server credentials removed from device. | Budget directory, database, metadata, and local backups strictly preserved on device. | Instrumented test (`DisconnectResetManagerTest`). |
| **App Upgrade** | Existing database upgraded with missing migrations; remote identity retained. | Existing database upgraded with missing migrations; local-only status retained. | Unit test (`BudgetMetadataTest`, `ActualMigrationsTest`). |
| **Archive Export** | Exports clean zip containing `db.sqlite` and `metadata.json` with cloud fields present. | Exports clean zip containing `db.sqlite` and `metadata.json` without cloud fields. | Unit test (`BudgetFileManagerTest`). |
| **Archive Import** | Downloaded from server with `cloudFileId` or restored over existing matching budget ID. | Imported as a new standalone budget; cloud fields stripped; ID collision reallocated safely. | Instrumented test (Issue #1167). |
| **Server Promotion (Upload)** | N/A (already server-backed). | User explicitly uploads standalone budget to server; assigns cloud file ID and starts sync. | Instrumented test (Issue #1167). |

---

## 3. Identified Compatibility Gaps & Tracking

### GAP-1: Unreadable Budget Silent Deletion Risk
- **Location:** `ActuaRepository.kt:187` (`discardUnreadableBudget`)
- **Problem:** If opening a budget fails `BudgetOpenProbe.validate()`, `discardUnreadableBudget` invokes `files.deleteBudget(budgetId)`. For server-backed budgets, data is safely retained on the server. For standalone budgets, this permanently destroys user data without confirmation or restore options.
- **Severity:** High (Data Loss Risk).
- **Remediation:** Inspect `metadata.isUserLocalOnly`. If the unreadable budget is standalone, quarantine it (e.g. rename or move aside) and present a recovery prompt to restore from a local backup, rather than calling `deleteBudget()`.
- **Tracking:** Linked to [Issue #1167](https://github.com/azimul-kabir/actua/issues/1167).

### GAP-2: Session Expired Banner False Positive
- **Location:** `AppNavigation.kt:1401`
- **Problem:**
  ```kotlin
  if (sessionExpired && repository.isUsingActualBudget && !DemoBudgetManager.isDemoBudget(favoriteBudgetId) &&
      detail != DetailDestination.Connection
  ) {
      SessionExpiredBanner(onSignIn = { detail = DetailDestination.Connection })
  }
  ```
  If a user has previously signed into a server whose session expired, and then switches to or works in a standalone budget, the app displays a prominent error banner saying they are signed out of their Actual server and changes will not sync.
- **Severity:** Medium (Confusing / Erroneous UI).
- **Remediation:** Update condition to verify the active budget is server-backed (`metadata?.isServerBacked == true`).
- **Tracking:** Linked to [Issue #1167](https://github.com/azimul-kabir/actua/issues/1167).

### GAP-3: Fresh-Install Archive Import Without Active Budget
- **Location:** `BackupService.kt:85`, `BudgetFileManager.kt:118`
- **Problem:**
  - `BackupService.importArchive()` requires `metadata.id == budgetId`, meaning an identical budget must already be open.
  - `BudgetFileManager.importBudget()` overwrites the existing directory for `original.id` without ID collision detection and assumes server cloud identity parameters.
- **Severity:** Medium (Prevents onboarding from backup).
- **Remediation:** Implement `BudgetFileManager.importLocalBudget(archive: InputStream, nameOverride: String? = null)` which:
  1. Validates `db.sqlite` and `metadata.json` via `BudgetArchivePolicy` and `BudgetOpenProbe`.
  2. Allocates a fresh budget ID if `original.id` collides with an existing local budget.
  3. Strips server fields (`cloudFileId`, `groupId`, `lastUploaded`, `encryptKeyId`).
  4. Installs the budget cleanly in `budgets/<id>/`.
- **Tracking:** Linked to [Issue #1167](https://github.com/azimul-kabir/actua/issues/1167).

### GAP-4: Missing Standalone Budget Creation & Management UI
- **Location:** `AppNavigation.kt` (`NoBudgetScreen`), `ConnectionScreen.kt`
- **Problem:**
  - `NoBudgetScreen` displays "Connect to Actual" button only.
  - `ConnectionScreen` lacks an "On this device" section for creating local budgets, importing archives, opening local budgets, or deleting them with typed-name confirmation.
- **Severity:** Medium (Feature UI).
- **Remediation:** Implement UI entry points as specified in Issue #1167.
- **Tracking:** Core scope of [Issue #1167](https://github.com/azimul-kabir/actua/issues/1167).

### GAP-5: Standalone Status & Manual Sync Messaging
- **Location:** `ConnectionScreen.kt:199,1008`
- **Problem:** `ManualSyncButtonContent` checks only `demoActive`. Standalone budgets display "Sync now", and on tap show "Synced 0 up, 0 down".
- **Severity:** Low (Polish / Usability).
- **Remediation:** Add explicit messaging for standalone budgets (e.g. "Process schedules & backup" or "Local only").
- **Tracking:** Linked to [Issue #1167](https://github.com/azimul-kabir/actua/issues/1167).

### GAP-6: Standalone Server Upload / Promotion Safety
- **Location:** `ConnectionScreen.kt`, `BudgetFileManager.kt`
- **Problem:** Currently, there is no flow to promote a standalone budget to a server-backed budget. A failed upload attempt must never delete the local standalone budget or discard its backups.
- **Severity:** Medium (Data Safety).
- **Remediation:** Add an explicit, confirmed upload action that validates server connectivity and credentials, uploads `db.sqlite` and `metadata.json`, records `cloudFileId` and `groupId` only upon server success, and preserves local files if network or server errors occur.
- **Tracking:** Core scope of [Issue #1167](https://github.com/azimul-kabir/actua/issues/1167).

---

## 4. Archive Format & Round-Trip Expectations

### Supported Archive Formats
1. **Root-Level Archive:**
   - Standard zip archive with `db.sqlite` and `metadata.json` at the root.
   - Produced by Actua's `writeArchive()` and BackupService.
2. **Subdirectory-Enclosed Archive:**
   - Zip archive enclosing `db.sqlite` and `metadata.json` within a single root directory (e.g., `<BudgetName>/db.sqlite`, `<BudgetName>/metadata.json`).
   - Standard export format produced by Actual Budget desktop and web apps.
   - Both formats are validated and extracted by Actua's `BudgetFileManager.extractArchive()`.

### Archive Security & Safety Rules
- **Extraction Limits:** Maximum uncompressed archive size of 500 MB; individual file entries capped at 500 MB (`BudgetArchivePolicy`).
- **Path Traversal Protection:** Entry names containing `..`, absolute paths, or invalid characters are rejected with `BudgetFileException.UnsafeArchive`.
- **Integrity Validation:** Every extracted database must pass `ActualBudgetDatabase.validate()` and `BudgetOpenProbe.validate()`.

### Metadata Normalization on Standalone Import
When an archive is imported into standalone mode:
- `id`: Retained if unique; if an existing local budget shares the ID, a new unique ID must be generated to prevent overwriting existing budgets.
- `budgetName`: Retained (or overridden by user).
- `cloudFileId`, `groupId`, `lastUploaded`, `encryptKeyId`: Explicitly stripped (set to `null` or omitted).
- `resetClock`: Set to `true` (Actua always generates fresh node IDs via `HybridLogicalClock.generateNodeId()`).

### Round-Trip Verification Expectations
1. **Actual Web/Desktop Export -> Actua Standalone:**
   - Archive contains `db.sqlite` and `metadata.json`.
   - On import into Actua, schema migrations are checked/upgraded, cloud fields are stripped, and all accounts, payees, transactions, rules, and schedules load cleanly.
2. **Actua Standalone Export -> Actual Web/Desktop:**
   - Exported archive lists only official Actual migrations in `__migrations__` in sequential order (Actua strips internal migration IDs via `BackupService.cleanSnapshot`).
   - Opens in Actual Budget without migration errors or schema rejections.

---

## 5. Regression Test Matrix

| Workload / Flow | Test Type | Target File | Verification Criteria | Status |
| --- | --- | --- | --- | --- |
| **Metadata Classification** | JVM Unit | `BudgetMetadataTest.kt` | Classifies demo (ID & JSON flag), server-backed, and local-only budgets across JSON variants. | **Automated** (Covered) |
| **Disconnect & Reset Isolation** | Instrumented | `DisconnectResetManagerTest.kt` | Deletes server budgets and keys; strictly retains standalone & demo budgets, backups, and active selection. | **Automated** (Covered) |
| **Local Schedule Auto-Posting** | Instrumented | `StandaloneSchedulePostingTest.kt` | Standalone budget auto-posts due schedules without server URL/token, fires `SyncSignals.dataChanged()`, and creates backup. | **Automated** (Covered) |
| **Sync Coalescing** | Instrumented | `StandaloneSchedulePostingTest.kt` | Standalone budget repeat execution within coalescing window reuses previous success without error. | **Automated** (Covered) |
| **Demo Sync Rejection** | Instrumented | `StandaloneSchedulePostingTest.kt` | Demo budget returns `NotConfigured` and executes zero remote network requests. | **Automated** (Covered) |
| **Standalone Budget Creation** | Instrumented | `BudgetCreationTest.kt` | Creating a local budget initializes clean schema, starter categories, and dashboard with no cloud fields. | **Automated** (Covered) |
| **Standalone Archive Import API** | Instrumented | New `StandaloneBudgetImportTest.kt` (Issue #1167) | Imports root-level and folder-nested archives, strips cloud fields, resolves ID collisions safely. | **Planned** (Issue #1167) |
| **Unreadable Budget Quarantine** | Instrumented | New `ActuaRepositoryRecoveryTest.kt` (Issue #1167) | Unreadable standalone budget is never silently deleted by repository initialization. | **Planned** (Issue #1167) |
| **Session Expired Banner Suppression** | Instrumented | New `AppNavigationStandaloneTest.kt` (Issue #1167) | Standalone budget never displays `SessionExpiredBanner` even when server token has expired. | **Planned** (Issue #1167) |
| **Server Promotion (Upload)** | Instrumented | New `StandaloneBudgetPromotionTest.kt` (Issue #1167) | Uploads standalone budget to server; failures preserve local budget and backups intact. | **Planned** (Issue #1167) |
| **Server-Connected Regression** | Instrumented | `ActualSyncClientTest.kt`, `BudgetDownloadServiceTest.kt` | Existing server-connected sync, download, encryption, and deletion continue to pass unchanged. | **Automated** (Covered) |
| **Offline App Navigation & Editing** | Manual | Real Device / Emulator | Open standalone budget in Airplane mode; verify accounts, categories, rules, reports, widgets, and app restart. | **Manual** |
