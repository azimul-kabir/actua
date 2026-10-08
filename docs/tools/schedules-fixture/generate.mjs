// Generates the #670 schedule recurrence fixture: synthetic recurrence configs run through Actual's own
// schedules code from @actual-app/api 26.9.0, offline, on a fixed "today" per case. For each case it
// records the next date `schedule/create` stores (loot-core's `getNextDate`), the next dates after
// repeated `schedule/skip-next-date` (each a `getNextDate` from the day after, the step the Balance
// Forecast's `getFutureOccurrenceDates` also takes), and the editor preview
// `schedule/get-upcoming-dates` returns. The JVM test
// ScheduleRecurrenceParityFixtureTest replays every case through Actua's ScheduleRecurrence. The
// workflow regenerates the file and fails if it changed.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

process.env.TZ = 'UTC';

// A fixed clock: `new Date()` and `Date.now()` read the current case's "today" at noon UTC. Each read
// advances by a millisecond so Actual's message clock keeps moving forward.
const RealDate = Date;
let nowMs = 0;
const tick = () => nowMs++;
class FixedDate extends RealDate {
  constructor(...args) {
    if (args.length === 0) super(tick());
    else super(...args);
  }
  static now() {
    return tick();
  }
}
globalThis.Date = FixedDate;
const setToday = iso => {
  const [y, m, d] = iso.split('-').map(Number);
  const noon = RealDate.UTC(y, m - 1, d, 12);
  // Days only move forward between cases, so the message clock never runs backwards.
  if (noon < nowMs - 60_000) throw new Error(`Cases must be ordered by today: ${iso}`);
  nowMs = Math.max(nowMs, noon);
};

const recur = (start, frequency, extra = {}) => ({
  start,
  frequency,
  interval: 1,
  patterns: [],
  skipWeekend: false,
  weekendSolveMode: 'after',
  endMode: 'never',
  ...extra,
});
const day = value => ({ type: 'day', value });
const weekday = (type, value) => ({ type, value });

/** Each case: a name, the day it runs on, and a recurrence config. Ordered by `today`. */
const CASES = [
  // Month ends and leap years.
  ['monthly on the 31st skips shorter months', '2026-01-15', recur('2026-01-31', 'monthly')],
  ['monthly on the 30th through February', '2026-01-15', recur('2026-01-30', 'monthly')],
  ['monthly on the 29th through February', '2026-01-15', recur('2025-12-29', 'monthly')],
  ['last day of the month (day -1)', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [day(-1)] })],
  ['second-to-last day (day -2)', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [day(-2)] })],
  ['days 15 and 31 combined', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [day(15), day(31)] })],
  ['yearly on Feb 29 from a leap year', '2026-01-15', recur('2024-02-29', 'yearly')],
  ['yearly on Feb 28', '2026-01-15', recur('2024-02-28', 'yearly')],
  ['yearly every 2 years on Mar 31', '2026-01-15', recur('2025-03-31', 'yearly', { interval: 2 })],
  // Weekday patterns.
  ['second Tuesday', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [weekday('TU', 2)] })],
  ['last Friday', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [weekday('FR', -1)] })],
  ['fifth Monday (months without one skipped)', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [weekday('MO', 5)] })],
  ['day 15 and last Friday combined', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [day(15), weekday('FR', -1)] })],
  // Intervals.
  ['daily every 3 days', '2026-01-15', recur('2026-01-02', 'daily', { interval: 3 })],
  ['weekly every 2 weeks', '2026-01-15', recur('2025-12-04', 'weekly', { interval: 2 })],
  ['monthly every 3 months from Nov 30', '2026-01-15', recur('2025-11-30', 'monthly', { interval: 3 })],
  ['monthly every 2 months with day -1', '2026-01-15', recur('2025-12-01', 'monthly', { interval: 2, patterns: [day(-1)] })],
  // Weekend solving.
  ['monthly on the 1st, weekends after', '2026-01-15', recur('2026-02-01', 'monthly', { skipWeekend: true, weekendSolveMode: 'after' })],
  ['monthly on the 1st, weekends before', '2026-01-15', recur('2026-02-01', 'monthly', { skipWeekend: true, weekendSolveMode: 'before' })],
  ['weekly on Saturdays, weekends after', '2026-01-15', recur('2026-01-03', 'weekly', { skipWeekend: true, weekendSolveMode: 'after' })],
  ['weekly on Sundays, weekends before', '2026-01-15', recur('2026-01-04', 'weekly', { skipWeekend: true, weekendSolveMode: 'before' })],
  ['daily, weekends after', '2026-01-15', recur('2026-01-15', 'daily', { skipWeekend: true, weekendSolveMode: 'after' })],
  ['daily, weekends before', '2026-01-15', recur('2026-01-15', 'daily', { skipWeekend: true, weekendSolveMode: 'before' })],
  ['last day of the month, weekends before', '2026-01-15', recur('2026-01-01', 'monthly', { patterns: [day(-1)], skipWeekend: true, weekendSolveMode: 'before' })],
  ['yearly on a weekend, weekends after', '2026-01-15', recur('2022-05-01', 'yearly', { skipWeekend: true, weekendSolveMode: 'after' })],
  // End modes.
  ['3 occurrences, some still ahead', '2026-01-15', recur('2025-12-10', 'monthly', { endMode: 'after_n_occurrences', endOccurrences: 3 })],
  ['3 occurrences, all past', '2026-01-15', recur('2025-06-01', 'monthly', { endMode: 'after_n_occurrences', endOccurrences: 3 })],
  ['1 occurrence in the future', '2026-01-15', recur('2026-02-20', 'monthly', { endMode: 'after_n_occurrences', endOccurrences: 1 })],
  ['ends on a date', '2026-01-15', recur('2026-01-20', 'monthly', { endMode: 'on_date', endDate: '2026-04-19' })],
  ['ends on an occurrence date', '2026-01-15', recur('2026-01-20', 'weekly', { endMode: 'on_date', endDate: '2026-02-03' })],
  ['ended before today', '2026-01-15', recur('2025-01-05', 'weekly', { endMode: 'on_date', endDate: '2025-02-01' })],
  ['ends after weekend solving, weekends after', '2026-01-15', recur('2026-01-03', 'weekly', { endMode: 'after_n_occurrences', endOccurrences: 4, skipWeekend: true })],
  // Start in the future, and today on the occurrence.
  ['starts in the future', '2026-01-15', recur('2026-06-15', 'monthly')],
  ['occurs today', '2026-01-15', recur('2025-10-15', 'monthly')],
  // Today on a weekend: a "before" solve can land before today.
  ['today Saturday, occurrence Saturday, weekends before', '2026-01-17', recur('2025-11-17', 'monthly', { skipWeekend: true, weekendSolveMode: 'before' })],
  ['today Saturday, occurrence Sunday, weekends after', '2026-01-17', recur('2025-10-18', 'monthly', { skipWeekend: true, weekendSolveMode: 'after' })],
  ['today Friday before a weekend occurrence, weekends before', '2026-02-27', recur('2025-12-28', 'monthly', { skipWeekend: true, weekendSolveMode: 'before' })],
  // Leap day as today.
  ['today Feb 29 on a monthly 29th', '2028-02-29', recur('2027-12-29', 'monthly')],
  ['today Feb 29, yearly on Feb 29', '2028-02-29', recur('2024-02-29', 'yearly')],
];

const OUT = resolve(process.argv[2] ?? '../../../app/src/test/resources/schedules-parity/upstream-26.9.0.json');
const DATA_DIR = resolve('work/data');
rmSync(DATA_DIR, { recursive: true, force: true });
mkdirSync(DATA_DIR, { recursive: true });

setToday(CASES[0][1]);
const api = await import('@actual-app/api');
const actual = await api.init({ dataDir: DATA_DIR });
const send = async (name, args) => {
  const result = await actual.send(name, args);
  if (result?.error) throw new Error(`${name}: ${JSON.stringify(result.error)}`);
  return result;
};
const isoFromInt = value => {
  if (value == null) return null;
  const s = String(value);
  return `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}`;
};
const nextDateOf = async id =>
  isoFromInt((await actual.db.first('SELECT local_next_date FROM schedules_next_date WHERE schedule_id = ?', [id]))?.local_next_date);

const results = [];
try {
  await send('create-budget', { budgetName: 'Schedules fixture', avoidUpload: true });
  const account = await api.createAccount({ name: 'Checking', offbudget: false });
  const payee = await api.createPayee({ name: 'Landlord' });

  for (const [name, today, config] of CASES) {
    setToday(today);
    const id = await api.createSchedule({
      name, posts_transaction: false, account, payee, amount: -10000, amountOp: 'is', date: config,
    });
    const nextDate = await nextDateOf(id);
    const upcoming = await send('schedule/get-upcoming-dates', { config, count: 8 });
    const skips = [];
    for (let i = 0; i < 6; i++) {
      await send('schedule/skip-next-date', { id });
      skips.push(await nextDateOf(id));
    }
    results.push({ name, today, config, nextDate, skips, upcoming });
  }
} finally {
  await api.shutdown();
}

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify({
  generator: 'docs/tools/schedules-fixture/generate.mjs',
  actual: '@actual-app/api 26.9.0',
  cases: results,
}, null, 2) + '\n');
console.log(`Wrote ${results.length} cases to ${OUT}`);
