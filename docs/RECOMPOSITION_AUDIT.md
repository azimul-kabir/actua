# Compose recomposition and state stability audit (#322)

Part of the [performance & UI smoothness initiative](https://github.com/azimul-kabir/actua/issues/316),
following the baseline instrumentation in [#321](https://github.com/azimul-kabir/actua/issues/321).

## Method

Per #322's instruction to measure rather than apply `remember`/`derivedStateOf`/stability
annotations mechanically, this audit used the Compose compiler's own stability/skippability
report instead of guessing. `app/build.gradle.kts` now configures:

```kotlin
composeCompiler {
    val composeMetricsDir = layout.buildDirectory.dir("compose_metrics")
    metricsDestination = composeMetricsDir
    reportsDestination = composeMetricsDir
}
```

Regenerate with `./gradlew :app:compileDebugKotlin`; output lands in
`app/build/compose_metrics/` (gitignored, not checked in):
- `app-classes.txt` — per-class stability inference (which fields make a class "unstable" to
  the compiler, and therefore unsafe to skip recomposition on).
- `app-composables.txt` / `.csv` — per-composable restartability/skippability and any unstable
  parameters.

## Findings

**Type stability is already good.** All 181 restartable composables in the app are marked
skippable, model/UI classes under `com.azimulkabir.actua.model` and `com.azimulkabir.actua.ui`
are stable, and only two composables have any unstable parameter at all (both `IntRange`/`Any`
params on a small color-memoization helper — expected and harmless). So the "unstable
parameters" and "stable/immutable UI models" items from #322's checklist were already
satisfied; no stability annotations were needed.

**The real cost was structural**: non-trivial filtering/sorting/grouping computed directly in
composable bodies (and in one case inside a `LazyColumn` content block) instead of behind
`remember`, so it re-ran on every recomposition of the enclosing screen rather than only when
the underlying data changed. Fixed, in #322's stated priority order:

| # | Location | Problem | Re-ran on |
| - | -------- | ------- | --------- |
| 1 | `TransactionsScreen.kt` (`visible`) | Filtering the whole transaction list (incl. a substring search across payee/category/account/notes/splits) | Every recomposition of the screen — every keystroke in search, every row selection tap |
| 2 | `TransactionsScreen.kt` (`groupedByDate`) | Re-grouping the visible list by date inside the `LazyColumn` content lambda | Same as above |
| 3 | `BudgetScreen.kt` (`visibleGroups`) | Filtering every group/category in the budget | Every recomposition of BudgetScreen (opening any sheet) |
| 4 | `AccountsScreen.kt` (`accountSections`) | Three filter passes over the account list | Every recomposition of AccountsScreen (opening the overflow menu, selecting an account) |
| 5 | `AccountsScreen.kt` (`creditCardByAccountId`) | `O(accounts × creditCards)` linear scan per row | Every recomposition of every visible row |
| 6 | `AddTransactionScreen.kt` picker dialog (`transferOptions`, `grouped`) | Sorting/grouping the full option list even when the results aren't shown (`query` non-blank) | Every keystroke while searching |
| 7 | `AppNavigation.kt` (Add/Edit Transaction destination) | Four filter/map/associate passes over `accounts`/`payeeNames` | Every keystroke/amount-entry change while the editor is open |
| 8 | `BudgetScreen.kt` (`recentCategoryTransactions`) | Filter + sort of the whole transaction list to keep the top 3 | Every recomposition of BudgetScreen while the category-details sheet is open |
| 9 | `AccountsScreen.kt` (`AccountsSummary`) | Full transaction-list scan + `AccountMonthlySummaryCalculator`, even when the summary is hidden | Every recomposition of the Accounts list header |

All nine are now behind `remember` (or, for #9, skipped entirely when not shown), keyed on the
inputs that actually change the result. Two smaller instances of the same pattern
(`AppNavigation.kt`'s Import Transactions and Manage/Display screens) were left as-is: those are
low-frequency secondary flows (#322's priority tier 6) filtering a small, rarely-changing
account list — not worth the `remember` overhead.

**Not found**: no missing `key = {...}` on any `LazyColumn`/`LazyRow` `items(...)` call in
Budget/Transactions/Accounts/AddTransaction — all already have stable per-item keys. No
`derivedStateOf` gaps (no scroll-position-derived state read by a child composable) in these
screens.

**Out of scope for this slice**: `AppNavigation.kt` itself remains a single ~2900-line
composable holding nearly all app state (see #316's own callout). Splitting it into
smaller, independently-recomposable composables is a larger structural change than "recomposition
and state stability" tuning — the fixes above remove the expensive *work* that was happening
inside it on every recomposition, but the god-composable shape itself is better addressed
alongside navigation/state-retention work in [#327](https://github.com/azimul-kabir/actua/issues/327)
or as its own follow-up, where it can be validated against the #321 benchmarks without also
changing the mutation architecture covered by [#324](https://github.com/azimul-kabir/actua/issues/324)/[#325](https://github.com/azimul-kabir/actua/issues/325).

## Acceptance criteria

- [x] Major recomposition hotspots are documented (this file).
- [x] Frequently rendered rows receive stable/minimal state where practical (`creditCardByAccountId` map instead of a per-row linear scan).
- [x] Expensive calculations are removed from hot composition paths (findings 1–9 above).
- [ ] A local item change does not trigger avoidable screen-wide recomposition — the god-composable
      structure of `AppNavigation.kt` means this is only partially addressed; see "Out of scope" above.
