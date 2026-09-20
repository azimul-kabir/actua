# Scheduled transactions parity

Issue [#279](https://github.com/azimul-kabir/actua/issues/279) tracks a 1:1 parity pass on
**Scheduled Transactions** against Actual Budget upstream, the same way
[docs/BUDGET_AUTOMATION_PARITY.md](BUDGET_AUTOMATION_PARITY.md) did for Budget Automation
(issue [#56](https://github.com/azimul-kabir/actua/issues/56)). It replaces the vague
"documented recurrence and posting subset" caveat in
[docs/ACTUAL_26_9_COMPATIBILITY.md](ACTUAL_26_9_COMPATIBILITY.md) with an itemized list of what
is covered.

## Upstream references

The audited reference is Actual Budget
[v26.9.0](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
(commit `59fe126f637d858c061e1eeedbef5436c8f2225a`), the same tag pinned by
`docs/ACTUAL_26_9_COMPATIBILITY.md`. The primary implementation points are
[`packages/loot-core/src/shared/schedules.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/schedules.ts)
(recurrence config → `RSchedule` rules, `getStatus`, `getNextDate`, occurrence/dedup matching,
preview generation), [`packages/loot-core/src/server/schedules/app.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/schedules/app.ts)
(schedule CRUD, `setNextDate`, `advanceSchedulesService` posting/catch-up loop),
[`packages/loot-core/src/server/schedules/find-schedules.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/schedules/find-schedules.ts)
(schedule discovery), and
[`packages/loot-core/src/shared/rules.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/rules.ts)
(`getApproxNumberThreshold`, used by both `isapprox` amount conditions and discovery matching).
Actuali (MattFaz/actuali) does not carry a portable schedules engine equivalent to its budget
automation code, so it is not a primary reference for this document; Actua's schedules engine was
ported directly against the upstream TypeScript sources above.

## Exact behavior to preserve

**Recurrence (`recurConfigToRSchedule`, `getNextDate`, `getUpcomingDays`):**
- Recurrence config fields: `frequency` (daily/weekly/monthly/yearly), `interval`, `start`,
  monthly `patterns` (`{type: 'day', value}` for day-of-month, or `{type: <2-letter weekday>,
  value: <nth>}` for nth-weekday-of-month), `endMode` (`never`/`after_n_occurrences`/`on_date`)
  with `endOccurrences`/`endDate`, `skipWeekend`, `weekendSolveMode` (`before`/`after`).
- Monthly day-of-month and nth-weekday patterns can be combined in one schedule (unioned, not
  exclusive); day values may be negative to count from month end.
- Weekend skipping shifts a weekend occurrence to the previous Friday (`before`) or next Monday
  (`after`), applied *after* the recurrence is computed, not as a recurrence constraint itself.
- `getStatus` priority order: `completed` → `completed`; has a matching transaction → `paid`;
  `nextDate == today` → `due`; `nextDate` within the upcoming window → `upcoming`;
  `nextDate < today` → `missed`; otherwise → `scheduled`. The upcoming window (`getUpcomingDays`)
  supports the presets `1`/`7`/`14`/`oneMonth`/`currentMonth` and a custom `<n>-day|week|month|year`
  format, overridable per schedule via `custom_upcoming_length`.

**Posting (`app.ts`: `postTransactionForSchedule`, `advanceSchedulesService`,
`getScheduleOccurrenceMatchStartDate`, `isScheduleOccurrencePosted`):**
- Manual posting creates exactly one transaction linked to the schedule via `schedule: schedule.id`
  and does not, by itself, advance `next_date`.
- Automatic posting (`posts_transaction: true`) runs once per day per budget
  (`lastScheduleRun` gate, retried until a sync succeeds) and catches up every `due`/`missed`
  occurrence in sequence, posting one transaction per occurrence and advancing `next_date` after
  each post, stopping only when the schedule is no longer `due`/`missed`/`paid` or is not
  recurring.
- Dedup/occurrence matching: the lower bound for "does this occurrence already have a posted
  transaction" is the occurrence date itself when the date condition operator is `is` or the
  schedule auto-posts (exact match, no lookback — a lookback would let yesterday's post falsely
  satisfy today's occurrence); otherwise a 2-day lookback (`isapprox`, manual posting) to tolerate
  early manual payments. Forecast/preview dedup additionally applies an upper bound of the
  occurrence date itself (`isScheduleOccurrencePosted`).
- Unlinking a transaction from a schedule (or deleting the schedule) never deletes or mutates the
  already-posted transaction rows.

**Condition matching/merging (`extractScheduleConds`, `updateConditions`, `getApproxNumberThreshold`):**
- A schedule's rule owns exactly four recognized condition slots (payee, account, amount, date);
  any other conditions on the rule are preserved verbatim across edits.
- `isapprox` amount matching uses a 7.5% tolerance (`round(abs(amount) * 0.075)`); an amount range
  (`isbetween`) posts the rounded midpoint of its two bounds.
- Editing a schedule's amount condition keeps a plain `set amount` rule action in sync, but never
  touches an amount action driven by a template/formula.

**Lifecycle:** `completed`, `paid` (has a matching transaction for the current occurrence), `due`,
`upcoming`, `missed`, `scheduled` (no next date yet computed, or a future date beyond the upcoming
window) are the only status values. Skipping a due/missed occurrence (`skipNextDate`) advances
`next_date` to the following occurrence without posting a transaction. Completing a schedule stops
it from being processed by the posting/advance service; restarting it recomputes `next_date` from
today.

**Discovery (`find-schedules.ts`):** candidate weekly / every-2-weeks / monthly / monthly-last-day /
monthly-1st-or-3rd / monthly-2nd-or-4th patterns are swept per account against transaction history,
matched by the same 7.5% amount tolerance and a `±2`-day date window, ranked by how closely dated
occurrences line up, and grouped/deduplicated by payee before being offered for creation.

## Actua implementation boundary

Actua's schedules engine
(`app/src/main/java/com/azimulkabir/actua/data/schedules/`) is a from-scratch Kotlin port of the
upstream TypeScript behavior above (not a port of Actuali, which has no equivalent engine), and
matches it closely:

- **Recurrence** (`ScheduleRecurrence.kt`): `RecurConfig` round-trips upstream's exact stored JSON
  shape (`frequency`/`interval`/`start`/`patterns`/`skipWeekend`/`weekendSolveMode`/`endMode`/
  `endOccurrences`/`endDate`). Daily/weekly/monthly/yearly frequencies, combined day-of-month and
  nth-weekday monthly patterns (including negative "from month end" days and real nth-weekday
  computation), bounded endings (`after_n_occurrences`, `on_date`), and post-computation weekend
  skipping (previous Friday / next Monday) are all implemented and covered by
  `ScheduleRecurrenceTest`/`ScheduleRecurrenceParserTest`. A schedule whose occurrences are
  entirely in the past because of a bounded ending still reports its final occurrence rather than
  `null`, matching upstream's reverse-lookup fallback in `getNextDate`. As an internal-only safety
  bound not present upstream, `ScheduleRecurrence.enumerate` caps generation at 20,000 candidate
  periods; this has no observable effect for any realistic schedule.
- **Status** (`ScheduleStatus.kt`): `ScheduleStatusCalculator.status` implements the exact upstream
  priority order (`completed` → `paid` → `due` → `upcoming` → `missed` → `scheduled`), and
  `ScheduleUpcomingLength` implements every upstream preset plus the custom compound format,
  editable per schedule via the "Upcoming window" picker in `EditScheduleScreen`.
- **Posting** (`SchedulePoster.kt`, `ActuaRepository.postScheduleTransaction`/
  `skipScheduleNextDate`): automatic posting is gated once per budget per day and backfills every
  missed/due occurrence in sequence, posting a transaction and advancing `next_date` for each one,
  matching `advanceSchedulesService`'s catch-up loop, with the per-occurrence dedup check
  (`ActualBudgetDatabase.hasScheduleTransaction`) bounded on both ends of the occurrence date,
  matching upstream's `isScheduleOccurrencePosted` window rather than an unbounded lower-bound-only
  existence query. Manual posting creates one linked transaction
  without advancing `next_date`, exactly as upstream's `postTransactionForSchedule`. Skip-next-date
  advances past the current occurrence via the same weekend-aware search logic used for recurrence.
  Deleting a schedule tombstones it and its owned rule but always preserves already-posted
  transactions, matching upstream's `deleteSchedule`.
- **Condition matching** (`ScheduleConditions.kt`): `extract`/`build`/`merge` own exactly the same
  four condition slots as upstream's `extractScheduleConds`/`updateConditions`, preserve any
  additional user- or Actual-added conditions on the rule untouched, and keep a plain `set amount`
  action in sync with the amount condition while leaving template/formula-driven actions alone.
  The 7.5% approximate-amount threshold and range-midpoint posting amount match upstream exactly.
- **Discovery** (`ScheduleDiscovery.kt`, `FindSchedulesScreen.kt`): implements the same pattern
  sweep, 7.5% amount tolerance, and closeness ranking as `find-schedules.ts`, surfaced as a
  read-only proposal list the user accepts (bulk-create) or ignores, matching upstream's
  accept-as-detected model (there is no per-field editing of a proposal before creation, upstream
  or in Actua).
- **Budget-template interaction** (`BudgetScheduleFunding.kt`): schedules feed Budget Automation's
  "Cover schedule" goal type through a dedicated read projection (occurrences-this-month, or a
  sinking-fund spread across the months until the next occurrence when the schedule doesn't recur
  this month), matching the funding behavior documented for schedule-template rows in
  `docs/BUDGET_AUTOMATION_PARITY.md`. Schedules whose date condition Actua cannot parse
  (`ScheduleDateCondition.Unsupported`) are excluded from funding rather than treated as a
  zero-dollar template.
- **UI** (`SchedulesScreen.kt`, `EditScheduleScreen.kt`, `FindSchedulesScreen.kt`,
  `BillsCalendarScreen.kt`): create/edit (including the full repeat editor with a live next-dates
  preview), delete, mark completed/restart, skip next date, manual post (today or on the schedule
  date), list and unlink individual posted transactions, and accept discovered proposals are all
  exposed. A schedule whose date condition is `Unsupported`, or that carries extra rule conditions
  beyond Actua's four recognized slots ("custom"), is disclosed to the user; the unsupported case
  blocks saving entirely rather than risking silent data loss, and the custom case allows editing
  the four recognized slots while explicitly preserving the rest.

## Deliberate deviations and known gaps

- **Fixed during this audit:** the automatic catch-up dedup check
  (`ActualBudgetDatabase.hasScheduleTransaction`) originally only bounded the match date from
  below (`date >= onOrAfter`), unlike upstream's `isScheduleOccurrencePosted`, which bounds both
  above and below (`matchStartDate <= tx.date <= occurrenceDate`). Since `SchedulePoster` only
  processes auto-posting schedules, where upstream's lower bound always equals the occurrence date
  exactly, the correct check for Actua's catch-up loop is an exact-date match. A schedule with an
  out-of-order or future-dated linked transaction could previously cause the catch-up loop to treat
  an unrelated later transaction as satisfying an earlier due/missed occurrence and silently skip
  posting it. `hasScheduleTransaction` now takes an `onOrBefore` bound (defaulting to `onOrAfter`,
  i.e. an exact match) and `SchedulePoster` relies on that default. Tracked as
  [#286](https://github.com/azimul-kabir/actua/issues/286), with regression coverage in
  `ActualBudgetReadModelTest.schedulePosterDedupDoesNotSkipAnEarlierOccurrenceBecauseOfALaterLinkedTransaction`.
- **No date-based "before/after due-date" prompt/notification UX.** Upstream's `schedules.ts`
  itself has no notification concept either (Actual's desktop/web client surfaces due schedules
  passively in its Bills UI, the same pattern Actua's Bills & Calendar screen follows); this is not
  a gap versus the audited upstream source, only versus a plausible mobile-specific extension. No
  action needed to match upstream.
- **Android early-post convenience.** Actual leaves every manual post at the current `next_date`.
  In Actua only, **Post Transaction Today** advances a recurring schedule past its current
  occurrence after creating the linked transaction, so an early payment immediately moves the
  Bills view to the next due date. **Post Transaction** retains upstream manual-post behavior.
- **`RecurConfig.parse` is strict about `interval`'s JSON type** (only a bare JSON integer is
  accepted; a string-encoded interval is rejected as `Unsupported` rather than coerced). Upstream's
  `recurConfigToRSchedule` does not itself validate `config.interval`'s type before using it
  arithmetically. This is a defensive Actua-only strictness, not a behavior difference for any
  schedule Actual itself would ever write, so no change is planned.

## Result

Given the above, `docs/ACTUAL_26_9_COMPATIBILITY.md`'s scheduled-transactions row is updated to
point here instead of the prior "documented recurrence and posting subset" placeholder, and
`BACKEND_PARITY.md`'s scheduled-transactions bullets are unchanged in substance (they already
itemize this same coverage) but now cite this document as the audited parity reference.
