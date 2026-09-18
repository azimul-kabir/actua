# Transactions and Accounts performance (#326)

Part of the [performance & UI smoothness initiative](https://github.com/azimul-kabir/actua/issues/316),
following the recomposition audit (#322), Budget's LazyList fixes (#323), the mutation
architecture audit (#324), and the invalidation-scoping fixes (#325) — all of which already
touched `TransactionsScreen.kt`/`AccountsScreen.kt` and are not re-covered here; see
`docs/RECOMPOSITION_AUDIT.md`, `docs/BUDGET_LIST_PERFORMANCE.md`,
`docs/MUTATION_ARCHITECTURE_AUDIT.md`, `docs/DATA_VERSION_INVALIDATION_AUDIT.md`.

## Findings and fixes

1. **`DateFormats.kt`'s `formatDate`/`parseStoredDate` allocated a fresh `DateTimeFormatter` and
   two fresh `Regex` objects on every call** — the same class of problem #323 fixed for
   `MoneyFormatter`'s `NumberFormat`, just in a different file the prior audit didn't cover. This
   runs once per transaction row, per recomposition, while scrolling (`formatTransactionDate` is
   called from `TransactionRow`, the sticky date header, transaction details, and the
   reconciliation row). Hoisted the formatters and regex patterns to module-level `val`s (all
   immutable/thread-safe, so this is safe unlike the `NumberFormat` case which needed a
   thread-local cache).
2. **Toggling one transaction's selection recomposed every visible row, not just the tapped
   one.** All rows read the same `selectedIds: Set<String>` state directly
   (`transaction.id in selectedIds`), so any single toggle invalidated every currently-composed
   row's scope. Wrapped the per-row membership check in `remember(transaction.id) { derivedStateOf { transaction.id in selectedIds } }`
   — `derivedStateOf` only reports a change (and triggers recomposition) for the row(s) whose
   boolean actually flipped, not every row that merely read the same underlying set.
3. **`ReconcileAccountScreen`'s `uncleared` filter, and its call site's account filter, ran
   unmemoized in the composable body.** `uncleared = transactions.filter { !it.cleared && !it.reconciled }`
   re-ran on every recomposition of the reconciliation screen — including every digit typed into
   the bank-balance calculator. The call site (`transactions.filter { it.account == account.name }`)
   had the same issue one level up. Both wrapped in `remember`.

## Already verified fine (no changes needed)

- **`accountRunningBalances`**: a single `O(n)` reverse pass over `allTransactions`, correctly
  memoized (`remember(allTransactions, accountName, showRunningBalance)`), skipped entirely when
  the running-balance toggle is off. Not a re-sum-per-row pattern.
- **Search debounce**: `LaunchedEffect(search, transactions, searchTransactions) { delay(200); searchTransactions(search) }`
  is a correct trailing-edge debounce — keying the effect on `search` means Compose cancels the
  prior coroutine (whether mid-delay or mid-query) on every keystroke, so rapid typing cannot
  fire overlapping search queries.
- **List item keys**: all `items`/`itemsIndexed` calls in both files already use stable,
  non-index keys (verified in #322, rechecked here — no regressions).

## Not changed (lower priority / out of scope)

- `rememberActualTagColors(transactions)` keys its (already IO-dispatched, off-main-thread) tag
  color fetch off the whole `transactions` list, so it re-triggers a DB read on every
  `dataVersion` bump even when unrelated to tags. Doesn't block the first frame since it's async;
  flagged as a minor follow-up candidate, not fixed here.

## Acceptance criteria

- [x] Transaction edits update affected UI without unnecessarily refreshing unrelated screens —
      covered by #325's invalidation-scoping work; nothing Transactions/Accounts-specific found beyond that.
- [x] Transfers/balance calculations do not introduce avoidable scroll-time work — verified
      `accountRunningBalances` is already correct; fixed the date-formatting allocation that did
      run on every row.
- [ ] Large transaction lists scroll smoothly / filter changes don't freeze the UI / entering-leaving
      an account feels immediate — the fixes above should measurably help (especially #1, which
      ran per row on every scroll frame), but confirming "smooth"/"immediate" needs a real Pixel 8
      run of the `#321` Macrobenchmark suite (`TransactionsScreenBenchmark`,
      `AccountsScreenBenchmark`) once real-device baseline numbers exist, tracked under #330.
