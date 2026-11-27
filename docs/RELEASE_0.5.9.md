# WASTI NEXUS 0.5.9 — release and verification record

Updated: 2026-11-27

## Current scope

Version 0.5.9 (versionCode 14) includes local GGUF chat, encrypted on-device chat history, model library/workspace support, explicit photo/document attachments, optional one-shot accessibility snapshots and gestures, and bounded on-demand shared-storage file search. File search supports filename matching and bounded excerpts from selected text/code formats. PDF/Office/media are metadata-only search results. Remote file excerpts require a separate opt-in; otherwise file-search requests fail closed if they would route to a remote model.

## Verified prior build

The signed APK for commit `553b6f5c1fab41d8855cadb5eadac749838102be` was built and signature-verified by [release workflow 46031465824](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465824). Artifact ID: `15215335359`; SHA-256: `ff2c3c233f445949dcc831cb3bac41844dc7591c7f10f529a37de7c0f0aa32cf`. It expires from Actions artifacts on 2026-12-27 unless downloaded. Download it from the workflow run's **Artifacts** section. It does not include the subsequent path-hardening commit.

The same source passed [CI run 46031465834](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465834) and completed all three CodeQL language analyses in [security run 46031465780](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465780). These are build and static-analysis results, not physical Redmi device validation or proof of zero security alerts.

## Follow-up security hardening

The subsequent commits move canonical-path validation ahead of file reads and directory traversal, preventing shared-storage symlink escapes. Text input is now read with an enforced streaming byte limit rather than relying only on the file's initially reported size. JVM tests cover in-root/out-of-root paths and symlink escape rejection. A fresh CI and signed APK run are required before treating this follow-up as release-verified.

## Not production-certified / not implemented

- No owner-controlled live inference provider is configured in the Cloudflare gateway; the gateway is a relay, not a model host.
- No physical Redmi Note 14 Pro test evidence is available from this development environment.
- The AccessibilityService primitives are not connected to an autonomous AI tool loop.
- PDF extraction, OCR, Office parsing, audio/video transcription, offline vision and image generation are not implemented.
- SMS/call-log access, default SMS/dialer roles, health/sensor integrations, background location, notification-content ingestion, device-owner provisioning and APK installation are not implemented.
- NEXUS-owned persistent reminders, PiP/split-screen orchestration and a permission-scoped action executor are not implemented.
- Encrypted cross-device chat-history restore, complete memory recovery drills, provider operations/monitoring and CodeQL alert triage remain open.
- Android All files access is optional and may be subject to distribution policy restrictions. Android/data, Android/obb and other apps' private sandboxes are excluded.

## Safe installation

For the previously verified build, open the [signed release workflow](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465824), scroll to **Artifacts**, and download `wasti-nexus-signed-release-apk`. Extract the ZIP and install `app-release.apk`. Do not uninstall the existing app first; that can destroy local app data. This APK is the build from commit `553b6f5c1fab41d8855cadb5eadac749838102be`, not the later storage-hardening commit.

For the next build, wait for the new signed-release workflow attached to the current main commit and verify the artifact's commit SHA before installing.
