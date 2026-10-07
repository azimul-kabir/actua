// Shared settings and the synthetic seed for the #668 budget-template check.
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import * as api from '@actual-app/api';

export const SERVER_URL = process.env.TEMPLATES_SERVER_URL ?? 'http://localhost:5006';
// A throwaway password for a throwaway CI server; not a credential.
export const PASSWORD = process.env.TEMPLATES_PASSWORD ?? 'budget-templates-parity';
// Each pair is seeded identically; Actual's engine runs on `upstream`, Actua's on `actua`. The
// whole-units pair turns on the synced `hideFraction` preference, which makes templates round.
export const PAIRS = [
  { label: '', upstream: 'Templates upstream', actua: 'Templates actua' },
  { label: 'whole units', upstream: 'Whole units upstream', actua: 'Whole units actua', hideFraction: true },
];
export const MONTHS = ['2026-08', '2026-09', '2026-10'];

export function dataDir(name) {
  const dir = resolve(process.env.TEMPLATES_WORK_DIR ?? 'work', name);
  mkdirSync(dir, { recursive: true });
  return dir;
}

export async function bootstrap() {
  const response = await fetch(`${SERVER_URL}/account/bootstrap`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: PASSWORD }),
  });
  const body = await response.json().catch(() => ({}));
  if (body.status !== 'ok' && body.reason !== 'already-bootstrapped') {
    throw new Error(`Bootstrapping ${SERVER_URL} failed: ${JSON.stringify(body)}`);
  }
}

const template = (fields) => ({ directive: 'template', priority: 1, ...fields });
const monthly = (amount, priority = 1) =>
  template({ type: 'periodic', amount, period: { period: 'month', amount: 1 }, starting: '2026-01-01', priority });
const limit = (fields) => ({ directive: 'template', type: 'limit', priority: null, hold: false, period: 'monthly', ...fields });
const remainder = (weight, extra = {}) => ({ directive: 'template', type: 'remainder', priority: null, weight, ...extra });

/**
 * One category per behavior. `templates` are stored as UI-managed `goal_def` through loot-core's
 * own `budget/set-category-automations`; `note` is a category note. Keep README.md in step.
 */
export const TEMPLATE_CATEGORIES = [
  { name: 'T Simple', templates: [template({ type: 'simple', monthly: 50 })] },
  { name: 'T Periodic month', templates: [monthly(40)] },
  { name: 'T Periodic week', templates: [template({ type: 'periodic', amount: 10, period: { period: 'week', amount: 1 }, starting: '2026-07-06' })] },
  { name: 'T Periodic 2 months', templates: [template({ type: 'periodic', amount: 30, period: { period: 'month', amount: 2 }, starting: '2026-07-15' })] },
  { name: 'T By date', templates: [template({ type: 'by', amount: 600, month: '2026-12', priority: 2 })] },
  { name: 'T By annual', templates: [template({ type: 'by', amount: 1200, month: '2026-06', annual: true, priority: 2 })] },
  { name: 'T Spend', templates: [template({ type: 'spend', amount: 300, month: '2026-10', from: '2026-07', priority: 2 })] },
  { name: 'T Average', templates: [template({ type: 'average', numMonths: 3 })] },
  { name: 'T Average adjusted', templates: [template({ type: 'average', numMonths: 3, adjustment: 10, adjustmentType: 'percent' })] },
  { name: 'T Average refund', templates: [template({ type: 'average', numMonths: 3 })] },
  { name: 'T Copy', templates: [template({ type: 'copy', lookBack: 2 })] },
  { name: 'T Percent available', templates: [template({ type: 'percentage', percent: 10, category: 'available funds', previous: false, priority: 3 })] },
  { name: 'T Percent salary', templates: [template({ type: 'percentage', percent: 5, category: 'Parity Salary', previous: false })] },
  { name: 'T Percent all income', templates: [template({ type: 'percentage', percent: 2, category: 'all income', previous: false })] },
  { name: 'T Percent previous', templates: [template({ type: 'percentage', percent: 3, category: 'Parity Salary', previous: true })] },
  { name: 'T Refill', templates: [limit({ amount: 200 }), template({ type: 'refill' })] },
  { name: 'T Refill weekly', templates: [limit({ amount: 25, period: 'weekly', start: '2026-07-06' }), template({ type: 'refill' })] },
  { name: 'T Limit release', templates: [monthly(100), limit({ amount: 60 })] },
  { name: 'T Limit hold', templates: [monthly(100), limit({ amount: 60, hold: true })] },
  { name: 'T Remainder 1', templates: [remainder(1)] },
  { name: 'T Remainder 2', templates: [remainder(1)] },
  { name: 'T Remainder 3', templates: [remainder(1)] },
  { name: 'T Remainder capped', templates: [remainder(2, { limit: { amount: 15, hold: false, period: 'monthly' } })] },
  { name: 'T Goal only', templates: [{ directive: 'goal', type: 'goal', amount: 1000 }] },
  { name: 'T Goal and fixed', templates: [monthly(25), { directive: 'goal', type: 'goal', amount: 500 }] },
  { name: 'T Schedule monthly', schedule: 'Parity Rent', templates: [template({ type: 'schedule', name: 'Parity Rent', priority: 2 })] },
  { name: 'T Schedule quarterly', schedule: 'Parity Insurance', templates: [template({ type: 'schedule', name: 'Parity Insurance', priority: 2 })] },
  { name: 'T Schedule full', schedule: 'Parity Gym', templates: [template({ type: 'schedule', name: 'Parity Gym', full: true, priority: 2 })] },
  { name: 'T Prebudgeted', templates: [monthly(40)] },
  // Notes-managed: stored into goal_def by loot-core's store-note-templates during the seed.
  { name: 'T Notes stored', note: '#template 20', storeNote: true },
  // Notes-managed, written the way another client leaves it: a note with no goal_def yet.
  { name: 'T Notes only', note: '#template 75' },
];

export const CLEANUP_CATEGORIES = [
  { name: 'C Source', note: '#cleanup source' },
  { name: 'C Sink 1', note: '#cleanup sink' },
  { name: 'C Sink 3', note: '#cleanup sink 3' },
  { name: 'C Overspent' },
  { name: 'C Group source', note: '#cleanup Fun source' },
  { name: 'C Group sink', note: '#cleanup Fun sink' },
];

export const SCHEDULES = [
  { name: 'Parity Rent', amount: -80000, date: { start: '2026-08-15', frequency: 'monthly', interval: 1 } },
  { name: 'Parity Insurance', amount: -30000, date: { start: '2026-10-20', frequency: 'monthly', interval: 3 } },
  { name: 'Parity Gym', amount: -4500, date: { start: '2026-08-05', frequency: 'monthly', interval: 1 } },
];

/** Builds the same synthetic budget in whichever budget `actual` has open. */
export async function seed(actual) {
  const account = await api.createAccount({ name: 'Parity Checking', offbudget: false });
  const incomeGroup = (await api.getCategoryGroups()).find((group) => group.is_income);
  const salary = await api.createCategory({ name: 'Parity Salary', group_id: incomeGroup.id, is_income: true });
  const employer = await api.createPayee({ name: 'Parity Employer' });
  const shop = await api.createPayee({ name: 'Parity Shop' });

  const ids = {};
  const templatesGroup = await api.createCategoryGroup({ name: 'Templates', is_income: false });
  for (const category of TEMPLATE_CATEGORIES) {
    ids[category.name] = await api.createCategory({ name: category.name, group_id: templatesGroup, is_income: false });
  }
  const cleanupGroup = await api.createCategoryGroup({ name: 'Cleanup', is_income: false });
  for (const category of CLEANUP_CATEGORIES) {
    ids[category.name] = await api.createCategory({ name: category.name, group_id: cleanupGroup, is_income: false });
  }

  for (const schedule of SCHEDULES) {
    await api.createSchedule({
      name: schedule.name,
      posts_transaction: false,
      account,
      payee: shop,
      amount: schedule.amount,
      amountOp: 'is',
      date: { ...schedule.date, endMode: 'never', patterns: [], skipWeekend: false, weekendSolveMode: 'after' },
    });
  }

  const income = (date, amount) => ({ date, amount, payee: employer, category: salary });
  const spend = (name, date, amount) => ({ date, amount, payee: shop, category: ids[name] });
  await api.addTransactions(account, [
    income('2026-05-01', 200000), income('2026-06-01', 200000), income('2026-07-01', 150000),
    income('2026-08-01', 500000), income('2026-09-01', 100000), income('2026-10-01', 100000),
    spend('T Average', '2026-05-10', -9000), spend('T Average', '2026-06-10', -12000), spend('T Average', '2026-07-10', -15000),
    spend('T Average adjusted', '2026-05-10', -1000), spend('T Average adjusted', '2026-06-10', -1001),
    spend('T Average adjusted', '2026-07-10', -1001),
    spend('T Average refund', '2026-05-10', -5000), spend('T Average refund', '2026-06-10', 2000),
    spend('T Average refund', '2026-07-10', -4000),
    spend('T Refill', '2026-08-20', -5000),
    spend('C Overspent', '2026-10-10', -4000),
  ]);

  await api.setBudgetAmount('2026-06', ids['T Copy'], 7777);
  await api.setBudgetAmount('2026-07', ids['T Limit release'], 9000);
  await api.setBudgetAmount('2026-07', ids['T Limit hold'], 9000);
  await api.setBudgetAmount('2026-08', ids['T Prebudgeted'], 1000);
  await api.setBudgetAmount('2026-09', ids['C Source'], 5000);
  await api.setBudgetAmount('2026-09', ids['C Group source'], 3000);

  for (const category of [...TEMPLATE_CATEGORIES, ...CLEANUP_CATEGORIES]) {
    if (category.note) await api.updateNote(ids[category.name], category.note);
  }
  const uiManaged = TEMPLATE_CATEGORIES.filter((category) => category.templates);
  await actual.send('budget/set-category-automations', {
    categoriesWithTemplates: uiManaged.map((category) => ({ id: ids[category.name], templates: category.templates })),
    source: 'ui',
  });
  const stored = TEMPLATE_CATEGORIES.filter((category) => category.storeNote).map((category) => ids[category.name]);
  await actual.send('budget/store-note-templates', stored);
  return ids;
}

/**
 * The open budget's local id, from loot-core's in-memory metadata. `get-budgets` reads every
 * budget's metadata.json and drops one it can't parse, which happens while sync is rewriting the
 * open budget's file (#919).
 */
export async function openBudgetId(actual) {
  const { id } = await actual.send('load-prefs');
  if (!id) throw new Error('No budget is open');
  return id;
}

/** Creates, seeds and syncs one budget; leaves it open. */
export async function createSeededBudget(actual, name, { hideFraction = false } = {}) {
  await actual.send('close-budget');
  const created = await actual.send('create-budget', { budgetName: name });
  if (created?.error) throw new Error(`create-budget failed: ${created.error}`);
  const remote = (await actual.send('get-remote-files'))?.find((file) => file.name === name);
  if (!remote) throw new Error(`"${name}" was not uploaded`);
  await seed(actual);
  if (hideFraction) await actual.send('preferences/save', { id: 'hideFraction', value: 'true' });
  await api.sync();
  // Actual creates a month's budget sheet (createAllBudgets) when a budget loads; reload so the
  // months of the seeded earlier transactions exist, as they do for anyone who reopens the budget.
  const id = await openBudgetId(actual);
  await actual.send('close-budget');
  await actual.send('load-budget', { id });
  return remote;
}
