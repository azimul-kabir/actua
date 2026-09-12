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
saving, save-by-date, refill-to-cap, weekly spending, and recent-month average. The whole-budget
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
to the available pool and recalculates them, matching the upstream distinction.

The following upstream constructs are deliberately not executed yet: schedule funding, percentage
sources, copy/history variants beyond recent average, remainder weighting, long-term goal-only rows,
note parsing, and cleanup source/sink groups. Their
stored definitions remain untouched and the preview names affected categories. Later #56 work must
port their evaluation and validation fixtures before enabling writes or full multi-automation editing.
