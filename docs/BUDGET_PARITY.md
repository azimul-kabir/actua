# Budget parity: month calculations and budget actions (envelope and tracking)

Feature-by-feature audit of Actual Budget's budget month cells and budget actions against Actua,
tracked in [#667](https://github.com/azimul-kabir/actua/issues/667) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Budget templates, cleanup and goals are
audited in `docs/BUDGET_AUTOMATION_PARITY.md`.

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit the other parity docs pin). `LC/` = `packages/loot-core/src/`. Main sources:
  [`LC/server/budget/base.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/base.ts),
  [`envelope.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/envelope.ts),
  [`tracking.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/tracking.ts),
  [`actions.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/actions.ts).
  The issue names `report.ts`; in v26.9.0 the tracking ("report") budget lives in `tracking.ts`.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`:
  `ActualBudgetDatabase.fetchBudgetMonth` (`data/budget/ActualBudgetDatabase.kt:1507`),
  `ActualBudgetWriter` (`data/budget/ActualBudgetWriter.kt`), the repository budget actions
  (`data/ActuaRepository.kt:1444-1625`) and `ui/budget/BudgetScreen.kt`.
- **Status:** **Match** = same rows, CRDT messages and displayed values; **Intentional** = an
  Android-only difference that other clients can't observe; **Divergence** = filed as an issue;
  **N/A** = upstream behavior Actua doesn't offer.

## Evidence: Actual's engine on a synthetic budget

`docs/tools/budget-fixture/generate.mjs` builds an envelope budget and a tracking budget in
`@actual-app/api` 26.9.0, Actual's own engine running offline. Both cover March–July 2026, with:

- overspending with rollover off and on;
- a manual hold, and a hold that is set and then reset;
- an income category held automatically;
- a refund and a two-category split;
- a categorised off-budget transaction and a deleted transaction;
- a category deleted into another one;
- a hidden category and a hidden group;
- a future month with budgets but no activity.

It records every month's cells as Actual computes them in
`app/src/androidTest/assets/budget-parity/upstream-26.9.0.json`.
`BudgetMonthParityFixtureTest` (androidTest) loads the same rows into SQLite. For each month it
compares `fetchBudgetMonth`'s To Budget, held amount and per-category budgeted, spent, balance and
carryover with Actual's values (income received, plus income budgeted for tracking). The
`budget-month-parity` workflow regenerates the fixture and fails if it changed. See the
[fixture README](tools/budget-fixture/README.md).

Result: no cell differs (`KNOWN_DIVERGENCES` is empty). Before committing, the fixture was also
checked with a line-for-line Python port of Actua's month walk.

## 1. Which table and budget type

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Type: `preferences.budgetType`, `tracking` → `reflect_budgets`, anything else → `zero_budgets` | `isTrackingBudget`, `getBudgetType` | `budgetTable()` (`:1634`): `tracking` or the legacy `report` → `reflect_budgets`; falls back to whichever table exists | Match. `report` is the pre-rename value. |
| Switching type | `setType` rebuilds the sheet; rows of the other table stay | read-only; Actua never writes `budgetType` | N/A (switch in the PWA; Actua follows the synced preference) |
| `zero_budget_months` (holds) used only by envelope budgets | `loadUserBudgets` | read only when envelope | Match |

## 2. Envelope cells (`zero_budgets`)

Upstream: `envelope.ts` `createCategory`, `createSummary`; `base.ts` `createCategory`
(`sum-amount`). Actua: the month walk in `fetchBudgetMonth`.

| Cell | Actual | Actua | Status |
| --- | --- | --- | --- |
| `sum-amount-<cat>` (spent) | `SUM(amount)` over `v_transactions_internal_alive` in the month, category through `category_mapping`, parents excluded, children only with a live parent, `accounts.offbudget = 0` | same predicates in one grouped query | Match (fixture). Actua also skips rows of tombstoned accounts, which Actual's account deletion never leaves behind. |
| `budget-<cat>` | stored `amount`, `null` → 0 | `amount`, `null` → 0 | Match |
| `leftover-<cat>` (balance) | `budget + spent + (prev carryover ? prev leftover : max(0, prev leftover))` | same recurrence | Match (fixture) |
| `carryover-<cat>` (rollover) | stored `carryover = 1` for that month; affects next month | same | Match |
| `last-month-overspent` | Σ `min(0, prev leftover)` over expense categories without rollover last month | same | Match (fixture) |
| `total-budgeted` | −Σ budgets of every live expense category, **including hidden categories and groups** | `assigned` sums every live expense category | Match for To Budget |
| `buffered` / `buffered-auto` / `buffered-selected` | manual hold from `zero_budget_months`; else Σ income received in categories with rollover this month | `held = manual or automatic` | Match (fixture) |
| `from-last-month` | prev `to-budget` + prev `buffered-selected` | carried in the walk | Match (fixture) |
| `available-funds` / `to-budget` | income + from last month; + overspent + total budgeted − held | same | Match (fixture) |
| `total-income` | the (first) income group's received | Σ live income categories | Match (one income group, as Actual creates) |
| Header and group totals: total budgeted / spent / balance | include hidden categories and groups; not affected by view filters | `budgetOverview` and group headers (`budgetTotalCategories`) include them; rows still follow the view filters | Match (fixed by [#916](https://github.com/azimul-kabir/actua/pull/916) for [#909](https://github.com/azimul-kabir/actua/issues/909)) |
| Month range: from 3 months before the earliest non-child transaction to 12 months after the current month; rows outside are ignored | `getBudgetRange` | walk starts at the earliest row of any kind; any month can be opened | **Divergence** [#911](https://github.com/azimul-kabir/actua/issues/911) (low) |
| First month in range | blank previous sheet: everything 0, no rollover | walk starts from 0 | Match |

## 3. Tracking cells (`reflect_budgets`)

Upstream: `tracking.ts`. Actua: the same walk with `envelope = false`.

| Cell | Actual | Actua | Status |
| --- | --- | --- | --- |
| Expense `leftover` | `budget + spent + (prev carryover ? prev leftover : 0)`, so a negative balance never leaks into the next month without rollover | same | Match (fixture) |
| Income budgeted / received | `budget-<cat>`, `sum-amount-<cat>` | `budgetedCents`, `receivedCents` | Match (fixture) |
| Income `leftover` = `budget − received (+ prev if rollover)` | shown in the PWA income rows | not shown | N/A (display) |
| No To Budget, holds or `last-month-overspent` | – | `toBudgetCents = null`, `bufferedCents = 0` | Match |
| Group and month totals exclude hidden categories and groups | `createCategoryGroup`/`createSummary` filters | header sums visible categories | Match |
| `total-saved` (budgeted income − budgeted expenses), `real-saved` (income − spent); the mobile budget shows "Projected savings" from the current month on and "Saved"/"Overspent" before | summary cells, `BudgetTable` `Saved` | shown in the To Budget slot the same way (`BudgetOverview.lead`) | Match ([#916](https://github.com/azimul-kabir/actua/pull/916)) |

## 4. Writes: integer cents and the same CRDT cells

Every budget write goes through `ActualBudgetWriter`, which applies CRDT messages with
`applyLocalMessages` and saves the clock and Merkle root.

| Write | Actual | Actua | Status |
| --- | --- | --- | --- |
| Set amount | `setBudget`: update `amount` on the existing row, or insert `{id: "<YYYYMM>-<cat>", month, category, amount}` | `setAmount`/`setAmounts`: existing row → `amount`; else `month`, `category`, `amount` on row `"<YYYYMM>-<cat>"` | Match. Amounts are `Long` cents serialized as `N:` integers. |
| Rollover | `setCategoryCarryover`: `carryover` 1/0 on every month from the start month to the last created month (current + 12) | `setCarryover` over the start month … now + 12 | Match |
| Hold for next month | `holdForNextMonth`: only when To Budget > 0; new hold = held + clamp(amount, −held, To Budget); `zero_budget_months {id: "YYYY-MM", buffered}` | the sheet edits the total hold, limited to `[0, To Budget + held]`; same row and cell | **Intentional.** Same reachable range and stored cell. Actua also lets you lower a hold while To Budget ≤ 0, which the PWA refuses until you reset it. |
| Reset hold | `buffered = 0` | `resetBuffer` → 0 | Match |
| Multi-cell actions in one batch | `batchMessages` | one `applyLocalMessages` per action | Match |

Tests: `src/androidTest/.../data/budget/ActualBudgetReadModelTest` (amount and transfer writes and
their CRDT messages), `ActualBudgetBufferTest` (holds), `BudgetMonthParityFixtureTest`.

## 5. Budget actions

| Action | Actual | Actua | Status |
| --- | --- | --- | --- |
| Set an amount | `budget/budget-amount` | budget sheet → `setBudgetAmount` | Match |
| Move between categories | `transferCategory`: no cap; appends a "Reassigned …" line to the `budget-<month>` note | Move Money: limited to the source's available balance (or To Budget); appends the same note (`BudgetMovementNote`) | Cap: **Intentional** (stricter, never produces a state Actual couldn't). Note: Match ([#915](https://github.com/azimul-kabir/actua/pull/915)) |
| Cover overspending | `coverOverspending`: up to the source's leftover; note | Overspent sheet → Move Money pre-filled with the capped amount; note, including "from To Budget" covers | Match ([#915](https://github.com/azimul-kabir/actua/pull/915)) |
| Move from To Budget to a category | `transferAvailable`: clamps to `[0, to-budget]` | To Budget sheet "Move to Category": limited to To Budget (`budgetSummaryMoveAmount`) | Match (fixed by [#914](https://github.com/azimul-kabir/actua/pull/914) for [#907](https://github.com/azimul-kabir/actua/issues/907)) |
| Cover overbudgeted (negative To Budget) | `coverOverbudgeted`: up to the category's positive balance; note | To Budget sheet "Cover From": categories with a positive balance, amount limited to it ([#914](https://github.com/azimul-kabir/actua/pull/914)); "→ Overbudgeted" note ([#915](https://github.com/azimul-kabir/actua/pull/915)) | Match |
| Copy last month | `copyPreviousMonth`: copies existing rows of visible categories (income only in tracking); categories without a row are left unchanged | copies the stored rows of visible categories (`storedBudgetAmounts`); others are left unchanged | Match (fixed by [#913](https://github.com/azimul-kabir/actua/pull/913) for [#906](https://github.com/azimul-kabir/actua/issues/906)) |
| Set to zero | `setZero`: every live category including hidden ones, income only in tracking | "Set budgets to zero" preview over every category including hidden ones, income only in tracking; writes only non-zero cells | Match (same final cells) |
| Hold / reset hold | see §4 | see §4 | see §4 |
| Rollover toggle | `budget/set-carryover` | category details → `setCategoryCarryover` | Match |
| 3/6/12-month averages (month and category), copy one category's last month, copy to year end, income "hold automatically" and its reset | `set3MonthAvg` … `copyUntilYearEnd`, `resetIncomeCarryover` | not offered (income rollover is read correctly) | **Divergence** [#910](https://github.com/azimul-kabir/actua/issues/910) |
| Templates, cleanup, goals | `goal-template.ts`, `cleanup-template.ts` | see `docs/BUDGET_AUTOMATION_PARITY.md` | – |

## Filed divergences

- [#906](https://github.com/azimul-kabir/actua/issues/906): "Copy last month's budget" zeroes categories that had no budget last month (P2). Fixed by [#913](https://github.com/azimul-kabir/actua/pull/913).
- [#907](https://github.com/azimul-kabir/actua/issues/907): To Budget "Move to Category" and "Cover From" don't cap the amount like Actual (P2). Fixed by [#914](https://github.com/azimul-kabir/actua/pull/914).
- [#908](https://github.com/azimul-kabir/actua/issues/908): Budget moves and covers don't add Actual's "Reassigned …" month note (low). Fixed by [#915](https://github.com/azimul-kabir/actua/pull/915).
- [#909](https://github.com/azimul-kabir/actua/issues/909): Budget month totals leave out hidden categories in envelope budgets; no tracking saved totals (low). Fixed by [#916](https://github.com/azimul-kabir/actua/pull/916).
- [#910](https://github.com/azimul-kabir/actua/issues/910): Remaining budget actions: averages, copy one category, copy to year end, income hold.
- [#911](https://github.com/azimul-kabir/actua/issues/911): Budget months outside Actual's budget range are counted and editable (low).

**Limitations:** the fixture runs Actual's engine through `@actual-app/api` (loot-core's
spreadsheet), not the PWA's UI. The actions in §5 were compared by reading source, not run.
