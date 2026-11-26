# WASTI NEXUS Cloudflare edge and web app

Worker name: `wasti-nexus-gateway`

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

Cloudflare serves the web console, authenticated NEXUS API gateway and first-party Workers AI inference through the `AI` binding. Workers AI hosts the selected model; it does not accept arbitrary custom GGUF uploads. NEXUS does not currently use KV, D1, R2, Durable Objects or Vectorize for memory or model weights. A separate R2 bucket is a possible future archive/download origin, but no NEXUS R2 bucket is provisioned.

## Static web app

- The canonical web console source is `cloudflare/public/index.html`.
- The deployment process embeds that HTML into the Worker response so the online console is served from the same workers.dev origin.
- No Cloudflare KV, D1, R2, Durable Objects or Workers Static Assets storage is used; the Worker code itself serves the console.
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

Snapshot filenames use a reverse-timestamp prefix so GitHub's lexicographic directory listing returns the newest snapshots first. The list endpoint returns at most 100; the current GitHub Contents API directory listing is bounded to the first 1,000 entries.

## GitHub memory integration

The Worker writes encrypted memory files directly through the GitHub Contents API. It does not use Cloudflare storage. The configured repository must remain public in this deployment mode; the Worker verifies visibility before every operation. Only PBKDF2-HMAC-SHA256 (600,000 iterations, 16-byte salt) plus AES-256-GCM ciphertext (12-byte nonce) and non-sensitive metadata are accepted. The user recovery passphrase never reaches the Worker. GitHub exposes those ciphertext files and metadata publicly, so plaintext personal/customer information and encryption keys must never be committed.

Configure the Worker variables in `wrangler.toml` (`NEXUS_MEMORY_REPOSITORY`, branch and prefix), then add a fine-grained GitHub token as a Worker secret named `NEXUS_GITHUB_TOKEN` (Cloudflare dashboard → Worker → Settings → Variables and Secrets, or `npx wrangler secret put NEXUS_GITHUB_TOKEN`). Give it Contents read/write access only to `Nabeelwasti/Wasti-AI-Agent`. Never put the token in the repository or client app. Without this secret, memory routes return HTTP 503 and do not write anything.

GitHub is a versioned sync/archive layer, not a transactional database. Batch memory into snapshots, keep local encrypted copies for offline access, and account for GitHub API rate limits and public visibility. The initial endpoint is a single personal vault, not a multi-tenant customer memory service.

## Per-application API keys

Do not ship the master `NEXUS_API_KEY` in a customer app, APK, website bundle or public source repository. For each product integration, create a separate client key with least-privilege scopes:

```bash
node cloudflare/scripts/create-client-key.mjs --id my-product --scopes models,chat
```

The script prints a random 256-bit client key once and a SHA-256 registry entry. Store the raw key only in a server-side secret manager or a private owner-controlled environment. Do not embed it in a distributed APK, browser bundle or public source; client-side bearer keys can be extracted. Public customer apps need user authentication and a backend/token-exchange layer. Add the printed registry entry to the JSON value of the Cloudflare Worker secret `NEXUS_CLIENT_KEYS_JSON`, preserving existing entries. Client keys can access only the `models` and `chat` scopes; the personal GitHub memory endpoints remain master-key only. Revoke a client by removing its entry or setting `enabled: false` in the secret, then update the secret.

The Worker stores only SHA-256 hashes of client keys in `NEXUS_CLIENT_KEYS_JSON`; raw client keys are never committed. The customer deployment also requires the `NEXUS_RATE_LIMIT` binding (60 requests per 60 seconds per key, per Cloudflare location). Cloudflare documents these counters as approximate and local to each location; they are a basic abuse-control layer, not a strict global quota. Use Durable Objects or a dedicated metering service for strict cross-location quotas. This provides per-app separation and revocation, but it is not a substitute for customer identity, tenant isolation, quotas or distributed rate limiting. Do not use the personal-memory API as a multi-tenant customer data store.

## Provider setup

Set Worker secret `NEXUS_PROVIDERS_JSON` to an object with a `providers` array. Each provider includes:
- `id`: provider identifier;
- `base_url`: trusted HTTPS OpenAI-compatible inference endpoint ending in `/v1`;
- `api_key_env`: name of another Worker secret;
- `models`: array of `id`, optional `upstream_model`, `agent`, `enabled` and `commercial_allowed` values.

The Worker defaults to customer-safe mode: a model is hidden and blocked unless `commercial_allowed: true` is explicitly set after reviewing its exact licence. Hybrid mode requires at least two distinct configured model IDs; the two first-party models currently satisfy that requirement. For private personal testing only, set `NEXUS_DEPLOYMENT_MODE=personal`; this does not grant redistribution or commercial rights.

Create a Worker secret for every referenced `api_key_env`. Do not store credentials in this repository. No arbitrary client-supplied provider URLs are accepted.

The Worker exposes two currently configured first-party Cloudflare Workers AI models through the `AI` binding: `nexus-gemma-4-26b-a4b` backed by `@cf/google/gemma-4-26b-a4b-it` (Gemma 4, Apache-2.0), and `nexus-qwen-3.8-27b` backed by `@cf/qwen/qwen3.8-27b` (Qwen 3.8 27B, Apache-2.0). No third-party provider API key is needed for this route; the NEXUS bearer key still authenticates access to the gateway. Cloudflare inference is metered, not unlimited free compute. Offline Android operation remains separate and uses a locally imported GGUF model.

## Online inference providers

The gateway accepts OpenAI-compatible providers through `NEXUS_PROVIDERS_JSON`. The example `providers.example.json` demonstrates OpenRouter's free-model router. To enable it:

1. Create an OpenRouter account and API key yourself.
2. Add `NEXUS_OPENROUTER_API_KEY` as a Worker secret (`npx wrangler secret put NEXUS_OPENROUTER_API_KEY`).
3. Add the JSON from `providers.example.json` as the Worker secret/config value `NEXUS_PROVIDERS_JSON` (`npx wrangler secret put NEXUS_PROVIDERS_JSON`).
4. Keep `NEXUS_DEPLOYMENT_MODE=customer` unless the selected model and upstream provider terms have been reviewed for customer use. The example deliberately sets `commercial_allowed: false`, so it is hidden in customer mode.

OpenRouter's `openrouter/free` router selects from its currently available free models; it does **not** guarantee a specific one of the twelve registered model families. Free-model IDs and availability change. The example is a personal experimentation path, not a promise of free production capacity. Never put provider keys in GitHub or frontend code.
