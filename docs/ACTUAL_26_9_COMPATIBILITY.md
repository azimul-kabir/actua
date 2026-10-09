# Actual v26.9.0 compatibility audit

This audit pins issue #109's backend review to Actual Budget
[v26.9.0](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
(commit `59fe126f637d858c061e1eeedbef5436c8f2225a`). The review compares Actua's
portable implementation with the tagged `packages/crdt`, `packages/loot-core`,
and `packages/sync-server` sources rather than a moving default branch.

## Results

| Boundary | Upstream source reviewed | Result |
| --- | --- | --- |
| Budget schema and migrations | `packages/loot-core/migrations` | Blank-budget schema and migration list verified identical to upstream by schema diff; see [docs/DATABASE_ARCHIVE_PARITY.md](DATABASE_ARCHIVE_PARITY.md) for the migration, view, tombstone and archive audit and its filed divergences. Updated Actua's upgrade path and blank-budget schema for migration `1783004650757` (`schedules.sort_order`) and `1787013118115` (`account_groups` and `accounts.account_group_id`). Account-group rows/fields are read and displayed (see [BACKEND_PARITY.md](../BACKEND_PARITY.md)); Actua never creates or reassigns groups itself. |
| CRDT and sync wire format | `packages/crdt/src/proto/sync.proto`, `packages/sync-server/src/app-sync.ts` | Compatible. Actua retains the current envelope fields, reserved request field 4, `keyId`/`since`, Merkle exchange, HLC ordering, and string CRDT value encoding. Existing upstream-derived binary fixtures cover encoding and convergence. |
| Encryption | `packages/loot-core/src/server/encryption`, CRDT encrypted envelopes | Compatible. Key derivation, AES-GCM envelope fields, key validation, encrypted download, and sync-message fixtures remain covered. Keys remain in Android Keystore-backed storage. |
| Rules | `packages/loot-core/src/shared/rules.ts`, `src/server/rules`, `src/server/transactions/transaction-rules.ts` | Compatible for Actua's documented supported condition/action subset; see [docs/RULES_PARITY.md](RULES_PARITY.md) for the itemized condition/action/ranking/orchestration behavior audit, including the schedule-rule bypass/exclusion gap in `runRules`. Template, formula and split actions are applied when rules run, and the rules fixture matches Actual's `runRules` in every case; the rule editor does not author these actions and keeps their stored JSON unchanged. |
| Scheduled transactions | `packages/loot-core/src/shared/schedules.ts`, schedule migrations | Compatible; see [docs/SCHEDULED_TRANSACTIONS_PARITY.md](SCHEDULED_TRANSACTIONS_PARITY.md) for the itemized recurrence/posting/status behavior audit. The new upstream sort order is now migrated and included in newly created budgets. |
| Payee locations | migration `1768872504000`, `src/shared/location-utils.ts` | Compatible. Table shape, tombstones, distance calculation, 500-metre matching, and CRDT writes remain covered. |
| Payees | `packages/loot-core/src/server/payees`, `src/server/db` | Compatible; see [docs/PAYEES_PARITY.md](PAYEES_PARITY.md) for create/rename/merge/delete, mapping resolution, category learning and Suggested Payees. Filed divergences are fixed. |
| Budget and categories | `packages/loot-core/src/server/budget`, `src/server/db` | Compatible; see [docs/BUDGET_PARITY.md](BUDGET_PARITY.md) (month totals checked against an upstream-generated fixture) and [docs/CATEGORIES_PARITY.md](CATEGORIES_PARITY.md). Filed divergences are fixed. |
| Reconciliation | `packages/desktop-client/src/accounts/reconciliation.ts`, `packages/loot-core/src/server/accounts` | Compatible; see [docs/RECONCILIATION_PARITY.md](RECONCILIATION_PARITY.md). Leaving reconciliation without locking writes no `last_reconciled`, a deliberate difference. |
| Bank sync and file import | `packages/loot-core/src/server/accounts/sync.ts`, `src/server/importers` | Compatible for SimpleFIN, GoCardless and file import; see [docs/BANK_SYNC_PARITY.md](BANK_SYNC_PARITY.md). Experimental Enable Banking is out of its scope. |
| Tags, preferences and notes | `packages/loot-core/src/server/tags`, `src/server/preferences`, `src/server/notes` | Compatible; see [docs/tags.md](tags.md) and [docs/PREFERENCES_NOTES_PARITY.md](PREFERENCES_NOTES_PARITY.md). |
| Dashboards and reports | report/dashboard migrations and report models | Compatible for Actua's documented native cards. Current `trim_intervals` and `show_trend_lines` fields are present; unknown future widget types remain disclosed rather than misrendered. |
| Backup and restore | `packages/loot-core/src/server/budgetfiles/backups.ts` | Archive layout, CRDT stripping, retention and restore/revert match. Actua validates `db.sqlite` and metadata, preserves cloud identity rules and keeps restore/revert coverage. Backups list only Actual's migration ids, in order. Budgets last uploaded by an older Actual get Actual's missing migrations when opened ([#719](https://github.com/azimul-kabir/actua/issues/719)). See [docs/DATABASE_ARCHIVE_PARITY.md](DATABASE_ARCHIVE_PARITY.md). |

## Compatibility policy

Actua may safely ignore a new upstream table only when it neither reads nor writes
that feature and the table still exists locally for incoming CRDT rows. Columns
used by Actua must be added transactionally and the latest stored CRDT value must
be replayed when an older downloaded budget is upgraded. Blank budgets must carry
the complete audited migration IDs so Actual does not mistake their schema level.

This audit does not claim UI parity for bank feeds, authoring advanced rule actions, or
every report implementation. Those boundaries remain documented in
`BACKEND_PARITY.md`.
