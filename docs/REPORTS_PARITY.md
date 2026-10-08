# Reports parity: dashboards, widgets and custom reports

Widget-by-widget audit of the Actual Budget reports that Actua shows. Tracked in
[#671](https://github.com/azimul-kabir/actua/issues/671) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)); the original reports work was
[#230](https://github.com/azimul-kabir/actua/issues/230), and earlier per-widget fixes were
#517, #523, #525–#549 and #621–#646.

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit the other parity docs pin). `R/` =
  `packages/desktop-client/src/components/reports/`, `S/` = `R/spreadsheets/`, `W/` = `R/reports/`.
  Each link below is a permalink to that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Engines:
  `data/reports/CoreReportEngine.kt` (`CRE`), `data/reports/SavedReportEngine.kt` (`SRE`),
  `data/reports/ReportAggregator.kt` (`RA`). UI: `ui/reports/ReportsScreen.kt`.
- **Tests:** `src/test` = `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`. Unless stated, JVM report tests are in
  `src/test/.../data/reports/` and named `Class.method`.
- **Status:** **Match** = same rows and displayed values for the same budget; **Intentional** =
  an Android-only difference that changes no synced data; **Divergence** = filed as an issue;
  **Not ported** = upstream behavior Actua doesn't offer, documented in Known gaps.

Actua only *reads* reports: it never creates, edits, moves or deletes dashboard pages, widgets
or saved reports, so none of the rows below write CRDT messages.

## 1. Dashboard pages and widgets

Upstream: [`R/ReportsDashboardRouter.tsx#L14-L57`][router],
[`desktop-client/src/reports/queries.ts#L24-L56`][queries], [`R/Overview.tsx#L160-L200`][overview-layout],
[`R/Overview.tsx#L785-L937`][overview-switch], schema
[`loot-core/src/server/aql/schema/index.ts#L203-L218`][schema-dashboard].
Actua: `ActualBudgetDatabase.fetchDashboardPages` / `fetchDashboardWidgets`
(`data/budget/ActualBudgetDatabase.kt:1399`, `:1424`), `CRE.dashboards` (`:36`), `CRE.compute` (`:88`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Live pages (`tombstone = 0`), opened at the first page | `q('dashboard_pages')`, redirect to `pages[0]` | `ORDER BY rowid`, first page selected | Match |
| Widgets belong to their `dashboard_page_id` | `widgets.filter(w => w.dashboard_page_id === id)` | `AND dashboard_page_id = ?` (or `IS NULL` without pages) | Match |
| Narrow-screen order: `y`, then `x` | mobile layout sort | `ORDER BY y, x, rowid` | Match |
| `width`/`height` layout | grid layout | single column, one card per widget | **Intentional** (phone layout) |
| Unknown widget type | renders nothing | counted in an "not available in Actua yet" note, card hidden | **Intentional** |
| Experimental widgets (Budget Analysis, Balance Forecast, Formula, Sankey, Monte Carlo) need the synced `flags.*` pref | hidden when the flag is off ([`useFeatureFlag.ts`][flags]) | always shown | **Intentional.** The widget rows are synced data, so showing them never misreports; no PWA-only state is created. |
| `custom-report` widget whose saved report is deleted | "This custom report has been deleted." ([`W/CustomReportListCards.tsx#L41-L47`][missing-report]) | same message, no amounts | Match (#949) |
| Widget name: `meta.name`, else the type's label | per card | `meta.name` else `CRE.label` | Match |

Tests: `src/androidTest/.../data/budget/ActualBudgetReadModelTest.readsSyncedDashboardPagesAndWidgetOrder`,
`DashboardRepairTest`, `src/androidTest/.../data/reports/CoreReportEngineTest.unknownSyncedWidgetStaysVisibleAsUnsupportedMetadata`,
`.allActualiWidgetTypesHaveNativeKinds`, `.dashboardCustomReportWidgetUsesSavedReportNameAndGraphType`,
`.dashboardCustomReportWidgetShowsDeletedStateWhenSavedReportMissing`, `MissingCustomReportTest`,
`src/androidTest/.../ui/reports/ReportsScreenTest`.

## 2. Shared semantics

### Transaction source

Upstream reads `q('transactions')` (split children inline, parents and tombstones excluded,
category and payee resolved through `category_mapping`/`payee_mapping`). Actua:
`fetchTransactionsForReports` (`ActualBudgetDatabase.kt:1378`) over `transactionChildSelect`
(`:2115`).

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Split parents excluded, children carry their own category | `splits: 'inline'` | `isParent = 0`, children of live parents only | Match |
| Tombstoned rows and children of tombstoned parents excluded | `v_transactions_internal_alive` | same filters | Match |
| Merged category resolves to its target id | `cm.transferId AS category` | `COALESCE(cm.transferId, t.category)` (#636) | Match |
| Merged payee resolves to its target | `payee_mapping` | `COALESCE(pm.targetId, t.description)` | Match |
| Transfer = payee with `transfer_acct` | `payee.transfer_acct` | `p.transfer_acct` | Match |
| Amounts | integer cents | `Long` cents | Match |
| Cleared/reconciled/pending | never change totals | same | Match |

Tests: `ReportAggregatorTest.tombstones split parents and date range are excluded`,
`.split children land in their own categories`, `.cleared and reconciled status does not alter totals`,
`ReportAggregatorScaleTest`.

### Conditions

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `customName` conditions are ignored | `conditions.filter(c => !c.customName)` | `CRE.parseConditions` (`:962`) / `SRE.parseConditions` drop them | Match |
| `conditionsOp` `and`/`or` | `$and`/`$or` | `Rule.ConditionsOp` | Match |
| Condition evaluation | `make-filters-from-conditions` (AQL) | `RulesEngine.matches` | Match for supported operators; unsupported ones fall back to no condition (Known gaps) |
| Formula sub-queries also pass `customName` conditions | not filtered ([`useFormulaExecution.ts#L388-L425`][formula-query]) | filtered | Divergence, low impact; tracked with Formula in Known gaps |

### Dashboard time frames

Upstream: `calculateTimeRange` ([`R/reportRanges.ts#L208-L302`][time-range]); each card passes its
own default. Actua: `CRE.timeFrame` (`:974`).

| Mode / input | Actual | Actua | Status |
| --- | --- | --- | --- |
| `static` (month or day bounds) | stored bounds | stored bounds | Match |
| `lastMonth`, `lastYear`, `yearToDate`, `priorYearToDate` | calendar bounds | same | Match |
| `sliding-window` with month bounds | same width, ending this month | same | Match |
| `sliding-window` with day bounds / `start > end` | slides by days / reversed window | same | Match (#948) |
| `currentQuarter`, `previousQuarter` | quarter bounds | same | Match (#948) |
| `full` | stored start → later of this month and latest transaction | same | Match (#948) |
| No `timeFrame` or no `mode` (Net Worth, Age of Money, Sankey, Budget Analysis) | last 6 months, sliding | same | Match (#948) |
| Card ranges widen to whole months (`firstDayOfMonth`/`lastDayOfMonth`) | every card spreadsheet | `CRE.compute` | Match (#948) |
| No `timeFrame` (Summary, Calendar: this month; Cash Flow: this month; Balance Forecast: this + 11 months) | card default | same | Match |

Tests: `TimeFrameTest`, `src/androidTest/.../data/reports/CoreReportEngineTest.slidingWindowKeepsConfiguredMonthCount`,
`CalendarTest`.

### Other shared rules

- **Weekly intervals** and "This week"/"Last week" use the synced `firstDayOfWeekIdx` (default
  Sunday), as upstream's `weekFromDate` does: Match (#953). Tests: `FirstDayOfWeekTest`.
- **Hidden decimals / currency** only change display; stored amounts are untouched: Match.
- **Freshness:** reports are computed from the synced on-device database, so a budget that hasn't
  synced recently shows older numbers than the PWA (#605). This is expected local-first behavior.

## 3. Widgets

### Summary (`summary-card`)

Upstream: [`S/summary-spreadsheet.ts`][summary], [`W/SummaryCard.tsx#L50-L58`][summary-card]. Actua: `CRE.summary` (`:811`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Range: first day of start month → today (end in this month) or end of month | `startDay`/`endDay` | `effectiveStart`/`effectiveEnd` | Match |
| No transfer, off-budget or category filtering beyond `conditions` | root query | same | Match |
| `content.type = sum` | sum of amounts | same | Match |
| `avgPerTransact` | sum / count | same, 0 when empty | Match |
| `avgPerMonth`: whole months + `day / daysInMonth` of the last month | `calculatePerMonth` | same formula | Match |
| `avgPerYear`: `(days + 1) / 365.25` | `calculatePerYear` | same | Match |
| `percentage`: divisor conditions, `divisorAllTimeDateRange`, `round(x × 10000) / 100` | `calculatePercentage` | same; a zero divisor shows 0% instead of ∞ | Match (zero-divisor display differs) |
| `content` stored as an object or a JSON string | `JSON.parse(meta.content)` | both accepted | Match |

Tests: `src/androidTest/.../data/reports/CoreReportEngineTest.summaryParsesDoubleEncodedContentAndTransferCondition`, `.summaryCurrentMonthStopsAtToday`.

### Net Worth (`net-worth-card`)

Upstream: [`S/net-worth-spreadsheet.ts#L22-L260`][net-worth]. Actua: `CRE.netWorth` (`:858`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Accounts: every account (on- and off-budget, open and closed) | `accounts` prop | every transaction | Match |
| Balance at each interval end = all matching transactions through that date | starting + per-interval sums | running sum through each boundary | Match |
| Headline = last interval's net worth | `endNetWorth` | last point | Match |
| `interval` Daily/Monthly/Yearly | | same | Match |
| `interval` Weekly | weeks by `firstDayOfWeekIdx` | same | Match (#953) |
| Extra "prior period" point before the range | added unless the first transaction is in range | not added | **Intentional** (chart only; headline unaffected) |
| `mode` trend/stacked | chart style | single line | **Intentional** |
| Default range | last 6 months | same | Match (#948) |

### Cash Flow (`cash-flow-card`)

Upstream: `simpleCashFlow` ([`S/cash-flow-spreadsheet.tsx#L19-L70`][cash-flow]). Actua: `CRE.cashFlow` (`:871`), filters at `:113`.

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Exclude off-budget accounts and every transfer (`payee.transfer_acct = null`) | yes | `transferAccountId == null`, not off-budget | Match |
| End capped at today | `min(end, currentDay)` | `minOf(end, today)` | Match |
| Income = amount > 0, expenses = amount < 0 | | same | Match |
| Default range: this month, sliding | `defaultTimeFrame` | current month | Match |

Tests: `src/androidTest/.../data/reports/CoreReportEngineTest.cashFlowDropsTransfersAndOffBudgetAccounts`, `.cashFlowCurrentMonthStopsAtToday`, `.cashFlowPointsCarryContributingTransactionIdsForDrillDown`.

### Spending (`spending-card`)

Upstream: [`S/spending-spreadsheet.ts#L70-L354`][spending], [`R/spendingAverageRange.ts`][spending-avg],
`calculateSpendingReportTimeRange` ([`R/reportRanges.ts#L304-L337`][spending-range], [`W/SpendingCard.tsx#L74-L90`][spending-card]).
Actua: `CRE.spending` (`:884`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Rows: exclude income categories and off-budget accounts only; transfers and uncategorized count | `!categoryIncome && !accountOffBudget` | same | Match |
| Spent = net of debits and credits (refunds reduce it) | `cumulativeAssets + cumulativeDebts` | `-sum(amount)` | Match |
| `isLive`, `compare`, `compareTo`, `mode` resolution | `calculateSpendingReportTimeRange` | same branches | Match |
| Current month compares through today's day; days 28–31 collapse into day 28 | `todayDay` index | `currentCutoff`/`comparisonCutoff` | Match |
| `mode = single-month` | compare vs compareTo | same | Match |
| `mode = budget`: compare month's budgeted, prorated by day; category/group conditions narrow budgets, unsupported operators keep all | `getSpendingBudgetFilters` | same | Match |
| `mode = average`, `averageRange` last 3/6/12 months, YTD, all-time; invalid → 3 | `resolveSpendingAverageRange` | same | Match |

Tests: `SpendingTest`, `src/androidTest/.../data/reports/CoreReportEngineTest.spendingBudgetUsesSyncedCategoryBudgetsAndMonthToDateProration`, `.spendingSingleMonthCarriesCurrentAndComparisonTransactionIds`, `.spendingBudgetModeHasNoComparisonTransactionIds`.

### Markdown (`markdown-card`)

Upstream: [`W/MarkdownCard.tsx#L100-L151`][markdown]. Actua: `CRE.compute` (`:117`), `ReportsScreen.WidgetCard`.

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `meta.content` | rendered as GFM (`remark-gfm`, `remark-breaks`) | headings, paragraphs with line breaks, lists and task lists, quotes, code, tables, rules, emphasis, links and bare URLs (`ui/reports/MarkdownBlocks.kt`) | Match (#957); raw HTML and footnotes are shown as text |
| `meta.text_align` | applied | same | Match (#957) |

Tests: `src/test/.../ui/reports/MarkdownBlocksTest`, `src/androidTest/.../ui/reports/ReportsMarkdownWidgetTest`.

### Age of Money (`age-of-money-card`)

Upstream: [`S/age-of-money-spreadsheet.ts#L407-L573`][aom]. Actua: `CRE.ageOfMoney` (`:158`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| FIFO over all history through `min(end, today)`; on-budget accounts only | income/expense queries | `pool` | Match |
| Transfers excluded unless the counterpart is off-budget, or outside an `account` filter | `buildTransferInclusionFilter` | same; `matches` is evaluated instead of falling back | Match (except `matches`) |
| Income/expense by sign, not category | `classifyTransactions` | same | Match |
| Headline = average of the last 10 ages from the start month | `calculateAverageAge` | same | Match |
| `granularity` daily/weekly (Monday-start)/monthly chart | honored | same | Match (#955) |
| Periods before the first age | omitted | same | Match (#955) |
| Default range | last 6 months | same | Match (#948) |

Tests: `AgeOfMoneyTest`, `AgeOfMoneyGranularityTest`.

### Formula (`formula-card`)

Upstream: HyperFormula with `QUERY`/`BUDGET_QUERY` and other functions ([`hooks/useFormulaExecution.ts`][formula-query]). Actua: `CRE.formula` (`:206`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `QUERY("name")` = sum of a named query's amounts | `fetchQuerySum` | same | Match |
| Query with no `timeFrame.mode` = all time | no date filter | same | Match |
| Unknown query name | error | `0` | **Intentional** |
| `+ - * /`, parentheses, unary signs | HyperFormula | `ArithmeticParser` | Match |
| Other functions (`BUDGET_QUERY`, `QUERY_COUNT`, spreadsheet functions) | supported | "This formula uses functions Actua cannot evaluate." | **Not ported** |

Tests: `FormulaTest`.

### Custom Report (`custom-report`)

The widget renders the saved report it points to: see §4. A deleted report shows a placeholder: §1.

### Calendar (`calendar-card`)

Upstream: [`S/calendar-spreadsheet.ts#L18-L140`][calendar]. Actua: `CRE.calendar` (`:249`), range at `:127`.

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Range widened to whole months | `firstDayOfMonth`/`lastDayOfMonth` | same | Match |
| Per-day income (> 0) and expense (< 0); no transfer/off-budget filter | | same | Match |
| Week layout by `firstDayOfWeekIdx` | | same | Match (#953) |

Tests: `CalendarTest`, `src/androidTest/.../data/reports/CoreReportEngineTest.calendarPointsCarryContributingTransactionIdsForDrillDown`.

### Crossover (`crossover-card`)

Upstream: [`S/crossover-spreadsheet.ts`][crossover], [`W/Crossover.tsx#L52-L56`][crossover-default]. Actua: `CRE.crossover` (`:268`). Re-audited in #622/#632.

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `timeFrame` full / sliding-window (re-anchored to last month) / static, clamped to history | | same | Match |
| `incomeAccountIds` (default all) monthly balances; CAGR default return; `estimatedReturn` | | same | Match |
| `expenseCategoryIds` (explicit, even empty, or all non-income); hidden only with `showHiddenCategories` | | same | Match |
| Expense projection `projectionType` hampel/median/mean; `expenseAdjustmentFactor`; `safeWithdrawalRate` (4%); `expectedContribution` | | same | Match |
| Years to retire from today to the crossover month | `differenceInMonths` | same | Match |
| "Target monthly income" / "Target life savings" labels | shown | not shown | **Intentional** (display only) |

Tests: `CrossoverTest`.

### Budget Analysis (`budget-analysis-card`)

Upstream: [`S/budget-analysis-spreadsheet.ts`][budget-analysis], [`W/BudgetAnalysisCard.tsx#L46-L76`][budget-analysis-card]. Actua: `CRE.budgetAnalysis` (`:435`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Budgeted, spent and leftover read from budget-engine cells; income categories excluded | `envelope-budget-month` | `fetchBudgetMonth` | Match |
| Headline = last month's balance (budgeted + spent + carried leftover) | `intervalData.at(-1).balance` | last point's available | Match |
| `showHiddenCategories` | `isBaseCategory` (category's own flag) | same | Match (#956) |
| Category/group conditions; unsupported operator | matches nothing | same | Match (#956) |
| Default range | last 6 months | same | Match (#948) |
| `graphType`, `balanceOnly` | chart style | bar chart | **Intentional** |

Tests: `BudgetAnalysisCategoryTest`, `src/androidTest/.../data/reports/CoreReportEngineTest.budgetAnalysisScopesBudgetedAndSpentToCategoryConditionsAndTracksBalance`, `.budgetAnalysisExcludesHiddenCategoriesUnlessRequested`.

### Sankey (`sankey-card`)

Upstream: [`S/sankey-spreadsheet.ts#L173-L838`][sankey], [`W/SankeyCard.tsx#L53-L150`][sankey-card]. Actua: `CRE.sankey` (`:467`).

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| `mode = spent`: categorized rows only, netted per category and account (and payee for income), split by net sign | per-category queries | same | Match (#950) |
| Income broken down per income category | | same | Match |
| `mode = budgeted` | budget cells | always spent | **Not ported** |
| `topNcategories` "Other", `groupAccounts`, `layerFrom`/`layerTo`, `categorySort`, `showPercentages` | layout options | not read | **Not ported** |
| Default range | last 6 months | same | Match (#948) |

Tests: `SankeyTest`, `src/androidTest/.../ui/reports/ReportsChartInteractionTest.sankeyRendersACategoryPerNodeAndARemainingNodeForUnspentIncome`.

### Balance Forecast (`balance-forecast-card`)

Upstream: `loot-core/src/server/forecast/` ([`forecast-projection.ts`][forecast]), [`W/BalanceForecastCard.tsx#L56-L100`][forecast-card]. Actua: `CRE.balanceForecast` (`:506`). Re-audited in #621/#630/#631.

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Default range this month + 11; starting balance before range; day-by-day walk | | same | Match |
| `accounts` (default all); accountless schedules only without an explicit list | `includeAccountlessSchedules` | same | Match |
| Completed schedules skipped; posted occurrences not double-counted | `isScheduleOccurrencePosted` | same | Match |
| Transfer schedules project both legs; counted once | | same | Match |
| Recurrence incl. skip-weekend | schedules engine | `ScheduleRecurrence` | Match |
| `conditions` on posted and projected rows | | same | Match |
| Headline ending and low balance; scheduled-count subtitle | | same | Match |
| `source = tracking-budget` (tracking budgets) | budgeted income/expense projection | schedule projection | **Divergence** [#958](https://github.com/azimul-kabir/actua/issues/958) |

Tests: `BalanceForecastTest`, `src/androidTest/.../data/reports/CoreReportEngineTest.balanceForecast*`.

### Monte Carlo (`monte-carlo-card`)

Upstream: [`W/monte-carlo/monteCarloSimulation.ts`][monte-carlo]. Actua: `CRE.monteCarlo` (`:643`). Re-audited in #623.

| Setting / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| mulberry32 seed, Box-Muller, one shared market shock per year | | same | Match |
| `pots` (balance or `accountId`, mean, std-dev, `accessAge`), `spendingPhases`, `minimumSpending`, `contributions`, inflation mean/std-dev, `simulationCount`, `currentAge`/`targetAge` | | same | Match |
| Withdrawal `proportional` / `sequential` | | same | Match |
| Inflation realized after the year's withdrawal and growth | | same | Match |
| Success rate headline; median / 10th percentile chart | | same | Match |
| Dynamic withdrawal rules, tax bands, fees, other strategies, historical returns, Median/Conservative comparison lines | | simplified result | **Not ported** |
| No `pots` meta | upstream requires configuration | one pot from linked balances (6%/10%, 4% withdrawal) | **Intentional** |

Tests: `src/androidTest/.../data/reports/CoreReportEngineTest.monteCarlo*`.

## 4. Saved custom reports (`custom_reports`)

Upstream: [`S/custom-spreadsheet.ts`][custom], [`S/filterHiddenItems.ts`][hidden-items],
[`S/filterEmptyRows.ts`][empty-rows], [`R/ReportOptions.ts`][report-options],
[`R/getLiveRange.ts`][live-range], [`W/GetCardData.tsx#L80-L170`][card-data],
[`loot-core/src/server/reports/app.ts#L30-L80`][reports-app]. Actua: `fetchSavedReports`
(`ActualBudgetDatabase.kt:1449`), `SRE.compute` (`:48`), `RA.includes` (`:88`), `RA.groupTotals` (`:124`).
Saved reports appear inside dashboard widgets and on Actua's own "Overview" page, which also has an
Actua-only income-vs-expenses card (`SRE.incomeExpense`, `:305`) and view filters
(**Intentional**, never synced).

| Column / behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Live rows only | `tombstone = 0` | same | Match |
| `balance_type` Payment/Deposit/Net/Net Payment/Net Deposit/Budgeted (and format literals); unknown → Payment | `balanceTypeMap` | `SRE.balanceType` (`:346`) | Match |
| Net Payment/Net Deposit clamp per group and per interval; total sums clamped intervals | `custom-spreadsheet`/`recalculate` | `clampNet`, `SRE.reportTotal` | Match |
| `Budgeted` reads budget cells, income categories excluded | `fetchBudgetData` | `SRE.computeBudgeted` (`:172`) | Match |
| `group_by` Category / Group / Payee / Account / Interval | | same | Match |
| `group_by = CategoryGroup` (two-ring donut) | | treated as Category | **Not ported** |
| Uncategorized split into Uncategorized / Transfers / Off budget (one "Uncategorized & Off budget" group for Group) | synthetic items | same | Match (#951) |
| `show_uncategorized` off keeps uncategorized off-budget rows | yes | same | Match (#951) |
| Payee grouping: rows with no payee | excluded from groups and totals | same | Match (#951) |
| `show_offbudget`, `show_hidden` (category or group hidden) | | same | Match |
| `conditions` / `conditions_op` | | `RulesEngine` | Match |
| `selected_categories` | moved into `conditions` and set to `NULL` by migration `1722717601000` ([migration][selected-categories]) | still applied when non-null | Match (always `NULL` after the migration) |
| `mode` total / time; `graph_type` donut, bar, stacked bar, line/area | | same; other graph types draw ranked bars | Match / **Intentional** |
| `interval` Daily / Monthly / Yearly | | same | Match |
| `interval` Weekly | `firstDayOfWeekIdx` | same | Match (#953) |
| `date_static`, `start_date`/`end_date` | | same | Match |
| `date_range` live presets (week, month, quarter, last N, 30 days, YTD, years) and `include_current` | `getLiveRange` | `SRE.dateRange` (`:355`) | Match |
| "All time" = earliest → latest transaction; quarter, 30-day and year presets clamp their start to the earliest transaction | `getLiveRange`/`validateRange` | same | Match (#954) |
| `sort_by` (`asc`/`desc` flipped for Payment and Net Payment, `name`, `budget` = item order), `show_empty`, `trim_intervals` | `sortData`, `filterEmptyRows`, `trimIntervals` | same | Match (#952) |
| Intervals through the range end (future days of "This month") | yes | same | Match (#952) |
| `show_trend_lines` | chart overlay | not drawn | **Not ported** |
| Summary panel: total and `Math.round(total / intervals)` | `ReportSummary` | opt-in, device-local toggle (#644) | Match |
| Dashboard card shows no headline total | | same (#629) | Match |

Tests: `SavedReportEngineTest`, `SavedReportNetBalanceTest`, `SavedReportBudgetedTest`,
`SavedReportTransferTest`, `IntervalPointsTest`, `StackedIntervalPointsTest`, `SavedReportSummaryTest`,
`SavedReportDisplaySettingsTest`,
`ReportAggregatorTest`, `src/androidTest/.../data/budget/ActualBudgetReadModelTest.savedReportsReadIncludeCurrentAndTolerateOlderSchemas`,
`src/androidTest/.../ui/reports/ReportsCustomSummaryTest`, `ReportsDrillDownTest`.

## 5. PWA comparison and #605

[#605](https://github.com/azimul-kabir/actua/issues/605) ("Report show different in app") is closed.
Its causes were fixed one by one:
- The dashboard card showed a total the PWA doesn't show: #628/#629. The sum/average request became the opt-in summary: #644.
- Balance Forecast scheduled count and ending balance: #630/#633, #631/#634.
- Crossover years to retire: #632/#635.
- Monte Carlo success rate: #623/#627.
- "6 months shows 2 months": merged-category ids (#636/#638) and `include_current` (#637/#639).

None of the divergences above reopen #605's figures.

This audit compared source code at the pinned commit. Comparing each widget's numbers side by side
with the PWA on one synthetic budget has **not** been done yet. That acceptance criterion of #671
stays open.

## Known gaps

- Condition operators the rules engine doesn't support fall back to no condition, unlike upstream's
  AQL filters.
- Formula cards evaluate only arithmetic and `QUERY()`.
- Monte Carlo is a simplified port (see above). Sankey budgeted mode and layout options, the
  CategoryGroup donut and trend lines are not ported.
- Fixtures: the report tests use synthetic budgets; no fixture is generated by upstream's
  spreadsheets yet.

[router]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/ReportsDashboardRouter.tsx#L14-L57
[queries]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/reports/queries.ts#L24-L56
[overview-layout]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/Overview.tsx#L160-L200
[overview-switch]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/Overview.tsx#L785-L937
[schema-dashboard]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/aql/schema/index.ts#L159-L218
[flags]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/hooks/useFeatureFlag.ts
[missing-report]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/CustomReportListCards.tsx#L41-L47
[formula-query]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/hooks/useFormulaExecution.ts#L388-L436
[time-range]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reportRanges.ts#L208-L302
[summary]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/summary-spreadsheet.ts
[summary-card]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/SummaryCard.tsx#L50-L58
[net-worth]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/net-worth-spreadsheet.ts#L22-L260
[cash-flow]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/cash-flow-spreadsheet.tsx#L19-L70
[spending]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/spending-spreadsheet.ts#L70-L354
[spending-avg]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spendingAverageRange.ts
[spending-range]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reportRanges.ts#L304-L337
[spending-card]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/SpendingCard.tsx#L74-L90
[markdown]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/MarkdownCard.tsx#L100-L151
[aom]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/age-of-money-spreadsheet.ts#L407-L573
[calendar]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/calendar-spreadsheet.ts#L18-L140
[crossover]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/crossover-spreadsheet.ts
[crossover-default]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/Crossover.tsx#L52-L56
[budget-analysis]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/budget-analysis-spreadsheet.ts
[budget-analysis-card]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/BudgetAnalysisCard.tsx#L46-L76
[sankey]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/sankey-spreadsheet.ts#L173-L838
[sankey-card]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/SankeyCard.tsx#L53-L150
[forecast]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/forecast/forecast-projection.ts
[forecast-card]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/BalanceForecastCard.tsx#L56-L100
[monte-carlo]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/monte-carlo/monteCarloSimulation.ts
[custom]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/custom-spreadsheet.ts
[hidden-items]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/filterHiddenItems.ts
[empty-rows]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/spreadsheets/filterEmptyRows.ts
[report-options]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/ReportOptions.ts#L300-L440
[live-range]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/getLiveRange.ts
[card-data]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/reports/reports/GetCardData.tsx#L80-L170
[reports-app]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/reports/app.ts#L30-L80
[selected-categories]: https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/migrations/1722717601000_reports_move_selected_categories.js
