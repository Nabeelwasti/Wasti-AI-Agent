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

The NEXUS app currently has source integration for GGUF import, model load/unload and local chat. The dependency and app have not yet passed CI or a real-device inference test. Do not claim offline support verified until those checks pass.

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

Use Git as the versioned source for curated Markdown/JSON knowledge, dataset manifests, evaluation cases and encrypted snapshot files. Keep local encrypted SQLite as the fast offline working store. Build embeddings/indexes locally and rebuild them from source files; do not commit large, frequently rewritten vector indexes.

GitHub API writes require a trusted backend credential, private repository access, commit batching and conflict-safe updates. Never put a GitHub write token in the Android app or browser bundle. Do not commit raw personal/customer memory to a public repository. Encrypt sensitive memory before upload and scope each file by tenant. Keep GitHub history retention/deletion implications explicit.

## Cloudflare role

Use Workers Static Assets for the online web app and the Worker for API gateway/routing only. The Worker is not a GPU host and NEXUS will not use Cloudflare storage products for memory, datasets or model weights. Static asset hosting reference: https://developers.cloudflare.com/workers/static-assets/

## GitHub repository constraints

Keep the monorepo focused on code and small text/structured datasets. GitHub recommends keeping repositories manageable; regular Git objects have a 100 MB hard file limit, and Git LFS has plan-based per-file/storage/bandwidth limits. Do not use Git history as a 12-model checkpoint archive. Keep full weights in the user's private local archive or another explicitly selected storage location outside Cloudflare.

References:
- https://docs.github.com/en/repositories/creating-and-managing-repositories/repository-limits
- https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-git-large-file-storage
- https://docs.github.com/en/billing/concepts/product-billing/git-lfs
