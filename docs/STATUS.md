# Verified implementation status

Last updated: 2026-11-26.

## Repository and delivery

- Canonical repository: [Nabeelwasti/Wasti-AI-Agent](https://github.com/Nabeelwasti/Wasti-AI-Agent), default branch `main`.
- One monorepo contains the Android app, model registry/acquisition scripts, Python orchestration service, Cloudflare gateway, SDKs, tests and documentation.
- No model weights, provider credentials, GitHub tokens or personal plaintext memory are committed.
- Latest debug APK artifact: `wasti-nexus-debug-apk`, 20,378,967 bytes, SHA-256 `ee3e326f0d02ef05269dfd14e896f24bdc994e481617b157c851b41c7a14aa64`, from run [45824266029](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45824266029). It expires on 2026-12-03. This is a CI debug artifact, not a production-signed APK.
- The complete PR CI passed on commit `5f1327491fc2679377eef61de46df390dec577ad` (run [45823646137](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45823646137)). Post-merge main CI [45823876505](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45823876505) passed Python/Worker checks, Android `assembleDebug`, APK artifact upload and the deployed gateway health/model-catalogue smoke test. The latest main CI [45824266029](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45824266029) also passed Python/Worker checks, health smoke and Android `assembleDebug`; the authenticated inference smoke was skipped because the GitHub Actions `NEXUS_API_KEY` secret is not configured. These are **debug** builds, not signed production releases.

## Android client

- Kotlin/Jetpack Compose app; package `ai.wasti.nexus`; min SDK 33, target SDK 36.
- Arm AI Chat `com.arm:ai-chat:0.1.0` supplies the GGUF/llama.cpp inference integration.
- Source supports manual GGUF import into app-private storage, one active local model, load/unload, chat, and removal. Auto mode attempts online inference first and falls back to local inference for network failures, missing online credentials, HTTP 408/429/5xx transient failures, provided a compatible local model is installed. Authentication/permission errors do not silently fall back. Turns answered locally are excluded from subsequent cloud conversation history.
- A Qwen2.5-0.5B Q4_K_M candidate is pinned to an immutable repository revision and SHA-256 in `models/registry.json`.
- No GGUF model has been downloaded to the target phone in this work. No real-device install, offline generation, RAM/thermal/battery benchmark, or Android upgrade test has been recorded. The app still has no built-in resumable model catalogue downloader; the current acquisition path is a browser download or the optional guarded developer script followed by import.
- Therefore local inference is implemented in source but **not yet device-certified**.

## Model registry

- Twelve requested model families are represented in the registry.
- The registry deliberately distinguishes model families, candidate checkpoints, licence status, download status and device fit. It does not imply all twelve checkpoints are free for commercial use or suitable for the user's phone.
- Qwen 0.5B is the first conservative local candidate. Other models require immutable revision, exact artifact hash, runtime compatibility, licence review and device-fit verification before being labelled production-ready.
- Model weights are not stored in GitHub. Selected downloads are SHA-256 checked where a trusted expected hash is registered.

## Cloudflare gateway

- Worker name: `wasti-nexus-gateway`; public endpoint: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.
- Worker source of record: `cloudflare/src/index.js`; Wrangler config: `cloudflare/wrangler.toml`.
- The Worker has an `AI` binding and a protected OpenAI-compatible API.
- **Critical upstream lifecycle finding:** Cloudflare deprecated both previous built-in model IDs, `@cf/meta/llama-3.2-1b-instruct` and `@cf/meta/llama-3.2-3b-instruct`, effective 2026-11-20. The prior successful CI only checked the binding/catalogue count and therefore did not detect that the upstream models no longer generated responses.
- The merged repair replaces those retired IDs with `@cf/google/gemma-4-26b-a4b-it`, exposes its Apache-2.0 licence, context and published token pricing metadata, and tests dispatch to that exact ID.
- The repair branch also adds hashed per-application API keys with `models`/`chat` scopes; the master key alone can access the personal memory endpoints. A Cloudflare Rate Limiting binding is configured at 60 requests per 60 seconds per key per location. Cloudflare describes this as approximate/local, not strict global quotas. Public apps must not embed these long-lived bearer keys; a backend auth/token-exchange layer is still required.
- The updated model was called successfully through the authenticated Cloudflare account API, returning `NEXUS_LIVE_MODEL_OK`. The current Worker source was deployed on 2026-11-26 at 12:53:33Z as version `85373829-cbf2-4b13-89e1-b9407ba624cc`; the `AI` binding, existing `NEXUS_API_KEY`/`NEXUS_GITHUB_TOKEN` secrets, plain-text variables and `NEXUS_RATE_LIMIT` binding were verified after upload. The deployed Worker source was compared with `cloudflare/src/index.js` and matched byte-for-byte. GitHub Actions confirmed the public health endpoint advertises `nexus-gemma-4-26b-a4b`. The authenticated Worker chat-completion smoke test is currently skipped because the GitHub repository secret `NEXUS_API_KEY` is absent; the account-level model test does not substitute for that route test. Cloudflare's documented Gemma 4 price is USD 0.10 per million input tokens and USD 0.30 per million output tokens; free-tier allocation and account eligibility must not be interpreted as unlimited free inference.
- The existing `NEXUS_API_KEY` and `NEXUS_GITHUB_TOKEN` bindings were observed as configured secrets; secret values were not read or exposed.

## Memory

- Android-side snapshots use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a user-controlled recovery passphrase.
- Worker routes accept encrypted envelopes and store ciphertext in the public GitHub repository. The passphrase is not sent to the Worker.
- GitHub-backed memory write/read has unit-test coverage with mocked GitHub responses, but no authenticated live round-trip or cross-device recovery drill is recorded.
- Because the repository is public, paths and metadata are visible. Only client-encrypted ciphertext may be stored there. GitHub is an archive/sync layer, not a transactional multi-tenant database.

## Security and production release gates

The project must not be advertised as a fully production-ready multi-customer service until all of the following have evidence:

1. Keep the authenticated real chat-completion smoke test enabled on the exact release commit; run 45824565915 has passed this route test.
2. Configure `NEXUS_CLIENT_KEYS_JSON` with unique per-application keys. The merged source includes a 256-bit key generator, SHA-256 hash validation, `models`/`chat` scopes and master-only memory access; the secret is not yet populated with app-specific client keys. The approximate per-location rate limiter is not a strict quota; add Durable Objects or an external metering service for strict global limits, plus user authentication and short-lived tokens for public apps.
3. Add tenant isolation and authorization for memory and any future customer data. The current GitHub memory path is a single personal vault, not a customer memory service.
4. Complete privacy/retention disclosures, abuse controls, monitoring/alerting, backup/restore drills and operational incident procedures.
5. Install the APK on the target Android device, test with networking disabled, validate at least one GGUF model's actual generation, and record RAM, thermal, latency and battery results.
6. Resolve all twelve candidate model revisions, expected artifact checksums, licences and redistribution/use restrictions before presenting the full catalogue as verified.
7. Configure the four protected release-signing secrets and run the new `.github/workflows/release.yml` workflow. CI now creates a disposable test keystore to validate release assembly/signature verification; this does not replace the owner's permanent release keystore. No production keystore was provided or tested; verify the signed APK signature and install/upgrade behaviour before publishing a versioned release.
8. Run dependency, secret, static-analysis and supply-chain checks and keep all CI jobs green on the exact release commit.

A passing unit test proves only the tested behaviour. A successful APK assembly is not a signed release. A healthy Worker is not proof of successful inference. A source-level local runtime is not proof of offline inference on a physical device.
