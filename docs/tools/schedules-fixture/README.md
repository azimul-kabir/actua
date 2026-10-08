# Schedules parity fixture

Evidence for [#670](https://github.com/azimul-kabir/actua/issues/670): Actual's own schedules code
decides the expected result of every recurrence case, and Actua's engine has to reproduce it.

- `generate.mjs` opens an offline budget in `@actual-app/api@26.9.0` with a fixed clock (noon UTC on
  each case's `today`). For every recurrence config it creates a schedule and records:
  - `nextDate`: the next date `schedule/create` stores, i.e. loot-core's `getNextDate`;
  - `skips`: the next date after each of six `schedule/skip-next-date` calls (each is a `getNextDate`
    from the day after the current next date, the step the Balance Forecast's
    `getFutureOccurrenceDates` walk also takes);
  - `upcoming`: the editor preview from `schedule/get-upcoming-dates` (8 dates).

  It writes them to `app/src/test/resources/schedules-parity/upstream-26.9.0.json`.
- `ScheduleRecurrenceParityFixtureTest` (JVM, runs with `./gradlew testInstrumentedUnitTest`) parses
  each config with `RecurConfig.parse` and replays it through `ScheduleRecurrence.nextOccurrence`, the
  skip-next-date search Actua's repository uses (repeated), and `ScheduleRecurrence.upcomingDates`.
  Differences fail unless `KNOWN_DIVERGENCES` lists them with their issue.
- The [`schedules-parity`](../../../.github/workflows/schedules-parity.yml) workflow regenerates the
  fixture and fails if the committed copy is out of date; the regenerated file is its artifact.

Cases cover month ends (29th/30th/31st, `day -1`/`-2`), leap years, nth-weekday and combined
patterns, intervals greater than 1, weekend solving before/after (including a "before" solve that
lands before today), every end mode, schedules that start in the future or have ended, and today on
an occurrence, a weekend or a leap day.

Run locally with `npm ci && node generate.mjs` from this directory. Don't regenerate the fixture to
make the test pass: a changed expectation means Actual's behavior changed.
