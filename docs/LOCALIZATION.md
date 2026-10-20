# Localization in Actua

Actua ships in English and Brazilian Portuguese (pt-BR). This document defines the conventions every string change must follow so both catalogs stay complete, in parity, and in the same voice as Actual Budget (refs #1225).

## Purpose

Actua localizes structurally like the Actual Budget Android effort (#424): the same eight per-feature `strings_*.xml` files and the same key-prefix discipline, so catalogs stay diffable against upstream. Terminology is shared with Actual's pt-BR translations so both apps use identical words for the same concepts.

## How Actua localizes

- `values/` is the default (English) catalog. Each locale gets a sibling resource directory: `values-pt-rBR/` for pt-BR.
- Strings live in per-feature files mirroring #424: `strings_navigation.xml`, `strings_budget_home.xml`, `strings_widgets.xml`, `strings_transactions.xml`, `strings_settings.xml`, `strings_financial_settings.xml`, `strings_connection_import.xml`, `strings_demo.xml`. A plain `strings.xml` holds cross-feature strings (app name, widgets, shortcuts, settings labels). The extraction phases land these files incrementally — `values*/` currently holds `strings.xml` plus the `strings_navigation.xml` and `strings_budget_home.xml` skeletons — but the eight-file layout is the fixed target: once a feature's file exists it must exist in every locale, even while empty.
- Per-file self-containment intentionally duplicates a few cross-feature labels (e.g. `navigation_cancel` alongside `common_cancel`), mirroring #424 — duplication is accepted to keep files self-contained.
- Key naming: `^[a-z][a-z0-9_]*$` (lower snake case) with a per-file prefix: `strings_navigation.xml` → `navigation_`; `strings_budget_home.xml` → `budget_`, `home_`, `common_`; `strings_widgets.xml` → `widget_`, `disconnect_`, `credit_card_due_`, `notification_`. The remaining feature files take matching feature prefixes as they fill in. Cross-feature keys live in `strings.xml` with their own prefixes — `widget_`, `shortcut_`, `tasker_`, `settings_` today (the `widget_*` keys move to `strings_widgets.xml` when extraction lands). One key, one meaning; never reuse a key for two screens.
- `app/src/test/java/com/azimulkabir/actua/ui/StringResourceParityTest.kt` enforces all of this on the JVM (key parity, placeholder parity, escaping, plural completeness, naming, markers). Run `./gradlew testFdroidInstrumentedUnitTest` before committing string changes.

## Rules

- Strings use positional placeholders: `%1$s`, `%2$d` — never bare `%s`/`%d`, because pt-BR reorders arguments.
- Plurals receive the count automatically: use bare `%d` (or `%1$d`); any additional arguments stay positional.
- A literal percent sign is written `%%`.
- Apostrophes: curly `’` needs no escaping; a straight `'` must be written `\'` or the build fails.
- Every plural defines both `one` and `other` quantities, in every locale.
- Translate complete sentences as units. Never concatenate translated fragments; if the same idea needs different wording in two places, add a second key.
- Never-translated strings are marked `translatable="false"` and exist only in the default catalog (currently `app_name`). Every translatable key must exist in every locale — no gaps.
- Values persisted in preferences or sent to the sync server are locale-neutral tokens (BCP-47 tags such as `pt-BR`, currency codes, setting enums), never translated display text. Localize at render time only.
- Preserve display markers: the widget amount prefixes `− `, `+ `, `⇄ ` (minus, plus, transfer) must survive translation exactly as leading characters.

## Terminology

Actual Budget's pt-BR glossary, maintained on Weblate via [actualbudget/translations](https://github.com/actualbudget/translations), is authoritative. When a concept exists there, use its pt-BR term verbatim (e.g. *orçamento* for budget, *gasto*/*receita* for expense/income); coin a new term only when the glossary has no match, and follow its spelling and register.

## Language selection

- Default is the system locale: the resource system resolves `values-pt-rBR/` on its own.
- Users can override per app under **Settings → Display → Language**: System default, English, Português (Brasil). The picker (`SettingsScreen.kt`, wired through `AppNavigation.kt`) stores the choice with the AppCompat per-app locales API, `AppCompatDelegate.setApplicationLocales` — framework-backed on API 33+, backported to this app's minSdk 28.
- The supported set is declared in `app/src/main/res/xml/locales_config.xml` (`en`, `pt-BR`), referenced by `AndroidManifest.xml` via `android:localeConfig` so the OS settings page matches the in-app picker.
- Activities receive the locale automatically through AppCompat. Receivers, workers, and any non-activity context must wrap themselves with `Context.withAppLanguage()` (`app/src/main/java/com/azimulkabir/actua/data/preferences/AppLanguage.kt`), as the widgets do.
- `app/build.gradle.kts` sets `bundle { language { enableSplit = false } }`, shipping every locale in a single artifact so switching in-app never triggers a Play Store download.
- `androidResources { localeFilters += listOf("en", "pt-rBR") }` strips dependency-library locales Actua does not translate. Add a new language to this list (and to `locales_config.xml`) when shipping it.
- Manifest shortcut labels are resolved by the launcher and follow the system — not the in-app — language below API 33.

## Adding a language

1. Create `values-<locale>/` mirroring **all** `values*/strings*.xml` files, with every translatable key translated.
2. Add the locale tag to `app/src/main/res/xml/locales_config.xml`.
3. Add a picker entry: a `settings_language_*` label in every catalog plus a `SettingsChoiceOption` in `SettingsScreen.kt`.
4. Add the new directory to `localeDirs` in `StringResourceParityTest` and keep the test green — it then checks key, placeholder, plural, and escaping parity for the new locale like any other.

Context: Actual ships a locale once its translation coverage reaches ~50% of strings ([actualbudget/translations](https://github.com/actualbudget/translations)). Actua holds itself to a stronger bar per slice: each localization PR translates 100% of the keys it touches, and the parity test keeps it that way.
