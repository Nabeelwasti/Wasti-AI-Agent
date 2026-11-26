# WASTI NEXUS — verified implementation status

**Snapshot:** 2026-11-27  
**Main HEAD:** `da44753f612647803b600b632851a5703753b287`  
**Merged milestone:** PR #31 — resource-aware local-first orchestration, Termux server, 50-entry model governance and self-hosted-only edge relay  
**Android release:** `0.5.0` (`versionCode 5`)  
**Signed release workflow:** run `45888314128` — successful  
**Signed APK artifact:** `15150097698` — 17,369,552 bytes; expires 2026-12-26  
**State:** release candidate; physical-device validation remains required

## Confirmed implementation

- PR #30 and PR #31 are merged into `main`.
- Twelve original pinned GGUF archive entries and their manifests remain intact.
- Android local inference and model management remain in one app/monorepo.
- Android runtime policy checks validated internet, Wi-Fi/cellular transport, metered state, available RAM, thermal status and battery/charging before choosing local, Termux, remote or blocked execution.
- Auto mode ranks installed local GGUFs by task and RAM budget: coder models for coding, DeepSeek R1 for reasoning when it fits, and Qwen3/general candidates for chat.
- Severe thermal state and critically low battery block native and Termux inference. Remote inference is used only when a validated network and explicitly configured self-hosted orchestrator are available.
- Offline mode never falls through to the internet. It can use native inference or an authenticated loopback Termux server.
- Termux setup installs `llama-cpp` and runs `llama-server` on `127.0.0.1` only, with one request slot, bounded threads, conservative context, CPU-only default and a generated local pairing token.
- Android Settings contains a 50-entry model governance catalogue and source links, plus upstream notices and full Apache-2.0/MIT licence texts.
- The 50 new registry records are candidates, not 50 downloaded/ready models. Only the original twelve GGUF files remain archive-ready until exact new checkpoints, hashes, licences, runtime adapters and benchmarks are verified.
- Python orchestration supports task-specific provider priorities, explicit licence approval, multimodal capability matching, sequential fallback and a process-local circuit breaker.

## Cloudflare decision and deployed state

- Cloudflare Workers AI inference is removed from Worker source and Wrangler configuration.
- Deployed Worker `wasti-nexus-gateway` has no `AI` binding. The deployment API settings were read after update and confirmed the AI binding absent; existing secrets and rate-limit/plain-text bindings were preserved.
- Main-branch deployed self-hosted-only gateway policy smoke test passed in CI run `45888314086`.
- Worker is an optional HTTPS relay/control plane for owner-controlled self-hosted providers only. It is not the inference host and is not on the default Android route.
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
- The app's Open Source Licences screen bundles full Apache-2.0 and MIT texts and the existing third-party model notices. Exact per-checkpoint copyright/NOTICE and additional usage terms still take precedence.
- See [MODEL_LICENSING_AND_HOSTING.md](MODEL_LICENSING_AND_HOSTING.md) and [extended-model-registry.json](../models/extended-model-registry.json).

## CI and signed release evidence

- Main WASTI NEXUS CI run `45888314086`: Android debug build/JVM tests, Python tests/source validation, Cloudflare Worker syntax and deployed gateway smoke test all passed.
- Main Orchestrator CI run `45888314256`: passed.
- Signed Android release run `45888314128`: passed APK assembly, APK signature verification and artifact upload.
- Signer certificate SHA-256 matches the permanent release certificate: `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`.
- Passing CI and signing do not certify the physical Redmi, sustained thermals, model quality, Urdu speech or long-running server uptime.

## Remaining production gates

1. Install the 0.5.0 signed build on the Redmi Note 14 Pro and verify upgrade signing.
2. Grant the Google Drive SAF folder permission; archive and re-install the original twelve; test interruption, retry and checksum rejection.
3. In Termux, install `llama-cpp`, start one small quantized GGUF, retrieve `nexus server token`, pair the Android app with `http://localhost:8088`, and verify local offline inference.
4. Benchmark native and Termux model selection across coding, reasoning and chat; record RAM, context, latency, temperature, battery and process survival.
5. Validate Auto routing with Wi-Fi, metered mobile data, captive portal/no-internet, airplane mode, severe thermal and low-battery cases.
6. Pin and validate new model artifacts one by one; implement dedicated vision, speech, image-generation and training runtimes before enabling those tasks.
7. Complete self-hosted remote-provider deployment, TLS/authentication, queueing, quotas, monitoring, cancellation and recovery before customer service use.
8. Complete the safe, permission-scoped Termux:API bridge; no unrestricted shell, root bypass or Android sandbox escape is promised.
9. Complete encrypted-memory restore and cross-device recovery; review its separate GitHub ciphertext storage policy.

## Honest conclusion

WASTI NEXUS 0.5.0 now has a merged local-first/self-hosted architecture, a 50-entry capability/licence catalogue, authenticated Termux inference, and an optional Cloudflare relay with Workers AI disabled. The signed APK and CI are verified. The 50 new candidates are not all downloaded or runnable, and physical-device, multimodal, Termux:API and sustained-server validation remain outstanding.
