# Budget rendering and LazyList performance (#323)

Part of the [performance & UI smoothness initiative](https://github.com/azimul-kabir/actua/issues/316),
building on the recomposition audit in [#322](https://github.com/azimul-kabir/actua/issues/322)
(`docs/RECOMPOSITION_AUDIT.md`), which had already memoized `BudgetScreen`'s group/category
filtering (`visibleGroups`) and the category-details recent-transactions list.

## Findings and fixes

All in `app/src/main/java/com/azimulkabir/actua/ui/budget/BudgetScreen.kt` unless noted.

1. **Missing `contentType` on the group `stickyHeader`/`itemsIndexed` calls.** The list renders
   three structurally different header/row composables per group (income, Plan-view, Table-view)
   without telling Compose which is which, so scrolling across a group/income boundary couldn't
   reuse composition slots cleanly. Added `contentType` to both, discriminated the same way the
   render branch already is (`group.isIncome` / `budgetView == "Plan"`).
2. **`group.copy(categories = visibleCategories)` allocated a new `BudgetGroup` on every header
   recomposition**, purely to hand the header composable a pre-filtered category list it only
   sums into totals. Hoisted into the existing `visibleGroups` `remember` block (from #322) as a
   `headerGroup` computed once per data/toggle change, alongside the group and its visible
   categories, instead of three inline `.copy()` calls per group per recomposition.
3. **`animateContentSize()` on the whole Table-view sticky-header `Row`** (`BudgetGroupHeader`)
   meant the icon+name portion — which never changes size — paid for an extra measure/layout
   pass alongside the totals whenever `budgeted`/`spent`/`balance` produced a different-width
   string. Scoped the modifier to just the totals/spacer sub-`Row`, the part that actually
   resizes when `showTotals` toggles.
4. **`NumberFormat.getIntegerInstance(locale)` constructed fresh on every whole-number format
   call** (`MoneyFormatter.kt`'s `formatWholeNumber`, the default/no-custom-format path) — hit at
   least once per row, often 2-3 times (assigned/spent/balance), per recomposition. Cached
   per-thread per-locale (`NumberFormat` instances aren't safe to share across threads) instead
   of allocating per call.

## Already verified fine (no changes needed)

- **Plan/Table view switching**: `budgetView` only gates which composable renders already-computed
  `visibleGroups`/`groups` (lines ~289/343/381) — no re-fetch or re-filter keyed on the view mode.
- **List item keys**: `"header-${group.name}"` and `"${group.name}-${category.name}"` are
  name-based and unique per current model shape, not index-based.
- **No nested lazy layouts**: the only nested scrollables are non-list-item containers (a filter
  row, sheet bodies), not `LazyColumn`/`LazyRow` inside list items.
- **No synchronous persistence during drag**: category-group reorder (`ManageCategoriesScreen.kt`,
  reached from Budget via "Manage Categories" → "Reorder Groups") only mutates local state during
  the gesture (`CategoryDragReorder.step`, pure/in-memory) and persists once at `onDragEnd` — this
  is the pattern PR #250 was called out for *not* following ([#316](https://github.com/azimul-kabir/actua/issues/316)'s
  stated anti-pattern), and it's already correct here.
- **Icons/vectors**: direct `Icons.Outlined.X` property access, not re-allocated per row.

## Acceptance criteria

- [x] Plan/Table switching avoids unnecessary data reloads (verified, no changes needed).
- [x] Changing one row avoids unnecessarily recomposing large portions of the list
      (`contentType` + removing the per-header `BudgetGroup` allocation).
- [x] No synchronous persistence occurs in active scroll/drag animation paths (verified).
- [ ] Budget remains smooth with a realistically large category set — needs real-device
      validation; re-run `BudgetScreenBenchmark` (from [#321](https://github.com/azimul-kabir/actua/issues/321),
      `macrobenchmark/`) on a Pixel 8 against a large synthetic budget once real baseline numbers
      exist, tracked under [#330](https://github.com/azimul-kabir/actua/issues/330).
- [ ] Expand/collapse does not produce obvious repeatable stalls — same as above; the
      `expandCollapseCategoryGroup` benchmark exists but needs a real-device run to confirm.
