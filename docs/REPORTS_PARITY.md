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
- **Transfers**: no hard-coded exclusion, matching upstream custom reports. A transfer has
  no category of its own, so it follows the same `showUncategorized`/category-filter toggles
  as any other uncategorized row, and lands in a synthetic "Transfers" bucket rather than
  "Uncategorized" when grouping by category or category group. Income/expense is decided by
  category, never by sign alone (`isIncome` categories vs. others), and by balance type for
  signed direction. Income vs expenses (below) is the exception: it excludes same-budget-side
  transfers outright, matching upstream's cash-flow spreadsheet.
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

## Monte Carlo

Reference: `desktop-client/src/components/reports/reports/monte-carlo/monteCarloSimulation.ts`
(`runMonteCarloSimulation`). `CoreReportEngine.monteCarlo` runs an actual stochastic
drawdown simulation against the widget's `pots`/`spendingPhases`/`currentAge`/`targetAge`/
`simulationCount` meta - normal-distributed yearly returns per pot (fixed-seed mulberry32
PRNG, same algorithm as upstream, so headline numbers are stable across recompositions),
proportional or sequential withdrawal, and optional inflation and contributions - and
reports the headline as a success-rate percentage against the target age plus a
median/10th-percentile ending-balance chart, matching the PWA's presentation. It is not a
full port: upstream's dynamic withdrawal rules (guardrails/ratcheting/floor-ceiling),
progressive tax bands, pot fees, the surplus-pot/target-mix/best-performer strategies, and
historical-return replay models are not simulated, so a widget configured with those
settings gets the simplified (proportional, no tax/fees) result instead. A widget with no
`pots` meta (a legacy dashboard predating this widget's full configuration) falls back to a
single pot sized from the report's linked account balances at a 6%/10% mean/volatility and
a 4%-of-balance annual withdrawal, so it still renders a real simulation instead of an
unconfigured error.

## Known gaps

- Custom conditions on saved reports use the rules engine, not upstream's query builder;
  unsupported operators fall back to no condition.
- Uncategorized positive amounts count against expenses (upstream may treat them as income).
- Monte Carlo does not simulate upstream's dynamic withdrawal rules, tax bands, fees, or
  historical-return models (see above).

Fixtures: `app/src/test/.../data/reports/ReportAggregatorTest.kt`. A dedicated
`ReportAggregatorScaleTest` reconciles a synthetic 200k-transaction ledger and asserts
aggregation stays a single linear pass (timing-bounded), guarding the "large real-world
budgets" acceptance criterion against an accidental quadratic regression. Compose UI
coverage (`app/src/androidTest/.../ui/reports/`) covers loading/empty states, saved-dashboard
navigation/state-restoration, and the drill-down wiring (tapping a report segment/category
opens the transactions behind it via the shared `TransactionRow`/`loadTransactions` path).

## Scope decision

Actua only *displays* reports that exist in the Actual budget (dashboard cards and saved
`custom_reports` rows); it does not create or edit them. Saved reports appear on a
"Saved reports" page, evaluated by `SavedReportEngine` on top of `ReportAggregator`, and
render as donut (`DonutGraph`), line/area, a real per-category stacked bar chart
(`StackedBarGraph`), or ranked bars (other graph types).
