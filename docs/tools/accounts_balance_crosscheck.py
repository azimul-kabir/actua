#!/usr/bin/env python3
"""Cross-check Actua's account balance SQL against Actual Budget's query semantics.

Builds a synthetic, in-memory Actual-shaped SQLite budget (transfers, splits, off-budget and
closed accounts, reconciled rows, tombstones, orphaned split children) and compares:

* Actual v26.9.0 (59fe126f): `accountBalance` / `accountBalanceCleared` /
  `accountBalanceUncleared` / `onBudgetAccountBalance` / `offBudgetAccountBalance` /
  `closedAccountBalance` bindings (desktop-client/src/spreadsheet/bindings.ts) compiled through
  AQL: aggregate queries read `v_transactions_internal_alive` and add `is_parent = 0`
  (loot-core/src/server/aql/schema/{index,executors}.ts). Running balances use
  `$sumOver` (`SUM(amount) OVER (... ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING)`) over
  the account register ordered `date DESC, starting_balance_flag, sort_order DESC, id` with
  `splits: 'none'` (`parent_id IS NULL`).
* Actua: the `ActualBudgetDatabase.fetchAccounts()` balance query and the
  `accountRunningBalances()` fold over `fetchTransactions()` rows
  (`ORDER BY t.date DESC, t.starting_balance_flag, t.sort_order DESC, t.id`).

Run: python3 docs/tools/accounts_balance_crosscheck.py  (exit status 1 on any unexpected mismatch)
All data is synthetic.
"""
import sqlite3
import sys

SCHEMA = """
CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, offbudget INTEGER DEFAULT 0,
  closed INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0);
CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER DEFAULT 0,
  isChild INTEGER DEFAULT 0, acct TEXT, category TEXT, amount INTEGER, description TEXT,
  date INTEGER, starting_balance_flag INTEGER DEFAULT 0, transferred_id TEXT, sort_order REAL,
  tombstone INTEGER DEFAULT 0, cleared INTEGER DEFAULT 1, reconciled INTEGER DEFAULT 0,
  parent_id TEXT);
CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT);
CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT);

-- Upstream v_transactions_internal / v_transactions_internal_alive (aql/schema/index.ts)
CREATE VIEW v_transactions_internal AS
  SELECT _.id, _.isParent AS is_parent, _.isChild AS is_child, _.acct AS account,
         CASE WHEN _.isParent = 1 THEN NULL ELSE cm.transferId END AS category,
         IFNULL(_.amount, 0) AS amount, pm.targetId AS payee, _.date,
         _.starting_balance_flag, _.transferred_id AS transfer_id, _.sort_order,
         _.tombstone, _.cleared, _.reconciled,
         CASE WHEN _.isChild = 0 THEN NULL ELSE _.parent_id END AS parent_id
  FROM transactions _
  LEFT JOIN category_mapping cm ON cm.id = _.category
  LEFT JOIN payee_mapping pm ON pm.id = _.description
  WHERE _.date IS NOT NULL AND _.acct IS NOT NULL AND (_.isChild = 0 OR _.parent_id IS NOT NULL);
CREATE VIEW v_transactions_internal_alive AS
  SELECT _.* FROM v_transactions_internal _
  LEFT JOIN transactions t2 ON (_.is_child = 1 AND t2.id = _.parent_id)
  WHERE IFNULL(_.tombstone, 0) = 0 AND (_.is_child = 0 OR t2.tombstone = 0);
"""

ACCOUNTS = [
    # id, name, offbudget, closed, sort_order
    ("checking", "Checking", 0, 0, 16384),
    ("savings", "Savings", 0, 0, 32768),
    ("card", "Card", 0, 0, 49152),
    ("brokerage", "Brokerage", 1, 0, 16384),
    ("old", "Old account", 0, 1, 65536),
]

T = "transactions"
TX = [
    # id, isParent, isChild, acct, amount, date, sbf, transferred_id, sort, tomb, cleared, reconciled, parent_id
    ("open-chk", 0, 0, "checking", 100000, 20260101, 1, None, 1, 0, 1, 1, None),
    ("rent", 0, 0, "checking", -120000, 20260101, 0, None, 2, 0, 1, 1, None),
    ("pay", 0, 0, "checking", 250000, 20260115, 0, None, 3, 0, 1, 0, None),
    ("uncleared", 0, 0, "checking", -4599, 20260116, 0, None, 4, 0, 0, 0, None),
    # Split: parent + two children (children carry the account's money)
    ("split", 1, 0, "checking", -10000, 20260117, 0, None, 5, 0, 1, 0, None),
    ("split-a", 0, 1, "checking", -6000, 20260117, 0, None, 6, 0, 1, 0, "split"),
    ("split-b", 0, 1, "checking", -4000, 20260117, 0, None, 7, 0, 1, 0, "split"),
    # Tombstoned split child of an alive parent is excluded (parent amount no longer matches)
    ("split2", 1, 0, "checking", -3000, 20260118, 0, None, 8, 0, 0, 0, None),
    ("split2-a", 0, 1, "checking", -3000, 20260118, 0, None, 9, 0, 0, 0, "split2"),
    ("split2-dead", 0, 1, "checking", -700, 20260118, 0, None, 10, 1, 0, 0, "split2"),
    # Child whose parent is tombstoned, and an orphan child with no parent row: both excluded
    ("dead-parent", 1, 0, "checking", -500, 20260119, 0, None, 11, 1, 1, 0, None),
    ("dead-parent-a", 0, 1, "checking", -500, 20260119, 0, None, 12, 0, 1, 0, "dead-parent"),
    ("orphan", 0, 1, "checking", -900, 20260119, 0, None, 13, 0, 1, 0, "missing"),
    # Tombstoned, dateless and amount-NULL rows
    ("deleted", 0, 0, "checking", -77777, 20260120, 0, None, 14, 1, 1, 0, None),
    ("no-date", 0, 0, "checking", -1111, None, 0, None, 15, 0, 1, 0, None),
    ("null-amount", 0, 0, "checking", None, 20260121, 0, None, 16, 0, 1, 0, None),
    # On-budget transfer checking -> savings
    ("xfer-out", 0, 0, "checking", -50000, 20260122, 0, "xfer-in", 17, 0, 1, 0, None),
    ("xfer-in", 0, 0, "savings", 50000, 20260122, 0, "xfer-out", 18, 0, 0, 0, None),
    # On-budget -> off-budget transfer
    ("invest-out", 0, 0, "checking", -25000, 20260123, 0, "invest-in", 19, 0, 1, 0, None),
    ("invest-in", 0, 0, "brokerage", 25000, 20260123, 0, "invest-out", 20, 0, 1, 1, None),
    ("gain", 0, 0, "brokerage", 1234, 20260124, 0, None, 21, 0, 0, 0, None),
    # Credit card with reconciled + cleared + uncleared rows, and a same-day starting balance
    ("card-open", 0, 0, "card", -20000, 20260110, 1, None, 30, 0, 1, 1, None),
    ("card-buy", 0, 0, "card", -1500, 20260110, 0, None, 1, 0, 1, 0, None),
    ("card-buy2", 0, 0, "card", -2500, 20260111, 0, None, 31, 0, 0, 0, None),
    # Closed account keeps its history
    ("old-1", 0, 0, "old", 4200, 20251201, 0, None, 40, 0, 1, 1, None),
]

# Mismatches already filed as issues; remove an entry when its fix lands.
KNOWN_DIVERGENCES: dict[str, str] = {}

UPSTREAM_ACCOUNT = """
  SELECT IFNULL(SUM(amount), 0) FROM v_transactions_internal_alive
  WHERE account = ? AND is_parent = 0 {extra}
"""
ACTUA_ACCOUNTS = """
  SELECT t.acct, COALESCE(SUM(t.amount), 0),
         COALESCE(SUM(CASE WHEN t.cleared = 1 THEN t.amount ELSE 0 END), 0),
         COALESCE(SUM(CASE WHEN t.reconciled = 1 THEN t.amount ELSE 0 END), 0)
  FROM transactions t
  LEFT JOIN transactions p ON p.id = t.parent_id
  WHERE t.acct IS NOT NULL AND t.date IS NOT NULL
    AND (t.tombstone = 0 OR t.tombstone IS NULL)
    AND (t.isChild = 0 OR t.isChild IS NULL OR
         (p.id IS NOT NULL AND (p.tombstone = 0 OR p.tombstone IS NULL)))
    AND (t.isParent = 0 OR t.isParent IS NULL)
  GROUP BY t.acct
"""
UPSTREAM_GROUP = """
  SELECT IFNULL(SUM(t.amount), 0) FROM v_transactions_internal_alive t
  LEFT JOIN accounts a ON a.id = t.account
  WHERE t.is_parent = 0 AND {where}
"""
# $sumOver over the register query (splits: 'none' => parent_id IS NULL)
UPSTREAM_RUNNING = """
  SELECT id, SUM(amount) OVER (ORDER BY date DESC, starting_balance_flag, sort_order DESC, id
                               ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING)
  FROM v_transactions_internal_alive WHERE account = ? AND parent_id IS NULL
  ORDER BY date DESC, starting_balance_flag, sort_order DESC, id
"""
# Actua fetchTransactions() top-level register order
ACTUA_REGISTER = """
  SELECT id, IFNULL(amount, 0) FROM transactions t
  WHERE acct = ? AND date IS NOT NULL AND IFNULL(tombstone, 0) = 0 AND IFNULL(isChild, 0) = 0
  ORDER BY t.date DESC, t.starting_balance_flag, t.sort_order DESC, t.id
"""


def main() -> int:
    db = sqlite3.connect(":memory:")
    db.executescript(SCHEMA)
    db.executemany("INSERT INTO accounts (id, name, offbudget, closed, sort_order) VALUES (?,?,?,?,?)", ACCOUNTS)
    db.executemany(
        f"INSERT INTO {T} (id, isParent, isChild, acct, amount, date, starting_balance_flag, transferred_id,"
        " sort_order, tombstone, cleared, reconciled, parent_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        TX,
    )
    failures = []

    def check(label, upstream, actua):
        known = KNOWN_DIVERGENCES.get(label)
        status = "ok" if upstream == actua else ("KNOWN" if known else "MISMATCH")
        print(f"{status:8} {label:44} upstream={upstream!r} actua={actua!r}" + (f"  [{known}]" if known and upstream != actua else ""))
        if upstream != actua and not known:
            failures.append(label)
        if upstream == actua and known:
            failures.append(f"{label} (fixed; remove from KNOWN_DIVERGENCES)")

    actua = {row[0]: row[1:] for row in db.execute(ACTUA_ACCOUNTS)}
    for account_id, *_ in ACCOUNTS:
        total, cleared, reconciled = actua.get(account_id, (0, 0, 0))
        up_total = db.execute(UPSTREAM_ACCOUNT.format(extra=""), (account_id,)).fetchone()[0]
        up_cleared = db.execute(UPSTREAM_ACCOUNT.format(extra="AND cleared = 1"), (account_id,)).fetchone()[0]
        up_uncleared = db.execute(UPSTREAM_ACCOUNT.format(extra="AND cleared = 0"), (account_id,)).fetchone()[0]
        up_reconciled = db.execute(UPSTREAM_ACCOUNT.format(extra="AND reconciled = 1"), (account_id,)).fetchone()[0]
        check(f"{account_id}: working balance", up_total, total)
        check(f"{account_id}: cleared balance", up_cleared, cleared)
        check(f"{account_id}: uncleared balance", up_uncleared, total - cleared)
        check(f"{account_id}: reconciled balance", up_reconciled, reconciled)

    accounts = {a[0]: a for a in ACCOUNTS}
    groups = {
        "on-budget (open)": ("a.offbudget = 0 AND a.closed = 0", lambda a: a[2] == 0 and a[3] == 0),
        "off-budget (open)": ("a.offbudget = 1 AND a.closed = 0", lambda a: a[2] == 1 and a[3] == 0),
        "closed": ("a.closed = 1", lambda a: a[3] == 1),
        "all open": ("a.closed = 0", lambda a: a[3] == 0),
    }
    for label, (where, keep) in groups.items():
        upstream = db.execute(UPSTREAM_GROUP.format(where=where)).fetchone()[0]
        mine = sum(actua.get(i, (0,))[0] for i, a in accounts.items() if keep(a))
        check(f"total: {label}", upstream, mine)

    for account_id, *_ in ACCOUNTS:
        upstream = dict(db.execute(UPSTREAM_RUNNING, (account_id,)).fetchall())
        rows = db.execute(ACTUA_REGISTER, (account_id,)).fetchall()
        balance, mine = 0, {}
        for tx_id, amount in reversed(rows):  # accountRunningBalances(): fold oldest-first
            balance += amount
            mine[tx_id] = balance
        check(f"{account_id}: running balance per row", upstream, mine)

    print(f"\n{len(failures)} unexpected result(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
