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
