# Configure a self-hosted NEXUS provider

The Cloudflare Worker is a protected relay, not an inference host. It does not download or run model weights, and it does not use Cloudflare Workers AI. The provider must be a server you control (for example, a workstation or GPU server running an OpenAI-compatible inference server).

## Current deployment state

The Worker source with bounded vision attachment validation is deployed. Its existing client authentication, GitHub memory secret, rate-limit binding and non-AI relay configuration were preserved. **No inference provider is currently configured in the deployed Worker**, so a healthy gateway does not mean that `/v1/models` will return usable model IDs or that chat inference is available.

## Provider requirements

- Publicly reachable HTTPS endpoint with a valid TLS certificate.
- OpenAI-compatible `POST /v1/chat/completions` and a `/v1/models`-style model ID configured in the NEXUS catalogue (the Worker exposes its configured IDs).
- Provider API key stored as a Cloudflare Worker secret. This is a server-to-server credential for your own provider, not a paid AI-vendor key.
- Model checkpoint, exact revision, licence and allowed-use terms reviewed before enabling the model.
- For images, the model and provider adapter must actually accept OpenAI-style `image_url` content with inline JPEG/PNG/WebP data URLs. Only then add `vision` to its declared capabilities.
- Enough compute, memory, request capacity and monitoring for the expected concurrency.

Do not expose a phone's Termux `llama-server` directly to the public internet. For remote access, use a properly authenticated TLS reverse proxy or private tunnel and understand the mobile network, battery and process-lifecycle limits. Termux on the same phone is a local runtime, not a reliable always-on cloud server.

## Provider catalogue

Start from [providers.example.json](../cloudflare/providers.example.json). Replace all placeholders. Example shape:

```json
{
  "providers": [
    {
      "id": "my-self-hosted-host",
      "base_url": "https://inference.example/v1",
      "api_key_env": "NEXUS_SELF_HOSTED_API_KEY",
      "models": [
        {
          "id": "my-general-model",
          "upstream_model": "actual-upstream-model-id",
          "agent": "generalist",
          "enabled": true,
          "license": "exact checkpoint licence",
          "license_status": "verified",
          "commercial_allowed": true,
          "capabilities": ["chat", "coding", "reasoning", "tools"]
        },
        {
          "id": "my-vision-model",
          "upstream_model": "actual-vision-model-id",
          "agent": "vision",
          "enabled": true,
          "license": "exact checkpoint licence",
          "license_status": "verified",
          "commercial_allowed": true,
          "capabilities": ["chat", "vision"]
        }
      ]
    }
  ]
}
```

The example is a schema illustration, not a recommendation to claim commercial rights. Set `commercial_allowed: true` and `license_status: "verified"` only after reviewing the exact model checkpoint and any usage policy. If that review is incomplete, leave the model disabled and gated.

For remote model tool calls, add the `tools` capability only when the actual upstream model and provider adapter support OpenAI-compatible function calling. The Worker accepts only the fixed NEXUS function-name allowlist. The Android app revalidates arguments and pauses for user approval before each call. Agent tools run only with one explicitly selected online model; Auto, Hybrid and offline local models do not run model-generated tools. A model catalogue entry is not proof of tool-call support; test each configured model with the app's approval flow.

## Configure Cloudflare

1. Set the Worker variable `NEXUS_PROVIDERS_JSON` to the reviewed JSON catalogue.
2. Add a Worker secret named exactly as each model provider's `api_key_env` (for the example: `NEXUS_SELF_HOSTED_API_KEY`).
3. Keep `NEXUS_API_KEY` (or scoped client keys) for app-to-gateway authentication. Never put this master key or provider secrets in Git, the APK or browser code.
4. Keep the existing `NEXUS_RATE_LIMIT` binding and `NEXUS_DEPLOYMENT_MODE=customer` setting. Do not replace or remove the GitHub memory secrets.
5. Verify `GET /healthz`, then authenticate `GET /v1/models`. Confirm only the expected enabled, approved IDs are returned.
6. Send an authenticated text request to each model. For a vision model, send a small JPEG/PNG/WebP image and confirm that the actual provider returns a grounded visual description.
7. Enable Hybrid only after at least two distinct, enabled, licence-approved model IDs are available. Test independent answers, peer review, synthesis, partial failures and request cancellation.

The Worker rejects external image URLs and unsupported image MIME types. It limits each image to 1 MB, at most two images per request, and aggregate text to 30,000 characters. Android applies stricter attachment controls before sending.

## Important distinctions

- An open-weight model is not automatically a free hosted service. Self-hosting still costs hardware, electricity, network and maintenance.
- A local Termux endpoint works for the phone's local route. It does not automatically become a reachable remote provider for the Cloudflare Worker.
- A model's licence and commercial permissions are separate from provider API terms and compute costs.
- The model catalogue is configuration, not proof that a checkpoint is installed, healthy, fast or accurate.
