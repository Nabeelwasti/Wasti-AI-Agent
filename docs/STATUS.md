# Verified implementation status

Evidence snapshot: 2026-11-26. Latest application change: PR #26 merged as `fae12f79135d1ee15dc856420669fab3370a7db3`. PR #25 selected Google Drive as the sole model archive; PR #26 added the Android SAF folder picker, verified Qwen Drive installer, twelve-family catalogue UI and local-first Auto routing.

## Executive status

WASTI NEXUS is an active, tested release candidate in one monorepo. It is **not yet production-certified**. PR #16 (resumable verified Qwen model downloads) and PR #17 (Gemini-style Android chat interface) are merged into `main`.

- PR #16: https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/16
- PR #17: https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/17
- Latest post-merge main CI: https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45859639925 (run #300, commit `348201f11383c6fe3a533881d1d96339a6670459`) completed successfully. Android debug APK assembly, Android JVM tests, ephemeral CI signing validation, Python tests/source validation, Cloudflare Worker syntax/unit tests, deployed gateway health and protected-route rejection all passed.
- The deployed authenticated chat-generation step was **skipped**, not passed: GitHub Actions logged that repository secret `NEXUS_API_KEY` is not configured. Therefore live authenticated inference through the Worker remains unverified.
- A separate owner-controlled signed release workflow completed successfully on 2026-11-26 (run #1, commit `4df9d4b971128eeaf7d207fd6ed49dee173c3909`). The permanent keystore was decoded, the release APK assembled, `apksigner verify --verbose --print-certs` passed, and artifact `wasti-nexus-signed-release-apk` was uploaded (17,277,969 bytes; expires 2026-12-26). The signing certificate is RSA 4096, subject `CN=Syed Nabeel wasti, OU=Wasti Nexus, O=ThriveBridge Growth Solutions, L=Lahore, ST=Punjab, C=PK`, certificate SHA-256 `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`. **This signed APK is from an earlier application commit**, before the dependency refresh now on `main`; rebuild the permanent signed APK from the current `main` before treating it as the latest release.
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
- Google Drive workspace access uses Android's Storage Access Framework folder picker. The owner selects one private Drive folder and NEXUS persists read access only to that folder; no AI API key, Google API key, OAuth client or service-account key is required for local model storage.
- The Qwen2.5-0.5B-Instruct Q4_K_M Drive installer copies the exact 491,400,032-byte file into app-private temporary storage, verifies exact byte length and SHA-256, then installs it. SAF transfers restart from zero after interruption; HTTP Range resume is not claimed. The owner must upload the exact Qwen GGUF into the selected Drive folder.
- JVM tests cover gateway fallback policy and model Content-Range parsing. CI build success does not certify UI, model runtime, speed, thermal behaviour or battery on a physical device.
- **Not yet device-certified:** Google Drive provider access and large-file transfer on the target Redmi, interruption/retry, permission revocation/reconnect, checksum rejection, airplane-mode generation, model unload/reload, RAM/thermal/battery/latency, app upgrade and small-screen/keyboard visual QA.

## Model catalogue and licensing

- Twelve requested families are research candidates: Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM.
- This is **not** twelve verified downloadable local models. The Android catalogue displays all twelve families, but only Qwen2.5-0.5B-Instruct Q4_K_M currently has a pinned source revision, exact byte length and SHA-256 and is marked downloadable.
- The other eleven entries remain pending exact checkpoint revision, checksum, licence, GGUF/runtime compatibility and device-fit evidence. The app refuses to download them until those gates are met.
- Google Drive is the sole owner model archive. No model weights are committed to Git or stored in Cloudflare. Cloudflare Workers AI remains optional hosted inference only.
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
3. **Device certification:** install the app on the target Android phone; connect the private Drive folder through the system picker; test Qwen file discovery/transfer, interruption and restart, checksum rejection, permission revocation/reconnect, airplane-mode generation, local-first Auto behaviour, model lifecycle, upgrade, RAM, thermal, latency and battery.
4. **Permanent release signing:** the permanent owner keystore and four GitHub signing secrets have been successfully used once; the signed artifact from commit `4df9d4b` passed APK signature verification. Re-run the manual release workflow against the current `main` (the earlier artifact predates dependency updates), download the new signed APK, and test install/upgrade on the target phone. CI's ephemeral keystore validates the signing pipeline but is not the permanent owner key.
5. **Memory verification:** complete live encrypted snapshot write/list/read/restore, cross-device recovery, backup/retention/deletion documentation and verify that no plaintext reaches Worker/GitHub.
6. **Model archive:** the private Google Drive workspace is selected in the Android app, but the owner must upload the exact Qwen file before the first live transfer can be tested. Pin and checksum each of the remaining eleven checkpoints, review all licence terms, verify conversion/runtime compatibility and device fit, then enable only approved models. Do not present the twelve families as twelve ready-to-install models.
7. **Operational readiness:** add production monitoring, alerting, incident response, privacy/retention disclosures, service ownership and documented recovery procedures.

## Current honest conclusion

The repository now includes a native Android app, a twelve-family catalogue, a scoped Google Drive folder picker, a verified Qwen Drive-to-device installer, local-first Auto routing, and an optional two-model Cloudflare inference gateway. PR #26's Android debug build, JVM tests and ephemeral release-signing verification passed in CI (run `45869848845`); Python validation and Worker tests also passed. This does not certify real Drive-provider transfers or phone performance. The owner must upload the exact Qwen GGUF to the private Drive workspace and select that folder in the app. The other eleven models remain unverified and unavailable. Latest-main permanent signed APK, physical-device testing, authenticated hosted inference, memory recovery and customer-grade security remain separate gates. This is an improved **release candidate, not yet fully production-certified**.
