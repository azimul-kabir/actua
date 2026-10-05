// Comparison step of the #663 two-client check. A third, fresh client downloads the budget after
// both sides have synced, then each scenario's "Upstream …" rows are compared with its "Actua …"
// rows (ids replaced by what they point at). Row differences fail the check; differences in which
// columns carry CRDT messages are reported but don't fail it, because Actua's inserts write
// explicit null/zero cells that Actual skips (docs/TRANSACTIONS_PARITY.md §1, Intentional).
import { appendFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import * as api from '@actual-app/api';
import { BUDGET_NAME, PASSWORD, SCENARIOS, SERVER_URL, SIDES, dataDir } from './common.mjs';

const dir = dataDir('compare');
const actual = await api.init({ dataDir: dir, serverURL: SERVER_URL, password: PASSWORD });
let budgetId;
try {
  const remote = (await actual.send('get-remote-files'))?.find((file) => file.name === BUDGET_NAME);
  if (!remote) throw new Error(`No "${BUDGET_NAME}" budget on ${SERVER_URL}`);
  await api.downloadBudget(remote.groupId);
  await api.sync();
  budgetId = (await actual.send('get-budgets')).find((budget) => budget.groupId === remote.groupId)?.id;
} finally {
  await api.shutdown();
}
const budgetDir = budgetId ? join(dir, budgetId) : join(dir, readdirSync(dir).find((name) => !name.startsWith('.')));
const db = new DatabaseSync(join(budgetDir, 'db.sqlite'), { readOnly: true });

const accounts = new Map(db.prepare('SELECT id, name FROM accounts').all().map((a) => [a.id, a.name]));
const payees = new Map(db.prepare(
  'SELECT pm.id AS id, p.name AS name, p.transfer_acct AS transfer FROM payee_mapping pm JOIN payees p ON p.id = pm.targetId',
).all().map((p) => [p.id, p]));
const categories = new Map(db.prepare(
  'SELECT cm.id AS id, c.name AS name FROM category_mapping cm JOIN categories c ON c.id = cm.transferId',
).all().map((c) => [c.id, c.name]));
const rows = db.prepare('SELECT * FROM transactions WHERE date BETWEEN 20260801 AND 20260931').all();
const byId = new Map(db.prepare('SELECT * FROM transactions').all().map((t) => [t.id, t]));
const messages = new Map();
for (const m of db.prepare("SELECT row, column FROM messages_crdt WHERE dataset = 'transactions'").all()) {
  const columns = messages.get(m.row) ?? {};
  columns[m.column] = (columns[m.column] ?? 0) + 1;
  messages.set(m.row, columns);
}
db.close();

const sideOf = (accountId) => {
  const name = accounts.get(accountId) ?? '';
  const side = Object.values(SIDES).find((prefix) => name.startsWith(`${prefix} `));
  return side ? { side, role: name.slice(side.length + 1) } : null;
};
const role = (accountId) => sideOf(accountId)?.role ?? `unknown account`;
const flag = (value) => (value ? 1 : 0);
const deleted = (row) => (flag(row.tombstone) ? ' (deleted)' : '');
const payeeLabel = (id) => {
  if (id == null) return null;
  const payee = payees.get(id);
  if (!payee) return `unknown payee`;
  return payee.transfer ? `transfer: ${role(payee.transfer)}` : payee.name;
};
const reference = (id, label) => {
  if (id == null) return null;
  const target = byId.get(id);
  return target ? `${label(target)}${deleted(target)}` : 'dangling';
};

function normalize(row) {
  return {
    account: role(row.acct),
    date: row.date,
    amount: row.amount,
    payee: payeeLabel(row.description),
    category: row.category == null ? null : (categories.get(row.category) ?? 'unknown category'),
    notes: row.notes,
    cleared: row.cleared,
    reconciled: flag(row.reconciled),
    tombstone: flag(row.tombstone),
    isParent: flag(row.isParent),
    isChild: flag(row.isChild),
    parent: reference(row.parent_id, () => 'parent'),
    transfer: reference(row.transferred_id, (target) => `${role(target.acct)} leg`),
    imported_description: row.imported_description,
    financial_id: row.financial_id,
    schedule: row.schedule,
    starting_balance_flag: flag(row.starting_balance_flag),
  };
}
const sortKey = (r) => JSON.stringify([r.account, r.isChild, r.category, r.amount, r.payee, r.tombstone, r.notes]);

const groups = new Map();
for (const row of rows) {
  const owner = sideOf(row.acct);
  if (!owner) continue;
  const scenario = row.date % 100;
  const group = groups.get(scenario) ?? { [SIDES.upstream]: [], [SIDES.actua]: [] };
  group[owner.side].push({ row: normalize(row), messages: messages.get(row.id) ?? {} });
  groups.set(scenario, group);
}

const report = ['## Two-client transaction check (#663)', '',
  `Actual \`@actual-app/api\` 26.9.0 vs Actua, synced through actual-server and read back by a fresh client.`, '',
  '| # | Scenario | Rows | CRDT message columns |', '| --- | --- | --- | --- |'];
const details = [];
const evidence = {};
let failures = 0;
for (const [number, title] of Object.entries(SCENARIOS)) {
  const group = groups.get(Number(number)) ?? { [SIDES.upstream]: [], [SIDES.actua]: [] };
  const upstream = group[SIDES.upstream].sort((a, b) => sortKey(a.row).localeCompare(sortKey(b.row)));
  const actua = group[SIDES.actua].sort((a, b) => sortKey(a.row).localeCompare(sortKey(b.row)));
  evidence[number] = { title, upstream, actua };
  const rowDiffs = [];
  const messageDiffs = [];
  if (upstream.length === 0 || actua.length === 0) {
    rowDiffs.push(`missing rows: upstream ${upstream.length}, Actua ${actua.length}`);
  } else if (upstream.length !== actua.length) {
    rowDiffs.push(`row count: upstream ${upstream.length}, Actua ${actua.length}`);
  } else {
    upstream.forEach((u, index) => {
      const a = actua[index];
      const label = `${u.row.account}${u.row.isParent ? ' parent' : u.row.isChild ? ' child' : ''}`;
      for (const field of Object.keys(u.row)) {
        if (JSON.stringify(u.row[field]) !== JSON.stringify(a.row[field])) {
          rowDiffs.push(`${label} \`${field}\`: upstream ${JSON.stringify(u.row[field])}, Actua ${JSON.stringify(a.row[field])}`);
        }
      }
      const onlyUpstream = Object.keys(u.messages).filter((c) => !(c in a.messages));
      const onlyActua = Object.keys(a.messages).filter((c) => !(c in u.messages));
      if (onlyUpstream.length || onlyActua.length) {
        messageDiffs.push(`${label}: only upstream [${onlyUpstream.join(', ')}], only Actua [${onlyActua.join(', ')}]`);
      }
    });
  }
  if (rowDiffs.length) failures++;
  report.push(`| ${number} | ${title} | ${rowDiffs.length ? `❌ ${rowDiffs.length} difference(s)` : `✅ ${upstream.length} row(s) match`} | ${messageDiffs.length ? 'differ (see below)' : 'same'} |`);
  if (rowDiffs.length || messageDiffs.length) {
    details.push(`### ${number}. ${title}`, '', ...rowDiffs.map((d) => `- Row: ${d}`), ...messageDiffs.map((d) => `- Messages: ${d}`), '');
  }
}
report.push('', ...details);
const markdown = report.join('\n');
console.log(markdown);
writeFileSync(join(dataDir('.'), 'two-client-report.md'), markdown);
writeFileSync(join(dataDir('.'), 'two-client-rows.json'), JSON.stringify(evidence, null, 2));
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n`);
if (failures) {
  console.error(`${failures} scenario(s) differ`);
  process.exit(1);
}
