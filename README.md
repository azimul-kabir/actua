<div align="center">

<img src="artwork/actua-icon.png" alt="Actua app icon" width="128" height="128">

# Actua

**A native Android client for [Actual Budget](https://actualbudget.org/), built with Kotlin and Jetpack Compose.**

</div>

## About this project

Actua is an independent, community-maintained Android project. Its development
was originally based on and informed by [Matt Farrell's open-source Actuali
project for iOS](https://github.com/MattFaz/actuali), whose tested behavior and
implementation remain important references.

Actua connects directly to a self-hosted Actual server. Budgets are downloaded
to local SQLite storage, remain usable offline, and synchronize through Actual's
encrypted CRDT protocol. There is no intermediary account or service operated
by this app.

Actua is not an official release of, affiliated with, endorsed by, or supported
by either the Actuali project or the Actual Budget team.

Actua is developed and maintained by **[Azimul Kabir Apu](https://github.com/azimul-kabir)**
with contributions welcomed from the community.

## Current functionality

- Password connection to an Actual server plus budget creation, download, selection, and confirmed server deletion
- Offline local budget storage and encrypted CRDT synchronization
- Automatic, foreground, post-mutation, and manual sync
- Budget table and availability-focused Plan views, category groups, Source of Fund/Income, monthly amounts, progress bars, and hide/show management
- Account lists, current/cleared/uncleared/reconciled balances, notes, monthly summaries, and full transaction history
- Expense, income, transfer, editable split, edit, clear, and delete transaction flows
- Full-screen searchable account, payee, and category selection with live alphabetical
  results, account balances, transfer grouping, and new-payee creation
- Ready to Assign/To Budget assignment plus category-to-category and category-to-budget money movement
- Calculator-style and conventional amount entry
- Inline transaction calculator expressions with predictable backspace editing that remain visible until confirmation
- Account, category, and group creation plus working contextual actions
- Automatic local backup, restore, retention, pre-restore revert, per-archive export, and optional folder mirroring
- Live sync status with last successful sync and last scheduled background refresh
- Actual-compatible rule listing, editing, CRDT mutations, and transaction
  processing for supported standard conditions and actions
- Scheduled transaction creation and review with lifecycle status, recurring
  schedule actions, and a dedicated repeat-pattern editor
- Persistent hide-reconciled filtering across transaction lists and searches
- Balance-focused category details with shared Budget, Move Money and Auto-Assign keypads, notes, rollover overspending, recent activity and category-preselected transaction entry
- Credit-card limits, billing-cycle metadata, fixed or offset due dates, cycle spending,
  urgency sorting, and opt-in Android payment reminders
- Actual-synced report dashboard pages and widget order, with Summary, Net Worth,
  Cash Flow, Spending, Markdown, Age of Money, Formula, Custom Report, Calendar,
  Crossover, Budget Analysis, Sankey, Balance Forecast, and Monte Carlo widgets
- Configurable display currency, decimals, appearance, start page, account summaries,
  transaction grouping, and Material bottom-navigation labels
- Global search across transactions, accounts, payees, categories, notes, and transfers
- Unified Material You category budgeting with synced targets, target-aware auto-assign, money movement, details, and recent activity
- Expandable inline Auto-Assign and Move Money controls within the category amount keypad
- Account-specific transaction entry, collapsible account summaries and notes, and configurable bottom navigation labels
- Mobile account reconciliation with bank-balance comparison, uncleared review, adjustments, and cleared-transaction locking
- Material You motion for tab changes, detail navigation, searches, and expandable sections
- Android-style per-tab navigation state, root reselect behavior, scroll-to-top actions, and contextual Back restoration

See [BACKEND_PARITY.md](BACKEND_PARITY.md) for the implementation boundary and detailed port status.

## Scope

The goal is behavioral compatibility with Actual Budget and with portable
budgeting behavior proven by Actuali, while retaining a native Android UI built
with Jetpack Compose. Changes in the iOS project can be reviewed and ported over
time, but this is a source-level reimplementation—not shared Swift code or a
byte-for-byte conversion.

Apple-platform integrations are deliberately excluded, including FinanceKit, Apple Wallet, Siri, App Intents, Shortcuts, iCloud, Keychain, and Apple background-task APIs. Android equivalents are used only where they serve the core budgeting workflow, such as Android Keystore and WorkManager.

## Requirements

- Android 9 (API 28) or later
- A reachable self-hosted Actual Budget server
- Android Studio with JDK 11 or later for local builds

## Testing releases

Testing APKs are published on the [GitHub Releases page](https://github.com/azimul-kabir/actua/releases). The current testing release is [Actua 1.0.0-beta.4](https://github.com/azimul-kabir/actua/releases/tag/v1.0.0-beta.4). Download the APK on an Android device, allow installation from the browser or file manager when prompted, and open Actua.

Actua uses the application ID `com.azimulkabir.actua`. Android therefore treats
it as a separate app from the earlier Actuali for Android alpha builds. Confirm
that local changes are synchronized and backed up before removing an older build.

Starting with the first persistently signed release, later GitHub release APKs
can upgrade it in place. Builds published before that transition used ephemeral
GitHub runner debug keys and cannot be upgraded by the new release key. Before
installing the first persistently signed APK, synchronize and back up Actua,
uninstall the older build once, and install the new APK. Keep that installation
for normal in-place upgrades afterward.

## Build and test

```bash
./gradlew assembleDebug
./gradlew testInstrumentedUnitTest lintDebug
./gradlew installDebug
```

The app can then connect from **More → Connection & Data**. Use the complete server URL and password, then create a budget or choose an existing remote budget. The Backups manager can export individual archives and mirror retained backups to a persistent folder selected through Android's system picker.

## Architecture

```text
Jetpack Compose UI
        ↓
ActuaRepository
        ↓
Local SQLite database ← CRDT mutation writers
        ↕
Actual sync client ← encrypted protobuf sync → Actual server
```

Writes are applied locally and represented as Actual-compatible CRDT messages. WorkManager provides Android-native periodic synchronization and backup scheduling.

## Upstream relationship and credits

Actua began as an Android reimplementation based on **[Matt Farrell's Actuali
for iOS](https://github.com/MattFaz/actuali)**. Its product design, tested
behavior, Swift implementation, documentation, and sync work continue to guide
portable behavior. Please use the original repository for the iPhone and iPad
app and direct iOS-specific contributions and issues there. Copyright attribution
from the upstream repository is preserved in this project's license.

Actuali itself builds on **[Actual Budget](https://github.com/actualbudget/actual)**. Portions of the synchronization behavior derive from Actual Budget's MIT-licensed CRDT and loot-core implementations, originally copyrighted by James Long and subsequent contributors.

The original Actuali icon was designed by
**[u/bdownz](https://www.reddit.com/user/bdownz/)**. Actua uses a new,
independently created adaptive icon and does not reuse that artwork.

See [NOTICE.md](NOTICE.md) and [LICENSE](LICENSE) for complete attribution and license terms.

## Contributing

Android bug reports and port-specific contributions belong in this repository. When implementing parity behavior, link the relevant upstream Actuali source, test, issue, or commit where possible. Do not report Android-port problems in the original iOS repository unless the same issue is reproducible in the iOS app.

## License

MIT. This repository contains work derived from Actuali and Actual Budget; their copyright notices are retained in [LICENSE](LICENSE).
