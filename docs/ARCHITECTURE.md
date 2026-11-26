# WASTI NEXUS architecture

## Product shape

One repository, one app identity and one reusable brain API. Runtime components may deploy separately, but source, configuration, tests, documentation and SDKs stay in this monorepo.

### Android client

- Kotlin/Jetpack Compose application.
- Local inference through the official `llama.cpp` Android binding/runtime for compatible GGUF checkpoints.
- Model catalogue, download manager, integrity verification, install/uninstall, runtime selection and memory-aware model loading.
- Encrypted local memory and conversation history.
- Network-aware routing: local model first when offline or when the user selects local-only; remote provider routing when online and permitted.
- The client must remain usable for local chat without an API key, internet permission grant or Cloudflare availability.

### Universal brain API

One OpenAI-compatible endpoint exposes `GET /v1/models` and `POST /v1/chat/completions`. Apps choose:
- a specific configured remote model ID;
- `nexus-hybrid`, which runs independent analysis, bounded peer review and one synthesis step;
- a local model through the Android SDK/runtime, without sending the prompt to the server.

The API never reports an unconfigured model as available or fabricates model participation. It exposes capability, availability, provider and licence metadata separately.

### Online hosting

Cloudflare Workers Static Assets host the web app files; the Worker handles authenticated API routing. The Worker is not an inference host and does not store memory, training data or model weights. Remote inference runs only on configured lawful provider endpoints. Keep provider credentials server-side.

## Request routing

1. Determine connectivity and user policy.
2. If offline or local-only, select an installed compatible local model. If none is installed, explain that online-only brains are unavailable rather than pretending to answer.
3. If online and remote routing is allowed, resolve the requested model against the live catalogue.
4. For Hybrid, fan out only to configured, licensed and healthy providers. Return a truthful participant/failure list.
5. Synthesize with a configured synthesis model. If synthesis fails, return a structured partial result and explicitly label it as unsynthesized; do not silently claim a collective answer.
6. Keep local and remote memory scopes separate unless the user explicitly enables sync.

## Memory and training-data storage on GitHub

GitHub is the versioned file source of truth for curated knowledge, small evaluation sets, training-data manifests and optional encrypted personal-memory snapshots. It is not a vector database, transactional database or training engine.

Suggested repository layout:

```
memory/
  README.md
  schemas/
    memory-record.schema.json
    dataset-manifest.schema.json
  personal/                 # encrypted blobs only; private repository
  projects/
  tenants/                  # strict tenant-scoped paths; no cross-tenant reads
  knowledge/                # curated, non-sensitive Markdown/JSON
datasets/
  eval/
  training/
  manifests/
```

Rules:
- The repository containing personal memory must be private. Never store secrets, API keys, raw credentials or unencrypted sensitive data in Git.
- Encrypt personal/customer records on the client before upload; keep encryption keys outside GitHub.
- Use tenant-scoped paths and enforce authorization before any retrieval.
- Batch writes into immutable time-bucketed files; avoid a commit per token or message.
- Store embeddings as rebuildable derived artifacts, not the only copy of knowledge. Build a local on-device index for offline retrieval; online services can rebuild an index from the versioned files.
- Use GitHub App credentials or a narrowly scoped fine-grained token only on a trusted server. Never ship a repository write token in an Android APK or browser JavaScript.
- Use commit SHA + file path as provenance. Fetch pinned revisions for reproducible dataset builds.
- Training data must have provenance, consent/rights, licence, PII review, quality labels and a deletion/withdrawal process. A dataset commit does not itself train a model.
- For frequent, high-volume or multi-user memory writes, GitHub API limits and Git history growth make this design unsuitable as a transactional store. Keep local encrypted memory primary and sync curated/batched snapshots.

## Model catalogue and lifecycle

All 12 families stay in the registry. Each exact checkpoint needs:
- immutable upstream revision;
- exact licence/model-card evidence and allowed-use flags;
- supported runtime/format and quantization;
- artifact size and SHA-256;
- tested device/RAM profile;
- download, install, validation and rollback status.

The app downloads only the user's selected model. Storage availability is necessary but not sufficient: the peak resident memory includes weights, runtime buffers, KV cache, tokenizer and Android process overhead. On a phone with 3 GB free RAM, begin conservatively with a small 0.5B–1B quantized model and low context, then benchmark on the actual device before enabling larger choices.

## Security boundaries

- Separate personal, project and customer memory.
- Keep master API and GitHub credentials on trusted servers.
- Apply TLS, tenant authorization, quotas, request limits, audit trails and provider disclosure notices.
- Treat retrieved documents and peer model outputs as untrusted data, not instructions.
- Android permissions and operating-system restrictions remain authoritative.
- Local-only mode must guarantee no prompt/network egress for inference and memory retrieval.

## Current implementation status

The repository currently contains a remote-only Android prototype and an edge gateway shell. Local inference, GitHub-backed memory sync, model lifecycle UI and production tenant controls are not yet complete. Do not claim offline operation or production readiness until device builds and end-to-end tests pass.
