# Budget automation parity

Issue [#56](https://github.com/azimul-kabir/actua/issues/56) tracks Actua's staged port of
Actual's experimental goal-template and cleanup automation system. This document fixes the
behavioral reference before broadening mutation support.

## Upstream references

The audited references are Actual commit
[`2fc69915`](https://github.com/actualbudget/actual/commit/2fc69915c21fd61071d7913ffb7382dcf38d5857)
and Actuali commit
[`ce60837f`](https://github.com/MattFaz/actuali/commit/ce60837f1c672eb29e1e1a9a5f285c66fa282be5).
The primary implementation points are Actual's
[`goal-template.ts`](https://github.com/actualbudget/actual/blob/2fc69915c21fd61071d7913ffb7382dcf38d5857/packages/loot-core/src/server/budget/goal-template.ts),
[`category-template-context.ts`](https://github.com/actualbudget/actual/blob/2fc69915c21fd61071d7913ffb7382dcf38d5857/packages/loot-core/src/server/budget/category-template-context.ts),
and [`cleanup-template.ts`](https://github.com/actualbudget/actual/blob/2fc69915c21fd61071d7913ffb7382dcf38d5857/packages/loot-core/src/server/budget/cleanup-template.ts).
Actuali's portable orchestration is in
[`GoalTemplateEngine.swift`](https://github.com/MattFaz/actuali/blob/ce60837f1c672eb29e1e1a9a5f285c66fa282be5/Actuali/Actuali/Services/Budget/GoalTemplateEngine.swift)
and its editor model is in
[`BudgetAutomations.swift`](https://github.com/MattFaz/actuali/blob/ce60837f1c672eb29e1e1a9a5f285c66fa282be5/Actuali/Actuali/Services/Budget/BudgetAutomations.swift).

## v26.9.0 template matrix ([#668](https://github.com/azimul-kabir/actua/issues/668))

Re-audited against Actual [v26.9.0 (`59fe126f`)](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
(`CTC` = [`category-template-context.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts)). The older `2fc69915` references below are kept
for history.

**Evidence.** The [`budget-templates-parity`](../.github/workflows/budget-templates-parity.yml) workflow
([`tools/budget-templates/`](tools/budget-templates/README.md)) runs Actual's engine through
`@actual-app/api` 26.9.0 and Actua's planner on two identical synthetic budgets: apply for 2026-08,
overwrite for 2026-09 and month-end cleanup for 2026-10. It then compares every category's budgeted
amount, `goal` and `long_goal`. "Check" names the category in that budget. Cells that still differ
are listed with their issue in `tools/budget-templates/known-divergences.json`, which the check keeps
current. First clean run: [run 37292113992](https://github.com/azimul-kabir/actua/actions/runs/37292113992).
Some cells are knock-ons rather than separate divergences: remainder, percent of available funds,
and the scarce-funds September schedule all depend on how much other templates took.

| Behavior | Actual | Actua | Status | Check |
| --- | --- | --- | --- | --- |
| Simple `#template 50`, `up to N` | [`runSimple`, `checkLimit`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L568-L678) | `BudgetTarget.fromGoalDef` reads `simple` as a FIXED row (`simple = true`) with its inline limit; `suggestedBudget` returns the monthly amount, or the cap less last month's balance; the cap joins `effectiveCap`; notes without `-N` get priority 0 | Match ([#854](https://github.com/azimul-kabir/actua/issues/854)) | T Simple, T Notes stored |
| Periodic: every N days/weeks/months/years from `starting` | [`runPeriodic`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L700-L754) | `BudgetTarget.fixedSuggestedBudget` | Match (budget) | T Periodic month/week/2 months |
| By date (`by`), sibling batching | [`runBy`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L908-L988) | `BudgetTemplatePlanner.combinedByDate` | Match for a future target | T By date |
| By date, repeating or annual after the target passed | rolls forward and interpolates (`runBy`) | budgets the whole amount | **Divergence** [#856](https://github.com/azimul-kabir/actua/issues/856) | T By annual |
| Spend (`by … spend from`) | [`runSpend`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L756-L823) | by-date with `allowEarlySpending` | Match in the checked case (no earlier spending) | T Spend |
| Average of N months, with adjustment | [`runAverage`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L871-L906), [`getCategoryAverage`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/actions.ts#L388-L414) | `suggestedBudget` (`HISTORICAL`/`AVERAGE`) | Match for plain spending; **Divergence** for refunds and rounding [#860](https://github.com/azimul-kabir/actua/issues/860) | T Average, T Average refund, T Average adjusted |
| Copy from N months ago | [`runCopy`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L687-L698) | `suggestedBudget` (`COPY`) | Match | T Copy |
| Percentage of an income category / all income | [`runPercentage`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L825-L869) | `requestedAtPriority` | Match | T Percent salary, T Percent all income |
| Percentage of available funds | `runPercentage` with the priority's starting funds | `requestedAtPriority` with the same start | Same rule; the checked value is a knock-on | T Percent available |
| Percentage of previous month's income | `runPercentage` (`previous`) | 0 | **Divergence** [#859](https://github.com/azimul-kabir/actua/issues/859) | T Percent previous |
| Refill to a monthly cap | [`runRefill`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/category-template-context.ts#L680-L685) | `requestedAtPriority` | Match | T Refill |
| Refill or limit with a weekly/daily cap | cap scaled to the month (`checkLimit`) | unscaled cap for refill | **Divergence** [#858](https://github.com/azimul-kabir/actua/issues/858) | T Refill weekly |
| Limit with carryover over the cap (`hold` false/true) | releases the excess or holds it (`checkLimit`) | `releasedByLimit` | Match (budget) | T Limit release, T Limit hold |
| Remainder by weight, with a cap | [`distributeRemainder`, `runRemainder`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/goal-template.ts#L17-L36) | `distributeRemainder` | Match for the cap; the checked split is a knock-on (same cent placement) | T Remainder 1–3, T Remainder capped |
| `#goal` (long-term goal) | `runGoal` | `targetBalanceGoal` | Match | T Goal only, T Goal and fixed |
| Schedule due this month, `full` | [`runSchedule`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/schedule-template.ts#L315-L400) | `BudgetScheduleFunding.requestedBudget` | Match (budget) | T Schedule monthly, T Schedule full |
| Schedule not due this month (sinking fund) | `getSinkingContributionBreakdown` | amount ÷ months until due | **Divergence** [#857](https://github.com/azimul-kabir/actua/issues/857) | T Schedule quarterly |
| Apply skips budgeted categories; Overwrite recalculates | [`processTemplate`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/goal-template.ts#L217-L300) (`force`) | `preview(overwriteExisting)` | Match | T Prebudgeted |
| Priority order and available-funds clamp (priority 0 unclamped) | `computeTemplates`, `runTemplatesForPriority` | `preview` priority loop | Same rule; September's scarce-funds values are knock-ons, and notes default to priority 1 ([#854](https://github.com/azimul-kabir/actua/issues/854)) | September rows |
| Notes re-read before applying (`storeNoteTemplates`) | [`applyTemplate`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/goal-template.ts#L71-L91) | only when a note is edited in Actua | **Divergence** [#855](https://github.com/azimul-kabir/actua/issues/855) | T Notes only |
| Monthly `goal`/`long_goal` cells (underfunded/overfunded status) | `goal` = full request, `long_goal` only for `#goal` | target totals and `long_goal = 1` for by/schedule; none for the rest | **Divergence** [#853](https://github.com/azimul-kabir/actua/issues/853) | every template row |
| Whole-number rounding when the budget hides decimals | `removeFraction` | not applied | **Divergence** [#861](https://github.com/azimul-kabir/actua/issues/861) (not in the check) | – |
| One invalid category stops the whole run | `computeTemplates` returns errors and writes nothing | skips and names unsupported categories, applies the rest | **Intentional** | – |
| Month-end cleanup: global and group sources, weighted sinks, overspent fill | [`cleanup-template.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/cleanup-template.ts) | `CleanupTemplatePlanner` | Match (budget); `long_goal` on sources differs ([#853](https://github.com/azimul-kabir/actua/issues/853)) | C rows |
| Set budgets to zero | [`setZero`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/actions.ts#L261-L274) | `ZeroBudgetPlanner` | Match for envelope; **Divergence** for tracking income [#862](https://github.com/azimul-kabir/actua/issues/862) (not in the check) | – |

## Exact behavior to preserve

- Templates are evaluated by priority across all categories, not category-by-category to completion.
- Normal application does not replace a non-zero budget cell; overwrite explicitly does.
- Existing budgeted funds managed by a template return to the available pool before recalculation.
- Balance limits are applied before contributions, remainder templates share funds by weight, and
  available funds clamp ordinary contributions.
- Note-managed templates refresh `goal_def`; UI-managed templates keep `template_settings.source = ui`.
- Budget and goal writes from one application are committed as one synchronized mutation batch.
- A dry run is read-only and reports category effects before application.
- Cleanup sources and weighted sinks are grouped by cleanup-group identity and run at month end.
- Invalid or unknown definitions stop or skip the affected operation with an explicit explanation;
  they are never approximated as a different template type.

## Actua implementation boundary

Actua currently reads and edits these exact UI-managed projections, using Actual's own seven
automation types plus its two standalone options: fixed amount, cover schedule, save-by-date,
percentage of income, from-history (recent-month average or prior-month copy), refill-to-cap,
whatever-is-left (remainder), balance-cap (limit), and long-term goal. Balance-cap and long-term-goal
rows update Actual's monthly `goal` and `long_goal` values without requesting
budget funds. The whole-budget
action now provides a read-only preview for these supported targets, identifies unchanged rows,
shows the net budget change, and discloses categories with unsupported definitions. Confirming the
preview writes all changed budget cells in one CRDT/database batch. A stale preview is rejected if
any involved budget cell changed before confirmation. Reapplying the same plan is a no-op.

Categories containing only those supported UI-managed types can now hold and edit multiple
automations in one list. Saving replaces `goal_def` and its UI source marker together through the
existing CRDT mutation path. Categories containing any unknown type, or a notes-managed definition,
remain read-only so Actua never drops or rewrites constructs it does not understand. Whole-budget
preview now evaluates supported contributions across categories in ascending upstream priority,
returns existing template-managed budget amounts to the available pool, batches sibling save-by-date
goals, applies refill caps, and clamps positive-priority contributions to available funds. Imported
priorities are preserved when an automation is edited and saved. The preview identifies categories
whose requested contribution was limited before the user confirms the atomic write. Normal Apply
leaves non-zero budget cells unchanged; the separately labelled Overwrite action returns their funds
to the available pool and recalculates them, matching the upstream distinction. Budget amounts and
goal values from one confirmation are stale-checked and committed in one synchronized CRDT batch;
orphaned monthly goals are cleared after their definition is removed. UI-managed remainder rows
with positive integer weights are also editable. After ordinary priorities consume their funding,
the planner divides the remaining Ready to Budget amount proportionally by weight across eligible
remainder categories, preserves every cent through deterministic rounding, and includes the result
in the same preview and atomic confirmation path. Remainder rows with an embedded daily, weekly,
or monthly cap are now accepted as editable supported definitions and their per-month limit is
applied before the remainder allocation is finalized. The preview identifies every category with
such a cap. Daily limits multiply by the selected calendar month's length; weekly limits count
occurrences from the stored weekly start date that fall in the selected month. If prior-month
carryover already exceeds the cap, `hold=true` preserves it and budgets nothing, while `hold=false`
emits the negative excess release that Actual uses to return the excess to Ready to Budget.
Remainder distribution uses integer-cent quotient/remainder allocation in stable category order,
so repeated previews and applications are deterministic.

Percentage sources now support current-month `available funds`, `all income`, and exact income
category IDs/names present in the downloaded budget, editable from the automation editor's
"Income source" picker (previously limited to `available funds` in the UI, though the codec
always accepted an arbitrary source). Previous-month sources remain unsupported; their stored
definitions remain untouched and the preview names affected categories. Percentage automations
use their source value at the start of the priority and are clamped by the same available-funds
rules as other positive-priority contributions.

Cover-schedule and from-history (average) automations expose Actual's `adjustment`/`adjustmentType`
"increase"/"decrease" modifier: a signed percentage scales the computed amount, a signed fixed
amount is added to it, and the result is clamped at zero. From-history's copy mode carries no
adjustment upstream, so the editor hides it there and any stored value is dropped on encode.

Schedule-template rows have an exact, lossless UI codec for their stable `scheduleId`/`name`
reference. The repository projects active schedules into recurrence-aware funding facts, including
fixed and recurring dates, range midpoint amounts, completed/past handling, and monthly occurrence
counts. Resolved schedule rows participate in the same priority, Ready to Budget clamp, preview,
stale-check, and atomic confirmation path as other templates. Missing, malformed, completed, or
unsupported schedule references remain read-only and are disclosed instead of being treated as
zero-dollar contributions. Schedule and save-by-date templates must share one priority, matching
Actual's validation rule.

Notes-managed templates are parsed from category notes before their `goal_def` is refreshed.
Supported note directives retain `template_settings.source = notes` and remain evaluable while
the editor stays read-only. Malformed notes or notes containing any unsupported directive update
only the note text and leave the prior definition untouched.

## Slice 9: cleanup source/sink groups

Faithful reference: Actual's
[`cleanup-template.ts`](https://github.com/actualbudget/actual/blob/2fc69915c21fd61071d7913ffb7382dcf38d5857/packages/loot-core/src/server/budget/cleanup-template.ts),
[`cleanup-template-notes.ts`](https://github.com/actualbudget/actual/blob/2fc69915c21fd61071d7913ffb7382dcf38d5857/packages/loot-core/src/server/budget/cleanup-template-notes.ts),
[`cleanup-groups.ts`](https://github.com/actualbudget/actual/blob/2fc69915c21fd61071d7913ffb7382dcf38d5857/packages/loot-core/src/server/budget/cleanup-groups.ts),
and the `cleanup-template.pegjs` grammar at the same commit.

Cleanup is entirely notes-managed: `#cleanup source`, `#cleanup sink [weight]`, `#cleanup
<group> source`, `#cleanup [<group>] sink [weight]`, and a bare `#cleanup <group>` (overspend)
are parsed from every category's note into `cleanup_def` (a JSON array of `{role, groupId,
weight}` rows) and `cleanup_groups` (named pools), mirroring `storeNoteCleanups()` and
`tombstoneOrphanCleanupGroups()`. There is no UI editor for cleanup rows, matching upstream.
Group names resolve case-insensitively; a re-scan un-tombstones a matching existing group rather
than duplicating it, and un-referenced groups are tombstoned, never deleted.

Cleanup-group-scoped rows (`groupId != null`) run first, one isolated pool per group: valid
(non-negative-balance) sources sum into the pool; a group with no sinks and no overspend rows is
left untouched and reported with an explicit warning instead of silently zeroing its sources;
otherwise the pool first funds that group's overspent, non-`carryover` categories (partial fill
allowed), then splits any remainder across that group's weighted sinks. Global rows (`groupId ==
null`) return each source's leftover to the shared Ready-to-Budget pool and reset its `goal`/
`long_goal`, followed by an unconditional overspend auto-fill of every non-income, non-`carryover`
category (whether or not it participates in any cleanup group) from that growing shared pool, and
finally any remainder splits across global weighted sinks.

A whole-budget "Month-end cleanup" action previews every proposed budget and goal change plus
warnings and invalid categories before anything is written; confirming writes the batch through
the existing `applyTemplate` atomic, stale-checked budget/goal path (the same one whole-budget
templates use), so a cleanup can never partially apply or silently overwrite budget cells changed
after the preview was taken. Reapplying an already-current preview is a no-op, giving idempotent
recovery if a batch is retried. Categories whose `cleanup_def` fails to parse (or whose weight or
role is not one Actua recognizes) are disclosed by name and left completely untouched — never
approximated as a supported row.

**Deliberate deviation from upstream:** weighted sink and cleanup distribution uses deterministic
integer-cent quotient/remainder allocation (`allocateByWeight`) so every distribution sums exactly
to the pool being divided. Upstream instead rounds each sink independently with `Math.round()`,
which can drift by a cent and relies on a final live-recompute clamp against Ready to Budget to
correct it. Actua's simulation is sequential and in-memory (`assigned`/`balance`/`toBudget` deltas
against the read model) rather than upstream's per-write live SQL-sheet recomputation, which is an
intentional, behavior-equivalent simplification for the same reason: this codebase never bypasses
Actual's CRDT writers, so there is no live SQL view to recompute against mid-batch. `cleanup_def`
rows attached to unrecognized JSON shapes remain untouched by design; there is currently no
`cleanup_def` variant Actua deliberately drops beyond malformed/unparseable notes and definitions.