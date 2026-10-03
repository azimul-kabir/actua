# Transactions parity: writes, transfers and splits

Feature-by-feature audit of Actual Budget transaction behavior that Actua implements, tracked in
[#663](https://github.com/azimul-kabir/actua/issues/663) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Reconciliation itself is audited in
[#664](https://github.com/azimul-kabir/actua/issues/664), rule invocation in
[#669](https://github.com/azimul-kabir/actua/issues/669), schedule posting and upcoming rows in
[#670](https://github.com/azimul-kabir/actua/issues/670), and bank-sync/file imports in
[#673](https://github.com/azimul-kabir/actua/issues/673).

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0). Links use `LC/` = `packages/loot-core/src/` and `DC/` =
  `packages/desktop-client/src/` at that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Status:** **Match** = same rows, CRDT messages and displayed values; **Intentional** = an
  Android-only difference that other clients can't observe (or can observe only as redundant
  messages that produce the same row); **Divergence** = filed as an issue; **N/A** = upstream behavior
  Actua doesn't offer.

## 1. Write path and CRDT message shape

Upstream: `batchUpdateTransactions`
([`LC/server/transactions/index.ts#L40-L196`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/index.ts#L40-L196)),
`insertWithSchema`/`updateWithSchema`/`delete_`
([`LC/server/db/index.ts#L207-L309`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L207-L309)),
`convertForInsert`/`conform`
([`LC/server/aql/schema-helpers.ts#L100-L197`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema-helpers.ts#L100-L197)),
transaction schema and column renames
([`LC/server/aql/schema/index.ts#L35-L59`, `#L374-L383`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L35-L59)).
Actua: `ActualTransactionWriter` (`data/budget/ActualTransactionWriter.kt`), persisted by
`ActualBudgetDatabase.insertTransactions`/`mutateTransactions`/`tombstoneTransactions` (`:821-854`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Column names on the wire: `acct`, `description`, `transferred_id`, `isParent`, `isChild`, `financial_id`, `imported_description` | `schemaConfig.views.transactions.fields` | `transactionFields` uses the stored names | Match |
| Insert emits one message per non-null field, plus defaults `amount = 0`, `sort_order = Date.now()`, `cleared = 1`, `reconciled = 0` | `convertForInsert` (`skipNull`) | emits every column including explicit nulls/zeros (`isParent`, `isChild`, `tombstone`, `starting_balance_flag`, …); import columns only when set | **Intentional.** Redundant `0:`/`N:0` cells give the same row in every client. |
| Insert `cleared` default | `true` when the caller omits it | the caller always supplies it; the editor defaults to uncleared, like the PWA mobile editor | Match (see §7) |
| Update emits only the fields the client diffed | `diffItems` then `updateWithSchema` | `changedFields` diff; `updateTransactionRow` writes only the columns that have messages | Match. Tests: `ActualDataIntegrityRegressionTest.ordinaryUpdateChangesOnlyRequestedRowAndColumns`, `ActualBudgetReadModelTest.unrelatedEditsKeepStaleMappedIdsRawAndMatchTheMessageLog` |
| Delete writes `tombstone = 1`; deleting a parent also tombstones its children | `delete_`, `idsWithChildren` | `deleteTransaction` tombstones the parent and `fetchChildTransactions` | Match. Test: `splitCreateAndDeleteTouchesOnlySplitFamily` |
| Deleting a transfer leg removes the other leg | `transfer.onDelete` → `removeTransfer` | `deleteTransactions` → `detachTransfers` in the same batch: tombstones the other leg (or unlinks it when it is a split child) and clears the deleted leg's `transferred_id` | Match ([#743](https://github.com/azimul-kabir/actua/issues/743)). Test: `TransferRemovalTest` |
| A save's rows and messages commit together | `batchMessages` | one SQLite transaction per `mutate`/`insertTransactions` | Match |
| Dates are `YYYYMMDD` integers; amounts are integer cents | schema `date`, `integer` | `validateBase`; `ActualTransactionFormService.cents` uses `BigDecimal` with `HALF_UP` | Match |
| Orphaned-payee prompt after a payee change | `connection.send('orphaned-payees')` (desktop) | not offered | N/A (payee cleanup is audited in [#665](https://github.com/azimul-kabir/actua/issues/665)) |

## 2. Create and edit a standard transaction

Upstream: PWA mobile editor
([`DC/components/mobile/transactions/TransactionEdit.tsx#L1840-L2056`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/transactions/TransactionEdit.tsx#L1840-L2056)).
Actua: `AddTransactionScreen` → `ActuaRepository.saveTransaction` (`data/ActuaRepository.kt:1024`) →
`ActualTransactionFormService.save` (`data/budget/ActualTransactionFormService.kt:86`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| New transaction defaults: uncleared; account from the entry point or the last transaction; date = the last transaction's date or today | mobile `useEffect` defaults | uncleared; the entry point's or the first account; today | **Intentional.** Only the pre-filled date and account differ; nothing different is stored. |
| Payee typed as new text creates a payee plus its `payee_mapping` | `createPayee` | `resolveOrCreatePayee` (case-insensitive reuse) | Match |
| `imported_payee` left empty for hand-entered rows | no `imported_payee` on the mobile draft | set to the payee name | **Divergence** [#749](https://github.com/azimul-kabir/actua/issues/749) |
| Account, transfer account and categories referenced by id | ids from pickers | list rows carry the ids; pickers show unique labels ("Misc (Food)", "Checking (2)" only when names repeat) that map back to one id, and `saveTransaction`/`previewRules` prefer the id while it still matches the name | Match ([#747](https://github.com/azimul-kabir/actua/issues/747), [#811](https://github.com/azimul-kabir/actua/pull/811)). Tests: `ActuaRepositoryDuplicateNamesTest`, `PickerChoicesTest` |
| Rules pre-fill empty fields while editing a new transaction (`rules-run` + `shouldApplyRuleChange`) | mobile `onUpdate` | `previewRules`, then `RulesEngine.apply` on save with `preserveCategory` | Rule semantics are in [#669](https://github.com/azimul-kabir/actua/issues/669) |
| Category learning (`learnCategories`) | desktop only; the mobile editor doesn't pass it | not done | Match (mobile) |
| Zero amounts allowed | yes | yes (`canSave` requires `amountCents >= 0`) | Match. Test: `formServiceAllowsZeroAmountButRejectsNegative` |
| Convert a scheduled future transaction into a one-time schedule | mobile `onSchedule` | not offered | N/A |

## 3. Transfers

Upstream: [`LC/server/transactions/transfer.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/transfer.ts),
snapshots in [`transfer.test.ts.snap`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/__snapshots__/transfer.test.ts.snap),
the desktop `sync-transfer-date` preference
([`DC/components/transactions/TransactionsTable.tsx#L1129-L1131`, `#L1283-L1327`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/transactions/TransactionsTable.tsx#L1283-L1327)).
Actua: `ActualTransactionFormService.createTransfer`/`updateTransfer`/`convertToTransfer` (`:158-215`),
`ActualTransactionWriter.createTransfer` (`:66`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| A transfer is a row whose payee is the other account's transfer payee; the other leg has the negated amount, the from-account transfer payee, and the same date and notes; `transferred_id` links both ways | `addTransfer` | same rows, both legs in one batch | Match. Test: `TransactionParityTest.newTransferWritesBothLegsWithEachOthersTransferPayee` |
| The other leg of a new transfer is inserted uncleared | `cleared: false` | `createTransfer` and conversion both insert it uncleared | Match ([#744](https://github.com/azimul-kabir/actua/issues/744)). Tests: `TransactionParityTest`.`aNewTransfersOtherLegIsUncleared`, `convertingToTransferInsertsAnUnclearedPartnerAndClearsOnBudgetCategory` |
| Rules run on the inserted other leg (`notes`, `cleared`, `schedule`) | `runRules(transferTransaction)` | not run | Rule invocation is in [#669](https://github.com/azimul-kabir/actua/issues/669) |
| Updating a leg copies `account`, `payee`, `notes`, `amount`, `schedule` to the other leg | `updateTransfer` | `updateTransfer` copies the same fields | Match ([#744](https://github.com/azimul-kabir/actua/issues/744)) |
| The other leg's `cleared`/`reconciled` are never changed by an edit | `updateTransfer` omits them | kept from the other leg's row | Match ([#744](https://github.com/azimul-kabir/actua/issues/744)). Test: `TransactionParityTest`.`editingOneLegKeepsTheOtherLegsClearedReconciledAndDate` |
| The other leg's date follows only when the synced `sync-transfer-date` preference is `true` (default off; desktop only), along with a split-child leg's parent | `TransactionsTable` | `ActualBudgetDatabase.syncTransferDate` gates it, and moves a split-child leg's parent too | Match ([#744](https://github.com/azimul-kabir/actua/issues/744)). Test: `TransactionParityTest`.`syncTransferDatePreferenceMovesTheOtherLegsDate` |
| Transfers between two on-budget or two off-budget accounts have no category | `clearCategory` | both legs `null` | Match. Test: `assertTransferPair` callers in `ActualBudgetReadModelTest` |
| An on-budget → off-budget transfer keeps (and can be given) a category on its on-budget leg | `clearCategory` returns false; the mobile editor enables the field except on the off-budget leg | the editor shows Category for an on/off-budget transfer unless the edited leg is off-budget; create, edit and conversion write it to the on-budget leg only | Match ([#745](https://github.com/azimul-kabir/actua/issues/745)). Tests: `TransactionParityTest.newOnOffBudgetTransfersCategorizeOnlyTheOnBudgetLeg`, `editingAnOnOffBudgetTransferKeepsItsCategory`, `onBudgetToOffBudgetConversionKeepsTheOnBudgetLegCategory` |
| Changing the payee to a regular payee (transfer → expense/income) removes the other leg and clears `transferred_id` | `onUpdate` → `removeTransfer` | the `Standard` save path clears `transferred_id` and removes the other leg through `detachTransfers` | Match ([#743](https://github.com/azimul-kabir/actua/issues/743)). Test: `TransferRemovalTest.savingATransferAsAnExpenseRemovesTheOtherLeg` |
| Changing a transfer into a split removes the transfer | `onUpdate` (`is_parent`) → `removeTransfer` | refused (`CannotConvertToSplit`); the editor clears split lines for transfers | **Intentional** (stricter) |
| Moving one leg to another account updates the other leg's payee | `updateTransfer` | `updateTransfer` rewrites both legs' payees | Match |
| Same account on both sides is rejected | the transfer payee for the same account isn't offered | `TransferAccountsMatch` | Match |
| A split child can be a transfer | `onInsert`/`onUpdate` run per child | not offered: the split line picker leaves out `Transfer: …` entries and the form service refuses such a label before writing; an existing payee with that name still resolves | **N/A** (refused, [#748](https://github.com/azimul-kabir/actua/issues/748), [#812](https://github.com/azimul-kabir/actua/pull/812)); listed as deferred in BACKEND_PARITY.md. Tests: `TransactionParityTest.aTransferLabelOnASplitLineNeverCreatesAPayee`, `aPayeeAlreadyNamedLikeATransferStillSavesOnASplitLine`, `TransactionFormMappingTest.splitLinePayeeOptionsLeaveOutTransfers` |
| Link an existing pair of transactions as a transfer (`validForTransfer`) | desktop "Make transfer" | not offered | N/A |

## 4. Splits

Upstream: [`LC/shared/transactions.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/transactions.ts)
(`makeChild` `#L33-L59`, `recalculateSplit` `#L97-L111`, `updateTransaction` `#L269-L332`,
`deleteTransaction` `#L334-L360`, `splitTransaction` `#L362-L391`, `realizeTempTransactions` `#L393-L413`).
Actua: `ActualTransactionFormService.createSplit`/`updateSplit`/`convertToSplit`/`collapseSplit`
(`:217-305`), `ActualTransactionWriter.createSplit` (`:78`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Parent: `isParent = 1`, no category, amount = the sum of its children | `batchUpdateTransactions` nulls the parent category; `recalculateSplit` | `createSplit` requires `categoryId == null` and a matching sum | Match. Test: `standardTransactionConvertsToSplitWithoutLeavingParentCategory` |
| Child: `isChild = 1`, `parent_id`; account, date, cleared, reconciled and `starting_balance_flag` copied from the parent | `makeChild` | account, date and cleared copied on create and on every parent edit | Match. Test: `TransactionParityTest.splitParentEditsFlowToChildrenAndInheritedChildPayeesFollowTheParent` |
| A child whose payee equals the old parent payee follows a parent payee change | `updateTransaction` | inherited lines are blank in the form and resolve to the new parent payee | Match (same test) |
| A child with no payee keeps no payee until the parent itself changes | `updateTransaction` (re-applies the parent payee through `makeChild` only when the parent is updated) | `updateSplit` keeps a stored payee-less child payee-less unless the parent's account, date, amount, payee, notes or cleared state changed | Match ([#750](https://github.com/azimul-kabir/actua/issues/750), [#814](https://github.com/azimul-kabir/actua/pull/814)). Tests: `TransactionParityTest.aPayeelessChildKeepsNoPayeeUntilTheParentIsEdited`, `ActualTransactionFormPlanTest.aPayeelessChildKeepsNoPayeeUntilTheParentChanges` |
| A split whose children don't add up can't be saved | the mobile footer offers "Amount left" in place of Save; the desktop stores `error` | `SplitAmountMismatch`; Save disabled | Match (mobile) |
| Minimum number of children | 1 (deleting down to one child keeps the split) | 2 in the editor | **Intentional** (stricter). Synced one-child splits still display, and editing one requires adding a line or collapsing it. |
| A new split's parent payee | cleared to `null` by `splitTransaction` | kept | **Intentional**; children inherit it in both clients |
| Child `sort_order` | new children use `-1`, `-2`, … under a `Date.now()` parent | parent sort − 1, − 2, … | **Intentional.** Children are grouped under their parent, and both keep the same relative (descending) order. |
| Deleting the only child turns the parent into a standard transaction; "unsplit" (`makeAsNonChildTransactions`) moves the remaining child's category onto the parent | `deleteTransaction`, `makeAsNonChildTransactions` | **Remove split** collapses to a standard transaction with the first line's category and tombstones the children | Match. Test: `formServiceRoutesExpenseTransferAndSplitEditsLikeIos` |
| Collapsing a split, or dropping a split line, removes the other legs of transfer children | `removeTransfer` per child | `collapseSplit` / `updateSplit` → `detachTransfers` | Match ([#743](https://github.com/azimul-kabir/actua/issues/743)). Test: `TransferRemovalTest` |
| Split lines in an off-budget account have no category | `batchUpdateTransactions` | `enforceOffBudgetCategoryPolicy` | Match. Test: `offBudgetStandardAndSplitPersistenceAlwaysClearsCategories` |

## 5. Off-budget accounts, starting balances and other columns

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Adding or moving a row into an off-budget account writes `category = null` | `batchUpdateTransactions` | `createTransaction` and `enforceOffBudgetCategoryPolicy` | Match. Test: `TransactionParityTest.movingAStandardTransactionOffBudgetClearsItsCategory` |
| `starting_balance_flag` rows sort first on their day and keep the flag when edited | `v_transactions` order | same `ORDER BY`; edits copy the original row | Match. Opening-balance creation is in [ACCOUNTS_PARITY.md](ACCOUNTS_PARITY.md) §1. |
| `notes` stored as entered; an empty note is `null` | – | `takeIf(String::isNotEmpty)` | Match |
| `financial_id`, `imported_description`, `pending`, `raw_synced_data` are kept through edits | diffed update | only written when they change | Match; import semantics are in [#673](https://github.com/azimul-kabir/actua/issues/673) |
| Manual reorder within a day (`transaction-move`, `shoveSortOrdersDescending`) | desktop | not offered | N/A |
| Merge two transactions (`transactions-merge`): same account and amount, transfers only to the same account; keep the bank import, then the imported payee, then the earlier date; fill empty payee, category, notes and schedule; cleared/reconciled if either was; move a dropped split's lines; carry or merge transfer legs | `server/transactions/merge.ts`, `shared/merge.ts`; desktop and mobile selection menus | `TransactionMerge` planned, applied by `ActualTransactionWriter.mergeTransactions` in one batch; Merge in the selection menu, enabled only for a valid pair and confirmed when a row is reconciled | Match ([#821](https://github.com/azimul-kabir/actua/issues/821)). Merging two transfers whose other legs are split lines is refused (**Intentional**: Actual would need split recalculation there). A kept split parent never takes the dropped row's category (**Intentional**: loot-core's raw `db.updateTransaction` would copy it, which `batchUpdateTransactions` never allows). Tests: `TransactionMergeTest`, `MergeSelectionTest`, `TransactionMergeWriterTest` |

## 6. Transaction list semantics

Upstream: `v_transactions`
([`LC/server/aql/schema/index.ts#L385-L432`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L385-L432)),
`transactionsSearch` and `uncategorizedTransactions`
([`DC/queries/index.ts#L83-L136`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/queries/index.ts#L83-L136)).
Actua: `ActualBudgetDatabase.fetchTransactions` (`:963`), `transactionSearchClause` (`:1654`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Order `date DESC, starting_balance_flag, sort_order DESC, id`; split children follow their parent | `v_transactions` | same `ORDER BY`; children ordered `sort_order DESC` | Match |
| Rows with no date or account, orphan children and children of deleted parents are hidden | `v_transactions_internal(_alive)` | `transactionSelect` / `transactionChildSelect` | Match (see [DATABASE_ARCHIVE_PARITY.md](DATABASE_ARCHIVE_PARITY.md)) |
| Search: payee, transfer account, notes, category, account name | `transactionsSearch` | the same fields, plus `imported_description` and split children | Match; the extra fields are **Intentional** |
| Search by amount or date | `transactionsSearch` | not matched | **Divergence** [#751](https://github.com/azimul-kabir/actua/issues/751) |
| Uncategorized: on-budget, no category, not a split parent, and not a transfer (or a transfer to an off-budget account) | `uncategorizedTransactions` | same predicate on the payee's transfer account | Match ([#745](https://github.com/azimul-kabir/actua/issues/745)). Test: `TransactionParityTest.uncategorizedFilterIncludesTransfersLeavingTheBudgetOnly` |
| Uncleared / Cleared / Reconciled filters and the synced `hide-reconciled-<account>` preference | desktop filters and preference | `TransactionStatusFilter`, `setHideReconciled` | Match. Tests: `transactionStateFiltersApplyBeforePagingAndSearch`, `hideReconciledUsesActualSyncedPerAccountPreference` |
| Upcoming projected schedule rows | `usePreviewTransactions` | `UpcomingTransactions.kt` | Audited in [#670](https://github.com/azimul-kabir/actua/issues/670) |

## 7. Cleared and reconciled

Upstream: mobile editor (`TransactionEdit.tsx#L748-L797`, `#L1091-L1128`, `#L1475-L1489`), desktop
account change (`TransactionsTable.tsx#L1258-L1260`).
Actua: `ActualTransactionWriter.setCleared`/`reconcileClearedTransactions` (`:106-128`), editor
Cleared/Reconciled toggle (`ui/transactions/AddTransactionScreen.kt`), confirmations
(`ui/transactions/ReconciledWarnings.kt`), `ActualTransactionFormService.keepReconciledInvariant`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Toggling cleared on a parent updates its children | `makeChild` | `setCleared` includes the children | Match. Test: `clearingASplitKeepsItsStoredChildrenAligned` |
| Toggling cleared on a transfer leg leaves the other leg alone | `updateTransfer` omits `cleared` | neither the list toggle nor an editor save changes it | Match ([#744](https://github.com/azimul-kabir/actua/issues/744)) |
| A reconciled row shows a locked Reconciled toggle, not Cleared | mobile editor | the list toggle is blocked; the editor shows a locked Reconciled toggle, and every form-service update keeps a reconciled row cleared | Match ([#746](https://github.com/azimul-kabir/actua/issues/746), [#813](https://github.com/azimul-kabir/actua/pull/813)). Tests: `TransactionParityTest.aReconciledRowStaysClearedAndMovingItUnreconcilesIt`, `ActualTransactionFormPlanTest.aReconciledRowStaysClearedUnlessItMovesAccount` |
| Confirm before saving or deleting a reconciled row, or one whose other transfer leg is reconciled | `confirm-transaction-edit` | the editor and the bulk Categorize/Move/Delete actions ask first with Actual's texts; list rows carry whether the other leg is reconciled | Match ([#746](https://github.com/azimul-kabir/actua/issues/746), [#813](https://github.com/azimul-kabir/actua/pull/813)). Tests: `ReconciledWarningsTest`, `TransactionParityTest.fetchReconciledIdsFindsTheReconciledOtherLeg` |
| Moving a row to another account clears `reconciled` | desktop `onEdit('account')` | the form service writes `reconciled = 0` when the account changes | Match ([#746](https://github.com/azimul-kabir/actua/issues/746), [#813](https://github.com/azimul-kabir/actua/pull/813)). Test: `TransactionParityTest.aReconciledRowStaysClearedAndMovingItUnreconcilesIt` |
| Reconcile locks every cleared, unreconciled row in the account | – | `reconcileClearedTransactions` | Audited in [#664](https://github.com/azimul-kabir/actua/issues/664) |

## 8. Bulk actions

Actua: `TransactionsScreen` selection menu (`ui/transactions/TransactionsScreen.kt:419-472`), wired in
`ui/navigation/AppNavigation.kt`. Every bulk edit goes through `ActuaRepository.saveTransaction`, so the
matrices above apply to each row.

| Action | Actual | Actua | Status |
| --- | --- | --- | --- |
| Mark cleared / uncleared | desktop bulk edit | `setCleared` per row; for a reconciled row it shows a "Reconciled transactions are locked" error | Match |
| Categorize | desktop bulk edit | `saveTransaction` per row, by category id, after the reconciled confirmation | Match ([#746](https://github.com/azimul-kabir/actua/issues/746), [#747](https://github.com/azimul-kabir/actua/issues/747)) |
| Move to another account | desktop bulk edit (clears `reconciled`) | `saveTransaction` per row, by account id, after the reconciled confirmation; clears `reconciled` | Match ([#746](https://github.com/azimul-kabir/actua/issues/746), [#747](https://github.com/azimul-kabir/actua/issues/747)) |
| Duplicate | `onBatchDuplicate` ([`DC/hooks/useTransactionBatchActions.ts#L286-L318`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/hooks/useTransactionBatchActions.ts#L286-L318)) inserts copies with `cleared: false`, `reconciled: false` and the schedule kept; transfers get a new pair through `onInsert` | `Transaction.asDuplicate` (`model/Transaction.kt:41`) creates uncleared, unreconciled copies (split children and both transfer legs included) and drops the schedule link; transfers go through `createTransfer` | Match for `cleared`/`reconciled` ([#752](https://github.com/azimul-kabir/actua/issues/752)). **Divergence:** the schedule link is dropped, since the editor's save path has no schedule field, so a copy is not counted as the schedule's posted transaction. Test: `TransferDraftTest` |
| Delete | `batchUpdateTransactions({deleted})` with `removeTransfer` | `deleteTransactions` in one batch | Match ([#743](https://github.com/azimul-kabir/actua/issues/743)) |
| Link / unlink schedule | batch update (copied to the other transfer leg) | `setScheduleLink` updates both legs in one batch | Match ([#744](https://github.com/azimul-kabir/actua/issues/744)). Test: `TransactionParityTest`.`linkingAScheduleToOneTransferLegLinksBothLegs`; schedule semantics in [#670](https://github.com/azimul-kabir/actua/issues/670) |
| Bulk edits and duplicates of an incoming transfer leg keep the transfer's direction | each row is its own leg in `batchUpdateTransactions` | `Transaction.asTransferDraft` turns an incoming leg into the From → To draft the editor saves | Match ([#757](https://github.com/azimul-kabir/actua/issues/757)). Tests: `ActuaRepositoryIncomingTransferTest`, `TransferDraftTest` |

## Test evidence and limitations

- New regression tests: `src/androidTest/.../data/budget/TransactionParityTest` (transfer pair shape,
  uncleared other leg on conversion, on/off-budget category, split parent → child propagation, off-budget move).
  Ran on an API 35 emulator (`connectedInstrumentedAndroidTest` filtered to the class): 5/5 passed.
- Existing coverage used as evidence is named in each row (`ActualBudgetReadModelTest`,
  `ActualDataIntegrityRegressionTest`, `ActualTransactionFormPlanTest`).
- Divergences are recorded as upstream behavior plus Actua code paths. The audit didn't make the same
  edit in the Actual PWA and in Actua against a test server and compare the synced rows. That two-client
  check is still outstanding for #663's second acceptance criterion.
