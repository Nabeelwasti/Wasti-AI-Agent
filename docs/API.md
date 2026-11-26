# WASTI NEXUS API reference

## Base URL

`https://wasti-nexus-gateway.wastinabeel99.workers.dev`

Do not append `/v1` to the base URL. Protected endpoints require:

```http
Authorization: Bearer YOUR_NEXUS_API_KEY
Content-Type: application/json
```

The bearer key authenticates access to the NEXUS gateway. It is **not** a model-provider API key. The built-in Workers AI model is invoked through the Cloudflare AI binding without a third-party vendor token.

## Live endpoints

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| GET | `/healthz` | Public | Liveness and safe model catalogue summary |
| GET | `/v1/models` | Required | List configured, enabled models |
| POST | `/v1/chat/completions` | Required | Single-model chat or Hybrid route |
| POST | `/v1/hybrid/chat/completions` | Required | Explicit multi-model Hybrid route |
| GET | `/v1/memory/snapshots` | Required | List encrypted personal-memory snapshots |
| POST | `/v1/memory/snapshots` | Required | Save one client-encrypted snapshot |
| GET | `/v1/memory/snapshots/{snapshot_id}` | Required | Retrieve encrypted snapshot for client-side decryption |

Streaming is not implemented. Send `"stream": false`.

## Current first-party model

The deployed Worker exposes one configured first-party model:

- Public NEXUS model ID: `nexus-gemma-4-26b-a4b`
- Cloudflare Workers AI model ID: `@cf/google/gemma-4-26b-a4b-it`
- Licence metadata: Apache-2.0
- Inference binding: `env.AI`

Example:

```bash
curl --fail-with-body \
  -H "Authorization: Bearer $NEXUS_API_KEY" \
  -H "Content-Type: application/json" \
  https://wasti-nexus-gateway.wastinabeel99.workers.dev/v1/chat/completions \
  -d '{
    "model": "nexus-gemma-4-26b-a4b",
    "messages": [
      {"role": "user", "content": "Explain vector search simply."}
    ],
    "stream": false,
    "max_tokens": 512
  }'
```

Workers AI Free currently includes 10,000 Neurons per day, not unlimited inference. Actual response counts depend on model and request size; model access, capacity and pricing can change. Check [official Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/).

## List configured models

```bash
curl --fail-with-body \
  -H "Authorization: Bearer $NEXUS_API_KEY" \
  https://wasti-nexus-gateway.wastinabeel99.workers.dev/v1/models
```

The endpoint returns only currently configured and enabled model IDs. The twelve-family local model registry is not twelve live cloud endpoints. The current online catalogue has one tested first-party model.

## Hybrid orchestration

Hybrid mode is deliberately disabled unless at least **two distinct model IDs** are configured. It must not present repeated calls to one model as a multi-agent consensus.

After a second licensed, tested model is configured, use:

```json
{
  "model": "nexus-hybrid",
  "messages": [
    {"role": "user", "content": "Evaluate this plan from different perspectives."}
  ],
  "stream": false,
  "nexus": {
    "mode": "hybrid",
    "agents": ["model-one", "model-two"],
    "review_rounds": 1
  }
}
```

The response reports actual participants and failures. Hybrid can use independent analysis, peer review and synthesis, but it consumes multiple inference calls and therefore more quota. Do not infer that Hybrid is available from the route existing; it remains unavailable until the model catalogue contains at least two distinct configured IDs.

## Authentication and client keys

Keep the master `NEXUS_API_KEY` private. Do not embed it in a public Android APK, web bundle or customer source code.

For trusted server-side product integrations, generate a unique 256-bit client key per application:

```bash
node cloudflare/scripts/create-client-key.mjs --id my-product --scopes models,chat
```

The script prints the raw key once and a SHA-256 registry entry. Store the raw key only in a server-side secret manager. Add the registry entry to the Cloudflare Worker secret `NEXUS_CLIENT_KEYS_JSON`, preserving any existing entries. Client keys may be scoped to `models` and `chat`; personal memory remains master-key only.

Public customer apps must authenticate their own users and call NEXUS through a trusted backend or short-lived-token broker. A long-lived shared key in an APK or browser is extractable and is not customer authentication.

The Worker has a rate-limit binding configured at 60 requests per 60 seconds per key per Cloudflare location. Cloudflare counters are approximate and location-local, not a strict global quota or billing meter.

## Encrypted personal-memory snapshots

The Worker writes encrypted snapshots to the public GitHub repository configured by `NEXUS_MEMORY_REPOSITORY`, `NEXUS_MEMORY_BRANCH` and `NEXUS_MEMORY_PREFIX`, using the Worker secret `NEXUS_GITHUB_TOKEN`. Binding names are present in the deployed Worker settings; live end-to-end memory recovery has not been device-certified.

The Android client derives an AES-256 key from a user-controlled recovery passphrase using PBKDF2-HMAC-SHA256 (600,000 iterations, 16-byte random salt), then encrypts with AES-256-GCM (12-byte random nonce). The passphrase and plaintext are not sent to the Worker. The same passphrase can be used to decrypt a snapshot on another device. GitHub exposes ciphertext, filenames, timestamps and commit metadata publicly; never store plaintext personal/customer data or encryption keys there.

This is one personal vault, not a tenant-isolated customer memory service. Do not use these routes for customer memory until per-tenant authorization, isolation, retention and deletion controls exist.

## Optional external providers

The Worker can route to configured HTTPS OpenAI-compatible providers through the secret `NEXUS_PROVIDERS_JSON`. Provider credentials are separate Worker secrets referenced by `api_key_env`. The built-in Gemma route does not need those credentials. Do not configure a provider unless its model availability, licence, data-retention terms and commercial rights have been reviewed.

## Online/offline client behaviour

The Android app defaults to Auto mode:

1. Try the configured online NEXUS gateway.
2. On network errors, missing online credentials, HTTP 408/429 or transient 5xx failures, load/use the selected installed GGUF model if one is available.
3. Keep local-fallback turns out of subsequent cloud conversation history.
4. Do not hide authentication or permission errors behind local fallback.

Offline inference runs in the Android process through the native GGUF runtime. It does not depend on the Worker or Termux.

## Production security reminders

- Never expose the master API key or GitHub write token in a public client.
- The GitHub memory vault is public ciphertext, not a private database.
- The current model archive is not provisioned; Workers AI is not a custom GGUF file host.
- The app supports manual GGUF import but does not yet have a resumable in-app catalogue downloader or registry SHA-256 verification for manual imports.
- Do not advertise the service as production-ready for multiple customers until authenticated route smoke tests, customer-key setup, strict quota design, tenant isolation, permanent Android signing and physical-device offline tests have passed.
