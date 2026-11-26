# WASTI NEXUS Cloudflare Gateway

This Worker is the edge API facade for future products. It exposes an OpenAI-compatible contract and routes requests to configured HTTPS model-provider endpoints. It does not host model weights or provide free inference by itself.

## Routes

- `GET /healthz` — public liveness check.
- `GET /v1/models` — authenticated model catalog.
- `POST /v1/chat/completions` — single model or Hybrid collective answer. Use `model: "nexus-hybrid"` or `nexus.mode: "hybrid"`.

## Required configuration

Set Worker secret `NEXUS_API_KEY` to a random value of at least 32 characters.

Set Worker variable `NEXUS_PROVIDERS_JSON` to a JSON object. Example only:

```json
{
  "providers": [
    {
      "id": "your-provider",
      "base_url": "https://inference.example.com/v1",
      "api_key_env": "YOUR_PROVIDER_API_KEY",
      "models": [
        {
          "id": "qwen",
          "upstream_model": "provider-specific-model-id",
          "agent": "reasoning",
          "enabled": true,
          "commercial_allowed": true
        },
        {
          "id": "llama",
          "upstream_model": "another-provider-model-id",
          "agent": "generalist",
          "enabled": true,
          "commercial_allowed": true
        }
      ]
    }
  ]
}
```

For every `api_key_env`, create a separate Worker secret with that exact name. Never put provider API key values in `NEXUS_PROVIDERS_JSON`, `wrangler.toml`, GitHub or client code. Provider base URLs must use HTTPS and end in `/v1`.

Optional variables:
- `MAX_HYBRID_AGENTS` (default 12; max 12)
- `REQUEST_TIMEOUT_MS` (default 45000; max 60000)
- `ALLOWED_ORIGINS` (comma-separated exact browser origins; leave empty for server-to-server use)

## Deploy

Install Wrangler, authenticate to the intended Cloudflare account, then run:

```bash
npx wrangler secret put NEXUS_API_KEY
npx wrangler secret put YOUR_PROVIDER_API_KEY
npx wrangler deploy
```

The Worker must have its `workers.dev` subdomain enabled or a custom domain/route attached before it has a public URL. After deployment, verify the health route, authenticated model catalog, single-model request and Hybrid request.

## Current deployment

Gateway base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.

The Worker is deployed, its `workers.dev` subdomain is enabled, and the deployed source was byte-for-byte compared with this repository. The `NEXUS_API_KEY` secret binding is configured. No provider models are configured yet, so the endpoint is provisioned but cannot generate answers until licensed HTTPS model backends are added and tested.
