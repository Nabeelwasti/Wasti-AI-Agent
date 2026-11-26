# Deployment guide

## Canonical production components

- **Android:** native Kotlin/Jetpack Compose app in `android/`; Arm AI Chat wraps llama.cpp for local GGUF inference.
- **Online gateway:** Cloudflare Worker `wasti-nexus-gateway`, source of record `cloudflare/src/index.js`, Wrangler configuration `cloudflare/wrangler.toml`.
- **Universal API URL:** `https://wasti-nexus-gateway.wastinabeel99.workers.dev`.
- **Model registry:** `models/registry.json`; model weights are not committed to Git.
- **Python orchestrator:** `orchestrator/` is a separately deployable service for deployments that need its configured multi-provider routing. It is not the current Cloudflare Worker runtime and is not required for the built-in Workers AI model.

## Current online inference

The deployed Worker has a first-party Cloudflare Workers AI binding and currently exposes:

- Public health: `GET /healthz`
- Authenticated model list: `GET /v1/models`
- Authenticated chat: `POST /v1/chat/completions`
- Authenticated hybrid route: `POST /v1/hybrid/chat/completions`
- Encrypted personal-memory snapshot routes under `/v1/memory/snapshots`

Current built-in model ID: `nexus-gemma-4-26b-a4b`, backed by `@cf/google/gemma-4-26b-a4b-it`. The model is invoked through the Cloudflare AI binding; no third-party model-provider API token is needed. The NEXUS bearer key is gateway authentication, not a model-provider key.

The Worker has passed the public health/model-catalogue smoke test. The Cloudflare account-level Gemma model test also generated a response. The GitHub Actions authenticated chat-route smoke test is currently skipped because the repository secret `NEXUS_API_KEY` is not configured. Do not mark authenticated route verification as passed until that secret is added and the test visibly executes.

## Configure GitHub Actions verification

In the GitHub repository, open **Settings → Secrets and variables → Actions** and add:

- `NEXUS_API_KEY`: the same high-entropy master bearer key already configured as the Cloudflare Worker secret. Do not paste it into source files or this conversation.

The CI workflow then runs an authenticated request through the deployed Worker and asserts that a non-empty answer is returned. If the secret is absent, the workflow logs a warning and skips this test; a skipped test is not a pass.

## Configure customer/application access

- Keep the master `NEXUS_API_KEY` private.
- Do not embed the master key in a public web bundle, customer APK, or downloadable source code.
- Generate a unique random key for each trusted server-side application, store only its SHA-256 digest in `NEXUS_CLIENT_KEYS_JSON`, and grant only the required `models` and/or `chat` scopes.
- A customer-facing product should authenticate its own users and call NEXUS from a trusted backend or a short-lived-token broker. Do not use a long-lived shared bearer key as end-user authentication.
- The current Worker Rate Limiting binding is approximate and local to Cloudflare locations; it is not a strict global quota or a billing meter.
- The GitHub-backed encrypted memory endpoint is a single personal vault, not a tenant-isolated customer database. Do not use it for customer memory until tenant isolation, authorization, retention and backup controls are implemented.

## Cloudflare deployment

The Worker source and Wrangler configuration live in this same repository. Deploy through the configured Cloudflare account and verify the deployed source, AI binding, rate-limit binding, secret bindings and public health endpoint after every release. The current deployed source was verified byte-for-byte against `cloudflare/src/index.js`.

Cloudflare Workers AI is an inference service. Do not upload arbitrary custom GGUF weights to Workers AI. If model files are later distributed to devices, use a separate R2 bucket or authorised GitHub Release assets, with exact file size, immutable source revision, licence evidence and SHA-256 verification. A dedicated NEXUS R2 bucket is not currently provisioned.

## Android release

- CI builds the debug APK and runs Android JVM unit tests.
- CI also assembles a release APK with a disposable test keystore and verifies the signature. This validates the Gradle signing configuration only.
- For a real production release, create and securely back up a permanent owner-controlled keystore, configure the four secrets listed in [RELEASE.md](RELEASE.md), and run `.github/workflows/release.yml`.
- Install the signed APK on the target phone. Test model import, GGUF load, airplane-mode generation, online inference, automatic fallback, memory restore, app upgrade, RAM use, thermal behaviour and battery use. CI cannot certify these physical-device behaviours.

## Model acquisition and offline mode

Termux is not required to run the Android app or execute local inference. The app currently supports manual GGUF import, metadata validation, one active local model at a time, load/unload and removal. Auto mode tries online inference first and falls back to a local model for network errors, missing online credentials and transient gateway/quota failures when a compatible local model is installed. Authentication and permission failures remain visible.

The first phone-sized candidate is Qwen2.5-0.5B-Instruct Q4_K_M. It is pinned with an upstream SHA-256, but it has not been downloaded to or tested on the target phone. The app does not yet provide an in-app catalogue downloader or verify a manual import against the registry SHA-256. See [MODEL_DISTRIBUTION.md](MODEL_DISTRIBUTION.md) and [the model catalogue](../models/README.md).

## Production readiness

Do not advertise WASTI NEXUS as a fully production-ready multi-customer service until the remaining gates in [STATUS.md](STATUS.md) are closed with evidence. A successful build, a healthy Worker and a populated model catalogue are not substitutes for live authenticated route tests, signed owner-controlled releases, physical-device offline inference and tenant/security review.
