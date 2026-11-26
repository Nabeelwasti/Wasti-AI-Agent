# NEXUS API quick reference

Base URL: `https://YOUR-NEXUS-HOST/v1`

All protected requests require `Authorization: Bearer YOUR_API_KEY`.

## List available brains

`GET /v1/models`

Returns configured model IDs and the virtual `nexus-hybrid` model. Only configured upstreams appear.

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

```json
{
  "model": "nexus-hybrid",
  "messages": [{"role":"user","content":"Evaluate this business idea and produce a practical plan."}],
  "stream": false,
  "nexus": {
    "mode": "hybrid",
    "agents": ["qwen", "llama", "gemma"],
    "synthesis_agent": "qwen",
    "review_rounds": 0
  }
}
```

The final answer is synthesized by the configured synthesis agent. The response's `nexus.participating_agents` lists actual successful contributors; `failed_agents` lists failures. `review_rounds` is reserved but not implemented in the current foundation and must remain zero until implemented.

## Example cURL

```bash
curl https://YOUR-NEXUS-HOST/v1/chat/completions \
  -H "Authorization: Bearer $NEXUS_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"nexus-hybrid","messages":[{"role":"user","content":"Hello"}],"nexus":{"mode":"hybrid","agents":["qwen","llama"],"synthesis_agent":"qwen"}}'
```

## Integration notes

- Use the same API base URL from all future products.
- Issue separate API keys per app/customer; do not embed a master key in a public mobile/web client.
- For customer-facing products, add user authentication and server-side tenant authorization before launch.
- Streaming is not implemented in the current version.
