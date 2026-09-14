# Actual v26.9.0 compatibility audit

This audit pins issue #109's backend review to Actual Budget
[v26.9.0](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
(commit `59fe126f637d858c061e1eeedbef5436c8f2225a`). The review compares Actua's
portable implementation with the tagged `packages/crdt`, `packages/loot-core`,
and `packages/sync-server` sources rather than a moving default branch.

## Results

| Boundary | Upstream source reviewed | Result |
| --- | --- | --- |
| Budget schema and migrations | `packages/loot-core/migrations` | Updated Actua's upgrade path and blank-budget schema for migration `1783004650757` (`schedules.sort_order`) and `1787013118115` (`account_groups` and `accounts.account_group_id`). Unknown account-group rows and fields now have a destination during sync even though Actua does not yet present account-group UI. |
| CRDT and sync wire format | `packages/crdt/src/proto/sync.proto`, `packages/sync-server/src/app-sync.ts` | Compatible. Actua retains the current envelope fields, reserved request field 4, `keyId`/`since`, Merkle exchange, HLC ordering, and string CRDT value encoding. Existing upstream-derived binary fixtures cover encoding and convergence. |
| Encryption | `packages/loot-core/src/server/encryption`, CRDT encrypted envelopes | Compatible. Key derivation, AES-GCM envelope fields, key validation, encrypted download, and sync-message fixtures remain covered. Keys remain in Android Keystore-backed storage. |
| Rules | `packages/loot-core/src/shared/rules.ts`, `src/server/rules` | Compatible for Actua's documented supported condition/action subset. Unknown advanced formula/template actions remain preserved in stored JSON and are not advertised as editable. |
| Scheduled transactions | `packages/loot-core/src/shared/schedules.ts`, schedule migrations | Compatible for the documented recurrence and posting subset. The new upstream sort order is now migrated and included in newly created budgets. |
| Payee locations | migration `1768872504000`, `src/shared/location-utils.ts` | Compatible. Table shape, tombstones, distance calculation, 500-metre matching, and CRDT writes remain covered. |
| Dashboards and reports | report/dashboard migrations and report models | Compatible for Actua's documented native cards. Current `trim_intervals` and `show_trend_lines` fields are present; unknown future widget types remain disclosed rather than misrendered. |
| Backup and restore | `packages/loot-core/src/server/budgetfiles/backups.ts` | Compatible at the archive boundary. Actua validates `db.sqlite` and metadata, preserves cloud identity rules, strips sync state only for private local snapshots, and keeps restore/revert coverage. |

## Compatibility policy

Actua may safely ignore a new upstream table only when it neither reads nor writes
that feature and the table still exists locally for incoming CRDT rows. Columns
used by Actua must be added transactionally and the latest stored CRDT value must
be replayed when an older downloaded budget is upgraded. Blank budgets must carry
the complete audited migration IDs so Actual does not mistake their schema level.

This audit does not claim UI parity for account groups, bank feeds, advanced rule
actions, or every report implementation. Those boundaries remain documented in
`BACKEND_PARITY.md`.
