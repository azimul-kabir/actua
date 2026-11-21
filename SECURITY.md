# Security policy

Actua handles credentials for your Actual server, encryption keys for backups, and budget data, so security reports
are taken seriously.

## Reporting a vulnerability

Please report security problems **privately**, not in a public issue, pull request or Discord message.

- On GitHub, open the **Security** tab and choose **Report a vulnerability**, if that button is available.
- Otherwise email [actua@azimulkabir.com](mailto:actua@azimulkabir.com) with "Security" in the subject.

Please include:

- the Actua version (Settings → About) and where you installed it from (Google Play or GitHub),
- your Android version and device,
- what you did, what you expected and what happened,
- the impact as you understand it.

Do not include real budgets, server addresses, passwords, tokens, recovery codes or other credentials. Use a synthetic
budget and a throwaway server where you can, and redact anything you must share. If a report needs a secret to be
reproduced, say so and we will agree a safe way to exchange it.

This is an independent, volunteer-maintained project. We will read every report and reply as soon as we can, but we
cannot promise a fixed response time. Please give us a reasonable opportunity to fix a problem before you disclose it
publicly.

## Supported versions

Only the **latest release** receives security fixes. Please update before reporting, and check whether the problem
still happens on the newest version.

## Scope

In scope:

- the Actua Android app, in every build (Google Play and the GitHub APK),
- how Actua stores and protects credentials, encryption keys, backups and local data,
- the sandbox-only website purchase service in the [actua-website](https://github.com/azimul-kabir/actua-website) repository,
  including checkout, recovery and entitlement checks (website sales are not planned for now),
- this repository's build and release workflows, where they could expose secrets or ship a tampered build.

Out of scope:

- vulnerabilities in the Actual server or its protocol: please report those to the
  [Actual Budget project](https://github.com/actualbudget/actual/security),
- vulnerabilities in Google Play, Android or other third-party services,
- attacks that need physical access to an unlocked device, a rooted device or a device the attacker already controls,
- missing hardening that has no demonstrated impact, and reports from automated scanners without a working scenario.

## Testing your own data only

Please test only against your own installs, servers and accounts. Do not access, change or delete other people's data,
and do not run load or denial-of-service tests against the website or purchase service.

## Handling

When a report is confirmed, the fix is developed privately where practical, released, and described in the
[changelog](CHANGELOG.md) in plain terms without details that would help an attacker. We will credit you if you want
to be credited.
