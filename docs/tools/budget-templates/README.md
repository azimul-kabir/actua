# Budget-template check

The numeric comparison for [#668](https://github.com/azimul-kabir/actua/issues/668): run Actual's
goal-template and cleanup engine and Actua's on the same synthetic budget, then compare the
budgeted amounts and goals. The
[`budget-templates-parity`](../../../.github/workflows/budget-templates-parity.yml) workflow runs it on
GitHub:

1. Starts `actualbudget/actual-server:26.9.0` (pinned by digest) with a throwaway password.
2. `upstream.mjs` creates two identical budgets, **Templates upstream** and **Templates actua**,
   through `@actual-app/api@26.9.0` (`seed` in `common.mjs`), and a second pair, **Whole units
   upstream** and **Whole units actua**, with the synced `hideFraction` preference on. Each category in the **Templates** group
   exercises one template behavior, stored as UI-managed `goal_def` through loot-core's own
   `budget/set-category-automations`, or as a category note. The **Cleanup** group holds
   `#cleanup` notes. The seed adds income, earlier months' spending and budgets, and three
   schedules. On **Templates upstream** it then runs Actual's engine through the handlers the PWA
   calls: `budget/apply-goal-template` for 2026-08, `budget/overwrite-goal-template` for 2026-09 and
   `budget/cleanup-goal-template` for 2026-10.
3. `BudgetTemplateParityTest` (an instrumentation test, skipped unless the workflow passes
   `templatesServerUrl`) downloads each **actua** budget on an API 35 emulator and runs the same three
   steps the way the Budget screen does: `BudgetTemplatePlanner.preview` over
   `ActuaRepository.budgetGroups`, `budgetOverview` and `budgetScheduleFunding`, confirmed with
   `applyBudgetTemplate`; then `refreshCleanupDefinitions`, `previewCleanup` and `applyCleanup`. Then
   it syncs.
4. `compare.mjs` downloads every budget into a fresh loot-core client and compares, per category and
   month (the whole-units pair's categories are reported as "Name (whole units)"), the budgeted amount and the `goal`/`long_goal` cells. A difference fails the job unless
   `known-divergences.json` lists it with its issue; a listed difference that no longer occurs also
   fails, so the list stays current. The report goes to the job summary and the
   `budget-templates-parity` artifact.

Later months build on earlier ones (September's carryover comes from August), so a divergence in
August can show up again in September.

## Limits

- Only the envelope budget type is checked.
- The steps run on past months, so they don't cover Actual's rule that an average for a future month
  counts back from the current month.
- One budget runs every scenario together, as a user's budget would: priorities and the available
  funds clamp interact across categories.
