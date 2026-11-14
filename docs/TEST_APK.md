# Manual test APK builds

Actua has a manual **Build Test APK** GitHub Actions workflow for device testing without creating a GitHub release.

## Build an APK

1. Open the repository's **Actions** tab.
2. Select **Build Test APK**.
3. Choose **Run workflow**.
4. In **Branch, tag, or commit to build**, enter the branch you want to test, for example `fix/widget-polish-34`. Leave it blank to build the workflow's selected branch.
5. Start the workflow and wait for **Build signed test APK** to finish.
6. Open the completed workflow run and download the `Actua-Test-...` artifact from the **Artifacts** section.
7. Extract the ZIP and install the APK on the Android device. The ZIP also holds the build's `mapping.txt` (see [Release build](#release-build)).

The artifact is retained for 7 days. The workflow does **not** create a tag, GitHub release, changelog entry, Obtainium update, or Discord notification.

## Release build

The workflow builds the **release** build type, the same as the published app: minified by R8, not debuggable, and with the baseline profile. Scrolling and animation in Actua Test therefore match what users get. Do not judge smoothness on a debug build (for example one installed from Android Studio): Compose runs several times slower in debug builds.

R8 renames classes, so a crash stack trace from Actua Test shows obfuscated names. Decode it with the `mapping.txt` from the same artifact, for example `retrace mapping.txt stacktrace.txt` (the `retrace` tool ships with the Android SDK command-line tools).

## Separate test app

Manual test builds are intentionally isolated from the normal Actua installation:

- Production app ID: `com.azimulkabir.actua`
- Test app ID: `com.azimulkabir.actua.test`
- Production launcher name: **Actua**
- Test launcher name: **Actua Test**

Android therefore installs **Actua Test** beside the regular **Actua** app instead of upgrading or replacing it. The two apps also have separate Android app storage, preferences, local databases, backups, widget configuration, and login/budget state. Installing or uninstalling Actua Test does not remove the production Actua app's local data.

Launcher shortcut target packages are rewritten during the test build so Expense, Income, Transfer, and Search shortcuts continue to open the isolated Actua Test package.

## Signing

The test APK is signed with Actua's persistent signing key, but because it has a different application ID it cannot replace the production Actua package.

The requested branch is built before signing secrets are made available, and Gradle signs it with a throwaway debug key. Signing with the persistent key is performed in a separate fixed workflow step with Android's `apksigner` rather than passing release signing secrets into the checked-out Gradle build. The signing key itself is never uploaded as an artifact.

## Safety

A test APK can contain unfinished code from the selected branch. Treat **Actua Test** as a separate installation. If you connect it to an important Actual server budget, it can still write synchronized changes to that budget, so create an independent backup before testing development builds.
