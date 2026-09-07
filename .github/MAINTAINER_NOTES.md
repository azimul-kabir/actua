# Community setup

Issue forms, the PR template and AGENTS.md are adapted from
[MattFaz/actuali](https://github.com/MattFaz/actuali) for Actua.
Merge this branch into `main` to make the issue chooser and privileged review
workflow available. Ensure the `bug` and `enhancement` labels exist.

## Android CI

`Android CI / build-test-lint` runs on PRs to main and pushes to main. It installs
JDK 25 and SDK 37 and runs `assembleDebug`, `testInstrumentedUnitTest` and
`lintDebug`. It uploads diagnostic reports, not signed releases. There are no
repository secrets or write permissions in this workflow. Instrumentation tests
still require a connected device/emulator and are not part of this CI job;
run `./gradlew connectedInstrumentedAndroidTest` locally when relevant.
After a successful GitHub run, select the build-test-lint check in the main
branch ruleset if it should be required for merging.

Release metadata changes on `rebrand/actua` run the Android Release workflow. It
builds and validates the debug-signed testing APK, creates the version tag and
GitHub prerelease, copies that version's `CHANGELOG.md` section into the release
description, and attaches the versioned APK. Update `versionCode`,
`versionName`, and `CHANGELOG.md` together for each release.

## Recommended: Codex GitHub review with ChatGPT Plus

Connect this repository in Codex cloud and enable Code review in Codex settings.
Request a review with `@codex review` in a PR comment, or enable Automatic reviews.
Codex reads the repository's AGENTS.md review rules. This uses your Codex plan
allowance and does not require an OpenAI API key or Claude subscription/token.
See [official setup instructions](https://learn.chatgpt.com/docs/third-party/github)
and [current plan limits](https://learn.chatgpt.com/docs/pricing).
Account connection and review settings must be configured separately; committing
these files does not enable the hosted integration. Start with manual reviews
to control usage. Leave the optional API workflow below disabled to avoid duplicate
reviews and separate API billing.

## Optional API alternative — disabled by default

No Claude subscription or AI credentials are needed for the community templates
or Android CI. Leave `AI_REVIEW_ENABLED` unset to skip AI review entirely.
The supplied optional implementation uses Anthropic's Messages API, billed
separately from a Claude subscription:

1. Create an Anthropic API key with API billing enabled. Add it as the repository
   Actions secret `ANTHROPIC_API_KEY` (Settings → Secrets and variables → Actions).
2. Add the Actions variable `AI_REVIEW_MODEL` with a model ID available to that
   API account. No model is hardcoded, so choose one before enabling reviews.
3. Set the Actions variable `AI_REVIEW_ENABLED` to `true`.

`CLAUDE_CODE_OAUTH_TOKEN` is **not used**. There is no Claude Code action,
subscription dependency, GitHub App installation, plugin or OAuth setup.
If choosing a different provider, adapt the request and secret before enabling.
API review sends PR diffs and base AGENTS.md to Anthropic and incurs API costs.
It runs on non-draft PR opening, new commits, reopening and ready-for-review,
including forks. Disable the variable to stop automatic spending. AI review is
advisory and should not be a required merge check.

The `pull_request_target` job never checks out a branch or runs repository code.
Its fixed workflow script downloads AGENTS.md from the base SHA and the PR diff
as text, calls the model without tools, and posts a comment on that PR using a
scoped GITHUB_TOKEN. Model output is never executed. Oversized diffs (>120 KB),
missing configuration and incomplete responses fail visibly instead of posting
a partial review; feedback for an outdated head is skipped. Prompts can still
mislead the model, so maintainers must verify findings. No build artifacts or
PR-provided configuration are loaded by this privileged job.

See the [Anthropic Messages API documentation](https://platform.claude.com/docs/en/api/messages)
for authentication and request details.
