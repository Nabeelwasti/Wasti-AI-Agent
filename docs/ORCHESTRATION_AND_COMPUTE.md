# WASTI NEXUS orchestration and remote-compute strategy

## Target architecture

Android chat UI
- Local policy engine: available RAM, battery, thermal state, storage and network
- Local llama.cpp runtime for compatible installed GGUF models
- Optional paired Termux companion with loopback access and explicit grants
- Authenticated NEXUS gateway
  - Edge auth, quotas, validation, routing and streaming relay
  - Optional Workers AI providers
  - Dedicated GPU/CPU inference-host adapters
  - Durable job queue and status polling
  - Encrypted personal-memory relay

Google Drive SAF is the private model/dataset/adapter archive only.

## Decision policy
- Offline: local only; never fall through to network.
- Auto: local first only when a compatible model is installed and measured resource headroom is sufficient. Otherwise use a configured remote route after connectivity, privacy, quota and budget checks.
- Online: remote route by explicit user choice. Do not silently change privacy mode.
- Vision/audio/image/training tasks require a provider with that exact capability.
- Unload idle models, cap context/generation, and never load multiple large models concurrently on the phone.
- Remote fallback uses bounded retries, exponential backoff with jitter, provider circuit breakers, deadlines and idempotency keys.
- If every route fails, preserve the prompt locally and report a recoverable error.

## Cloudflare's role
Use Workers/Pages for gateway, auth, rate limiting, routing, streaming relay and job coordination. Workers are not GPU servers. The current published Workers limits include 128 MB memory and up to five minutes CPU per invocation on Paid; Paid starts at $5/month plus metered usage. A separately provisioned GPU/CPU host is required for large-model inference.

## Termux integration
Termux is optional. A future paired bridge must bind only to loopback, require explicit pairing, expose a narrow allowlist, use one-time challenges and per-request approval, and maintain an audit trail. Termux:API exposes selected APIs only after Android permission flows. It cannot remove Android sandbox restrictions or grant unrestricted root access. Root-only actions remain unavailable unless the device is rooted and the user explicitly configures them.

## Google Drive only
Use Storage Access Framework folder grants for model archives, datasets, LoRA adapters, evaluation reports and exported GGUFs. Do not use R2, KV, D1 or other Cloudflare products as model/object storage. Drive is not an inference runtime or continuously mounted server filesystem.

## Safety and reliability
- No model auto-download over mobile data without consent.
- No inference if estimated working set exceeds conservative memory budget.
- Avoid concurrent local and remote generation unless multi-agent comparison is requested.
- Require approval for SMS, calls, file deletion, app installation, accessibility actions, shell commands and other sensitive operations.
- Never expose a general-purpose shell endpoint to the public internet.
- Keep credentials in Android Keystore/server secret stores.
- Provide local-only, remote-route, kill-switch and export/delete controls.
