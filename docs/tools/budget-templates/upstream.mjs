// Upstream side of the #668 budget-template check. Bootstraps a throwaway actual-server, creates two
// identical synthetic budgets through loot-core (@actual-app/api), then runs Actual's own template
// engine on the "upstream" one: apply for August, overwrite for September and month-end cleanup for
// October. BudgetTemplateParityTest.kt runs the same three steps through Actua on the "actua" one.
import * as api from '@actual-app/api';
import { BUDGETS, PASSWORD, SERVER_URL, bootstrap, createSeededBudget, dataDir } from './common.mjs';

await bootstrap();
const actual = await api.init({ dataDir: dataDir('upstream'), serverURL: SERVER_URL, password: PASSWORD });
try {
  await createSeededBudget(actual, BUDGETS.actua);
  await createSeededBudget(actual, BUDGETS.upstream);

  // The PWA's "Apply budget template", "Overwrite with budget template" and "End of month cleanup".
  const steps = [
    ['budget/apply-goal-template', '2026-08'],
    ['budget/overwrite-goal-template', '2026-09'],
    ['budget/cleanup-goal-template', '2026-10'],
  ];
  for (const [handler, month] of steps) {
    const result = await actual.send(handler, { month });
    console.log(`${handler} ${month}: ${JSON.stringify(result)}`);
    if (result?.message === 'template-errors' || result?.type === 'error') {
      throw new Error(`${handler} ${month} reported errors; the seed must stay valid for Actual`);
    }
  }
  await api.sync();
  console.log('Upstream side done');
} finally {
  await api.shutdown();
}
