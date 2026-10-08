# Reconciliation parity

Feature-by-feature audit of Actual Budget account reconciliation as Actua implements it, tracked
in [#664](https://github.com/azimul-kabir/actua/issues/664) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Account balances in general are covered in
[ACCOUNTS_PARITY.md](ACCOUNTS_PARITY.md) §5. Edit/delete guards on reconciled rows were ported in
[#746](https://github.com/azimul-kabir/actua/issues/746) and are re-checked in §5 below.

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit pinned by the other parity docs). `LC/` =
  `packages/loot-core/src/`, `DC/` = `packages/desktop-client/src/`; every link is a permalink at
  that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Status:** **Match** = same rows, CRDT cells and displayed values; **Intentional** = an
  Android-only difference that other clients can't observe; **Divergence** = filed as an issue;
  **N/A** = upstream behavior Actua doesn't offer.

## Summary

| Area | Status |
| --- | --- |
| Cleared balance, difference and the balanced state | Match |
| Lock ("Lock transactions") rows and atomicity | Match |
| Adjustment transaction cells | Match ([#993](https://github.com/azimul-kabir/actua/issues/993)) |
| Unlocking a reconciled row | Match ([#992](https://github.com/azimul-kabir/actua/issues/992)) |
| Edit/delete/move confirmations on reconciled rows | Match (#746); Actua also confirms bulk Categorize and Merge |
| `hide-reconciled-<id>` preference | Match (mobile semantics) |
| `accounts.last_reconciled` | Match on lock; leaving without locking writes nothing, a deliberate difference ([#994](https://github.com/azimul-kabir/actua/issues/994)) |
| "Use last synced total" (`balance_current`) | Missing: **Divergence** [#995](https://github.com/azimul-kabir/actua/issues/995) |
| Bank sync never updates a reconciled match | Match |

No mutation is applied before the user confirms it. Opening the reconcile screen, typing a bank
balance and going back write nothing. The only writes are the three listed in §6, and each starts
from an explicit tap.

## 1. Entering reconciliation and the balances compared

Upstream: desktop `ReconcileMenu`/`ReconcilingMessage`
([`DC/components/accounts/Reconcile.tsx#L34-L232`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/accounts/Reconcile.tsx#L34-L232)),
mobile `AccountReconcileModal`
([`DC/components/modals/AccountReconcileModal.tsx#L34-L184`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/modals/AccountReconcileModal.tsx#L34-L184))
and `ReconcilingBanner`
([`DC/components/mobile/accounts/ReconcilingBanner.tsx#L24-L158`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/accounts/ReconcilingBanner.tsx#L24-L158)),
binding `accountBalanceCleared`
([`DC/spreadsheet/bindings.ts#L32-L40`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/spreadsheet/bindings.ts#L32-L40)).
Actua: `ReconcileAccountScreen` (`ui/transactions/TransactionsScreen.kt:887`), opened from the
account menu's **Reconcile**; balances from `ActualBudgetDatabase.fetchAccounts` (`:83`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Cleared balance = `SUM(amount)` over live rows with `cleared = 1`, `is_parent = 0`, children of a tombstoned parent excluded | `accountBalanceCleared` (`splits: 'none'` + aggregate → non-parents in `v_transactions_internal_alive`) | `fetchAccounts` cleared sum, same filters | Match |
| Bank balance input, integer cents, negative allowed | prefilled with the cleared balance | starts empty; **Use cleared balance** fills it; calculator pad with sign | **Intentional.** Same stored value. Neither client writes it. |
| Difference = bank − cleared, shown with a leading `+` when positive | `targetDiff`, `(targetDiff > 0 ? '+' : '')` | `bankBalance - account.clearedCents`, same sign rule | Match |
| Display uses exact cents | `format(…, 'financial')` | `formatReconciliationMoney` ignores hide-decimals and balance privacy | Match |
| Balanced state shows "All reconciled!" and **Lock transactions** | `targetDiff === 0` | "Reconciled" card and **Lock cleared transactions** | Match (wording differs) |
| Unbalanced state offers **Create reconciliation transaction** and **Exit reconciliation** | banner buttons | **Create adjustment transaction** and the back button; plus a list of uncleared rows that can be marked cleared | **Intentional.** The review list is an Android addition and writes through the normal cleared path (§6). |
| **Use last synced total** from `accounts.balance_current` | modal/menu, when non-null | not offered | **Divergence** [#995](https://github.com/azimul-kabir/actua/issues/995) |
| "Reconciled {time} ({date})" / "Not yet reconciled" | from `last_reconciled` | same line under the balances (`lastReconciledMillis`) | Match ([#994](https://github.com/azimul-kabir/actua/issues/994)) |
| Scheduled preview rows are hidden while reconciling | mobile `previewTransactionsToDisplay = []` | the reconcile screen lists only stored rows | Match |

## 2. Locking cleared transactions

Upstream: `lockTransactions` / `finishReconciliation`
([`DC/accounts/reconciliation.ts#L18-L93`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/accounts/reconciliation.ts#L18-L93)),
grouped split query
([`LC/server/aql/schema/executors.ts#L97-L176`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/executors.ts#L97-L176)),
`updateTransaction`/`makeChild`
([`LC/shared/transactions.ts#L35-L55`, `#L269-L330`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/transactions.ts#L269-L330)).
Actua: `ActuaRepository.reconcileAccount` (`data/ActuaRepository.kt:1818`) →
`ActualTransactionWriter.reconcileClearedTransactions` (`:187`) →
`ActualBudgetDatabase.fetchClearedUnreconciledTransactions` (`:1405`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Lock only when the difference is zero | `finishReconciliation` locks only if `reconcileAmount − cleared === 0` | the lock button exists only in the balanced state | Match |
| Rows locked: live rows of the account with `cleared = 1` and `reconciled = 0` | AQL filter `{cleared: true, reconciled: false, account}` | same SQL filter, children only when their parent is live | Match |
| Split groups: parent and every child become `reconciled = 1` | `updateTransaction` on the parent rewrites each child via `makeChild` (child `cleared`/`reconciled` copied from the parent) | parent and children are each selected by their own `cleared = 1` | Match. Equivalent because both clients keep children's `cleared` equal to the parent's (`setCleared` aligns them; see test below). Actual would also lock a whole group if only one row in it matched, a state neither client produces. |
| Transfers: only this account's leg is locked; the other leg keeps its own state | `transfer.onUpdate` doesn't propagate `reconciled` | only rows with `acct = accountId` | Match |
| Cells written: `reconciled = 1` (plus unchanged cells Actual re-sends) | `transactions-batch-update` with full rows | only the `reconciled` cell per row, applied with `applyLocalMessages` | Match. Rows end up the same. Actual's redundant same-value messages aren't observable. |
| All rows in one atomic batch | one `transactions-batch-update` (`batchMessages`) | one `mutate(updates = …)` | Match |
| `last_reconciled` stamped on finish (both **Lock** and **Exit**) | `updateAccount({…, last_reconciled: Date.now().toString()})`, after the lock | stamped on **Lock** only, in the same batch as the lock (`reconcileClearedTransactions(reconciledAt = …)`) | Match for Lock ([#994](https://github.com/azimul-kabir/actua/issues/994)). Deliberate difference for Exit, decided in #994: Actua has no Exit button, and going back writes nothing, so no write happens without an explicit tap. Other clients then keep showing the previous time. |

Tests: `src/androidTest/.../data/budget/ActualBudgetReadModelTest.reconciliationLocksEveryClearedStoredRowAtomically`
(ordinary row, split parent + children, transfer leg; the other leg stays unlocked),
`clearingASplitKeepsItsStoredChildrenAligned`, `lockingStampsLastReconciledInTheSameBatch`,
`src/test/.../ui/transactions/LastReconciledTest`,
`src/androidTest/.../data/budget/ActualDataIntegrityRegressionTest.reconciliationLocksOnlyClearedRowsInRequestedAccount`
(uncleared and other-account rows are byte-identical before and after).

## 3. Reconciliation adjustment transaction

Upstream: `createReconciliationTransaction`
([`DC/accounts/reconciliation.ts#L95-L124`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/accounts/reconciliation.ts#L95-L124)),
transaction schema defaults
([`LC/server/aql/schema/index.ts#L40-L80`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L40-L80)).
Actua: `ActuaRepository.createReconciliationAdjustment` → `ActualTransactionWriter.createReconciliationAdjustment`.

| Cell / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `amount` = bank − cleared (integer cents) | `diff` | `difference` from the screen | Match |
| `date` = today (`YYYYMMDD`) | `currentDay()` | `DayDate.today().yyyymmdd` | Match |
| `cleared = 1`, `reconciled = 0` | explicit | explicit | Match |
| `notes` = `Reconciliation balance adjustment` | `t('…')` (translated in non-English UIs) | the English text | Match for English. A translated PWA writes translated notes; Actua is English-only. |
| No payee, no category, not a transfer or split | omitted | `null` | Match |
| `sort_order` | schema default `Date.now()` | `System.currentTimeMillis()` | Match |
| Rules run on the new row (`rules-run`), honouring a tombstoning rule, and a transfer payee set by a rule adds the other leg (`batchUpdateTransactions` runs transfers) | yes | `ActualTransactionWriter.createReconciliationAdjustment`: `createTransaction(applyRules = true, runTransfers = true)` | Match ([#993](https://github.com/azimul-kabir/actua/issues/993)) |
| Asks before writing | no: one tap creates it | confirmation dialog | **Intentional** (stricter) |
| Not locked automatically; the user then locks the now-balanced account | yes | yes: the screen recomputes the difference and shows the lock button | Match |

Tests: `src/androidTest/.../data/budget/ActualBudgetReadModelTest.reconciliationAdjustmentRunsRulesLikeActual`.

## 4. Unlocking a reconciled transaction

Upstream: `unlockTransaction`
([`DC/accounts/reconciliation.ts#L47-L69`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/accounts/reconciliation.ts#L47-L69)),
mobile `onToggleTransactionCleared`
([`DC/components/mobile/accounts/AccountTransactions.tsx#L265-L290`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/accounts/AccountTransactions.tsx#L265-L290)),
desktop `TransactionsTable` `onUpdate`
([`#L1210-L1225`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/transactions/TransactionsTable.tsx#L1210-L1225)).
Actua: `ActualTransactionWriter.setCleared` (`:172`), register `onClearedClick`
(`TransactionsScreen.kt:652`, `:680`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Tapping a reconciled row's status asks (`unlockReconciled`), then writes `reconciled = 0` for the row and its split children; `cleared` stays 1 | yes | the register and search rows ask with the same text (`rememberClearedToggle`), then `ActualTransactionWriter.unlockTransaction` writes `reconciled = 0` for the whole split group in one batch | Match ([#992](https://github.com/azimul-kabir/actua/issues/992)) |
| Reconciled rows show a lock icon | `SvgLockClosed` in `TransactionListItem` | lock icon labelled "Reconciled" (`ClearedIndicator`) | Match ([#992](https://github.com/azimul-kabir/actua/issues/992)) |
| Bulk cleared edits skip reconciled rows | `useTransactionBatchActions` `name === 'cleared' && trans.reconciled → return` | `bulkClearedTargets` skips them | Match ([#992](https://github.com/azimul-kabir/actua/issues/992)) |
| The editor shows a disabled **Reconciled** toggle in place of **Cleared** | `TransactionEdit.tsx#L1475-L1488` | `AddTransactionScreen.kt:545` | Match (#746) |

Tests: `src/test/.../ui/transactions/ReconciledWarningsTest.bulkClearedEditsSkipReconciledRows`,
`unlockUsesActualsUnlockReconciledText`,
`src/androidTest/.../data/budget/ActualBudgetReadModelTest.unlockingAReconciledSplitUnlocksTheWholeGroupAndKeepsItCleared`.

## 5. Editing, deleting and moving reconciled rows

Upstream: `ConfirmTransactionEditModal` texts
([`DC/components/modals/ConfirmTransactionEditModal.tsx`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/modals/ConfirmTransactionEditModal.tsx)),
mobile `TransactionEdit` save/delete
([`#L748-L795`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/transactions/TransactionEdit.tsx#L748-L795),
[`#L1091-L1129`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/transactions/TransactionEdit.tsx#L1091-L1129)),
batch actions
([`DC/hooks/useTransactionBatchActions.ts#L150-L160`, `#L269-L283`, `#L451-L513`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/hooks/useTransactionBatchActions.ts#L451-L513)).
Actua: `ui/transactions/ReconciledWarnings.kt`, `AddTransactionScreen.kt:197-252`,
`TransactionsScreen.kt` `confirmReconciled`, `ActualTransactionFormService.keepReconciledInvariant`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Saving a reconciled row (any change on mobile) asks `editReconciled` | yes | `ReconciledAction.EDIT` | Match |
| Saving/deleting a row whose other transfer leg is reconciled asks `batch…WithReconciledTransfer` | yes | `transferReconciled` warning | Match |
| Deleting a reconciled row asks `deleteReconciled` / `batchDeleteWithReconciled` | yes | `ReconciledAction.DELETE` | Match |
| Bulk edits ask only for amount, payee, account and date | `batchEditWithReconciled` for those four fields | bulk Move asks; bulk **Categorize** and **Merge** also ask | **Intentional** (stricter: an extra confirmation, the same rows once confirmed) |
| Moving a row to another account writes `reconciled = 0` | `TransactionsTable#L1258`, batch `#L155-L157` | `keepReconciledInvariant` | Match |
| A row that stays reconciled stays cleared | the cleared toggle is replaced by the lock | `keepReconciledInvariant` forces `cleared = 1` | Match |
| Duplicating a reconciled row produces an uncleared, unreconciled copy | `onBatchDuplicate` | `Transaction.asDuplicate` | Match |
| Merge keeps `reconciled` if either row was | `LC/server/transactions/merge.ts#L164-L175` | `TransactionMerge` | Match |
| Cancel writes nothing | modal `onCancel` | dialog dismiss | Match |

Tests: `src/test/.../ui/transactions/ReconciledWarningsTest`,
`src/test/.../data/budget/ActualTransactionFormPlanTest.aReconciledRowStaysClearedUnlessItMovesAccount`,
`src/test/.../model/TransferDraftTest.duplicatesAreUnclearedAndUnreconciled`,
`src/test/.../data/budget/TransactionMergeTest.theKeptRowKeepsItsOwnValuesAndIsReconciledIfEitherWas`,
`src/androidTest/.../data/budget/TransactionParityTest.aReconciledRowStaysClearedAndMovingItUnreconcilesIt`,
`fetchReconciledIdsFindsTheReconciledOtherLeg`.

## 6. Writes during the flow

| User action | Actual write | Actua write | Status |
| --- | --- | --- | --- |
| Open reconcile, enter a balance, leave | `last_reconciled` on **Exit reconciliation** | none | Deliberate difference ([#994](https://github.com/azimul-kabir/actua/issues/994)): back navigation never writes |
| Mark an uncleared row cleared while reconciling | `cleared = 1` (row + split children) | `setCleared(row, true)`, same cells | Match |
| Create adjustment | one inserted row (after rules) | one inserted row after rules, behind a confirmation dialog | see §3 |
| Lock | `reconciled = 1` on every cleared row, then `last_reconciled` | the same cells, in one batch | see §2 |

## 7. "Hide reconciled transactions" and the reconciled balance

Upstream: synced pref `hide-reconciled-<accountId>` (`"true"`/`"false"`,
[`LC/types/prefs.ts#L40`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/types/prefs.ts#L40)),
mobile filter
([`DC/components/mobile/accounts/AccountTransactions.tsx#L74-L95`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/accounts/AccountTransactions.tsx#L74-L95)),
desktop filter
([`DC/components/accounts/Account.tsx#L476-L488`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/accounts/Account.tsx#L476-L488)).
Actua: `ActuaRepository.setHideReconciled` (`:1634`), `ActualBudgetDatabase.fetchHideReconciledAccountIds`
(`:331`), `hiddenAsReconciled` (`TransactionsScreen.kt:1264`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Per-account synced preference, stored as `"true"`/`"false"` | `useSyncedPref` | `setPreference(…, hide.toString())` | Match |
| Hidden rows: `reconciled = 1` | `filter({reconciled: {$eq: false}})` | same | Match |
| Hiding is suspended while reconciling | mobile `!isReconciling` | the reconcile screen ignores the preference. Its uncleared review list does follow a selected status chip, so with **Cleared** or **Reconciled** chosen it lists nothing. | Match. The chip quirk only affects Actua's own review list, not the balances or the rows written. |
| Desktop shows reconciled rows anyway while running balances are on | desktop only | follows mobile: hides them; running balances are folded from full history | **Intentional** (mobile semantics) |
| A status chip (Reconciled/Cleared/…) supersedes the preference | no status chips upstream | Android filter UI | **Intentional** |
| Reconciled balance (`SUM` over `reconciled = 1`, non-parents) | no binding: Actual shows no reconciled balance | account details **Reconciled** amount (`TransactionsScreen.kt:1149`) | **Intentional.** A read-only display; uses the same row filter as the cleared balance. |

Tests: `src/test/.../ui/transactions/HideReconciledFilterTest`,
`src/androidTest/.../data/budget/ActualBudgetReadModelTest.hideReconciledUsesActualSyncedPerAccountPreference`,
`reconciledStatusFilterReturnsOnlyReconciledRows`.

## 8. Other state touched by reconciliation

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `accounts.last_reconciled` column (migration `1740506588539_add_last_reconciled_at.sql`) | written by `updateAccount` only when truthy ([`LC/server/accounts/app.ts#L94-L107`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/app.ts#L94-L107)) | read by `fetchAccounts` when the column exists; written on lock | Match ([#994](https://github.com/azimul-kabir/actua/issues/994)) |
| Bank sync never updates a matched reconciled row | `reconcileTransactions` skips `match.reconciled` ([`LC/server/accounts/sync.ts#L670-L674`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/sync.ts#L670-L674)) | `BankSyncService` `match.reconciled ->` counts it as matched, writes nothing | Match. Test: `src/test/.../data/bank/BankSyncMatcherTest` |
| Reconciled state in rules (`reconciled` field) | condition/action field | `RulesEngine` exposes `reconciled` | Covered by [RULES_PARITY.md](RULES_PARITY.md) |

## Verification limits

The matrix comes from reading the upstream source at the pinned commit next to Actua's code, plus
the tests listed above. No side-by-side session with the PWA against a test server was run for this
audit. The lock path's row results are covered by Android SQLite tests that need an emulator, and
those weren't re-run here.
