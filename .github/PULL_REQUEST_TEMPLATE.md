## Why

<!-- Describe the problem; link the issue and any Actual/Actuali source or commit used for parity. -->

## What

<!-- Summarize the behavior change and any database/sync compatibility implications. -->

## Risk

<!-- The risk workflow labels this PR automatically. For risk:high changes, describe data integrity, sync, migration, security, financial-calculation, or workflow risks and rollback/recovery considerations. -->

## How it was tested

<!-- List exact commands/results and manual checks, including device/API and Actual server version where relevant. State anything not tested. -->

## Screenshots

<!-- For UI changes, include redacted before/after images. Delete if not applicable. -->

- [ ] Behavior changes have appropriate regression coverage; upstream fixture assertions remain intact.
- [ ] Sync/database/financial changes preserve Actual compatibility, integer-cent amounts, offline edits, transfers/splits, and atomic writes where applicable.
- [ ] Navigation/UI changes were checked for Android back behavior, state restoration, keyboard/insets, and accessibility where applicable.
- [ ] Destructive operations, migrations, restore/archive flows, and rollback/recovery were considered where applicable.
- [ ] No passwords, tokens, encryption keys, real budgets, or other sensitive financial data are included.
- [ ] Documentation/parity notes were updated if the implementation boundary changed.
