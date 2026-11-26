# WASTI NEXUS orchestration and self-hosted compute

## Primary architecture

- Android native local GGUF engine.
- Optional Termux llama.cpp server bound to `127.0.0.1:8088`.
- Optional owner-controlled HTTPS inference host for larger models.
- Python orchestrator with task classification, capability matching, commercial licence gates and sequential fallback.
- Google Drive SAF as the user-controlled archive for model weights, datasets, adapters and exports.
- Cloudflare Worker, if retained, is only an optional relay/control plane. Workers AI inference is disabled; R2/KV/D1 and other Cloudflare storage are not used.

## Runtime decision order

1. Determine task capability: general chat, reasoning, coding, vision, image generation, speech-to-text, text-to-speech or training.
2. Read Android runtime state: validated internet, Wi-Fi/mobile-data transport, metered state, free RAM, battery, charging and thermal status.
3. Offline mode permits only native local inference or the loopback Termux server.
4. Auto mode uses the native model when the task is supported and measured RAM/thermal/battery headroom is safe.
5. If native inference is unsuitable, use a reachable loopback Termux server for supported text tasks.
6. If no local route is suitable and validated internet exists, use an explicitly configured owner-controlled HTTPS self-hosted orchestrator. Auto mode requires `nexus-auto` to be advertised.
7. If no valid route exists, preserve the prompt and return a truthful blocked/unavailable result.

## Safety thresholds

- Native model loading uses a conservative estimate based on GGUF file size plus a fixed reserve; the actual native load check remains authoritative.
- Severe thermal status and critically low battery block new local loads even when a model is already loaded.
- Only one local chat model should be loaded at a time.
- Metered cellular internet is detected and called out. Model-weight downloads are never silently started by Auto inference.
- Termux server defaults to CPU-only, one parallel request, bounded threads and a 4096-token context.
- Termux server binds to loopback only. Do not bind to `0.0.0.0` or expose it publicly.

## Provider fallback

The Python router selects only configured, enabled and licence-approved providers whose declared capabilities match the task. It tries candidates in priority order and reports failed agents. Vision, audio, image generation and training tasks fail closed if no matching specialist exists. A generic chat model is not silently substituted for a missing modality.

Provider configuration should declare `capabilities`, `license`, `license_status`, `commercial_allowed`, `enabled`, `priority`, `base_url`, `model` and optional `api_key_env`. Customer mode requires explicit commercial clearance and approved/verified licence status. Keep secrets in server-side secret stores.

## Cloudflare and long-running work

Cloudflare Workers AI is not used. The Worker relay is optional and has platform request/CPU/memory limits; long-running tasks should connect directly to the self-hosted inference host. No task depends on Cloudflare availability. Heavy inference requires an owner-operated GPU server or cluster with authenticated HTTPS, queueing, bounded concurrency, health checks, cancellation and resource monitoring.

## Termux and Android boundary

Termux is a separate Android app sandbox. Its local llama.cpp server can use model files in Termux `$HOME/models`; it cannot silently read the Android app's private model directory. The Android app connects to the loopback server via `localhost`. Termux:API exposes selected APIs after their own permission flows; it does not bypass Android sandbox restrictions or grant unrestricted root/device control.

## Google Drive archive

Drive is the archive, not inference. Use SAF folder permissions; validate file size and SHA-256; atomically install verified artifacts into app-private storage. No Cloudflare object storage is introduced. Keep model manifests separate from the runtime model registry: a candidate is not downloadable until the exact artifact is pinned and verified.
