# Categories parity: create, rename, hide, delete, reorder, notes and carryover

Feature-by-feature audit of Actual Budget category and category-group behavior against Actua,
tracked in [#666](https://github.com/azimul-kabir/actua/issues/666) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Budget amounts, carryover math and
To Budget are covered by `docs/BUDGET_PARITY.md`; goal templates by
`docs/BUDGET_AUTOMATION_PARITY.md`. The transaction category picker's order and hidden-group
filtering were fixed separately in [#921](https://github.com/azimul-kabir/actua/issues/921).

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit the other parity docs pin). Links use `LC/` =
  `packages/loot-core/src/`, `DC/` = `packages/desktop-client/src/` and `MIG/` =
  `packages/loot-core/migrations/` at that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Method:** source comparison of the server handlers (`LC/server/budget/app.ts`), the database
  layer (`LC/server/db/index.ts`) and the client mutations that add checks on top
  (`DC/budget/mutations.ts`), against Actua's writer and repository. No two-client run was made
  for this audit.
- **Status:** **Match** = same rows, CRDT messages and displayed values; **Intentional** = an
  Android-only difference that other clients can't observe; **Divergence** = filed as an issue;
  **N/A** = upstream behavior Actua doesn't offer.

## 1. Schema

Upstream: [`LC/server/sql/init.sql#L58-L83`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sql/init.sql#L58-L83),
`MIG/1555786194328_remove_category_group_unique.sql`, `MIG/1685007876842_add_category_hidden.sql`,
`MIG/1712784523000_unhide_input_group.sql`. Actua: `data/budget/ActualMigrations.kt`,
`data/budget/BlankBudgetFactory.kt:68`, `ActualBudgetDatabase.fetchCategoryGroups` (`:1141`).

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| `categories(id, name, is_income, cat_group, sort_order, tombstone, hidden, goal_def, template_settings, cleanup_def)` | migrations | same columns | Match |
| `category_groups(id, name, is_income, sort_order, tombstone, hidden)`, no unique name constraint | migrations | same | Match |
| `category_mapping(id, transferId)`, one self-row per category | `init.sql` | same, also in new blank budgets (`INSERT INTO category_mapping SELECT id,id FROM categories`) | Match |
| Income group is never hidden | `1712784523000_unhide_input_group.sql`; no hide action for it | no hide action for the income group (`ui/categories/ManageCategoriesScreen.kt:308`) | Match |

## 2. Create

Upstream: `category-create` / `category-group-create`
([`LC/server/budget/app.ts#L309-L330`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L309-L330),
[`#L429-L443`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L429-L443)),
`insertCategory` / `insertCategoryGroup`
([`LC/server/db/index.ts#L359-L391`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L359-L391),
[`#L442-L495`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L442-L495)),
`useSaveCategoryMutation`
([`DC/budget/mutations.ts#L206-L251`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/budget/mutations.ts#L206-L251)),
mobile `BudgetPage` new category (`isHidden: false`). Actua: `ActualEntityWriter.createCategory`
(`data/budget/ActualEntityWriter.kt:447`), `createCategoryGroup` (`:437`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| New category is inserted first in its group: neighbors shoved (`shoveSortOrders`, increment 16384) and the new row gets the midpoint | `insertCategory` (not `atEnd`) | `SortOrder.shove` before the group's first category (`data/budget/SortOrder.kt`, an exact port) | Match |
| New category writes its `category_mapping` self-row in the same batch | `insertCategory` | same batch | Match |
| `is_income` from the group | client passes the group's flag | from the group | Match |
| Add a category to the income group | offered (mobile `BudgetPage` passes `isIncome`) | not offered: "Add category" is hidden for the income group (`ui/categories/ManageCategoriesScreen.kt:308`) | **N/A** |
| `hidden` on a new category | `0` (hidden only through a hidden group) | `0` | Match ([#927](https://github.com/azimul-kabir/actua/issues/927)) |
| Duplicate category name in the same group (case-insensitive, hidden included) rejected | client and `insertCategory` | rejected | Match |
| Duplicate group name (case-insensitive, live groups) rejected | `insertCategoryGroup` | rejected | Match |
| New group: `sort_order` = last live group's (income included) + 16384; `is_income = 0`, `hidden = 0` | `insertCategoryGroup` | same | Match |
| Names trimmed | category: `name.trim()`; group: as typed | both trimmed; empty rejected | **Intentional** for groups (avoids `Bills ` next to `Bills`; other clients see an ordinary name) |

## 3. Rename and hide

Upstream: `category-update` / `category-group-update`
([`app.ts#L332-L352`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L332-L352),
[`#L445-L447`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L445-L447)),
`updateCategoryGroup`
([`LC/server/db/index.ts#L393-L411`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L393-L411)),
`useSaveCategoryMutation`, `useUpdateCategoryGroupMutation`
([`DC/budget/mutations.ts#L401-L446`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/budget/mutations.ts#L401-L446)).
Actua: `ActualEntityWriter.renameCategory`/`setCategoryHidden` (`:102-103`),
`renameCategoryGroup`/`setCategoryGroupHidden` (`:143-144`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Rename writes `name` only | `category-update`, `category-group-update` | `name` only | Match |
| Category rename to a name used by another category in its group rejected | `useSaveCategoryMutation` | not checked | **Divergence** [#928](https://github.com/azimul-kabir/actua/issues/928) |
| Group rename to a name used by another live group rejected | client and `updateCategoryGroup` | not checked | **Divergence** [#928](https://github.com/azimul-kabir/actua/issues/928) |
| Hide/unhide a category writes `hidden` only | `category-update` | same | Match |
| Hide/unhide a group writes the group's `hidden` only; its categories keep their own flags and are hidden through the group | `category-group-update` | same | Match |

## 4. Delete

Upstream: `category-delete`
([`app.ts#L368-L413`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L368-L413)),
`category-group-delete` ([`#L461-L482`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L461-L482)),
`must-category-transfer` ([`#L484-L511`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L484-L511)),
`deleteCategory` / `deleteCategoryGroup`
([`LC/server/db/index.ts#L530-L554`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L530-L554),
[`#L428-L440`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L428-L440)),
`doTransfer` ([`LC/server/budget/base.ts#L245-L266`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/base.ts#L245-L266)),
`useDeleteCategoryMutation` / `useDeleteCategoryGroupMutation`
([`DC/budget/mutations.ts#L253-L308`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/budget/mutations.ts#L253-L308),
[`#L467-L529`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/budget/mutations.ts#L467-L529)).
Actua: `ActuaRepository.deleteCategory` (`data/ActuaRepository.kt:1429`) →
`ActualEntityWriter.deleteCategory` (`:142`); `deleteCategoryGroup` (`:302`, not reachable from the UI).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| A category with live transactions (through `category_mapping`) or a non-zero budget amount in any created month must be transferred: the user picks a same-type target | `must-category-transfer`, `confirm-category-delete` | same check (`ActualBudgetDatabase.categoryDeleteRequiresTransfer`); the delete dialog then requires a visible same-type target (`ui/categories/CategoryDeleteDialog.kt`) | Match ([#926](https://github.com/azimul-kabir/actua/issues/926)) |
| Transfer: expense budget amounts added to the target in every created month; existing `category_mapping` rows pointing at the category, then its own row, forwarded to the target; income ↔ expense rejected | `category-delete` with `transferId` | `ActualEntityWriter.deleteCategory(id, transferId)`: same writes in one batch, over the budget range | Match ([#926](https://github.com/azimul-kabir/actua/issues/926)) |
| A category with no transactions and no budget amounts: tombstone only | `deleteCategory` without `transferId` | tombstone only | Match |
| Transactions that still reference a deleted, unmapped category read as having none | `v_transactions` joins live categories | same (`ActualBudgetReadModelTest.deletedPayeesAndCategoriesReadAsNoneLikeActualsTransactionView`) | Match |
| Delete group: transfer check over its categories, then every category and the group tombstoned in one batch | `useDeleteCategoryGroupMutation`, `deleteCategoryGroup` | not offered in the UI; the unused writer tombstones without a transfer | **N/A** (the writer gap is noted on [#926](https://github.com/azimul-kabir/actua/issues/926)) |
| Income group can't be deleted | no delete action for it | writer refuses | Match |

Tests: `src/androidTest/.../data/budget/CategoryDeleteTransferTest`,
`ActualBudgetReadModelTest.deletingCategoryUsesTombstoneMutation`, `src/test/.../data/budget/CategoryDeletePlanTest`.

## 5. Reorder and move

Upstream: `category-move` / `category-group-move`
([`app.ts#L354-L366`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L354-L366),
[`#L449-L459`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/app.ts#L449-L459)),
`moveCategory` / `moveCategoryGroup`
([`LC/server/db/index.ts#L509-L528`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L509-L528),
[`#L413-L426`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L413-L426)),
`shoveSortOrders` ([`LC/server/db/sort.ts#L24-L67`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/sort.ts#L24-L67)),
`useReorderCategoryMutation` ([`DC/budget/mutations.ts#L337-L373`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/budget/mutations.ts#L337-L373)),
separate `category` / `income-category` drag types (`DC/components/budget/ExpenseCategory.tsx`,
`IncomeCategory.tsx`). Actua: `ActualEntityWriter.moveCategory` (`:318`), `moveCategoryGroup`
(`:343`), `data/budget/CategoryReorderPlanner.kt`, `SortOrder.kt`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Category move writes the shoved neighbors' `sort_order`, then the category's `sort_order` and `cat_group` | `moveCategory` | same | Match ([#927](https://github.com/azimul-kabir/actua/issues/927)) |
| Expense categories can't be moved into the income group or back | separate drag types | the reorder UI and up/down controls skip the income group (`CategoryReorderPlanner.previousGroup`/`nextGroup`) | Match |
| Move into a group that already has a category with that name (case-insensitive) rejected | `useReorderCategoryMutation` | not checked | **Divergence** [#928](https://github.com/azimul-kabir/actua/issues/928) |
| Placement: before a target (midpoint, neighbors shoved when the gap is ≤ 2) or at the end (+16384) | `shoveSortOrders` over the destination group, the moving row included | the same rules over the destination group without the moving row | Match (the resulting order is the same; the written `sort_order` value can differ when the moving row is next to its target) |
| Group move: income group fixed; others placed with the same shove rules | the income group sorts last by `is_income`; `shoveSortOrders` over all live groups | the income group can't be moved or used as a target; shove over the expense groups | Match (same displayed order) |
| Alphabetical sort of categories (`categories-sort`) | desktop and mobile budget pages | not offered | **N/A** |

Tests: `src/androidTest/.../data/budget/ActualEntityWriterReorderTest`,
`src/test/.../data/budget/CategoryReorderPlannerTest`.

## 6. Notes and carryover

Upstream: notes are the `notes` row with `id = <category id>`; `budget/set-carryover`
→ `setCategoryCarryover` ([`LC/server/budget/actions.ts#L704-L721`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/actions.ts#L704-L721))
sets `carryover` from the chosen month through the last created month (`getAllMonths`; created
months end 12 months after the current month, `getBudgetRange`,
[`LC/server/budget/base.ts#L20-L39`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/base.ts#L20-L39)).
Actua: `ActuaRepository.setCategoryNote` (`:1569`), `setCategoryCarryover` (`:1595`),
`ActualBudgetWriter.setCarryover`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Category note is `notes {id: <category id>, note}` | notes handlers | same; a valid `#template` note also refreshes the category's note template | Match (templates: `docs/BUDGET_AUTOMATION_PARITY.md`) |
| Rollover overspending sets `carryover` on `zero_budgets`/`reflect_budgets` from the chosen month through current month + 12, creating missing month rows | `setCategoryCarryover` | same months and rows, one batch | Match (amounts: `docs/BUDGET_PARITY.md`) |

## 7. `category_mapping` read paths

Upstream: every transaction read resolves the category through `category_mapping`
(`v_transactions_internal`, `MIG/1608652596044_trans_views.sql`; budget activity in
`LC/server/budget/base.ts`; `must-category-transfer`). Actua resolves
`COALESCE(cm.transferId, t.category)` in each read (fixed for merged categories by
[#636](https://github.com/azimul-kabir/actua/issues/636)).

| Read path | Actua | Status |
| --- | --- | --- |
| Transaction lists and status filters | `ActualBudgetDatabase.fetchTransactions` (`:2062`, `:2086`) | Match |
| Search matching a split child's category | `:2018` | Match |
| Split portions shown under a parent | `:1273` | Match |
| Budget activity (spent per category, per month) | `categoryActivity` (`:1539`, `:1581`) | Match |
| Reports and Accounts summaries | built from `fetchTransactions` rows (`data/ActuaRepository.kt:1009`, `:1015`) | Match |
| Rules conditions and actions | `data/rules/RuleModels.kt` (mapping-resolved ids) | Match (`ActualBudgetReadModelTest.rulesKeyedToMergedPayeesAndCategoriesFollowTheMapping`) |
| Category learning register | `fetchCategoryLearningRegister` (`:724`) | Match |

Tests: `ActualBudgetReadModelTest.transactionsInAMergedCategoryReportTheTargetCategoryId`.

## Divergences

| Issue | Severity | Summary |
| --- | --- | --- |
| [#926](https://github.com/azimul-kabir/actua/issues/926) | P2 | Deleting a category with transactions or budget amounts doesn't transfer them (fixed) |
| [#927](https://github.com/azimul-kabir/actua/issues/927) | Lower | Moving or creating a category copies its group's hidden flag onto it (fixed) |
| [#928](https://github.com/azimul-kabir/actua/issues/928) | Lower | Category rename/move and group rename allow duplicate names |
