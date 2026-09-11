# Community setup

Issue forms, the PR template and AGENTS.md are adapted from
[MattFaz/actuali](https://github.com/MattFaz/actuali) for Actua. Ensure the
`bug`, `enhancement`, and `dependencies` labels exist. The PR risk workflow
creates and maintains its own `risk:low`, `risk:medium`, and `risk:high` labels.

## Android CI

`Android CI / build-test-lint` runs on PRs to main and pushes to main. It installs
JDK 25 and SDK 37 and runs `assembleDebug`, `testInstrumentedUnitTest` and
`lintDebug`. It uploads diagnostic reports, not signed releases. There are no
repository secrets or write permissions in this workflow.

`Android Compatibility` runs the connected instrumentation suite on representative
emulators for API 28 / Android 9, API 35 / Android 15, and API 36 / Android 16.
This makes the advertised Android 9+ floor repeatable in CI while also exercising
a middle platform and the newest emulator image available on GitHub-hosted runners.
SDK 37 compilation remains covered by Android CI; API 37 emulator coverage can be
added when Google publishes an installable system image. The matrix is parallel,
fail-fast is disabled, and each API uploads its instrumentation reports for
troubleshooting. Run the same suite locally with an emulator/device using
`./gradlew connectedInstrumentedAndroidTest`.

After successful GitHub runs, select the build-test-lint and compatibility checks
in the main branch ruleset if they should be required for merging.

Release metadata changes on `main` run the Android Release workflow. It
builds and validates the persistently signed release APK, creates the version tag and
GitHub prerelease, copies that version's `CHANGELOG.md` section into the release
description, and attaches the versioned APK. Update `versionCode`,
`versionName`, and `CHANGELOG.md` together for each release.

The release workflow requires one long-lived signing key. Generate and back up
the keystore outside the repository, then configure these GitHub Actions secrets:

- `ACTUA_RELEASE_KEYSTORE_BASE64`: the base64-encoded keystore file
- `ACTUA_RELEASE_STORE_PASSWORD`: the keystore password
- `ACTUA_RELEASE_KEY_ALIAS`: the key alias
- `ACTUA_RELEASE_KEY_PASSWORD`: the key password

Never commit the keystore or its passwords. Losing them means future APKs cannot
upgrade installations signed with that key. Rotating the key also requires users
to uninstall the existing app unless distribution has moved to Google Play with
an approved signing-key upgrade.

On macOS, create the key once and keep the file in a private backed-up location:

```sh
mkdir -p "$HOME/Documents/Actua-Signing"
keytool -genkeypair -v \
  -keystore "$HOME/Documents/Actua-Signing/actua-release.jks" \
  -alias actua -keyalg RSA -keysize 4096 -validity 10000
base64 < "$HOME/Documents/Actua-Signing/actua-release.jks" | tr -d '\n' | pbcopy
```

Paste the clipboard value into `ACTUA_RELEASE_KEYSTORE_BASE64`. Save the two
passwords and alias in a password manager, add the other three secrets, and keep
an encrypted backup of the keystore somewhere other than the Mac.

## Zero-cost pull-request review

The default review path has no paid service dependency:

- Android CI builds the debug APK, runs JVM regression tests, and runs lint.
- Android Compatibility runs connected instrumentation across API 28, 35, and 36.
- PR Risk Classification applies a risk label from touched paths without
  checking out or executing PR code.
- Dependabot opens weekly Gradle and GitHub Actions update PRs.
- CODEOWNERS requests maintainer review for sensitive project areas.

Use branch protection or a main-branch ruleset to require the Android CI check
and at least one approving review before merge. Treat `risk:high` as a prompt
for deeper manual review of sync, database, financial, security, migration, or
workflow behavior. Dependabot proposes updates but does not merge them.

GitHub dependency review is not configured because its dependency-review action
requires the repository dependency graph, which may not be available for this
project. Dependabot and the Gradle build remain the free dependency safeguards.

## Optional: Codex GitHub review with ChatGPT Plus

Connect this repository in Codex cloud and enable Code review in Codex settings.
Request a review with `@codex review` in a PR comment, or enable Automatic reviews.
Codex reads the repository's AGENTS.md review rules. This uses your Codex plan
allowance and does not require an OpenAI API key or Claude subscription/token.
See [official setup instructions](https://learn.chatgpt.com/docs/third-party/github)
and [current plan limits](https://learn.chatgpt.com/docs/pricing).
Account connection and review settings must be configured separately; committing
repository files does not enable the hosted integration. Start with manual
reviews to control plan usage. Actua has no repository-hosted paid AI review
workflow and requires no OpenAI or Anthropic API key.
