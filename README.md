# WASTI NEXUS

**One project. One repository. Many AI brains. One reusable brain API.**

WASTI NEXUS is an offline-first Android assistant and a multi-model orchestration platform for future apps. The Android app can run selected compatible models locally; the same app can route to remote models when online. Other products can connect to one OpenAI-compatible API.

> Status: active release candidate; not yet certified production-ready. CI builds the Android debug APK. A disposable CI-only keystore is used to test release assembly/signature verification; it is not the owner's permanent signing key. The deployed Cloudflare gateway's public health/model catalogue checks pass, and Gemma 4 was tested directly through the Cloudflare account API. An authenticated chat request through the Worker itself has **not** been verified because the GitHub Actions `NEXUS_API_KEY` secret is absent. Physical-device offline inference, permanent release signing, customer tenant isolation and operational readiness remain release gates.

## Fixed architecture

- **One repository:** Android, API, model registry, dataset/memory schemas, SDKs, deployment and tests.
- **Offline:** selected GGUF models run natively on Android through Arm AI Chat backed by llama.cpp. No Cloudflare/API dependency for local chat. The app supports manual GGUF import, metadata validation, multi-model listing/selection, load/unload and removal. Auto mode tries the online gateway first and falls back to a loaded/installed local GGUF model on network, quota and transient server failures; auth/permission errors remain visible. Local-fallback turns are excluded from subsequent cloud history. In-app model-catalogue downloads with resumable progress are not implemented yet; use the guarded acquisition tools and import the verified file. Local and cross-device encrypted snapshot restore are implemented in source, but the live recovery drill remains unverified.
- **Online:** the deployed gateway calls Cloudflare-hosted open-weight models through its native Workers AI binding without a third-party model API key. The current configured model is `@cf/google/gemma-4-26b-a4b-it`. A separate NEXUS bearer key protects the gateway; do not expose a master key in a public app. Cloudflare Workers AI Free includes 10,000 Neurons/day, not unlimited inference; some models may require Paid.
- **Hybrid:** requires at least two distinct configured online model IDs. The Worker and console now reject/hide Hybrid with the current one-model catalogue; no fake multi-agent participation is reported.
- **GitHub:** versioned source, curated knowledge, dataset manifests, small evaluation/training files and client-encrypted memory snapshots in the public repository. The Worker GitHub token is configured and the app can send ciphertext; live memory round-trip and cross-device recovery have not yet been verified. Never store plaintext personal/customer data in this public repository.
- **Cloudflare:** Worker-hosted web console and API gateway. Model weights are not uploaded to Workers AI. If model distribution is later enabled, use a separate R2 bucket or authorised GitHub Release assets; keep customer data and personal memory out of public object storage.
- **Model flexibility:** 12 model families are catalogued as research candidates, not 12 verified, downloadable or online-ready brains. Only the Qwen2.5-0.5B Q4_K_M local candidate is pinned with an expected SHA-256; only Gemma 4 26B A4B is currently configured in the Cloudflare gateway. Other candidates still need immutable revisions, checksums, licence review and provider configuration.

## Device constraints

The currently reported free resources are approximately 58 GB storage and 3 GB RAM. Storage does not determine whether a model can run: model weights, runtime buffers, KV cache and Android overhead all consume memory. The initial on-device profile should be conservative (roughly 0.5B–1B quantized, low context), then benchmarked on the exact chipset and Android build. Larger models remain online until the user frees resources and the device passes fit checks.

## Repository layout

```
android/       Native Android client and local inference integration
orchestrator/  OpenAI-compatible multi-model API
cloudflare/    Worker-hosted web console and API gateway
models/        Model registry, licence evidence and acquisition tools
memory/        GitHub-backed memory schemas and retrieval contracts
datasets/      Versioned evaluation/training data and manifests
sdks/          TypeScript and Python client libraries
docs/          Architecture, API, security and status
tests/         Cross-component tests
```

## Universal brain API

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev` (deployed universal brain API; public health/model catalogue smoke test passes).

- `GET /v1/models`
- `POST /v1/chat/completions`
- `GET /v1/memory/snapshots`
- `POST /v1/memory/snapshots`
- `GET /v1/memory/snapshots/{snapshot_id}`

Use `nexus-gemma-4-26b-a4b` for the currently configured first-party Cloudflare Workers AI model. `nexus-hybrid` is currently disabled/hidden until a second distinct licensed model is configured and tested. The Worker rejects one-model Hybrid requests instead of presenting repeated calls to the same model as multiple agents. Gemma uses Cloudflare's account binding and requires no third-party model-provider key, but Cloudflare usage is metered and the gateway still requires a NEXUS bearer key. Per-application scoped keys are supported in source, but `NEXUS_CLIENT_KEYS_JSON` has not yet been populated. Future public apps must call this API from a trusted backend and must not expose a master or long-lived bearer key in public JavaScript or APK resources. See [docs/API.md](docs/API.md) and the [Termux companion](termux/README.md).

## GitHub memory and training data

GitHub is a versioned file repository, not a live vector database or training engine. Personal/customer records must be encrypted on-device before upload; this repository is public, so only ciphertext and non-sensitive metadata may be committed. Never commit plaintext or encryption keys. Batch snapshots and scope tenant records. Curated knowledge and training/evaluation datasets need provenance, licence, consent and PII review. Embeddings are derived indexes and can be rebuilt. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Models and licensing

The registry contains candidate families Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM. They do not all have permissive commercial licences: Command R and StableLM candidates are non-commercial, while several others require terms review. Do not use or distribute a candidate in customer products until its exact checkpoint, provenance and licence are approved. Only the Qwen local checkpoint currently has a pinned revision and expected file hash. Full weights are not committed to ordinary Git history. See [models/README.md](models/README.md) and [models/LICENSE_REVIEW.md](models/LICENSE_REVIEW.md).

## Development

Python orchestrator: see [orchestrator/README.md](orchestrator/README.md). Android: open `android/` in Android Studio. Run CI checks from the repository root as defined in `.github/workflows/ci.yml`.

## Security

Android OS permissions and model/provider licences remain authoritative. Keep personal, project and customer data isolated. Treat retrieved memory and peer model outputs as untrusted input. Never commit API keys, GitHub tokens, private credentials or raw sensitive memory.
