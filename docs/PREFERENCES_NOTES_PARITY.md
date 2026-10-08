# Synced preferences, notes and formatting parity

Audit of Actual Budget's synced `preferences` table, the `notes` table and amount/date
formatting against Actua, tracked in [#674](https://github.com/azimul-kabir/actua/issues/674)
(part of [#658](https://github.com/azimul-kabir/actua/issues/658)). Feature behavior behind a
preference (budget type, upcoming schedules, transfer dates, category learning, report weeks) is
audited in the feature's own parity doc; this file records which keys Actua reads or writes and
how.

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0, the commit the other parity docs pin). Links use `LC/` =
  `packages/loot-core/src/`, `DC/` = `packages/desktop-client/src/` and `MIG/` =
  `packages/loot-core/migrations/` at that commit.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Method:** source comparison of `SyncedPrefs`
  ([`LC/types/prefs.ts#L19-L71`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/types/prefs.ts#L19-L71)),
  the preference and notes handlers, `useFormat` and `shared/util.ts` against every
  `preferences`/`notes` query and writer call in Actua (found by searching for the table names).
  No two-client run was made for this audit.
- **Status:** **Honoured** = Actua reads the synced value and behaves like Actual;
  **Device-local** = Actua deliberately uses its own per-device setting instead and never writes
  the key; **Ignored** = Actua has no matching feature and leaves the key untouched;
  **Divergence** = filed as an issue.

## 1. Storage and write paths

Upstream: `preferences/save` writes `{id, value}` through `db.update` (a CRDT message) and
`preferences/get` reads the whole table
([`LC/server/preferences/app.ts#L47-L77`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/preferences/app.ts#L47-L77));
`notes-save` writes `{id, note}` the same way and `notes-get` reads one row
([`LC/server/notes/app.ts#L18-L26`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/notes/app.ts#L18-L26)).
Actua: `ActualEntityWriter.setPreference` / `setNote` (`data/budget/ActualEntityWriter.kt:425-426`),
`allowedFields` (`:545`: `preferences.value`, `notes.note` only).

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| Schema `preferences(id TEXT PRIMARY KEY, value TEXT)` | `MIG/1723665565000_prefs.js` | same (`data/budget/ActualMigrations.kt:256`) | Match |
| Migrating synced keys out of `metadata.json` | the migration's key list, `String(value)` | same key list (`ActualMigrations.kt:282`); JSON `null` values are skipped instead of stored as `"null"` | Match (a `"null"` string means "unset" to every reader) |
| Preference and note writes go through the CRDT message log | `db.update` | `update(...)` → `applyLocalMessages`, `value`/`note` columns only | Match |
| Values are strings; booleans are `"true"`/`"false"` | `useSyncedPref` stores strings; readers compare `String(v) === 'true'` | writers use `Boolean.toString()`; readers compare `= 'true'` | Match |
| Keys Actua writes | – | `hide-reconciled-<accountId>` (`ActuaRepository.setHideReconciled`, `data/ActuaRepository.kt:1625`), `learn-categories` (`ActualEntityWriter.setLearnCategoriesEnabled`, `:233`), `actuali:credit_card:<accountId>` (`ActuaRepository.kt:918`) | Match. The last is the cross-platform Actuali key ([docs/ACCOUNTS_PARITY.md](ACCOUNTS_PARITY.md) §7); Actual ignores ids it doesn't know. No other key is written. |
| Demo budget | – | `DemoBudgetSeeder` inserts its rows directly into a local-only demo file; its credit-card row uses `limitCents` instead of Actuali's `limit` | **Divergence** ([#974](https://github.com/azimul-kabir/actua/issues/974)) |

## 2. Synced preference matrix

Every `SyncedPrefs` key at the baseline. Actua's device-local settings live in
`SharedPreferences` (`data/preferences/DisplayPreferences.kt` and siblings) and never reach the
budget file.

| Key | Actual use | Actua | Status |
| --- | --- | --- | --- |
| `budgetType` | `envelope`/`tracking` budget | read by `ActualBudgetDatabase.budgetTable` (`:1774`; legacy `report` = tracking); never written | Honoured ([docs/BUDGET_PARITY.md](BUDGET_PARITY.md) §1) |
| `upcomingScheduledTransactionLength` | default upcoming window | read (`ActualBudgetDatabase.kt:370`, `ActuaRepository.kt:303`); never written | Honoured ([docs/SCHEDULED_TRANSACTIONS_PARITY.md](SCHEDULED_TRANSACTIONS_PARITY.md)) |
| `firstDayOfWeekIdx` | week start in reports, calendar and date pickers | read (`ActualBudgetDatabase.firstDayOfWeekPreference`, `:705`; `0`–`6`, default `0`) for report weeks and the calendar widget; never written | Honoured ([docs/REPORTS_PARITY.md](REPORTS_PARITY.md), #953). Android date pickers keep the platform week start. |
| `dateFormat` | how dates are shown and typed | read only to parse typed dates in transaction search (`ActualBudgetDatabase.kt:344`, `:1257`); display uses the device-local Date format | **Divergence** for display ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `numberFormat` | separators: `comma-dot`, `dot-comma`, `space-comma`, `apostrophe-dot`, `comma-dot-in` | not read; device-local Number format | **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `hideFraction` | show whole units | read only for budget-template rounding (`ActualBudgetDatabase.kt:361`, `model/BudgetTarget.kt:598`); display uses the device-local Decimal places setting | Templates: Honoured. Display: **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `isPrivacyEnabled` | blur amounts | not read; device-local Hide balances (`DisplayPreferences.hideBalances`) | **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)); privacy for names is [#224](https://github.com/azimul-kabir/actua/issues/224) |
| `defaultCurrencyCode` | currency symbol and decimal places | read only for "Reassigned …" month notes (`ActuaRepository.kt:1790`); display uses the device-local currency and always 2 decimals | **Divergence**: zero-decimal currencies are 100× off ([#971](https://github.com/azimul-kabir/actua/issues/971)); symbol ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `currencySymbolPosition`, `currencySpaceBetweenAmountAndSymbol` | symbol before/after, narrow space | not read; symbol always before, no space | **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `hide-reconciled-<id>` | register filter | read and written | Honoured ([docs/ACCOUNTS_PARITY.md](ACCOUNTS_PARITY.md) §6) |
| `show-balances-<id>`, `show-extra-balances-<id>`, `hide-cleared-<id>`, `transaction-table-columns`, `transaction-table-columns-<id>`, `show-group-<id>`, `show-account-<id>-net-worth-chart`, `side-nav.show-balance-history-<id>` | desktop register, sidebar and account-header layout | not read or written; Actua's running-balance and summary toggles are device-local | Ignored (desktop layout; [docs/ACCOUNTS_PARITY.md](ACCOUNTS_PARITY.md) §6) |
| `sync-transfer-date` | a transfer date edit moves the other leg | read (`ActualBudgetDatabase.kt:379`); never written | Honoured ([docs/TRANSACTIONS_PARITY.md](TRANSACTIONS_PARITY.md)) |
| `learn-categories` | rules learn categories from edits | read and written | Honoured ([docs/PAYEES_PARITY.md](PAYEES_PARITY.md)) |
| `show-hidden-tags` | Manage Tags filter | not read or written | Ignored (audited in [#672](https://github.com/azimul-kabir/actua/issues/672)) |
| `parse-date-<id>-{csv,qif}`, `import-reimport-deleted-<id>`, `csv-mappings-<id>`, `csv-delimiter-<id>`, `csv-skip-start-lines-<id>`, `csv-skip-end-lines-<id>`, `csv-in-out-mode-<id>`, `csv-out-value-<id>`, `csv-has-header-<id>`, `flip-amount-<id>-{csv,qif}`, `ofx-fallback-missing-payee-<id>`, `ofx-swap-payee-memo-<id>`, `qif-swap-payee-memo-<id>`, `camt-swap-payee-memo-<id>` | per-account file-import settings | not read or written; Actua keeps named import profiles on the device (`data/importing/ImportPreferences.kt`) | Device-local (import parity is [#673](https://github.com/azimul-kabir/actua/issues/673)) |
| `custom-sync-mappings-<id>`, `sync-import-pending-<id>`, `sync-reimport-deleted-<id>`, `sync-import-notes-<id>`, `sync-import-transactions-<id>`, `sync-update-dates-<id>` | per-account bank-sync settings | not read or written | Ignored (bank sync parity is [#673](https://github.com/azimul-kabir/actua/issues/673)) |
| `flags.<FeatureFlag>` | experimental features, synced per budget | not read or written. Actua's features follow their own availability; the only opt-in, Enable Banking, is device-local (`data/preferences/ExperimentalPreferences.kt`, whose comment wrongly calls Actual's flags device-local) | Ignored |

**Device-local only, by design:** favorites, tab bar, home layout, start page, appearance,
category status colors, location and every other key in `data/preferences/` write only to
Android `SharedPreferences` (each class calls `getSharedPreferences`; none takes a database).
Actual has no synced equivalent, so nothing is lost for other clients.

## 3. Notes

Upstream ids: account `account-<id>`
([`DC/components/mobile/accounts/AccountPage.tsx#L121`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/mobile/accounts/AccountPage.tsx#L121)),
category and group = their own id (`SidebarCategoryButtons`, `SidebarGroup`), budget month
`budget-<YYYY-MM>`
([`DC/components/budget/envelope/budgetsummary/BudgetSummary.tsx#L150`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/components/budget/envelope/budgetsummary/BudgetSummary.tsx#L150),
[`LC/server/budget/actions.ts#L727-L783`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/budget/actions.ts#L727-L783)).
Dashboards have no `notes` row; text widgets keep their Markdown in the widget's `meta`. Actua:
`ActualBudgetDatabase.fetchNote`/`fetchNotes` (`:297`, `:311`), `ActuaRepository.setCategoryNote`
(`:1611`), `setAccountNote` (`:1632`), `ActualBudgetWriter` month note (`:159`),
`data/budget/BudgetMovementNote.kt`.

| Entity | Actual | Actua | Status |
| --- | --- | --- | --- |
| Account | `account-<id>`, read and edit | same id, read and edit | Match |
| Category | `<categoryId>`, read and edit; `#template` / cleanup lines are parsed from it | same id, read and edit; same parsing ([docs/BUDGET_AUTOMATION_PARITY.md](BUDGET_AUTOMATION_PARITY.md)) | Match |
| Category group | `<groupId>`, read and edit | not shown or edited | Ignored (no group notes UI) |
| Budget month | `budget-<YYYY-MM>`, read and edit; moves append `- Reassigned …` | same id; Actua only appends the move/cover line, it doesn't show or edit the note | Match for writes; showing month notes: Ignored |
| Dashboard | Markdown in `dashboard.meta`, not `notes` | rendered from `meta` | Match ([docs/REPORTS_PARITY.md](REPORTS_PARITY.md)) |
| Missing row | `notes-get` returns `null`; UI shows empty | `fetchNote` returns `""`; `fetchNotes` omits the id | Match |
| Clearing a note | writes `note: ''`; the row stays | writes `""` (blank or whitespace-only text is saved as `""`); the row stays | Match. Whitespace-only notes are normalized: other clients see an empty note either way. |
| Note text | stored as typed | stored as typed | Match |

## 4. Amount and date formatting

Upstream: `getNumberFormat`, `integerToCurrency`, `amountToInteger`
([`LC/shared/util.ts#L320-L555`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/util.ts#L320-L555)),
`useFormat`
([`DC/hooks/useFormat.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/desktop-client/src/hooks/useFormat.ts)),
`LC/shared/currencies.ts`. Actua: `ui/components/MoneyFormatter.kt` (`formatMoneyCents` `:21`,
`centsToInput` `:130`, `parseInputCents` `:136`), `ui/components/DateFormats.kt`.

| Behavior | Actual | Actua | Status |
| --- | --- | --- | --- |
| Stored amounts are integers in minor units; formatting never writes | `integerToCurrency` divides for display only | `formatMoneyCents` reads the `Long`; no formatter writes | Match |
| Typed amounts | `amountToInteger` (`Math.round(amount × 10^dp)`) | `parseInputCents`: `BigDecimal.movePointRight(2).longValueExact()`, more than 2 decimals rejected | Match for 2-decimal currencies (no float round trip) |
| Editing with decimals hidden | edit text still uses the currency's decimals | `centsToInput` always shows both decimals, so a hidden fraction is never lost on save | Match |
| Decimal places per currency | `IRR`, `JPY`, `KRW` = 0, others 2 | always 2 | **Divergence** ([#971](https://github.com/azimul-kabir/actua/issues/971)) |
| Hidden fraction rounding | `Intl.NumberFormat` rounds half away from zero; `-0` → `0` | drops the cents (`1,234.56` → `1,234`; `-0.40` → `−0`) | **Divergence** ([#972](https://github.com/azimul-kabir/actua/issues/972)), display only |
| Separators | five synced formats | device-local: system, `1,234.56`, `1.234,56`, `1 234,56`, `1234.56`, `1,23,456.78`; no `apostrophe-dot` | **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| Negative amounts | `-` before the symbol (`-$12.00`) | `−` (U+2212) before the symbol | Device-local (presentation only; same meaning) |
| Currency symbol | `currencies.ts` symbol, before/after with optional narrow space | device-local currency; symbol or locale symbol always before | **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| Stored dates | `YYYY-MM-DD` strings in the UI, `YYYYMMDD` integers in the database | `parseStoredDate` accepts both; `storageDate` writes `YYYYMMDD` | Match |
| Date display | synced `dateFormat`: `MM/dd/yyyy` (default), `dd/MM/yyyy`, `yyyy-MM-dd`, `MM.dd.yyyy`, `dd.MM.yyyy`, `dd-MM-yyyy` | device-local: system medium date (default), `DD/MM/YYYY`, `MM/DD/YYYY`, `YYYY-MM-DD` | **Divergence** ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| Month notes amount text | `integerToCurrency(amount, undefined, currency.decimalPlaces)`: en-US, two fraction digits | `BudgetMovementNote.amountText` (same) | Match (`src/test/.../data/budget/BudgetMovementNoteTest`) |

Tests: `src/test/.../ui/components/MoneyFormatterTest` (`hidingDecimalsOnlyChangesPresentation`,
`inputRequiresAtMostExactCents`), `ActualBudgetReadModelTest.hideReconciledUsesActualSyncedPerAccountPreference`.

## Divergences

| Issue | Severity | Summary |
| --- | --- | --- |
| [#971](https://github.com/azimul-kabir/actua/issues/971) | P2 | Zero-decimal currencies (`JPY`, `KRW`, `IRR`) are shown and entered 100× off |
| [#972](https://github.com/azimul-kabir/actua/issues/972) | Lower | Hidden decimals truncate instead of rounding |
| [#973](https://github.com/azimul-kabir/actua/issues/973) | Lower | Display ignores the budget's synced number, date, fraction, privacy and currency preferences |
| [#974](https://github.com/azimul-kabir/actua/issues/974) | Lower | Demo budget's credit-card limit uses the wrong field |

**Limitations:** source comparison only; no budget was opened in both clients for this audit.
