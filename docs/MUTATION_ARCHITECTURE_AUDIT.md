# Mutation architecture, gestures, DB/CRDT and sync audit (#324)

Part of the [performance & UI smoothness initiative](https://github.com/azimul-kabir/actua/issues/316).
This slice audits every interaction path that calls repository/database/CRDT/sync logic, per
#324's checklist, specifically checking whether the PR #250 category-reorder-during-drag
anti-pattern recurs anywhere else.

## Findings

**The PR #250 anti-pattern does not recur anywhere in the app.** There is no `Slider()` and no
other continuous-drag input besides category/group reorder. Both group reorder
(`ReorderGroupsScreen.kt`) and in-group category reorder (`ManageCategoriesScreen.kt`) already
persist only in `onDragEnd` — per-frame drag deltas only update local list state
(`CategoryDragReorder.step`, pure/in-memory). Budget-amount and transaction-amount entry
(`CalculatorAmountSheet`) is purely local state per keystroke; the DB write happens once on
explicit Save. Sync (`ActualSyncWorker`) already runs as a `CoroutineWorker` off the main thread
via WorkManager, with no blocking wait for it anywhere in navigation or gesture code — the UI
observes it reactively through `SyncSignals`' `StateFlow`s.

**The real, fixed issue: an N+1 query pattern in `budgetGroups()`.** `ActuaRepository.budgetGroups()`
(called synchronously during composition via `remember(dataVersion, budgetMonth) { repository.budgetGroups(...) }`,
per #322's audit — i.e. on the main thread, once per data/month change) called
`ActualBudgetDatabase.fetchNote(categoryId)` once per category, both for expense and income
categories. `fetchNote` itself re-checked `hasTable("notes")` — a fresh `sqlite_master` query —
on every single call. For a budget with N categories, that's up to 2N synchronous SQLite
round-trips just for notes, on top of 7 separate `fetchBudgetMonth` calls (current month + 6
months of history) already present. `accounts()` had the same per-account pattern for account
notes.

**Fix**: added `ActualBudgetDatabase.fetchNotes(ids: Collection<String>): Map<String, String>` —
a single batched query (and a single `hasTable` check) — and use it in both `budgetGroups()` and
`accounts()` in place of the per-row `fetchNote()` loop. Verified against the demo budget via the
existing `DemoBudgetTest` (androidTest), which passed unchanged — this is a pure performance
change with no behavioral difference (same notes, same fallback to `""` when a category has no
note row).

## Deliberately not changed

`mutate()` (`AppNavigation.kt`) — the function nearly every DB write in the app goes through
(`mutate("Saving X") { repository.someCall(...) }`) — is fully synchronous: it runs the repository
call directly on the calling (main/UI) thread, with no `withContext`/coroutine dispatch. This is
used in toggles (category/group hidden, transaction cleared, carryover), and everywhere else a
button or switch triggers a write.

This was **not** converted to run off the main thread in this slice. Reasoning:
- Every individual write found (`setTransactionCleared`, `setCategoryHidden`, `saveTransaction`,
  etc.) is a small, single/few-row SQLite write — not the kind of N+1 or multi-hundred-row scan
  that `budgetGroups()` was. No profiling evidence (per #316's own non-goal: "optimizing blindly
  without profiling evidence") suggests these individually cause visible jank; #321's benchmarks
  don't currently isolate write latency from the recomposition/list costs already fixed in
  #322/#323.
- `mutate()` is called from 50+ sites throughout `AppNavigation.kt`. Making it asynchronous
  correctly — handling in-flight state, error surfacing, and avoiding races with `dataVersion`
  bumps and CRDT sync scheduling (`scheduleSync` fires after every write) — is an architectural
  change, not an audit-driven fix, and risks the CRDT/sync correctness #316 requires stay
  authoritative. It's a reasonable candidate for its own follow-up once #321's benchmarks can
  show which specific writes are actually slow enough to matter.

## Acceptance criteria

- [x] Category reorder uses local state during drag and persists once after completion (already true, reverified).
- [x] Other gesture callbacks are audited for repeated persistence — none found beyond reorder.
- [x] Sync does not compete with interaction-critical main-thread work (already true — `CoroutineWorker`/WorkManager).
- [x] UI remains responsive while background sync is active (no blocking waits found).
- [x] Actual Budget/CRDT correctness remains intact (verified via `DemoBudgetTest`; the only code change is query batching, not write/sync logic).
- [ ] No known DB/CRDT/network operation blocks a normal gesture or navigation transition unnecessarily — the `budgetGroups()`/`accounts()` N+1 is fixed; `mutate()`'s synchronous main-thread writes are audited and documented but intentionally left for a follow-up pending profiling evidence (see above).
