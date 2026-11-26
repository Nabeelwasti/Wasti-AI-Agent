# Deployment guide

## Current architecture

The orchestrator is a Python ASGI service. It needs a compute host that can run Python and reach configured model endpoints. Cloudflare Worker in `cloudflare/worker` is an optional HTTPS edge proxy; it is not the inference host.

## Deploy orchestrator

1. Provision a Python 3.11+ host or container platform.
2. Install `orchestrator/requirements.txt`.
3. Set a high-entropy `NEXUS_API_KEY` in the host's secret manager.
4. Set `NEXUS_AGENTS_JSON` to only the model endpoints you have tested.
5. Set `NEXUS_SYNTHESIS_AGENT` to a configured agent.
6. Run behind HTTPS with a reverse proxy and request/rate limits.
7. Verify `/healthz`, authenticated `/v1/models`, single-agent chat and Hybrid chat.
8. Record provider data retention and processing terms before sending personal/customer data.

## Deploy optional Cloudflare edge

1. Configure `ORIGIN_BASE_URL` as a Worker variable pointing to the orchestrator's HTTPS origin.
2. Deploy `cloudflare/worker` with Wrangler.
3. Enable the Worker `workers.dev` subdomain or attach a domain you control.
4. Verify `/healthz` and authenticated `/v1/models` through the edge URL.
5. Do not publish the endpoint as production-ready until origin authentication, tenant authorization, rate limiting and monitoring are tested.

Official deployment references:
- https://developers.cloudflare.com/workers/wrangler/commands/
- https://developers.cloudflare.com/workers/wrangler/configuration/

## Why there is not yet a live endpoint

A stable public API needs a running orchestrator origin and at least one configured model backend. A Cloudflare proxy with no origin would only return errors. This repository therefore contains deployment code and a stable API contract, but does not invent a live URL.
