# WASTI NEXUS 0.5.10 — release candidate

Updated: 2026-11-27

## Version and upgrade compatibility

- Version name: `0.5.10`
- Version code: `15`
- Implementation commit: `3653563141c59ae56205c99889b16125d64ac628`
- This increments the previous 0.5.9 version code 14, so Android can install it as an in-place update over 0.5.9 using the same signing identity. Do not uninstall the existing app.

## Changes in this candidate

- On-demand local shared-storage search with bounded file traversal and text excerpts.
- Canonical path checks before opening files and before descending into directories; symlinks targeting outside the shared-storage root are rejected.
- Streaming text reads enforce the 128 KiB limit even if a file changes size during reading.
- JVM regression tests cover canonical path containment, symlink escape rejection and oversized text reads.
- Remote file-context sharing remains off by default and requires a separate opt-in.

## Verification

GitHub Actions was triggered for the implementation commit:
- [Android CI](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions)
- [Signed Android release](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions)
- [CodeQL security analysis](https://github.com/Nabeelwasti/Wasti-AI-Agent/security/code-scanning)

Do not install a previous 0.5.9 artifact as if it contained these changes. Install only the signed 0.5.10 artifact built from the implementation commit above after its release workflow succeeds and signature verification is green.

## Installation after verification

1. Open the successful signed-release workflow run for commit `3653563141c59ae56205c99889b16125d64ac628`.
2. Under **Artifacts**, download `wasti-nexus-signed-release-apk`.
3. Extract the ZIP and install `app-release.apk`.
4. Keep the existing app installed; Android should offer an update.
5. If Android reports a signature mismatch, stop. Do not uninstall or clear app data; verify the artifact and signing certificate first.

## Known production gates

This release candidate is not a fully autonomous phone agent or fully production-certified service. It does not implement a model tool executor, PDF/Office extraction, OCR, audio/video transcription, offline vision, SMS/call-log roles, Health Connect, notification ingestion, device-owner provisioning, app installation, NEXUS-owned persistent reminders or arbitrary third-party split-screen orchestration. The deployed gateway has no live inference provider configured, and no physical Redmi Note 14 Pro test evidence is available from this environment.
