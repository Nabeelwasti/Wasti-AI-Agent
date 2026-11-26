# Verified implementation status

Last updated: 2026-11-26 (main commit `348201f11383c6fe3a533881d1d96339a6670459`).

## Executive status

WASTI NEXUS is an active, tested release candidate in one monorepo. It is **not yet production-certified**. PR #16 (resumable verified Qwen model downloads) and PR #17 (Gemini-style Android chat interface) are merged into `main`.

- PR #16: https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/16
- PR #17: https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/17
- Latest post-merge main CI: https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45859639925 (run #300, commit `348201f11383c6fe3a533881d1d96339a6670459`) completed successfully. Android debug APK assembly, Android JVM tests, ephemeral CI signing validation, Python tests/source validation, Cloudflare Worker syntax/unit tests, deployed gateway health and protected-route rejection all passed.
- The deployed authenticated chat-generation step was **skipped**, not passed: GitHub Actions logged that repository secret `NEXUS_API_KEY` is not configured. Therefore live authenticated inference through the Worker remains unverified.
- The CI debug APK artifact `wasti-nexus-debug-apk` was uploaded successfully (20,508,214 bytes; SHA-256 `c08af05480f17c7145614dab8702eac47a99fee15b1fb0f2cfcfa3bcc824e7b5`); it expires 2026-12-03. This is a debug artifact, not a signed production release.

## Repository and architecture

- Canonical repository: https://github.com/Nabeelwasti/Wasti-AI-Agent, default branch `main`.
- One monorepo contains the native Android client, model registry/acquisition tools, Python orchestration API, Cloudflare Worker gateway/web console, SDKs, schemas, tests and documentation.
- No model weights, provider credentials, GitHub tokens, or plaintext personal memory are committed.
- Android toolchain: AGP 9.3.1, Gradle 9.7.0, Kotlin/Compose compiler 2.4.21, Android SDK/Build Tools 36, JDK 17.

## Android client

- Native Kotlin/Jetpack Compose app, package `ai.wasti.nexus`, min SDK 33, target SDK 36, version 0.2.0 (versionCode 2).
- Arm AI Chat `com.arm:ai-chat:0.1.0` supplies in-process GGUF/llama.cpp inference; Termux is not required at runtime.
- The latest UI milestone provides a focused chat screen with app identity/status, New chat, Settings, user/assistant turns, welcome state, persistent composer, keyboard-aware layout, auto-scroll, request progress and visible errors. Model, gateway, hybrid and encrypted-memory controls remain in Settings.
- Modes: Auto tries the HTTPS gateway and falls back to a loaded local model for network errors, missing online credentials and transient HTTP 408/429/5xx; 401/403 are not hidden by fallback. Online uses the gateway. Offline uses the local runtime without sending inference prompts to the gateway.
- Local-only/fallback turns are excluded from later cloud conversation context.
- Model management supports GGUF import, metadata validation, listing/selection, load/unload/removal, RAM/storage preflight and native-memory release.
- The Qwen2.5-0.5B-Instruct Q4_K_M catalogue installer supports HTTP Range resume into app-private partial storage, restart from zero when the server ignores Range, bounded Content-Range validation, progress callbacks, SHA-256 verification, GGUF metadata validation and safe replacement. Retry is user-initiated after reopening; this is not a background WorkManager download.
- JVM tests cover gateway fallback policy and model Content-Range parsing. CI build success does not certify UI, model runtime, speed, thermal behaviour or battery on a physical device.
- **Not yet device-certified:** install/upgrade, one-tap download/resume on phone, airplane-mode generation, model unload/reload, RAM/thermal/battery/latency, and small-screen/keyboard visual QA.

## Model catalogue and licensing

- Twelve requested families are research candidates: Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM.
- This is **not** twelve verified downloadable local models. Only Qwen2.5-0.5B-Instruct Q4_K_M currently has a pinned source revision and expected SHA-256 for the in-app installer.
- Other candidates still require exact checkpoint selection, immutable revision, checksum, GGUF/runtime compatibility, device-fit evidence and exact licence review. Some candidate terms are non-commercial or conditional; do not enable them for customer use without rights review.
- No model weights are committed to Git or uploaded to Workers AI. Use an authorised distribution channel with immutable manifests and integrity checks if additional models are enabled.
- Manual import currently validates GGUF metadata; it does not establish publisher provenance or registry-hash identity for arbitrary imported files.

## Cloudflare gateway

- Worker: `wasti-nexus-gateway`; base URL: https://wasti-nexus-gateway.wastinabeel99.workers.dev
- Canonical source: `cloudflare/src/index.js`; config: `cloudflare/wrangler.toml`.
- Two Cloudflare Workers AI model IDs are configured: `nexus-gemma-4-26b-a4b` (`@cf/google/gemma-4-26b-a4b-it`) and `nexus-qwen-3.8-27b` (`@cf/qwen/qwen3.8-27b`). Both are first-party Cloudflare-hosted model routes and do not require third-party provider API keys. Cloudflare usage is metered; free-tier allowances are not unlimited and can change.
- The Worker supports model listing, chat completion and Hybrid dispatch across two distinct model IDs. Unit tests cover single-model and Hybrid dispatch and reject Hybrid when fewer than two distinct models are available.
- Public health/catalogue checks and unauthenticated rejection for protected model-list routes have passed in earlier deployed smoke runs. Direct account-level model tests are not proof that the deployed authenticated Worker route works.
- The latest main CI deployed authenticated generation smoke was skipped because the GitHub Actions repository secret `NEXUS_API_KEY` is absent. The same run did pass public health and unauthenticated-protection checks; those do not prove authenticated chat generation. Treat authenticated end-to-end generation as **unverified** until a dedicated scoped key is configured and both live model routes return non-empty responses through the Worker.
- The Worker has a configured per-location rate limiter (60 requests / 60 seconds / key); it is approximate, not a strict global quota or billing control.
- `NEXUS_CLIENT_KEYS_JSON` is not populated with application-specific client keys. Never ship the master key in an APK or browser bundle. Public customer deployments need end-user authentication, short-lived token exchange, per-tenant authorization, strict quotas and abuse monitoring.

## Memory and customer data

- Android memory snapshots use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a user-controlled recovery passphrase.
- The Worker transports encrypted envelopes; the passphrase is not sent to the Worker. The backing GitHub repository is public, so paths, timestamps and non-secret metadata remain visible. Only ciphertext and non-sensitive metadata may be stored there.
- The app includes local encrypted snapshot save/restore and Worker-backed ciphertext sync/restore flows. A live remote write/list/read/restore and cross-device recovery drill has not been recorded.
- This is a personal vault, not a tenant-isolated customer memory service. Do not put customer memory in the public personal repository.
- The chat UI's visible conversation state is not a substitute for a tested durable encrypted chat-history store. Confirm persistence behaviour on process death/restart before promising durable chat history.

## Release and production gates

Do not label this build a production-certified customer service until the following evidence exists:

1. **Authenticated live gateway:** create a dedicated chat-only client key (not the master key), configure its hash in Worker secret `NEXUS_CLIENT_KEYS_JSON`, configure the raw key as GitHub Actions secret `NEXUS_API_KEY`, then verify authenticated chat-completion through the deployed Worker for both configured models. The CI smoke test must run, not skip.
2. **Customer security:** implement end-user authentication, short-lived client token exchange, tenant isolation at every data boundary, strict global quotas, abuse controls, monitoring and incident response before exposing customer-facing keys.
3. **Device certification:** install the app on the target Android phone; test UI insets/keyboard/scrolling, Qwen download, interruption/resume, checksum rejection, airplane-mode generation, Auto fallback, model lifecycle, upgrade, RAM, thermal, latency and battery.
4. **Permanent release signing:** owner creates and backs up a permanent keystore, configures the four protected signing secrets described in [RELEASE.md](RELEASE.md), builds a signed APK and tests install/upgrade. CI's ephemeral keystore only validates the signing pipeline.
5. **Memory verification:** complete live encrypted snapshot write/list/read/restore, cross-device recovery, backup/retention/deletion documentation and verify that no plaintext reaches Worker/GitHub.
6. **Model archive:** pin and checksum each exact checkpoint, review all licence and redistribution terms, verify conversion/runtime compatibility and device fit, then enable only legally approved models. Do not present the twelve research candidates as ready-to-install models.
7. **Operational readiness:** add production monitoring, alerting, incident response, privacy/retention disclosures, service ownership and documented recovery procedures.

## Current honest conclusion

The repository now contains a working native Android implementation, a tested resumable verified first-model installer, a refreshed chat UI, a two-model Cloudflare gateway implementation, and a reusable API/SDK foundation. The latest post-merge main CI run completed successfully for Android compilation/JVM tests, Python validation, Worker syntax/unit tests, deployed public health/protected-route checks and ephemeral CI signing validation. Authenticated deployed gateway generation was skipped because `NEXUS_API_KEY` is absent. Physical-device behaviour, permanent owner signing, memory recovery verification and customer-grade tenant/data controls remain separate gates. This is a **release candidate, not a fully production-certified product**.
