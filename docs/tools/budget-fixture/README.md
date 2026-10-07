# Budget month parity fixture

Evidence for [#667](https://github.com/azimul-kabir/actua/issues/667): Actual's own budget
spreadsheet decides every month's cells, and Actua's month walk has to reproduce them.

- `generate.mjs` builds two offline budgets in `@actual-app/api@26.9.0`: one envelope, one tracking,
  with the same synthetic accounts, categories and transactions. It sets budget amounts, rollover
  flags and holds through loot-core's own `budget/*` handlers, then reopens each budget so the
  spreadsheet is rebuilt from the stored rows. It writes the rows and every month's cells from
  `api/budget-month` (`to-budget`, `from-last-month`, `last-month-overspent`, `buffered`, totals and
  per-category budgeted / spent / balance / carryover) to
  `app/src/androidTest/assets/budget-parity/upstream-26.9.0.json`, with ids replaced by stable tokens
  so the file is deterministic.
- `BudgetMonthParityFixtureTest` (androidTest, runs with
  `./gradlew connectedInstrumentedAndroidTest`) loads those rows into SQLite and compares
  `ActualBudgetDatabase.fetchBudgetMonth` with Actual's cells, month by month. Differences fail unless
  `KNOWN_DIVERGENCES` lists them with their issue.
- The [`budget-month-parity`](../../../.github/workflows/budget-month-parity.yml) workflow regenerates
  the fixture and fails if the committed copy is out of date; the regenerated file is its artifact.

The budgets cover March–July 2026:

- Overspending with rollover off (it reduces next month's To Budget) and with rollover on (it
  stays in the category).
- A manual hold for next month, and a hold that is set and then reset.
- An income category set to hold automatically for one month (`buffered-auto`).
- A refund, and a split whose lines use two categories.
- A categorised transaction in an off-budget account, which is ignored.
- A deleted transaction.
- A category deleted with its transactions moved into another one (`category_mapping`).
- A hidden category and a hidden group.
- A future month with budgets but no activity, and in the tracking budget, income budgets and
  income rollover.

Run locally with `npm ci && node generate.mjs` from this directory (Node 22.13 or newer, for
`node:sqlite`). Don't regenerate the fixture to make the test pass: a changed expectation means
Actual's behavior changed.
