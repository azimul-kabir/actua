# Rules parity fixture

Evidence for [#669](https://github.com/azimul-kabir/actua/issues/669): Actual's own rules engine
decides the expected result of every case, and Actua's engine has to reproduce it.

- `generate.mjs` opens an offline budget in `@actual-app/api@26.9.0` and creates synthetic accounts,
  payees, categories and two schedules. It then creates one or more rules per case and runs each case's
  transactions through loot-core's `rules-run` handler (`runRules`, the same function bank sync, file
  import and the PWA's editor use). It writes the entities, the rules exactly as Actual stored them,
  and every input/output pair to `app/src/test/resources/rules-parity/upstream-26.9.0.json`, with ids
  replaced by stable tokens so the file is deterministic.
- `RulesParityFixtureTest` (JVM, runs with `./gradlew testInstrumentedUnitTest`) parses those rules
  with Actua's `Rule.parse` and replays every case through `RulesEngine.apply`. It compares account,
  date, amount, payee (by name), category, notes, cleared, schedule, deletion and split lines.
  Differences fail unless `KNOWN_DIVERGENCES` lists them with their issue.
- The [`rules-parity`](../../../.github/workflows/rules-parity.yml) workflow regenerates the fixture
  and fails if the committed copy is out of date; the regenerated file is its artifact.

Cases cover every condition operator per field type (string, id, number with inflow/outflow, date
including month/year/recurring values, boolean), `and`/`or`, every action (`set` per field,
`payee_name`, prepend/append notes, `link-schedule`, `delete-transaction`, template, formula and split
actions), stage and score ordering, chaining, and the schedule-rule bypass.

Run locally with `npm ci && node generate.mjs` from this directory. Don't regenerate the fixture to
make the test pass: a changed expectation means Actual's behavior changed.
