# Deployment guide

## Canonical production components

- **Android:** native Kotlin/Jetpack Compose app in `android/`; Arm AI Chat wraps llama.cpp for local GGUF inference.
- **Online gateway:** Cloudflare Worker `wasti-nexus-gateway`, source of record `cloudflare/src/index.js`, Wrangler configuration `cloudflare/wrangler.toml`.
- **Universal API URL:** `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.
- **Model registry:** `models/registry.json`; model weights are not committed to Git.
- **Owner model archive:** the owner's Google Drive subscription, in the dedicated `WASTI-NEXUS-MODEL-WORKSPACE` folder.
- **Python orchestrator:** `orchestrator/` is a separately deployable service for deployments that need its configured multi-provider routing. It is not the current Cloudflare Worker runtime and is not required for built-in local inference.

## Model storage and inference are separate

Google Drive is the selected model-file archive. **Do not provision or use Cloudflare R2, Cloudflare storage, GitHub Releases or Git LFS for NEXUS model weights.** The Cloudflare Worker is an optional online inference gateway only. It does not host custom GGUF files.

The Android app must download selected model files from the owner's Google Drive through official Google Drive API access, verify the exact byte length and SHA-256, and then run the installed model locally. Once installed, offline chat must not require Google sign-in, an AI API key, an internet connection or Cloudflare Workers AI.

Private Drive access uses Android's system folder picker (Storage Access Framework). The owner selects one Google Drive folder and Android grants NEXUS scoped read access to that folder only. No AI API key, Google API key, OAuth client or service-account credential is required. See [Google Drive model workspace](GOOGLE_DRIVE_MODEL_WORKSPACE.md).

## Current online inference

The Worker is a protected relay to owner-controlled self-hosted providers. Cloudflare Workers AI is disabled. The Worker exposes:

- Public health: `GET /healthz`
- Authenticated model list: `GET /v1/models`
- Authenticated chat: `POST /v1/chat/completions`
- Authenticated hybrid route: `POST /v1/hybrid/chat/completions`
- Encrypted personal-memory snapshot routes under `/v1/memory/snapshots`

The model catalogue is built from `NEXUS_PROVIDERS_JSON` and includes only enabled, configured, licence-approved self-hosted models. The Worker is not an inference host and does not make models free; provider hardware, bandwidth, licence and operating costs remain the owner's responsibility.

The Worker health and protected-route smoke tests verify the relay-only deployment policy. The live authenticated chat path still requires a configured `NEXUS_API_KEY` secret and at least one enabled provider in `NEXUS_PROVIDERS_JSON`. Do not mark inference availability as verified until a real authenticated provider request succeeds.

## API key policy

- Local model download from an authenticated Google Drive account uses Google OAuth, not a NEXUS AI API key.
- Local inference requires no API key.
- The optional hosted gateway currently uses bearer authentication. Its key is only for remote gateway access and is not required for offline mode.
- Never embed the Cloudflare Worker master key in the APK, public web bundle or source code.
- Remote personal-memory sync is separate from local encrypted memory and currently has additional gateway authorization requirements. Local encrypted memory must remain available without any gateway credential.

## Cloudflare deployment

Deploy the Worker through the configured Cloudflare account and verify the deployed source, AI binding, secret bindings and public health endpoint after every release. Cloudflare is used for the optional inference gateway only; no model weights or personal Drive files are to be stored there.

## Android release

- CI builds the debug APK and runs Android JVM unit tests.
- CI also assembles a release APK with a disposable test keystore and verifies the signature. This validates the Gradle signing configuration only.
- For a real production release, use the permanent owner-controlled keystore and the four secrets listed in [RELEASE.md](RELEASE.md), then run `.github/workflows/release.yml`.
- Install the signed APK on the target phone. Test model import, GGUF load, airplane-mode generation, online inference, automatic fallback, memory restore, app upgrade, RAM use, thermal behaviour and battery use. CI cannot certify these physical-device behaviours.

## Model acquisition and offline mode

Termux is not required to run the Android app or execute local inference. The app supports manual GGUF import, metadata validation, one active local model at a time, automatic selected-model loading, load/unload and removal. Chat accepts bounded text/code attachments. JPEG/PNG/WebP images are routed only to a configured online provider whose model declares `vision`; the on-device runtime remains text-only. Auto mode is local-first; online inference is an explicit optional route. Offline mode never sends inference prompts to the gateway.

The first phone-sized candidate is Qwen2.5-0.5B-Instruct Q4_K_M. It is pinned with an upstream SHA-256, but has not been downloaded to or tested on the target phone. The app now includes the scoped Drive folder picker and verified Qwen copy path; the remaining eleven models are still pending exact artifact and licence validation. The Drive transfer has not yet been certified on the target phone. See [MODEL_DISTRIBUTION.md](MODEL_DISTRIBUTION.md), [Google Drive model workspace](GOOGLE_DRIVE_MODEL_WORKSPACE.md) and [the model catalogue](../models/README.md).

## Production readiness

Do not advertise WASTI NEXUS as a fully production-ready multi-customer service until the remaining gates in [STATUS.md](STATUS.md) are closed with evidence. A successful build, a healthy Worker and a populated model catalogue are not substitutes for private Drive OAuth, verified model assets, signed owner-controlled releases, physical-device offline inference and customer security review.
