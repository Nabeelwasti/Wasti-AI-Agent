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

Private Drive access requires Google account OAuth authorization. This is a Google storage permission, not an AI API key. The Android app must request only the necessary read scope, protect short-lived access tokens, and never embed a long-lived refresh token or service-account private key in the APK. See [Google Drive model workspace](GOOGLE_DRIVE_MODEL_WORKSPACE.md).

## Current online inference

The deployed Worker has a first-party Cloudflare Workers AI binding and currently exposes:

- Public health: `GET /healthz`
- Authenticated model list: `GET /v1/models`
- Authenticated chat: `POST /v1/chat/completions`
- Authenticated hybrid route: `POST /v1/hybrid/chat/completions`
- Encrypted personal-memory snapshot routes under `/v1/memory/snapshots`

Current built-in model IDs: `nexus-gemma-4-26b-a4b` backed by `@cf/google/gemma-4-26b-a4b-it`, and `nexus-qwen-3.8-27b` backed by `@cf/qwen/qwen3.8-27b`. These are optional hosted fallbacks, not the owner's twelve-model archive. Workers AI inference is separate from local model execution and has its own service availability and usage terms.

The Worker has passed the public health/model-catalogue smoke test. The Cloudflare account-level Gemma and Qwen model tests both generated responses. The GitHub Actions authenticated chat-route smoke test is currently skipped because the repository secret `NEXUS_API_KEY` is not configured. Do not mark authenticated route verification as passed until that test visibly executes.

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

Termux is not required to run the Android app or execute local inference. The app currently supports manual GGUF import, metadata validation, one active local model at a time, load/unload and removal. The target Auto mode is local-first; online inference is an explicit optional route. Offline mode never sends inference prompts to the gateway.

The first phone-sized candidate is Qwen2.5-0.5B-Instruct Q4_K_M. It is pinned with an upstream SHA-256, but has not been downloaded to or tested on the target phone. The app does not yet provide the private Google Drive model catalogue/downloader or verify a manual import against the registry SHA-256. See [MODEL_DISTRIBUTION.md](MODEL_DISTRIBUTION.md), [Google Drive model workspace](GOOGLE_DRIVE_MODEL_WORKSPACE.md) and [the model catalogue](../models/README.md).

## Production readiness

Do not advertise WASTI NEXUS as a fully production-ready multi-customer service until the remaining gates in [STATUS.md](STATUS.md) are closed with evidence. A successful build, a healthy Worker and a populated model catalogue are not substitutes for private Drive OAuth, verified model assets, signed owner-controlled releases, physical-device offline inference and customer security review.
