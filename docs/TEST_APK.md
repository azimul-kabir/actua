# Manual test APK builds

Actua has a manual **Build Test APK** GitHub Actions workflow for device testing without creating a GitHub release.

## Build an APK

1. Open the repository's **Actions** tab.
2. Select **Build Test APK**.
3. Choose **Run workflow**.
4. In **Branch, tag, or commit to build**, enter the branch you want to test, for example `fix/widget-polish-34`. Leave it blank to build the workflow's selected branch.
5. Start the workflow and wait for **Build signed test APK** to finish.
6. Open the completed workflow run and download the `Actua-test-...` artifact from the **Artifacts** section.
7. Extract the ZIP and install the APK on the Android device.

The artifact is retained for 7 days. The workflow does **not** create a tag, GitHub release, changelog entry, Obtainium update, or Discord notification.

## Signing and upgrades

The test APK is re-signed with Actua's persistent release signing key, so it uses the same signing identity as normal signed beta releases and can be used for in-place device testing when Android's normal version rules permit it. The signing key itself is never uploaded as an artifact.

The requested branch is built before signing secrets are made available. Signing is performed in a separate fixed workflow step with Android's `apksigner` rather than passing release signing secrets into the checked-out Gradle build.

## Safety

A test APK can contain unfinished code from the selected branch. Back up an important Actual budget before testing development builds, and confirm important changes have synchronized before switching builds or uninstalling Actua.
