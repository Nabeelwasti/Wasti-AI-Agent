# WASTI NEXUS Termux companion

The Termux CLI is a companion client for the same WASTI NEXUS gateway. It does not contain model weights and does not bypass Android app permissions.

## Install

```bash
pkg update
pkg install curl python
mkdir -p "$HOME/.config/wasti-nexus"
cp termux/env.example "$HOME/.config/wasti-nexus/env"
nano "$HOME/.config/wasti-nexus/env"
chmod 600 "$HOME/.config/wasti-nexus/env"
cp termux/nexus "$PREFIX/bin/nexus"
chmod 700 "$PREFIX/bin/nexus"
```

Set `NEXUS_API_KEY` in the private env file. This is the gateway access key, **not** a model-provider API key. Never commit the env file or paste its value into chat.

## Commands

- `nexus health` — public gateway liveness check.
- `nexus models` — list models enabled for this gateway.
- `nexus chat MODEL "your prompt"` — use one selected online brain.
- `nexus hybrid "your prompt"` — run the configured multi-agent review/synthesis workflow.
- `nexus open "your prompt"` — open the Android app with the prompt prefilled (the app must be installed).

The `chat` and `hybrid` commands require internet and an enabled gateway model. `open` uses Android's text-share intent and does not execute a response inside the shell. Direct shell access to the Android app's private GGUF files or local inference engine is intentionally not granted. A future authenticated localhost bridge/foreground-service module is required for full bidirectional Termux-to-device inference.

## Gateway

Default base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.

Workers AI models are invoked through Cloudflare's native `AI` binding. No vendor API key is needed for that path. The gateway bearer key protects your endpoint. Workers AI has a limited free daily allocation; usage above the allowance or models requiring paid access can incur charges or fail. See Cloudflare's current [Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/).
