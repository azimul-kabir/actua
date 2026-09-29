# Accounts parity: lifecycle, balances, groups and credit cards

Feature-by-feature audit of Actual Budget account behavior that Actua implements, tracked in
[#662](https://github.com/azimul-kabir/actua/issues/662) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Reconciliation is audited separately in
[#664](https://github.com/azimul-kabir/actua/issues/664), bank-sync download/import in
[#673](https://github.com/azimul-kabir/actua/issues/673), and transaction/transfer/split writes in
[#663](https://github.com/azimul-kabir/actua/issues/663).

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit pinned by the other parity docs). Links use the prefix `U/` =
  `https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/`, and
  `LC/` = `U/packages/loot-core/src/`, `DC/` = `U/packages/desktop-client/src/`.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Status:** **Match** = same rows, CRDT messages and displayed values; **Intentional** = an
  Android-only difference that other clients can't observe; **Divergence** = filed as an issue;
  **N/A** = upstream behavior Actua doesn't offer.

## 1. Create account

Upstream: `createAccount` ([`LC/server/accounts/app.ts#L553-L590`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L553-L590)),
`insertAccount`/`insertPayee` ([`LC/server/db/index.ts#L567-L578`, `#L736-L747`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L736-L747)),
`getStartingBalancePayee` ([`LC/server/accounts/payees.ts#L18-L37`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/payees.ts#L18-L37)).
Actua: `ActualEntityWriter.createAccount` (`data/budget/ActualEntityWriter.kt:216`), called from
`ActuaRepository.createAccount` (`data/ActuaRepository.kt:1196`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `accounts` row: `name`, `offbudget`, `closed = 0`; one CRDT batch | `insertAccount` | `accounts`: `name`, `type`, `offbudget`, `closed = 0`, `tombstone = 0`, `sort_order` | Match. The extra `type`/`tombstone` cells are real Actual columns (see §3). |
| New account's `sort_order`: `shoveSortOrders` append, i.e. last `sort_order` in the same on/off-budget partition + 16384 | `db/index.ts#L736-L747` | `nowMillis()` (epoch ms) | **Intentional.** Always larger than every shoved value, so the account still appends to the end of its section. Later upstream appends and moves keep working because `shoveSortOrders` only compares neighbours. |
| Duplicate names allowed | no uniqueness check | rejects an existing name (case-insensitive) | **Intentional** as a UX guard. Actua's other mutations currently resolve accounts by name, so synced duplicates are a bug: **Divergence** [#714](https://github.com/azimul-kabir/actua/issues/714). |
| Transfer payee: `payees {name: '', transfer_acct: id}` + `payee_mapping {targetId: id}` | `insertPayee` | same two rows (plus `tombstone = 0`) in the same batch | Match |
| Opening balance only when non-zero: payee = existing live "Starting Balance" (case-insensitive) or a new one + mapping | `createPayee` (`UNICODE_LOWER`) | `findPayeeByName` (`UPPER`, ASCII) | Match for the fixed ASCII name |
| Opening-balance category: live income category named "Starting Balances", else the first live income category; `null` for off-budget accounts | `getStartingBalancePayee` | same selection from `fetchCategoryGroups()` | Match |
| Opening transaction: today (`YYYYMMDD`), integer cents, `cleared = 1`, `starting_balance_flag = 1` | `insertTransaction` | same cells, plus explicit defaults (`reconciled = 0`, `isParent = 0`, …) | Match |
| Bank-linked create (`createLinkedAccount`) | provider-specific `link*Account` | `createAccount(balance = 0)` then `linkBankAccount` | See §7 |

Tests: `src/androidTest/.../data/budget/ActualBudgetReadModelTest.createsActualAccountGraphAndCategoryMappingsThroughCrdt`.

## 2. Close, reopen and delete

Upstream: `closeAccount` / `reopenAccount` ([`LC/server/accounts/app.ts#L592-L706`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L592-L706)),
[`DC/components/modals/CloseAccountModal.tsx`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/modals/CloseAccountModal.tsx).
Actua: `AccountsScreen` "Close account"/"Reopen account" → `ActuaRepository.setAccountClosed`
(`data/ActuaRepository.kt:1165`) → `ActualEntityWriter.setAccountClosed` (`ActualEntityWriter.kt:32`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Close unlinks bank sync first (not undoable) | `unlinkAccount` | not done | **Divergence** [#713](https://github.com/azimul-kabir/actua/issues/713) |
| Account with zero transactions is deleted (tombstoned), not closed | `numTransactions === 0 → deleteAccount` | sets `closed = 1` | **Divergence** [#713](https://github.com/azimul-kabir/actua/issues/713) |
| Non-zero balance requires a transfer account; adds a `Closing account` transaction of `-balance` to the target's transfer payee, dated today; a category is required when on-budget → off-budget; the target can't be the closing account | `closeAccount`, `CloseAccountModal` | closes with the balance still in the account | **Divergence** [#713](https://github.com/azimul-kabir/actua/issues/713) |
| "Force close": tombstone every transaction, clear `transfer_id`/`payee` on counterpart legs, tombstone account + transfer payee, one batch | `closeAccount({forced: true})` | not offered. `ActualEntityWriter.deleteAccount` (`:121`) exists but has no callers, and it doesn't tombstone transactions or detach counterpart legs | **Divergence** [#713](https://github.com/azimul-kabir/actua/issues/713) |
| Reopen writes only `closed = 0` | `reopenAccount` | `setAccountClosed(id, false)` | Match |
| Closed accounts keep their transfer payee; it isn't offered as a new transfer destination | `v_payees` keeps it; autocomplete hides closed accounts | transfer-destination lookup requires `!closed` (`ActuaRepository.kt:1035-1038`); transfer payees stay | Match |
| Schedules aren't changed by close | – | – | Match |
| Transfer payees can't be deleted or merged on their own | `deletePayee` early return; `mergePayees` filters | `deletePayee` / `mergePayees` `require`s (`ActualEntityWriter.kt:94-117`) | Match |

## 3. `type`, `offbudget`, `sort_order` and reorder

Upstream: `moveAccount` ([`LC/server/db/index.ts#L758-L785`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L758-L785)),
`shoveSortOrders`/`midpoint` ([`LC/server/db/sort.ts#L1-L67`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/sort.ts#L1-L67)),
`getAccounts` (`db/index.ts#L722-L734`), migrations `1686139660866_remove_account_type.sql` /
`1688841238000_add_account_type.sql`.
Actua: `ActualEntityWriter.moveAccount` (`:191`), `data/budget/SortOrder.kt`,
`data/budget/AccountReorderPlanner.kt`, `data/budget/AccountDragReorder.kt`,
`ui/accounts/ReorderAccountsScreen.kt`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `accounts.type` | Column exists (dropped then re-added in 2023) but isn't read, written or exposed by the PWA/API | `setAccountType` writes `checking`/`savings`/`credit`/`investment`/`mortgage`/`debt`/`other`; unknown or `NULL` reads as `CHECKING` | **Intentional.** A real Actual column that other clients ignore, so applying the message is safe. The PWA won't show the type. |
| `offbudget` is fixed after creation | no handler changes it | no writer changes it | Match |
| Read order `ORDER BY sort_order, name` | `getAccounts` | `ORDER BY sort_order` (`ActualBudgetDatabase.kt:106`); reorder screen `sortOrder, id` | **Intentional.** Ties only occur with equal stored `sort_order`, which neither client creates. |
| Move places the account before the target (or at the end) using `shoveSortOrders`: midpoint, and re-sequence at `+16384` when the gap is ≤ 2 | `moveAccount` | `SortOrder.shove`, an exact port | Match |
| Move considers only the moving account's partition (`closed = 1`, or open accounts with the same `offbudget`), and the list includes the moving account | `moveAccount` | considers all live accounts except the moving one | **Intentional.** The relative order within every section is the same; Actua may emit extra `sort_order` cells for other-section rows during a re-sequence. The reorder screen only moves accounts within a section. |
| Every changed `sort_order` in one batch | `batchMessages` | one `persist(messages)` | Match |

Tests: `src/test/.../data/budget/SortOrderTest`, `AccountReorderPlannerTest`,
`AccountDragReorderTest`, `src/test/.../ui/accounts/ReorderAccountsChunkingTest`,
`src/androidTest/.../data/budget/ActualEntityWriterReorderTest.moveAccount*`.

## 4. Account groups (`account_groups`)

Upstream: migration `1787013118115_add_account_groups.sql`,
[`LC/server/account-groups/app.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/account-groups/app.ts),
`getAccountGroups`/`insertAccountGroup`/`moveAccountGroup`/`deleteAccountGroup`
([`LC/server/db/index.ts#L787-L868`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L787-L868)).
In v26.9.0 groups are reachable only through the server handlers and the `api/account-group-*`
API; the desktop client has no group UI.
Actua: `ActualBudgetDatabase.fetchAccountGroups` (`:131`), `ColumnMigration(1787013118115, …)`,
`ui/accounts/AccountsScreen.kt` `chunkedByGroup`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Schema: `account_groups(id, name, sort_order REAL, tombstone)`, `accounts.account_group_id TEXT DEFAULT NULL` | migration | same migration; reads skip when the table is absent | Match |
| Live groups ordered `sort_order, id` | `getAccountGroups` | `ORDER BY sort_order`; UI chunks by `groupSortOrder, id` | Match |
| A reference to a missing/tombstoned group is treated as ungrouped (the delete comment says consumers must do this) | `deleteAccountGroup` | `groups[groupId]` lookup → ungrouped | Match |
| Create/rename/move/delete groups; case-insensitive unique names | `account-group-*` handlers | read-only; Actua never writes `account_groups` or `account_group_id` | **Intentional** (documented in `BACKEND_PARITY.md`) |
| Display: on/off-budget sections, groups by sort order, then an ungrouped bucket | no upstream UI | Android presentation | **Intentional** |

Tests: `src/androidTest/.../ActualBudgetReadModelTest.migrationAddsCurrentActualAccountGroupSchema`,
`accountGroupsAreAbsentUntilTheServerAssignsThemThenRoundTripOnRead`;
`src/test/.../ui/accounts/AccountGroupChunkingTest`.

## 5. Balances

Upstream: bindings `accountBalance`, `accountBalanceCleared`, `accountBalanceUncleared`,
`onBudgetAccountBalance`, `offBudgetAccountBalance`, `closedAccountBalance`
([`DC/spreadsheet/bindings.ts#L22-L86`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/spreadsheet/bindings.ts#L22-L86)).
Aggregates run over `v_transactions_internal_alive` with `is_parent = 0`
([`LC/server/aql/schema/index.ts#L373-L412`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L373-L412),
[`LC/server/aql/schema/executors.ts#L107-L117`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/executors.ts#L107-L117)).
The running balance is `$sumOver` over the register
([`DC/components/accounts/Account.tsx#L687-L721`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/accounts/Account.tsx#L687-L721)).
Actua: `ActualBudgetDatabase.fetchAccounts` (`:78`), `ActuaRepository.accounts` (`:613`),
`ui/transactions/TransactionsScreen.kt` `accountRunningBalances` (`:1264`).

| Balance | Actual | Actua | Status |
| --- | --- | --- | --- |
| Working | `SUM(amount)` of alive, non-parent rows with a date and account; children only when their parent is alive; `NULL` amount = 0 | same predicate set in one grouped query | Match |
| Cleared | `… AND cleared = 1` | `SUM(CASE WHEN cleared = 1 …)` | Match |
| Uncleared | `… AND cleared = 0` | working − cleared | Match. Differs only for a row whose `cleared` was explicitly synced as `NULL` (the column defaults to 1). |
| Reconciled | no binding; reconcile UI works from cleared (#664) | `SUM(CASE WHEN reconciled = 1 …)`, display only | **Intentional** (read-only extra figure) |
| On-budget / off-budget / closed totals | filter on `account.offbudget` / `account.closed` | sums of the per-account balances in the same partitions | Match |
| Running balance: bottom-up sum over the register ordered `date DESC, starting_balance_flag, sort_order DESC, id` (`splits: 'none'`) | `$sumOver` | full-history fold ordered `date DESC, sort_order DESC, id` | **Divergence** on the opening day only: [#715](https://github.com/azimul-kabir/actua/issues/715). Final balances match. |
| Running balance with search, filters or non-date sort | hidden (`canCalculateBalance`) | computed over the unfiltered history, so visible rows show correct values | **Intentional** |
| Hide reconciled keeps reconciled rows in the running balance | rows loaded but not shown | folds `allTransactions` (includes hidden rows) | Match |

**Synthetic cross-check.** `docs/tools/accounts_balance_crosscheck.py` builds an in-memory,
Actual-shaped SQLite budget. It has on-budget, off-budget and closed accounts; an on→on transfer;
an on→off-budget transfer; a split; a split with a tombstoned child; a child of a tombstoned
parent; an orphan child; tombstoned, dateless and `NULL`-amount rows; and cleared, uncleared and
reconciled rows. It then runs Actual's view and aggregate SQL and Actua's `fetchAccounts` SQL /
running-balance fold against the same data. Result: all 24 per-account and group balance checks
match. Running balances match for four of five accounts; the fifth is the known #715 case. Run it
with `python3 docs/tools/accounts_balance_crosscheck.py` (exit 0 = no unexpected mismatch).

**Limitation:** the upstream side is Actual's SQL, transcribed from the pinned sources, not a
live PWA instance. That's enough to compare query semantics, but it doesn't exercise the PWA's
spreadsheet cache.

Tests: `src/androidTest/.../ActualBudgetReadModelTest.readsActualRelationshipsAndSplitAwareBalances`;
`src/test/.../ui/transactions/AccountRunningBalanceTest`.

## 6. Notes and synced per-account preferences

Upstream: notes id `account-<id>` ([`DC/components/sidebar/Account.tsx#L121`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/sidebar/Account.tsx#L121),
`DC/components/mobile/accounts/AccountPage.tsx#L110-L125`), synced prefs
([`LC/types/prefs.ts#L31-L65`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/types/prefs.ts#L31-L65)).
Actua: `ActuaRepository.setAccountNote` (`:1305`), `setHideReconciled` (`:1298`),
`ActualBudgetDatabase.fetchNotes` (`:226`), `fetchHideReconciledAccountIds` (`:246`).

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| Account note: `notes` row `account-<id>`, column `note` | `notes-save` | `setNote("account-$id", …)` | Match |
| `hide-reconciled-<id>`, value string `"true"`/`"false"` | `useSyncedPref`; hidden when `String(v) === 'true'` | read `value = 'true'`; writes `"true"`/`"false"` | Match |
| `show-balances-<id>`, `hide-cleared-<id>`, `transaction-table-columns-<id>`, `show-group-<id>`, `show-extra-balances-<id>`, `show-account-<id>-net-worth-chart`, `side-nav.show-balance-history-<id>` | desktop register/sidebar layout prefs | not read or written. Actua's running-balance toggle is a device-local display preference. | **Intentional** (desktop layout; Actua has its own mobile layout) |
| Import/bank-sync prefs (`csv-*`, `parse-date-*`, `sync-*`, `custom-sync-mappings-*`, `ofx-*`, `qif-*`, `camt-*`, `flip-amount-*`) | import/bank-sync settings | out of scope here; see #673 | – |

Tests: `ActualBudgetReadModelTest.hideReconciledUsesActualSyncedPerAccountPreference`.

## 7. Actua-only data written to the budget

The acceptance criterion: Actua-only features must not write fields that Actual doesn't have.

| Data | Written as | Status |
| --- | --- | --- |
| Credit-card config (statement day, due day, limit, …) | `preferences` row `actuali:credit_card:<accountId>`, the cross-platform Actuali contract | Match. `preferences` is Actual's generic synced key/value table; Actual ignores unknown ids. Out-of-range values are skipped on read (#675). |
| Credit-card statements, dues, reminders, picker order, accessibility (#676–#682) | derived at read time from transactions + the preference above | Match (no extra columns) |
| `accounts.type` | real Actual column | Match (see §3) |
| GoCardless requisition | Before [#721](https://github.com/azimul-kabir/actua/pull/721) (released v1.0.0–v1.2.0): `accounts.gocardless_requisition_id` CRDT cells on every link/unlink, even with a `null` value, for a column Actual doesn't have. Now: a `banks` row (`bank_id` = requisition id, reused on relink) referenced by `accounts.bank`, cleared on unlink (`ActualEntityWriter.kt:47-66`); `fetchBankSyncAccounts` (`ActualBudgetDatabase.kt:154`) reads `banks.bank_id`, falling back to the legacy local column | Match since #721 (fixes [#708](https://github.com/azimul-kabir/actua/issues/708) / [#712](https://github.com/azimul-kabir/actua/issues/712)), matching `link.findOrCreateBank`. Budgets that already synced the old cells need the recovery below. |
| Favorites, running-balance toggle, collapsed sections | device-local preferences | Match (not synced) |

### Recovery for budgets linked by Actua v1.0.0–v1.2.0

The messages those versions sent stay in the server's sync log; an Actua update can't recall them.

- **Who is affected:** a budget where one of those versions linked or unlinked a bank account
  (GoCardless or SimpleFIN) and that is also used in Actual (web, desktop or another client).
  Budgets used only in Actua are not affected, because Actua applies or safely skips the column.
- **Symptom in Actual:** every sync shows "Update required: We couldn't apply changes from the
  server…" ([`DC/sync-events.ts#L333-L343`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/sync-events.ts#L333-L343)).
  Actual applies each downloaded batch in one transaction
  ([`LC/server/sync/index.ts#L330-L378`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/index.ts#L330-L378)),
  so that client stops receiving *all* changes from other devices until it recovers. Its own
  edits still upload.
- **Recovery:** update Actua and let it sync. Then, in the Actual client that has the most complete
  data, use *Settings → Advanced → Reset sync*
  ([`DC/components/settings/Reset.tsx`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/settings/Reset.tsx)).
  That uploads its local copy under a new sync ID and drops the old log, including the bad
  messages. Other devices, including Actua, must then download the budget again.
- **What can be lost:** Reset sync keeps only what the resetting client has. Changes other devices
  made after that client got stuck never reached it, so they are dropped. Before resetting,
  compare recent activity in Actua with the Actual client and re-enter anything missing
  afterwards. Actua has no "reset sync from this device" action, so Actua can't be the source.
- **Bank links afterwards:** a fresh download no longer carries the legacy column, so Actua reports
  GoCardless accounts linked by those versions as "missing its GoCardless connection"
  (`data/bank/BankSyncService.kt:57`). Relink them in Actua (Manage → Bank Sync). The new link
  writes Actual's `banks` row, which also lets Actual's own bank sync use the account.

## 8. Monthly income/expense summary

Actua shows This-month income, expenses and net per account and in totals
(`ui/accounts/AccountMonthlySummary.kt:21`). Actual has no per-account monthly summary. The
closest PWA equivalent is the Cash Flow report
([`DC/components/reports/spreadsheets/cash-flow-spreadsheet.tsx#L45-L60`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/cash-flow-spreadsheet.tsx#L45-L60)).

| Case | Cash Flow (Actual) | Actua summary | Status |
| --- | --- | --- | --- |
| Classification | by sign: `amount > 0` income, `< 0` expense | by category: income categories → income; expense categories → expenses (a refund reduces expenses) | **Intentional.** Mirrors the budget's income/spent view rather than cash flow. |
| Transfers between on-budget accounts | excluded (`payee.transfer_acct = null`) | excluded | Match |
| On-budget ↔ off-budget transfer | excluded from income/expense (shown as a separate transfers series) | on-budget leg counted by sign; off-budget leg skipped (#118, #531) | **Intentional.** Money leaving the budget is shown as spending. |
| Off-budget accounts | excluded (`account.offbudget = false`) | their rows are uncategorized, so they contribute nothing | Match |
| Uncategorized, non-transfer rows | counted by sign | skipped | **Intentional** (category-based definition) |
| Split transactions | children | each child by its own category and sign | Match (category-based) |

Tests: `src/test/.../ui/accounts/AccountMonthlySummaryCalculatorTest`.

## Filed divergences

- [#712](https://github.com/azimul-kabir/actua/issues/712): Bank link/unlink synced a non-Actual `accounts.gocardless_requisition_id` column (P1, sync). Fixed by [#721](https://github.com/azimul-kabir/actua/pull/721) under duplicate [#708](https://github.com/azimul-kabir/actua/issues/708); recovery steps in §7.
- [#713](https://github.com/azimul-kabir/actua/issues/713): Close account doesn't follow Actual's close flow (P2).
- [#714](https://github.com/azimul-kabir/actua/issues/714): Account actions resolve accounts by name (P2).
- [#715](https://github.com/azimul-kabir/actua/issues/715): Running balance ignores `starting_balance_flag` ordering (low).
- Credit-card slice, already fixed: #675–#682.
