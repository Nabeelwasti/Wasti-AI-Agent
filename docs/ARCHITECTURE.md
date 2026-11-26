# WASTI NEXUS architecture

## Product shape

One repository, one app identity and one reusable brain API. Runtime components may deploy separately, but source, configuration, tests, documentation and SDKs stay in this monorepo.

### Android client

- Kotlin/Jetpack Compose application.
- Local inference through Arm AI Chat (`com.arm:ai-chat:0.1.0`), backed by llama.cpp, for compatible GGUF checkpoints.
- Manual GGUF import through Android's document picker, app-private model storage, load/unload lifecycle and a conservative RAM preflight. A full model catalogue, in-app downloader, checksum verification and uninstall manager remain planned.
- Encrypted local memory and conversation history.
- Default Auto routing: try the online gateway first, then fall back to the installed local GGUF model for network errors, missing online credentials and transient 408/429/5xx responses. Authentication/permission errors remain visible. Local-fallback turns are excluded from subsequent cloud conversation history. Explicit Offline mode never sends inference prompts to the gateway.
- The client must remain usable for local chat without an API key, internet permission grant or Cloudflare availability.

### Universal brain API

One OpenAI-compatible endpoint exposes `GET /v1/models` and `POST /v1/chat/completions`. Apps choose:
- a specific configured remote model ID;
- `nexus-hybrid`, which runs independent analysis, bounded peer review and one synthesis step only when at least two distinct configured model IDs exist;
- a local model through the Android SDK/runtime, without sending the prompt to the server.

The API never reports an unconfigured model as available or fabricates model participation. Native Workers AI model IDs are exposed only when the AI binding is present. It exposes capability, availability, provider and licence metadata separately. Customer-mode API requests require a configured per-key Rate Limiting binding; its counters are approximate and local to each Cloudflare location, not a strict global quota.

### Online hosting

Cloudflare hosts the web console and authenticated API routing. The Worker does not store model weights; it invokes the currently configured Gemma 4 26B A4B model through the `AI` binding and can route to optional configured HTTPS providers. The live online catalogue currently has one configured first-party model, so genuine multi-model Hybrid remains disabled until a second distinct model is configured and tested. Remote inference is metered and subject to each model/provider's terms and availability. Keep provider credentials server-side.

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
  personal/                 # encrypted blobs only; public repo means paths/metadata are visible
  projects/
  tenants/                  # strict tenant-scoped paths; no cross-tenant reads
  knowledge/                # curated, non-sensitive Markdown/JSON
datasets/
  eval/
  training/
  manifests/
```

Rules:
- A private repository is preferred for personal memory. This deployment intentionally keeps the repository public, so only authenticated, client-encrypted ciphertext may be stored under personal paths; filenames and non-sensitive metadata remain visible. Never store secrets, API keys, raw credentials or unencrypted sensitive data in Git.
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

The app imports only the user's selected model. A resumable in-app model downloader and automatic registry SHA-256 verification on import are not implemented yet. Storage availability is necessary but not sufficient: the peak resident memory includes weights, runtime buffers, KV cache, tokenizer and Android process overhead. On a phone with 3 GB free RAM, begin conservatively with a small 0.5B–1B quantized model and low context, then benchmark on the actual device before enabling larger choices.

## Security boundaries

- Separate personal, project and customer memory.
- Keep master API and GitHub credentials on trusted servers.
- Apply TLS, tenant authorization, quotas, request limits, audit trails and provider disclosure notices.
- Treat retrieved documents and peer model outputs as untrusted data, not instructions.
- Android permissions and operating-system restrictions remain authoritative.
- Local-only mode must guarantee no prompt/network egress for inference and memory retrieval.

## Termux integration\n\nAn optional Termux CLI exists for developer testing of the same online gateway. Termux is not part of the production Android inference architecture and is not required to download, load or run local GGUF models. The native inference engine runs in the Android app process.\n\n## Current implementation status

The Android app now builds in GitHub Actions, has JVM tests for automatic offline-fallback policy, supports manual GGUF import and native local chat, and defaults to Auto mode with the live NEXUS gateway preconfigured. The app still needs target-device install and offline generation evidence. The Cloudflare Worker is deployed with one first-party Gemma 4 model; public health/catalogue checks pass, but the authenticated live chat route smoke test is skipped until the GitHub Actions `NEXUS_API_KEY` secret is configured. Hybrid is guarded and disabled until two distinct model IDs are available. Model catalogue downloads, registry SHA-256 verification for imported files, tenant-isolated customer memory and owner-controlled signed production APKs remain incomplete. Do not claim full production readiness until those gates pass.
