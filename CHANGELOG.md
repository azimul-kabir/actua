# Changelog

All notable user-facing changes to Actua are recorded here. This project uses [Semantic Versioning](https://semver.org/) where practical. Versions marked `alpha` are testing builds and may contain incomplete workflows or require a clean reinstall before a future production release.

## Unreleased

### Changed

- Replaced transaction cleared-state letters with compact green or gray check controls without changing amount alignment
- Transaction taps now open a read-only detail sheet with explicit Edit and Delete actions
- Global search transaction results now use the same detailed row and view flow as All Accounts
- Restored the compact single-line Ready to Budget overview in Plan view
- Aligned Table overview amounts and category balance pills to the same column anchors used by Plan view
- Unified transaction, Budget, To Budget, and Move Money calculators around one compact equal-size Material key grid
- Replaced Budget entry headings and separate amount labels with a focused inline amount and cursor

## [0.1.0-alpha.5] - 2026-09-07

Fifth public testing release.

### Added

- Global Material You search across transactions, accounts, payees, categories, notes, and transfer accounts
- Unified category budget sheet with Budget entry, Auto-Assign, Move Money, Details, recent transactions, notes, rollover, rename, hide, and deletion actions
- Screenshot-inspired Material You calculators for transaction amounts and category budgeting

### Changed

- Redesigned transaction rows with category chips, notes, cleared status, per-row dates when date grouping is disabled, and account context in All Accounts
- Transfers in All Accounts now identify both source and destination accounts, while individual account views omit the current account
- Saving a new transaction now opens All Accounts transactions
- Renamed Assigned to Budgeted and Available to Balance throughout the Budget views
- Made the complete category row open Budget entry in both Table and Plan views; amount fields no longer have separate tap actions
- Unified the Plan and Table overview layouts and added aligned pills to To Budget and Balance amounts
- Plan group Budgeted totals are shown only while the group is collapsed

## [0.1.0-alpha.4] - 2026-09-07

Fourth public testing release.

### Added

- Full-screen Material account, payee, and category selectors with immediate search, alphabetical sections, selected-item indicators, transfer-account grouping, new-payee creation, and account balances
- A persistent availability-focused Plan budget view alongside the existing table view
- Interactive Plan figures: Assigned opens assignment and money-moving actions, while Spent opens the category's transactions for the selected month
- Ready to Assign and To Budget funding flows for assigning money to categories or covering a negative To Budget balance
- Source of Fund/Income as the final Budget section, with Actual-backed received totals and income-safe actions

### Changed

- Adopted the original Actua Fold A as a fully scalable SVG and native Android vector icon
- Preserved the solid violet adaptive background with matching Android 13+ Material You vector geometry
- Aligned account working-balance values by moving the disclosure control beside the label
- Remembered collapsed account summaries and Budget category groups across navigation and app restarts
- Replaced always-open account and category note forms with compact tappable note rows and focused editors
- Added a tappable Budget month label with a Material month-and-year selector
- Ported Actuali's rule manager with searchable summaries, stage ordering, all/any conditions, typed values, entity pickers, and editable actions
- Added Actual-compatible CRDT rule creation, updates, deletion, schedule-owned rule protection, and native transaction execution
- Added editable primary and fallback Actual server URLs without disconnecting or replacing downloaded budgets, with automatic failover during connection and sync
- Allowed cleartext HTTP for the configured local Actual server at `192.168.68.109` while retaining Android's cleartext block for other destinations
- Renamed the independent Android client from Actuali for Android to Actua
- Changed the application ID and Kotlin namespace from `com.azimulkabir.actuali`
  to `com.azimulkabir.actua`
- Added an original Material You-ready adaptive launcher icon with a monochrome
  themed-icon layer
- Updated project documentation while preserving credit to Actuali for iOS and
  Actual Budget
- Transaction notes now use a compact single-line field
- Budget groups, categories, account sections, and account rows have clearer Material hierarchy
- Availability pills in Plan view use tighter corners and aligned amount text
- Saving or cancelling an edited transaction returns to its originating account

### Migration

- Android treats Actua as a separate app from earlier Actuali for Android alpha
  builds. Synchronize and back up local changes before removing an older build.

## [0.1.0-alpha.3] - 2026-09-06

Third public testing release.

### Changed

- The working-balance summary in account details can now be collapsed while keeping the current balance visible
- Account balance details and notes now use the same compact typography scale as the Budget tab
- Added restrained Material motion for main-tab changes, detail navigation, search fields, and expandable account summaries

## [0.1.0-alpha.2] - 2026-09-05

Second public testing release.

### Added

- Account details now show working, cleared, uncleared, and reconciled balances
- Synced notes for accounts and budget categories using Actual's native notes data
- Credit-card account details with available credit, limit, current billing cycle, cycle spend, and payment due date
- Category rollover-overspending control and history-based quick assign suggestions
- Full split transaction entry and editing with per-line category, amount, optional payee, note, direction, remaining amount, line addition/removal, and collapse back to a normal transaction

### Fixed

- Transaction forms now scroll through fields and actions within the available screen and keyboard space
- Expense, Income, and Transfer selector labels are centered consistently
- Add mode now has an explicit Cancel action; Edit mode has working Save, Delete, and Cancel actions
- Split edits retain existing child transaction identities instead of unnecessarily replacing every line
- Existing split transactions can be safely converted back to standard transactions

## [0.1.0-alpha.1] - 2026-09-05

Initial public testing release.

### Added

- Native Jetpack Compose interface for Budget, Accounts, Add, Reports, and More
- Password connection to self-hosted Actual servers
- Remote budget selection, download, local SQLite storage, and offline access
- Actual-compatible encrypted CRDT synchronization with manual and background sync
- Budget overview, month navigation, category groups, collapsible rows, totals, progress bars, and editable budget amounts
- Persistent category and group hiding with hidden-category management
- Account balances, on-budget/off-budget grouping, monthly income/expense/net summary, and transaction browsing
- Full local transaction history with search and optional date grouping
- Expense, income, and transfer entry with searchable account, payee, and category fields
- Transaction editing, clearing, deletion, splitting, date pickers, notes, and calculator amount entry
- New-payee creation from transaction entry
- Long-press actions for accounts, groups, categories, and transactions
- Local backup, restore, retention, and pre-restore revert support
- Rules and scheduled-transaction backend processing
- Credit-card limits, statement cycles, due dates, cycle spend, and available-credit display
- Basic reports backed by local budget data
- Light, dark, and system appearance modes
- Configurable start page, decimal visibility, balance privacy, transaction grouping, and account summary
- Currency display options for None, BDT, USD, EUR, GBP, CAD, AUD, JPY, INR, CNY, SGD, AED, and SAR
- Optional symbol-only currency formatting
- App name and icon matching the original Actuali visual identity

### Known limitations

- This build is alpha software and should be used with tested backups
- The APK is debug-signed for sideload testing, not Play Store distribution
- OpenID Connect, custom proxy headers, advanced dashboards, bank-feed setup, and schedule-management UI are not yet included
- Some advanced entity merge, reorder, template, goal, and automation workflows remain incomplete
- Apple-only features from the iOS project are intentionally excluded

### Credits

- [Matt Farrell's Actuali for iOS](https://github.com/MattFaz/actuali) is the upstream behavioral and design reference
- [Actual Budget](https://github.com/actualbudget/actual) provides the underlying budgeting platform and source reference for CRDT behavior
- The Actuali icon was designed by [u/bdownz](https://www.reddit.com/user/bdownz/)

[0.1.0-alpha.1]: https://github.com/azimul-kabir/actuali-android/releases/tag/v0.1.0-alpha.1
[0.1.0-alpha.2]: https://github.com/azimul-kabir/actuali-android/releases/tag/v0.1.0-alpha.2
[0.1.0-alpha.3]: https://github.com/azimul-kabir/actuali-android/releases/tag/v0.1.0-alpha.3
[0.1.0-alpha.4]: https://github.com/azimul-kabir/actuali-android/releases/tag/v0.1.0-alpha.4
[0.1.0-alpha.5]: https://github.com/azimul-kabir/actuali-android/releases/tag/v0.1.0-alpha.5
