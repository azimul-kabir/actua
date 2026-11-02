# Actual 26.11 compatibility review

Actua 2.4.0 targets Actual Budget v26.11.0
([`1766241e3f4b3c578e424c4fa15f1eb83c0da17d`](https://github.com/actualbudget/actual/commit/1766241e3f4b3c578e424c4fa15f1eb83c0da17d)).

## Scope and limits

This is a review of the v26.11.0 release notes plus a comparison of `packages/crdt`, `packages/loot-core` (sync,
migrations, rules, schedules, budget, reports, auth, cloud storage) and `packages/sync-server` against Actua. It is
not a full schema or protocol audit: the last complete schema and wire diff was at 26.9.0, documented in
[ACTUAL_26_9_COMPATIBILITY.md](ACTUAL_26_9_COMPATIBILITY.md), and remains the basis for this review. Nothing here
was run against a live 26.11 server or a physical device.

## Findings

- **Wire protocol:** no changes found to CRDT value encoding, protobuf fields, Merkle hashing, HLC timestamps or
  encryption.
- **Migrations:** the 60 migration ids in a new Actua budget are identical to Actual 26.11's. The only addition
  since 26.9 is `1788468782000` (`messages_pending`), introduced by 26.10.

## Ported in 2.4.0

| Area | Change | Reference |
| --- | --- | --- |
| Sync | `messages_pending` table, deferral and replay of changes Actua cannot apply yet | [#1420](https://github.com/azimul-kabir/actua/pull/1420) |
| Rules | Formulas read and write everyday amounts | [#1423](https://github.com/azimul-kabir/actua/pull/1423) |
| Templates | `isSubMonthly` caps for weekly (4) and daily (31) schedules | [#1424](https://github.com/azimul-kabir/actua/pull/1424) |
| Schedules | Next date uses the weekend-moved date | [#1425](https://github.com/azimul-kabir/actua/pull/1425) |
| Schedules | Auto-posting can run again the same day after a create or edit | [#1426](https://github.com/azimul-kabir/actua/pull/1426) |
| Reports | `group_by = Tag` with `metadata.tagScope` | [#1427](https://github.com/azimul-kabir/actua/pull/1427) |
| Server | `POST /account/logout` on disconnect, best effort | [#1428](https://github.com/azimul-kabir/actua/pull/1428) |
| Rules | Category learning skips payees covered by a "one of" rule | [#1429](https://github.com/azimul-kabir/actua/pull/1429) |
| Currencies | NOK, TZS, ZAR; CHF symbol | [#1430](https://github.com/azimul-kabir/actua/pull/1430) |
| Reports | Balance Forecast `yAxisBase` zero-based axis | [#1431](https://github.com/azimul-kabir/actua/pull/1431) |
| Accounts | `hide-reconciled-all` preference | [#1432](https://github.com/azimul-kabir/actua/pull/1432) |
| Budget | Fund goal from To Budget | [#1433](https://github.com/azimul-kabir/actua/pull/1433) |
| Server files | Downloads never reuse an archive id as the destination | [#1419](https://github.com/azimul-kabir/actua/pull/1419) |

## Verified as already matching (by code reading)

- A transfer keeps its category when moved between on-budget and off-budget accounts.
- "Payee is nothing" and "category is nothing" rule conditions.
- Spending Analysis excludes income.

## Not applicable to Actua

Desktop and web UI work (themes, sidebar, settings redesign), sync-server administration, SSRF and password
changes, the public API, YNAB4 import and bank-provider server fixes.

## Not ported or not reviewed in depth

- Monte Carlo investment phases, historical block-bootstrap model and the pot balances chart.
- Automatic bank sync on a timer and per-budget SimpleFIN settings.
- Report interval click-through.
- Import fixes: QIF categories, currency-suffixed amounts, CSV unchecked-match handling, formulas with a BOM.
- Sankey missing-payee fallback.
- Preserving split payees when a schedule posts.

These are tracked in [BACKEND_PARITY.md](../BACKEND_PARITY.md) as deferred rather than represented as working.
