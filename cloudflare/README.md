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
- `GET /v1/memory/snapshots` — list encrypted snapshot IDs.
- `POST /v1/memory/snapshots` — append one immutable client-encrypted snapshot to GitHub.
- `GET /v1/memory/snapshots/{uuid}` — retrieve a ciphertext snapshot for device-side decryption.

## GitHub memory integration

The Worker writes encrypted memory files directly through the GitHub Contents API. It does not use Cloudflare storage. The configured repository must remain public in this deployment mode; the Worker verifies visibility before every operation. Only AES-256-GCM ciphertext and non-sensitive metadata are accepted. GitHub exposes those ciphertext files and metadata publicly, so plaintext personal/customer information and encryption keys must never be committed.

Configure the Worker variables in `wrangler.toml` (`NEXUS_MEMORY_REPOSITORY`, branch and prefix), then add a fine-grained GitHub token as a Worker secret named `NEXUS_GITHUB_TOKEN`. Give it Contents read/write access only to `Nabeelwasti/Wasti-AI-Agent`. Never put the token in the repository or client app. Without this secret, memory routes return HTTP 503 and do not write anything.

GitHub is a versioned sync/archive layer, not a transactional database. Batch memory into snapshots, keep local encrypted copies for offline access, and account for GitHub API rate limits and public visibility. The initial endpoint is a single personal vault, not a multi-tenant customer memory service.

## Provider setup

Set Worker secret `NEXUS_PROVIDERS_JSON` to an object with a `providers` array. Each provider includes:
- `id`: provider identifier;
- `base_url`: trusted HTTPS OpenAI-compatible inference endpoint ending in `/v1`;
- `api_key_env`: name of another Worker secret;
- `models`: array of `id`, optional `upstream_model`, `agent`, `enabled` and `commercial_allowed` values.

The Worker defaults to customer-safe mode: a model is hidden and blocked unless `commercial_allowed: true` is explicitly set after reviewing its exact licence. For private personal testing only, set `NEXUS_DEPLOYMENT_MODE=personal`; this does not grant redistribution or commercial rights.

Create a Worker secret for every referenced `api_key_env`. Do not store credentials in this repository. No arbitrary client-supplied provider URLs are accepted.

The Worker orchestrates configured inference services; it does not host large model weights or provide GPU inference itself. Online requests will return no usable model until at least one lawful inference provider is configured. Offline Android operation uses the separate native local runtime.
