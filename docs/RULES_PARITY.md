# Rules parity

Issue [#282](https://github.com/azimul-kabir/actua/issues/282) tracks a 1:1 parity pass on the
**Rules** engine against Actual Budget upstream, the same way
[docs/BUDGET_AUTOMATION_PARITY.md](BUDGET_AUTOMATION_PARITY.md) did for Budget Automation
(issue [#56](https://github.com/azimul-kabir/actua/issues/56)) and
[docs/SCHEDULED_TRANSACTIONS_PARITY.md](SCHEDULED_TRANSACTIONS_PARITY.md) did for Scheduled
Transactions (issue [#279](https://github.com/azimul-kabir/actua/issues/279)). Rules are central to
Actua: they drive transaction categorization, are shared by Scheduled Transactions (schedule-owned
condition extraction/merge, see `ScheduleConditions.kt`), and feed report filters
(`CoreReportEngine.kt`).

## Upstream references

The audited reference is Actual Budget
[v26.9.0](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
(commit `59fe126f637d858c061e1eeedbef5436c8f2225a`), the same commit pinned by
`docs/ACTUAL_26_9_COMPATIBILITY.md` and `docs/SCHEDULED_TRANSACTIONS_PARITY.md`. The primary
implementation points are
[`packages/loot-core/src/shared/rules.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/rules.ts)
(field/op tables, `getApproxNumberThreshold`), the `packages/loot-core/src/server/rules/` module —
[`condition.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/rules/condition.ts)
(condition evaluation),
[`action.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/rules/action.ts)
(action execution, including split/formula/handlebars actions),
[`rule.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/rules/rule.ts)
(`Rule`, `execActions`, split-transaction action orchestration), and
[`rule-utils.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/rules/rule-utils.ts)
(`rankRules`, `OP_SCORES`, `migrateIds`, `iterateIds`) — and
[`packages/loot-core/src/server/transactions/transaction-rules.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/transactions/transaction-rules.ts)
(`runRules`: the per-transaction orchestration that ties rule ranking, schedule bypass/exclusion,
category-group refresh, and payee-name resolution together). Actuali (MattFaz/actuali) commit
[`ce60837f`](https://github.com/MattFaz/actuali/commit/ce60837f1c672eb29e1e1a9a5f285c66fa282be5)
is the portable product/behavior reference per `AGENTS.md`; its
[`RulesEngine.swift`](https://github.com/MattFaz/actuali/blob/ce60837f1c672eb29e1e1a9a5f285c66fa282be5/Actuali/Actuali/Services/Rules/RulesEngine.swift)
is itself a client-side reimplementation of `condition.ts`/`action.ts` (a `TransactionBag` mirroring
a plain transaction row) and is the direct model Actua's `RulesEngine.kt` was ported from.

## Exact behavior to preserve

**Fields, types and operators (`shared/rules.ts`):** each condition/action field has a fixed type
(`id`, `string`, `number`, `date`, `boolean`) that determines its valid operator set —
`is`/`isapprox`/`gt`/`gte`/`lt`/`lte` for dates; `is`/`contains`/`matches`/`oneOf`/`isNot`/
`doesNotContain`/`notOneOf`/`onBudget`/`offBudget` for id fields (`onBudget`/`offBudget` disallowed
on `payee`, `category`, `category_group`); `is`/`contains`/`matches`/`oneOf`/`isNot`/
`doesNotContain`/`notOneOf`/`hasTags`/`hasAnyTag` for strings (`oneOf`/`notOneOf` disallowed on
`notes`; `hasTags`/`hasAnyTag` disallowed on `imported_payee`); `is`/`isapprox`/`isbetween`/`gt`/
`gte`/`lt`/`lte` for numbers; `is` only for booleans. `amount-inflow`/`amount-outflow` are virtual
fields that deserialize to the `amount` field plus an `inflow`/`outflow` option.
`getApproxNumberThreshold(n) = round(abs(n) * 0.075)`.

**Condition evaluation (`condition.ts`):** string comparisons are case-insensitive (both sides
lowercased). `amount` conditions with an `outflow` option reject positive amounts and negate the
field value before comparing; `inflow` rejects negative amounts. `isapprox` on a number uses the
7.5% threshold; on a date it matches a plain date within ±2 days, or — for a *recurring* date value
(an `RSchedule` built from a stored recurrence config, the same shape schedules use) —
`schedule.occursBetween(date-2, date+2)`. Non-approximate `is` on a date dispatches on the stored
value's granularity: an exact `YYYY-MM-DD` compares the full date, `YYYY-MM` compares only
year+month, `YYYY` compares only the year, and a recurring value uses `schedule.occursOn(date)`
(exact occurrence, no tolerance). `gt`/`gte`/`lt`/`lte` on dates only accept exact-date values (not
month/year/recurring) and compare via day math. `hasTags`/`hasAnyTag` extract `#tag` tokens from the
condition value and test each with a `(?<!#)tag([\s#]|$)` boundary regex — `hasTags` requires every
extracted tag to match, `hasAnyTag` requires at least one. `matches` compiles the value as a raw
(non-lowercased on the pattern, case-sensitive as written) regex and never throws — an invalid
pattern is logged and evaluates to `false`. `category`/`category_group` `is`/`isNot` against a null
value are expanded to extra implicit conditions: `is <field> null` also requires
`transfer == false AND parent == false` (a transfer or split-parent transaction never satisfies
"category is (none)"); `isNot <field> null` also requires `parent == false` (a split-parent
transaction never satisfies "category is not (none)", but a transfer does).

**Action execution (`action.ts`, `rule.ts`):** `set` writes the target field's value onto the
transaction, coercing per field type; setting `payee_name` additionally sets `payee` to the sentinel
`'new'` so payee resolution creates a payee row with that name. `set` also supports two advanced,
mutually-exclusive value sources instead of a literal: a Handlebars `options.template` (compiled with
the in-progress transaction plus `today`, string result coerced per field type) or a HyperFormula
`options.formula` (must start with `=`; the transaction's own current fields — including a prefetched
account/category running-balance context — are exposed as named cells; the numeric result is rounded
to cents; a non-coercible result is recorded on the transaction's `_ruleErrors` rather than applied).
`prepend-notes`/`append-notes` concatenate onto the existing `notes` value (no-op onto an empty
value, i.e. no leading/trailing separator is added, and no separator is inserted between the
existing text and the added text — the action's own literal value must already contain any needed
whitespace/newline). `link-schedule` sets the `schedule` field. `delete-transaction` sets
`tombstone = 1`. `set-split-amount` (`options.method` of `fixed-amount`, `fixed-percent`, or
`remainder`) and any `set` action carrying `options.splitIndex` operate on split-transaction actions:
`execActions` partitions a rule's actions into non-split (`splitIndex` unset) and split
(`splitIndex` set) groups, applies the non-split actions to the parent first, and — only if any
action references a `splitIndex` (i.e. `totalSplitCount > 1`) and the transaction is not itself a
split child — converts the transaction into split children (auto-growing the split list as needed),
runs non-amount split actions, resolves `fixed-percent` splits against the remainder left after fixed
amounts, and distributes `remainder` splits evenly across each other with the last remainder split
absorbing any leftover cent from rounding.

**Ranking (`rule-utils.ts`):** each condition contributes a fixed score by operator
(`is`/`isNot` 10, `oneOf`/`notOneOf` 9, `isapprox`/`isbetween` 5, `gt`/`gte`/`lt`/`lte` 1,
everything else 0); a rule's total score is doubled if *every* condition's operator is one of
`is`/`isNot`/`isapprox`/`oneOf`/`notOneOf`. Rules are bucketed by `stage` (`pre` → normal
(`null`/unset) → `post`) and, *within* each stage, sorted ascending by score with ties broken by
rule id (a stable, order-independent sort, not "most specific first" — a low-specificity rule in a
stage runs *before* a more specific one in the same stage, and pre-stage rules always run before
every normal/post rule regardless of score).

**Per-transaction orchestration (`transaction-rules.ts: runRules`):** rules are looked up via
first-character/payee indexes (a performance optimization, not a behavior difference) then ranked.
If the transaction being processed is linked to a schedule (`trans.schedule != null`), the schedule's
own rule (looked up via the schedule → rule id mapping) *bypasses condition evaluation entirely* —
its actions always run — while every *other* rule that is linked to any schedule is skipped
entirely; ordinary (non-schedule-linked) rules still run normally, condition-checked as usual. When
the transaction has no linked schedule, every applicable rule (schedule-linked or not) is
condition-checked and applied normally. After each rule application, the payee name is re-resolved
(a pending `payee_name` set becomes a real payee row) and, if the category changed, the category's
current group is tracked so a later notes/formula action referencing category-group context stays in
sync mid-run. `shouldApplyRuleChange` (desktop-client `components/transactions/table/utils.ts`, not
`transaction-rules.ts` itself) decides whether a rule-set field should overwrite a value the user
already typed in the entry form: an empty/zero/false current value is always overwritten; a
non-notes field with any existing value is never overwritten; `notes` is overwritten only if the
new value doesn't already contain the current value as a prepended/appended substring (i.e. the
guard treats a rule's own prior prepend/append as already-applied and doesn't reapply it).

**How rules interact with Scheduled Transactions:** a schedule owns exactly one rule; that rule's
condition list holds up to four recognized slots (payee, account, amount, date) that the scheduler
edits in place, and any other conditions/actions on that rule are preserved verbatim. The schedule's
`date` condition is written as `isapprox` with either an exact date string or a recurring config
(the same shape `runRules`'s recurring-date matching consumes). Manual/automatic posting builds the
transaction directly from the schedule's own resolved fields (not by relying on the rule's condition
match), but `runRules`'s per-transaction bypass above still governs which rule actions apply if/when
a posted or manually-entered transaction is run back through the rules engine.

**How rules interact with Budget Automation:** budget automation reads/writes `goal_def` and
`cleanup_def` category-level JSON, entirely separate from the rules table; the only overlap is that
schedule-template budget rows read amount/date data through the same schedule → rule projection
described above (see `docs/BUDGET_AUTOMATION_PARITY.md`'s schedule-template section).

**How rules interact with imported vs. manually entered transactions:** upstream applies the same
`runRules` path to both a bank-import-created transaction and a manually entered one; the only
difference is which fields are already populated (e.g. `imported_payee` on an import) before rules
run.

**Rule creation/editing UX:** upstream's desktop/web rule editor supports condition/action builders,
"stage" selection, live matching-transaction counts and a list of currently-matching transactions
(`conditionsToAQL`), and a bulk "apply rule now" action that re-runs a rule (or all rules) against
already-existing transactions.

## Actua implementation boundary

Actua's rules engine (`app/src/main/java/com/azimulkabir/actua/data/rules/`) is a from-scratch
Kotlin port following the same architecture as Actuali's `RulesEngine.swift` (a `Bag`/
`TransactionBag` wrapping one transaction's mutable field values), not a line-for-line port of
upstream's class-based `Condition`/`Action`/`Rule`.

- **Schema** (`RuleModels.kt`): `RuleSchema` reproduces upstream's field type table
  (`RuleFieldType.ID/STRING/NUMBER/DATE/BOOLEAN`) and `validOps` reproduces the exact allowed
  operator set per field, including the `onBudget`/`offBudget` restriction to `account` and the
  `oneOf`/`notOneOf` exclusion on `notes`. `Rule.conditionsJson`/`actionsJson` round-trip the exact
  stored field-name aliasing upstream's public API applies internally (`acct` ↔ `account`,
  `description` ↔ `payee`, `financial_id` ↔ `imported_id`, `imported_description` ↔
  `imported_payee`, `transferred_id` ↔ `transfer_id`, `isParent`/`isChild` ↔ `is_parent`/`is_child`).
- **Condition evaluation** (`RulesEngine.evaluate`/`evaluateNumber`/`evaluateText`,
  `RuleDateMatcher`): case-insensitive string comparison, the 7.5% approximate-amount threshold,
  `inflow`/`outflow` amount-sign filtering, `hasTags`/`hasAnyTag` boundary-regex tag matching (ported
  into `TagFilter`, matching the `(?<!#)tag([\s#]|$)` pattern), a non-throwing `matches` regex (an
  invalid pattern evaluates to `false` rather than crashing), and the "category is/isNot (none)"
  transfer/parent special case for the `is` operator (`evaluateText`'s `"is"` branch: empty target
  on the `category` field also requires the transaction be neither a transfer nor a split parent,
  matching upstream's implicit `AND transfer=false AND parent=false` expansion) are all implemented.
  `RuleDateMatcher` implements exact/month/year granularity dispatch for `is` (by counting digits
  after stripping separators) and the ±2-day window for `isapprox` on plain dates, matching
  upstream's date/month/year `parseDateString` branches and its date `isapprox`/`gt`/`gte`/`lt`/`lte`
  comparisons.
- **Action execution** (`RulesEngine.apply`, the `Bag` class): `set` (writing the coerced value and,
  for `payee_name`, clearing the resolved `payee` id so a new payee is created from the pending
  name), `prepend-notes`/`append-notes`, `link-schedule`, and `delete-transaction` are implemented
  exactly as upstream. `set` actions carrying `options.template`, `options.formula`, or a positive
  `options.splitIndex` are recognized and deliberately left unapplied (the stored value is preserved
  verbatim rather than approximated), matching the documented "advanced split, formula, and template
  rule actions" boundary in `BACKEND_PARITY.md`.
- **Ranking** (`RuleRanker.kt`): `score` reproduces `OP_SCORES` exactly, including the "every
  condition is `is`/`isNot`/`isapprox`/`oneOf`/`notOneOf`" doubling rule, and `rank` buckets by stage
  (pre → default → post) then sorts ascending by score with an id tiebreak, matching `rankRules`
  exactly, field for field and tiebreak for tiebreak.
- **Payee-name resolution** (`ActualTransactionWriter.createTransaction`): a `set payee_name` action's
  pending name is resolved to a real (or newly created) payee id after rule application, matching
  upstream's `resolvePayeeNameForRules` step.
- **Manual-entry precedence** (`RuleChangeGuard.kt`): `shouldApplyRuleChange` is a direct, field-for-
  field and case-for-case port of the desktop client's guard, including the notes
  prepend/append-already-applied detection, and gates whether a rule-derived value overwrites a
  value the user already typed in the transaction entry form (`ActuaRepository.saveTransaction`'s
  `applyRules = !transaction.rulesApplied` and `ActualTransactionWriter.createTransaction`'s
  `preserveCategory` parameter).
- **Rule application on write** (`ActualTransactionWriter.createTransaction`): rules run for every
  new, non-transfer-leg transaction unless explicitly suppressed (e.g. reconciliation adjustments,
  transfer legs, or a draft the UI already ran through `previewRules`), matching upstream's uniform
  application to both manually entered and schedule-posted transactions (Actua has no bank-feed
  import path yet — see Deliberate deviations).
- **Report filter reuse** (`CoreReportEngine.kt`): custom report filters are expressed as the same
  `Rule.Condition`/`ConditionsOp` shapes and evaluated with `RulesEngine.matches`, matching upstream's
  reuse of rule condition syntax for report filtering.
- **Schedule-owned rule editing** (`ScheduleConditions.kt`, cross-referenced in
  `docs/SCHEDULED_TRANSACTIONS_PARITY.md`): the four recognized condition slots
  (payee/account/amount/date) are extracted, built and merged exactly as upstream's
  `extractScheduleConds`/`updateConditions`, preserving any other conditions/actions on the rule
  verbatim, and keeping a plain `set amount` action in sync with the amount condition.
- **UI** (`RulesScreen.kt`): list/search, stage selection, condition/action builders (including
  multi-select for `oneOf`/`notOneOf`, the AND/OR match-mode toggle, and disclosure of schedule-owned
  rules), and Actual-compatible CRDT create/update/delete are implemented for the supported
  condition/action schema described above.

## Deliberate deviations and known gaps

- **No `runRules` schedule bypass/exclusion.** Upstream's `runRules` special-cases a transaction
  linked to a schedule (`trans.schedule != null`): that schedule's own rule always runs (bypassing
  its own condition check entirely) and every rule linked to any *other* schedule is skipped, so a
  posted/edited schedule transaction can never accidentally pick up another schedule's rule actions
  by incidentally matching its conditions, and the owning schedule's rule always applies even when
  the actual posting date falls outside what its (possibly recurring) date condition would otherwise
  match. `RulesEngine.apply` has no equivalent: it condition-checks every rule uniformly regardless
  of whether the transaction being processed is linked to a schedule, so (a) a schedule-owned rule
  whose date condition is a recurring config never matches through the generic date-condition path
  (see the next gap) and its non-schedule-slot actions are not guaranteed to apply on posting, and
  (b) an unrelated schedule's rule could in principle also apply to a posted transaction if its other
  conditions happen to match. In practice this is a narrower gap for schedule postings whose
  category/payee/amount already come from a direct schedule-field projection
  (`SchedulePoster`/`ActuaRepository.postScheduleTransaction`) rather than rule application, but any
  *extra*, user-added action on a schedule-owned rule (e.g. a note or tag beyond the four recognized
  slots) is not guaranteed to apply on posting today. Tracked as
  [#290](https://github.com/azimul-kabir/actua/issues/290).
- **Recurring date conditions never match through `RulesEngine`.** `RuleDateMatcher.matches` only
  accepts a string date value; a rule's `date` condition holding a recurring config (the shape a
  schedule's `isapprox` date condition stores, and the shape upstream's `condition.ts` evaluates via
  `RSchedule.occursBetween`/`occursOn`) is parsed by `RuleValue.fromJson` as an `ObjectValue`, whose
  `.text` is `null`, so the condition always evaluates to `false`. Combined with the previous gap,
  this means a schedule-owned rule's date condition can never be satisfied by the generic rules path
  (only the dedicated schedule-matching code in `data/schedules/` evaluates recurring dates
  correctly); a schedule-owned rule is therefore only reliably useful today through its
  category/payee/amount slots, which `SchedulePoster` already applies directly rather than through
  rule condition matching. Tracked together with the previous gap as
  [#290](https://github.com/azimul-kabir/actua/issues/290).
- **`isNot category (none)` does not exclude split parents.** Upstream expands `isNot category null`
  to also require `parent == false` (a split parent never satisfies "category is not none", even
  though a transfer does). Actua's `evaluateText` `"isNot"` branch has no such exclusion.
- **No Handlebars template or HyperFormula formula actions**, and **no split-transaction rule
  actions** (`set-split-amount`, `set` with `options.splitIndex`). These are recognized and preserved
  unmodified in the stored condition/action JSON rather than approximated, and are already tracked as
  "Advanced split, formula, and template rule actions" under Post-v1 portable features in
  `BACKEND_PARITY.md`.
- **No "apply rule now" bulk re-application to existing transactions**, and **no live
  matching-transaction count/list in the rule editor** (upstream's `conditionsToAQL`-backed preview).
  Actua's rule editor edits and previews a rule's effect only on a single in-progress transaction
  draft (`ActuaRepository.previewRules`/`TransactionRulePreview.kt`); it has no equivalent to
  re-scanning the transaction table for a rule change. This is a genuinely unsupported UX surface
  today, not a deliberate simplification of an implemented behavior.
- **No bank-feed/SimpleFIN import path**, so "rules on import vs. manual entry" is not yet a
  distinction Actua can exercise — every transaction Actua creates today goes through the same
  manual-entry/schedule-posting write paths, already tracked under Post-v1 portable features in
  `BACKEND_PARITY.md` ("SimpleFIN linking, download, reconciliation, and bank-feed pending-import
  approval").
- **No automatic category-rule suggestion/creation** (upstream's `updateCategoryRules`, which offers
  to create or extend a payee rule after a user repeatedly recategorizes transactions from the same
  payee). Actua only supports explicit rule creation/editing through the Rules screen.

## Result

`docs/ACTUAL_26_9_COMPATIBILITY.md`'s Rules row is updated to point here instead of its prior
un-itemized "documented supported condition/action subset" note, and `BACKEND_PARITY.md`'s rules
bullets are unchanged in substance (they already summarize this same coverage) but now cite this
document as the audited parity reference.
