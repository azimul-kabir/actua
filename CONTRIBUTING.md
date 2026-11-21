# Contributing to Actua

Thanks for helping. Actua is an independent Android client for Actual Budget, licensed under the
[GNU GPL v3.0](LICENSE). Contributions of any size are welcome: bug reports, ideas, translations, documentation and
code.

[AGENTS.md](AGENTS.md) is the detailed source of truth for the repository map, data and sync rules, code conventions
and review priorities. This page is the short version. Please also read the [README](README.md), and
[BACKEND_PARITY.md](BACKEND_PARITY.md) before changing behavior that Actual also implements.

## Security problems

Do not report vulnerabilities in a public issue. See [SECURITY.md](SECURITY.md).

## Asking questions and reporting bugs

- Questions and discussion: the [Discord server](https://discord.gg/FhedEyP97f).
- Bugs and feature requests: [open an issue](https://github.com/azimul-kabir/actua/issues/new/choose) and fill in the
  template. Include the Actua version, where you installed it from, your Android version and clear steps to
  reproduce.
- Never paste real budgets, server addresses, passwords, tokens or unredacted financial data. Use a demo or
  synthetic budget, and blur screenshots.

## Making a change

Every code or build change starts with an issue, so the problem, scope and acceptance criteria are agreed before work
begins.

1. **Open or find an issue** that describes the change. Use the existing labels; please don't create new ones.
2. **Create a focused branch** from `main` (for example `fix/1234-short-name` or `feat/1234-short-name`). Don't work
   on `main`.
3. **Keep it to one concern** per issue, branch and pull request, with no unrelated refactors or cleanup.
4. **Open a pull request** linked to the issue, using the pull request template. Describe what changed, the risk,
   and exactly which checks you ran and which you could not.
5. A maintainer reviews and merges. Please don't merge your own pull request.

Documentation-only changes (Markdown, with no code, build or workflow changes) still go through a pull request when
they come from contributors.

## Building and testing

Use the committed Gradle wrapper (`./gradlew`). You need the Android SDK 37; `minSdk` is 28. The Gradle daemon
toolchain and dependency versions are set in `gradle/gradle-daemon-jvm.properties` and `gradle/libs.versions.toml`.

There are two flavors, `direct` (the GitHub downloads) and `playstore` (Google Play). Test and lint tasks name the
flavor explicitly:

```sh
./gradlew assembleDebug testDirectInstrumentedUnitTest testPlaystoreInstrumentedUnitTest lintDirectDebug lintPlaystoreDebug
```

With an emulator or device connected:

```sh
./gradlew connectedDirectInstrumentedAndroidTest
```

Narrow a run while you work, for example `./gradlew testDirectInstrumentedUnitTest --tests '*SyncCoreFixtureTest'`.
GitHub Actions runs the full matrix on every pull request, including API 28 and API 35 emulators, so you do not
need to run everything locally. Please say in the pull request what you did run.

- Add or update tests for behavior you change: pure logic in `src/test`, Android, SQLite and Keystore tests in
  `src/androidTest`.
- Never weaken an assertion, or regenerate the upstream sync fixtures, just to make a failure go away.
- Check UI changes on a device or emulator, including back navigation and a large font size.

## Rules that matter most

- **Money is integer cents** (`Long`). Never use floating point for amounts.
- **Synced writes go through the existing writers** and `applyLocalMessages`, not ad hoc SQL, so changes stay
  compatible with Actual.
- **Use synthetic fixtures.** Do not commit real budgets, credentials, keys, tokens or keystores.
- **Follow the existing style** in the code you touch: names, structure, libraries and the localized string
  resources (English, French, German, Italian, Japanese, Japanese, Simplified Chinese, Brazilian Portuguese and Spanish; see [docs/LOCALIZATION.md](docs/LOCALIZATION.md)).
- **Ask before adding a dependency.** Say what it is for and why existing code is not enough. It must be
  GPL-3.0-compatible and must not add tracking or unapproved network calls.
- **Don't change `versionCode` or `versionName`.** A version bump on `main` triggers a signed release, a Google Play
  upload and a GitHub release.

## Documentation and the changelog

Update the docs in the same pull request as the change: the [README](README.md) feature list, `BACKEND_PARITY.md`
and the matching `docs/*_PARITY.md` when the implementation boundary moves, and [PRIVACY.md](PRIVACY.md) when data
handling changes.

For a user-facing change, add one short line under `## Unreleased` in [CHANGELOG.md](CHANGELOG.md), under the matching
**Added**, **Changed** or **Fixed** heading, ending with a linked pull request reference:

```
- Fixed the transaction list scrolling under the keyboard on split entry ([#512](https://github.com/azimul-kabir/actua/pull/512))
```

Say what changed, not how or why; the pull request holds the detail. Skip the changelog for internal-only changes.

## Commit messages

Write a short imperative subject (about 72 characters or fewer, no trailing period), with an optional body that
explains why. Keep each commit to one logical change.

## Licence

By contributing you agree that your contribution is licensed under the [GNU GPL v3.0](LICENSE), the same as the rest
of Actua.
