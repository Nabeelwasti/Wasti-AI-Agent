# WASTI NEXUS 0.5.10 — release candidate

Updated: 2026-11-27

## Version and upgrade compatibility

- Version name: `0.5.10`
- Version code: `15`
- Implementation commit: `bc233fb2b39db0bfb9d9e1af13223b04d6c978e7`
- This increments the previous 0.5.9 version code 14, so Android can install it as an in-place update over 0.5.9 using the same signing identity. Do not uninstall the existing app.

## Changes in this candidate

- On-demand local shared-storage search with bounded file traversal and text excerpts.
- Canonical path checks before opening files and before descending into directories; symlinks targeting outside the shared-storage root are rejected.
- Streaming text reads enforce the 128 KiB limit even if a file changes size during reading.
- JVM regression tests cover canonical path containment, symlink escape rejection and oversized text reads.
- Remote file-context sharing remains off by default and requires a separate opt-in, including when AUTO mode falls back to a remote brain after local inference fails. A regression test protects this boundary.

## Verification

GitHub Actions was triggered for the implementation commit:
- [Android CI](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions)
- [Signed Android release](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions)
- [CodeQL security analysis](https://github.com/Nabeelwasti/Wasti-AI-Agent/security/code-scanning)

Do not install a previous 0.5.9 artifact as if it contained these changes. Install only the signed 0.5.10 artifact built from the implementation commit above after its release workflow succeeds and signature verification is green.

## Installation after verification

1. Open the successful signed-release workflow run for commit `bc233fb2b39db0bfb9d9e1af13223b04d6c978e7`.
2. Under **Artifacts**, download `wasti-nexus-signed-release-apk`.
3. Extract the ZIP and install `app-release.apk`.
4. Keep the existing app installed; Android should offer an update.
5. If Android reports a signature mismatch, stop. Do not uninstall or clear app data; verify the artifact and signing certificate first.

## Known production gates

This release candidate is not a fully autonomous phone agent or fully production-certified service. It does not implement a model tool executor, PDF/Office extraction, OCR, audio/video transcription, offline vision, SMS/call-log roles, Health Connect, notification ingestion, device-owner provisioning, app installation, NEXUS-owned persistent reminders or arbitrary third-party split-screen orchestration. The deployed gateway has no live inference provider configured, and no physical Redmi Note 14 Pro test evidence is available from this environment.


## 0.5.10 build verification — passed

All three workflows passed for implementation commit `bc233fb2b39db0bfb9d9e1af13223b04d6c978e7`:

- [Android CI run 46034757596](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757596) — Android build, JVM tests, Python validation, Worker tests and gateway smoke test passed.
- [Signed release run 46034757379](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757379) — signed APK built, v2 signature verified and artifact uploaded.
- [CodeQL run 46034757345](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757345) — Kotlin/Java, JS/TS and Python analyses passed.

**Download:** [Signed APK artifact ZIP](https://api.github.com/repos/Nabeelwasti/Wasti-AI-Agent/actions/artifacts/15216636058/zip) or open the [successful release workflow](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757379) and select the `wasti-nexus-signed-release-apk` artifact. GitHub may require you to be signed in. Artifact ID: `15216636058`; SHA-256: `e8ad82defbbbebb1497bb14ac324a819c2d85ea64fd32fae9512dc9576304a9b`; expires 2026-12-27.

The APK signer certificate SHA-256 is `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`, matching the earlier owner-signed release identity. Keep the existing app installed and install this APK as an update.

CodeQL success means the analysis completed; it does not prove there are no open alerts. Physical Redmi Note 14 Pro tests and live inference-provider verification are still required before calling the product fully production-certified.
