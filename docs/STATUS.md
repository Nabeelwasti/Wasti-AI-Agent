# Verified implementation status

Evidence snapshot: 2026-11-26. PR #27 (versionCode 3 / versionName 0.3.0) merged as `2aaf539b4ee0ad6c76b42a37b4a03edc1a8ab5d2`. PR #25 selected Google Drive as the sole model archive; PR #26 added the Android SAF folder picker, verified Qwen Drive installer, twelve-model catalogue UI and local-first Auto routing. PR #27 makes the Drive-enabled app an upgrade over the previous 0.2.0 APK.

## Executive status

WASTI NEXUS is an active, tested release candidate in one monorepo. It is **not yet production-certified**. PR #16 (resumable verified Qwen model downloads) and PR #17 (Gemini-style Android chat interface) are merged into `main`.

- PR #16: https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/16
- PR #17: https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/17
- Latest post-merge main CI: https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45859639925 (run #300, commit `348201f11383c6fe3a533881d1d96339a6670459`) completed successfully. Android debug APK assembly, Android JVM tests, ephemeral CI signing validation, Python tests/source validation, Cloudflare Worker syntax/unit tests, deployed gateway health and protected-route rejection all passed.
- New post-PR-27 main CI run `45871427587` completed successfully. Python tests/source validation, Cloudflare Worker syntax/unit tests, Android debug APK assembly, Android JVM tests, ephemeral release-signing validation, and the deployed gateway health/protection plus authenticated generation smoke test all passed.
- A separate owner-controlled signed release workflow completed successfully on 2026-11-26 (run #1, commit `4df9d4b971128eeaf7d207fd6ed49dee173c3909`). The permanent keystore was decoded, the release APK assembled, `apksigner verify --verbose --print-certs` passed, and artifact `wasti-nexus-signed-release-apk` was uploaded (17,277,969 bytes; expires 2026-12-26). The signing certificate is RSA 4096, subject `CN=Syed Nabeel wasti, OU=Wasti Nexus, O=ThriveBridge Growth Solutions, L=Lahore, ST=Punjab, C=PK`, certificate SHA-256 `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`. **This signed APK is from an earlier application commit**, before the dependency refresh now on `main`; rebuild the permanent signed APK from the current `main` before treating it as the latest release.
- The CI debug APK artifact `wasti-nexus-debug-apk` was uploaded successfully (20,508,214 bytes; SHA-256 `c08af05480f17c7145614dab8702eac47a99fee15b1fb0f2cfcfa3bcc824e7b5`); it expires 2026-12-03. This is a debug artifact, not a signed production release.

## Repository and architecture

- Canonical repository: https://github.com/Nabeelwasti/Wasti-AI-Agent, default branch `main`.
- One monorepo contains the native Android client, model registry/acquisition tools, Python orchestration API, Cloudflare Worker gateway/web console, SDKs, schemas, tests and documentation.
- No model weights, provider credentials, GitHub tokens, or plaintext personal memory are committed.
- Android toolchain: AGP 9.5.3, Gradle 9.7.0, Kotlin/Compose compiler 2.4.21, Android SDK/Build Tools 36, JDK 17.

## Android client

- Native Kotlin/Jetpack Compose app, package `ai.wasti.nexus`, min SDK 33, target SDK 36, version 0.4.0 (versionCode 4) on the PR #28 release candidate.
- Arm AI Chat `com.arm:ai-chat:0.1.0` supplies in-process GGUF/llama.cpp inference; Termux is not required at runtime.
- The latest UI milestone provides a focused chat screen with app identity/status, New chat, Settings, user/assistant turns, welcome state, persistent composer, keyboard-aware layout, auto-scroll, request progress and visible errors. Model, gateway, hybrid and encrypted-memory controls remain in Settings.
- Modes: Auto is local-first and uses an installed compatible local model before any hosted inference. If local inference fails, Auto may use the optional authenticated gateway. Online explicitly uses the gateway. Offline uses the local runtime without sending inference prompts to the gateway.
- Local-only/fallback turns are excluded from later cloud conversation context.
- Model management supports GGUF import, metadata validation, listing/selection, load/unload/removal, RAM/storage preflight and native-memory release.
- Google Drive workspace access uses Android's Storage Access Framework folder picker. The owner selects one private Drive folder and NEXUS persists read access only to that folder; no AI API key, Google API key, OAuth client or service-account key is required for local model storage.
- The Qwen2.5-0.5B-Instruct Q4_K_M Drive installer copies the exact 491,400,032-byte file into app-private temporary storage, verifies exact byte length and SHA-256, then installs it. SAF transfers restart from zero after interruption; HTTP Range resume is not claimed. The owner must upload the exact Qwen GGUF into the selected Drive folder.
- JVM tests cover gateway fallback policy and model Content-Range parsing. CI build success does not certify UI, model runtime, speed, thermal behaviour or battery on a physical device.
- **Not yet device-certified:** Google Drive provider access and large-file transfer on the target Redmi, interruption/retry, permission revocation/reconnect, checksum rejection, airplane-mode generation, model unload/reload, RAM/thermal/battery/latency, app upgrade and small-screen/keyboard visual QA.

## Model catalogue and licensing

- The app catalogue now curates twelve compact open-weight candidates with permissive upstream licence families (Apache-2.0 or MIT, including the inherited Qwen base terms for DeepSeek R1 Distill): Qwen2.5 0.5B, Qwen2.5 1.5B, Qwen2.5 Coder 1.5B, Qwen3 0.6B, Qwen3 1.7B, SmolLM2 135M, SmolLM2 360M, SmolLM2 1.7B, TinyLlama 1.1B, Granite 3.1 2B, DeepSeek R1 Distill 1.5B and OLMo 2 1B Instruct.
- WASTI NEXUS is the product and collection identity; upstream model names, authors, source repositories, licences and required notices are preserved. NEXUS does not claim to have trained or authored these third-party models. See `docs/WASTI_NEXUS_MODEL_LIBRARY.md`.
- This is **not** twelve verified downloadable local models. All twelve model entries now have immutable GGUF source revisions, exact byte lengths and SHA-256 values pinned, with permissive upstream licence evidence. CI audits every pinned file against Hugging Face. All twelve are marked downloadable from the owner's private Drive archive; Android runtime/device validation remains pending.
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
4. **Permanent release signing:** the permanent owner keystore and four GitHub signing secrets have been successfully used once; the signed artifact from commit `4df9d4b` passed APK signature verification. The signed-release workflow now also runs automatically on trusted pushes to `main` (while retaining manual dispatch). After this PR merges, confirm the new signed build run and artifact, then test install/upgrade on the target phone. CI's ephemeral keystore validates the signing pipeline but is not the permanent owner key.
5. **Memory verification:** complete live encrypted snapshot write/list/read/restore, cross-device recovery, backup/retention/deletion documentation and verify that no plaintext reaches Worker/GitHub.
6. **Model archive:** all twelve GGUF source artifacts now have immutable revisions, exact byte lengths, SHA-256 values and permissive base-model licence evidence pinned in the app catalogue and registry. All twelve are marked ready for Drive-to-device installation. The owner must select the private Drive folder and run the new archive-all action; then validate provider write behaviour, transfer integrity and local inference on the Redmi. Do not claim device certification until runtime, RAM, thermal, latency and battery tests pass.
7. **Operational readiness:** add production monitoring, alerting, incident response, privacy/retention disclosures, service ownership and documented recovery procedures.

## Current honest conclusion

The repository now includes a native Android app release candidate at version 0.4.0, a twelve-model permissive open-weight shortlist, a scoped Google Drive folder picker, a twelve-model Drive-to-device installer with pinned integrity manifests, local-first Auto routing, and an optional two-model Cloudflare inference gateway. PR #27 passed its pull-request CI. Post-merge main CI run `45871427587` passed Python validation, Worker syntax/tests and the deployed gateway checks including authenticated live generation; Android build/JVM tests, ephemeral signing validation and the authenticated hosted-generation smoke test all completed successfully. The signed-release workflow now runs automatically on trusted pushes to `main`; confirm the post-merge 0.4.0 signed artifact and signature verification. This does not certify real Drive-provider transfers or phone performance. The owner must select the private Drive folder and run **Archive all 12 models to Drive** (9,586,646,112 bytes total) in the app. All twelve manifests are pinned and downloadable; physical-device inference and performance certification remain outstanding. Physical-device testing, encrypted memory recovery and customer-grade security remain separate gates. This is an improved **release candidate, not yet fully production-certified**.
