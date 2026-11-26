# Legacy Cloudflare Worker entrypoint

This directory is retained only as a compatibility entrypoint for the earlier edge-worker layout. It re-exports the single canonical implementation from [`cloudflare/src/index.js`](../src/index.js) and its tests exercise that same source. There is no second independent gateway implementation.

**Deploy the canonical service only from `cloudflare/wrangler.toml` in the `cloudflare/` directory.** Its Worker name is `wasti-nexus-gateway` and its public API URL is `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.

Do not deploy `cloudflare/worker/wrangler.toml`; it names a separate legacy Worker and is kept solely for compatibility/testing. Configuration, secrets, model routing, authentication and releases are maintained in the canonical `cloudflare/` directory.
