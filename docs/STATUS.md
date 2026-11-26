# Verified implementation status

Last updated: 2026-11-26.

## Repository and delivery

- Canonical repository: [Nabeelwasti/Wasti-AI-Agent](https://github.com/Nabeelwasti/Wasti-AI-Agent), default branch `main`.
- One monorepo contains the Android app, model registry/acquisition scripts, Python orchestration service, Cloudflare gateway, SDKs, tests and documentation.
- No model weights, provider credentials, GitHub tokens or personal plaintext memory are committed.
- The latest previously verified full CI run passed on commit `d5dfa7fb5d8c88b44545c763033181e56914afab` (run [45819011014](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45819011014)). It built a **debug** APK; it did not produce a signed production release.

## Android client

- Kotlin/Jetpack Compose app; package `ai.wasti.nexus`; min SDK 33, target SDK 36.
- Arm AI Chat `com.arm:ai-chat:0.1.0` supplies the GGUF/llama.cpp inference integration.
- Source supports manual GGUF import into app-private storage, one active local model, load/unload, chat, and removal.
- A Qwen2.5-0.5B Q4_K_M candidate is pinned to an immutable repository revision and SHA-256 in `models/registry.json`.
- No GGUF model has been downloaded to the target phone in this work. No real-device install, offline generation, RAM/thermal/battery benchmark, or Android upgrade test has been recorded.
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
- The current repair branch replaces those retired IDs with `@cf/google/gemma-4-26b-a4b-it`, exposes its Apache-2.0 licence, context and published token pricing metadata, and updates the unit test to verify dispatch to that exact ID.
- The repair branch also adds hashed per-application API keys with `models`/`chat` scopes; the master key alone can access the personal memory endpoints. A Cloudflare Rate Limiting binding is configured at 60 requests per 60 seconds per key per location. Cloudflare describes this as approximate/local, not strict global quotas. Public apps must not embed these long-lived bearer keys; a backend auth/token-exchange layer is still required.
- The updated model has been called successfully through the authenticated Cloudflare account API, returning `NEXUS_LIVE_MODEL_OK`. This verifies the model is available to the connected Cloudflare account, but it does not yet prove the deployed NEXUS Worker is updated or its route returns the answer. The repair branch is not yet deployed or proven by a live authenticated Worker generation request. Cloudflare's documented Gemma 4 price is USD 0.10 per million input tokens and USD 0.30 per million output tokens; free-tier allocation and account eligibility must not be interpreted as unlimited free inference.
- The existing `NEXUS_API_KEY` and `NEXUS_GITHUB_TOKEN` bindings were observed as configured secrets; secret values were not read or exposed.

## Memory

- Android-side snapshots use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a user-controlled recovery passphrase.
- Worker routes accept encrypted envelopes and store ciphertext in the public GitHub repository. The passphrase is not sent to the Worker.
- GitHub-backed memory write/read has unit-test coverage with mocked GitHub responses, but no authenticated live round-trip or cross-device recovery drill is recorded.
- Because the repository is public, paths and metadata are visible. Only client-encrypted ciphertext may be stored there. GitHub is an archive/sync layer, not a transactional multi-tenant database.

## Security and production release gates

The project must not be advertised as a fully production-ready multi-customer service until all of the following have evidence:

1. Deploy the current Worker source and run authenticated real model generation against the live endpoint.
2. Deploy and configure `NEXUS_CLIENT_KEYS_JSON` with unique per-application keys. The branch includes a 256-bit key generator, SHA-256 hash validation, `models`/`chat` scopes and master-only memory access. The approximate per-location rate limiter is not a strict quota; add Durable Objects or an external metering service for strict global limits, plus user authentication and short-lived tokens for public apps.
3. Add tenant isolation and authorization for memory and any future customer data. The current GitHub memory path is a single personal vault, not a customer memory service.
4. Complete privacy/retention disclosures, abuse controls, monitoring/alerting, backup/restore drills and operational incident procedures.
5. Install the APK on the target Android device, test with networking disabled, validate at least one GGUF model's actual generation, and record RAM, thermal, latency and battery results.
6. Resolve all twelve candidate model revisions, expected artifact checksums, licences and redistribution/use restrictions before presenting the full catalogue as verified.
7. Create a release-signed APK using protected signing secrets, verify its signature and install/upgrade behaviour, and publish a versioned release.
8. Run dependency, secret, static-analysis and supply-chain checks and keep all CI jobs green on the exact release commit.

A passing unit test proves only the tested behaviour. A successful APK assembly is not a signed release. A healthy Worker is not proof of successful inference. A source-level local runtime is not proof of offline inference on a physical device.
