# WASTI NEXUS — optional Cloudflare edge relay

Cloudflare is **not an AI inference host, GPU provider or model archive** in the WASTI NEXUS architecture. Workers AI has been removed from the source and deployment configuration. The Android app defaults to the local Termux llama.cpp endpoint (`http://localhost:8088`) and can connect directly to an HTTPS inference server that you own.

## What this Worker is allowed to do

- Serve the optional web console.
- Authenticate client requests and enforce basic edge rate limits.
- Relay requests to explicitly configured, owner-controlled HTTPS OpenAI-compatible inference endpoints.
- Expose only providers whose model records are enabled and have approved licence status.
- Provide the existing encrypted-memory sync endpoints (the Worker does not store model weights).

## What this Worker does not do

- No Cloudflare Workers AI inference or GPU calls. The `AI` binding is not used by source code.
- No R2, KV, D1, Durable Objects, Vectorize or other Cloudflare object/model storage.
- No model download, model conversion, fine-tuning or training.
- No promise of unlimited compute or zero request/CPU/memory limits.

Cloudflare Workers still has platform request, CPU, memory, concurrency and plan limits. A relay can fail or time out even when the actual inference server is healthy. Therefore **do not make the Worker relay a mandatory step in a critical or long-running task**. Prefer direct Android-to-self-hosted-server HTTPS connections when possible. The local Termux route bypasses Cloudflare entirely.

## API

- `GET /healthz` — public health; reports `cloudflare_ai_enabled: false` and `inference_backend: self-hosted-provider-relay-only`.
- `GET /v1/models` — authenticated list of configured, enabled and licence-approved self-hosted provider models.
- `POST /v1/chat/completions` — request to a configured self-hosted OpenAI-compatible provider.
- `POST /v1/hybrid/chat/completions` — bounded multi-model review using at least two distinct configured self-hosted model IDs.
- `GET/POST /v1/memory/snapshots` — encrypted memory sync through GitHub Contents API; this is separate from the Google Drive model archive.

## Configure a self-hosted provider (optional)

Set Worker secret `NEXUS_PROVIDERS_JSON` to a JSON object with `providers`. Each provider must use a trusted HTTPS endpoint and a server-side secret name:

```json
{
  "providers": [
    {
      "id": "my-owned-server",
      "base_url": "https://inference.example/v1",
      "api_key_env": "NEXUS_INFERENCE_KEY",
      "models": [
        {
          "id": "wasti-qwen-local",
          "upstream_model": "qwen3-4b-q4",
          "enabled": true,
          "commercial_allowed": true,
          "license": "Apache-2.0",
          "license_status": "verified",
          "capabilities": ["chat", "reasoning", "coding"]
        }
      ]
    }
  ]
}
```

Set `NEXUS_INFERENCE_KEY` as a Worker secret. Never place provider credentials in repository files, Android builds or browser code. The `license_status` value must be `verified` or `approved` and `commercial_allowed` must be explicitly true for customer mode. Restricted, pending or unknown licence records are hidden and blocked.

## Google Drive archive rule

All model weights, datasets, LoRA adapters, evaluation reports and exported GGUF files belong in the user's selected Google Drive workspace through Android Storage Access Framework. The Worker never stores or proxies model files. Do not add R2 or any Cloudflare storage binding.

## Encrypted memory note

The current legacy memory sync uses GitHub to store client-encrypted snapshots and is not the model archive. Only AES-256-GCM ciphertext and non-sensitive metadata are sent. The configured repository is public, so do not store plaintext personal/customer data or encryption keys there. This GitHub sync remains a separate existing feature and is not a replacement for Google Drive model storage.

## Operational recommendation

Treat this Worker as optional convenience infrastructure only. Keep the phone's local GGUF engine and the Termux loopback server as independent paths. If the Worker is unavailable, the app should continue local inference. For long-running heavy work, connect directly to a self-hosted machine you control, with authenticated TLS, resource monitoring, queueing and explicit spend/power controls.
