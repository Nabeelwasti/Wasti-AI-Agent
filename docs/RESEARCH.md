# WASTI NEXUS engineering research

Research date: 2026-11-26. Public repositories are reference material, not dependencies copied wholesale. Review each upstream licence and pin a revision before importing code.

## Android local inference

### Preferred first runtime: Arm AI Chat library backed by llama.cpp

- Upstream engine: https://github.com/ggml-org/llama.cpp
- Android binding: https://github.com/arm/ai-chat
- Maven Central artifact: https://central.sonatype.com/artifact/com.arm/ai-chat
- Official Android guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- Official Android sample: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android
- Selected dependency: `com.arm:ai-chat:0.1.0` (Apache-2.0).
- API: `AiChat.getInferenceEngine(context)`, `InferenceEngine.loadModel(path)`, token `Flow` from `sendUserPrompt`, `cleanUp()` and `destroy()`.
- The library's documented baseline is minSdk 33, compileSdk 36 and Java 17; the Android project toolchain has been aligned to that baseline.
- Why: GGUF support, native llama.cpp integration, Kotlin Flow token generation, and a maintained Android library rather than a hand-written JNI bridge.

The NEXUS app source now includes GGUF metadata validation, multi-model import/selection/removal, native load/unload, local chat and device-encrypted memory snapshots. The dependency and app have not yet passed a verified CI build or real-device inference test. Do not claim offline support verified until those checks pass.

### Additional implementation references

- https://github.com/ExperimentalMachines/openweights — native Kotlin/Compose app with llama.cpp and ExecuTorch; useful ideas for model discovery, fit estimates, model format routing, tool isolation and model benchmarks. Its own documentation warns that device-specific speed and quality results must not be generalized.
- https://github.com/stevelaskaridis/awesome-mobile-llm — curated survey of mobile model families, runtimes and benchmarks.
- https://github.com/eclairsameal/android-on-device-llm-poc — comparison of llama.cpp, LiteRT-LM, MNN and other Android paths; useful for reproducible device benchmarking.
- https://github.com/GeoSId/OnDeviceAi — comparison-oriented Android sample for LiteRT and llama.cpp.
- https://github.com/ShounakPatra/Solus — multi-runtime Android reference; use as a source of design ideas, not as proof that every runtime/model works on this handset.

### Runtime decision

Start with Arm AI Chat + llama.cpp + GGUF because it gives one broadly compatible route for the first selected models. Keep a runtime adapter interface so LiteRT-LM or ExecuTorch can be added later for models with better compiled/mobile support. Do not add several native runtimes to the first APK until one end-to-end offline model has passed device tests.

Official llama.cpp Android docs show both an Android Studio binding and an NDK route. The binding is preferable for a native app; CLI-in-Termux is useful for experimentation but should not become the production app architecture.

## Model fit and memory

The first registered phone candidate is `Qwen/Qwen2.5-0.5B-Instruct-GGUF`, using the Q4_K_M file (491 MB). The official file page identifies Apache-2.0, exact immutable revision `12145bd1d629190a4d44254073650877954d02c9`, and SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`. The downloader verifies this checksum. Upstream file listing: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/tree/12145bd1d629190a4d44254073650877954d02c9.

- Storage free space is not the same as inference RAM.
- Budget for weights + KV cache + compute buffers + tokenizer/runtime + Android OS and other apps.
- With about 3 GB free RAM, initially test a small 0.5B–1B quantized model, 1K–2K context and one active model. These are conservative trial values, not a guarantee.
- Read the actual SoC, ABI, Android version and memory class at runtime. Benchmark load success, peak process memory, tokens/sec, first-token latency, thermal throttling and crash/recovery.
- Only mark a model "recommended" after the exact model file and device profile pass a repeatable test.

## Memory and retrieval references

- https://github.com/mem0ai/mem0 — agent memory layer and memory lifecycle patterns. NEXUS can borrow the separation between memory extraction, retrieval and evaluation; do not assume GitHub itself replaces a memory service.
- https://github.com/letta-ai/letta — stateful agent architecture and long-term memory patterns. Current active implementation has moved to https://github.com/letta-ai/letta-code.
- https://github.com/khoj-ai/khoj — second-brain retrieval across Markdown, PDFs and other documents, with local and online model support.

## NEXUS-specific memory design

Use the public Git repository for curated Markdown/JSON knowledge, dataset manifests, evaluation cases and client-encrypted memory snapshots. The Android client currently stores encrypted snapshot files in app-private storage; encrypted SQLite, retrieval indexing, embedding rebuilds and retention management are future work. Do not commit large, frequently rewritten vector indexes.

The Android client derives a key from the user's passphrase with PBKDF2-HMAC-SHA256 (600,000 iterations and a random 16-byte salt) and encrypts with AES-256-GCM. The passphrase is never sent to the Worker. The deployed Worker writes encrypted snapshots directly through the GitHub Contents API using a server-side fine-grained token. The repository must remain public in this deployment mode. GitHub exposes ciphertext, file paths, timestamps, tags, commit history and other metadata to everyone; never store plaintext personal/customer memory or encryption keys. Batch snapshots, use immutable IDs, and keep the GitHub token out of Android/browser code. The token is not configured yet, so remote memory sync is currently unavailable.

## Cloudflare role

The Worker serves the online console from its embedded HTML response and provides API gateway/routing only. The Worker is not a GPU host. NEXUS does not use Cloudflare KV, D1, R2, Durable Objects, Vectorize or Workers Static Assets for memory, datasets or model weights. The canonical console HTML remains in `cloudflare/public/index.html`.

## GitHub repository constraints

Keep the monorepo focused on code and small text/structured datasets. GitHub recommends keeping repositories manageable; regular Git objects have a 100 MB hard file limit, and Git LFS has plan-based per-file/storage/bandwidth limits. Do not use Git history as a 12-model checkpoint archive. Keep full weights in the user's private local archive or another explicitly selected storage location outside Cloudflare.

References:
- https://docs.github.com/en/repositories/creating-and-managing-repositories/repository-limits
- https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-git-large-file-storage
- https://docs.github.com/en/billing/concepts/product-billing/git-lfs

## Online inference providers and free-tier constraints

The gateway accepts OpenAI-compatible providers, but no provider API key or provider catalogue is configured yet. A sample OpenRouter free-router config is in `cloudflare/providers.example.json`.

- OpenRouter documents a free-account allowance of 50 free-model requests/day and 20 requests/minute. Its `openrouter/free` router chooses from the currently available free pool; it does not guarantee a specific one of NEXUS's twelve families. Availability and context windows change. See https://openrouter.ai/docs and https://openrouter.ai/blog/tutorials/how-to-get-the-lowest-cost-llm-inference-on-openrouter/.
- Free hosted inference is not equivalent to unlimited self-hosted inference. Some providers require keys, impose quotas, or do not host a requested family.
- The gateway hides models in customer mode unless `commercial_allowed: true` is explicitly set after exact model/provider terms are reviewed. The sample free router is marked personal-only.
- A single provider key can make one or more models available, but no hosted free provider has been verified to serve all twelve selected families.

## GitHub Actions cost strategy

GitHub documents standard GitHub-hosted runner usage as free for public repositories. Private repositories instead consume plan-based included compute usage. NEXUS remains public as requested. Its CI workflow skips docs-only/memory-only/dataset-only changes, cancels superseded runs on the same ref, and uploads an APK artifact only for manual/tagged builds. GitHub Free artifact storage is limited, so APK artifacts have one-day retention.

Reference: https://docs.github.com/en/actions/concepts/billing-and-usage and https://docs.github.com/en/billing/concepts/product-billing/github-actions.
