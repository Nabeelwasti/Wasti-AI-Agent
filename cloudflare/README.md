# WASTI NEXUS Cloudflare edge and web app

Worker name: `wasti-nexus-gateway`

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

Cloudflare's role is limited to hosting the online web app's static files and forwarding the online API. NEXUS does not use Cloudflare KV, D1, R2, Durable Objects, Vectorize or other Cloudflare storage for memory, datasets or model weights.

## Static web app

- Static files live in `cloudflare/public/`.
- Wrangler serves them through Workers Static Assets.
- The online console asks the user for an API key in the browser and does not persist it.
- The web UI must never contain a master key or GitHub write credential.
- Same-origin requests use `/v1` on this Worker.

## API endpoints

- `GET /healthz` — public liveness.
- `GET /v1/models` — authenticated model list.
- `POST /v1/chat/completions` — single model or `model: nexus-hybrid`.
- `POST /v1/hybrid/chat/completions` — explicit Hybrid endpoint.

## Provider setup

Set Worker secret `NEXUS_PROVIDERS_JSON` to an object with a `providers` array. Each provider includes:
- `id`: provider identifier;
- `base_url`: trusted HTTPS OpenAI-compatible inference endpoint ending in `/v1`;
- `api_key_env`: name of another Worker secret;
- `models`: array of `id`, `agent` and `enabled` values.

Create a Worker secret for every referenced `api_key_env`. Do not store credentials in this repository. No arbitrary client-supplied provider URLs are accepted.

The Worker orchestrates configured inference services; it does not host large model weights or provide GPU inference itself. Online requests will return no usable model until at least one lawful inference provider is configured. Offline Android operation uses the separate native local runtime.
