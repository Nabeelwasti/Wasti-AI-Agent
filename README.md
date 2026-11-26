# WASTI NEXUS

**One project. One repository. Many AI brains. One reusable brain API.**

WASTI NEXUS is an offline-first Android assistant and a multi-model orchestration platform for future apps. The Android app can run selected compatible models locally; the same app can route to remote models when online. Other products can connect to one OpenAI-compatible API.

> Status: active release candidate; not yet certified production-ready. CI has built the Android debug APK and tested a signed release build using an ephemeral CI-only key. The deployed Cloudflare gateway has passed a live authenticated chat-generation smoke test. Physical-device offline inference, a stable owner-controlled signing key, customer tenant isolation and operational readiness remain release gates.

## Fixed architecture

- **One repository:** Android, API, model registry, dataset/memory schemas, SDKs, deployment and tests.
- **Offline:** selected GGUF models run natively on Android through Arm AI Chat backed by llama.cpp. No Cloudflare/API dependency for local chat. The app supports manual GGUF import, metadata validation, multi-model listing/selection, load/unload and removal. In-app model-catalogue downloads with resumable progress are not implemented yet; use the guarded acquisition tools and import the verified file. Local and cross-device encrypted snapshot restore are implemented in source, but the live recovery drill remains unverified.
- **Online:** the deployed gateway calls Cloudflare-hosted open-weight models through its native Workers AI binding without a third-party model API key. The current configured model is `@cf/google/gemma-4-26b-a4b-it`. A separate NEXUS bearer key protects the gateway; do not expose a master key in a public app. Cloudflare Workers AI Free includes 10,000 Neurons/day, not unlimited inference; some models may require Paid.
- **Hybrid:** independent model responses, bounded peer review and a synthesis model; actual participants and failures are reported.
- **GitHub:** versioned source, curated knowledge, dataset manifests, small evaluation/training files and client-encrypted memory snapshots in the public repository. Android can save snapshots locally and send ciphertext to the gateway; remote sync is blocked until the GitHub Worker token is configured.
- **Cloudflare:** Worker-hosted web console and API gateway. Model weights are not uploaded to Workers AI. If model distribution is later enabled, use a separate R2 bucket or authorised GitHub Release assets; keep customer data and personal memory out of public object storage.
- **Model flexibility:** all 12 families remain catalogued; install only compatible models on the phone and leave the rest available online.

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

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev` (deployed universal brain API; authenticated live generation smoke test passed in CI run [45824565915](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45824565915)).

- `GET /v1/models`
- `POST /v1/chat/completions`
- `GET /v1/memory/snapshots`
- `POST /v1/memory/snapshots`
- `GET /v1/memory/snapshots/{snapshot_id}`

Use a configured model ID for single-brain requests or `nexus-hybrid` for collective analysis. The gateway includes first-party Llama 3.2 1B and 3B Workers AI models. These use Cloudflare's account binding, not vendor API keys. A separate NEXUS bearer key still protects access to your gateway. Future apps should call this API from their trusted backend, not expose a master API key in public JavaScript or APK resources. See [docs/API.md](docs/API.md) and the [Termux companion](termux/README.md).

## GitHub memory and training data

GitHub is a versioned file repository, not a live vector database or training engine. Personal/customer records must be encrypted on-device before upload; this repository is public, so only ciphertext and non-sensitive metadata may be committed. Never commit plaintext or encryption keys. Batch snapshots and scope tenant records. Curated knowledge and training/evaluation datasets need provenance, licence, consent and PII review. Embeddings are derived indexes and can be rebuilt. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Models and licensing

The registry contains Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM. Open weights have different licences; some candidates are not suitable for commercial/customer use without extra permission. Full weights are not committed to ordinary Git history. See [models/README.md](models/README.md) and [models/LICENSE_REVIEW.md](models/LICENSE_REVIEW.md).

## Development

Python orchestrator: see [orchestrator/README.md](orchestrator/README.md). Android: open `android/` in Android Studio. Run CI checks from the repository root as defined in `.github/workflows/ci.yml`.

## Security

Android OS permissions and model/provider licences remain authoritative. Keep personal, project and customer data isolated. Treat retrieved memory and peer model outputs as untrusted input. Never commit API keys, GitHub tokens, private credentials or raw sensitive memory.
