# Navigation performance audit

Issue #327 audits the primary navigation paths from the performance initiative in #316.

## State retention

`AppNavigation` keeps each main-tab/detail pair in a `SaveableStateHolder`. The Budget,
Accounts, Transactions, Reports, and Manage roots therefore retain their lazy-list or scroll
state when another tab is selected. Per-tab detail snapshots additionally retain account,
category, month, search, and open-category context. Selecting the active tab still returns its
detail to the tab root, and selecting an already-rooted tab requests scroll-to-top.

## Immediate tab feedback

The selected bottom-navigation item is updated independently from the displayed destination.
Destination restoration starts on the next frame, after Compose has had an opportunity to draw
the selection indicator. Rapid repeated taps cancel the superseded pending switch so an older
tap cannot replace a newer choice.

## Destination work

Reports aggregation is the expensive main-tab initialization path: it reads transaction,
account, category, dashboard, and budget data. It now runs on the I/O dispatcher after the tab
has been selected. The Reports screen appears immediately with a loading state on first entry
and retains the last local snapshot while a newer `dataVersion` is prepared. Budget, Accounts,
Transactions, and Manage continue to use their already-cached root data and saveable screen
state.

Transaction save/delete work remains off the main thread, and sync remains independent of
navigation. Detail transitions continue to use the existing Android back callback and Material
motion; navigation never waits for sync or report database work.
