# WASTI NEXUS

**One project. One repository. Many AI brains. One reusable brain API.**

WASTI NEXUS is an offline-first Android assistant and a multi-model orchestration platform for future apps. The Android app can run selected compatible models locally; the same app can route to remote models when online. Other products can connect to one OpenAI-compatible API.

> Status: active foundation, not production-ready. The Android prototype currently sends remote requests only. Local inference and model management are still being integrated. The Cloudflare gateway has no inference providers configured.

## Fixed architecture

- **One repository:** Android, API, model registry, dataset/memory schemas, SDKs, deployment and tests.
- **Offline:** selected GGUF models run natively on Android using the llama.cpp Android runtime. No Cloudflare/API dependency for local chat.
- **Online:** all configured and legally usable remote brains can be called through one universal endpoint.
- **Hybrid:** independent model responses, bounded peer review and a synthesis model; actual participants and failures are reported.
- **GitHub:** versioned source, curated knowledge, dataset manifests, small evaluation/training files and encrypted private memory snapshots.
- **Cloudflare:** web app static assets and API gateway only. No Cloudflare storage for memory, datasets or weights.
- **Model flexibility:** all 12 families remain catalogued; install only compatible models on the phone and leave the rest available online.

## Device constraints

The currently reported free resources are approximately 58 GB storage and 3 GB RAM. Storage does not determine whether a model can run: model weights, runtime buffers, KV cache and Android overhead all consume memory. The initial on-device profile should be conservative (roughly 0.5B–1B quantized, low context), then benchmarked on the exact chipset and Android build. Larger models remain online until the user frees resources and the device passes fit checks.

## Repository layout

```
android/       Native Android client and local inference integration
orchestrator/  OpenAI-compatible multi-model API
cloudflare/    Web asset hosting and API gateway
models/        Model registry, licence evidence and acquisition tools
memory/        GitHub-backed memory schemas and retrieval contracts
datasets/      Versioned evaluation/training data and manifests
sdks/          TypeScript and Python client libraries
docs/          Architecture, API, security and status
tests/         Cross-component tests
```

## Universal brain API

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev` (deployment shell; no model providers configured yet).

- `GET /v1/models`
- `POST /v1/chat/completions`

Use a configured model ID for single-brain requests or `nexus-hybrid` for collective analysis. Future apps should call this API from their trusted backend, not expose a master API key in public JavaScript or APK resources. See [docs/API.md](docs/API.md).

## GitHub memory and training data

GitHub is a versioned file repository, not a live vector database or training engine. Private personal/customer records must be encrypted before upload, stored in a private repository, scoped by tenant and committed in batches. Curated knowledge and training/evaluation datasets need provenance, licence, consent and PII review. Embeddings are derived indexes and can be rebuilt. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Models and licensing

The registry contains Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM. Open weights have different licences; some candidates are not suitable for commercial/customer use without extra permission. Full weights are not committed to ordinary Git history. See [models/README.md](models/README.md) and [models/LICENSE_REVIEW.md](models/LICENSE_REVIEW.md).

## Development

Python orchestrator: see [orchestrator/README.md](orchestrator/README.md). Android: open `android/` in Android Studio. Run CI checks from the repository root as defined in `.github/workflows/ci.yml`.

## Security

Android OS permissions and model/provider licences remain authoritative. Keep personal, project and customer data isolated. Treat retrieved memory and peer model outputs as untrusted input. Never commit API keys, GitHub tokens, private credentials or raw sensitive memory.
