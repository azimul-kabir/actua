# Local-first launch and transaction-save audit (#348)

A beta tester reported the app feeling unresponsive while syncing on launch, and
Add/Edit Transaction appearing to wait on the server. This audit traces both
paths end to end and records what was already local-first vs. what needed a fix.

## App launch / foreground sync

Already non-blocking, no changes needed:

- `MainActivity.onCreate`/`onStart` (`MainActivity.kt`) never awaits sync; it only
  schedules WorkManager sync and bumps a `foregroundGeneration` counter.
- `AppNavigation`'s `LaunchedEffect(foregroundGeneration)` triggers
  `ActualSyncRunner.run(...)` inside `withContext(Dispatchers.IO)` as a fire-and-forget
  effect. Budget/Accounts/Transactions screens are built from local SQLite reads
  (`remember(dataVersion, ...) { repository.xxx() }`) and render on the first frame,
  independent of that effect.
- `reportedFullyDrawn` is marked once local budget data exists, explicitly decoupled
  from sync completion.
- The only sync-related UI is a dismissible, non-blocking top banner
  (`SyncStatusBanner`) shown while sync is running; it does not disable other UI.

## Transaction save path

Also already local-first in the sense that it never awaited a network/server
response — `saveTransaction`/`deleteTransaction` only append to the local CRDT
message log (`messages_crdt`, which already serves as the durable outbound sync
queue/outbox) and enqueue a WorkManager mutation-sync job
(`ActualSyncScheduler.scheduleMutation`), which runs later, off the main thread,
in a `CoroutineWorker`.

The one real gap: the local SQLite/CRDT write itself ran synchronously on the
main thread inside the Compose `onSave`/`onDelete` callbacks in
`AppNavigation.kt` — disk I/O blocking the UI thread, not network I/O. This was
flagged but deliberately deferred by the #324 mutation-architecture audit
(`docs/MUTATION_ARCHITECTURE_AUDIT.md`) pending a dedicated follow-up.

**Fix**: `AddTransactionScreen`'s `onSave`/`onDelete` handlers in
`AppNavigation.kt` now dispatch the local write via
`coroutineScope.launch { withContext(Dispatchers.IO) { ... } }` (a new
`mutateAsync` helper mirrors the existing synchronous `mutate()` helper for the
delete path). The editor still only dismisses/navigates away once the local
write has durably completed — "optimistic" does not mean risking loss if the
process dies mid-save; it means not waiting on the network, which was already
true and remains true. Sync scheduling, the CRDT outbox, and
`ActualSyncWorker`/`ActualSyncRunner` are unchanged.

`mutate()`'s other ~50 call sites (category/account toggles, schedules, rules,
tags, etc.) are unchanged. They remain synchronous main-thread writes for the
same reasons #324 gave: each is a small, single/few-row write with no profiling
evidence of visible jank, and converting the shared 50-site helper broadly is a
larger architectural change than this issue's transaction-save scope. It
remains a reasonable candidate for a future follow-up if profiling shows it
matters.

## Acceptance criteria

- [x] With a valid local budget, app-open foreground sync does not block normal navigation or interaction (already true).
- [x] A visible sync indicator does not disable unrelated UI (already true).
- [x] Add/Edit Transaction does not wait for a server response after the mutation is durably committed locally (already true).
- [x] Saved transaction is immediately visible from local state (already true; now also off the main thread).
- [x] Local mutations made while sync is active are preserved and eventually synchronized (CRDT log + WorkManager retry, already true).
- [x] Offline transaction creation/editing continues to work (unchanged; local write is independent of connectivity).
- [x] Network failure after local save does not lose or roll back the transaction (unchanged; save never depended on the network call succeeding).
- [x] Pending changes survive process death/restart (CRDT log is written durably before the callback returns; unchanged).
- [x] Concurrent foreground/background sync requests are safely deduplicated/coalesced (`ExistingWorkPolicy.APPEND_OR_REPLACE` + 1s coalescing delay, unchanged).
- [x] Sync failure does not invalidate valid local data (unchanged).
- [x] No new ad-hoc queue introduced; the existing CRDT mutation log (`messages_crdt`) serves as the durable outbox.
- [ ] Launch-to-interactive and transaction Save-to-dismiss timings measured before/after — not measured in this slice; both paths were already off the blocking-network critical path, and the main-thread SQLite write moved off-thread is a small, single-row write per #324's own profiling caveat.
