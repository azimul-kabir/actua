# Sync behavior and verification

Actua is local-first. It renders the selected local SQLite budget immediately and does not block
launch on the network. Remote changes become visible after a foreground or background sync commits
them to that database.

## Foreground refresh

`MainActivity.onStart` increments a foreground generation. The active Compose navigation tree uses
each non-zero generation to request a foreground sync on a background dispatcher. This covers both
a fresh launch and returning after the activity was stopped. The initial composition at generation
zero does not start a duplicate request.

While the request is active, Actua displays **Syncing budget… Showing local data.** The screen remains
usable offline. A successful sync emits an in-process data-generation signal. The visible Budget,
Accounts, Transactions, Reports, rules, schedules, payees, and credit-card projections then reread
the committed database without requiring navigation or **Sync now**.

The banner only appears for **App open** and **Background** syncs, where the server may hold changes
the local database doesn't have yet. A sync triggered by a local edit (trigger **After change**) only
uploads what the visible screen already reflects, so it never shows the banner; a failure from that
upload still surfaces through the existing error path in **Manage → Connection & Data** rather than a
blocking banner. Without this distinction, active editing (entering several transactions, budgeting
multiple categories, toggling cleared status) would otherwise retrigger the banner on every edit spaced
more than a second apart, since each mutation sync is a full network round trip that never reuses a
recent success (see below).

Foreground and periodic requests for the same budget reuse a successful sync that completed within
five seconds. This prevents an app-open request waiting behind a nearly completed background worker
from immediately repeating the full network round trip. Post-mutation work never reuses that result,
because it may have new local CRDT messages to upload.

## Periodic background refresh

Actua registers one unique periodic WorkManager job with:

- a 15-minute interval, Android's minimum periodic interval
- a connected-network constraint
- exponential retry beginning at 10 seconds, capped by the worker after five attempts
- persistent unique-work registration using `ExistingPeriodicWorkPolicy.UPDATE`
- per-run schedule posting, a second sync when schedules were posted, widget refresh, and local backup

Periodic work is not a real-time push channel. Android may defer it because of Doze, App Standby,
battery optimization, background restrictions, unavailable network, or scheduler load. Force-stopping
Actua prevents scheduled work until the user opens the app again. These are platform constraints, not
a guarantee that work runs every 15 minutes exactly. See Android's documentation for
[periodic work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#schedule_periodic_work)
and [background optimization](https://developer.android.com/topic/performance/background-optimization).

Demo budgets, disconnected installations, missing cloud identity, and unavailable encryption keys
do not enter normal server synchronization. Errors are retained in **Manage → Connection & Data**.

## Status and timing

**Manage → Connection & Data → Sync** reports:

- current state and trigger: App open, Background, After change, or Manual
- last successful sync
- last successful app-open refresh
- last completed periodic background attempt, whether successful, skipped, or failed
- duration of the latest successful sync
- the latest actionable error

The duration is measured from the synchronized network/database work beginning until the database is
committed. The in-process data signal is emitted immediately afterward, so Compose invalidation occurs
in the same completion turn.

## Release smoke test

Use a backed-up, non-demo test budget and record the Actual server version, Actua build, device,
Android version, battery mode, and network.

1. Open the budget in Actua, wait for idle, and note the displayed app-open refresh time and duration.
2. Put Actua fully in the background and change a clearly identifiable synthetic transaction in the
   Actual web app.
3. Reopen Actua. Confirm local data appears immediately, the non-blocking sync banner appears, and the
   web change appears without navigation or **Sync now** when the banner clears.
4. Repeat by leaving Actua open until a periodic worker completes. Confirm the active screen refreshes
   without navigation.
5. Disable the network, reopen Actua, and confirm local data remains usable and the failure is visible.
6. Restore the network and use **Sync now** as an explicit retry.
7. Repeat once with battery optimization enabled and once with Actua background-restricted. Record the
   actual delay rather than expecting an exact 15-minute start.

Automated regression coverage verifies foreground-generation gating, same-budget coalescing boundaries,
mutation bypass, trigger labels, status timing, existing sync convergence/retry behavior, and the API
28/35/36 instrumentation suite. A real server smoke test remains required for release validation.
