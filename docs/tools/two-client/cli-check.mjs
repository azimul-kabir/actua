// Independent read-back of the #663 two-client budget through Actual's official CLI
// (@actual-app/cli, pinned in package.json to the server's version). Run after both sides have
// synced. The CLI is a separate reader from compare.mjs, which opens the downloaded SQLite file:
// this one goes through the CLI's own download, sync and AQL query path, the way a script or
// another Actual user would see the budget. All data is synthetic.
//
// Checks:
//   1. Each account's balance from `accounts list` equals the sum of its non-parent transactions
//      from `query run` (the CLI agrees with itself).
//   2. Each role's balance on the "Actua …" side equals the "Upstream …" side, because both sides
//      make the same edits (the two sides must have converged on the same numbers).
//   3. The Actua accounts are not empty, so a run where Actua never synced cannot pass.
import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, resolve } from 'node:path';
import { BUDGET_NAME, PASSWORD, ROLES, SERVER_URL, SIDES, dataDir } from './common.mjs';

const cliDir = dataDir('cli');
mkdirSync(cliDir, { recursive: true });
const cli = resolve('node_modules/.bin/actual');

function run(args, { syncId, input } = {}) {
  const out = execFileSync(cli, ['--format', 'json', '--no-cache', ...args], {
    encoding: 'utf8',
    input,
    env: {
      ...process.env,
      ACTUAL_SERVER_URL: SERVER_URL,
      ACTUAL_PASSWORD: PASSWORD,
      ACTUAL_DATA_DIR: cliDir,
      ...(syncId ? { ACTUAL_SYNC_ID: syncId } : {}),
    },
  });
  return JSON.parse(out);
}

const budget = run(['budgets', 'list']).find((b) => b.name === BUDGET_NAME);
if (!budget) throw new Error(`No "${BUDGET_NAME}" budget on ${SERVER_URL}`);
const syncId = budget.groupId;

const accounts = run(['accounts', 'list'], { syncId });
const sums = run(['query', 'run', '--file', '-'], {
  syncId,
  input: JSON.stringify({
    table: 'transactions',
    select: ['account.name', { total: { $sum: '$amount' } }],
    groupBy: ['account.name'],
    filter: { is_parent: false },
  }),
});
const queried = new Map(sums.map((row) => [row['account.name'], row.total]));
const balanceOf = (name) => accounts.find((a) => a.name === name)?.balance;

const failures = [];
const report = ['## Actual CLI read-back (#663)', '',
  `\`@actual-app/cli\` 26.9.0 against the synced "${BUDGET_NAME}" budget.`, '',
  '| Account | `accounts list` balance | `query run` sum |', '| --- | --- | --- |'];
for (const account of accounts) {
  const sum = queried.get(account.name) ?? 0;
  const ok = account.balance === sum;
  if (!ok) failures.push(`${account.name}: accounts list ${account.balance}, query run ${sum}`);
  report.push(`| ${account.name} | ${account.balance} | ${sum}${ok ? '' : ' ❌'} |`);
}

report.push('', '| Role | Upstream | Actua |', '| --- | --- | --- |');
for (const { role } of ROLES) {
  const upstream = balanceOf(`${SIDES.upstream} ${role}`);
  const actua = balanceOf(`${SIDES.actua} ${role}`);
  const ok = upstream !== undefined && upstream === actua;
  if (!ok) failures.push(`${role}: Upstream ${upstream}, Actua ${actua}`);
  report.push(`| ${role} | ${upstream} | ${actua}${ok ? '' : ' ❌'} |`);
}
const actuaRows = sums.filter((row) => row['account.name'].startsWith(`${SIDES.actua} `)).length;
if (actuaRows === 0) failures.push('The CLI sees no transactions in the Actua accounts (did Actua sync?)');

const markdown = report.join('\n');
console.log(markdown);
writeFileSync(join(dataDir('.'), 'cli-check-report.md'), markdown);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n`);
if (failures.length) {
  console.error(`CLI read-back failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
