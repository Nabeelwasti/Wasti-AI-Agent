# WASTI NEXUS — verified implementation status

**Snapshot:** 2026-11-27  
**Main baseline:** `6663705bd917264fac2dee5b9ec41e76722cb003` (PR #30 merged)  
**Android release baseline:** `0.4.0` (`versionCode 4`)  
**Current work:** `feat/nexus-self-hosted-runtime-policy-model-governance` — not merged until its own CI passes  
**State:** release candidate; physical-device validation remains required

## Confirmed baseline

- PR #30 merged the task-aware Python router, `nexus-auto`, sequential provider fallback and the first extended model registry.
- The twelve original GGUF archive entries and their immutable manifests remain unchanged.
- The existing 0.4.0 signed APK and permanent signing certificate are documented in the prior release evidence; a new APK is not implied by this unmerged branch.

## Current engineering branch scope

- Android now has a runtime policy that checks validated internet, Wi-Fi/cellular transport, metered state, available RAM, thermal status and battery before choosing local, Termux, remote or blocked execution.
- Auto mode ranks installed local GGUFs by task and memory budget: coder models for coding, DeepSeek R1 for reasoning when it fits, and Qwen3/general candidates for chat. A model is not loaded if its estimated working set exceeds available RAM.
- Severe thermal state and critically low battery block native and Termux inference. Remote inference is used only when a validated network and an explicitly configured self-hosted orchestrator are available.
- Offline mode never falls through to the internet. It can use native inference or an authenticated loopback Termux server.
- Termux setup installs the official `llama-cpp` package and runs `llama-server` on `127.0.0.1` only, with one request slot, bounded threads, conservative context, CPU-only default and a generated local pairing token.
- Android bundles and displays a 50-entry model governance catalogue covering requested LLMs, image, speech and training tools, including creator, capability, licence status, placement and activation gates.
- The 50 new registry records are candidates, not 50 downloaded/ready models. Only the original twelve GGUF files remain archive-ready until exact new checkpoints, hashes, licences, runtime adapters and benchmarks are verified.
- The Python orchestrator now supports task-specific provider priority, explicit licence approval, multimodal capability matching, sequential fallback and a process-local circuit breaker.

## Cloudflare decision and deployment

- Cloudflare Workers AI inference has been removed from the Worker source and Wrangler configuration.
- The deployed Worker `wasti-nexus-gateway` was updated using the Worker upload API. Deployment ID: `d835d9bd51174bc98bfee30df3a3085d`.
- A subsequent Worker settings read confirmed the `AI` binding is absent. Existing text/secret/rate-limit bindings were preserved.
- The Worker is an optional HTTPS relay/control plane for owner-controlled self-hosted providers only. It is not the inference host and is not on the default Android route.
- No Cloudflare R2, KV, D1, Vectorize or other Cloudflare object/model storage is used. Google Drive SAF remains the model/dataset/adapter archive.
- Cloudflare Workers still has platform limits. A relay can be unavailable or time out; local inference and Termux do not depend on it.

## Storage and model archive

- Google Drive is accessed through Android Storage Access Framework. No Drive API key, service account or OAuth client is needed for the folder-picker archive path.
- The original twelve GGUF artefacts retain exact filenames, byte lengths, SHA-256 hashes and source revisions.
- New registry entries have null checkpoint/revision/SHA-256 until their exact official artifacts are selected and verified. They are intentionally not included in the one-tap archive action.
- Drive stores files; it does not execute inference. Heavy models need an owner-operated inference machine/cluster.

## Licence and usage policy

- Apache-2.0 and MIT model weights can generally be self-hosted and used commercially subject to exact checkpoint notices and other applicable terms.
- Hosted APIs are separate services and may be paid, rate-limited or unavailable even when the underlying weights are permissively licensed.
- gpt-oss has an additional usage policy. Llama 4 uses a Community License. FLUX.1 [dev] is held from commercial use; FLUX.1 [schnell] is the Apache-2.0 candidate.
- Kimi K3, MiniMax M3, Qwen Flash-Next, some Hunyuan variants, DeepSeek distills and individual Piper voices remain review-gated until exact upstream terms are pinned.
- See [MODEL_LICENSING_AND_HOSTING.md](MODEL_LICENSING_AND_HOSTING.md) and [extended-model-registry.json](../models/extended-model-registry.json).

## Current CI and release policy

- PR #30 checks passed before merge.
- The current branch adds Android JVM policy tests, Python capability/circuit-breaker tests, extended-registry validation, Worker tests and Termux shell syntax checks. Its CI result must be checked before merge.
- A passing build does not certify the physical Redmi, sustained thermals, model quality, Urdu speech, Termux package state or long-running server uptime.

## Remaining production gates

1. Install the current signed build on the Redmi Note 14 Pro and verify upgrade signing.
2. Grant the Google Drive SAF folder permission; archive and re-install the original twelve; test interruption, retry and checksum rejection.
3. In Termux, install `llama-cpp`, start one small quantized GGUF, retrieve `nexus server token`, pair the Android app with `http://localhost:8088`, and verify local offline inference.
4. Benchmark native and Termux model selection across coding, reasoning and chat; record RAM, context, latency, temperature, battery and process survival.
5. Validate Auto routing with Wi-Fi, metered mobile data, captive portal/no-internet, airplane mode, severe thermal and low-battery cases.
6. Pin and validate new model artifacts one by one; implement dedicated vision, speech, image-generation and training runtimes before enabling those tasks.
7. Complete self-hosted remote-provider deployment, TLS/authentication, queueing, quotas, monitoring, cancellation and recovery before customer service use.
8. Complete encrypted-memory restore and cross-device recovery; review its separate GitHub ciphertext storage policy.

## Honest conclusion

WASTI NEXUS now has a stronger local-first/self-hosted architecture and a 50-entry capability/licence catalogue, while preserving the original twelve-model archive. Cloudflare AI is disabled and its AI binding has been removed from the deployed Worker. The new branch is still awaiting CI and merge; no claim is made that the 50 candidate models are downloaded, that all modalities are implemented, or that the Redmi has passed sustained inference tests.
