// Shared settings for the #663 two-client check. Everything here is synthetic.
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

export const SERVER_URL = process.env.TWO_CLIENT_SERVER_URL ?? 'http://localhost:5006';
// A throwaway password for a throwaway CI server; not a credential.
export const PASSWORD = process.env.TWO_CLIENT_PASSWORD ?? 'two-client-parity';
export const BUDGET_NAME = process.env.TWO_CLIENT_BUDGET ?? 'Two-client parity';

/** Account-name prefix for the rows each client writes; the rest of the name is the account's role. */
export const SIDES = { upstream: 'Upstream', actua: 'Actua' };
export const ROLES = [
  { role: 'Checking', offbudget: false },
  { role: 'Savings', offbudget: false },
  { role: 'Brokerage', offbudget: true },
];
export const CATEGORY_GROUP = 'Parity';
export const CATEGORIES = ['Parity Food', 'Parity Rent'];
export const PAYEES = ['Parity Store', 'Parity Landlord', 'Parity Market'];

/**
 * Scenario n writes its rows on 2026-08-n and may move them to 2026-09-n, so the day of the month
 * identifies the scenario. Keep in step with TwoClientTransactionParityTest.kt and README.md.
 */
export const SCENARIOS = {
  1: 'Create an expense',
  2: 'Edit an expense (amount, payee, category, notes, date, cleared)',
  3: 'Create an income with no category',
  4: 'Create a transfer between on-budget accounts',
  5: 'Edit a transfer leg (amount, notes, date, cleared)',
  6: 'Create an on-budget → off-budget transfer with a category',
  7: 'Delete a transfer leg',
  8: 'Change a transfer leg to a regular payee',
  9: 'Change an expense into a transfer',
  10: 'Create a split with an inherited and an own child payee',
  11: 'Edit a split parent (payee, date, cleared)',
  12: 'Delete a split parent',
  13: 'Create a zero-amount expense',
  14: 'Toggle cleared on an expense',
  15: 'Toggle cleared on a transfer leg',
  16: 'Move an expense to another on-budget account',
  17: 'Move a categorized expense to an off-budget account',
};

export const aug = (day) => `2026-08-${String(day).padStart(2, '0')}`;
export const sep = (day) => `2026-09-${String(day).padStart(2, '0')}`;

export function dataDir(name) {
  const dir = resolve(process.env.TWO_CLIENT_WORK_DIR ?? 'work', name);
  mkdirSync(dir, { recursive: true });
  return dir;
}
