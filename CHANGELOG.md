# Changelog

All notable user-facing changes to Actua are recorded here. This project uses [Semantic Versioning](https://semver.org/) where practical. Versions marked `alpha` are testing builds and may contain incomplete workflows or require a clean reinstall before a future production release.

## [1.0.0-beta.1] - 2026-09-09

First beta of the 1.0 release line.

### Added

- Ported every dashboard widget currently rendered by Actuali: Age of Money,
  Formula, Custom Report, Calendar, Crossover, Budget Analysis, Sankey, Balance
  Forecast, and Monte Carlo
- Added native Material cards, charts, calendar grids, category bars, comparison
  series, and forecast displays for the newly supported report widgets

### Changed

- Updated fresh-install defaults to use Plan view with overview and progress bars,
  enable transaction and account options, start on Budget, use no currency
  override, and hide decimal places
- Replaced the floating transaction Save action with a full-width bottom button
  and matched transaction keypad borders to the Budget keypad
- Grouped Accounts, Transactions, and Reports toolbar actions inside Material
  pill-shaped surfaces and added Accounts controls for monthly summary, expanding
  all groups, and collapsing all groups
- Promoted Rules and Scheduled Transactions to separate visible sections on More
- Expanded More with an About Actua page containing version, project purpose,
  developer information, compatibility, upstream credits, independence notice,
  and license information

### Fixed

- Aligned Scheduled Transaction names, status chips, amounts, recurrence details,
  accounts, and overflow actions into consistent responsive rows
- Corrected nested Settings navigation so Android Back returns directly to More
  instead of requiring an extra gesture

## Unreleased

No user-facing changes yet.

## [0.1.0-beta.3] - 2026-09-09

### Added

- Added an Actuali-style backup manager with automatic app-background backups,
  retention, restore, one-tap pre-restore revert, per-backup export, and optional
  mirroring to a user-selected device folder
- Added live sync status, last successful sync, last scheduled background refresh,
  and a manual Sync Now action to Connection & Data
- Added Actual-compatible blank-budget creation plus confirmed server-budget deletion
- Added synced Actual dashboard pages and widget ordering with native Summary,
  Net Worth, Cash Flow, Spending, and Markdown cards

### Changed

- Replaced the previous fixed Reports overview with the dashboard configured in
  Actual Budget, including synced names, ordering, timeframes, filters, spending
  comparisons, and budget-based comparison values
- Kept unsupported synced dashboard widget types visible with a clear availability
  notice instead of silently dropping them

### Fixed

- Kept report aggregation split-aware and excluded transfers, off-budget accounts,
  and income categories where required by Actual's report calculations
- Corrected backup restore and archive display handling for all backup item types

## [0.1.0-beta.2] - 2026-09-08

### Added

- Added a searchable Scheduled Transactions screen with due-state visibility and skip, complete, restart and delete actions
- Added opt-in Android credit-card payment reminders for 7, 5, 3 and 1 days before the calculated due date
- Added a persistent app-wide option to hide reconciled transactions from lists and searches

### Changed

- Sorted credit cards with unpaid balances first, followed by their upcoming payment due date
- Restored the transaction Save action to its fixed bottom-right position instead of moving or duplicating it around the keyboard
- Matched transaction amount entry to the existing compact Budget-tab keypad without an extra amount or Save row above the keys

### Fixed

- Made backspace visually remove every part of calculator expressions such as `100 + 100`, including the pending operator
- Revalidated notification permission at delivery time so revoked access cannot crash a due-date reminder

## [0.1.0-beta.1] - 2026-09-08

First public beta release.

### Added

- Added fixed day-of-month credit-card payment dates while retaining the existing days-after-statement option
- Added fixed-date handling for short months and a compatible fallback offset for older Actua and Actuali builds

### Changed

- Global transaction search now queries complete local history and matches split-child payees, notes, imported descriptions and categories
- Updated rule documentation to reflect the existing editor, supported standard actions and CRDT mutation support

### Fixed

- Recovered missing, malformed and epoch-like sync clocks from the local message-log high-water mark without discarding pending changes
- Removed categories from off-budget standard and split transactions during creation, editing, account changes and rule processing

## [0.1.0-alpha.14] - 2026-09-08

Fourteenth public testing release.

### Changed

- Signed GitHub release APKs with one persistent release key so alpha.14 and later builds can upgrade each other in place
- Added release-signature verification before publishing an APK

### Important

- Earlier releases used temporary GitHub runner debug keys. Back up and synchronize Actua, uninstall the older build once, then install alpha.14. Future persistently signed releases will install as normal updates.

## [0.1.0-alpha.13] - 2026-09-08

Thirteenth public testing release.

### Added

- Added predictable Android-style bottom navigation with per-tab state restoration and reselect-to-top behavior
- Added category-aware Back navigation from View all and Transactions this month to the originating Category Details page

### Changed

- Attached the transaction Save action directly above the amount and split keypads without relying on a fixed floating offset
- Matched the Save action color to the main Transaction action and hid bottom navigation during Add/Edit Transaction

### Fixed

- Kept Plan expense-category and account separators above opaque row backgrounds so they remain visible
- Removed excessive spacing between the transaction Save action and the amount keypad

## [0.1.0-alpha.12] - 2026-09-08

Twelfth public testing release.

### Changed

- Added clearly visible inset separators between Plan categories and between accounts while preserving clean group boundaries
- Kept the transaction Save button visible above both the built-in amount keypad and the Android system keyboard

### Fixed

- Removed the blocked focus overlay shown after tapping the Amount field in Add or Edit Transaction

## [0.1.0-alpha.11] - 2026-09-08

Eleventh public testing release.

### Added

- Added a category-specific Transaction button that preselects the category and returns to its details page after saving, cancelling or pressing Back
- Added direct Budget, Move Money and Auto-Assign actions to the redesigned category summary

### Changed

- Redesigned Category Details around a balance-focused summary card, compact category settings and a unified recent-activity card
- Moved category rename, visibility, deletion and current-month transactions into the top overflow menu
- Reused the expandable budget keypad for Category Details Budget, Move Money and Auto-Assign actions
- Made Plan category separators clearer while retaining the same first-, middle- and last-row behavior as Table view

### Fixed

- Opening a zero transaction or split amount now clears the displayed 0.00 before the first digit is entered

## [0.1.0-alpha.10] - 2026-09-08

Tenth public testing release.

### Added

- Added an account-specific Transaction button that preselects the open account and returns to that account after saving or cancelling
- Added persistent Icons only and Icons and names choices for the bottom navigation bar
- Added a global Current balance summary setting, synchronized with the account-page display menu
- Added account-page controls for showing the current balance summary and notes

### Changed

- Moved From, To and Available to move into the existing budget keypad instead of opening a separate Move Money page
- Made Auto-Assign suggestions expand inside the existing budget keypad
- Kept complete transaction calculator expressions visible while entering amounts, then replaced them with the final result on confirmation
- Renamed Working balance to Current balance throughout the interface
- Made Plan view progress bars, spending details and group totals independently configurable while leaving Table view unchanged

### Fixed

- Allowed large Plan group balances to use enough width instead of being clipped to a minus sign

## [0.1.0-alpha.9] - 2026-09-07

Ninth public testing release.

### Changed

- Rebuilt Move Money as a fixed full-page flow with From and To category selectors, balances, and a swap action
- Added an inline Move Money amount cursor and prevented confirmation when the amount exceeds the source balance
- Unified amount-entry keyboards around a compact equal-size grid with addition, subtraction, clear, decimal/sign, and backspace controls
- Removed multiplication, division, and separate equals controls from budgeting keypads
- Kept the compact Material key styling while standardizing the four-row keypad layout

## [0.1.0-alpha.8] - 2026-09-07

Bugfix testing release.

### Fixed

- Replaced the transaction editor's bottom action row with a fixed extended Save button
- Moved Cancel to the top-left close control and Edit-mode Delete to the top-right
- Kept transaction fields scrollable above the floating Save button

## [0.1.0-alpha.7] - 2026-09-07

Seventh public testing release.

### Changed

- Anchored the Plan Ready to Budget amount left and its label right
- Matched group-total amount typography to category balance amounts
- Kept the full category details page inside the app's safe content bounds
- Made recent category transactions open the shared view mode with Edit and Delete actions
- Slimmed every calculator key vertically and forced calculator sheets to open fully expanded
- Removed calculator drag handles, added compact close controls, and replaced budget action glyphs with Material icons
- Replaced the center Add tab with a dedicated Transactions tab and date-grouping controls
- Added an adaptive Material `+ Transaction` button to Budget, Accounts, Transactions, and Reports
- Replaced full-width transaction editor actions with compact Save, Delete, and Cancel buttons
- Hid the fixed Category field from transfer entry while preserving transfer behavior

## [0.1.0-alpha.6] - 2026-09-07

Sixth public testing release.

### Changed

- Replaced transaction cleared-state letters with compact green or gray check controls without changing amount alignment
- Transaction taps now open a read-only detail sheet with explicit Edit and Delete actions
- Global search transaction results now use the same detailed row and view flow as All Accounts
- Restored the compact single-line Ready to Budget overview in Plan view
- Aligned Table overview amounts and category balance pills to the same column anchors used by Plan view
- Unified transaction, Budget, To Budget, and Move Money calculators around one compact equal-size Material key grid
- Replaced Budget entry headings and separate amount labels with a focused inline amount and cursor
- Moved category details to a dedicated full-screen view with centered summaries and denser auto-assign choices
- Limited the To Budget pressed state to its rounded amount pill instead of the full overview cell

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

[0.1.0-alpha.1]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.1
[0.1.0-alpha.2]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.2
[0.1.0-alpha.3]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.3
[0.1.0-alpha.4]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.4
[0.1.0-alpha.5]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.5
[0.1.0-alpha.6]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.6
[0.1.0-alpha.7]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.7
[0.1.0-alpha.8]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.8
[0.1.0-alpha.9]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.9
[0.1.0-alpha.10]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.10
[0.1.0-alpha.11]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.11

[0.1.0-alpha.12]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.12
[0.1.0-alpha.13]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.13
[1.0.0-beta.1]: https://github.com/azimul-kabir/actua/releases/tag/v1.0.0-beta.1
