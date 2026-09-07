# Actua backend parity

The original [Actuali for iOS project by Matt Farrell](https://github.com/MattFaz/actuali)
is the upstream behavioral reference for this independent Android port. A local
checkout may be available at `../actuali-ios/Actuali/Actuali` during development,
but must not be assumed by builds or tests. Android platform integrations
replace Apple-only APIs; portable financial semantics and Actual
protocol/database behavior should remain equivalent.

This is a reimplementation in Kotlin and Jetpack Compose, not a shared-code
build of the Swift application. See [README.md](README.md), [NOTICE.md](NOTICE.md),
and [LICENSE](LICENSE) for project scope and attribution.

## Version 1 boundary

Version 1 is a solid, usable Android budgeting client: budget download/local
storage, automatic and manual sync, backup/restore, accounts/categories/payees,
budget amounts and transfers, transactions/transfers/splits, imported rules,
and scheduled transactions. Every action displayed in the release UI must work.

Advanced reports, goal/cleanup automations, bank-feed setup, notifications,
location suggestions, and widgets are post-v1. Apple-only integrations are not
Android backlog items and will never be ported.

## Ported and tested

- Budget archive validation, import, download, active selection, and export
- Password login and server file lifecycle endpoints
- Editable primary/fallback server addresses with explicitly scoped private-LAN HTTP support and automatic failover without replacing local budgets
- HLC, CRDT values/messages, protobuf sync protocol, Merkle tree, encryption
- Sync convergence loop and Android Keystore-backed credentials/keys
- Actual schema migrations required by current Android reads
- Accounts, payees, category groups/categories, transactions, transfers, splits
- Transaction form planning and atomic transaction mutations, including split
  creation, child-preserving edits, opposite-direction lines, and collapse to a
  standard transaction
- Zero/reflect budget month calculations, carryover, To Budget, and exact-cent writes
- Synced account/category notes, per-account working/cleared/uncleared/reconciled
  balances, and category rollover-overspending preferences
- Calculator-style amount entry parity for budget writes
- Local backup snapshots, CRDT stripping, retention, restore, and one-shot revert
- Rule JSON parsing, schema translation, ranking, condition/action evaluation,
  named-payee resolution, and rule application for incoming transactions
- Timezone-free schedule day math, upcoming windows, lifecycle status, and
  transaction occurrence matching
- Daily/weekly/monthly/yearly schedule recurrence, monthly day/nth-weekday
  patterns, bounded endings, weekend solving, skipping, and previews
- Schedule-owned condition extraction/build/merge with custom-rule preservation,
  amount-action synchronization, JSON paths, and value conversion
- Postable/forecast schedule database projection, effective next-date selection,
  payee mapping, closed-account filtering, duplicate-row defense, and payment dedup query
- Automatic schedule posting, catch-up loop, linked-transaction deduplication,
  recurring next-date CRDT advancement, daily per-budget gate, and dirty-pass retry
- Inclusive schedule list projection (including broken/completed/manual rows),
  custom-rule detection, paid-state lookup, and unique-name checks
- Schedule create/update/delete/next-date/complete write planning and generic
  CRDT persistence, including repair of missing rule and next-date rows and local JSON paths
- Schedule discovery transaction filtering, recurrence sweeps, matching, ranking,
  payee deduplication, and create-form projection
- Account, category, and category-group rename/close/hide long-press actions
  wired through CRDT mutations and immediate UI refresh
- Category deletion through Actual-compatible tombstone mutations, with existing
  transactions safely falling back to uncategorized
- Account/category/group creation with Actual transfer-payee, opening-balance,
  mapping, duplicate-name, and sort-order behavior
- Category context actions for budget editing, month/all transaction lists,
  paired budget transfers/overspending coverage, and reversible hide/show
- Android system-back integration for detail screens and bottom-tab history;
  canonical iOS settings hub entries are visible with incomplete destinations disabled
- Android WorkManager replacement for iOS lifecycle sync: network-constrained
  foreground, post-mutation, and periodic jobs; encrypted budgets; bounded retry;
  post-sync schedule posting/re-push; periodic local backup
- Manual Sync Now and persisted last-success/error operational status in Connection & Data
- Connection & Data backup creation, archive restore, and one-shot pre-restore revert UI
- Foreground sync refresh and visible mutation failure reporting through Android snackbars
- Persistent app-wide decimal-place display preference
- Reversible category/group hiding with explicit unhide actions while hidden rows are shown
- Exact-cent account, category, transaction, summary, and transaction-entry presentation;
  hiding decimals never changes stored values
- Real database-backed Budget overview and Accounts monthly income/expense/net totals
- Actual income/source-of-funds categories rendered as the final Budget section,
  with received totals and income-safe contextual actions
- Persistent table and availability-focused Plan budget presentations
- Working previous/next budget month navigation, with reads and budget writes scoped to the selected month
- App-wide display currency selection (including no currency), symbol-only mode,
  and decimal-place presentation
- Category Spent amounts open the matching category transactions for the selected month
- Category details with notes, rollover overspending, and six-month history-based quick assign
- Account details with notes and working, cleared, uncleared, and reconciled balances
- Collapsible account balance details with compact Budget-tab typography
- Credit-card account details with limit, available credit, current billing cycle,
  cycle spending, and calculated payment due date
- Add/edit split transaction UI with per-line category, amount, direction, payee,
  notes, remaining allocation, and Actual-compatible child-row persistence

## Remaining version 1 work

- Remaining entity deletion/merge/reorder mutations and Android action wiring
- Budget templates, goals, and broader automation UI
- Local-backup export/share and import picker

## Post-v1 portable features

- Rule editor/list UI, CRDT rule mutations, split/formula/template rule actions
- Schedule discovery UI wiring
- Goal templates, cleanup templates, and budget automations
- Reports/dashboard models and calculation engines
- SimpleFIN linking, download, reconciliation, and pending-import approval
- Android transaction notifications and new-transaction detection
- Location-backed payee suggestions
- Widget snapshot generation

## Permanently excluded or replaced

- FinanceKit / Apple Wallet: excluded
- App Intents / Shortcuts: excluded
- iCloud/Keychain/background-task APIs: replaced with Android storage, Keystore,
  and WorkManager equivalents

## Port maintenance

When upstream Actuali changes, compare the relevant Swift model, service, test,
and view behavior before changing Android. Port financial and synchronization
semantics with tests; adapt only platform presentation and lifecycle behavior.
Record deliberate exclusions here so the Android project never presents an
Apple-only feature as unfinished work.
