// Comparison step of the #668 budget-template check. Downloads every synced budget into a fresh
// loot-core client and compares, for every category in the Templates and Cleanup groups and every
// checked month, the budgeted amount and the `goal`/`long_goal` cells. A difference fails the check
// unless known-divergences.json lists it with its issue; a listed difference that no longer occurs
// also fails, so the list can't go stale.
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import * as api from '@actual-app/api';
import { MONTHS, PAIRS, PASSWORD, SERVER_URL, dataDir } from './common.mjs';

const dir = dataDir('compare');
const actual = await api.init({ dataDir: dir, serverURL: SERVER_URL, password: PASSWORD });
const files = {};
try {
  const remotes = await actual.send('get-remote-files');
  for (const name of PAIRS.flatMap((pair) => [pair.upstream, pair.actua])) {
    const remote = remotes?.find((file) => file.name === name);
    if (!remote) throw new Error(`No "${name}" budget on ${SERVER_URL}`);
    await api.downloadBudget(remote.groupId);
    await api.sync();
    const local = (await actual.send('get-budgets')).find((budget) => budget.groupId === remote.groupId);
    files[name] = join(dir, local.id, 'db.sqlite');
  }
} finally {
  await api.shutdown();
}

// Categories of a labelled pair are reported (and listed in known-divergences.json) as "Name (label)".
function readCells(path, label) {
  const db = new DatabaseSync(path, { readOnly: true });
  const categories = db.prepare(`
    SELECT c.id, c.name, g.name AS grp FROM categories c JOIN category_groups g ON g.id = c.cat_group
    WHERE c.tombstone = 0 AND g.name IN ('Templates', 'Cleanup') ORDER BY g.sort_order, c.sort_order`).all();
  const cells = new Map(db.prepare('SELECT * FROM zero_budgets').all().map((row) => [`${row.month}|${row.category}`, row]));
  db.close();
  const result = new Map();
  for (const category of categories) {
    for (const month of MONTHS) {
      const cell = cells.get(`${month.replace('-', '')}|${category.id}`);
      result.set(`${category.name}${label ? ` (${label})` : ''}|${month}`, {
        budgeted: cell?.amount ?? 0,
        goal: cell?.goal ?? null,
        long_goal: cell?.long_goal ? 1 : null,
      });
    }
  }
  return { categories: categories.map((c) => `${c.name}${label ? ` (${label})` : ''}`), result };
}

const cellsOf = (side) => {
  const read = PAIRS.map((pair) => readCells(files[pair[side]], pair.label));
  return { categories: read.flatMap((r) => r.categories), result: new Map(read.flatMap((r) => [...r.result])) };
};
const upstream = cellsOf('upstream');
const actua = cellsOf('actua');
const known = JSON.parse(readFileSync(new URL('./known-divergences.json', import.meta.url)));
const knownKey = (d) => `${d.category}|${d.month}|${d.field}`;
const knownByKey = new Map(known.map((d) => [knownKey(d), d]));

const money = (v) => (v == null ? '–' : (v / 100).toFixed(2));
const rows = [];
const unexpected = [];
const seenKnown = new Set();
for (const name of upstream.categories) {
  for (const month of MONTHS) {
    const u = upstream.result.get(`${name}|${month}`);
    const a = actua.result.get(`${name}|${month}`) ?? { budgeted: 0, goal: null, long_goal: null };
    const diffs = [];
    for (const field of ['budgeted', 'goal', 'long_goal']) {
      if (u[field] === a[field]) continue;
      const key = `${name}|${month}|${field}`;
      const issue = knownByKey.get(key)?.issue;
      if (issue) seenKnown.add(key);
      else unexpected.push(key);
      const label = typeof issue === 'number' ? `#${issue}` : issue;
      diffs.push(`${field}${issue ? ` (${label})` : ' ❌'}`);
    }
    rows.push(`| ${name} | ${month} | ${money(u.budgeted)} | ${money(a.budgeted)} | ${money(u.goal)}${u.long_goal ? ' (long)' : ''} | ${money(a.goal)}${a.long_goal ? ' (long)' : ''} | ${diffs.length ? diffs.join(', ') : '✅'} |`);
  }
}
const stale = known.map(knownKey).filter((key) => !seenKnown.has(key));

const report = [
  '## Budget-template check (#668)', '',
  'Actual `@actual-app/api` 26.9.0 vs Actua on identical synthetic budgets (plus a `hideFraction` pair, labelled "whole units"): apply 2026-08, overwrite 2026-09, month-end cleanup 2026-10.', '',
  `Unexpected differences: ${unexpected.length}. Known divergences still present: ${seenKnown.size}. Known divergences no longer present: ${stale.length}.`, '',
  ...(unexpected.length ? ['### Unexpected', '', ...unexpected.map((key) => `- ${key}`), ''] : []),
  ...(stale.length ? ['### Listed in known-divergences.json but no longer different', '', ...stale.map((key) => `- ${key}`), ''] : []),
  '| Category | Month | Budgeted (Actual) | Budgeted (Actua) | Goal (Actual) | Goal (Actua) | Result |',
  '| --- | --- | --- | --- | --- | --- | --- |',
  ...rows,
].join('\n');
console.log(report);
writeFileSync(join(dataDir('.'), 'budget-templates-report.md'), report);
writeFileSync(join(dataDir('.'), 'budget-templates-cells.json'), JSON.stringify({
  upstream: Object.fromEntries(upstream.result), actua: Object.fromEntries(actua.result),
}, null, 2));
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${report}\n`);
if (unexpected.length || stale.length) process.exit(1);
