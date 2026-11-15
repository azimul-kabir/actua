# AppNavigation state extraction plan (#1378)

First deliverable for [#1378](https://github.com/azimul-kabir/actua/issues/1378).
This is a behavior-preserving plan, not a new invalidation architecture. Inventory
checked against `AppNavigation.kt` at commit
`f517e1392d7c512a08b079ff0d355486fa8b507f`; names below are source anchors rather
than line numbers, which will move during extraction.

## Current ownership and consumers

| State / source anchors | Destinations and other consumers | Lifetime to preserve / proposed boundary |
| --- | --- | --- |
| `repositoryVersion`, `repository`, `activeBudgetId`, `budgetReplacementInProgress`, `budgetReplacementCompleted`, local budget options | Every destination; Connection, recovery and budget selection replace the repository | Root owner; retain the replacement blocker through the first frame of refreshed data |
| `dataVersion`, sync signal generations, `syncStatus`, `sessionExpired`, foreground sync effect | All read projections, Connection, banners, reminders and widgets | Root invalidation and activity/composition effects; no per-domain counters |
| `destination`, `selectedTab`, `detail`, `tabSwitchJob`, `tabSnapshots`, `rootRequests`, `tabStateHolder` | Floating tabs, root/detail switch, tab re-selection, back handling, every flow | Root coordinator; keep saved destinations separate from immediate tab selection and preserve per-tab saved-state ownership |
| `errorMessage`, `transactionImpactCues`, `mutate*`, `categoryAvailableCents`, `impactCues` | All mutating screens, root Snackbar and category impact feedback | Composition-owned mutation/feedback holder; root retains Snackbar rendering and invalidation callback |
| `budgetMonth`, `loadedBudgetMonth`, `shownBudgetMonth`, `activeBudgetCategory`, `reopenBudgetCategory`, `transactionsReturnCategory`, `editingAutomationCategory` | Budget, category details, transaction editor/register, Budget Automation, Home | Budget holder for selection; leave shared read projections at root initially; preserve fallback month while loading |
| `rootLists`, account/category/payee choices, `transactions`, `narrowedTransactions`, `transactionStatusFilter`, search loader | Home, Budget, Transactions, Accounts, editor, rules/schedule pickers, reports | Shared read models stay rooted; status/filter ownership moves with register only after every consumer is mapped |
| `transactionAccount`, `transactionCategory`, `transactionMonth`, `transactionSearch`, `lastOpenAccountId`, refresh flags, `reconcileOpen` | Transactions tab and account/category registers, reconciliation, search, new-bank-transaction markers | Register flow holder, with explicit root navigation and open-account notification callbacks |
| `editingTransaction`, `newTransactionType`, `transactionPrefill`, `addOrigin`, `editorReturnsTo*`, `editorReturnCategory`, `keptReceiptPhoto`, receipt versions | Add/Edit Transaction entered from FAB, shortcut, account, category, reports or statement | Editor holder; retain transient transaction/photo state and saveable origin flags without adding persistence |
| `activeDrillDown`, `searchReturnsToReports`, `reportSnapshot`, its version/month-start keys, `requestedReportPageId` and request counter | Reports tab/detail, Search, Home favorite reports | Keep repository-keyed report cache shared; move return/request state with register/destination adapters, preserving request consumption |
| `editingScheduleId`, `scheduleReturnsTo*`, `billsCalendarReturnsToSchedules`, `scheduleRuleId` | Bills tab, calendar, Schedules, New/Edit Schedule, Rules, transaction schedule links | Schedule flow holder; explicit return transitions rather than independent destination-local copies |
| `creditCardsReturnToBills`, `statementsAccountId`, `statementsReturnToTransactions`, `selectedStatement`, `editorReturnsToStatementDetail`, loan actions | Accounts, Credit Cards, statement list/detail, editor and Loans | Account-detail flow holders; keep transient statement object distinct from saved account/return identifiers |
| `homeLayout`, `homeCollapsed`, `homeMonthStart`, `homeHolidays`, favorites, greeting preferences/state | Home, Customize Home, Budget favorites, Reports, schedules and Settings | Focused Home/preferences owners; retain budget-ID keys and cross-consumer month-start/holiday dependencies |
| Display formats, appearance/dynamic color, privacy, list/budget options, `tabBarLayout`, `tabBarScroll`, notification settings/launchers | Settings/Manage, all rendered screens, FAB/bar, Activity appearance callbacks | Settings coordinator plus explicit shared display inputs; launchers remain composed at a stable root call site initially |
| Provider configured/discovery/loading flags, GoCardless requisition/institutions, `enableBankingFlow` | Bank Sync, account linking/creation, credential and authorization flows | Provider flow holder; preserve asynchronous job scope, error sink and repository identity |
| Shared import URIs/text, capture count/request/return flags, `launchRequest` effect | Import Transactions, Review Captured, notifications/widgets/shortcuts, deep-link destination selection | Import holder for flow state; root consumes external requests and owns queue observation/disposal |

This mapping groups related declarations; it does not claim all state belongs to
one screen. In particular, reports, schedules, budget data, favorites and display
preferences cross destination boundaries. Moving their loads into a destination
without tracing consumers could change freshness or lose state on tab switches.

## Extraction contract

Use small Kotlin holders and focused composables in `ui/navigation/`, following
existing patterns. Do not introduce a navigation framework, architecture library,
or one replacement holder containing everything. Keep `AppNavigation` as the
composition root for repository replacement, cross-flow routing, launch requests,
permissions and the floating bar until their contracts are explicitly extracted.

For each moved declaration, preserve its existing `remember`/`rememberSaveable`
choice, keys and scope. A screen-scoped API does not imply destroying its state
when that destination leaves composition: existing state often lives for the
whole navigation composition. Preserve `SaveableStateHolder` and `TabSnapshot`
behavior, process recreation and tab re-selection. Update callbacks/strings when
configuration or repository identity changes; avoid remembering lambdas that
capture an obsolete repository, locale or selected budget.

Keep the single `dataVersion` and its current triggers. Successful local writes,
sync signals and refresh paths must still refresh every current consumer. Domain
counters remain out of scope for the cross-dependencies documented in
[the invalidation audit](DATA_VERSION_INVALIDATION_AUDIT.md), including rule editor
pickers, schedule-owned rules and budget schedule funding. The current async
`rememberLoaded` projections and repository-keyed report cache must retain their
keys and loading/fallback behavior. The older audits describe historical source
shapes; this inventory reflects the current implementation.

## Ordered slices

| Order | Issue | Extraction and completion boundary |
| --- | --- | --- |
| 1 | [#1676](https://github.com/azimul-kabir/actua/issues/1676) | Mutation runner and error/impact feedback state. Move all five `mutate*` variants together; preserve synchronous return values, IO dispatch for async writes, diagnostics, rejection/failure text, and success ordering. Keep `dataVersion` rooted and pass invalidation explicitly. |
| 2 | [#1677](https://github.com/azimul-kabir/actua/issues/1677) | Register/editor state, origins and return transitions, report/search entry context and receipt lifetime. Leave shared repository projections and root tab coordination intact. |
| 3 | [#1678](https://github.com/azimul-kabir/actua/issues/1678) | Budget selection and Home/preferences owners, retaining shared month-start/holiday/favorite inputs and asynchronous loaded-month semantics. |
| 4 | [#1679](https://github.com/azimul-kabir/actua/issues/1679) | Schedule and account-detail flow holders: rule editing, Bills/register returns, statements and loan entry context. |
| 5 | [#1680](https://github.com/azimul-kabir/actua/issues/1680) | Settings destination adapters and distinct provider/import owners. Preserve shared preference propagation, permission ownership, capture observation and external launch consumption. |

Review and merge this design deliverable before implementing slice 1; execute the
extractions in order with a focused PR per issue. Later slices depend on the
reviewed APIs of their predecessors. Do not close #1378 when this document or the
first implementation merges: the remaining slices stay tracked individually.

### First implementation details

The mutation holder receives the existing composition coroutine scope, localized
message inputs, feedback state and callbacks for invalidation/impact reads. It
must not own a second repository, start an application-wide scope or serialize
previously concurrent writes. Repository writers continue to provide CRDT
atomicity and synchronization. Preserve the existing error capture/cancellation
behavior during extraction; any correction found while auditing it needs separate
scope and regression evidence.

For plain async writes preserve `IO action → main-thread invalidation → onChanged`.
For impact writes preserve `IO before snapshot → IO action → invalidation → IO
after snapshot → impact feedback → onChanged`. A rejected/failed action does not
invalidate or call success callbacks; synchronous helpers return the current
Boolean result on the calling thread. Do not widen exception handling to include
callbacks that currently run outside `runCatching(action)`. Move feedback state
without replacing the current single-message Snackbar effect with a queue.

## Validation and evidence

This deliverable changes documentation only: check formatting, local links and
source anchors; no app build, device suite or timing measurements are needed.
Each implementation slice must add meaningful tests for the moved contracts,
run both-flavor build/unit/lint checks as appropriate, and record exact results.

Slice 1 needs regression coverage for success/rejection/exception, exactly-once
invalidation, callback ordering, impact snapshots and sync return values, plus
configuration changes and composition disposal. Subsequent slices need Android
navigation/back, tab restoration, process recreation, keyboard/insets and budget
replacement coverage for their affected flows. Use synthetic budgets only.

Match implementation changes to retained journeys in
[PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md): `AddEditTransactionBenchmark`
and `NavigationBenchmark` for slices 1–2; `BudgetScreenBenchmark` and navigation
for slice 3; account-register and relevant navigation journeys for slice 4;
navigation for slice 5. Use the same physical device/API, variant and data before
and after; record commits, median/p90 and janky frames with interpretation. Do not
claim improvement from fewer lines or emulator timings. If physical hardware is
unavailable, explicitly record that limitation; the installed API 28 and API 37
emulators can validate behavior but cannot supply the requested physical baseline.

## Implementation review stack

The five planned slices have implementation PRs, in dependency order:

| Slice | Pull request |
| --- | --- |
| Mutation runner and feedback | [#1682](https://github.com/azimul-kabir/actua/pull/1682) |
| Transaction register/editor state | [#1683](https://github.com/azimul-kabir/actua/pull/1683) |
| Budget selection and Home preferences | [#1684](https://github.com/azimul-kabir/actua/pull/1684) |
| Schedule/account-detail flows | [#1686](https://github.com/azimul-kabir/actua/pull/1686) |
| Settings/provider/import owners | [#1687](https://github.com/azimul-kabir/actua/pull/1687) |

The current extraction deliberately keeps cross-flow routing and shared read
models in the root. Moving state ownership alone does not establish smaller
recomposition scopes or justify narrowing invalidation. Review each slice and
its recorded validation/measurement limitations before merging; the parent issue
remains open until its acceptance criteria are satisfied.
