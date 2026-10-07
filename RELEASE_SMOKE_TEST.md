# Actua release smoke-test checklist

Use this checklist for release candidates before promoting a beta or stable build. CI remains the automated gate; this checklist covers device, integration, and user-flow behavior that repository tests cannot fully prove.

## Release identity and install

- [ ] Android CI is green for the exact release commit.
- [ ] Release APK is produced from the intended commit/tag and has the expected version name/code.
- [ ] Since release builds are now R8-minified: exercise PDF statement import/export (pdfbox-android), background sync (WorkManager), and general Compose navigation on the installed release APK, and confirm no `ClassNotFoundException`/`NoSuchMethodException` crashes from shrinking or obfuscation.
- [ ] Release builds now embed a baseline profile: confirm the release APK's cold start and initial navigation feel at least as fast as the previous release's on the primary test device (no regression); a real-device Macrobenchmark run per `docs/PERFORMANCE_BASELINE.md` is preferred where available.
- [ ] APK installs as an update over the previous production-signed Actua build without removing app data.
- [ ] `Actua Test` still installs side-by-side with production Actua and keeps separate app data.
- [ ] Fresh install launches successfully on Android 9+ and the current target Android version.
- [ ] Upgrade install launches successfully with an existing downloaded budget.

## Safety and data

- [ ] Create an independent Actual backup before testing against a real budget.
- [ ] Add a custom HTTP header (e.g. `CF-Access-Client-Id`/`Secret`) on the Connection screen and verify sync/login succeeds against a server that requires it; verify removing the header still works against a server that doesn't.
- [ ] Connect to a self-hosted server over plain HTTP on a private/local address (e.g. `192.168.x.x`, `10.x.x.x`, `localhost`) other than the old hardcoded test IP and verify it succeeds instead of "Cleartext HTTP traffic not permitted".
- [ ] Install a self-signed CA as a user certificate (Settings > Security > Encryption & credentials > Install a certificate, including on GrapheneOS) and connect to a self-hosted server using a certificate issued by it over HTTPS; verify the connection succeeds instead of `Trust anchor for certification path not found`.
- [ ] Connect to a self-hosted HTTPS server whose certificate isn't trusted by Android and verify the "Server certificate isn't trusted" dialog shows host, issuer, validity and SHA-256 fingerprint; trust it and confirm the connection succeeds, then swap the server's certificate and confirm the "Server certificate changed" warning appears before anything is re-trusted.
- [ ] Configure a primary server URL and a fallback server URL where only the fallback's certificate is untrusted; verify the trust prompt shows the fallback server's host/certificate (not the primary's) and trusting it lets login/sync succeed.
- [ ] From Manage → Bank Sync (or Accounts "+" → Set up bank sync), configure a SimpleFIN setup token or GoCardless Secret ID/Key, discover provider accounts, and link one to a new and to an existing account; verify GoCardless's browser consent flow and "check accounts" return correctly, and that Pluggy.ai is shown as not yet supported.
- [ ] Sync a linked bank account twice where the provider re-sends a transaction under a new ID and verify it is not imported again; verify a timed-out download and an account linked through an unsupported provider (e.g. Pluggy.ai) show a specific message instead of "did not return the account".
- [ ] Pull down on Accounts and verify all linked bank accounts sync; pull down on a single linked account's register and verify only that account syncs.
- [ ] With Settings → Experimental → Enable Banking off, verify Bank Sync shows no Enable Banking row and an account already linked to Enable Banking still reports it can't sync in Actua. Turn it on, save an Enable Banking Application ID and private key (verify a wrong key shows the server's error and nothing is kept on the device), choose a country and bank, authorize in the browser, link an existing and a new account, sync, and verify transactions import in exact cents; expire or revoke the consent and verify the account shows "Needs reauthorization". Requires an Actual server with Enable Banking set up.
- [ ] On Connection & data, tap the Budgets section header and verify the budget list collapses/expands with the chevron animating, and other sections (Backups, etc.) are unaffected.
- [ ] Download/select a real self-hosted Actual budget and verify opening balances/category values against Actual.
- [ ] Manual Sync Now completes and the sync status/last-success state updates.
- [ ] Make one harmless edit in Actua, sync, and confirm it appears correctly in Actual.
- [ ] Make one harmless edit in Actual, sync Actua, and confirm it appears correctly in Actua.
- [ ] Verify a transfer remains a paired transfer and balances stay symmetric after sync.
- [ ] Start an OpenID browser authorization, return without completing it, and verify Connection & Data leaves its loading state and remains usable for another attempt.
- [ ] Verify split transaction create/edit and sync on a disposable/test transaction.
- [ ] Confirm demo budget never attempts server sync and can be reset.
- [ ] Create a local backup, export it, restore it, and verify the pre-restore revert path.
- [ ] Create a backup on Android 10 (API 29) and verify it succeeds; import an Actua backup into Actual (web/desktop) and verify Actual opens it without an out-of-sync-migrations error.
- [ ] On a disposable budget also used in Actual, link and unlink a bank account in Actua, sync both clients, and verify Actual keeps syncing without "Update required" and shows the account as linked.
- [ ] Open a budget last uploaded by an older Actual version, sync, and verify Actual still opens and syncs it afterwards.
- [ ] With an unsynced edit, switch to another downloaded budget and back, and verify the edit is still there and then syncs.
- [ ] Expire or revoke the server session (e.g. log out all sessions in Actual), then verify Actua shows the signed-out banner and Sign in again restores sync without losing downloaded budgets or unsynced edits.
- [ ] Reset sync for a disposable budget in Actual, sync Actua, and verify Connection & Data explains that a fresh download is needed, background sync stops retrying, and the local budget is untouched.
- [ ] Upgrade over 1.2.0 with a custom HTTP header configured and verify login/sync still succeed with the header.
- [ ] Create a new budget in Actua, sync, and verify Actual shows its default reports dashboard.

## Budget and category flows

- [ ] Budget opens in the configured default Plan/Table view and month navigation works.
- [ ] Ready to Budget/To Budget, Budgeted and Balance values agree with Actual for the test month.
- [ ] Verify ordinary category progress bars show spending (including carryover) and match Actual/Actuali for a category with carryover, and that goal-only/save-by-date targets still show balance-funded progress.
- [ ] Category rows/cards show a status dot matching the progress bar's color; customize a status color (and reset to defaults) from Settings → Budget → Category status colors and verify it updates everywhere immediately; toggle the "Category status dot" switch off/on and verify the dot stops/resumes composing.
- [ ] On Budget (Plan and Table views), verify only overspent (negative) balances get a colored pill in the Overspent red, positive and fully spent balances sit on a neutral pill, an unassigned zero has no fill, and group totals have no pill; verify the status color picker offers the expanded swatch set.
- [ ] In Settings → Budget, turn on "Color balances" and verify Balance amounts in both Plan view and Table view sit on a pill in the same color as the category's status dot (with the status read out by TalkBack), and that turning it off restores plain text. With a goal category funded above its goal, verify it shows "Funded past goal" while a category exactly at its goal still shows "Goal reached", and that the Funded past goal color can be changed and reset under Category status colors in light and dark themes and at large font scale.
- [ ] Rapidly switch bottom-navigation tabs and confirm the selection indicator updates immediately, each tab keeps its scroll position, and Reports shows a loading state then data without freezing the UI.
- [ ] Edit a category budget amount with the keypad and verify exact-cent persistence.
- [ ] Move money category-to-category and category-to-budget and verify both sides.
- [ ] Move money into an overspent category from one with enough balance and verify the Cover button fills exactly the overspent amount (capped at the source's balance) and the target ends at zero.
- [ ] Category details show notes, recent activity, rollover setting and target information correctly.
- [ ] Auto-Assign works for a supported target and produces the expected amount.
- [ ] Hide/unhide category/group and verify the state survives refresh/relaunch.
- [ ] A category with an active budget-automation goal shows its progress bar filling toward the goal as it's funded, and dropping when the goal amount is spent; a category without a goal still shows plain spend-down progress.
- [ ] Fund a multi-month "Have amount by a date" or "Cover scheduled transaction" target partway and verify the progress bar reflects overall goal progress, not just the current month's installment; verify the progress bar reflects the goal immediately after saving, without needing a whole-budget "Apply Templates" run first.
- [ ] Budget toolbar filter chips (Overspent, Underfunded, Overfunded, Money Available) narrow the category list correctly and the selection persists after navigating away and back.
- [ ] Star a category from Budget/category details and an account from Accounts; verify both appear in Home's Favorite Categories/Accounts sections and the category also appears when Budget's new Favorites filter chip is enabled (composed with an existing filter chip, not replacing it); unstar and verify both disappear; verify the existing Favourite Categories widget reflects the same category favorites.
- [ ] The budget month selector shows the month label with a small down-chevron (no separate step arrows); tapping anywhere on the row opens the month/year picker, and the tap ripple hugs the label instead of stretching across the toolbar.
- [ ] Switching between "Move to Category"/"Hold for Next Month" and between "Auto-Assign"/"Move Money" in the budget action sheet animates height and content together with no flicker or instant snap; tapping "Details" plays the sheet's hide animation before opening category details.
- [ ] Tap the Ready/To Budget amount (Plan or classic view) and verify the Budget Summary sheet opens already expanded showing "Move to Category" (category selector, amount field, calculator) with no extra tap needed; "Hold for Next Month" and "Reset Hold" remain reachable; on an envelope budget, use "Hold for Next Month" to buffer part or all of the amount, verify it carries to next month, then "Reset Hold" and confirm it's cleared; the hold/reset tiles are unavailable on non-envelope budgets.
- [ ] Manage → Categories → Manage Categories: create/rename/hide a group and a category, delete a category, and move a category to another group; drag a category within its group using the handle (no long-press needed) and verify it persists once on drop (not on every row crossed) and auto-scrolls near the list edges; verify a failed persist reverts the drag.
- [ ] Manage → Categories → Reorder Groups: drag a group to a new position with the up/down buttons as a non-drag alternative; verify the new order persists and syncs, and the income group's position stays fixed.
- [ ] Manage → Categories → automation editor: open a category's Budget Automation page and verify the "Automations" list (Fixed amount, Cover schedule, Save by date, % of income, From history, Refill to cap, Whatever is left) and "Options" section (Balance cap, Long-term goal) each allow at most one entry; set a Balance cap and a Refill to cap together and verify Refill to cap tracks the Balance cap amount instead of taking its own; verify Fixed amount's period (day/week/month/year), Save by date's repeat/early-spending options, and Cover schedule's savings mode and schedule picker (select/change/clear) all save and reload correctly, including the note field on each automation.
- [ ] In Actual, add `#template 50` and `#template up to 100` notes to two categories, sync, preview and apply templates in Actua, and verify the budgeted amounts and the underfunded/overfunded colors match Actual; repeat with the budget set to hide decimals and verify whole-unit amounts.
- [ ] On Budget Table view at the largest font size and on a narrow screen, verify Budgeted, Spent and Balance amounts shrink to fit with their decimals visible instead of being clipped.
- [ ] Set a Cover schedule or From history (average) automation's signed increase/decrease adjustment, save, and verify it reloads correctly; set % of income to a specific income category (not just available funds/all income), save, reopen the editor and verify the selected category round-trips instead of reverting to the default.
- [ ] Budget screen overflow menu → "Copy last month's budget": verify it copies the previous month's budgeted amounts into visible expense categories (and visible income categories on a tracking budget) for the selected month, and leaves hidden categories/groups unchanged.
- [ ] With "Show warnings" enabled in Settings → Budget, verify the Budget screen shows an uncategorized-transactions banner alongside the overspent-categories banner when the selected month has uncategorized transactions, and both banners disappear when "Show warnings" is toggled off.

## Transactions and accounts

- [ ] On the single-page Add transaction form, add an expense, an income (via the sign toggle) and a transfer (by picking an account as the payee); edit each and verify the sign and payee choice round-trip.
- [ ] In an unsaved transfer draft, use Reverse transfer and verify its source and destination accounts swap; save it and verify the resulting paired transfer remains balanced after sync.
- [ ] After saving a categorized transaction, verify the confirmation cue shows that category's current available balance.
- [ ] Pull down on Transactions and verify the list refreshes without leaving the tab or changing any transaction.
- [ ] Create a new account and pick a non-default type from the picker; verify it syncs with the correct type. Use "Change account type" on an existing account and verify the change persists and syncs.
- [ ] On the Accounts screen, drag an account to a new position using its drag handle and verify the new order persists and syncs.
- [ ] On a budget with Actual's experimental account groups configured (created from the web/PWA), verify the Accounts screen displays on/off-budget accounts nested under their group headers in the group's sort order, with an ungrouped bucket for accounts outside every group; on a budget without account groups, verify accounts still show as a flat list.
- [ ] Add a transaction from an Account Detail screen's "+" and verify that account is preselected in the editor; open the category picker and verify each category shows its current available balance.
- [ ] Sync a bank transaction whose amount, date and account match an existing manually entered transaction and verify it reconciles into the manual one instead of creating a duplicate.
- [ ] View an off-budget account's transactions with no category set and verify they are not shown or filtered as "Uncategorized" (in the account register or the Transactions tab's Uncategorized status chip).
- [ ] Close an account with no transactions and verify it is deleted; close one with a balance and verify the balance must move to another open account as a "Closing account" transfer dated today; force close another and verify its transactions are removed and the other transfer legs remain; reopen a closed account; verify all of it matches Actual after sync.
- [ ] Rename and change the type of one of two accounts with the same name and verify only that account changes.
- [ ] Open Reorder Accounts and verify it shows On budget, Off budget and Closed sections plus account groups, each collapsible, with each account's position number; reorder an account and verify the order persists and syncs.
- [ ] Collapse a category group on Budget and an account section on Accounts, switch tabs and return, and verify both stay collapsed.
- [ ] Merge a payee and a category in Actual, sync, and verify transactions on the merged-away payee/category count toward the target in reports and filters; edit one of those transactions (e.g. toggle cleared) and verify its payee and category are not cleared.
- [ ] Add and edit a split transaction.
- [ ] In a split, verify the split-line payee picker offers no `Transfer: …` entries; leave one line's payee blank, edit only the line amounts and verify that line stays payee-less, then change the parent payee and verify the blank line takes the new payee.
- [ ] With two accounts sharing a name and a category name repeated in two groups, verify the pickers show disambiguated labels and that a saved transaction, a transfer and bulk Categorize/Move land on the picked account and category in Actual.
- [ ] Typing `#` (or deleting characters) in a transaction note while suggestions are showing keeps the on-screen keyboard open without flicker.
- [ ] Long-press a transaction to enter selection mode with it pre-selected (there is no separate app-bar Select button); select several more and verify the floating selection bar's always-visible Categorize and Label icon actions apply to every selected transaction, and that Edit (single selection), Mark cleared/uncleared, Delete (with confirmation naming the count), Move, Link to schedule and Unlink schedule are available from the 3-dot overflow menu.
- [ ] From the overflow menu, use "Duplicate" and verify an unlinked copy is created immediately without opening the editor.
- [ ] Duplicate a cleared transaction, a split and a transfer (single and bulk) and verify every copy, including split lines and both transfer legs, is uncleared and unreconciled and the account's cleared balance is unchanged.
- [ ] Select two transactions with the same account and amount (for example a bank import and the same purchase entered by hand), merge them, and verify one transaction remains: the bank import is kept and its empty payee, category and notes are filled from the other, in the same sync batch; verify Merge is only enabled for a valid pair and asks to confirm when a row is reconciled.
- [ ] Add a transaction by hand in Actua and sync to Actual: verify its Imported payee column is empty there, and that an imported-payee rule does not match it but still matches a bank-synced one.
- [ ] From the overflow menu, open "View schedule" on a linked transaction, then back/save/delete on the schedule and verify you return to Transactions rather than Schedules or Bills calendar.
- [ ] Transactions screen status filter chips (Uncategorized, Uncleared, Cleared, Reconciled) each narrow the list correctly and can be combined/cleared.
- [ ] Enable "hide reconciled transactions", then select the Reconciled status chip in an account's transaction list and verify reconciled transactions appear (not an empty "No unreconciled transactions" state); verify the empty-state message matches whichever status chip is active.
- [ ] Pick an explicit category in Add Transaction before typing a payee that matches a rule setting a different category, and verify the explicit choice is preserved; leave the category empty and verify the rule still fills it in.
- [ ] Payee search filters character-by-character across normal payees and transfer accounts.
- [ ] Find nearby payees requests foreground permission only after explicit use and normal search remains available on denial/failure.
- [ ] Indoors, Find nearby payees succeeds from a recent valid fix or an enabled network/fused source when GPS alone cannot obtain a fix.
- [ ] Payee locations created in Actual Budget sync into Actua and appear within 500 metres without being recorded again in Actua.
- [ ] With recording enabled, save an ordinary transaction and verify its location appears under Manage → Settings → Privacy → Payee Locations.
- [ ] Verify transfer and blank payees never record a location and same-payee samples within 500 metres are deduplicated.
- [ ] Delete one saved location, then clear all for a payee, sync, and verify the tombstones are reflected in Actual.
- [ ] Global search finds transactions, accounts, payees, categories, notes and transfers.
- [ ] In Transactions search, enter an amount with cents (`42.50`) and a whole number (`42`) and verify they match the absolute amount (including expenses and split lines), and enter a date in the budget's date format (full, short year and day-month) and verify that day's transactions appear.
- [ ] `#tag` renders with its configured Actual color consistently across the main Transactions tab, account lists, transaction detail, search results and category recent-activity.
- [ ] Typing `#` in a transaction note offers matching/creatable tag suggestions; selecting one inserts it correctly and syncs.
- [ ] Type a recognized `#tag` directly into a transaction's notes field (without using the suggestion list) and verify it highlights with the same pill background used by saved tag chips, not just colored text, before the transaction is saved.
- [ ] Manage → Tags: create, edit (color/hidden), rename and delete a tag; the color picker offers Actual web's full tag color set; renaming updates matching transaction-note hashtags.
- [ ] Manage → Tags: tap a tag row's actions menu (3-dot icon) and verify it opens anchored to that row's icon, not at the top of the list.
- [ ] Tapping a managed tag opens its matching transactions, including parent/split notes, with the active filter clearly shown and clearable.
- [ ] Cleared/uncleared/reconciled balances agree with the source budget; account detail shows an always-visible Cleared / Balance / Uncleared row (left/center/right aligned), with Reconciled (and, for credit cards, Available credit / Credit limit) behind the collapsible toggle.
- [ ] Enable "Running balance" from an account's transaction register overflow menu and verify each row shows the correct balance after that transaction (now on the category row, not stacked under the amount), including across transfers, splits and the opening balance, and that the setting persists after navigating away and relaunching.
- [ ] In a transaction row: the cleared/selection indicator sits left of the payee and top-aligns with it; the category chip and notes sit flush left under the payee; the right side shows amount, then running balance (or, in the cross-account Transactions tab, the account name colored like the category chip), then date; the cleared checkmark uses the theme's primary color.
- [ ] Open a transaction's details sheet and verify spacing between the amount and the cleared tick in the header.
- [ ] Reconcile an account using a known bank balance and verify the resulting locked/reconciled rows.
- [ ] Open a reconciled transaction and verify the editor shows a locked Reconciled toggle; verify saving, deleting, bulk Categorize/Move/Delete, and editing a transfer whose other leg is reconciled each ask for confirmation first, and that moving a reconciled transaction to another account leaves it unreconciled after sync.
- [ ] Reconciled-transaction filtering works in account and all-transactions views.
- [ ] Credit-card limit, cycle spending and due-date information render correctly when configured; the account's Billing cycle card shows the current cycle's date range and a "Statement history" link.
- [ ] Tap a credit card's history icon (or the Billing cycle card's "Statement history" link) and verify the last 3 closed statements list with correct due amounts; opening a statement shows the correct transactions for that billing cycle; use the system back gesture/button from the statements list and from a statement's transaction list and verify it returns to the account page instead of skipping to Main.
- [ ] Open an account's dropdown menu and toggle "Show credit card section" off/on; verify the billing cycle/statement history section hides/shows accordingly.
- [ ] Toggle the global "Notes" setting under Display off and verify the notes field is hidden on accounts and budget categories; toggle it back on and verify notes reappear unchanged.

## Rules, schedules and reports

- [ ] Existing supported Actual rules load and can be edited without corrupting their JSON/conditions.
- [ ] Create/edit/delete a supported rule and confirm it syncs correctly.
- [ ] Create a transfer with a rule configured to match its destination account; selecting the destination applies the rule's Notes/Cleared/etc. in the editor, and the saved transfer stays correctly linked.
- [ ] Post or edit a schedule's linked transaction: its own schedule-linked rule still applies, and an unrelated rule linked to a different schedule does not; a rule with a recurring-date condition matching the schedule's own recurrence evaluates correctly instead of never matching.
- [ ] Import a CSV file and a bank-notification transaction whose payee matches a categorizing rule and verify both arrive with the rule's category and payee; bank-sync an account where a rule renames the payee and verify a matched transaction takes the rule's payee and category and no payee is left with the bank's raw name.
- [ ] Create a transfer to an account with a rule matching that account's side (for example setting Notes or Cleared) and verify the rule applies to the other leg.
- [ ] Chain two rules (rename a payee, then categorize by the new payee) and verify a new transaction gets both changes; in Actual, create a rule with a formula amount or template notes and one with split actions, sync, add a matching transaction in Actua, and verify the computed values, exact-cent split lines and parent match what Actual produces, and that Actua's rule editor still loads those rules without changing them.
- [ ] Manually post a recurring schedule on its due date and verify its next occurrence advances exactly once.
- [ ] Use "Post Transaction Today" early (before the due date) and verify the schedule advances to its next due date and the just-completed occurrence still shows as paid on the Bills calendar, rather than looking due again.
- [ ] Create a schedule occurrence out of order or with a future-dated linked transaction, then let the catch-up loop run; verify an earlier due/missed occurrence still posts instead of being masked.
- [ ] Scheduled Transactions list opens and status/date/amount alignment is correct; the status badge sits on its own second row with the account name and repeat/date text (not inline with the title), and the amount stays right-aligned regardless of badge width.
- [ ] Create/edit a recurring schedule and verify recurrence preview.
- [ ] Exercise Post, Post today, Skip next date and linked-history/unlink flows on test data.
- [ ] Manage → Automation exposes separate Bills & Calendar and Scheduled Transactions entries.
- [ ] Bills & Calendar loads scheduled/card-bill entries and filters/actions work; Back returns to the correct origin.
- [ ] Reports dashboard loads its saved ordering and representative report cards render without crashes.
- [ ] Open a saved Actual custom report and verify its donut/interval charts, tapping a bar or segment shows exact values, the date/account filter narrows results, and drilling into a segment lists the contributing transactions and matches Actual.
- [ ] Open a saved `StackedBarGraph` custom report and verify it renders as a per-category stacked bar chart (one color per category, consistent stack order across bars, zero-filled where a category has no activity that interval), and tapping a segment drills down to its contributing transactions.
- [ ] In Reports, filter by category group and toggle include-off-budget; verify saved reports and Income vs expenses update, chips wrap without horizontal scrolling and survive rotation, and a filter matching nothing shows the empty message.
- [ ] Open the Reports overview and verify the Income vs expenses card's monthly in/out/net bars respond to taps and the date/account filter, and that the Income and Expenses rows drill down to transactions that sum to the card's totals in Actual.
- [ ] With a schedule due or upcoming, verify Transactions and an account's register show it as an italic "Upcoming" row that is excluded from balances, and that the "Show upcoming transactions" overflow toggle hides/shows it; with a missed schedule, verify its row stays in the list marked "Missed".
- [ ] Tap an upcoming row and verify Post transaction, Post transaction today, and Skip next scheduled date (recurring) or Mark as completed (one-off) each behave as in Actual after sync.
- [ ] In the schedule editor, tap Edit as rule, change the rule, close it, and verify you return to the schedule and the change syncs to Actual.
- [ ] Delete a transfer, and separately change a transfer to an expense, and verify the other account's leg is removed; edit a transfer and verify the other leg's cleared state and date are unchanged; give an on→off-budget transfer a category and verify it is kept after editing and syncing.
- [ ] Bulk-categorize, duplicate and move the receiving side of a transfer and verify the transfer direction is unchanged.
- [ ] Add Transaction auto-focuses the amount and advances through the payee/category/account pickers; verify Settings → Budget's hide-income-group switch hides the income group in Budget, and the category status dot shows in the Plan view.
- [ ] Check Summary, Net Worth, Cash Flow, Spending and at least one advanced report card against known data.
- [ ] Open a synced dashboard (e.g. "Main") and verify Net Worth/Balance Forecast/Age of Money show a gradient area fill, gridlines and Y-axis labels, and that tapping/dragging shows a tooltip bubble with the value at that point; verify Crossover/Budget Analysis/Monte Carlo behave the same way.
- [ ] On a synced dashboard, verify Cash Flow renders as a tappable grouped income/expense bar chart with a legend (not static text rows), and Calendar supports month navigation, tap-to-reveal per-day tooltips and magnitude-scaled day bars, with each visible month's totals computed from that month alone; verify the app's own Overview page (income vs expenses + saved reports) is unchanged.
- [ ] On a synced dashboard, verify Sankey renders as a real flow diagram (an income node linked to per-category expense nodes, plus a "Remaining" node for unspent income) with tap-to-highlight, not two plain text columns.
- [ ] On a synced dashboard, tapping a Cash Flow period or a Calendar day, and tapping either Spending total, opens the same transaction list sheet already used by Income vs expenses and Custom Report, listing the contributing transactions.
- [ ] Open the Spending widget with data where one side (current vs. compare) is much larger than the other and verify the progress bar renders without crashing.
- [ ] Open the Spending widget on a dashboard with a transfer to an off-budget account and verify that leg is now counted (only same-side transfers stay excluded), matching Actual's PWA/Web totals.
- [ ] Open the Age of Money widget on a dashboard with a transfer to an account outside the widget's account filter and verify it now counts as an expense instead of being dropped.
- [ ] Open the Budget Analysis widget with a category/category-group filter set and verify only matching categories are totaled and the balance carries over from the prior month (rollover), instead of showing an unfiltered non-rolling total.
- [ ] Open the Crossover widget and verify its projected return uses historical-balance CAGR and its expense projection uses the Hampel/median/mean method (not a fixed safe-withdrawal-rate and plain average), matching the PWA.
- [ ] Open the Sankey widget on a dashboard with income from more than one source and verify each source appears as its own node with the date range shown, instead of a single combined income node.
- [ ] Open the Monte Carlo widget and verify it shows a success-rate percentage with a median/10th-percentile ending-balance chart driven by a real simulation, not a fixed deterministic dollar projection.
- [ ] Type a `#tag` in a transaction's Notes field and verify the in-line pill is rounded, matching the saved tag chip style.
- [ ] On a synced dashboard, verify Cash Flow no longer counts a post-dated (future) transaction in the current in-progress month's bar.
- [ ] Open a saved report using a Formula widget with a `query("name")` sub-query that has no saved date range and verify it evaluates over all-time data instead of clamping to the current month; verify a `query()` call naming an unknown query evaluates to 0 instead of matching every current-month transaction.
- [ ] Open a saved custom report grouped by category with "Show uncategorized" both on and off, on data containing transfers, and verify transfers appear in a synthetic "Transfers" row (grouped) and are included/excluded per the toggle, instead of always being hard-excluded.
- [ ] Open a saved custom report using the "Budgeted" balance type and verify it matches the Budget screen's budgeted amounts (via the budget engine) instead of summing transaction spend.
- [ ] Open the Calendar report widget with a saved time frame that doesn't align to whole months and verify no transactions near the range's start/end are dropped, instead widening to whole calendar months like the PWA.
- [ ] Open the Spending widget's "all-time" average range on a budget whose earliest transaction predates the widget's own scoped data and verify the average starts from that true earliest transaction.
- [ ] Open the Age of Money widget with a post-dated (future) transaction in the account/date range and verify it doesn't enter the FIFO pool or produce a zero-day age, matching the widget's other today-capped behavior.
- [ ] Open the Crossover widget with no expense-category selection saved and verify it defaults to every non-income, non-hidden category; save an explicitly empty expense-category selection and verify it is honored as zero expenses instead of reverting to "all categories."
- [ ] Open the Balance Forecast widget on a budget with a scheduled transaction that has already posted and verify it is not double-counted, and that the ending/low balance match Actual.
- [ ] Post a refund/reimbursement transaction to an expense category included in the Crossover widget's expense selection and verify it nets against that month's spend (reducing the projected expense and years-to-crossover) instead of being dropped; verify an uncategorized deposit into an income account is still excluded from the expense total.
- [ ] Open the Balance Forecast widget with a scheduled transaction that has no linked account and with a scheduled transfer between two forecast accounts; verify the unlinked schedule is still counted and the transfer credits the receiving account instead of lowering the projected balance.
- [ ] Open a schedule set to move weekend dates earlier and verify later occurrences still appear in Balance Forecast, the bills calendar and schedule previews.
- [ ] Open the Crossover widget with a sliding-window range saved earlier and verify the range is anchored to today; with a hidden expense category, verify it is excluded unless "show hidden categories" is on.
- [ ] Open the Monte Carlo widget and verify each year's withdrawal is inflation-adjusted from the prior year and results broadly match Actual for the same inputs.
- [ ] Open a saved custom report, turn on its summary and verify the total and average per period match Actual; verify the dashboard card shows no total-sum headline unless the summary is on.
- [ ] Open saved reports set to include the current month/week, and ones using week, quarter and last-30-days ranges, and verify they include the current period and follow today's date.
- [ ] Open saved reports using the Net Payment and Net Deposit balance types and verify they show net amounts matching Actual.
- [ ] Add a transfer between an on-budget account and an off-budget account and verify the Accounts tab's "All accounts" monthly summary counts the on-budget leg as an expense or income (by direction) without double-counting the off-budget leg; verify a transfer between two on-budget accounts, and one between two off-budget accounts, remain excluded as before.

## Android integrations

- [ ] Foreground/manual sync works with network available and reports failures visibly.
- [ ] While a manual sync is in progress, scrolling Transactions/Budget and saving a new transaction remain responsive with no visible stall or SQLITE_BUSY-style hang.
- [ ] Periodic/background sync is scheduled and does not create duplicate schedule postings.
- [ ] Backup WorkManager job remains configured after relaunch.
- [ ] Credit-card reminder permission flow behaves correctly and reminders can be enabled/disabled.
- [ ] Launcher long-press shortcuts open Expense, Income, Transfer and Search in the correct destination.
- [ ] Turn on "Accept transactions from Tasker" on the import screen, send a `com.azimulkabir.actua.action.QUEUE_TRANSACTION` broadcast with the token and an amount from Tasker (or `adb shell am broadcast`), and verify it appears in the import review queue and nothing is written until imported; verify a wrong token, a replaced token or the switch turned off is ignored.
- [ ] Budget Snapshot widget works at 2x2 and compact 3x1/4x1 sizes.
- [ ] Quick Transaction widget works at 2x2 and compact 3x1/4x1 sizes and all three actions open correctly.
- [ ] Favourite Categories and Account Balances widgets configure, refresh and deep-link correctly.
- [ ] Upcoming Schedules widget shows overdue-first ordering, relative due labels (Today/Tomorrow/In N days/N days overdue), respects hide-balances, refreshes after schedule mutations and sync, and tapping it opens Scheduled Transactions.
- [ ] Upcoming Schedules widget switches to its compact layout at the smallest resize size without clipped or overlapping content, and back to the full layout when resized larger.
- [ ] Upcoming Schedules widget's 7/14/30-day period is chosen when the widget is added, persists per widget instance, and can be changed later through the reconfigure entry point.
- [ ] With more than four schedules due in the widget's period, verify the Upcoming Schedules widget scrolls through all of them in the same order as the full layout.
- [ ] Widget amounts respect hide-balances, currency and decimal display preferences.

## UI and navigation

- [ ] Bottom navigation labels the operational hub **Manage** with the Tune icon in labeled and Icons only modes; its separate Settings gear opens general preferences.
- [ ] Manage → Settings and Settings → preference pages animate forward; header Back and Android Back animate in reverse through the same hierarchy.
- [ ] An upgrade with **More** stored as the start page opens **Manage** and persists the migrated value.
- [ ] Bottom navigation preserves per-tab state and root reselect/scroll-to-top behavior.
- [ ] Switch between two local budgets and verify a loading state remains visible until the newly selected budget's data is ready; no rows from the previous budget are presented as the new budget.
- [ ] With two or more downloaded budgets, use the Budget row at the top of Manage to switch budgets: the active budget is checked, the switch shows the same loading state, and Manage then names the new budget.
- [ ] On a fresh install, default visible bottom navigation is Budget | Accounts | Add | Reports | Manage, with Home and Transactions hidden by default; Home (once shown via Customize Tab Bar) shows Ready to Budget, Favorite Categories, Favorite Accounts, Upcoming, This Month, Reports and Recent Activity, each tapping through to its full screen; tapping a favorited category card on Home opens that category's Budget details directly (not just the Budget overview); tapping a favorited account card on Home opens that account's transactions directly (not just the Accounts overview); tapping a favorited report shortcut opens Reports with that report selected; a device previously set to start on Reports opens Home instead after upgrading.
- [ ] On a fresh install, Budget opens in Table view (not Plan) with the Spent column and Group Total shown, and Transactions & Accounts' Conventional amount entry is off, matching the new out-of-the-box defaults; an existing install's already-saved preferences are unaffected by the default change.
- [ ] Settings → Tab Bar: reorder tabs by dragging and with the up/down arrows, and show/hide tabs with the switches; Manage has no drag handle or switch and is labeled Required; a switch disables instead of hiding a tab once only 3 tabs would remain visible, and re-adding is blocked past 5 visible tabs; Restore Defaults returns to the default layout. Enable Reports as a bottom tab and verify it renders as a full tab root (own back-to-Home fallback) alongside the pushed-detail entry points from Home/Manage. Enable the "Add" tab and verify it opens Add Transaction from the bar, is never shown as selected, and the floating Add Transaction button disappears from tab roots and from a drilled-into account/category (with no leftover bottom padding gap); disabling "Add" restores the floating button.
- [ ] Save a transaction and verify the animated balance-impact card (not a snackbar) slides up showing the account balance before → after the save, tinted red for an expense and green for income, then auto-dismisses.
- [ ] Reports dashboard picker: open the dropdown and verify it matches the picker card's width, corner shape and surface color instead of a default Material menu wrapped to its text.
- [ ] Record a transfer between two on-budget accounts and verify Home's This Month card doesn't count it as income or spending, matching Accounts' cash-flow totals for the same period.
- [ ] In Customize Home, set the This Month start day to 27, verify Home shows the date range (27th of the previous month to the 26th) with matching Income, Spent and Net, check a start day of 31 in a short month and February, switch to another budget and verify its setting is separate, restart the app and verify it persists, and set it back to 1 to verify the range label disappears.
- [ ] From Home's app bar, open Customize Home: hide/show an optional section, reorder sections via drag or the up/down arrows, and Restore Defaults; verify the layout survives navigating away and an app relaunch.
- [ ] Android Back behaves correctly from details, search, preferences and transaction flows.
- [ ] From a deep detail screen, repeatedly press/gesture Back and verify the app collapses to the configured Start page tab (falling back to the first visible tab, then Accounts, if Start page's tab is hidden) before the next back press exits the app.
- [ ] From Manage → Settings → Display → Tab Bar and Manage → Settings → Home (Customize Home), press/gesture Back and verify it returns to the Settings page it was opened from (General or Display), not all the way to the Manage root.
- [ ] Add/edit transaction Save button, keypad and selectors remain usable with the software keyboard open.
- [ ] The About screen shows working links to the Actua website, FAQ and Discord server.
- [ ] Light/dark/system appearance and Material You rendering remain legible.
- [ ] On Android 12+, Settings > Display "Material You colors" toggle switches between the app's own brand palette and wallpaper-derived dynamic color, in both light and dark mode; the setting persists across relaunch and the toggle is hidden/inert below Android 12.
- [ ] Settings → Display → Currency picker offers the full expanded currency list (beyond the original ~12); select a newly added currency (e.g. BRL, CHF, RUB, TRY) and verify amounts display with its correct symbol in both normal and symbol-only decimal-hiding mode.
- [ ] Spot-check Budget, Transactions, Reports, Manage/Settings sub-screens, and common dialogs/bottom sheets after the design-system pass: consistent corner rounding and spacing, no visual regressions from the previous release, and paid/cleared/due-soon status colors remain legible in both themes.
- [ ] Confirm the default (non-Material You) theme shows the brighter violet/orchid palette in both light and dark mode, with success/warning accent colors still legible against it.
- [ ] No obvious clipping, blank space, overlapping text or inaccessible actions on the primary test device.
- [ ] Typing in the Transactions search field and toggling row selection feel responsive with no visible lag on a large transaction list; switching Budget between Plan/Table view and scrolling through many category groups feels smooth with no stutter at income/Plan/Table section boundaries.
- [ ] Budget category details, Accounts list scrolling (including a budget with many credit-card accounts), and opening Add/Edit Transaction's payee/category/account pickers all remain responsive while typing.

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
