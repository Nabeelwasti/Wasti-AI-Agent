# WASTI NEXUS

**One Android app. One monorepo. Local-first intelligence. Self-hosted models. Google Drive archive.**

WASTI NEXUS is a personal AI workspace with native Android GGUF inference, an optional Termux llama.cpp server, a self-hosted model orchestration API and an extended model/licence registry. It does not depend on Cloudflare Workers AI or any paid model-provider API for local inference.

> **Current source version: 0.5.3 (`versionCode 8`).** Android local-model health/auto-load, cancellable generation, real gateway collaboration traces, bounded text attachments and remote vision-image attachments are implemented in the current `main` source. CI and the owner-signed APK for this exact source revision must pass before distributing it. Physical Redmi testing, provider deployment and long-running server benchmarks remain release gates.

## Runtime hierarchy

1. **Android native inference:** use an installed compatible GGUF only when measured free RAM, thermal state and battery headroom are safe.
2. **Termux local server:** optional llama.cpp `llama-server` on `localhost:8088`, bound to loopback only and protected by a generated pairing token. It works without internet and uses the same phone hardware.
3. **Owner-controlled inference host:** optional direct HTTPS connection to a workstation/server/cluster you operate. Use this for models too large for the phone.
4. **Optional Cloudflare relay:** edge authentication and request relay only. Cloudflare Workers AI inference is disabled; the relay is never required for offline or critical work.

Auto mode checks validated internet, Wi-Fi/mobile-data transport, metered status, available RAM, thermal state and battery before choosing a route. Offline mode never falls through to internet. Unsupported tasks such as image generation, speech or training are blocked unless an installed specialist runtime actually supports them. Image understanding is available only through a configured online vision-capable provider; it is not an offline capability.

## Storage policy

Google Drive via Android Storage Access Framework is the sole user-controlled archive for model weights, datasets, LoRA adapters, evaluation reports and exported GGUFs. No Cloudflare R2/KV/D1 or other Cloudflare model storage is used. Drive is an archive, not an inference runtime.

## Implemented capabilities

- Native Kotlin/Jetpack Compose Android app with local GGUF model management, automatic selected-model loading, per-model health state and offline chat.
- Selectable/copyable chat messages, prompt editing, cancellable local and remote generation, and actual collaboration metadata from the gateway.
- Bounded text/code attachments for local or remote context; JPEG/PNG/WebP attachments are sent only to a configured online vision-capable self-hosted model. Local native inference remains text-only.
- Google Drive SAF workspace with persistent folder permission, size/SHA-256 checks and atomic installation.
- Twelve original pinned model entries preserved in `nexus-model-catalog.json`.
- Task-aware Python orchestrator with `nexus-auto`, provider capability matching, explicit licence gates, task-specific priorities, circuit breakers and sequential fallback.
- Android runtime policy for network, metering, RAM, heat and battery-aware routing, plus task-aware selection among installed local GGUFs.
- Termux `llama-cpp` server setup and CLI, bound to loopback, authenticated with a generated local pairing token, one concurrent request and conservative defaults.
- In-app 50-entry model governance catalogue with creator, capability, licence, hosting, commercial status and source links.
- In-app third-party notices and full Apache-2.0/MIT licence texts.
- Optional Cloudflare Worker relay with Workers AI removed from source, Wrangler config and deployed AI binding.

## Model activation is deliberately gated

The 50-entry registry is not a claim that 50 weights are already downloaded or runnable. Each new model needs an exact official checkpoint, immutable revision, SHA-256, licence and usage-policy capture, runtime adapter, task-specific evaluation and device/server benchmark before it becomes installable. Large 27B–763B models are not phone downloads merely because they are open-weight.

Apache-2.0 and MIT weights can generally be used commercially with their required notices, but the model licence does not make hosted API inference free or unlimited. Usage policies, voice-pack licences, community licences, trademarks and third-party rights still matter. See [models/extended-model-registry.json](models/extended-model-registry.json) and [docs/MODEL_LICENSING_AND_HOSTING.md](docs/MODEL_LICENSING_AND_HOSTING.md).

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

See [docs/STATUS.md](docs/STATUS.md), [docs/ORCHESTRATION_AND_COMPUTE.md](docs/ORCHESTRATION_AND_COMPUTE.md), and [docs/SECURITY.md](docs/SECURITY.md).
