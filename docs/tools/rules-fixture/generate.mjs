// Generates the #669 rules fixture: synthetic rules and transactions run through Actual's own rules
// engine (`rules-run`, i.e. loot-core's `runRules`) from @actual-app/api 26.9.0, offline. The JVM test
// RulesParityFixtureTest replays every case through Actua's RulesEngine. Ids are replaced with stable
// tokens so the output is deterministic; the workflow regenerates it and fails if it changed.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import * as api from '@actual-app/api';

const OUT = resolve(process.argv[2] ?? '../../../app/src/test/resources/rules-parity/upstream-26.9.0.json');
const DATA_DIR = resolve('work/data');
rmSync(DATA_DIR, { recursive: true, force: true });
mkdirSync(DATA_DIR, { recursive: true });

const actual = await api.init({ dataDir: DATA_DIR });
const tokens = new Map(); // real id -> stable token
const token = (id, name) => { tokens.set(id, name); return id; };
const send = async (name, args) => {
  const result = await actual.send(name, args);
  if (result?.error) throw new Error(`${name}: ${JSON.stringify(result.error)}`);
  return result;
};

try {
  await send('create-budget', { budgetName: 'Rules fixture', avoidUpload: true });

  // Entities. Ids become tokens like `account-on`, `payee-grocer`.
  const accounts = {
    on: token(await api.createAccount({ name: 'On Budget', offbudget: false }), 'account-on'),
    other: token(await api.createAccount({ name: 'Second Checking', offbudget: false }), 'account-other'),
    off: token(await api.createAccount({ name: 'Brokerage', offbudget: true }), 'account-off'),
  };
  const groups = {
    parity: token(await api.createCategoryGroup({ name: 'Parity Group', is_income: false }), 'group-parity'),
    other: token(await api.createCategoryGroup({ name: 'Other Group', is_income: false }), 'group-other'),
  };
  const categories = {
    food: token(await api.createCategory({ name: 'Food', group_id: groups.parity }), 'category-food'),
    fun: token(await api.createCategory({ name: 'Fun', group_id: groups.parity }), 'category-fun'),
    hit: token(await api.createCategory({ name: 'Hit', group_id: groups.other }), 'category-hit'),
    misc: token(await api.createCategory({ name: 'Misc', group_id: groups.other }), 'category-misc'),
  };
  const payees = {
    amazon: token(await api.createPayee({ name: 'Amazon Shop' }), 'payee-amazon-shop'),
    grocer: token(await api.createPayee({ name: 'Grocer' }), 'payee-grocer'),
    landlord: token(await api.createPayee({ name: 'Landlord' }), 'payee-landlord'),
  };
  for (const payee of await api.getPayees()) {
    if (payee.transfer_acct) token(payee.id, `payee-transfer-${tokens.get(payee.transfer_acct)}`);
  }

  // Two schedules whose rules gain an extra action, for runRules' schedule bypass/exclusion.
  const recur = (start) => ({ start, frequency: 'monthly', interval: 1, endMode: 'never', patterns: [], skipWeekend: false, weekendSolveMode: 'after' });
  const schedules = {};
  for (const [key, name, payee, amount, notes] of [
    ['s1', 'Parity Rent', payees.landlord, -100000, 'from schedule 1'],
    ['s2', 'Parity Groceries', payees.grocer, -4200, 'from schedule 2'],
  ]) {
    const id = await api.createSchedule({
      name, posts_transaction: false, account: accounts.on, payee, amount, amountOp: 'is', date: recur('2026-08-15'),
    });
    schedules[key] = token(id, `schedule-${key}`);
    const ruleId = (await actual.db.first('SELECT rule FROM schedules WHERE id = ?', [id])).rule;
    token(ruleId, `rule-schedule-${key}`);
    const rule = await send('rule-get', { id: ruleId });
    await send('rule-update', { ...rule, actions: [...rule.actions, { op: 'set', field: 'notes', value: notes, type: 'string' }] });
  }

  const c = (field, op, value, options) => ({ field, op, value, ...(options ? { options } : {}) });
  const set = (field, value, options) => ({ op: 'set', field, value, ...(options ? { options } : {}) });
  const hit = [set('category', categories.hit)];
  const base = { account: accounts.on, date: '2026-08-15', amount: -2500, payee: payees.grocer, category: categories.food, cleared: false };

  /**
   * Each case: rules (all evaluated against every transaction, so each case's rules carry a unique
   * marker) and transactions. Condition cases pair a transaction that should match with one that
   * shouldn't, judged only by what Actual returns.
   */
  const CASES = [];
  const conditionCase = (name, condition, match, miss, marker = 'imported_payee') => {
    const tag = `cond-${CASES.length}`;
    const markerCondition = c(marker, 'is', tag);
    CASES.push({
      name, rules: [{ conditions: [markerCondition, condition], actions: hit }],
      transactions: [match, miss].filter(Boolean).map((t) => ({ ...base, [marker]: tag, ...t })),
    });
  };

  // imported_payee (string)
  conditionCase('imported_payee is (case-insensitive)', c('imported_payee', 'is', 'AMAZON MKTP'), { imported_payee: 'amazon mktp' }, { imported_payee: 'amazon' }, 'notes');
  conditionCase('imported_payee isNot', c('imported_payee', 'isNot', 'amazon'), { imported_payee: 'grocer' }, { imported_payee: 'Amazon' }, 'notes');
  conditionCase('imported_payee contains', c('imported_payee', 'contains', 'MKTP'), { imported_payee: 'amazon mktp 123' }, { imported_payee: 'amazon' }, 'notes');
  conditionCase('imported_payee doesNotContain', c('imported_payee', 'doesNotContain', 'mktp'), { imported_payee: 'grocer' }, { imported_payee: 'AMAZON MKTP' }, 'notes');
  conditionCase('imported_payee doesNotContain on empty', c('imported_payee', 'doesNotContain', 'mktp'), { imported_payee: null }, null, 'notes');
  conditionCase('imported_payee oneOf', c('imported_payee', 'oneOf', ['Grocer', 'Amazon']), { imported_payee: 'amazon' }, { imported_payee: 'amazon mktp' }, 'notes');
  conditionCase('imported_payee notOneOf', c('imported_payee', 'notOneOf', ['Grocer', 'Amazon']), { imported_payee: 'landlord' }, { imported_payee: 'GROCER' }, 'notes');
  conditionCase('imported_payee matches (pattern case)', c('imported_payee', 'matches', '^AMZ\\d+'), { imported_payee: 'AMZ123' }, { imported_payee: 'amz123' }, 'notes');
  conditionCase('imported_payee matches lower pattern', c('imported_payee', 'matches', '^amz\\d+'), { imported_payee: 'AMZ123' }, { imported_payee: 'xamz1' }, 'notes');
  conditionCase('imported_payee matches invalid regex', c('imported_payee', 'matches', '(['), { imported_payee: '([' }, null, 'notes');

  // payee (id)
  conditionCase('payee is', c('payee', 'is', payees.amazon), { payee: payees.amazon }, { payee: payees.grocer });
  conditionCase('payee isNot', c('payee', 'isNot', payees.amazon), { payee: payees.grocer }, { payee: payees.amazon });
  conditionCase('payee oneOf', c('payee', 'oneOf', [payees.amazon, payees.landlord]), { payee: payees.landlord }, { payee: payees.grocer });
  conditionCase('payee notOneOf', c('payee', 'notOneOf', [payees.amazon, payees.landlord]), { payee: payees.grocer }, { payee: payees.amazon });
  conditionCase('payee contains a name fragment', c('payee', 'contains', 'Amazon'), { payee: payees.amazon }, { payee: payees.grocer });
  conditionCase('payee doesNotContain a name fragment', c('payee', 'doesNotContain', 'Amazon'), { payee: payees.amazon }, { payee: payees.grocer });
  conditionCase('payee matches a name pattern', c('payee', 'matches', '^amazon'), { payee: payees.amazon }, { payee: payees.grocer });
  conditionCase('payee is empty', c('payee', 'is', null), { payee: null }, { payee: payees.grocer });

  // notes (string)
  conditionCase('notes is', c('notes', 'is', 'Weekly Shop'), { notes: 'weekly shop' }, { notes: 'weekly shop!' });
  conditionCase('notes is empty', c('notes', 'is', ''), { notes: null }, { notes: 'x' });
  conditionCase('notes isNot empty', c('notes', 'isNot', ''), { notes: 'x' }, { notes: null });
  conditionCase('notes contains', c('notes', 'contains', 'Shop'), { notes: 'weekly shop' }, { notes: 'weekly' });
  conditionCase('notes doesNotContain', c('notes', 'doesNotContain', 'shop'), { notes: 'weekly' }, { notes: 'SHOP' });
  conditionCase('notes hasTags (all)', c('notes', 'hasTags', '#food #weekly'), { notes: 'bought #food #weekly' }, { notes: 'bought #food' });
  conditionCase('notes hasTags case and prefix', c('notes', 'hasTags', '#Food'), { notes: 'x #food' }, { notes: 'x #foodie' });
  conditionCase('notes hasAnyTag', c('notes', 'hasAnyTag', '#food #fun'), { notes: 'x #fun' }, { notes: 'x #misc' });

  // category / category_group (id)
  conditionCase('category is', c('category', 'is', categories.fun), { category: categories.fun }, { category: categories.food });
  conditionCase('category isNot', c('category', 'isNot', categories.fun), { category: categories.food }, { category: categories.fun });
  conditionCase('category oneOf', c('category', 'oneOf', [categories.fun, categories.misc]), { category: categories.misc }, { category: categories.food });
  conditionCase('category is empty', c('category', 'is', null), { category: null }, { category: categories.food });
  conditionCase('category is empty on a transfer', c('category', 'is', null), { category: null, payee: tokensReverse('payee-transfer-account-other') }, null);
  conditionCase('category isNot empty', c('category', 'isNot', null), { category: categories.food }, { category: null });
  conditionCase('category contains a name fragment', c('category', 'contains', 'Foo'), { category: categories.food }, { category: categories.fun });
  conditionCase('category_group is', c('category_group', 'is', groups.other), { category: categories.misc }, { category: categories.food });
  conditionCase('category_group isNot', c('category_group', 'isNot', groups.other), { category: categories.food }, { category: categories.misc });

  // account (id)
  conditionCase('account is', c('account', 'is', accounts.other), { account: accounts.other }, { account: accounts.on });
  conditionCase('account oneOf', c('account', 'oneOf', [accounts.other, accounts.off]), { account: accounts.off }, { account: accounts.on });
  conditionCase('account contains a name fragment', c('account', 'contains', 'Second'), { account: accounts.other }, { account: accounts.on });
  conditionCase('account onBudget', c('account', 'onBudget', null), { account: accounts.on }, { account: accounts.off });
  conditionCase('account offBudget', c('account', 'offBudget', null), { account: accounts.off }, { account: accounts.on });

  // amount (number), cents
  conditionCase('amount is', c('amount', 'is', -2500), { amount: -2500 }, { amount: 2500 });
  conditionCase('amount isapprox (7.5%)', c('amount', 'isapprox', -10000), { amount: -10750 }, { amount: -10751 });
  conditionCase('amount isapprox rounding edge', c('amount', 'isapprox', -1010), { amount: -1086 }, { amount: -1087 });
  conditionCase('amount isbetween (unordered bounds)', c('amount', 'isbetween', { num1: -1000, num2: -3000 }), { amount: -3000 }, { amount: -3001 });
  conditionCase('amount gt', c('amount', 'gt', -2500), { amount: -2499 }, { amount: -2500 });
  conditionCase('amount gte', c('amount', 'gte', -2500), { amount: -2500 }, { amount: -2501 });
  conditionCase('amount lt', c('amount', 'lt', -2500), { amount: -2501 }, { amount: -2500 });
  conditionCase('amount lte', c('amount', 'lte', -2500), { amount: -2500 }, { amount: -2499 });
  conditionCase('amount outflow is', c('amount', 'is', 2500, { outflow: true }), { amount: -2500 }, { amount: 2500 });
  conditionCase('amount outflow gt', c('amount', 'gt', 2000, { outflow: true }), { amount: -2500 }, { amount: 2500 });
  conditionCase('amount inflow is', c('amount', 'is', 2500, { inflow: true }), { amount: 2500 }, { amount: -2500 });
  conditionCase('amount inflow lt', c('amount', 'lt', 3000, { inflow: true }), { amount: 2500 }, { amount: -100 });

  // date
  conditionCase('date is (day)', c('date', 'is', '2026-08-15'), { date: '2026-08-15' }, { date: '2026-08-16' });
  conditionCase('date is (month)', c('date', 'is', '2026-08'), { date: '2026-08-31' }, { date: '2026-09-01' });
  conditionCase('date is (year)', c('date', 'is', '2026'), { date: '2026-01-01' }, { date: '2025-12-31' });
  conditionCase('date isapprox (±2 days)', c('date', 'isapprox', '2026-08-15'), { date: '2026-08-17' }, { date: '2026-08-18' });
  conditionCase('date gt', c('date', 'gt', '2026-08-15'), { date: '2026-08-16' }, { date: '2026-08-15' });
  conditionCase('date gte', c('date', 'gte', '2026-08-15'), { date: '2026-08-15' }, { date: '2026-08-14' });
  conditionCase('date lt', c('date', 'lt', '2026-08-15'), { date: '2026-08-14' }, { date: '2026-08-15' });
  conditionCase('date lte', c('date', 'lte', '2026-08-15'), { date: '2026-08-15' }, { date: '2026-08-16' });
  conditionCase('date is recurring', c('date', 'is', recur('2026-08-15')), { date: '2026-09-15' }, { date: '2026-09-16' });
  conditionCase('date isapprox recurring', c('date', 'isapprox', recur('2026-08-15')), { date: '2026-09-13' }, { date: '2026-09-12' });

  // booleans
  conditionCase('cleared is true', c('cleared', 'is', true), { cleared: true }, { cleared: false });
  conditionCase('transfer is true', c('transfer', 'is', true), { payee: tokensReverse('payee-transfer-account-other'), category: null }, { payee: payees.grocer });

  // conditionsOp or
  CASES.push({
    name: 'conditionsOp or',
    rules: [{ conditionsOp: 'or', conditions: [c('imported_payee', 'is', 'or-a'), c('imported_payee', 'is', 'or-b')], actions: hit }],
    transactions: [{ ...base, imported_payee: 'or-b' }, { ...base, imported_payee: 'or-c' }],
  });

  // Actions
  const actionCase = (name, actions, transaction = {}) => {
    const tag = `act-${CASES.length}`;
    CASES.push({ name, rules: [{ conditions: [c('imported_payee', 'is', tag)], actions }], transactions: [{ ...base, imported_payee: tag, ...transaction }] });
  };
  actionCase('set category', [set('category', categories.misc)]);
  actionCase('set payee', [set('payee', payees.landlord)]);
  actionCase('set payee_name to an existing payee', [set('payee_name', 'landlord')]);
  actionCase('set payee_name to a new payee', [set('payee_name', 'Brand New Payee')]);
  actionCase('set notes', [set('notes', 'rule notes')], { notes: 'typed' });
  actionCase('prepend-notes', [{ op: 'prepend-notes', field: 'notes', value: 'pre ' }], { notes: 'typed' });
  actionCase('prepend-notes onto empty', [{ op: 'prepend-notes', field: 'notes', value: 'pre ' }], { notes: null });
  actionCase('append-notes', [{ op: 'append-notes', field: 'notes', value: ' post' }], { notes: 'typed' });
  actionCase('set amount', [set('amount', -1234)]);
  actionCase('set date', [set('date', '2026-08-20')]);
  actionCase('set cleared', [set('cleared', true)]);
  actionCase('set account', [set('account', accounts.other)]);
  actionCase('link-schedule', [{ op: 'link-schedule', value: schedules.s1 }]);
  actionCase('delete-transaction', [{ op: 'delete-transaction', value: null }]);
  actionCase('set notes from a template', [set('notes', '', { template: '{{imported_payee}} via template' })]);
  actionCase('set amount from a formula', [set('amount', 0, { formula: '=amount*2' })]);
  actionCase('split by remainder', [
    { op: 'set-split-amount', value: null, options: { splitIndex: 1, method: 'remainder' } },
    set('category', categories.food, { splitIndex: 1 }),
    { op: 'set-split-amount', value: null, options: { splitIndex: 2, method: 'remainder' } },
    set('category', categories.fun, { splitIndex: 2 }),
  ]);

  // Ordering: stages, then ascending score within a stage.
  CASES.push({
    name: 'stage order: pre, default, post',
    rules: [
      { stage: 'post', conditions: [c('imported_payee', 'is', 'stage-x')], actions: [{ op: 'append-notes', field: 'notes', value: ' post' }] },
      { stage: null, conditions: [c('imported_payee', 'is', 'stage-x')], actions: [{ op: 'append-notes', field: 'notes', value: ' default' }] },
      { stage: 'pre', conditions: [c('imported_payee', 'is', 'stage-x')], actions: [{ op: 'append-notes', field: 'notes', value: ' pre' }] },
    ],
    transactions: [{ ...base, imported_payee: 'stage-x', notes: 'start' }],
  });
  CASES.push({
    name: 'score order: less specific rule runs first',
    rules: [
      { conditions: [c('imported_payee', 'is', 'score-x'), c('amount', 'is', -2500)], actions: [set('category', categories.fun)] },
      { conditions: [c('imported_payee', 'contains', 'score-x')], actions: [set('category', categories.misc)] },
    ],
    transactions: [{ ...base, imported_payee: 'score-x' }],
  });
  CASES.push({
    name: 'a later rule sees an earlier rule\'s changes',
    rules: [
      { stage: 'pre', conditions: [c('imported_payee', 'is', 'chain-x')], actions: [set('category', categories.misc)] },
      { conditions: [c('category', 'is', categories.misc), c('imported_payee', 'is', 'chain-x')], actions: [set('notes', 'chained')] },
    ],
    transactions: [{ ...base, imported_payee: 'chain-x' }],
  });

  // Schedules: the linked schedule's rule runs without its conditions; other schedules' rules don't run.
  CASES.push({
    name: 'schedule-linked transaction: own rule bypasses conditions, other schedule rules skipped',
    rules: [],
    transactions: [{ ...base, payee: payees.grocer, amount: -4200, date: '2026-09-15', schedule: schedules.s1 }],
  });
  CASES.push({
    name: 'unlinked transaction matching a schedule rule',
    rules: [],
    transactions: [{ ...base, payee: payees.grocer, amount: -4200, date: '2026-09-15' }],
  });

  function tokensReverse(name) {
    for (const [id, t] of tokens) if (t === name) return id;
    throw new Error(`No id for ${name}`);
  }

  // Store every case's rules, then run every transaction.
  for (const [caseIndex, testCase] of CASES.entries()) {
    for (const [ruleIndex, rule] of testCase.rules.entries()) {
      const added = await send('rule-add', { stage: rule.stage ?? null, conditionsOp: rule.conditionsOp ?? 'and', conditions: rule.conditions, actions: rule.actions });
      token(added.id, `rule-${caseIndex}-${ruleIndex}`);
    }
  }
  const cases = [];
  for (const [caseIndex, testCase] of CASES.entries()) {
    for (const [i, input] of testCase.transactions.entries()) {
      const transaction = { id: `transaction-${caseIndex}-${i}`, ...input };
      const output = await actual.send('rules-run', { transaction });
      cases.push({ name: testCase.name, input: transaction, output: pick(output) });
    }
  }

  const payeeRows = await actual.db.all('SELECT id, name, transfer_acct FROM payees WHERE tombstone = 0');
  payeeRows.forEach((payee, index) => { if (!tokens.has(payee.id)) token(payee.id, `payee-created-${index}`); });
  const fixture = {
    upstream: '@actual-app/api 26.9.0 (actualbudget/actual 59fe126f)',
    generatedBy: 'docs/tools/rules-fixture/generate.mjs',
    entities: {
      accounts: await actual.db.all('SELECT id, name, offbudget FROM accounts WHERE tombstone = 0 ORDER BY sort_order'),
      payees: payeeRows,
      categoryGroups: await actual.db.all("SELECT id, name FROM category_groups WHERE tombstone = 0 AND name IN ('Parity Group', 'Other Group')"),
      categories: await actual.db.all("SELECT id, name, cat_group FROM categories WHERE tombstone = 0 AND name IN ('Food', 'Fun', 'Hit', 'Misc')"),
      schedules: await actual.db.all('SELECT id, rule FROM schedules WHERE tombstone = 0'),
    },
    rules: await actual.db.all('SELECT id, stage, conditions_op, conditions, actions FROM rules WHERE tombstone = 0'),
    cases,
  };
  let json = JSON.stringify(fixture, null, 2);
  for (const [id, name] of tokens) json = json.split(id).join(name);
  // Sort rules by token so the file is stable.
  const stable = JSON.parse(json);
  stable.rules.sort((a, b) => a.id.localeCompare(b.id));
  stable.entities.payees.sort((a, b) => a.id.localeCompare(b.id));
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, `${JSON.stringify(stable, null, 2)}\n`);
  console.log(`Wrote ${cases.length} cases to ${OUT}`);
} finally {
  await api.shutdown();
}

/** The transaction fields a rule can change, as the PWA would save them. */
function pick(t) {
  return {
    account: t.account ?? null,
    date: t.date ?? null,
    amount: t.amount ?? null,
    payee: t.payee ?? null,
    payee_name: t.payee_name ?? null,
    category: t.category ?? null,
    notes: t.notes ?? null,
    cleared: t.cleared ?? null,
    schedule: t.schedule ?? null,
    tombstone: t.tombstone ? 1 : 0,
    subtransactions: (t.subtransactions ?? []).map((s) => ({ amount: s.amount, category: s.category ?? null })),
  };
}
