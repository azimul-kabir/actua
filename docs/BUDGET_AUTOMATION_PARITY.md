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

Actua currently reads and edits these exact UI-managed projections: monthly spending, fixed monthly
saving, save-by-date, refill-to-cap, weekly spending, recent-month average, prior-month copy,
goal-only balance
targets, and current-month percentage-of-Available-Funds contributions. Goal-only rows update Actual's monthly `goal` and `long_goal` values without requesting
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
category IDs/names present in the downloaded budget. Previous-month sources remain unsupported;
their stored definitions remain untouched and the preview names affected categories. Percentage
automations use their source value at the start of the priority and are clamped by the same
available-funds rules as other positive-priority contributions.

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