# WASTI NEXUS — Termux self-hosted inference

Termux can run llama.cpp locally on the same Android phone. The server binds to `127.0.0.1` only, so it is not exposed to Wi-Fi, mobile data or the public internet. No Cloudflare AI, provider API key, R2 or hosted GPU is involved.

## Install

Use a current Termux build. Then run:

```bash
pkg update && pkg upgrade -y
pkg install -y curl python
pkg install -y llama-cpp
mkdir -p "$HOME/.config/wasti-nexus" "$HOME/models"
cp termux/env.example "$HOME/.config/wasti-nexus/env"
nano "$HOME/.config/wasti-nexus/env"
chmod 600 "$HOME/.config/wasti-nexus/env"
cp termux/nexus "$PREFIX/bin/nexus"
chmod 700 "$PREFIX/bin/nexus"
```

Copy one verified `.gguf` model into `$HOME/models/`. Do not copy a 20B+ model onto the phone expecting it to fit. Start with the smallest quantized model that has passed your device test.

## Start the local server

```bash
nexus server start "$HOME/models/your-model.gguf"
nexus server status
nexus models
nexus server token
# Paste the printed local pairing token into the app's endpoint key field.
nexus models
# Replace MODEL_ID with the model ID returned above.
nexus chat MODEL_ID "Reply in Urdu and English."
```

Stop it when finished:

```bash
nexus server stop
```

Default endpoint: `http://localhost:8088/v1`. The server requires a random local pairing token stored in Termux's private state directory; use `nexus server token` to view it. This is local authentication, not a vendor AI API key. The server uses one concurrent request, a conservative context size, bounded CPU threads and CPU-only inference by default. You can tune `NEXUS_SERVER_CTX`, `NEXUS_SERVER_THREADS` and `NEXUS_GPU_LAYERS` after measuring stability. Increasing them can cause heat, memory pressure or Android process termination.

## Automatic fallback

`nexus auto "prompt"` tries the local Termux model first. If the local server is unavailable, it may use `NEXUS_REMOTE_BASE_URL` only when you explicitly configure your own HTTPS self-hosted orchestrator and its bearer key. It never calls Cloudflare Workers AI. If both routes are unavailable, it returns an error rather than pretending the task completed.

## Android app integration

In WASTI NEXUS Settings, the self-hosted endpoint defaults to `http://localhost:8088`. This cleartext exception is restricted to the exact `localhost` hostname. Remote endpoints must use HTTPS and a strong bearer token. The app checks validated internet, Wi-Fi/mobile-data transport, metering, available RAM, thermal state and battery before selecting a route. Offline mode never falls through to an internet provider; it may use the local Termux loopback server if it is running.

Termux and the Android app have separate sandboxes. Put the model in Termux's own `$HOME/models` directory for llama.cpp. Do not expect Termux to read the Android app's private GGUF files automatically.

## Boundaries

- Self-hosting avoids per-token provider fees, but not electricity, device wear, hardware limits, download bandwidth or maintenance.
- A phone is not an unlimited GPU server. Android may stop background processes; keep Termux open and disable battery optimization only if you understand the battery impact.
- Loopback-only is intentional. Do not change the server host to `0.0.0.0` or expose it through port forwarding without a separate authenticated TLS reverse proxy, firewall and rate limits.
- Termux:API permissions are separate and limited to documented Android APIs. No root bypass or unrestricted access to other apps is implied.
- This CLI does not provide arbitrary shell execution through the NEXUS public API.

## Commands

- `nexus server install` — install the Termux `llama-cpp` package.
- `nexus server start MODEL.gguf` — start a loopback-only local server.
- `nexus server status` / `stop` — inspect or stop it.
- `nexus health`, `models`, `chat`, `auto` — test and use the local-first route.
- `nexus hybrid` — optional remote NEXUS orchestrator only; requires your own self-hosted HTTPS endpoint.

