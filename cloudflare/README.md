# WASTI NEXUS Cloudflare gateway

This Worker provides a provider-neutral API and routes to configured HTTPS OpenAI-compatible inference endpoints. It does not host large model weights.

Set Worker secrets:
- `NEXUS_API_KEY`: random secret of at least 32 characters.
- `NEXUS_PROVIDERS_JSON`: JSON object with a `providers` array. Each provider includes `id`, `base_url`, `api_key_env`, and `models` with `id`, `agent`, `enabled`.
- One secret per referenced `api_key_env`.

Set `ALLOWED_ORIGINS` to exact browser origins. Deploy with Wrangler. No model is enabled until a real provider is configured. Endpoints: `/healthz`, `/v1/models`, `/v1/chat/completions`, `/v1/hybrid/chat/completions`.
