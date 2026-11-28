# WASTI NEXUS

**One Android app. One monorepo. Local-first intelligence. Self-hosted models. Google Drive archive.**

WASTI NEXUS is a personal AI workspace with native Android GGUF inference, an optional Termux llama.cpp server, a self-hosted model orchestration API and an extended model/licence registry. It does not depend on Cloudflare Workers AI or any paid model-provider API for local inference.

> **Current source version: 0.5.15 (`versionCode 20`); this candidate is awaiting CI/signature verification.** The app now includes encrypted local conversation history, search/rename/delete/Markdown export, safe Markdown rendering for assistant output, local streaming, cancellable remote SSE for explicitly selected single models, model health/auto-load, bounded attachments and real gateway collaboration traces. The 0.5.13 signed APK passed signature verification; 0.5.15 CI is still pending. Physical Redmi testing and live provider inference remain release gates.

## Runtime hierarchy

1. **Android native inference:** use an installed compatible GGUF only when measured free RAM, thermal state and battery headroom are safe.
2. **Termux local server:** optional llama.cpp `llama-server` on `localhost:8088`, bound to loopback only and protected by a generated pairing token. It works without internet and uses the same phone hardware.
3. **Owner-controlled inference host:** optional direct HTTPS connection to a workstation/server/cluster you operate. Use this for models too large for the phone.
4. **Optional Cloudflare relay:** edge authentication and request relay only. Cloudflare Workers AI inference is disabled; the relay is never required for offline or critical work.

Auto mode checks validated internet, Wi-Fi/mobile-data transport, metered status, available RAM, thermal state and battery before choosing a route. Offline mode never falls through to internet. Unsupported tasks such as image generation, speech or training are blocked unless an installed specialist runtime actually supports them. Local images support on-device OCR and coarse ML Kit labels; natural-language visual reasoning still requires a configured online vision-capable provider.

## Storage policy

Google Drive via Android Storage Access Framework is the sole user-controlled archive for model weights, datasets, LoRA adapters, evaluation reports and exported GGUFs. No Cloudflare R2/KV/D1 or other Cloudflare model storage is used. Drive is an archive, not an inference runtime.

## Implemented capabilities

- Native Kotlin/Jetpack Compose Android app with local GGUF model management, automatic selected-model loading, per-model health state and offline chat.
- Chat workspace with encrypted local history (Android Keystore AES-256-GCM), conversation search/rename/delete/Markdown export, safe Markdown rendering, selectable/copyable responses, prompt editing, Stop/cancellation and streaming for local plus explicitly selected remote models. Auto/Hybrid remote responses remain non-streaming.
- Bounded text/code, PDF (first 100 pages), DOCX/XLSX/PPTX and ODT/ODS/ODP attachments are extracted on-device. Images receive local OCR and coarse visual labels; image pixels are sent only to a configured online vision-capable self-hosted model. Local native inference remains text-only.
- Google Drive SAF workspace with persistent folder permission, size/SHA-256 checks and atomic installation.
- Twelve original pinned model entries preserved in `nexus-model-catalog.json`.
- Task-aware Python orchestrator with `nexus-auto`, provider capability matching, explicit licence gates, task-specific priorities, circuit breakers and sequential fallback.
- Android runtime policy for network, metering, RAM, heat and battery-aware routing, plus task-aware selection among installed local GGUFs.
- Termux `llama-cpp` server setup and CLI, bound to loopback, authenticated with a generated local pairing token, one concurrent request and conservative defaults.
- In-app 50-entry model governance catalogue with creator, capability, licence, hosting, commercial status and source links.
- In-app third-party notices and full Apache-2.0/MIT licence texts.
- Optional Cloudflare Worker relay with Workers AI inference disabled, HTTPS-only self-hosted provider routing, licence/capability gates, request limits, customer-mode rate limiting, personal-memory isolation, provider-address SSRF checks and cancellable single-model SSE.

## Model activation is deliberately gated

The 50-entry registry is not a claim that 50 weights are already downloaded or runnable. Each new model needs an exact official checkpoint, immutable revision, SHA-256, licence and usage-policy capture, runtime adapter, task-specific evaluation and device/server benchmark before it becomes installable. Large 27B–763B models are not phone downloads merely because they are open-weight.

Apache-2.0 and MIT weights can generally be used commercially with their required notices, but the model licence does not make hosted API inference free or unlimited. Usage policies, voice-pack licences, community licences, trademarks and third-party rights still matter. See [models/extended-model-registry.json](models/extended-model-registry.json) and [docs/MODEL_LICENSING_AND_HOSTING.md](docs/MODEL_LICENSING_AND_HOSTING.md).

## Current release and remaining gates

- Current source candidate: **0.5.15 / versionCode 20**. Previous signed release artifacts are not equivalent to this new source candidate; check [STATUS.md](docs/STATUS.md) for exact workflow evidence. See [Release signing and artifacts](docs/RELEASE.md).
- The deployed Worker is a relay, not an inference host. At this snapshot, no `NEXUS_PROVIDERS_JSON` provider catalogue is configured, so online model inference is not yet available.
- Do not label the whole product production-certified until the provider round-trip, Redmi device regression, CodeQL alert triage, operational controls and remaining roadmap items are evidenced.

## Chat workspace details

See [Chat features and storage boundaries](docs/CHAT_FEATURES.md), [verified implementation status](docs/STATUS.md) and [remaining release gates](docs/ROADMAP.md).

## Termux quick start

```bash
pkg update && pkg upgrade -y
pkg install -y curl python llama-cpp
mkdir -p "$HOME/models"
```

Copy one tested GGUF into `$HOME/models/`, configure `termux/env.example` as `~/.config/wasti-nexus/env`, then:

```bash
nexus server start "$HOME/models/your-model.gguf"
nexus server token
nexus models
nexus chat MODEL_ID "Hello in Urdu and English"
```

Replace `MODEL_ID` with the ID printed by `nexus models`. Paste the generated local pairing token into the app's endpoint key field. This is local authentication, not an AI provider API key. See [termux/README.md](termux/README.md).

## Important limits

- A phone is not an unlimited GPU server. Android may terminate hot or memory-heavy processes.
- Termux runs on the same mobile hardware; it is an alternate runtime, not extra compute.
- Self-hosting avoids model-vendor token charges but still consumes hardware, power, bandwidth and maintenance.
- Colab free GPU sessions are not guaranteed and are not part of the core inference path.
- Do not expose llama-server on `0.0.0.0` or the public internet without a separate authenticated TLS reverse proxy and firewall.
- Android sandbox and Termux:API permissions remain in force. No unrestricted shell, root bypass or silent device control is claimed.
- Passing CI does not certify real-device inference quality, sustained thermals, battery life, Urdu quality or server uptime.

See [docs/STATUS.md](docs/STATUS.md), [docs/PROVIDER_SETUP.md](docs/PROVIDER_SETUP.md), [docs/ORCHESTRATION_AND_COMPUTE.md](docs/ORCHESTRATION_AND_COMPUTE.md), and [docs/SECURITY.md](docs/SECURITY.md).
