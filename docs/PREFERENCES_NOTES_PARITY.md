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
| Demo budget | – | `DemoBudgetSeeder` inserts its rows directly into a local-only demo file; its credit-card row uses Actuali's `limit` field | Match ([#974](https://github.com/azimul-kabir/actua/issues/974)) |

## 2. Synced preference matrix

Every `SyncedPrefs` key at the baseline. Actua's device-local settings live in
`SharedPreferences` (`data/preferences/DisplayPreferences.kt` and siblings) and never reach the
budget file.

| Key | Actual use | Actua | Status |
| --- | --- | --- | --- |
| `budgetType` | `envelope`/`tracking` budget | read by `ActualBudgetDatabase.budgetTable` (`:1774`; legacy `report` = tracking); never written | Honoured ([docs/BUDGET_PARITY.md](BUDGET_PARITY.md) §1) |
| `upcomingScheduledTransactionLength` | default upcoming window | read (`ActualBudgetDatabase.kt:370`, `ActuaRepository.kt:303`); never written | Honoured ([docs/SCHEDULED_TRANSACTIONS_PARITY.md](SCHEDULED_TRANSACTIONS_PARITY.md)) |
| `firstDayOfWeekIdx` | week start in reports, calendar and date pickers | read (`ActualBudgetDatabase.firstDayOfWeekPreference`, `:705`; `0`–`6`, default `0`) for report weeks and the calendar widget; never written | Honoured ([docs/REPORTS_PARITY.md](REPORTS_PARITY.md), #953). Android date pickers keep the platform week start. |
| `dateFormat` | how dates are shown and typed | read to parse typed dates in transaction search and, while the device Date format is "Same as budget" (the default), to show dates (`data/preferences/BudgetDisplayFormats.kt`) | Honoured; a device choice overrides it ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `numberFormat` | separators: `comma-dot`, `dot-comma`, `space-comma`, `apostrophe-dot`, `comma-dot-in` | read while the device Number format is "Same as budget" (the default) | Honoured; a device choice overrides it ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `hideFraction` | show whole units | read for budget-template rounding and, while the device Decimal places setting is "Same as budget" (the default), for display | Honoured; a device Show/Hide overrides display ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| `isPrivacyEnabled` | blur amounts | read while the device Balances setting is "Same as budget" (the default); Actua masks amounts instead of blurring them | Honoured; a device Show/Hide overrides it ([#973](https://github.com/azimul-kabir/actua/issues/973)); privacy for names is [#224](https://github.com/azimul-kabir/actua/issues/224) |
| `defaultCurrencyCode` | currency symbol and decimal places | decimal places: amount display and entry (`ActuaRepository.budgetDecimalPlaces`); symbol: while the device Currency is "Same as budget" (the default), Actual's short symbol | Honoured ([#971](https://github.com/azimul-kabir/actua/issues/971), [#973](https://github.com/azimul-kabir/actua/issues/973)); a device currency overrides the symbol |
| `currencySymbolPosition`, `currencySpaceBetweenAmountAndSymbol` | symbol before/after, narrow space | read while the device Currency is "Same as budget"; a device currency is always shown before the amount | Honoured ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
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
| Typed amounts | `amountToInteger` (`Math.round(amount × 10^dp)`) | `parseInputCents`: `BigDecimal.movePointRight(dp).longValueExact()`, more decimals than the currency has are rejected | Match (no float round trip) |
| Editing with decimals hidden | edit text still uses the currency's decimals | `centsToInput` always shows both decimals, so a hidden fraction is never lost on save | Match |
| Decimal places per currency | `useFormat`: `IRR`, `JPY`, `KRW` = 0, others 2, for display, transaction and rule amount entry | the same for display, amount entry (`CalculatorAmountState`, `parseInputCents`), rule amounts and credit-card limits (`CurrencyDisplay.decimalPlaces`) | Match ([#971](https://github.com/azimul-kabir/actua/issues/971)) |
| Decimal places in search and file import | always 2: `transactionsSearch` and `ImportTransactionsModal` use `amountToInteger(amount)` | always 2 | Match (upstream doesn't apply the currency's places here either) |
| Hidden fraction rounding | `Intl.NumberFormat` rounds half away from zero; `-0` → `0` | rounds half away from zero (`1,234.56` → `1,235`); an amount that rounds to zero has no minus sign | Match ([#972](https://github.com/azimul-kabir/actua/issues/972)), display only |
| Separators | five synced formats | the budget's format by default, including `apostrophe-dot` (`1’234.56`); device overrides add system and no-grouping `1234.56`. `space-comma` groups with a plain space instead of U+202F | Match ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| Negative amounts | `-` before the symbol (`-$12.00`) | `−` (U+2212) before the symbol | Device-local (presentation only; same meaning) |
| Currency symbol | `currencies.ts` symbol, before/after with optional narrow space | budget currency by default: short symbol, position and narrow space from the synced prefs | Match ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| Stored dates | `YYYY-MM-DD` strings in the UI, `YYYYMMDD` integers in the database | `parseStoredDate` accepts both; `storageDate` writes `YYYYMMDD` | Match |
| Date display | synced `dateFormat`: `MM/dd/yyyy` (default), `dd/MM/yyyy`, `yyyy-MM-dd`, `MM.dd.yyyy`, `dd.MM.yyyy`, `dd-MM-yyyy` | the budget's format by default, with all six patterns; device overrides add the system medium date | Match ([#973](https://github.com/azimul-kabir/actua/issues/973)) |
| Month notes amount text | `integerToCurrency(amount, undefined, currency.decimalPlaces)`: en-US, two fraction digits | `BudgetMovementNote.amountText` (same) | Match (`src/test/.../data/budget/BudgetMovementNoteTest`) |

Tests: `src/test/.../ui/components/MoneyFormatterTest` (`hidingDecimalsOnlyChangesPresentation`,
`inputRequiresAtMostExactCents`), `ActualBudgetReadModelTest.hideReconciledUsesActualSyncedPerAccountPreference`.

## Divergences

| Issue | Severity | Summary |
| --- | --- | --- |
| [#971](https://github.com/azimul-kabir/actua/issues/971) | P2 | Zero-decimal currencies (`JPY`, `KRW`, `IRR`) are shown and entered 100× off (fixed) |
| [#972](https://github.com/azimul-kabir/actua/issues/972) | Lower | Hidden decimals truncate instead of rounding (fixed) |
| [#973](https://github.com/azimul-kabir/actua/issues/973) | Lower | Display ignores the budget's synced number, date, fraction, privacy and currency preferences (fixed) |
| [#974](https://github.com/azimul-kabir/actua/issues/974) | Lower | Demo seeder wrote the credit-card limit under the wrong field; `DemoBudgetManager` patched it afterwards (fixed) |

**Limitations:** source comparison only; no budget was opened in both clients for this audit.
