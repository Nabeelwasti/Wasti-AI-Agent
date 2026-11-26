# Verified implementation status

Last updated: 2026-11-26.

## Repository and CI

- Canonical repository: [Nabeelwasti/Wasti-AI-Agent](https://github.com/Nabeelwasti/Wasti-AI-Agent), default branch `main`.
- One monorepo contains the Android client, model registry, Python orchestration service, Cloudflare gateway, SDKs, tests and documentation.
- GitHub Actions now uses Node 24-compatible action releases. Dependabot is enabled for GitHub Actions, Gradle, Python and npm dependencies.
- Latest full CI run: [45827982406](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45827982406), commit `f648a5112c696f8b3da0a37dc155c517a993dded`. Python/source tests, Worker tests, Android debug assembly and Android JVM fallback-policy tests have passed. The deployed public health check confirms both configured model IDs, and an unauthenticated `/v1/models` request correctly returns HTTP 401. The ephemeral release-signature validation and debug APK artifact upload are still running. The authenticated model-generation smoke is skipped because the GitHub Actions `NEXUS_API_KEY` secret is absent.
- Earlier run [45827619035](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45827619035) passed Python/source checks, Cloudflare Worker tests and the deployed gateway smoke test; its Android job was still running when the newer run was queued. Do not treat it as a complete end-to-end pass.
- The most recent downloadable debug APK before these changes is from run [45824266029](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45824266029). It is a debug build, not an owner-signed production APK.
- No model weights, provider credentials, GitHub tokens or personal plaintext memory are committed.

## Android client

- Kotlin/Jetpack Compose app; package `ai.wasti.nexus`; min SDK 33, target SDK 36.
- Arm AI Chat `com.arm:ai-chat:0.1.0` provides the native GGUF/llama.cpp inference integration inside the Android app process. Termux is not required for runtime or local inference.
- The app defaults to Auto mode and has the deployed NEXUS gateway URL preconfigured. It attempts online inference first, then falls back to an installed local model on network errors, missing online credentials and transient HTTP 408/429/5xx failures. Authentication/permission errors remain visible.
- Local-fallback turns are excluded from subsequent cloud conversation history. Explicit Offline mode does not send inference prompts to the gateway.
- Manual GGUF import, metadata validation, model listing/selection, load/unload and removal are implemented. A resumable in-app model catalogue downloader and registry SHA-256 verification for manually imported files are not implemented.
- JVM tests cover network/quota fallback, auth/permission non-fallback and missing online credentials. The latest full Android build/test run is pending at this status update.
- No GGUF model has been loaded on the target phone in this work. Real-device install, offline generation, RAM/thermal/battery measurements and upgrade testing remain unverified. Local inference is implemented in source but not device-certified.

## Model catalogue and licensing

- Twelve requested families are catalogued as research candidates: Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM.
- This is not twelve verified downloadable models. Only the Qwen2.5-0.5B-Instruct Q4_K_M local candidate has a pinned upstream revision and expected SHA-256. Other local candidates need exact checkpoint, immutable revision, checksum, runtime/device fit and licence evidence.
- Candidate licences are not all permissive for commercial use. Command R and StableLM candidates are non-commercial; all exact checkpoint terms must be reviewed before customer use.
- No GGUF weights are stored in Git history or uploaded to Workers AI. Model distribution may later use authorised GitHub Release assets under the per-file size limit or a dedicated R2 bucket. No NEXUS R2 bucket is provisioned.
- The Android browser can download the Qwen candidate from its publisher or ModelScope page; the ModelScope page is an alternative source, not yet checksum-verified by the registry. The app's import flow currently validates GGUF metadata but does not compare the imported file against the registry hash.

## Cloudflare online gateway

- Worker: `wasti-nexus-gateway`; base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.
- Canonical source: `cloudflare/src/index.js`; config: `cloudflare/wrangler.toml`.
- Latest deployment: `fae22882-e831-4abb-b6d1-68bd30e60487`, version `833dd541-ba07-4b70-8784-71378f4d0ccf`, deployed 2026-11-26 at 13:19:49Z. The deployed Worker source matches `cloudflare/src/index.js` on main byte-for-byte. The `AI` and `NEXUS_RATE_LIMIT` bindings and existing master/GitHub secrets were preserved and verified.
- Worker settings were checked after deployment: the `AI` binding, `NEXUS_RATE_LIMIT`, `NEXUS_API_KEY`, `NEXUS_GITHUB_TOKEN`, and expected plain-text variables remain configured. Secret values were not read or exposed.
- Two first-party models are configured through Cloudflare Workers AI, without third-party provider API keys:
  - `nexus-gemma-4-26b-a4b` → `@cf/google/gemma-4-26b-a4b-it`, Apache-2.0.
  - `nexus-qwen-3.8-27b` → `@cf/qwen/qwen3.8-27b`, Apache-2.0.
- Both model IDs generated responses in direct Cloudflare account-level inference tests (`NEXUS_LIVE_MODEL_OK` and `NEXUS_QWEN_LIVE_OK`). Qwen 3.8's short direct test consumed 16.62 Neurons. Cloudflare Workers AI Free currently provides 10,000 Neurons/day; usage is metered and model-dependent, not unlimited.
- The Worker catalogue, single-model dispatch, two-model Hybrid dispatch and one-model Hybrid rejection have unit-test coverage. The latest Worker tests passed in run [45827982406](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45827982406). Its deployed gateway smoke check confirms the live catalogue advertises exactly Gemma and Qwen, and the protected model-list endpoint rejects unauthenticated requests with HTTP 401.
- Hybrid mode now uses two distinct model IDs and is available in the Android app and web console after refreshing the model catalogue. The Worker rejects Hybrid if fewer than two distinct models are configured.
- Public `/healthz`, exact two-model catalogue and unauthenticated-protection checks pass. The authenticated live chat-generation smoke test is **not yet verified**: GitHub Actions skips it because the repository secret `NEXUS_API_KEY` is not configured. The Cloudflare account-level model tests do not prove the deployed authenticated Worker route.
- A per-location rate limit of 60 requests per 60 seconds per key is configured. It is approximate and not a strict global quota or billing meter.
- `NEXUS_CLIENT_KEYS_JSON` is not populated with app-specific client keys. The source supports hashed 256-bit client keys with `models`/`chat` scopes and master-only memory access. Never expose the master key in public APKs or browser bundles.

## Memory and customer data

- Android-side memory snapshots use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a user-controlled recovery passphrase.
- The Worker accepts encrypted envelopes and stores ciphertext in the public GitHub repository. The passphrase is not sent to the Worker.
- The Worker settings contain a `NEXUS_GITHUB_TOKEN` secret binding and the expected memory repository variables. Worker unit tests mock GitHub; no live memory write/list/read or cross-device recovery drill has been recorded.
- The GitHub repository is public. Paths and metadata are visible; only client-encrypted ciphertext and non-sensitive metadata may be stored there.
- This is one personal vault, not a tenant-isolated customer memory service. Do not store customer memory there.

## Production release gates

WASTI NEXUS must **not** be certified as a fully production-ready multi-customer product until these items have evidence:

1. Add the existing Cloudflare Worker master key as the GitHub Actions repository secret `NEXUS_API_KEY` (Settings → Secrets and variables → Actions). Then verify that the authenticated live chat smoke test actually runs and returns non-empty responses from both configured model IDs. A skipped test is not a pass.
2. Configure unique server-side client keys in `NEXUS_CLIENT_KEYS_JSON`; add end-user authentication, short-lived token exchange, tenant isolation and strict global quotas before customer deployments.
3. Complete the in-app resumable model downloader, signed/immutable model manifest and SHA-256 verification for downloaded/imported files, then test cancellation/retry/storage handling.
4. Download and load at least one compatible GGUF on the target phone. Test airplane-mode generation, Auto fallback, model unload, app restart/upgrade, memory usage, thermal behaviour, latency and battery.
5. Create and securely back up a permanent owner-controlled Android release keystore, configure the four protected secrets in [RELEASE.md](RELEASE.md), build the signed APK and verify installation/upgrade on the target device. CI's disposable keystore only validates the build/signing pipeline.
6. Complete a live encrypted-memory write/read/restore drill and document backup, retention and deletion behaviour.
7. Review exact licences and provenance for each of the twelve candidate model families before enabling customer use; do not equate an open-weight checkpoint with unrestricted commercial rights.
8. Add production monitoring, incident response, abuse controls, privacy/retention disclosures and operational ownership.

The current implementation is a strong, tested release candidate with a deployed two-model online gateway and native offline inference source. It is **not yet fully production-certified** because the authenticated route test, device certification, permanent signing key, model download/integrity flow and customer data controls remain open.
