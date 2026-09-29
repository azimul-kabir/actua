# Database schema, migrations, backup/restore and archive parity

This document audits the database file Actua shares with Actual Budget: the SQLite schema and
migration list, the views Actual reads through, the `db.sqlite` + `metadata.json` archive, local
backups, and tombstone and mapping handling in Actua's read models. It is tracked in
[#661](https://github.com/azimul-kabir/actua/issues/661), part of
[#658](https://github.com/azimul-kabir/actua/issues/658). Server endpoints and the budget lifecycle
are in [SERVER_FILE_PARITY.md](SERVER_FILE_PARITY.md). `/sync/sync` and message application are in
[SYNC_PARITY.md](SYNC_PARITY.md). The version summary is in
[ACTUAL_26_9_COMPATIBILITY.md](ACTUAL_26_9_COMPATIBILITY.md).

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0). `L/` is shorthand for
  `https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/`,
  and `M/` for `.../packages/loot-core/migrations/`.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. `DB` means
  `data/budget/ActualBudgetDatabase.kt`. `src/test` and `src/androidTest` mean
  `app/src/{test,androidTest}/java/com/azimulkabir/actua/`.
- **Status:** **Match** means Actual and Actua read or write the same thing. **Intentional** means
  a deliberate Android difference that other clients can't see. **Divergence** means it is filed as
  an issue. **N/A** means an upstream feature Actua doesn't offer.

## 1. Migrations

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| Migration list: 59 ids in `M/`, applied in id order; 5 are JavaScript (`1632571489012`, `1722717601000`, `1722804019000`, `1723665565000`, `1765518577215`) | [`L/migrate/migrations.ts#L77-L100`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/migrate/migrations.ts#L77-L100) | `BlankBudgetFactory.MIGRATIONS` lists the same 59 ids | Match. Checked by the schema diff in §6. | `src/androidTest/.../data/budget/BudgetCreationTest` |
| Blank budget schema: `default-db.sqlite` with every migration applied on first load | [`L/budgetfiles/app.ts#L400-L466`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budgetfiles/app.ts#L400-L466), [`L/update.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/update.ts) | `BlankBudgetFactory.SCHEMA` creates the final schema directly | Match for tables, columns, declared types and indexes. Starter category groups, categories and mappings are identical to `default-db.sqlite`. | §6 |
| JS migrations that seed rows: `1722804019000` adds the default dashboard widgets and `1765518577215` adds a `Main` dashboard page | [`M/1722804019000_create_dashboard_table.js`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/migrations/1722804019000_create_dashboard_table.js), [`M/1765518577215_multiple_dashboards.js`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/migrations/1765518577215_multiple_dashboards.js) | Both ids are marked applied, but neither row is seeded | **Divergence** [#716](https://github.com/azimul-kabir/actua/issues/716). In Actual, a budget created by Actua has no dashboard page. | – |
| Opening: `checkDatabaseValidity` requires `__migrations__` to be an exact ordered prefix of the known list, otherwise `out-of-sync-migrations`. Budgets newer than the client are refused. `patchBadMigrations` renames `1685375406832`. | [`L/migrate/migrations.ts#L53-L65`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/migrate/migrations.ts#L53-L65), [`#L146-L192`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/migrate/migrations.ts#L146-L192) | `DB.open` → `runMigrations` (`DB:1684-1744`) never refuses a budget. Required tables are checked by `DB.validate` (`DB:1787`), and core reads by `BudgetOpenProbe`. | **Intentional.** Actua opens newer budgets and leaves unknown tables and columns alone (see §4). | `BudgetDownloadServiceTest.downloadedBudgetThatFailsOpenValidationIsRejectedBeforeInstall` |
| Older budgets: pending SQL/JS migrations run in full, in order | [`L/migrate/migrations.ts#L178-L192`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/migrate/migrations.ts#L178-L192) | `runMigrations` adds only the tables, columns and indexes Actua reads (`columnMigrations`, `DB:1640-1656`) in one transaction, then replays the latest stored CRDT value into each new column | Match for data: nothing is dropped or rewritten, and the new columns get their synced values. **Divergence** in bookkeeping, see next row. | `ActualBudgetReadModelTest.migrationAddsScheduleAndReplaysLatestStoredValue`, `migrationAddsCurrentActualAccountGroupSchema`, `cleanupSchemaMigratesAndRoundTripsThroughOneAtomicBatch`, `PayeeLocationDatabaseTest.migrationCreatesUpstreamTableAndIndexes` |
| `__migrations__` bookkeeping | Only ids from `M/` | `runMigrations` records private ids (`1694438752001`, `1694438752002`, `1720665000001`, `1778510362741`, `1780606214999`, `1780606215003`–`05`, `1787013118200`) on every open, and can record an upstream id without the migrations before it. `BackupService.cleanSnapshot` strips most private ids from backups but misses `1780606215005` and `1787013118200`. | **Divergence** [#709](https://github.com/azimul-kabir/actua/issues/709). Actua backups imported into Actual fail with `out-of-sync-migrations`. | – |
| Only upstream columns are synced | The AQL schema and migrations define every syncable column. `apply()` raises `invalid-schema` for unknown ones. | `ActualEntityWriter` also syncs `accounts.gocardless_requisition_id`, which Actual doesn't have | **Divergence** [#708](https://github.com/azimul-kabir/actua/issues/708) | – |

## 2. Views

Actual doesn't store views in migrations. `updateViews` generates them from the AQL schema at every
load and records their hash in `__meta__.view-hash`
([`L/update.ts#L14-L32`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/update.ts#L14-L32)).
Actua never creates, drops or reads views. Instead it writes the same logic as explicit SQL over the base
tables. Views Actual left in a downloaded file are preserved untouched, and Actual regenerates them
when the hash changes.

| View (from [`L/aql/schema/index.ts#L313-L432`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L313-L432)) | Actua equivalent | Status |
| --- | --- | --- |
| `v_transactions_internal`: `payee = payee_mapping.targetId`, `category = category_mapping.transferId` (null for parents), `amount = IFNULL(amount, 0)`, rows need `date` and `acct`, and children need a `parent_id` | `transactionSelect` / `transactionChildSelect` (`DB` companion) resolve both mappings and require `date` and `acct`. Actua falls back to the raw id when there is no mapping row (`COALESCE(cm.transferId, t.category)`). | Match when the mapping rows exist, which Actual always writes. The fallback is part of [#711](https://github.com/azimul-kabir/actua/issues/711). |
| `v_transactions_internal_alive`: `tombstone = 0`, and children only if the parent is alive | Every balance, report, reconciliation, statement and budget query uses `(t.tombstone = 0 OR NULL)` and checks the parent join (`DB:78-128`, `:290-390`, `:997-1037`, `:1149-1185`). The list shows parents and hydrates live children. | Match. Actua also treats a `NULL` tombstone as alive. |
| `v_transactions`: joins `payees`, `categories` and `accounts` with `tombstone = 0`, so deleted references read as null | Actua's transaction joins check the tombstone for transfer accounts, but not for payees or categories | **Divergence** [#711](https://github.com/azimul-kabir/actua/issues/711) |
| `v_payees`: hides transfer payees whose account is deleted and names transfer payees after their account | `fetchPayees` returns every live payee. Callers drop transfer payees from name lists, and transaction rows use `COALESCE(pa.name, p.name)` with `pa` restricted to live accounts. | Match for what is displayed. Transfer payees are never offered as ordinary payees. |
| `v_categories`: `group = cat_group` | `fetchCategoryGroups` / `fetchBudgetMonth` read `cat_group` directly and filter tombstones | Match |
| `v_schedules`: `next_date` is `local_next_date` when `local_next_date_ts = base_next_date_ts`, else `base_next_date`, and the payee resolves through `payee_mapping` | `fetchSchedules` / `fetchScheduleSummaries` (`DB:571-682`) apply the same rule and resolve the payee through `payee_mapping` | Match. Details in [SCHEDULED_TRANSACTIONS_PARITY.md](SCHEDULED_TRANSACTIONS_PARITY.md). |

## 3. Tombstones and mappings in read queries

Every read in `ActualBudgetDatabase` was checked. "Live" means `tombstone = 0 OR tombstone IS NULL`.

| Read | Tombstone handling | Mapping handling | Status |
| --- | --- | --- | --- |
| `fetchAccounts` balances | Live rows, live parents for children, parents excluded | – | Match (`splits: 'none'` balance) |
| `fetchAccounts`, `fetchAccountGroups`, `fetchBankSyncAccounts` | Live only | – | Match |
| `existingFinancialIds` (bank-sync `imported_id` dedupe) | Includes deleted rows | – | Match: Actual queries `v_transactions_internal` unless `reimportDeleted` is on ([`L/accounts/sync.ts#L845-L852`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L845-L852)) |
| `fuzzyMatchCandidates` | Live only | `payee_mapping` | Match |
| `fetchPayees`, `findPayeeByName`, `fetchPayeeLocations`, `fetchRules`, `fetchCleanupGroups`, `fetchDashboardPages`, `fetchDashboardWidgets`, `fetchSavedReports`, `scheduleNameExists`, `scheduleOwnedRuleIds` | Live only | – | Match |
| `fetchCategoryGroups`, budget-month categories and groups | Live only | – | Match |
| `fetchBudgetMonth` spending | Live rows, live parent, parents excluded, live on-budget accounts | `category_mapping` | Match |
| `fetchTransactions`, `fetchTransaction`, `fetchScheduleTransactions`, split portions, search | Live rows. Joined payee and category aren't checked for tombstones. | Both mappings | **Divergence** [#711](https://github.com/azimul-kabir/actua/issues/711) |
| `fetchChildTransactions`, `fetchClearedUnreconciledTransactions`, `fetchTransactionsForReports` | Live rows. Children need a live parent (reconciliation, reports). | Both mappings | Match, apart from #711 |
| `fetchSchedules`, `fetchPaidScheduleIds`, `fetchSchedulePaymentDates`, `hasScheduleTransaction` | Live schedules, rules and transactions | `payee_mapping` | Match |
| `fetchDiscoveryTransactions` | Live only | `payee_mapping` | Match |
| `fetchNote(s)`, preferences, `zero_budget_months`, budget cells | No tombstone column | – | Match |

## 4. Incoming messages for unknown schema

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| A message for a table or column the local schema lacks | `apply()` throws `SyncError('invalid-schema')` ([`L/sync/index.ts#L80-L108`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/index.ts#L80-L108)) | `applyMessageRows` (`DB:1471`) stores the message in `messages_crdt` and skips the cell. When a later Actua migration adds that column, `replayStoredMessages` applies the latest value. | **Intentional.** Actua is more tolerant, so a newer Actual can't break Actua's sync. Messages for tables Actua creates later (for example `payee_locations`) aren't replayed. |

## 5. Archive, backup and restore

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| Zip layout: `db.sqlite` and `metadata.json` at the root. Import also accepts one shared sub-directory. Caps of 500 MB per archive, per entry and uncompressed total. | [`L/cloud-storage.ts#L212-L287`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/cloud-storage.ts#L212-L287), `L/util/zip.ts` | `BudgetFileManager.extractArchive` (`:222-265`) takes the two files by base name from any single location and rejects path traversal, absolute or drive paths, duplicate names and more than 500 MB uncompressed (`BudgetArchivePolicy`). Writes put both entries at the root. | Match. Actua is slightly more lenient: it doesn't require both files in the same directory. | `src/test/.../data/budget/BudgetArchivePolicyTest` |
| `metadata.json` fields: `id`, `budgetName`, `cloudFileId`, `groupId`, `lastUploaded`, `encryptKeyId`, `resetClock`, plus others such as `userId` and legacy synced prefs | `L/prefs.ts`, `L/cloud-storage.ts` | `BudgetMetadata` reads the seven core fields. Download keeps unknown keys verbatim and overwrites the cloud identity (see SERVER_FILE_PARITY §3). | Match |
| `resetClock`: on load, generate a new clock node, then clear the flag | [`L/budgetfiles/app.ts#L602-L616`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budgetfiles/app.ts#L602-L616) | Every writer and sync client starts a fresh random node (`HybridLogicalClock.generateNodeId()`). The stored clock only supplies the timestamp high-water mark, so the flag is read but never needed. | Match (stronger) | – |
| Export (`export-budget`): empties `kvcache`/`kvcache_key` and sets `resetClock = true` | [`L/cloud-storage.ts#L145-L210`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/cloud-storage.ts#L145-L210) | No standalone export. The Backups screen exports a backup archive, and `uploadArchive` is used only for new budgets. | N/A. Backup archives are what users move between apps (next rows). |
| Backup creation: copy `db.sqlite`, delete `messages_crdt` and `messages_clock`, then zip it with the current `metadata.json` | [`L/budgetfiles/backups.ts#L106-L165`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budgetfiles/backups.ts#L106-L165) | `BackupService.makeBackup` / `cleanSnapshot` (`:27-44`, `:164-178`) do the same on a consistent snapshot, also strip private migration ids (incompletely, #709), and write through a temp file | Match, except [#709](https://github.com/azimul-kabir/actua/issues/709) | `BackupServiceTest.backupStripsSyncStateAndRestoreCanRevert` |
| Snapshot method | Plain file copy | `VACUUM INTO` on API 29 and later; on API 28, `wal_checkpoint(FULL)` then a copy | **Divergence** [#710](https://github.com/azimul-kabir/actua/issues/710): AOSP Android 10 bundles SQLite 3.22.0, and `VACUUM INTO` needs 3.27. | – |
| Retention: 3 backups for today, 1 per earlier day, at most 10 in total; automatic every 15 minutes (desktop only) | [`L/budgetfiles/backups.ts#L81-L104`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budgetfiles/backups.ts#L81-L104), `#L257-L275` | `BackupService.backupsToRemove` uses the same rule. Backups are made by WorkManager jobs (`LocalBackupWorker` and optionally with a sync run) and on demand. | Match (schedule adapted to Android) | `src/test/.../data/budget/BackupRetentionTest` |
| First restore keeps `db.latest.sqlite` and `metadata.latest.json` for a one-tap revert. Revert copies them back and deletes them. Making a new backup discards the revert point. | [`L/budgetfiles/backups.ts#L167-L212`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budgetfiles/backups.ts#L167-L212) | `BackupService.restore` and `makeBackup` behave the same | Match | `BackupServiceTest.backupStripsSyncStateAndRestoreCanRevert` |
| Restoring a backup: clear `groupId`, `lastSyncedTimestamp` and `lastUploaded`, try `upload()`, then write the archive's `db.sqlite` and `metadata.json` verbatim. The revert also re-uploads. | [`L/budgetfiles/backups.ts#L213-L254`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budgetfiles/backups.ts#L213-L254) | Restores the database and writes metadata with the archive's `id`, `budgetName` and `resetClock`. `cloudFileId` and `encryptKeyId` come from the live budget, `groupId` and `lastUploaded` are set to null, and nothing is uploaded. Sync then reports "not configured" until the budget is downloaded again. | **Intentional.** A restored copy is never pushed over the server file; the UI says restoring disconnects sync. | `BackupServiceTest.backupStripsSyncStateAndRestoreCanRevert` |
| Importing a zip from outside the app | `import-budget` (type `actual`) creates or overwrites the budget named by `metadata.id`, empties `kvcache`, loads it and tries `upload()` ([`L/importers/actual.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/importers/actual.ts)) | `BackupService.importArchive` (`:66-96`) validates the archive (layout, required tables, `BudgetOpenProbe`) and adds it to the backup list only when `metadata.id` matches the open budget. Restoring it is a separate, confirmed step. | **Intentional.** Actua doesn't create local-only budgets (SERVER_FILE_PARITY §4), and a restore never replaces data without confirmation. | `BackupServiceTest.importedBackupIsValidatedAndDoesNotChangeTheActiveBudget`, `invalidAndDifferentBudgetImportsFailWithoutAddingABackup` |
| Backup mirror to external storage | Desktop only (the backups folder) | `BackupDestinationManager` mirrors each archive to a Storage Access Framework folder chosen by the user, and removes pruned ones | Intentional (Android addition) | – |

### Round trip

The round trip was checked from source, not on a live PWA:

1. **PWA export → Actua.** An Actual export of the same budget imports as a backup (the `id` matches),
   passes validation and restores. Its `messages_crdt` is kept, and `resetClock = true` has no
   effect because Actua always uses fresh nodes. Data is unchanged. The restored copy is detached
   from sync, as described above.
2. **Actua → PWA import.** Tables and columns are Actual's own schema. However, the archive's
   `__migrations__` currently contains `1780606215005` (and `1787013118200` for budgets opened
   since that migration), so Actual refuses the file with `out-of-sync-migrations`. This is
   **blocked by [#709](https://github.com/azimul-kabir/actua/issues/709)**. A budget that Actua has
   linked to a bank provider also carries `accounts.gocardless_requisition_id` values. They don't
   stop the file from opening, but syncing them breaks Actual
   ([#708](https://github.com/azimul-kabir/actua/issues/708)).

After #709 is fixed, the full round trip (PWA export → Actua import → Actua backup export → PWA
import) should be run manually against a real Actual server with a synthetic budget, and the result
recorded here.

## 6. Schema-diff method

The blank-budget comparison in §1 was reproduced as follows:

1. Take `packages/loot-core/default-db.sqlite` at `59fe126f`, apply each `M/*.sql` in id order,
   apply the DDL of the five JS migrations, and record every id.
2. Build Actua's blank budget from `BlankBudgetFactory.SCHEMA`.
3. Compare the `PRAGMA table_info` of every table, all `CREATE INDEX` statements, the views and
   triggers (none in either), and `__migrations__`.

The result was the same tables, columns, types, `NOT NULL` flags, primary keys and indexes, and an
identical 59-id migration list. The only differences are equivalent default spellings:
`goal`/`long_goal`/`goal_def` default `null` versus `NULL`, and `payees.favorite` default `FALSE`
versus `0`.

## 7. API 28 SQLite compatibility

Android 9 and 10 bundle SQLite 3.22.0; Android 11 bundles 3.28.0. A search of `src/main` for
post-3.22 syntax (window functions, UPSERT, `RETURNING`, column rename or drop, `json_each`,
`iif`, `unixepoch`, `->>`) found only `VACUUM INTO` ([#710](https://github.com/azimul-kabir/actua/issues/710)).
Migrations use only `CREATE TABLE`/`CREATE INDEX IF NOT EXISTS` and `ALTER TABLE ADD COLUMN`, which
work on API 28.

## Divergences filed

- [#708](https://github.com/azimul-kabir/actua/issues/708) (**P1**, sync): linking or unlinking a
  bank account syncs `accounts.gocardless_requisition_id`, a column Actual doesn't have, which
  causes `invalid-schema` in Actual clients
- [#709](https://github.com/azimul-kabir/actua/issues/709) (**P1**, round trip): private or
  out-of-order migration ids make Actua backups fail to open in Actual
- [#710](https://github.com/azimul-kabir/actua/issues/710) (P2): backup creation uses `VACUUM INTO`
  on Android 10 (SQLite 3.22)
- [#711](https://github.com/azimul-kabir/actua/issues/711) (P2): transaction reads show deleted
  payees and categories where Actual shows none
- [#716](https://github.com/azimul-kabir/actua/issues/716) (P2): budgets created in Actua have no
  dashboard page in Actual
