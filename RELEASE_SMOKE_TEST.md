# Actua release smoke-test checklist

Use this checklist for release candidates before promoting a beta or stable build. CI remains the automated gate; this checklist covers device, integration, and user-flow behavior that repository tests cannot fully prove.

## Release identity and install

- [ ] Android CI is green for the exact release commit.
- [ ] Release APK is produced from the intended commit/tag and has the expected version name/code.
- [ ] APK installs as an update over the previous production-signed Actua build without removing app data.
- [ ] `Actua Test` still installs side-by-side with production Actua and keeps separate app data.
- [ ] Fresh install launches successfully on Android 9+ and the current target Android version.
- [ ] Upgrade install launches successfully with an existing downloaded budget.

## Safety and data

- [ ] Create an independent Actual backup before testing against a real budget.
- [ ] Download/select a real self-hosted Actual budget and verify opening balances/category values against Actual.
- [ ] Manual Sync Now completes and the sync status/last-success state updates.
- [ ] Make one harmless edit in Actua, sync, and confirm it appears correctly in Actual.
- [ ] Make one harmless edit in Actual, sync Actua, and confirm it appears correctly in Actua.
- [ ] Verify a transfer remains a paired transfer and balances stay symmetric after sync.
- [ ] Verify split transaction create/edit and sync on a disposable/test transaction.
- [ ] Confirm demo budget never attempts server sync and can be reset.
- [ ] Create a local backup, export it, restore it, and verify the pre-restore revert path.

## Budget and category flows

- [ ] Budget opens in the configured default Plan/Table view and month navigation works.
- [ ] Ready to Budget/To Budget, Budgeted and Balance values agree with Actual for the test month.
- [ ] Edit a category budget amount with the keypad and verify exact-cent persistence.
- [ ] Move money category-to-category and category-to-budget and verify both sides.
- [ ] Category details show notes, recent activity, rollover setting and target information correctly.
- [ ] Auto-Assign works for a supported target and produces the expected amount.
- [ ] Hide/unhide category/group and verify the state survives refresh/relaunch.

## Transactions and accounts

- [ ] Add expense, income and transfer transactions.
- [ ] Add and edit a split transaction.
- [ ] Payee search filters character-by-character across normal payees and transfer accounts.
- [ ] Find nearby payees requests foreground permission only after explicit use and normal search remains available on denial/failure.
- [ ] With recording enabled, save an ordinary transaction and verify its location appears under More → Privacy → Payee Locations.
- [ ] Verify transfer and blank payees never record a location and same-payee samples within 500 metres are deduplicated.
- [ ] Delete one saved location, then clear all for a payee, sync, and verify the tombstones are reflected in Actual.
- [ ] Global search finds transactions, accounts, payees, categories, notes and transfers.
- [ ] Cleared/uncleared/reconciled balances agree with the source budget.
- [ ] Reconcile an account using a known bank balance and verify the resulting locked/reconciled rows.
- [ ] Reconciled-transaction filtering works in account and all-transactions views.
- [ ] Credit-card limit, cycle spending and due-date information render correctly when configured.

## Rules, schedules and reports

- [ ] Existing supported Actual rules load and can be edited without corrupting their JSON/conditions.
- [ ] Create/edit/delete a supported rule and confirm it syncs correctly.
- [ ] Scheduled Transactions list opens and status/date/amount alignment is correct.
- [ ] Create/edit a recurring schedule and verify recurrence preview.
- [ ] Exercise Post, Post today, Skip next date and linked-history/unlink flows on test data.
- [ ] Bills calendar loads scheduled/card-bill entries and filters/actions work.
- [ ] Reports dashboard loads its saved ordering and representative report cards render without crashes.
- [ ] Check Summary, Net Worth, Cash Flow, Spending and at least one advanced report card against known data.

## Android integrations

- [ ] Foreground/manual sync works with network available and reports failures visibly.
- [ ] Periodic/background sync is scheduled and does not create duplicate schedule postings.
- [ ] Backup WorkManager job remains configured after relaunch.
- [ ] Credit-card reminder permission flow behaves correctly and reminders can be enabled/disabled.
- [ ] Launcher long-press shortcuts open Expense, Income, Transfer and Search in the correct destination.
- [ ] Budget Snapshot widget works at 2x2 and compact 3x1/4x1 sizes.
- [ ] Quick Transaction widget works at 2x2 and compact 3x1/4x1 sizes and all three actions open correctly.
- [ ] Favourite Categories and Account Balances widgets configure, refresh and deep-link correctly.
- [ ] Widget amounts respect hide-balances, currency and decimal display preferences.

## UI and navigation

- [ ] Bottom navigation preserves per-tab state and root reselect/scroll-to-top behavior.
- [ ] Android Back behaves correctly from details, search, preferences and transaction flows.
- [ ] Add/edit transaction Save button, keypad and selectors remain usable with the software keyboard open.
- [ ] Light/dark/system appearance and Material You rendering remain legible.
- [ ] No obvious clipping, blank space, overlapping text or inaccessible actions on the primary test device.

## Release/distribution

- [ ] README version/download badges and release notes match the release being published.
- [ ] CHANGELOG contains the release changes and user-facing safety notes where relevant.
- [ ] GitHub release contains the expected signed APK and checksum.
- [ ] Obtainium can discover the new prerelease/release as intended.
- [ ] F-Droid verification workflow runs only after the Android Release workflow succeeds.
- [ ] Discord release notification/changelog is posted once.

## Sign-off

Record the release candidate, commit SHA, device/Android version, Actual server version, tester, date, failures found, and whether each failure blocks release.

A release should not be promoted to stable while any safety/data-integrity item is unresolved. Beta releases may carry known non-critical UI issues only when they are documented and do not risk budget data.