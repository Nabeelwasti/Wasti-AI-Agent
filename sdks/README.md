# NEXUS SDKs

The gateway exposes one API for all apps. These thin clients make integration easier.

Live base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`

Important: do not ship the gateway master API key in public browser code or a customer-facing mobile APK. Call NEXUS from your own authenticated backend and keep the key in server-side secrets. Issue customer-specific credentials only after tenant authentication and quotas are implemented.
