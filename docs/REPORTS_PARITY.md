# Reports parity with Actual Budget (issue #230)

Reference: Actual `packages/loot-core/src/server/reports` and
`packages/desktop-client/src/components/reports` (custom report spreadsheets
`custom-spreadsheet.ts`, `spending-spreadsheet.ts`, `cash-flow-spreadsheet.ts`,
`net-worth-spreadsheet.ts`). Actua reuses one calculation layer,
`data/reports/ReportAggregator.kt`, instead of per-report interpretations.

## Upstream report families

| Upstream report | Chart | Actua status |
| --- | --- | --- |
| Custom report (category/group/payee/account/period grouping, balance types) | bar, line, area, donut | Slice 2 (donut, category/group) |
| Cash flow / Income vs expenses | stacked bar + net line | Slice 3 |
| Net worth | line/area over time | Slice 4 |
| Spending (month vs compare month/average/budget) | line | dashboard widget exists |
| Summary, Calendar, Crossover, Age of money, Budget analysis, Sankey, Forecast, Formula, Markdown | cards | dashboard widgets exist (`CoreReportEngine`) |

## Shared semantics (implemented in `ReportAggregator`)

- **Balance type** mirrors upstream `balanceType`: `DEBTS` (amount < 0 only, upstream
  default "Payment"), `ASSETS` (amount > 0), `NET_ASSETS` / `NET_DEBTS` (signed sum, so
  refunds reduce spending).
- **Dates** are inclusive YYYYMMDD integers.
- **Deleted data**: tombstoned rows are ignored. Split parents (`isParent`) are ignored;
  split children carry their own category, so splits aggregate per category and never
  double count.
- **Transfers**: a transfer between two on-budget (or two off-budget) accounts is excluded.
  A transfer that crosses the budget boundary counts as uncategorized spending/income
  (money leaves/enters the budget). Income/expense is decided by category, never by sign
  alone (`isIncome` categories vs. others), and by balance type for signed direction.
- **Off-budget**: accounts excluded unless `showOffBudget`.
- **Hidden**: hidden categories or groups excluded unless `showHiddenCategories`.
- **Uncategorized**: included unless `showUncategorized` is false or a category/group
  filter is active.
- **Status**: cleared/reconciled/pending never change totals.
- **Money**: `Long` cents only.
- **Drill-down**: each group total carries the ids of its contributing transactions, and
  totals equal the sum of those rows by construction (tested).

## Income vs expenses

The "Overview" page opens with an income-vs-expenses card (default: last 12 months, monthly).
Income is every transaction in an income category; expenses are every other in-scope
transaction (uncategorized included), so refunds net against their own side and sign alone
never decides. Transfers within the same side of the budget are excluded, off-budget accounts
are excluded, and Income/Expenses rows drill down to the contributing transactions.

## Known gaps

- Custom conditions on saved reports use the rules engine, not upstream's query builder;
  unsupported operators fall back to no condition.
- Uncategorized positive amounts count against expenses (upstream may treat them as income).

Fixtures: `app/src/test/.../data/reports/ReportAggregatorTest.kt`.

## Scope decision

Actua only *displays* reports that exist in the Actual budget (dashboard cards and saved
`custom_reports` rows); it does not create or edit them. Saved reports appear on a
"Saved reports" page, evaluated by `SavedReportEngine` on top of `ReportAggregator`, and
render as donut (`DonutGraph`), line/area, or ranked bars (other graph types).
