# NEXUS API quick reference

## Live gateway address

Base URL (do not append `/v1`): `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

All protected requests require `Authorization: Bearer YOUR_NEXUS_API_KEY` and `Content-Type: application/json`.

The gateway Worker is deployed and its workers.dev subdomain is enabled. It has a native Cloudflare Workers AI binding and includes two first-party model IDs: `nexus-llama-3.2-1b` and `nexus-llama-3.2-3b`. This path does not need a third-party model API key. The separate `NEXUS_API_KEY` is only the access credential for your gateway.

## Health
`GET /healthz` — public liveness response; does not expose provider secrets.

## List configured brains
`GET /v1/models` — authenticated; returns only enabled, configured providers.

## Single brain
`POST /v1/chat/completions`

```json
{
  "model": "qwen",
  "messages": [{"role":"user","content":"Explain vector search simply."}],
  "stream": false
}
```

## Hybrid collective answer
`POST /v1/chat/completions`

```json
{
  "model": "nexus-hybrid",
  "messages": [{"role":"user","content":"Evaluate this business idea and produce a practical plan."}],
  "nexus": {"mode":"hybrid","agents":["qwen","llama","gemma"],"review_rounds":1}
}
```

The Worker also accepts `POST /v1/hybrid/chat/completions` with an optional `models` array. Hybrid mode runs independent analysis, peer critique and synthesis. The response lists only actual successful participants, failures, independent answers and peer reviews.

## Encrypted GitHub memory snapshots

The deployed Cloudflare Worker exposes GitHub-backed snapshot routes. Cloudflare is only the authenticated API gateway; the Worker sends encrypted files directly to the GitHub Contents API. No Cloudflare storage product is used.

The owner has added the Worker secret `NEXUS_GITHUB_TOKEN`; its presence is verified by secret name/type only (the value is never read). Configure these Worker settings:
- `NEXUS_MEMORY_REPOSITORY=Nabeelwasti/Wasti-AI-Agent`
- `NEXUS_MEMORY_BRANCH=main`
- `NEXUS_MEMORY_PREFIX=memory/personal/encrypted`
- `NEXUS_GITHUB_TOKEN` — configured as a Worker secret. Use a fine-grained token limited to Contents read/write for this repository.

The repository must remain public in the current deployment mode. Every memory request checks its visibility. The Worker accepts only an AES-256-GCM envelope with a 12-byte nonce and authenticated ciphertext; it never receives plaintext memory. GitHub exposes the encrypted files and all metadata publicly. Never commit plaintext personal/customer data, encryption keys, API keys or GitHub tokens.

Endpoints (all require the NEXUS bearer key):
- `GET /v1/memory/snapshots?limit=100` — list up to 100 newest encrypted snapshot IDs (newest first). Timestamp-reversed filenames keep the newest entries inside GitHub's first 1,000 directory items.
- `POST /v1/memory/snapshots` — create an immutable encrypted snapshot.
- `GET /v1/memory/snapshots/{snapshot_id}` — retrieve ciphertext for device-side decryption. The Android app can restore the newest snapshot on the same device; Android Keystore keys are device-bound, so cross-device recovery needs a separate recovery-key feature.

Example write body:
```json
{
  "snapshot_id": "123e4567-e89b-42d3-a456-426614174000",
  "created_at": "2026-11-26T12:00:00+05:00",
  "key_id": "pbkdf2-sha256-600k-v1",
  "kdf": "PBKDF2-HMAC-SHA256",
  "iterations": 600000,
  "salt": "base64url-encoded-16-byte-salt",
  "nonce": "base64url-encoded-12-byte-nonce",
  "ciphertext": "base64url-encoded-AES-256-GCM-ciphertext-and-tag",
  "content_type": "conversation-summary",
  "tags": ["nexus", "project"]
}
```

The Android client derives an AES-256 key from a user-controlled recovery passphrase using PBKDF2-HMAC-SHA256 (600,000 iterations, 16-byte random salt), then encrypts with AES-256-GCM (12-byte random nonce). It retains an encrypted local copy for offline use and never sends plaintext or the passphrase to this endpoint. The same passphrase can decrypt snapshots on another device; losing it makes snapshots unrecoverable. The current API is a single personal vault, not a multi-tenant customer memory service. Keep the GitHub token in Worker secrets only. Batch many records into one encrypted snapshot because GitHub is a versioned sync/archive layer, not a transactional database; respect API rate limits and repository history growth. The recovery passphrase is user-controlled and portable; NEXUS never uploads or stores it.

## First-party Workers AI (no vendor API key)\n\nThe Worker binds Cloudflare Workers AI as `env.AI`. The built-in catalogue currently exposes Llama 3.2 1B and 3B. The gateway calls the binding directly; no OpenRouter, Gemini, OpenAI or other vendor key is required for these models. Cloudflare's Workers AI free allocation is limited (currently 10,000 Neurons/day on the Free plan); requests can fail after quota exhaustion, and some models require paid access. Check current [Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/) before relying on it for customer workloads. The Llama 3.2 Community License has use conditions; review the exact licence before commercial release.\n\n## Optional external provider configuration

Set the Cloudflare Worker secret `NEXUS_PROVIDERS_JSON` to this shape (replace placeholders):

```json
{
  "providers": [
    {
      "id": "my-inference-host",
      "base_url": "https://YOUR-INFERENCE-HOST/v1",
      "api_key_env": "NEXUS_PROVIDER_KEY",
      "models": [
        {"id":"actual-upstream-model-id","agent":"reasoning","enabled":true}
      ]
    }
  ]
}
```

Then create a Worker secret named `NEXUS_PROVIDER_KEY` with the provider's credential. The upstream must implement OpenAI-compatible `POST /chat/completions` and use HTTPS. Never place credentials in Git.

## Future product integration

Use the same base URL in your backend services. For customer-facing web/mobile apps, authenticate each customer on your own backend and keep the NEXUS master key server-side. Do not embed the master key in public frontend code or ship one shared key to every customer.

Streaming is not implemented. The Worker is a gateway and inference client for Cloudflare Workers AI; it is not a general-purpose GPU host or model-weight archive.
