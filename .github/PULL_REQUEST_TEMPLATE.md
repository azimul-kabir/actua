## Why

<!-- Describe the problem. Link the issue with a closing keyword on its own line, e.g. "Closes #123" (use "Refs #123" for one slice of a multi-slice issue). Link any Actual/Actuali source or commit used for parity. -->

## What

<!-- Summarize the behavior change and any database/sync compatibility implications. Stay within the issue's scope. -->

## Risk

<!-- The risk workflow labels this PR automatically. For risk:high changes, describe data integrity, sync, migration, security, financial-calculation, or workflow risks and rollback/recovery considerations. -->

## How it was tested

<!-- List exact commands and results, e.g. ./gradlew assembleDebug testInstrumentedUnitTest lintDebug, plus manual checks with device/API and Actual server version. For platform/SQLite changes, include API 28. For performance-sensitive changes, include the Macrobenchmark before/after comparison from docs/PERFORMANCE_BASELINE.md, or say it was not measured on a physical device. State anything not run. -->

## Screenshots

<!-- For UI changes, include redacted before/after images (check small screens and large font scale). Delete if not applicable. -->

- [ ] This PR is linked to an issue that defines the scope and acceptance criteria, and covers only that scope.
- [ ] `CHANGELOG.md` Unreleased has one short line with a PR link for user-facing changes (or the change is not user-facing).
- [ ] Behavior changes have appropriate regression coverage; upstream fixture assertions remain intact and fixtures were not regenerated to pass.
- [ ] Sync/database/financial changes preserve Actual compatibility, integer-cent amounts, offline edits, transfers/splits, and atomic writes where applicable.
- [ ] Synchronized mutations go through the existing writers and `applyLocalMessages`, not ad-hoc SQL.
- [ ] Navigation/UI changes were checked for Android back behavior, state restoration, keyboard/insets, and accessibility where applicable.
- [ ] Destructive operations, migrations, restore/archive flows, and rollback/recovery were considered where applicable.
- [ ] No passwords, tokens, encryption keys, real budgets, or other sensitive financial data are included.
- [ ] `BACKEND_PARITY.md`/documentation were updated if the implementation boundary changed.
