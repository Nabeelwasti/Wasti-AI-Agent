# WASTI NEXUS API reference

**Gateway:** `https://wasti-nexus-gateway.wastinabeel99.workers.dev`  
**API prefix:** `/v1`

Do not append `/v1` to the configured base URL; clients append the route themselves.

## Authentication

Protected requests use:

```http
Authorization: Bearer YOUR_NEXUS_API_KEY
Content-Type: application/json
```

The bearer key authenticates to your NEXUS gateway. It is **not a paid model-vendor API key**. Offline Android GGUF inference does not need this gateway or any online key. Remote gateway use requires gateway authentication; upstream self-hosted provider credentials are kept as Worker secrets and are never sent to the Android app.

The master `NEXUS_API_KEY` can access the personal encrypted-memory endpoints. Optional scoped client keys configured through `NEXUS_CLIENT_KEYS_JSON` can be limited to `models` and `chat`; they cannot access the personal memory vault, even if a key entry lists a `memory` scope. Customer mode defaults when `NEXUS_DEPLOYMENT_MODE` is absent and fails closed if the `NEXUS_RATE_LIMIT` binding is missing.

## Endpoints

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| GET | `/healthz` | Public | Liveness and safe configured-model count; no secrets |
| GET | `/v1/models` | Required | List enabled, licence-approved self-hosted models |
| POST | `/v1/chat/completions` | Required | Single model, Auto routing or Hybrid mode |
| POST | `/v1/hybrid/chat/completions` | Required | Explicit multi-model Hybrid orchestration |
| GET | `/v1/memory/snapshots` | Master key | List client-encrypted personal-memory snapshots |
| POST | `/v1/memory/snapshots` | Master key | Store a client-encrypted snapshot |
| GET | `/v1/memory/snapshots/{snapshot_id}` | Master key | Retrieve ciphertext for client-side decryption |

## Model catalogue and provider setup

The gateway exposes only models from the owner-configured `NEXUS_PROVIDERS_JSON` Worker variable. A model must be enabled and pass the deployment-mode licence gate. Provider credentials are referenced by `api_key_env` and stored as individual Worker secrets.

**Current deployment state (2026-11-27):** the Worker has no `NEXUS_PROVIDERS_JSON` variable and no inference provider configured. The health endpoint and relay-policy tests can pass while the model catalogue is empty. Configure an owner-controlled HTTPS OpenAI-compatible inference server before expecting remote chat to work. See [PROVIDER_SETUP.md](PROVIDER_SETUP.md). The relay does not store model weights and does not invoke Cloudflare Workers AI.

## Chat request

```json
{
  "model": "your-configured-model-id",
  "messages": [
    {"role": "user", "content": "Explain this concept simply."}
  ],
  "temperature": 0.3,
  "max_tokens": 1200,
  "stream": true
}
```

- `messages`: 1–40 entries; supported roles are `system`, `user` and `assistant`.
- `temperature`: optional number from 0 to 2.
- `max_tokens`: optional integer from 1 to 8192; the relay clamps the upstream request to 4096.
- `stream`: optional boolean; defaults to non-streaming behavior.
- `nexus.mode`: optional `auto` or `hybrid` orchestration mode.
- Provider responses are OpenAI-compatible chat completions.

### Remote streaming

Set `stream: true` only with one explicitly selected configured model. The Worker proxies OpenAI-compatible Server-Sent Events, the Android app renders arriving text tokens, and client cancellation is propagated upstream.

- **Single selected model:** SSE supported.
- **Auto:** use `stream: false`; sequential fallback must finish before the answer is returned.
- **Hybrid:** use `stream: false`; independent answers, reviews and synthesis complete before the final trace is returned.
- An Auto or Hybrid request with `stream: true` is rejected rather than pretending to stream.

### Auto mode

```json
{
  "model": "nexus-auto",
  "nexus": {"mode": "auto"},
  "messages": [{"role": "user", "content": "Review this Kotlin code."}],
  "stream": false
}
```

Auto currently routes only general chat, coding, reasoning and vision to compatible configured models. It does not claim support for audio, image generation or training runtimes that are not installed.

### Hybrid mode

```json
{
  "model": "nexus-hybrid",
  "nexus": {"mode": "hybrid", "review_rounds": 1},
  "messages": [{"role": "user", "content": "Compare these approaches."}],
  "stream": false
}
```

Hybrid requires at least two distinct eligible model IDs. The response includes actual provider participants, independent outputs, peer-review results, synthesis model, failures, trace ID and duration. It does not expose hidden chain-of-thought.

## Attachment and request limits

- Up to 40 messages per request.
- Text message limit: 24,000 characters; combined text limit: 30,000 characters.
- Overall request body limit: 5.5 MB.
- Structured user content: up to five parts per message.
- Images: inline JPEG, PNG or WebP data URLs only; at most two images, each no larger than 1 MB.
- Remote image URLs are rejected. Vision requests are routed only to models whose configured capability list explicitly includes `vision`.
- The Android client additionally limits text/code files to 128 KB each, combined attachment context to 24,000 characters, and four total attachments per request.

## Encrypted memory

Memory payloads are encrypted client-side before upload. The Worker stores and returns ciphertext only; it cannot decrypt the personal memory. The underlying GitHub repository may expose non-sensitive file metadata, so do not put secrets in snapshot metadata. Memory routes are master-key-only.

## Error behavior

Responses use JSON errors with an HTTP status and a safe message. Common statuses:

| Status | Meaning |
|---:|---|
| 400 | Invalid request, unsupported stream mode or unsupported content |
| 401 | Missing/invalid bearer key |
| 403 | Valid scoped client key lacks permission |
| 413 | Request exceeds a size limit |
| 429 | Rate limit exceeded |
| 502 | Upstream inference provider failed |
| 503 | Provider configuration, licence gate or required rate-limit binding unavailable |
| 504 | Upstream provider timed out |

Do not expose provider secrets, raw upstream credentials or internal stack traces to clients.
