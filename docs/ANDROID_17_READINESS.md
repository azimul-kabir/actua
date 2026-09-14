# Android 17 readiness

Actua compiles and targets Android 17 (API 37) while retaining Android 9
(API 28) as its minimum supported version. This audit records the platform
behaviours reviewed for issue #109 and the coverage that guards them.

## Platform behaviour audit

| Area | Status | Actua handling |
| --- | --- | --- |
| Local network access | Ready | `ACCESS_LOCAL_NETWORK` is declared and requested at runtime on API 37 before password or OpenID connections. This preserves access to self-hosted Actual servers on a LAN and handles denial without starting the connection. |
| Edge-to-edge and insets | Ready | Both activities call `enableEdgeToEdge`; Compose scaffolds and full-screen destinations consume their supplied insets. |
| Background work | Ready | Sync, local backup, and credit-card reminder work use WorkManager. Periodic sync uses the platform minimum 15-minute interval, network constraints, unique work, and retry/backoff behaviour. |
| Notifications | Ready | Android 13+ notification permission is requested before Actua posts reminder notifications. Transaction import uses the user-enabled notification-listener service rather than notification permission. |
| Location | Ready | Nearby-payee actions request foreground fine/coarse location at the point of use, accept approximate fixes, and expose location privacy controls. No background-location permission is requested. |
| Files and backups | Ready | Import/export uses the Storage Access Framework (`OpenDocument`, `CreateDocument`, and `OpenDocumentTree`) with persisted URI grants for scheduled backups. Actua does not request broad storage access. |
| Widgets | Ready | Widgets use text, progress bars, and resource drawables only. They do not place bitmap or icon payloads in `RemoteViews`, so they remain below Android 17's widget parcel memory limit. |
| Shortcuts and deep links | Ready | Launcher shortcuts target the exported main activity. The OpenID callback is restricted to the `actua://oidc-complete` scheme/host pair, and app launch intents are parsed without trusting arbitrary actions. |
| Process recreation | Covered | Credentials and preferences are persisted outside Compose state; the selected budget, sync state, import preferences, display preferences, and location settings have Android regression tests. |
| App upgrades | Covered | Android backup rules exclude credentials and downloaded budget data while preserving safe preferences. Database, backup/restore, encryption, and preference regression tests run across supported emulator APIs. |

## Compatibility coverage

The Android compatibility workflow runs the complete connected instrumented test
suite on API 28, 35, and 36. The matrix deliberately retains the oldest supported
release and the two newest stable emulator images currently installable on
GitHub-hosted runners, so platform fixes do not silently regress existing
installations.

API 37 compilation and target-SDK checks run in the regular Android workflow and
the compatibility workflow, both of which install the canary API 37 platform
explicitly. API 37 is not yet in the runtime compatibility matrix because Google's stable SDK channel does not currently
publish an installable `platforms;android-37` package and emulator image to the
runner. Add the device job once those packages are available from the stable
channel; until then, Android 17 runtime paths require the Pixel 8/manual checks
listed in `RELEASE_SMOKE_TEST.md`.

The regular Android workflow additionally builds debug and release variants,
runs JVM tests, and runs lint. Signed release installation and the manual paths
listed in `RELEASE_SMOKE_TEST.md` remain release-candidate checks because CI does
not have production signing credentials or a real self-hosted Actual server.

## Sources

- [Android 17 behaviour changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Android 17 changes affecting all apps](https://developer.android.com/about/versions/17/behavior-changes-all)
