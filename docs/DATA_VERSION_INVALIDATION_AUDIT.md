# Reducing broad `dataVersion` / global invalidation (#325)

Part of the [performance & UI smoothness initiative](https://github.com/azimul-kabir/actua/issues/316).
#325 itself flags this as the highest architectural-risk slice and suggests a short design note
before broad implementation — this is that note, plus the scoped, low-risk fix actually applied.

## How invalidation works today

`AppNavigation.kt` holds a single `var dataVersion by remember { mutableStateOf(0) }`. `mutate()`
(the wrapper nearly every repository write goes through) increments it by 1 on any successful
write, anywhere in the app. Roughly a dozen `remember(dataVersion, ...) { repository.X() }` reads
at the top of `AppNavigation`'s body — `budgetGroups`, `accounts`, `transactions`,
`categoryNames`, `payeeNames`, `creditCards`, `rules`, `reorderCategoryGroups`,
`rulesSupported`, `scheduleOwnedRuleIds`, `ruleEditorData`, `schedules`, `reportSnapshot` — all
key off that same counter, so **every** mutation anywhere re-runs **all** of them, regardless of
how narrow the actual change was: marking one transaction cleared refetches and rebuilds the
entire budget, every account, every rule, every schedule, and the full reports aggregation, even
though none of that data could plausibly have changed.

## Why a full split into per-domain counters is out of scope here

The obvious-looking fix — replace the single `dataVersion` with several counters
(`transactionsVersion`, `budgetVersion`, `rulesVersion`, ...) bumped only by the mutations that
actually affect each — turns out to have real cross-dependencies once traced:

- `ruleEditorData()` reads accounts, category groups, *and* payees (for the rule condition/action
  pickers) — so it would need to be invalidated by account, category, *and* payee mutations, not
  just rule mutations.
- `scheduleOwnedRuleIds()` depends on which rules are linked to schedules — a schedule mutation
  needs to invalidate rule-related state, not just schedule state.
- `budgetScheduleFunding()` (Budget screen) reads schedule data — so schedule mutations can't be
  fully isolated from the Budget tab either.

Getting this right for every one of the ~15 read functions and ~70 `mutate()` call sites would
mean tracing every read's true dependency set and keeping ~70 call sites correctly annotated with
which counter(s) to bump — exactly the kind of "over-scoping into a larger rewrite of the
reactive/mutation model" #325 itself warns against, with real correctness risk (a missed bump
means stale data silently shown) for a codebase whose Actual Budget/CRDT correctness must stay
authoritative. This is left as a candidate for a dedicated follow-up, not attempted here.

## What was fixed instead: stop eagerly recomputing single-destination reads

A narrower, unambiguously-safe win: several of the values above are read by exactly **one**
navigation destination that isn't always on screen — yet they were declared at the very top of
`AppNavigation`'s body, so they recomputed on *every* `dataVersion` bump regardless of which
screen the user was actually looking at. Toggling a transaction's cleared state while on the
Budget tab was, before this fix, also refetching and rebuilding Reports' full aggregation, the
rule editor's account/category/payee choice lists, and the category-group reorder list — none of
which anything was currently displaying.

Moved to `remember(dataVersion) { ... }` at their point of use instead (the same pattern the
existing `payeeLocations` read at `DetailDestination.PayeeLocations` already used — this isn't a
new pattern, just applying an existing one more consistently):

| Value | Sole consumer |
| --- | --- |
| `reportSnapshot` (`repository.reports()`) | `MainDestination.Reports` |
| `rules`, `rulesSupported`, `scheduleOwnedRuleIds`, `ruleEditorData` | `DetailDestination.Rules` |
| `reorderCategoryGroups` (`repository.categoryGroupsForReorder()`) | `DetailDestination.ManageCategories` / `DetailDestination.ReorderGroups` |

This changes *when* the work happens (only while that destination is actually composed) without
changing *what* triggers a refresh (still `dataVersion`, so correctness is unaffected — a mutation
that used to force an immediate, unseen recompute now just means that data is recomputed fresh
the next time its screen is opened, same as `payeeLocations` already behaved). No change to
`budgetGroups`, `accounts`, `transactions`, `categoryNames`, `payeeNames`, or `creditCards` — each
of those is read by more than one primary tab/destination (or by an always-visible part of the
UI, like the FAB's account/payee pickers), so eagerly keeping them warm remains the right
trade-off.

## Acceptance criteria

- [x] Major broad-invalidation sources are identified (this document).
- [x] Highest-impact global refreshes replaced with narrower updates where practical — the
      single-destination reads above no longer run for unrelated mutations on other screens.
- [ ] Small mutations produce correspondingly small UI/data work — improved for the reads above;
      `budgetGroups`/`accounts`/`transactions` still all refetch on every mutation, since safely
      narrowing those requires the domain-counter work described above as out of scope here.
- [x] UI-only state changes do not unnecessarily invalidate repository data (audited during #324;
      display preferences, expand/collapse, and navigation state are already local `remember`
      state, never routed through `mutate()`/`dataVersion`).
