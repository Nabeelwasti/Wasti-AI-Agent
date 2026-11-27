# WASTI NEXUS Android release signing

The regular CI workflow produces a debug APK for development and sideload testing. It is not a production release. A production APK must be signed with a stable release keystore that the project owner controls.

## Create and protect the release keystore

Run this on a trusted computer or Termux environment. Keep the keystore and passwords private; never commit them or paste them into a chat.

```bash
keytool -genkeypair -v \
  -keystore wasti-nexus-release.jks \
  -storetype JKS \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias wasti-nexus
```

Back up `wasti-nexus-release.jks` in at least two secure offline locations. If the signing key is lost, you may not be able to publish updates under the same signing identity. Do not reuse the AirPad or another app's keystore.

## Configure GitHub Actions secrets

In the repository's Settings → Secrets and variables → Actions, create these repository secrets:

- `NEXUS_RELEASE_KEYSTORE_BASE64` — base64-encoded contents of the JKS file.
- `NEXUS_RELEASE_KEYSTORE_PASSWORD` — keystore password.
- `NEXUS_RELEASE_KEY_ALIAS` — `wasti-nexus` (or the alias you chose).
- `NEXUS_RELEASE_KEY_PASSWORD` — key password.

On Linux/Termux, produce the base64 value without line breaks:

```bash
base64 -w 0 wasti-nexus-release.jks
```

On macOS:

```bash
base64 < wasti-nexus-release.jks | tr -d '\n'
```

Paste that output directly into the GitHub secret value. Do not put the base64 value in the repository.

## Build and verify

After all four secrets are configured:

1. Open Actions → **WASTI NEXUS signed Android release**.
2. Choose **Run workflow**.
3. Wait for the **Build and verify signed release APK** job.
4. Download the `wasti-nexus-signed-release-apk` artifact.
5. Confirm the signature verification step passed before installing it.

The workflow fails closed when required secrets are absent. It does not silently publish an unsigned APK.

## Release discipline

- Increment `versionCode` and update `versionName` in `android/app/build.gradle.kts` for every public update.
- Keep the signing key and passwords backed up. Do not rotate the signing identity casually.
- Install and test the signed APK on the target device, including an upgrade over the previous signed build.
- The signed artifact is not device-certified until offline GGUF generation, install/upgrade behaviour, memory use, thermal behaviour and battery use have been tested on real hardware.


## Verified Android 0.5.4 release (2026-11-27)

- Version: `0.5.4` (`versionCode 9`).
- Workflow: [WASTI NEXUS signed Android release — run 45968917127](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45968917127).
- Artifact: `wasti-nexus-signed-release-apk`, ID `15184321293`.
- APK signature: APK Signature Scheme v2 verified.
- Release certificate SHA-256: `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636` (same signing identity as 0.5.2 and 0.5.3).
- 0.5.4 includes encrypted local chat history, history search/rename/delete/Markdown export, safe Markdown display and remote single-model SSE.

Install as an upgrade over 0.5.3 without uninstalling or clearing app data. The release build and signature are verified in CI; physical-device behavior must still be tested on the target phone before broad distribution.
