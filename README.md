# WASTI NEXUS

**One project. One repository. Many AI brains. One reusable brain API.**

WASTI NEXUS is an offline-first Android assistant and a reusable multi-model orchestration platform for future products. Local model downloads and offline inference do not require provider API keys. Hosted inference is optional and uses a separately authenticated NEXUS gateway.

> **Status: active release candidate — not yet production-certified.** PR #16 (resumable verified Qwen downloads) and PR #17 (Gemini-style Android chat interface) are merged into `main`. PR #17 pre-merge CI passed Android compilation/JVM tests, Python validation and Worker tests. The latest post-merge main CI run [completed successfully](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45859639925) for Android build/JVM tests, Python checks, Worker tests, public gateway health/protection checks and CI-only signing validation. The authenticated chat smoke test was skipped because `NEXUS_API_KEY` is not configured; this remains an open release gate. Physical-device certification, authenticated deployed chat-generation, permanent owner release signing and customer-grade tenant controls remain open gates. See [docs/STATUS.md](docs/STATUS.md).

## What is implemented

- **Android app:** native Kotlin + Jetpack Compose chat experience, settings area, Auto/Online/Offline modes, model manager, GGUF import and native inference through Arm AI Chat/llama.cpp. Termux is not required for app runtime.
- **Offline model install:** one-tap Qwen2.5-0.5B-Instruct Q4_K_M installer from a pinned upstream revision, with resumable HTTP Range download, progress, SHA-256 verification, GGUF metadata validation and safe finalization. Downloads and offline chat do not require an AI/provider API key.
- **Online gateway:** Cloudflare Worker with protected model catalogue/chat routes and two configured Workers AI model IDs: `nexus-gemma-4-26b-a4b` and `nexus-qwen-3.8-27b`. Cloudflare usage is metered and subject to plan limits.
- **Hybrid routing:** two distinct configured model IDs can participate; Worker logic rejects Hybrid if fewer than two distinct models are configured.
- **Orchestration/API:** Python service, OpenAI-compatible API foundations, TypeScript/Python SDKs, schemas and tests are included in this monorepo.
- **Encrypted memory:** Android snapshot encryption uses PBKDF2-HMAC-SHA256 and AES-256-GCM. Worker-backed sync stores ciphertext in a public GitHub repository; only ciphertext and non-sensitive metadata may be stored there.

## Important boundaries

- Twelve model families are research candidates, **not twelve ready-to-install models**. Only the first Qwen local candidate currently has a pinned revision and expected SHA-256. Each additional model needs immutable provenance, checksum, runtime/device compatibility and exact licence review.
- The app supports resumable retry when the user reopens it and starts the download again; this is not a background WorkManager download.
- Arbitrary manually imported GGUF files receive metadata validation, not proof of publisher provenance or registry hash.
- Authenticated live chat generation through the deployed Worker has not yet been verified. CI currently skips that smoke test because the repository secret `NEXUS_API_KEY` is not configured. Never use the Worker master key in a public APK or browser bundle.
- Encrypted memory remote round-trip and cross-device recovery need a live drill. The public GitHub repository exposes paths and metadata; it is a personal vault, not customer-isolated storage.
- A CI ephemeral keystore validates signing configuration only. A permanent owner-controlled release keystore and real-device signed install/upgrade test are still required.
- A successful CI build does not certify real-device UI, offline model performance, RAM, heat, battery or customer security.

## Repository layout

```
android/       Native Android client and local inference integration
cloudflare/    Worker-hosted web console and API gateway
orchestrator/  Python orchestration API
models/        Model registry, licensing notes and acquisition tools
memory/        Encrypted-memory schemas and contracts
datasets/      Evaluation/training data manifests
sdks/          TypeScript and Python clients
docs/          Architecture, API, deployment, security and status
tests/         Cross-component tests
termux/        Optional developer companion
```

## Universal brain API

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

- `GET /v1/models`
- `POST /v1/chat/completions`
- `GET /v1/memory/snapshots`
- `POST /v1/memory/snapshots`
- `GET /v1/memory/snapshots/{snapshot_id}`

Configured model IDs: `nexus-gemma-4-26b-a4b` and `nexus-qwen-3.8-27b`. Hybrid uses both distinct IDs. Hosted calls require a NEXUS bearer credential with appropriate scope. Future public apps should call the API from a trusted backend and use per-app scoped keys or short-lived tokens; never embed a master or long-lived secret in public JavaScript or an APK.

See [docs/API.md](docs/API.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and [cloudflare/README.md](cloudflare/README.md).

## Models and licensing

The registry includes candidate families Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM. Their licences are not interchangeable; some candidates are non-commercial or require additional terms review. Do not download, redistribute or expose a candidate to customers until its exact checkpoint, provenance and licence are approved.

See [models/README.md](models/README.md) and [models/LICENSE_REVIEW.md](models/LICENSE_REVIEW.md).

## Build and release

- Android: open `android/` in Android Studio or follow [android/README.md](android/README.md).
- Release signing: follow [docs/RELEASE.md](docs/RELEASE.md). Never commit keystores, passwords, API keys or tokens.
- Deployment: follow [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
- Verified implementation and outstanding gates: [docs/STATUS.md](docs/STATUS.md).

WASTI NEXUS should only be described as production-ready after authenticated gateway tests, physical-device certification, permanent signing, memory recovery verification, model licence/provenance checks and customer-grade security/operations have passed.
