# NEXUS API quick reference

## Live gateway address

Base URL (do not append `/v1`): `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

All protected requests require `Authorization: Bearer YOUR_NEXUS_API_KEY` and `Content-Type: application/json`.

The gateway Worker is deployed and its workers.dev subdomain is enabled. **No inference provider is configured yet**, so `GET /v1/models` currently returns an empty model list and chat requests cannot generate answers until a real backend is connected.

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

## Encrypted GitHub memory snapshots (orchestrator)

The FastAPI orchestrator provides a personal-memory snapshot adapter. It stores only client-encrypted AES-256-GCM payloads as files in the configured GitHub repository. The API never decrypts memory.

Configure these variables on the trusted orchestrator host:
- `NEXUS_MEMORY_REPOSITORY=owner/Wasti-AI-Agent`
- `NEXUS_GITHUB_TOKEN` — fine-grained token with Contents read/write access restricted to this repository
- `NEXUS_MEMORY_BRANCH=main` (optional)
- `NEXUS_MEMORY_PREFIX=memory/personal/encrypted` (optional)

Endpoints (all require the NEXUS bearer key):
- `GET /v1/memory/snapshots` — list snapshot IDs.
- `POST /v1/memory/snapshots` — create an immutable encrypted snapshot file.
- `GET /v1/memory/snapshots/{snapshot_id}` — retrieve an encrypted snapshot for client-side decryption.

The write body includes `snapshot_id` (UUID), timezone-aware `created_at`, `key_id`, base64url `nonce`, base64url `ciphertext`, optional `content_type` and `tags`. Batch many memory records into one encrypted snapshot before upload. The API writes under `memory/personal/encrypted/`; it does not accept a client-controlled repository path or tenant path. The configured repository must be public. GitHub exposes encrypted blobs and their metadata to everyone; the server refuses plaintext records. Keep encryption keys exclusively on trusted devices and maintain a separate encrypted recovery backup.

This initial adapter is for one personal vault, not a multi-customer identity system. Do not expose the shared NEXUS key to customers. This deployment deliberately uses a public repository: only client-encrypted ciphertext is written there, and all blob metadata is public. Keep the GitHub token server-side, encrypt before upload, and retain a local encrypted copy so offline mode works. Never store plaintext customer or personal records in this repository. GitHub commit history and API rate limits make this a versioned sync/archive layer, not a high-frequency transactional database.

## Provider configuration

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

Streaming is not implemented. The Worker is an orchestration gateway, not a GPU host or model-weight archive.
