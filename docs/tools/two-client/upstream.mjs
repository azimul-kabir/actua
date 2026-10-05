// Upstream side of the #663 two-client check: bootstraps a fresh actual-server, creates the
// synthetic budget both clients share, then makes each scenario's edit through Actual's own
// loot-core (@actual-app/api, the same `transactions-batch-update` handler and shared
// `updateTransaction`/`deleteTransaction` helpers the PWA uses) in the "Upstream …" accounts.
// TwoClientTransactionParityTest.kt makes the same edits through Actua in the "Actua …" accounts.
import { randomUUID } from 'node:crypto';
import * as api from '@actual-app/api';
import {
  BUDGET_NAME, CATEGORIES, CATEGORY_GROUP, PASSWORD, PAYEES, ROLES, SERVER_URL, SIDES, aug, dataDir, sep,
} from './common.mjs';

async function bootstrap() {
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

await bootstrap();
const actual = await api.init({ dataDir: dataDir('upstream'), serverURL: SERVER_URL, password: PASSWORD });
try {
  // create-budget uploads the new file, so Actua and the comparison client can download it.
  const created = await actual.send('create-budget', { budgetName: BUDGET_NAME });
  if (created?.error) throw new Error(`create-budget failed: ${created.error}`);
  const remote = (await actual.send('get-remote-files'))?.find((file) => file.name === BUDGET_NAME);
  if (!remote) throw new Error('The new budget was not uploaded');

  const accounts = {};
  for (const side of Object.values(SIDES)) {
    for (const { role, offbudget } of ROLES) {
      accounts[`${side} ${role}`] = await api.createAccount({ name: `${side} ${role}`, offbudget });
    }
  }
  const group = await api.createCategoryGroup({ name: CATEGORY_GROUP, is_income: false });
  const categories = {};
  for (const name of CATEGORIES) categories[name] = await api.createCategory({ name, group_id: group, is_income: false });
  const payees = {};
  for (const name of PAYEES) payees[name] = await api.createPayee({ name });
  const transferPayees = Object.fromEntries(
    (await api.getPayees()).filter((payee) => payee.transfer_acct).map((payee) => [payee.transfer_acct, payee.id]),
  );

  const acct = (role) => accounts[`${SIDES.upstream} ${role}`];
  const transferTo = (role) => transferPayees[acct(role)];
  const FOOD = categories['Parity Food'];
  const RENT = categories['Parity Rent'];
  const [STORE, LANDLORD, MARKET] = PAYEES.map((name) => payees[name]);

  // A new row as the PWA mobile editor saves it (TransactionEdit.tsx: uncleared draft, realized id).
  const row = (fields) => ({ id: randomUUID(), account: acct('Checking'), cleared: false, ...fields });
  // LC/shared/transactions.ts `makeChild` at 59fe126f (the fields a new child row carries).
  const makeChild = (parent, data) => ({
    amount: 0,
    ...data,
    category: 'category' in data ? data.category : parent.category,
    payee: 'payee' in data ? data.payee : parent.payee,
    id: randomUUID(),
    account: parent.account,
    date: parent.date,
    cleared: parent.cleared ?? null,
    is_child: true,
    parent_id: parent.id,
  });
  // A new split: the parent has no category; children are sorted -1, -2, … like the mobile editor's.
  const split = (fields, children) => {
    const parent = row({ ...fields, is_parent: true, category: null });
    return [parent, ...children.map((data, index) => makeChild(parent, { sort_order: -(index + 1), ...data }))];
  };
  const insert = (...rows) => actual.send('transactions-batch-update', { added: rows });
  const update = (id, fields) => api.updateTransaction(id, fields);
  const children = [
    { amount: -600, category: FOOD },
    { amount: -400, category: RENT, payee: LANDLORD, notes: 'rent part' },
  ];

  // 1
  await insert(row({ date: aug(1), amount: -1234, payee: STORE, category: FOOD, notes: 's1' }));
  // 2
  const t2 = row({ date: aug(2), amount: -1234, payee: STORE, category: FOOD, notes: 's2' });
  await insert(t2);
  await update(t2.id, { amount: -2000, payee: LANDLORD, category: RENT, notes: 's2 edited', date: sep(2), cleared: true });
  // 3
  await insert(row({ date: aug(3), amount: 5000, payee: STORE, notes: 's3' }));
  // 4
  await insert(row({ date: aug(4), amount: -2500, payee: transferTo('Savings'), notes: 's4', cleared: true }));
  // 5
  const t5 = row({ date: aug(5), amount: -2500, payee: transferTo('Savings'), notes: 's5', cleared: true });
  await insert(t5);
  await update(t5.id, { amount: -3000, notes: 's5 edited', date: sep(5), cleared: false });
  // 6
  await insert(row({ date: aug(6), amount: -4000, payee: transferTo('Brokerage'), category: FOOD, notes: 's6' }));
  // 7
  const t7 = row({ date: aug(7), amount: -1000, payee: transferTo('Savings'), notes: 's7' });
  await insert(t7);
  await api.deleteTransaction(t7.id);
  // 8
  const t8 = row({ date: aug(8), amount: -1000, payee: transferTo('Savings'), notes: 's8' });
  await insert(t8);
  await update(t8.id, { payee: STORE, category: FOOD });
  // 9
  const t9 = row({ date: aug(9), amount: -1500, payee: STORE, category: FOOD, notes: 's9', cleared: true });
  await insert(t9);
  await update(t9.id, { payee: transferTo('Savings') });
  // 10
  await insert(...split({ date: aug(10), amount: -1000, payee: STORE, notes: 's10' }, children));
  // 11
  const s11 = split({ date: aug(11), amount: -1000, payee: STORE, notes: 's11' }, children);
  await insert(...s11);
  await update(s11[0].id, { payee: MARKET, date: sep(11), cleared: true });
  // 12
  const s12 = split({ date: aug(12), amount: -1000, payee: STORE, notes: 's12' }, children);
  await insert(...s12);
  await api.deleteTransaction(s12[0].id);
  // 13
  await insert(row({ date: aug(13), amount: 0, payee: STORE, category: FOOD, notes: 's13' }));
  // 14
  const t14 = row({ date: aug(14), amount: -500, payee: STORE, category: FOOD, notes: 's14' });
  await insert(t14);
  await update(t14.id, { cleared: true });
  // 15
  const t15 = row({ date: aug(15), amount: -700, payee: transferTo('Savings'), notes: 's15' });
  await insert(t15);
  await update(t15.id, { cleared: true });
  // 16
  const t16 = row({ date: aug(16), amount: -800, payee: STORE, category: FOOD, notes: 's16' });
  await insert(t16);
  await update(t16.id, { account: acct('Savings') });
  // 17
  const t17 = row({ date: aug(17), amount: -900, payee: STORE, category: FOOD, notes: 's17' });
  await insert(t17);
  await update(t17.id, { account: acct('Brokerage') });

  await api.sync();
  console.log(`Upstream side done: budget ${remote.groupId}`);
} finally {
  await api.shutdown();
}
