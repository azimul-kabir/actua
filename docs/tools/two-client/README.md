# Two-client transaction check

The second acceptance criterion of [#663](https://github.com/azimul-kabir/actua/issues/663): make the
same transaction edits in Actual and in Actua against one server, then compare the synced rows. The
[`two-client-parity`](../../../.github/workflows/two-client-parity.yml) workflow runs it on GitHub:

1. Starts `actualbudget/actual-server:26.9.0` (pinned by digest) and bootstraps it with a throwaway
   password.
2. `upstream.mjs` creates the synthetic **Two-client parity** budget through `@actual-app/api@26.9.0`:
   accounts `Upstream Checking/Savings/Brokerage` and `Actua Checking/Savings/Brokerage` (Brokerage is
   off budget), categories `Parity Food`/`Parity Rent` and payees `Parity Store`/`Landlord`/`Market`.
   It then makes every scenario's edit in the `Upstream …` accounts through loot-core, using the same
   paths the PWA uses: new rows go through `transactions-batch-update` (split children are built
   like `makeChild`), and edits and deletes go through the shared `updateTransaction`/`deleteTransaction`
   helpers (`api.updateTransaction`, `api.deleteTransaction`).
3. `TwoClientTransactionParityTest` (an instrumentation test, skipped unless the workflow passes
   `twoClientServerUrl`) downloads the budget on an API 35 emulator, makes the same edits through
   `ActualTransactionFormService` and `ActualTransactionWriter` in the `Actua …` accounts, and syncs
   with `ActualSyncClient`.
4. `compare.mjs` downloads the budget into a fresh loot-core client, syncs, and compares each
   scenario's raw `transactions` rows between the two sides, with account, payee, category, parent
   and transfer ids replaced by what they point at. Any row difference fails the job. Differences in
   which columns carry CRDT messages are listed but don't fail it, because Actua's inserts also write
   explicit null and zero cells that Actual leaves out (TRANSACTIONS_PARITY.md §1, **Intentional**).
   The report goes to the job summary and the `two-client-parity` artifact, with the normalized rows
   (`two-client-rows.json`).

Scenario *n* writes on 2026-08-*n* and may move its rows to 2026-09-*n*, so the day of the month
identifies it. The scenario list lives in `common.mjs`; `upstream.mjs` and the Kotlin test must make
the same edits in the same order.

| # | Edit |
| --- | --- |
| 1 | Create an expense |
| 2 | Edit an expense (amount, payee, category, notes, date, cleared) |
| 3 | Create an income with no category |
| 4 | Create a transfer between on-budget accounts |
| 5 | Edit a transfer leg (amount, notes, date, cleared) |
| 6 | Create an on-budget → off-budget transfer with a category |
| 7 | Delete a transfer leg |
| 8 | Change a transfer leg to a regular payee |
| 9 | Change an expense into a transfer |
| 10 | Create a split with an inherited and an own child payee |
| 11 | Edit a split parent (payee, date, cleared) |
| 12 | Delete a split parent |
| 13 | Create a zero-amount expense |
| 14 | Toggle cleared on an expense |
| 15 | Toggle cleared on a transfer leg |
| 16 | Move an expense to another on-budget account |
| 17 | Move a categorized expense to an off-budget account |

## Limits

- The upstream side drives loot-core directly, not the PWA's screens. It covers the server handlers
  and shared split/transfer helpers the PWA calls, but not editor-only defaults (for example the date
  a new mobile draft starts with); those are compared by reading the code in TRANSACTIONS_PARITY.md.
- The new-split flow is modelled as the end state of a mobile split (parent payee kept, children from
  `makeChild`), not by replaying `splitTransaction`, which clears the parent payee first.
- The `sync-transfer-date` preference is left at its default (off), and no rules or schedules exist.

## Running it elsewhere

Start an `actual-server` 26.9.0, then from this directory:

```sh
npm ci
TWO_CLIENT_SERVER_URL=http://localhost:5006 node upstream.mjs
./gradlew connectedInstrumentedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.azimulkabir.actua.data.budget.TwoClientTransactionParityTest \
  -Pandroid.testInstrumentationRunnerArguments.twoClientServerUrl=http://10.0.2.2:5006 \
  -Pandroid.testInstrumentationRunnerArguments.twoClientPassword=two-client-parity   # from the repository root
TWO_CLIENT_SERVER_URL=http://localhost:5006 node compare.mjs
```

Use a throwaway server: the scripts bootstrap it and create a budget on it.
