// Generates the #667 budget-month fixture: a synthetic multi-month budget run through Actual's own
// budget spreadsheet (loot-core's envelope.ts / tracking.ts via @actual-app/api 26.9.0, offline).
// It writes the budget's rows and every month's cells as Actual computes them; the androidTest
// BudgetMonthParityFixtureTest loads the rows into SQLite and checks ActualBudgetDatabase
// .fetchBudgetMonth against those cells. Ids are replaced with stable tokens so the output is
// deterministic; the workflow regenerates it and fails if it changed.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import * as api from '@actual-app/api';

const OUT = resolve(process.argv[2] ?? '../../../app/src/androidTest/assets/budget-parity/upstream-26.9.0.json');
const DATA_DIR = resolve('work/data');
const MONTHS = ['2026-03', '2026-04', '2026-05', '2026-06', '2026-07'];
rmSync(DATA_DIR, { recursive: true, force: true });
mkdirSync(DATA_DIR, { recursive: true });

const actual = await api.init({ dataDir: DATA_DIR });
const send = async (name, args) => {
  const result = await actual.send(name, args);
  if (result?.error) throw new Error(`${name}: ${JSON.stringify(result.error)}`);
  return result;
};
const cents = (value) => Math.round(value * 100);
// Reopen the budget so every month's cells are rebuilt from the stored rows, as when a user opens it.
const reopen = async (budgetId) => {
  await send('close-budget');
  await send('load-budget', { id: budgetId });
  await send('get-budget-bounds');
};

/** Builds one budget and returns { rows, months } with real ids (tokenised later). */
async function buildBudget(name, type) {
  await send('create-budget', { budgetName: name, avoidUpload: true });
  const { id: budgetId } = (await send('get-budgets')).find((budget) => budget.name === name);
  if (type === 'tracking') await send('preferences/save', { id: 'budgetType', value: 'tracking' });

  const ids = {};
  ids.checking = await api.createAccount({ name: 'Checking', offbudget: false });
  ids.savings = await api.createAccount({ name: 'Savings', offbudget: true });
  ids.bills = await api.createCategoryGroup({ name: 'Bills', is_income: false });
  ids.fun = await api.createCategoryGroup({ name: 'Fun', is_income: false });
  ids.archive = await api.createCategoryGroup({ name: 'Archive', is_income: false });
  await api.updateCategoryGroup(ids.archive, { name: 'Archive', hidden: true });
  const income = (await api.getCategoryGroups()).find((group) => group.is_income);
  ids.income = income.id;
  ids.rent = await api.createCategory({ name: 'Rent', group_id: ids.bills });
  ids.utilities = await api.createCategory({ name: 'Utilities', group_id: ids.bills });
  ids.old = await api.createCategory({ name: 'Old Bill', group_id: ids.bills });
  await api.updateCategory(ids.old, { name: 'Old Bill', hidden: true });
  ids.dining = await api.createCategory({ name: 'Dining', group_id: ids.fun });
  ids.hobbies = await api.createCategory({ name: 'Hobbies', group_id: ids.fun });
  ids.coffee = await api.createCategory({ name: 'Coffee', group_id: ids.fun });
  ids.gym = await api.createCategory({ name: 'Gym', group_id: ids.archive });
  ids.salary = await api.createCategory({ name: 'Salary', group_id: ids.income, is_income: true });
  ids.bonus = await api.createCategory({ name: 'Bonus', group_id: ids.income, is_income: true });
  // The starter budget's own categories would add noise; remove them.
  for (const group of await api.getCategoryGroups()) {
    for (const category of group.categories ?? []) {
      if (!Object.values(ids).includes(category.id)) await api.deleteCategory(category.id);
    }
    if (!Object.values(ids).includes(group.id)) await api.deleteCategoryGroup(group.id);
  }

  const tx = (date, amount, category, payee, extra = {}) => ({ date, amount: cents(amount), category, payee_name: payee, ...extra });
  await api.addTransactions(ids.checking, [
    tx('2026-03-01', 3000, ids.salary, 'Employer'),
    tx('2026-03-02', -1200, ids.rent, 'Landlord'),
    tx('2026-03-05', -150, ids.utilities, 'Power Co'), // overspent by 50, no rollover
    tx('2026-03-08', -300, ids.dining, 'Bistro'), // overspent by 100, rollover on
    tx('2026-03-09', -50, ids.hobbies, 'Craft Shop'),
    tx('2026-03-10', -20, ids.coffee, 'Cafe'), // category later deleted into Dining
    tx('2026-03-11', -30, ids.gym, 'Gym'), // hidden group
    tx('2026-04-01', 3000, ids.salary, 'Employer'),
    tx('2026-04-02', -1200, ids.rent, 'Landlord'),
    tx('2026-04-05', -80, ids.utilities, 'Power Co'),
    tx('2026-04-08', -100, ids.dining, 'Bistro'),
    tx('2026-04-12', 25, ids.hobbies, 'Craft Shop'), // refund
    tx('2026-05-01', 3000, ids.salary, 'Employer'),
    tx('2026-05-03', 400, ids.bonus, 'Employer'), // income set to hold automatically
    tx('2026-05-02', -1200, ids.rent, 'Landlord'),
    tx('2026-05-09', -90, null, 'Market', {
      subtransactions: [{ amount: cents(-60), category: ids.dining }, { amount: cents(-30), category: ids.hobbies }],
    }),
    tx('2026-06-02', -1300, ids.rent, 'Landlord'), // overspent by 100, no rollover
    tx('2026-06-06', -70, ids.utilities, 'Power Co'),
  ]);
  await api.addTransactions(ids.savings, [tx('2026-04-20', -500, ids.hobbies, 'Broker')]); // off budget
  await api.addTransactions(ids.checking, [tx('2026-04-21', -999, ids.rent, 'Mistake')]); // deleted below
  for (const removed of await api.getTransactions(ids.checking, '2026-04-21', '2026-04-21')) {
    await api.deleteTransaction(removed.id);
  }
  await api.deleteCategory(ids.coffee, ids.dining);

  const budgets = {
    '2026-03': { rent: 1200, utilities: 100, dining: 200, hobbies: 100, old: 40, gym: 25, salary: 3000 },
    '2026-04': { rent: 1200, utilities: 100, dining: 200, hobbies: 50, old: 40, gym: 25, salary: 3000 },
    '2026-05': { rent: 1200, utilities: 100, dining: 150, hobbies: 50, salary: 3000, bonus: 400 },
    '2026-06': { rent: 1200, utilities: 120, dining: 150, hobbies: 0, salary: 3000 },
    '2026-07': { rent: 1250, utilities: 120, dining: 100, salary: 3000 },
  };
  for (const [month, amounts] of Object.entries(budgets)) {
    for (const [key, amount] of Object.entries(amounts)) {
      if (type === 'envelope' && (key === 'salary' || key === 'bonus')) continue;
      await send('budget/budget-amount', { month, category: ids[key], amount: cents(amount) });
    }
  }
  await send('budget/set-carryover', { startMonth: '2026-03', category: ids.dining, flag: true });
  // Holds read `to-budget`, so rebuild the spreadsheet from the stored rows first.
  await reopen(budgetId);
  if (type === 'envelope') {
    await send('budget/set-carryover', { startMonth: '2026-05', category: ids.bonus, flag: true });
    await send('budget/set-carryover', { startMonth: '2026-06', category: ids.bonus, flag: false });
    await reopen(budgetId);
    await send('budget/hold-for-next-month', { month: '2026-04', amount: cents(500) });
    await send('budget/hold-for-next-month', { month: '2026-06', amount: cents(200) });
    await send('budget/reset-hold', { month: '2026-06' });
  } else {
    await send('budget/set-carryover', { startMonth: '2026-04', category: ids.salary, flag: true });
  }

  await reopen(budgetId);
  const months = {};
  for (const month of MONTHS) months[month] = await api.getBudgetMonth(month);
  await send('close-budget');
  return { ids, months, budgetId };
}

const TABLES = {
  accounts: 'id, name, offbudget, closed, tombstone, sort_order',
  category_groups: 'id, name, is_income, hidden, tombstone, sort_order',
  categories: 'id, name, is_income, cat_group, hidden, tombstone, sort_order',
  category_mapping: 'id, transferId',
  payees: 'id, name, transfer_acct, tombstone',
  payee_mapping: 'id, targetId',
  transactions: 'id, isParent, isChild, acct, category, amount, description, date, parent_id, tombstone',
  zero_budgets: 'id, month, category, amount, carryover',
  zero_budget_months: 'id, buffered',
  reflect_budgets: 'id, month, category, amount, carryover',
  preferences: 'id, value',
};

function dump(budgetId) {
  const db = new DatabaseSync(join(DATA_DIR, budgetId, 'db.sqlite'), { readOnly: true });
  const rows = {};
  for (const [table, columns] of Object.entries(TABLES)) {
    const order = columns.split(',')[0];
    rows[table] = db.prepare(`SELECT ${columns} FROM ${table} ORDER BY ${order}`).all()
      .map((row) => ({ ...row }));
  }
  db.close();
  return rows;
}

// Stable tokens: entity ids by their key, every other id (transactions, budget rows) by content.
function tokenise({ ids, months }, rows) {
  const names = new Map(Object.entries(ids).map(([key, id]) => [id, key]));
  for (const payee of rows.payees) {
    if (payee.transfer_acct) names.set(payee.id, `transfer-${names.get(payee.transfer_acct)}`);
    else if (!names.has(payee.id)) names.set(payee.id, `payee-${payee.name.toLowerCase().replace(/\W+/g, '-')}`);
  }
  const sortedTransactions = [...rows.transactions].sort((a, b) =>
    (a.date - b.date) || (a.amount - b.amount) || String(a.parent_id ?? '').localeCompare(String(b.parent_id ?? '')));
  sortedTransactions.forEach((row, index) => { if (!names.has(row.id)) names.set(row.id, `tx-${String(index + 1).padStart(2, '0')}`); });
  const swap = (value) => (typeof value === 'string' && names.has(value) ? names.get(value) : value);
  const budgetRowId = (row) => `${row.month}-${swap(row.category)}`;
  const out = {};
  for (const [table, list] of Object.entries(rows)) {
    out[table] = list.map((row) => {
      const mapped = Object.fromEntries(Object.entries(row).map(([key, value]) => [key, swap(value)]));
      if (table === 'zero_budgets' || table === 'reflect_budgets') mapped.id = budgetRowId(row);
      return mapped;
    }).sort((a, b) => String(a.id).localeCompare(String(b.id)));
  }
  const expected = {};
  for (const [month, data] of Object.entries(months)) {
    const { categoryGroups, month: _, ...summary } = data;
    expected[month] = {
      ...summary,
      groups: categoryGroups.map(({ id, name, is_income, hidden, categories, ...cells }) => ({
        id: swap(id), name, is_income, hidden, ...cells,
        categories: categories.map(({ id: categoryId, name: categoryName, hidden: categoryHidden, budgeted, spent, received, balance, carryover }) =>
          Object.fromEntries(Object.entries({ id: swap(categoryId), name: categoryName, hidden: categoryHidden, budgeted, spent, received, balance, carryover })
            .filter(([, value]) => value !== undefined))),
      })),
    };
  }
  return { rows: out, expected };
}

try {
  const envelope = await buildBudget('Envelope fixture', 'envelope');
  const tracking = await buildBudget('Tracking fixture', 'tracking');
  const fixture = {
    source: '@actual-app/api 26.9.0 (loot-core budget/envelope.ts, budget/tracking.ts), offline',
    months: MONTHS,
    envelope: tokenise(envelope, dump(envelope.budgetId)),
    tracking: tokenise(tracking, dump(tracking.budgetId)),
  };
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, `${JSON.stringify(fixture, null, 2)}\n`);
  console.log(`Wrote ${OUT}`);
} finally {
  await api.shutdown();
}
