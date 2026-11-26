# WASTI NEXUS

**One Android app. One monorepo. Local-first intelligence. Self-hosted models. Google Drive archive.**

WASTI NEXUS is a personal AI workspace with native Android GGUF inference, an optional Termux llama.cpp server, a self-hosted model orchestration API and an extended model/licence registry. It does not depend on Cloudflare Workers AI or any paid model-provider API for local inference.

> **Status: active engineering release candidate — not yet production-certified.** The twelve original pinned GGUF entries remain intact. PR #30 added task-aware provider routing and an expanded model registry. The current milestone adds resource-aware Android routing, a loopback-only Termux server, self-hosted-only edge relay policy and licence governance for 50 model families/tools. Device performance, all new checkpoint downloads, multimodal runtimes and long-running server benchmarks remain validation gates.

## Runtime hierarchy

1. **Android native inference:** use an installed compatible GGUF only when measured free RAM, thermal state and battery headroom are safe.
2. **Termux local server:** optional llama.cpp `llama-server` on `localhost:8088`, bound to loopback only. It works without internet and uses the same phone hardware.
3. **Owner-controlled inference host:** optional direct HTTPS connection to a workstation/server/cluster you operate. Use this for models too large for the phone.
4. **Optional Cloudflare relay:** edge authentication and request relay only. Cloudflare Workers AI inference has been removed; the relay is never required for offline or critical work.

Auto mode checks validated internet, Wi-Fi/mobile-data transport, metered status, available RAM, thermal state and battery before choosing a route. Offline mode never falls through to internet. Unsupported tasks such as image generation, speech or training are blocked unless an installed specialist runtime actually supports them.

## Storage policy

Google Drive via Android Storage Access Framework is the sole user-controlled archive for model weights, datasets, LoRA adapters, evaluation reports and exported GGUFs. No Cloudflare R2/KV/D1 or other Cloudflare model storage is used. Drive is an archive, not an inference runtime.

## What is implemented

- Native Kotlin/Jetpack Compose Android app with local GGUF model management and offline chat.
- Google Drive SAF workspace with persistent folder permission, size/SHA-256 checks and atomic installation.
- Twelve original pinned model entries preserved in `nexus-model-catalog.json`.
- Task-aware Python orchestrator with `nexus-auto`, provider capability matching, licence gates and sequential fallback.
- Android runtime policy for network, metering, RAM, heat and battery-aware routing.
- Termux `llama-cpp` server setup and CLI, bound to loopback with one concurrent request and conservative defaults.
- Extended in-app governance catalogue with 48 model/tool entries, licences, capabilities, placement and activation gates.
- Optional Cloudflare Worker relay with Workers AI removed from code and Wrangler configuration.
- Existing encrypted-memory snapshot features and tests.

## Model activation is deliberately gated

The 48-entry registry is not a claim that 48 weights are already downloaded or runnable. Each new model needs an exact official checkpoint, immutable revision, SHA-256, licence and usage-policy capture, runtime adapter, task-specific evaluation and device/server benchmark before it becomes installable. Large 27B–763B models are not phone downloads merely because they are open-weight.

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
nexus server status
nexus chat wasti-local "Hello in Urdu and English"
```

See [termux/README.md](termux/README.md) for setup, model placement, safety and Android integration.

## Repository layout

```
android/       Native Android client, model manager and local runtime
cloudflare/    Optional edge relay and web console (no Workers AI inference)
orchestrator/  Python model routing API
models/        Model, licence and capability registries
termux/        Local llama.cpp server and CLI
memory/        Encrypted-memory schemas
datasets/      Evaluation/training data manifests
sdks/          TypeScript and Python clients
docs/          Architecture, security, licensing and status
```

## Important limits

- A phone is not an unlimited GPU server. Android may terminate hot or memory-heavy processes.
- Termux runs on the same mobile hardware; it is an alternate process/runtime, not extra compute.
- Self-hosting avoids model-vendor token charges but still consumes hardware, power, bandwidth and maintenance.
- Colab free GPU sessions are not guaranteed and are not part of the core inference path.
- Do not expose llama-server on `0.0.0.0` or the public internet without a separate authenticated TLS reverse proxy and firewall.
- Android sandbox and Termux:API permissions remain in force. No unrestricted shell, root bypass or silent device control is claimed.
- Passing CI does not certify real-device inference quality, sustained thermals, battery life, Urdu quality or server uptime.

See [docs/STATUS.md](docs/STATUS.md), [docs/ORCHESTRATION_AND_COMPUTE.md](docs/ORCHESTRATION_AND_COMPUTE.md), and [docs/SECURITY.md](docs/SECURITY.md).
