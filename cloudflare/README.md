# WASTI NEXUS Cloudflare gateway

Deployed Worker: `wasti-nexus-gateway`

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

The Worker is deployed and its workers.dev subdomain is enabled. Its API key is stored as a Cloudflare secret. No model provider is configured yet, so the current model list is empty.

## Configure providers

Set Worker secret `NEXUS_PROVIDERS_JSON` to an object with a `providers` array. Each provider includes:
- `id`: provider identifier;
- `base_url`: trusted HTTPS OpenAI-compatible inference endpoint ending in `/v1`;
- `api_key_env`: name of another Worker secret;
- `models`: array of `id`, `agent` and `enabled` values.

Create a Worker secret for every referenced `api_key_env`. Do not store credentials in this repository. No arbitrary client-supplied provider URLs are accepted.

## Endpoints
- `GET /healthz` — public liveness.
- `GET /v1/models` — authenticated model list.
- `POST /v1/chat/completions` — single model or `model: nexus-hybrid`.
- `POST /v1/hybrid/chat/completions` — explicit Hybrid endpoint.

The Worker orchestrates configured inference services; it does not host large model weights or provide GPU inference itself. Offline Android operation must use the separate local runtime.
