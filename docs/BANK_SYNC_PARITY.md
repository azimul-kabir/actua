# Bank sync and file import parity

Feature-by-feature audit of Actual Budget's server-hosted bank sync (SimpleFIN, GoCardless) and
transaction file import as Actua implements them, tracked in
[#673](https://github.com/azimul-kabir/actua/issues/673) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). The user-facing import sources are
described in [TRANSACTION_IMPORTS.md](TRANSACTION_IMPORTS.md). Locked (reconciled) rows are covered
in [RECONCILIATION_PARITY.md](RECONCILIATION_PARITY.md). The experimental Enable Banking provider
(#832) is out of scope here.

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit pinned by the other parity docs). `LC/` = `packages/loot-core/src/`,
  `DC/` = `packages/desktop-client/src/`, `SS/` = `packages/sync-server/src/`. Every link is a
  permalink at that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Status:** **Match** = same rows, CRDT cells and server requests; **Intentional** = an
  Android-only difference that other clients can't observe, or a recorded boundary;
  **Divergence** = filed as an issue; **N/A** = upstream behavior Actua doesn't offer.
- **Method:** each row compares upstream source and its `sync.test.ts` cases with Actua's code and
  tests. Actual itself was not run against a live provider for this audit. "Same payload"
  statements trace one synthetic `/transactions` response through both pipelines by reading the
  code.

## Summary

| Area | Status |
| --- | --- |
| Provider secrets (`/secret`) and status endpoints | Match. Secrets are never logged or stored on the device (§1). |
| Account discovery (SimpleFIN, GoCardless) | Match |
| Linking: `account_id`, `account_sync_source` | Match |
| Linking: `banks` row (SimpleFIN missing, GoCardless unnamed) | Divergence ([#1004](https://github.com/azimul-kabir/actua/issues/1004)) |
| Starting balance on the first sync; `balance_current` | Divergence ([#1003](https://github.com/azimul-kabir/actua/issues/1003)) |
| Unlinking | Divergence ([#1005](https://github.com/azimul-kabir/actua/issues/1005)) |
| Start date, timeouts, error → `bank_sync_status`, `last_sync` | Match, with edge cases in [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| Row normalization (ids, payee, notes, amount) | Divergence ([#1002](https://github.com/azimul-kabir/actua/issues/1002)) |
| Rules before matching; payee creation only when used | Match |
| Exact `imported_id` match for new rows | Match |
| Exact `imported_id` match for rows already imported (update path) | Divergence ([#1001](https://github.com/azimul-kabir/actua/issues/1001)) |
| Fuzzy matching (±7 days, payee pass, then date pass) | Match |
| Updating a fuzzy-matched row | Match, except `raw_synced_data` ([#1002](https://github.com/azimul-kabir/actua/issues/1002)) |
| Per-account sync preferences (`sync-import-pending-…` etc.) | Divergence ([#1006](https://github.com/azimul-kabir/actua/issues/1006)) |
| Atomic batch, transfers, `sort_order` | Divergence ([#1007](https://github.com/azimul-kabir/actua/issues/1007)) |
| Category learning | Match (neither client learns from imports) |
| File import: formats and mapping | Intentional boundary (CSV only in common) |
| File import: payee title-case, cleared default | Divergence ([#1008](https://github.com/azimul-kabir/actua/issues/1008)) |
| File import: matching existing transactions | Divergence ([#1009](https://github.com/azimul-kabir/actua/issues/1009)) |

**Same synthetic payload, both clients.** Consider a booked row with an id, a non-blank payee, notes
without `#`, a two-decimal amount, and no matching row in the account. With no per-account
preferences set, both clients write the same cells: date, integer-cent amount, payee,
`imported_description`, notes, `financial_id`, `cleared = 1`. Three cells differ. Actua leaves
`raw_synced_data` empty, writes `pending = 0`, and uses a different `sort_order`. An existing manual
row with the same amount within ±7 days is matched and updated the same way by both. Every other
row shape is listed under its issue in §4–§6 and in BACKEND_PARITY.md.

## 1. Provider configuration and secrets

Upstream: `setSecret`/`checkSecret`
([`LC/server/accounts/app.ts#L718-L789`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L718-L789)),
`goCardlessStatus`/`simpleFinStatus`
([`#L871-L911`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L871-L911)).
Actua: `ActuaRepository.setSimpleFinToken` / `setGoCardlessCredentials` (`data/ActuaRepository.kt:796`),
`ActualServerClient.setSecret` (`data/network/ActualServerClient.kt:376`), `ui/banksync/BankSyncScreen.kt`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| SimpleFIN setup token stored as server secret `simplefin_token` | `POST /secret {name, value}` | same | Match |
| GoCardless `gocardless_secretId` + `gocardless_secretKey` | two `POST /secret` | same | Match |
| Non-admin rejected | server 403 | 403 → "You must be an admin…" | Match |
| Provider configured? | `POST /simplefin/status`, `/gocardless/status` → `data.configured` | same; 404/405/501 → not configured | Match |
| Remove a secret (`value: null` → `DELETE /secret/<name>`) | offered | not offered | N/A |
| `X-Actual-File-Id` header on `/secret` | sent when a file is open | not sent | Intentional. The header only scopes Pluggy.ai secrets, which Actua doesn't support. |
| Secrets on the client | sent once; not persisted by the client | held in composable `remember` state, cleared after **Save**, never written to preferences, the budget, logs or error messages. Server error bodies are not logged. | Match |
| Synced rows from the provider | `raw_synced_data` keeps the provider row (may include account names and descriptions) | not written ([#1002](https://github.com/azimul-kabir/actua/issues/1002)) | Divergence in content, not a secret leak |

## 2. Discovery, linking and unlinking

Upstream: `linkGoCardlessAccount` / `linkSimpleFinAccount`
([`LC/server/accounts/app.ts#L169-L313`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L169-L313)),
`findOrCreateBank`
([`LC/server/accounts/link.ts#L26-L50`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/link.ts#L26-L50)),
`unlinkAccount`
([`app.ts#L1689-L1767`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L1689-L1767)),
desktop sync-all selection
([`DC/accounts/mutations.ts#L615-L638`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/accounts/mutations.ts#L615-L638)).
Actua: `ActualEntityWriter.linkBankAccount` / `unlinkBankAccount` (`data/budget/ActualEntityWriter.kt:52`,
`:68`), `ActuaRepository.createLinkedAccount`, `AppNavigation.linkBankAccount`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| SimpleFIN discovery | `POST /simplefin/accounts` | same | Match |
| GoCardless banks, web token, accounts for a requisition | `/gocardless/get-banks`, `/create-web-token`, `/get-accounts` (polled) | same calls; the user taps "check accounts" instead of a poll loop | Intentional (Android lifecycle) |
| Link writes `account_id`, `account_sync_source` | yes | yes | Match |
| GoCardless `banks` row (`bank_id` = requisition id) | `findOrCreateBank(institution, requisitionId)`: `name` = institution, reused on `bank_id` + `name IS ?` | `bank_id` written and reused by `bank_id`; `name` null, so Actual won't reuse it or show the bank name | **Divergence** [#1004](https://github.com/azimul-kabir/actua/issues/1004) |
| SimpleFIN `banks` row (`bank_id` = org domain ?? org id, name = institution) | written; Actual's **Sync all** includes only accounts with `bank` | not written | **Divergence** [#1004](https://github.com/azimul-kabir/actua/issues/1004) |
| New linked account | inserts `name`, `official_name`, `mask`, `offbudget`, transfer payee, then syncs immediately | `createAccount(…, 0)` + link; no sync until the user refreshes | **Divergence** [#1003](https://github.com/azimul-kabir/actua/issues/1003) (see §3) |
| Unlink cells | clears `account_id`, `bank`, `balance_current/available/limit`, `account_sync_source`, `bank_sync_status`; keeps `last_sync` | clears `account_id`, `account_sync_source`, `bank`, `bank_sync_status`, **`last_sync`**; keeps balances | **Divergence** [#1005](https://github.com/azimul-kabir/actua/issues/1005) |
| Unlink removes the GoCardless requisition once unused | `POST /gocardless/remove-account` | only when closing the account | **Divergence** [#1005](https://github.com/azimul-kabir/actua/issues/1005) |

## 3. Download

Upstream: `getAccountSyncStartDate`, `download*Transactions`, `processBankSyncDownload`,
`syncAccount`, `simpleFinBatchSync`
([`LC/server/accounts/sync.ts#L75-L394`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L75-L394),
[`#L1067-L1343`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L1067-L1343)),
`handleSyncResponse` / `getBankSyncStatusFromError` / `accountsBankSync`
([`app.ts#L1283-L1626`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L1283-L1626)),
server feeds
([`SS/app-simplefin/app-simplefin.js#L100-L280`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/sync-server/src/app-simplefin/app-simplefin.js#L100-L280),
[`SS/app-gocardless/app-gocardless.ts#L212-L272`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/sync-server/src/app-gocardless/app-gocardless.ts#L212-L272)).
Actua: `BankSyncService.sync` (`data/bank/BankSyncService.kt:39`),
`ActualServerClient.downloadSimpleFinTransactions` / `downloadGoCardlessTransactions` /
`parseSimpleFinDownloads` / `parseSingleBankSyncDownload` (`data/network/ActualServerClient.kt:351`,
`:483`, `:655`, `:674`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Start date = max(today − 89, oldest row) | oldest row with `date <= today` | `MIN(date)` over live rows, future rows included | Match, except an account holding only future-dated rows ([#1002](https://github.com/azimul-kabir/actua/issues/1002)) |
| SimpleFIN: one batch request for all linked accounts | `accountId[]`, `startDate[]`, 5-minute timeout | same (`BANK_SYNC_READ_TIMEOUT_MILLIS`) | Match |
| GoCardless request body | `requisitionId`, `accountId`, `startDate`, `includeBalance = newAccount` | `requisitionId`, `accountId`, `startDate`, `endDate = today`; no `includeBalance`, so the server fetches balances every time | **Divergence** [#1003](https://github.com/azimul-kabir/actua/issues/1003) |
| Accounts synced | live, open, with `account_id` (+ `bank` outside the SimpleFIN batch) | live, open, with `account_id` and `account_sync_source` | Match for Actua-initiated syncs (see #1004 for the reverse) |
| Timeout → `timed-out`; batch-missing account → `account-missing`; `ITEM_LOGIN_REQUIRED`/`INVALID_ACCESS_TOKEN` → `reauth-required`; `ACCOUNT_NEEDS_ATTENTION` → `attention-required`; `RATE_LIMIT_EXCEEDED` → `rate-limit-exceeded`; else `failed` | `getBankSyncStatusFromError` | `downloadFailure`, `bankSyncStatus` | Match (`src/test/.../BankSyncDownloadFailureTest`, `ActualServerBankSyncTest`) |
| SimpleFIN batch entry carrying both rows and an error | error stored, **no rows imported** | error stored, rows imported | **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| Success → `last_sync = now ms`, `bank_sync_status = 'ok'` | `handleSyncResponse` | `recordBankSyncStatus(…, "ok", now)` | Match |
| GoCardless account with no `banks` row | skipped, status untouched | skipped with a message, status untouched | Match |
| Pluggy.ai / Akahu | synced | reported as unsupported, status untouched | N/A (BACKEND_PARITY boundary) |
| First sync of an empty account inserts a Starting Balance row | yes | no | **Divergence** [#1003](https://github.com/azimul-kabir/actua/issues/1003) |
| `balance_current` updated from the download | every non-initial sync | never written | **Divergence** [#1003](https://github.com/azimul-kabir/actua/issues/1003) |
| Per-account error reporting | `errors[]` with the account name, notification per account | one summary line per account in a snackbar | Match (presentation differs) |

## 4. Row normalization

Upstream: `normalizeBankSyncTransactions` and default mappings
([`LC/server/accounts/sync.ts#L510-L596`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L510-L596),
[`LC/server/util/custom-sync-mapping.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/util/custom-sync-mapping.ts)).
Actua: `ActualServerClient.parseBankSyncRows` (`data/network/ActualServerClient.kt:682`), drafts
in `BankSyncService.sync`.

| Field | Actual | Actua | Status |
| --- | --- | --- | --- |
| `cleared` | `Boolean(booked)` | `booked` | Match |
| `pending` column | not written (stays 0) | `pending = !booked` | Intentional. Actual's AQL schema doesn't read the column, so other clients see nothing different. |
| Amount | `amountToInteger(transactionAmount.amount)` (rounds) | exact `BigDecimal` cents; more than two decimals → row dropped | Match for ≤2 decimals; otherwise **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| Date | mapped field (`date` by default) | `date` | Match for the default mapping; custom mappings → [#1006](https://github.com/azimul-kabir/actua/issues/1006) |
| Payee name | mapped field (`payeeName`), not title-cased, trimmed into `imported_description` | `payeeName` | Match |
| Blank payee name | `imported_description = ""`, no payee | `"Unknown"` payee and description | **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| Notes | mapped field (`notes`), trimmed, `#` → `##` | raw `notes` | **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| `imported_id` | `transactionId`; booked without it → `<provider account>-<internalTransactionId>`; neither → none | `transactionId`; otherwise the row is **dropped** | **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| Provider `category` | kept if it is an existing category id | ignored | Match in practice (SimpleFIN/GoCardless don't send Actual ids) |
| `raw_synced_data` | provider row JSON | not written | **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |
| Same id twice in one download with different data | both rows go through matching | the group is skipped with a "conflicting" message | **Divergence** [#1002](https://github.com/azimul-kabir/actua/issues/1002) |

## 5. Rules, matching and writes

Upstream: `matchTransactions` / `reconcileTransactions`
([`LC/server/accounts/sync.ts#L635-L996`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L635-L996)),
`batchUpdateTransactions`
([`LC/server/transactions/index.ts#L40-L52`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/index.ts#L40-L52)).
Actua: `BankSyncService.sync` (`data/bank/BankSyncService.kt:107-211`), `ImportRules.apply`
(`data/importing/ImportRules.kt`), `BankSyncMatcher.match` (`data/bank/BankSyncMatcher.kt`),
`ActualBudgetDatabase.existingFinancialIds` / `fuzzyMatchCandidates`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Payee resolved by name before rules; new names get a provisional id; created only if still used | `resolvePayee` → `runRules` → `createNewPayees` | `ImportRules.apply`; `createPayeeName` resolved only when the row is written | Match (`BankSyncReconciliationTest.ruleRenamedPayeeLeavesNoBankNamedPayeeBehind`) |
| Rules run on every downloaded row | yes, including rows that match by id | only on rows whose id isn't stored yet | Same result for new rows; see the next row for stored ids |
| Id already stored on a live row | update path: `imported_description` replaced, payee/category/notes filled only if empty, `cleared` OR'd, split children's `cleared` copied; reconciled rows untouched | row skipped entirely, so a pending row stays uncleared after it is booked | **Divergence** [#1001](https://github.com/azimul-kabir/actua/issues/1001) |
| Id stored only on a deleted row | re-imported by default (`sync-reimport-deleted` true) | never re-imported (`existingFinancialIds` counts tombstones) | **Divergence** [#1006](https://github.com/azimul-kabir/actua/issues/1006) |
| Fuzzy dataset | live rows in the account, same amount, ±7 days; bank-sync accounts use `strictIdChecking = false`, so rows with another `imported_id` count | same window and filters; rows taken by an exact id in this download are excluded | Match (`BankSyncReconciliationTest.reDownloadUnderANewIdMatchesTheAlreadyImportedTransaction`) |
| Pass 1 same payee, pass 2 any; closest date first; a candidate is claimed once | yes | yes | Match (`src/test/.../BankSyncMatcherTest`) |
| Matched reconciled row | ignored | counted as matched, not written | Match |
| Fuzzy-matched update cells | `imported_id`, payee/category/notes fill-only, `imported_description`, `cleared` OR, `raw_synced_data` kept | same, except `raw_synced_data`, plus `pending` | Match except [#1002](https://github.com/azimul-kabir/actua/issues/1002) (`BankSyncReconciliationTest.syncReconcilesAManuallyEnteredTransactionPostedOnADifferentDate`, `matchedTransactionTakesTheRuleCategoryWhereItsOwnIsEmpty`) |
| Rule deletes a new row | not added | not added | Match |
| Rule splits a new row | parent + children via `makeSplitTransaction` | `createSplit` with the rule's children; off-budget children have no category | Match |
| One account's writes in one batch | `batchUpdateTransactions({added, updated})` after `createNewPayees` | row-by-row writes; payees and splits separate | **Divergence** [#1007](https://github.com/azimul-kabir/actua/issues/1007) |
| Transfer payee set by a rule creates the counterpart | `runTransfers = true` | not created | **Divergence** [#1007](https://github.com/azimul-kabir/actua/issues/1007) |
| New rows' `sort_order` | `now − index × increment` | writer default | **Divergence** [#1007](https://github.com/azimul-kabir/actua/issues/1007) |
| Category learning | `learnCategories = false` for imports | not run | Match |
| Per-account preferences (`sync-import-pending`, `sync-import-notes`, `sync-import-transactions`, `sync-update-dates`, `sync-reimport-deleted`, `custom-sync-mappings`) | read from synced `preferences` | ignored; behaves as the defaults, except reimport-deleted (above) | **Divergence** [#1006](https://github.com/azimul-kabir/actua/issues/1006) |

## 6. File import

Upstream: `importTransactions` → `reconcileTransactions` (`isBankSyncAccount = false`,
`strictIdChecking = true`, `payeeNameNormalization = 'title-case'`)
([`LC/server/accounts/app.ts#L1634-L1687`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L1634-L1687),
[`sync.ts#L416-L508`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L416-L508)),
parsers in
[`LC/server/importers/`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/importers),
dialog
([`DC/components/modals/ImportTransactionsModal/ImportTransactionsModal.tsx`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/modals/ImportTransactionsModal/ImportTransactionsModal.tsx)).
Actua: `CsvTransactionCandidateSource`, `XlsxStatementReader`, `StatementDocumentReader`
(`data/importing/`), `ui/settings/ImportTransactionsScreen.kt`, `ActuaRepository.importTransactions`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Formats | CSV, QIF, OFX/QFX, CAMT.053 | CSV, XLSX, text-based PDF, pasted or shared text, notifications, Tasker broadcasts | Intentional boundary. CSV is the only format both support. |
| CSV mapping | date, payee, notes, category, amount or inflow/outflow, in/out column, multiplier, flip, skip lines, header toggle, delimiter | date, payee, notes, reference, amount or debit/credit, "expenses are positive", auto delimiter, header row, saved profiles | Match for date/payee/notes/amount. Category column and multiplier: N/A. Reference is Android-only and appended to notes as `Reference: …`. |
| `imported_id` | from OFX/QFX FITID and CAMT ids; CSV has none | none (CSV/XLSX/PDF carry no stable id) | Match for CSV |
| Rules before payee creation | `runRules` on each row | `ImportRules.apply` | Match |
| Payee name normalization | trimmed and title-cased (`title()`) | trimmed only | **Divergence** [#1008](https://github.com/azimul-kabir/actua/issues/1008) |
| `cleared` | **Clear transactions on import**, on by default | always `cleared = 0` | **Divergence** [#1008](https://github.com/azimul-kabir/actua/issues/1008) |
| Matching existing transactions | same amount ±7 days without an `imported_id`, payee pass first; matched rows are **updated**; user can force-add | rows with the same date, amount and normalized payee are flagged and unchecked; nothing is updated | **Divergence** [#1009](https://github.com/azimul-kabir/actua/issues/1009) |
| Duplicates inside the same file | each row matched separately (one candidate claimed once) | an earlier identical row in the file is flagged | Intentional (review-only hint; the user decides) |
| Nothing written before confirmation | preview runs with `isPreview` | parsing and review never write | Match |
| One batch, transfers, `sort_order` | `batchUpdateTransactions` with `runTransfers`; file order kept | inserts batched; payees and splits separate; no transfers | **Divergence** [#1007](https://github.com/azimul-kabir/actua/issues/1007) |
| Category learning | off for imports | off | Match |
| Import history | none | device-local last 20 import summaries, no rows | Intentional (device-only) |

## Divergence issues

| Issue | Severity | Summary |
| --- | --- | --- |
| [#1001](https://github.com/azimul-kabir/actua/issues/1001) | P2 | Update rows downloaded again under the same id (pending → booked) |
| [#1002](https://github.com/azimul-kabir/actua/issues/1002) | P2 | Normalize rows as Actual does (ids, blank payee, notes, rounding, raw data, batch errors, start date) |
| [#1003](https://github.com/azimul-kabir/actua/issues/1003) | P2 | Starting balance on first sync, `balance_current`, GoCardless `includeBalance` |
| [#1004](https://github.com/azimul-kabir/actua/issues/1004) | P2 | `banks` rows as `findOrCreateBank` writes them (SimpleFIN missing, GoCardless unnamed) |
| [#1005](https://github.com/azimul-kabir/actua/issues/1005) | P2 | Unlink cells and GoCardless requisition removal |
| [#1006](https://github.com/azimul-kabir/actua/issues/1006) | P2 | Per-account bank sync preferences, including reimport of deleted rows |
| [#1007](https://github.com/azimul-kabir/actua/issues/1007) | P2 | Atomic import batch, transfer counterparts, `sort_order` |
| [#1008](https://github.com/azimul-kabir/actua/issues/1008) | P2 | File import payee title-case and cleared default |
| [#1009](https://github.com/azimul-kabir/actua/issues/1009) | P2 | File import fuzzy matching against existing rows |
