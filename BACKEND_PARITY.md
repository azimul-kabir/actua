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

Version 1 is a solid, usable Android budgeting client: password and OpenID/OIDC
server authentication, budget download/local storage, automatic and manual sync,
backup/restore, accounts/categories/payees, budget amounts and transfers,
transactions/transfers/splits, imported rules, scheduled transactions, category
targets, mobile reconciliation, synced report dashboards, Android credit-card
payment reminders, home-screen widgets, and launcher actions. Every action
displayed in the release UI must work.

Features that still require substantial new financial semantics or external
integrations remain outside the current version boundary, including full
budget/cleanup automation authoring, bank-feed setup, general transaction
notifications/new-transaction detection, and location-backed payee suggestions.
Apple-only integrations are not Android backlog items and will never be ported;
where an Android equivalent exists, Actua uses the native Android integration.

## Ported and tested

- Budget archive validation, import, download, active selection, and export
- Password and OpenID/OIDC login through Actual's `/account/login` flow, including explicit
  login-method selection, browser authorization, a localhost-only callback listener,
  Actual session-token capture, and preservation of password login on mixed-mode servers
- Server file lifecycle endpoints, including Actual-compatible blank-budget creation/upload
  and exact-name confirmed server deletion with local cleanup
- Local-only demo-budget lifecycle with fixed `demo` identity, current-schema recreation,
  no cloud registration, explicit sync rejection, and Connection & Data launch/reset flow
- Demo seed coverage for checking, savings, credit-card and off-budget investment accounts;
  six months of transactions; paired card-payment transfers; cleared/uncleared/reconciled
  states; category targets; rules; scheduled transactions; notes; and dashboard report data
- Editable primary/fallback server addresses with explicitly scoped private-LAN HTTP support and automatic failover without replacing local budgets
- HLC, CRDT values/messages, protobuf sync protocol, Merkle tree, encryption
- Sync convergence loop and Android Keystore-backed credentials/keys
- Stored sync clock validation and legacy/epoch recovery from the message-log
  high-water mark, preserving pending edits and Merkle-guided restart recovery
- Actual schema migrations required by current Android reads
- Accounts, payees, category groups/categories, transactions, transfers, splits
- Transaction form planning and atomic transaction mutations, including split
  creation, child-preserving edits, opposite-direction lines, and collapse to a
  standard transaction
- Zero/reflect budget month calculations, carryover, To Budget, and exact-cent writes
- Synced account/category notes, per-account working/cleared/uncleared/reconciled
  balances, and category rollover-overspending preferences
- Shared compact calculator-style amount entry for budget and transaction writes,
  including complete expression display and predictable operator backspace editing
- Local backup snapshots, CRDT stripping, retention, validated document-picker import,
  restore, and one-shot revert
- Rule JSON parsing, schema translation, ranking, condition/action evaluation,
  named-payee resolution, and rule application for incoming transactions
- Rule list/search/editor UI and Actual-compatible CRDT create, update, and
  delete mutations for supported condition and action schemas, with protection
  for schedule-owned rules
- Timezone-free schedule day math, upcoming windows, lifecycle status, and
  transaction occurrence matching
- Searchable Scheduled Transactions UI with new-schedule creation,
  paid/due/upcoming/missed/completed status, completed-history visibility,
  recurrence skipping, restart/completion, deletion, and linked transaction
  history/unlinking through the existing CRDT write path
- Daily/weekly/monthly/yearly schedule recurrence, monthly day/nth-weekday
  patterns, bounded endings, weekend solving, skipping, and previews, with a
  dedicated Android repeat editor for all supported options
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
  payee deduplication, create-form projection, and selectable Find Schedules UI
- Monthly Bills calendar with recurring and card-bill modes, due-date projection,
  paid-transaction matching, status totals and filters, and safe schedule actions
- Account, category, and category-group rename/close/hide long-press actions
  wired through CRDT mutations and immediate UI refresh
- Category deletion through Actual-compatible tombstone mutations, with existing
  transactions safely falling back to uncategorized
- Account/category/group creation with Actual transfer-payee, opening-balance,
  mapping, duplicate-name, and sort-order behavior
- Entity mutation core completed for account deletion, category-group deletion,
  ordinary-payee deletion/merge, category reorder, and category-group reorder. All
  writes use synced CRDT messages; payee merges redirect `payee_mapping` before
  tombstoning source payees; account deletion tombstones its owned transfer payee;
  transfer payees cannot be independently deleted or merged; group deletion
  tombstones its categories before the group; and reorder uses Actual-compatible
  shove sort orders. Destructive UI remains opt-in only where a safe confirmation
  flow is present.
- Category context actions for budget editing, month/all transaction lists,
  paired budget transfers/overspending coverage, and reversible hide/show
- Android system-back integration for detail screens and bottom-tab history;
  canonical iOS settings hub entries are visible with incomplete destinations disabled
- Android WorkManager replacement for iOS lifecycle sync: network-constrained
  foreground, post-mutation, and periodic jobs; encrypted budgets; bounded retry;
  post-sync schedule posting/re-push; periodic local backup
- Android home-screen widget snapshot generation from the selected local budget, including
  budget overview, configurable category and account rows, privacy-aware amount formatting,
  post-write/post-sync refresh, and transaction-entry deep links
- Android launcher long-press actions for preselected expense, income, and transfer entry plus search
- Manual Sync Now plus live idle/running/error, last-success, and last-background-refresh status in Connection & Data
- Dedicated backup manager with private archives, independent app-background creation,
  retention, archive export, optional Storage Access Framework folder mirroring,
  restore, and one-shot pre-restore revert
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
- Actual-compatible UI-managed category targets for monthly spending, fixed monthly saving,
  save-by-date, refill-to-cap, weekly spending, and recent-spending averages; target-aware
  Auto Assign; preview-first whole-budget application of supported targets in upstream priority
  order with multi-contribution, refill-cap and available-funds handling, safe Apply versus
  explicit Overwrite behavior, and one CRDT mutation batch;
  multi-automation list editing and atomic `goal_def` replacement for fully supported UI-managed
  definitions; goal-only balance targets with atomic budget/goal writes; weighted remainder
  distribution after ordinary priorities; and safe read-only disclosure of advanced or
  notes-managed templates
- Account details with notes and working, cleared, uncleared, and reconciled balances
- Full mobile account reconciliation with bank-balance comparison, difference display,
  uncleared-transaction review, optional cleared adjustment, and atomic CRDT locking
  of every cleared stored row including split parents and children
- Collapsible account balance details with compact Budget-tab typography
- Credit-card account details with limit, available credit, current billing cycle,
  cycle spending, and calculated payment due date using either a fixed due day or
  a legacy days-after-statement offset
- Opt-in Android credit-card payment reminders at 7, 5, 3, and 1 days before
  due, with permission handling, stale-work cancellation, delivery-time balance
  validation, and unpaid-first stable due-date sorting
- Database-backed complete-history transaction search, including live split-child
  payees, notes, imported descriptions and categories; stable database paging
- Character-by-character payee-picker filtering with one alphabetical result list
  across ordinary payees and matching transfer accounts
- Persisted app-wide reconciled-transaction filtering applied before database
  paging and search, shared by account and all-transaction lists
- Add/edit split transaction UI with per-line category, amount, direction, payee,
  notes, remaining allocation, and Actual-compatible child-row persistence
- Off-budget transaction category enforcement for standard and split create/edit
  flows, including clearing stale categories when an account changes
- Regression coverage for interrupted sync retries, transfer-pair symmetry,
  standard/split conversion, off-budget splits, rule JSON round trips, and
  credit-card due-date boundaries
- Synced Actual dashboard pages and ordered widget rows, Actuali-compatible
  widget time frames and shared rule conditions, and native Summary, Net Worth,
  Cash Flow, Spending, Markdown, Age of Money, Formula, Custom Report, Calendar,
  Crossover, Budget Analysis, Sankey, Balance Forecast, and Monte Carlo cards
  with unknown future widget-type disclosure

## Remaining version 1 work

- Cleanup automation and remaining advanced whole-budget template evaluation;
  see [the audited behavior and staged boundary](docs/BUDGET_AUTOMATION_PARITY.md)

## Post-v1 portable features

- Advanced split, formula, and template rule actions
- Goal/cleanup templates and broader budget automation authoring beyond the
  category targets and target-aware Auto Assign already shipped
- SimpleFIN linking, download, reconciliation, and pending-import approval
- General Android transaction notifications and new-transaction detection beyond
  the credit-card payment reminders already shipped
- Location-backed payee suggestions

## Permanently excluded or replaced

- FinanceKit / Apple Wallet: excluded
- App Intents / Shortcuts: excluded; Android launcher actions provide the
  platform-native quick-entry/search equivalent where appropriate
- iCloud/Keychain/background-task APIs: replaced with Android storage, Keystore,
  and WorkManager equivalents

## Port maintenance

When upstream Actuali changes, compare the relevant Swift model, service, test,
and view behavior before changing Android. Port financial and synchronization
semantics with tests; adapt only platform presentation and lifecycle behavior.
Record deliberate exclusions here so the Android project never presents an
Apple-only feature as unfinished work.
