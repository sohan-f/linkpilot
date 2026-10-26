# Releasing LinkPilot

Release builds are signed with a local keystore and published by the `Release` GitHub Actions
workflow. The keystore is never committed.

## One-time setup

The release keystore lives at `android/keystore/linkpilot-release.jks` and its local credentials live
in `android/keystore.properties`; both paths are gitignored. CI receives the same material through
repository secrets:

| Secret | Value |
|---|---|
| `LINKPILOT_KEYSTORE_BASE64` | `base64 -w0 android/keystore/linkpilot-release.jks` |
| `LINKPILOT_KEYSTORE_PASSWORD` | keystore password |
| `LINKPILOT_KEY_ALIAS` | `linkpilot` |
| `LINKPILOT_KEY_PASSWORD` | key password |

Set them with `gh secret set <NAME>` from the repository directory.

Keep the keystore and passwords backed up. Android only accepts updates signed with the same key, so
losing them means users must uninstall and reinstall the app.

## Cutting a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag starts the workflow, which builds a signed APK and AAB, verifies the APK signature, and
creates a GitHub release named after the tag. `versionName` comes from the tag and `versionCode`
from the workflow run number. To rebuild an existing tag, run the workflow manually with its tag.

To build and collect the artifacts without publishing, dispatch the workflow with `publish` off:

```bash
gh workflow run release.yml -f publish=false
gh run download <run-id> -n linkpilot-run-<run-number>
```

The artifacts are then attached to the workflow run instead of a release. Publishing requires both a
tag and the signing secrets; without the secrets the release variant still builds, unsigned.

## Local signed build

With `android/keystore.properties` present:

```bash
cd android
./gradlew :app:assembleRelease :app:bundleRelease
```

Without a keystore the release variant still builds, but unsigned.
