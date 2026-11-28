# WASTI NEXUS — verified implementation status

**Snapshot:** 2026-11-27  
**Validated application code commit:** `d061d8b1369fb8064e846ec88d6d3fdcbc4a263d`  
**Android release:** 0.5.5 (`versionCode 10`)  
**Repository:** `Nabeelwasti/Wasti-AI-Agent`  
**Workflow:** PR #35 was merged earlier; all subsequent work in this task was committed directly to `main`. No new PR was opened.

## Implemented in main

### Android chat workspace
- Kotlin/Jetpack Compose client with native Arm AI Chat / llama.cpp GGUF runtime; one local GGUF is loaded at a time.
- Automatic selected-model loading, per-model install/load/verification state and visible failures.
- Task-aware local model selection and network/RAM/thermal/battery-aware route policy. Offline mode does not send inference prompts to the gateway.
- Local generation streams tokens and supports Stop. Remote single-model chat supports SSE token streaming; Auto and Hybrid remain non-streaming. HTTP cancellation closes the Android request.
- The local model preflight estimate was reduced from 2.5× model size + 1.2 GB to 1.2× + 512 MB, and model selection uses the same estimate. This removes the old conservative false rejection; the Arm native runtime still uses an 8,192-token context and 512-token batch, so real memory use remains model/device dependent and the estimate is not a guarantee.
- A source index is generated at build time from Android Kotlin/UI resources/tests, gateway/configuration, orchestrator, SDKs, Termux companion, model scripts/registry, workflows and docs. App-related prompts retrieve relevant excerpts for every local or configured remote brain; the complete source index is on-device, but each request receives only relevant excerpts within the model's finite context window.
- Selectable text across the full Scaffold, chat/settings surface and key dialogs; copy/select/edit chat messages; safe Markdown rendering for headings, bold, italic, inline/fenced code, quotes and lists. Model output is rendered as text; HTML is not executed and links are not automatically opened.
- Durable local chat history: AES-256-GCM encrypted with a non-exportable Android Keystore key; atomic file writes with backup recovery; no artificial session/turn/5 MB caps; search includes remote turns, open, rename, delete and Markdown export.
- Chat history no longer has app-imposed session, turn or 5 MB caps. The encrypted file remains subject to available device storage and process memory; the current single-file JSON store may become slower as it grows. Attachments themselves are not persisted with the conversation; only the prompt text and attachment names are retained.
- Chat history remains on this device. It is not silently uploaded or included in the GitHub memory snapshot system. Markdown export writes to a destination explicitly selected by the user.
- Model library cards are visible without first connecting Drive. Each card carries its own on-device load/install status and its action; the standalone model-health list was removed. Drive download actions prompt for folder access when not yet connected.
- The header now uses compact icon actions for new chat, history and settings; system dynamic light/dark colors; composer attachment, Hybrid team quick control, Send/Stop icons. Hybrid team is described in plain language and requires at least two distinct online brains.
- Text/code attachments: TXT, MD, CSV, JSON, XML, HTML, KT, PY, JS and LOG; each file up to 128 KB. No app-level combined text prompt character cap; model/provider context and request limits still apply.
- JPEG, PNG and WebP attachments: each image up to 1 MB, at most two images per request and four attachments total. Images route only to online/Auto vision-capable providers, never to local text-only inference.

### Gateway and orchestration
- Cloudflare Worker is a protected relay to owner-controlled self-hosted OpenAI-compatible providers. It has no Workers AI binding and stores no model weights.
- Single explicitly selected model supports OpenAI-compatible SSE; Auto and Hybrid reject `stream=true` rather than faking progress.
- Client cancellation propagates to upstream provider fetches; streaming cleanup clears timers and abort listeners.
- Worker validates structured content, inline JPEG/PNG/WebP base64 and MIME, image count/size, aggregate text and total request size. Remote image URLs are rejected.
- Vision routes only to a model whose configured capability list explicitly includes `vision`.
- Provider endpoints require HTTPS, reject credentials/query/hash and block localhost, local/internal names, IPv6 literals and private/reserved IPv4 address ranges. Provider URLs remain owner-controlled configuration; DNS rebinding is not claimed to be fully prevented by hostname checks.
- Customer mode is the default when `NEXUS_DEPLOYMENT_MODE` is absent and fails closed if the rate-limit binding is missing. Scoped client keys cannot access the personal memory vault.
- Auto task routing and Hybrid parallel independent answers, bounded peer review and synthesis remain provider-dependent. Returned traces contain actual provider metadata/output; no hidden chain-of-thought is exposed.
- Python orchestrator, Termux companion, model registries, licensing notices and SDK sources remain in the single monorepo.

## CI, release and deployment evidence

- Latest application CI: [run 45987949978](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45987949978), completed successfully on code commit `d061d8b1369fb8064e846ec88d6d3fdcbc4a263d`. All four jobs passed: Android debug build/JVM tests, Python/source validation, Cloudflare Worker syntax/unit tests, and deployed relay-policy smoke test.
- Android JVM suite: **37 tests passed, zero failures** (34 prior tests plus 3 new large-model/history regression tests).
- Cloudflare Worker suite: **30 tests passed, zero failures** on the tested Worker source, including SSE, unsupported Auto/Hybrid stream behavior, memory-scope isolation, customer rate-limit default and private/reserved provider endpoint rejection.
- Three-language CodeQL security-extended workflow: [run 45987949917](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45987949917), all Java/Kotlin, Python and JavaScript/TypeScript jobs completed successfully. Successful analysis does not itself mean every alert is resolved; alert triage remains open.
- Owner-signed Android 0.5.5 APK: [release run 45987949934](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45987949934), artifact `wasti-nexus-signed-release-apk`, artifact ID `15193295298` (21,812,491 bytes). APK Signature Scheme v2 verification passed.
- Signing certificate SHA-256: `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`; it matches the previous signing identity, so 0.5.5 can upgrade over the existing app without uninstalling or clearing data.
- Debug APK from the same CI run: artifact ID `15192187055`; use the signed release artifact for normal installation.
- Worker `wasti-nexus-gateway` was updated through Cloudflare's content-only API on 2026-11-27 at `08:39:54Z` (deployment ID `0222800e42194dcfb6bd8b14d139f374`). The API read-back confirmed the deployed source contains SSE, provider-address validation, personal-memory scope isolation and customer-default rate limiting.
- The content-only deployment preserved existing Worker settings and bindings: `ALLOWED_ORIGINS`, `MAX_HYBRID_AGENTS`, `NEXUS_API_KEY`, `NEXUS_DEPLOYMENT_MODE`, `NEXUS_GITHUB_TOKEN`, memory repository/branch/prefix, `NEXUS_RATE_LIMIT` and `REQUEST_TIMEOUT_MS`. Secret values were not read or exposed.

## Known deployment and product limits

- **No inference provider is configured in the deployed Worker.** `NEXUS_PROVIDERS_JSON` is absent and the live model catalogue has no configured inference model. The relay and SSE code are deployed and mocked/unit-tested, but no live authenticated text or vision round-trip is possible yet.
- The provider setup requires an owner-controlled HTTPS OpenAI-compatible inference server. The provider's credential, if enabled, is stored as a Worker secret. This is not a paid model-vendor API requirement; local GGUF inference remains key-free. Remote gateway authentication still requires the owner's NEXUS bearer key.
- Vision code is deployed and tested, but no live vision provider is configured.
- No physical Redmi Note 14 Pro regression suite has been run from this environment. APK build/signing does not prove device behavior. The 1.2× + 512 MB preflight reduces false rejections but cannot force unsupported architectures or models whose native working set exceeds the phone's actual process budget.
- Chat history is encrypted at rest locally, but there is no cross-device encrypted chat-history sync/restore workflow. Markdown export is plaintext at the user-selected destination.
- Auto/Hybrid remote streaming and live inter-agent progress are not implemented; only explicitly selected single-model requests stream tokens. Hybrid also remains unavailable until at least two online provider models are configured.

- The UI now follows familiar modern Material chat patterns, but this is not complete Gemini feature parity or a pixel-for-pixel copy. Deep Research, full web search, audio/video, image generation and arbitrary tool execution are not implemented; no fake controls are exposed.

## Production gates still open

1. **Provider readiness and live E2E:** deploy an owner-controlled HTTPS inference endpoint; set `NEXUS_PROVIDERS_JSON` and provider secret(s); verify exact model IDs, immutable checkpoints, licences, capabilities, TLS, quotas and capacity. Then test authenticated text inference and a real vision request.
2. **Physical Android validation:** install 0.5.5 over the existing app; test all model load states, generation, Stop, Activity recreation, low RAM, thermal/battery, airplane mode, Wi-Fi/LAN, mobile data, attachments, encrypted history recovery and upgrade signing on the actual Redmi Note 14 Pro.
3. **CodeQL alert triage:** inspect GitHub Security findings from the successful scan, resolve confirmed high/critical issues and document justified suppressions.
4. **Remote streaming completeness:** SSE is single-model only. Add resumable/error-aware Auto and Hybrid progress streaming if required by the product.
5. **History recovery:** add a tested encrypted export/import and cross-device recovery design if users need chat migration. Current Markdown export is readable plaintext, not a restorable encrypted backup.
6. **Tools/actions:** permission-scoped tool registry, user approval UX, sandbox, audit trail and revocation are not implemented. Do not enable arbitrary model-directed actions.
7. **Additional modalities:** PDF text extraction, audio transcription/speech, offline vision, image generation and training runtimes are not certified.
8. **Model archive:** the 50-entry governance catalogue is not 50 installable models. Exact checkpoint, immutable revision, checksum, licence, runtime compatibility and benchmark evidence remain per model. The original twelve GGUF archive entries remain preserved.
9. **Memory recovery:** complete live encrypted-memory upload/restore and cross-device recovery drills; document retention and GitHub ciphertext metadata implications.
10. **Production operations:** configure and verify per-customer isolation, provider quotas, monitoring/alerts, incident response, backups, abuse handling, retention/deletion policy and operational ownership.
11. **Repository governance:** `main` is not branch-protected. Direct-to-main remains the requested workflow, but repository owner should configure least-privilege access, MFA and recovery controls without requiring PRs if direct pushes must remain enabled.

## Release decision

**Status: signed 0.5.5 release candidate; not fully production-certified.** Current main application CI, 37 Android JVM tests, 30 Worker tests, three-language CodeQL execution and signed release build pass. The Worker source is deployed with existing bindings preserved. However, no inference provider is configured and no physical-device validation evidence exists. Persistent history, Markdown chat rendering and single-model remote streaming are now implemented; several customer-grade, recovery, tool and multimodal gates remain open. Do not advertise the whole product as production-ready until the open gates are closed with evidence.


---

## 2026-11-27 follow-up: 0.5.9 shared-storage search

**Implementation commit:** `3903f209f7e0c12073ce31f0bca56f92786b0f2e`  
**App version:** 0.5.9 (`versionCode 14`)  
**Status:** CI/release verification pending for this follow-up.

### Newly implemented
- Optional Android All files access declaration and direct Settings handoff.
- On-demand shared-storage search triggered by natural-language requests such as "find my passport photo".
- Bounded scan (12,000 filesystem entries, ten results maximum); filename matching for supported text, media and document formats.
- Bounded local text extraction for supported text/code formats; metadata-only results for PDF, Office, image, video and audio files.
- Android/data and Android/obb are excluded; other apps' private storage is not accessible.
- Search excerpts are labeled untrusted in model context.
- Remote file-context sharing is off by default and requires a separate Settings opt-in. If it remains off, file-search requests fail closed when routing would send excerpts to a remote model.
- Four JVM tests cover search intent detection and prompt-context safety.

### Still not implemented
- PDF text extraction, OCR, Office parsing, audio/video transcription and semantic embeddings.
- SMS and call-log access; these are hard-restricted permissions and cannot be granted unless the installer/role allowlists them. A user tapping a Settings button cannot override that OS restriction.
- A full default SMS or dialer implementation.
- Health Connect data connectors and sensor history.
- Background location, notification content ingestion, device-owner provisioning, package installation, PIP/split-screen orchestration.
- Persistent NEXUS-owned reminders and a general model-directed action executor.
- A full agent tool loop with action planning, permission-aware tool schemas, approval UX, result observation, cancellation and audit logs.
- Physical Redmi Note 14 Pro tests.

### Privacy boundary
The file-search permission is optional and user-granted. Search is on demand, not continuous. File contents are not automatically persisted into chat history or long-term memory. Remote sharing is a distinct opt-in. Android's protected app directories remain inaccessible.

## 2026-11-27 verified 0.5.9 release and storage-boundary hardening

**Storage-hardening implementation commit:** `7e1a0062f75e1c554f0d710280f9ba549558cb1e` (includes canonical-path and bounded-read tests; later documentation-only commits may advance main).  
**App version:** 0.5.9 (`versionCode 14`).  
**Release candidate artifact:** signed APK from [workflow run 46031465824](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465824), artifact ID `15215335359`, SHA-256 `ff2c3c233f445949dcc831cb3bac41844dc7591c7f10f529a37de7c0f0aa32cf`. Artifact retention expires 2026-12-27 unless downloaded earlier. No GitHub Release/tag has been published.

### Latest verification evidence before this follow-up hardening

- CI run [46031465834](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465834): all four jobs passed, including Android debug assembly, Android JVM tests, Python/source validation, Worker syntax/unit tests and deployed gateway policy smoke test.
- Signed release run [46031465824](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465824): signed APK assembled, signature verified, artifact uploaded.
- Security run [46031465780](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46031465780): Java/Kotlin, JavaScript/TypeScript and Python CodeQL analyses completed successfully. A successful scan is not proof that the repository has zero alerts; alert triage must still be checked.

### Additional storage hardening committed after that build

- Canonical path containment is checked before opening any shared-storage file or descending into a directory, preventing a symlink inside shared storage from being used to read a target outside the shared-storage root.
- Text reads now enforce the 128 KiB ceiling while streaming, even if a file grows after its initial size check.
- Added JVM coverage for canonical path containment and symlink escape rejection.
- These changes require a fresh CI and signed-release run; the earlier successful artifact is for the prior commit and does not contain this follow-up hardening.

### Corrected status interpretation

The earlier sections in this file describe prior milestones and should not be read as the current release snapshot. Current Android build/test, signed APK, CodeQL, live inference-provider setup and physical-device test results must be taken from the latest workflow runs and actual device evidence. The app is a signed release candidate, not a fully production-certified autonomous phone agent.


### Follow-up correction: implementation head and 25-commit review

The shared-storage security hardening is present through implementation commit `7e1a0062f75e1c554f0d710280f9ba549558cb1e`. The most recent 25 commits were reviewed at the per-commit diff level. They cover the 0.5.8 accessibility/screenshot/streaming/new-chat fixes and the 0.5.9 file-search implementation, permission UX, privacy documentation, resource bounds, and subsequent symlink/read-limit hardening. No unrelated files or model/gateway credentials were changed in this follow-up. CI, signed release and CodeQL results for the implementation head are tracked by runs 46034162193, 46034162260 and 46034162346 respectively; do not treat them as passed until their final conclusions are success.


## 2026-11-27 release candidate 0.5.10

**App version:** `0.5.10` (`versionCode 15`).  
**Implementation commit:** `bc233fb2b39db0bfb9d9e1af13223b04d6c978e7`.  
This version-code increment is intentional: the earlier 0.5.9 artifact used versionCode 14, so a device that already installed that APK can upgrade without uninstalling or clearing app data.

The 0.5.10 source includes the shared-storage symlink containment check, bounded streaming text reads, and regression tests for out-of-root/symlink paths and oversized text files. A further privacy review found and closed an AUTO-mode fallback path that could otherwise send local file excerpts to a remote model after local inference failed; a policy helper and regression test now require the same explicit opt-in on fallback. The signed release, Android CI and CodeQL workflows have been triggered for the implementation commit. Their final results and the signed artifact must be checked before installing; prior run results do not certify this commit.


### 0.5.10 verification completed

All three workflows completed successfully for implementation commit `bc233fb2b39db0bfb9d9e1af13223b04d6c978e7`:

- Android CI [run 46034757596](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757596): Android debug APK assembly, Android JVM tests, ephemeral release-signing validation, Python/source validation, Worker tests and deployed gateway-policy smoke test all passed.
- Signed release [run 46034757379](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757379): signed APK assembled, APK Signature Scheme v2 verification passed, and artifact uploaded.
- CodeQL [run 46034757345](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46034757345): Java/Kotlin, JavaScript/TypeScript and Python analyses all completed successfully. This is not a claim that every repository alert is resolved; alert triage remains a separate review.

**Verified signed APK artifact:** `wasti-nexus-signed-release-apk`, artifact ID `15216636058`, size 21,878,426 bytes, SHA-256 `e8ad82defbbbebb1497bb14ac324a819c2d85ea64fd32fae9512dc9576304a9b`, expires 2026-12-27.  
**Signing certificate SHA-256:** `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`, matching the previously verified owner signing identity. This supports in-place upgrade without uninstalling or clearing app data.

The workflow checks validate source/build/signature, not real-world behavior on the owner's Redmi device. Live inference provider configuration and physical-device acceptance testing remain open.


## 2026-11-27 — 0.5.11 reminder implementation

Added a native on-device reminder manager and a narrow chat intent path:
- User can create, list and delete reminders from Models & settings → My reminders.
- Strict chat phrases such as `Remind me to call home in 15 minutes` and `Remind me to submit assignment tomorrow at 5:30 pm` are parsed deterministically. The exact title and time are shown in an approval dialog before anything is saved.
- Reminders are persisted locally, scheduled with Android's inexact `AlarmManager.setAndAllowWhileIdle`, and restored after reboot and system time/time-zone changes.
- Notification permission is requested only when a reminder is saved; denied notification permission does not crash the receiver.
- Invalid/corrupt reminder data is not silently overwritten; the UI surfaces a recoverable error.
- Unit tests cover validation, relative parsing, tomorrow parsing, unsupported syntax and ID allocation.

**Important limitations:** Android may defer inexact alarms under Doze, battery optimization, OEM background restrictions or force-stop. This is not exact scheduling and must not be used for safety-critical reminders. General model tool execution, other device actions and real-device acceptance testing remain outstanding. CI/release/CodeQL must pass for the exact final commit before publishing an APK.


### Follow-up: approval-gated device action foundation

A small deterministic device-action executor is now connected to the chat composer. Supported exact commands include `tap the Settings button`, `scroll down`, `scroll up`, `go back`, `go home`, `open notifications`, and `read current screen`. The app shows an approval dialog before executing. High-impact/destructive button labels are rejected; screen text is displayed locally and is not sent to a model. AccessibilityService acceptance is not proof that the target app completed an operation.

This is not the requested general autonomous agent. Typing, arbitrary taps, sending messages, calls, app installation, changing permissions, multi-step plans, general model tool calls and action postcondition verification remain unimplemented. The device-action additions require a fresh successful Android build and JVM test run before release.


### Follow-up: on-demand notification reading

Added a user-enabled Android NotificationListenerService that does not cache notification events or store notification content in the background. The chat command `read my notifications` requires a separate confirmation and returns a bounded snapshot of active notifications locally. Notification access is independent of AccessibilityService access. Content may be saved in the encrypted chat history because the user explicitly requested the snapshot; it is not sent to a model by this command. The feature requires physical-device verification, including permission revocation and OEM behavior.


## 2026-11-28: bounded on-device document and image analysis foundation

- App source version advanced to 0.5.13 / versionCode 18.
- Added an on-device extraction service for text PDFs (up to 100 pages), modern OOXML formats (DOCX/XLSX/PPTX), and OpenDocument formats (ODT/ODS/ODP).
- Added bundled ML Kit text recognition and image labeling for local image OCR and coarse visual labels. These labels are not a natural-language visual caption and are not equivalent to a multimodal reasoning model.
- Enforced 20 MB input, 32 MB expanded archive, 500-entry archive and 200,000-character output limits. XML parsing disables DTD/external entities.
- Integrated into the attachment picker and bounded shared-storage content search. Local extraction results are added to the current prompt as untrusted document context; remote file-context sharing remains subject to the existing explicit opt-in.\n- Supported images retain the original image for optional online vision and also add local OCR/visual-label context. In Offline mode, only OCR/labels are used; no visual reasoning or image pixels are sent to a server.\n- Legacy binary DOC/XLS/PPT, scanned-PDF page OCR, HEIC/HEIF, semantic image description, and video/audio extraction remain unsupported. Office extraction is limited to text-bearing XML parts and is not a full fidelity layout/table reconstruction.
- CI, signed release verification and CodeQL for the exact final commit must pass before installation. No physical Redmi test is claimed.


## 2026-11-28: contacts and safe phone handoff

- Added a Contacts & calling screen reachable from the navigation drawer.
- READ_CONTACTS is requested only when the user opens that screen. The address book is queried on demand, capped at 1,000 phone entries, kept in memory and not uploaded or persisted by NEXUS.
- Contact actions launch Android's dialer (ACTION_DIAL) or SMS composer (ACTION_SENDTO). The user reviews and initiates the call/message in the system app; NEXUS does not silently place calls or send SMS.
- SMS inbox/history and call-log reading remain unavailable in this build. READ_SMS/RECEIVE_SMS and READ_CALL_LOG are restricted by Android role/installer policy. NEXUS does not request default SMS/dialer takeover or attempt to bypass the restriction.
- CI and physical-device permission-revocation tests remain pending for this feature.


## 2026-11-28: one-time foreground location

- Added a My location screen that requests Android approximate location only after the user opens the screen and taps Get current location.
- Uses Android's one-shot foreground LocationManager API, a 25-second timeout and cancellation. No background-location permission, continuous updates, location history or automatic remote upload.
- Shows coordinates, reported accuracy and fix time; opening Maps is a separate user action.
- Permission denial/revocation is handled without retry loops. Physical Redmi/HyperOS location-provider testing remains required.


## 2026-11-28: Health Connect and foreground step sensor

- Added a Health & sensors screen using the stable Health Connect client (1.1.0).
- Health Connect requests read-only access to Steps and HeartRate records only, and displays a seven-day aggregate/summary locally. Permissions are checked again before reading so revocation is handled.
- Added optional Android TYPE_STEP_COUNTER access with ACTIVITY_RECOGNITION permission. Sensor listener is active only while the Health & sensors screen is foregrounded and is unregistered in onStop. The counter is cumulative since reboot, not daily steps.
- No health write, background health read, background sensor collection, or health-data upload is implemented.
- Device availability, HyperOS sensor behavior and Health Connect permission/revocation tests remain pending.


## 2026-11-28: Android PackageInstaller handoff

- Added an explicit Install APK screen. The user selects an APK through Android's Storage Access Framework; NEXUS streams it into a PackageInstaller session with a 512 MB cap.
- If needed, NEXUS opens Android's per-source Install unknown apps setting. The user can revoke this setting after installation.
- The system PackageInstaller remains responsible for package/signature verification and the final install/upgrade confirmation. NEXUS does not use root, hidden APIs, silent install, downgrade bypass or package replacement tricks.
- Installer status is reported through a private receiver and locally stored status text. Physical install/upgrade/rejection tests remain required.


## 2026-11-28: picture-in-picture and Health Connect privacy rationale

- Enabled NEXUS activity resizeability and Android picture-in-picture support. A PiP control appears only when Android reports the feature and API level are supported; the user must tap it. Split-screen placement remains controlled by Android Recents/system UI.
- Added the Health Connect permission rationale activity and Android 14+ permission-usage alias. It explains the exact read-only Steps/Heart Rate data use, local processing and lack of background health reads/uploads.
- PiP sizing/interaction and Health Connect rationale launch paths still need device verification.
