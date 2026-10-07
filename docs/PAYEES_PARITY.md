# Payees parity: create, rename, merge, delete, mapping and locations

Feature-by-feature audit of Actual Budget payee behavior against Actua, tracked in
[#665](https://github.com/azimul-kabir/actua/issues/665) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Payee *rule* matching operators are
covered by `docs/RULES_PARITY.md`, and transfer/split writes by `docs/TRANSACTIONS_PARITY.md`.

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit the other parity docs pin). Links use `LC/` =
  `packages/loot-core/src/`, `DC/` = `packages/desktop-client/src/` and `MIG/` =
  `packages/loot-core/migrations/` at that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Status:** **Match** = same rows, CRDT messages and displayed values; **Intentional** = an
  Android-only difference that other clients can't observe; **Divergence** = filed as an issue;
  **N/A** = upstream behavior Actua doesn't offer.

## 1. Schema

Upstream: `MIG/1550601598648_payees.sql`, `MIG/1720664867241_add_payee_favorite.sql`,
`MIG/1737158400000_add_learn_categories_to_payees.sql`, `MIG/1768872504000_add_payee_locations.sql`.
Actua: `data/budget/ActualMigrations.kt` (applies the same migration ids),
`data/budget/BlankBudgetFactory.kt:81`, `ActualBudgetDatabase` payee-location migration (`:1934`).

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| `payees(id, name, category, tombstone, transfer_acct, favorite DEFAULT 0, learn_categories DEFAULT 1)` | migrations | same columns, also in new blank budgets | Match |
| `payee_mapping(id, targetId)` | `1550601598648_payees.sql` | same | Match |
| `payee_locations(id, payee_id, latitude, longitude, created_at, tombstone DEFAULT 0)` plus its three indexes | `1768872504000_add_payee_locations.sql` | same table and index definitions | Match |
| `payees.category` (legacy) | unused by v26.9.0 code | unused | Match |

## 2. Create

Upstream: `insertPayee` ([`LC/server/db/index.ts#L568-L578`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L568-L578)),
`createPayee` (import/bank sync, [`LC/server/accounts/payees.ts#L3-L16`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/accounts/payees.ts#L3-L16)),
`payee-create` ([`LC/server/payees/app.ts#L57-L59`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/payees/app.ts#L57-L59)),
autocomplete "Create payee" ([`DC/components/autocomplete/PayeeAutocomplete.tsx#L480-L536`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/autocomplete/PayeeAutocomplete.tsx#L481-L536)).
Actua: `ActualTransactionWriter.resolveOrCreatePayee` (`data/budget/ActualTransactionWriter.kt:26`),
`ActualBudgetDatabase.findPayeeByName` (`:516`) / `insertPayee` (`:871`), used by the transaction
form (`ActualTransactionFormService.resolvePayee`, `:359`), imports and bank sync
(`data/bank/BankSyncService.kt:126-133`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| New payee = `payees {name}` + `payee_mapping {id → id}` in one batch | `insertPayee` | `payees {name, transfer_acct: null, tombstone: 0}` + mapping, one transaction | Match. The extra cells are the column defaults. |
| Implicit create from transaction entry, reusing an existing payee with the same name | PWA hides "Create payee" when the top match equals the input after `getNormalisedString` (lower-case, diacritics removed); import/bank sync use `createPayee` (`UNICODE_LOWER`) | create-or-reuse by full Unicode lower-casing (`PayeeNames.unicodeLower`, JavaScript `toLowerCase()` semantics) over live payees | Match ([#905](https://github.com/azimul-kabir/actua/pull/905) for [#898](https://github.com/azimul-kabir/actua/issues/898)). Picker typing doesn't strip diacritics; saving reuses the case-folded match. |
| Name trimming | name stored as typed | trimmed; empty rejected | **Intentional.** Avoids creating `Shop ` next to `Shop`; other clients see an ordinary name. |
| Duplicate names | possible (`payee-create` doesn't check) | possible via sync; lookups take the first live match | Match |
| Payee written before the transaction, not in the same batch | `payee-create` then `transactions-batch-update` | `insertPayee` then the transaction write | Match |
| Explicit create without a transaction | Payees page "Add payee" (`payees-batch-change`) | not offered; payees are created from transactions, rules and imports | **Intentional** (the payee picker creates payees on save) |

Tests: `src/androidTest/.../data/budget/ActualBudgetReadModelTest` (`resolveOrCreatePayee("New Shop")`
reused for `"new shop"`, `:298`).

## 3. Rename, merge and delete

Upstream: `updatePayee`, `deletePayee`, `mergePayees`
([`LC/server/db/index.ts#L580-L648`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L580-L648)),
`payees-batch-change` / `payees-merge` ([`LC/server/payees/app.ts#L93-L129`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/payees/app.ts#L93-L129)),
Payees page (`DC/components/payees/`, `DC/components/mobile/payees/`).
Actua: `ActualEntityWriter.renamePayee` / `deletePayees` / `mergePayees` / `setPayeeFavorite` /
`setPayeeLearnCategories` / `setLearnCategoriesEnabled`, called from Manage → Payees
(`ui/settings/ManagePayeesScreen.kt`, [#903](https://github.com/azimul-kabir/actua/pull/903)).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| UI to rename, merge, delete, favorite and set "learn categories" (per payee and the global preference) | Payees page (desktop and mobile) | Manage → Payees: per-row actions, long-press selection for merge (choose the payee to keep) and bulk delete, confirmation dialogs | Match ([#896](https://github.com/azimul-kabir/actua/issues/896)) |
| Rule counts per payee and the orphaned-payees filter | Payees page | not shown | **Intentional** for now: informational only, no data effect |
| Rename writes `payees.name` only | `updatePayee` | `payees.name`, trimmed and non-empty; transfer payees refused | Match |
| Delete tombstones the payee; `payee_mapping` and transactions are untouched, so the transactions show no payee | `deletePayee` | `payees.tombstone = 1` only, several payees in one batch. Reads show no payee for a tombstoned payee ([#711](https://github.com/azimul-kabir/actua/issues/711)). | Match |
| Delete of a transfer payee is a no-op | early `return` | `require` fails | **Intentional** (writer refuses rather than silently ignoring) |
| Rules referencing a deleted payee are left alone | no rule changes | no rule changes | Match |
| Merge: re-point every mapping whose `targetId` is a merged id, then `payee_mapping[id] = target` and tombstone each merged payee, one batch | `mergePayees` | re-points every mapping targeting a merged payee, then maps and tombstones the merged payees, one batch | Match (fixed by [#902](https://github.com/azimul-kabir/actua/pull/902) for [#895](https://github.com/azimul-kabir/actua/issues/895)) |
| Merge never touches rules; rules follow merges by id mapping at load time | `migrateIds` | `fetchRules` maps ids through `payee_mapping`/`category_mapping` | Match (fixed by [#900](https://github.com/azimul-kabir/actua/pull/900) for [#893](https://github.com/azimul-kabir/actua/issues/893)), see §6 |
| Transfer target or transfer sources | target: no-op; sources: filtered out | `require` fails | **Intentional** (refuses instead of partially applying) |
| Undo | `undoable` handlers | Actua has no undo | N/A |

## 4. Transfer payees

Upstream: account creation (`insertPayee({name: '', transfer_acct})`), `v_payees`
([`LC/server/aql/schema/index.ts#L314-L328`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L314-L328)),
`getActivePayees` ([`DC/payees/queries.ts#L78-L92`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/payees/queries.ts#L78-L92)).
Actua: `ActualEntityWriter.createAccount` (`:352`), `ActualBudgetDatabase.transferPayeeId` (`:236`),
transaction selects (`:1791`, `:1831`), payee picker (`ui/navigation/AppNavigation.kt:1624`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Created with the account: `name = ''`, `transfer_acct = account`, self mapping | `createAccount` | same rows in the account's batch | Match (see `docs/ACCOUNTS_PARITY.md` §1) |
| Display name = the account name; hidden when the account is tombstoned | `v_payees` `COALESCE(accounts.name, name)` | `COALESCE(pa.name, p.name)` with a live-account join | Match |
| Can't be renamed, deleted or merged on their own | Payees page disables them; server guards | no UI; writer `require`s | Match |
| Tombstoned with the account on force close / delete | `closeAccount` | `ActualEntityWriter` close/delete path | Match |
| Picker hides transfer payees of closed accounts | `getActivePayees` | `Transfer:` options built from non-closed accounts | Match |
| Transfer payees offered on split lines | transfer split children supported | not offered ([#748](https://github.com/azimul-kabir/actua/issues/748)) | **Intentional** (documented in `BACKEND_PARITY.md`) |

## 5. `favorite`, `learn_categories` and category learning

Upstream: `getPayeeSuggestions` ([`DC/components/autocomplete/PayeeAutocomplete.tsx#L55-L97`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/autocomplete/PayeeAutocomplete.tsx#L55-L97)),
`updateCategoryRules` ([`LC/server/transactions/transaction-rules.ts#L888-L995`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/transaction-rules.ts#L888-L996)),
called from `batchUpdateTransactions` ([`LC/server/transactions/index.ts#L153-L165`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/index.ts#L153-L165))
when the synced `learn-categories` preference isn't `"false"`
([`DC/components/payees/CategoryLearning.tsx`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/payees/CategoryLearning.tsx)).
Actua: `ActualBudgetDatabase.fetchPayees` (`:501`) doesn't read either column; the `payees` writer
allowlist is `name`, `tombstone` (`ActualEntityWriter.kt:436`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `favorite` read: favorites lead the picker's Suggested Payees | `getPayeeSuggestions` | same (`PayeeSuggestions`) | Match ([#897](https://github.com/azimul-kabir/actua/issues/897)) |
| `learn_categories` read: payees set to 0 are left out of learning | `updateCategoryRules` register | same filter (skipped on schemas without the column, where every payee learns) | Match |
| `favorite` / `learn_categories` written from the Payees page | `payees-batch-change` | integer 0/1 cells for ordinary payees | Match ([#896](https://github.com/azimul-kabir/actua/issues/896)) |
| Category learning: after a categorised add/edit, for each payee with `learn_categories = 1`, take its latest 5 non-parent transactions in open accounts (±180 days); a category used ≥ 3 times updates every `payee is X → set category` default-stage rule, or creates one | `updateCategoryRules` | runs after transaction-form saves (rows whose `category` cell the save set to a non-null value), with the same register, 5-row window, ≥ 3 vote rule and setter-rule update/create; all rule writes in one batch (`ActualEntityWriter.learnCategories`, `data/rules/CategoryLearning.kt`) | Match (fixed by [#901](https://github.com/azimul-kabir/actua/pull/901) for [#894](https://github.com/azimul-kabir/actua/issues/894)). Like the desktop register; Actual's mobile editor and bank sync don't learn, and neither do Actua's imports and bank sync. |
| Learned rules created by other clients are applied | rules engine | applied like any other rule | Match |

## 6. Every read that accepts a payee id resolves through `payee_mapping`

Upstream resolves a transaction's payee as `pm.targetId` in `v_transactions_internal`
([`LC/server/aql/schema/index.ts#L384-L403`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L385-L402)),
schedule payees through `pm.targetId` (`#L350`, `#L369`), and rule ids through the in-memory
mapping table (`LC/server/db/mappings.ts`, `migrateIds` in
[`LC/server/rules/rule-utils.ts#L112-L159`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/rules/rule-utils.ts#L112-L159)).
Mapping lookups are one hop in every path.

| Read path | Actua | Resolves? |
| --- | --- | --- |
| Transaction list, single fetch, search results (`transactionSelect`, `ActualBudgetDatabase.kt:1791`) | `p.id` from `COALESCE(pm.targetId, t.description)` → `ActualTransaction.payeeId`/`payeeName` | Yes ([#640](https://github.com/azimul-kabir/actua/issues/640)) |
| Split children (`transactionChildSelect`, `:1831`) and split portions (`:1113`) | same join | Yes |
| Parent row's shared child payee (`child_payee`, `:1806-1816`) | one distinct raw `description` across children, then mapped | Yes for the shown name. Two children on different raw ids that map to one target show no shared payee: an Actua-only display hint, no data effect. |
| Global search, payee text (`transactionSearchClause`, `:1763`) | parent and child payees joined through the mapping | Yes |
| Reports, dashboards, custom/saved reports (`data/reports/*`) | consume `ActualTransaction.payeeId` | Yes. Saved filter *values* compare raw ids, as upstream AQL filters do. |
| Schedules: payee from the schedule rule's `payee is` condition (`:743`, `:804`) | `SELECT targetId FROM payee_mapping` | Yes |
| Find Schedules discovery (`fetchDiscoveryTransactions`, `:674`) | `JOIN payee_mapping` | Yes |
| Bank sync fuzzy match candidates (`fuzzyMatchCandidates`, `:277`) | `COALESCE(pm.targetId, t.description)` | Yes ([#640](https://github.com/azimul-kabir/actua/issues/640)) |
| Bank sync / import new-payee lookup (`findPayeeByName`) | by name over live payees | N/A (name lookup, not an id) |
| Rules: id-typed `is`/`isNot`/`oneOf`/`notOneOf` condition values and `set` actions (`fetchRules` → `Rule.withMappedIds`) | mapped on read, stored JSON unchanged | Yes ([#893](https://github.com/azimul-kabir/actua/issues/893)) |
| Rule editor and rule-preview payee names (`ruleContext().payeeNames`) | rules arrive mapped, so names come from the surviving payee; saving an edited rule stores the mapped ids, as upstream `serialize` does | Yes ([#893](https://github.com/azimul-kabir/actua/issues/893)) |
| Payee locations (`fetchNearbyPayees`, `fetchPayeeLocations`) | raw `payee_id`; locations of a merged payee drop out with the tombstoned payee | Match: upstream `getNearbyPayees` joins `payees` directly without the mapping |

## 7. Payee picker ordering and filtering

Upstream: `PayeeAutocomplete` sections Nearby → Suggested (favorites + `getCommonPayees`, max 5)
→ Payees → Transfer To/From
([`#L271-L320`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/autocomplete/PayeeAutocomplete.tsx#L271-L320));
filtering with case-insensitive `Fzf`, max 100 results; `getPayees` order
`transfer_acct IS NULL DESC, name COLLATE NOCASE, offbudget, sort_order`
([`LC/server/db/index.ts#L650-L686`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L650-L686)).
Actua: `ActuaRepository.payeeNames` (`:185`), `AppNavigation.kt:1624`,
`AddTransactionScreen` picker (`:1040-1052`), `filterPickerOptions` (`:1304`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Nearby section first when location is enabled | yes | yes (opt-in) | Match |
| Suggested Payees (every favorite by name, then common payees from the last 12 weeks up to 5 in total, by name), left out of the main list | yes | "Suggested" group after Nearby while the search is empty (`ActuaRepository.suggestedPayeeNames`, `fetchCommonPayeeNames`) | Match ([#904](https://github.com/azimul-kabir/actua/pull/904) for [#897](https://github.com/azimul-kabir/actua/issues/897)) |
| Ordinary payees alphabetical, case-insensitive | `COLLATE NOCASE` | `CASE_INSENSITIVE_ORDER`, grouped by first letter | Match (letter headers are Android presentation) |
| Transfers in their own section after payees | Transfer To/From, ordered on-budget first then `sort_order` | `Transfer:` section, alphabetical | **Intentional.** Same members; Android orders by name. |
| Typing filters ordinary and transfer entries together | Fzf fuzzy subsequence | case-insensitive substring, alphabetical | **Intentional** (documented in `BACKEND_PARITY.md`). Substring is stricter than fuzzy; no wrong matches. |
| Exact match suppresses "Create payee" | `getNormalisedString` (also strips diacritics) | "Add" is hidden for a case-insensitive exact match; saving reuses the payee found by `findPayeeByName` | **Intentional**: the diacritic-insensitive picker hint is UI-only; the server-visible create-or-reuse rule matches (§2) |

## 8. Payee locations

Upstream: `payee-location-create`, `payee-locations-get`, `payee-location-delete`,
`payees-get-nearby` ([`LC/server/payees/app.ts#L148-L357`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/payees/app.ts#L148-L357)),
mobile save prompt ([`DC/components/mobile/transactions/TransactionEdit.tsx#L1952-L1973`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/transactions/TransactionEdit.tsx#L1952-L1973)),
`DEFAULT_MAX_DISTANCE_METERS = 500` (`LC/shared/constants.ts`).
Actua: `data/location/PayeeLocationWriter.kt`, `data/location/PayeeLocation.kt` (`LocationUtils`),
`ActualBudgetDatabase.fetchPayeeLocations` (`:546`) / `fetchNearbyPayees` (`:570`),
`ui/settings/PayeeLocationsScreen.kt`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Create: validated coordinates; row `{payee_id, latitude, longitude, created_at = now ms}` via `insertWithUUID` (`tombstone` default) | `createPayeeLocation` | same cells plus explicit `tombstone = 0`, one CRDT batch; gated on the table/columns existing | Match |
| Save offered only when the payee has no saved location within 500 m of the current fix | mobile `TransactionEdit` | `LocationUtils.shouldRecord` with the same 500 m radius | Match |
| Save trigger | user taps the save prompt | per-payee Save action, plus opt-in recording on new transactions | **Intentional** (opt-in Android behavior; same rows) |
| Locations only for ordinary payees | not enforced on the server | writer `require`s an ordinary payee | **Intentional** (stricter; upstream UI never offers transfer payees) |
| List: live rows, optional payee filter, `ORDER BY created_at DESC` | `getPayeeLocations` | same, also skipping incomplete synced rows and invalid coordinates | **Intentional** (defensive read; complete rows match) |
| Delete: tombstone one location | `delete_` | `tombstone = 1`; also bulk "clear for payee" in one batch | Match |
| Nearby: haversine (R = 6371 km), `distance ≤ maxDistance` (500 m), closest location per payee, `ORDER BY distance`, `LIMIT 10`; live payees and locations only | SQL in `getNearbyPayees` | same formula (`atan2` form), same per-payee min, sort and limit, in Kotlin | Match |
| Nearby includes transfer payees that have locations | yes (no filter) | excluded | **Intentional.** Actua never stores them; a location synced for one is ignored. |
| Nearby results filtered by typed text | Fzf on name | substring filter | **Intentional** (as §7) |

Tests: `src/androidTest/.../data/location/PayeeLocationDatabaseTest` (migration, ranking/dedup,
writer CRDT messages, schema gate, partial rows), `src/test/.../ui/transactions/PayeeLocationUiPolicyTest`,
`src/androidTest/.../ui/settings/PayeeLocationsScreenTest`.

## Filed divergences

- [#893](https://github.com/azimul-kabir/actua/issues/893): Rules keyed to a merged payee or category stop matching after the merge (P2). Fixed by [#900](https://github.com/azimul-kabir/actua/pull/900).
- [#894](https://github.com/azimul-kabir/actua/issues/894): Saving a transaction doesn't learn the payee's category (P2). Fixed by [#901](https://github.com/azimul-kabir/actua/pull/901).
- [#895](https://github.com/azimul-kabir/actua/issues/895): Merging a payee leaves earlier merges pointing at the merged payee (latent; blocks #896). Fixed by [#902](https://github.com/azimul-kabir/actua/pull/902).
- [#896](https://github.com/azimul-kabir/actua/issues/896): No payee management (rename, merge, delete, favorite, learn categories). Fixed by [#903](https://github.com/azimul-kabir/actua/pull/903).
- [#897](https://github.com/azimul-kabir/actua/issues/897): Payee picker has no Suggested Payees section (low). Fixed by [#904](https://github.com/azimul-kabir/actua/pull/904).
- [#898](https://github.com/azimul-kabir/actua/issues/898): Payee name lookup is ASCII-only case-insensitive (P2). Fixed by [#905](https://github.com/azimul-kabir/actua/pull/905).

**Limitations:** this audit compares source at the pinned commit and Actua's code; it doesn't
run a live PWA. `ActualEntityWriter.renamePayee`/`deletePayee`/`mergePayees` have no test
coverage before #895/#896; both added it.
