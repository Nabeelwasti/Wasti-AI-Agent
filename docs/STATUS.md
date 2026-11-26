# Verified implementation status

Last updated: 2026-11-26.

## Repository and source

- Single repository: [Nabeelwasti/Wasti-AI-Agent](https://github.com/Nabeelwasti/Wasti-AI-Agent).
- Twelve model families are catalogued. Llama and Gemma have pinned immutable revisions; ten candidates remain unpinned.
- Initial licence screening is documented. No weights have been downloaded or approved for redistribution.
- FastAPI orchestrator source, Cloudflare Worker gateway source, SDKs and native Android Compose client are present.
- Cloudflare Worker deployment was previously confirmed in the Cloudflare control plane. Public HTTP smoke test is still outstanding.

## Verified defects found during this audit

- CI invokes `pytest -q tests/test_registry.py`, but that file did not exist. This guaranteed a CI failure. A registry integrity test file has now been added.
- `models/README.md` incorrectly claimed no revisions were pinned, despite Llama and Gemma already having pinned SHAs. The instructions/status have been corrected.
- The Android app source is remote-only. There is no local inference engine, model downloader or offline inference path yet.
- The model archive downloader is separate from the Android device model manager. Full upstream weights are not in Git.
- `cloudflare/wrangler.toml` names the Worker `wasti-nexus-edge`, while the previously deployed Worker is documented as `wasti-nexus-gateway`. Treat a deployment rename as a breaking change; reconcile the name before any deployment.
- Android build and GitHub Actions execution have not been verified. The latest inspected commit had no reported combined status checks; this does not prove the workflow passed or even ran.

## Architecture decisions now fixed

- One monorepo only.
- Android supports both local inference and remote inference; local operation must not depend on Cloudflare.
- Only device-compatible selected models are installed. Other licensed/configured models remain online-only.
- GitHub stores source-controlled memory/knowledge/dataset files; private personal/customer records must be encrypted and tenant-scoped.
- Cloudflare hosts web assets and the API gateway only. No Cloudflare object storage for memory, datasets or model weights.
- The universal API uses OpenAI-compatible endpoints and truthful single/Hybrid model availability.

## Not complete / release blockers

- The Android client still needs llama.cpp native integration, local model manager, model-fit checks, offline routing and device tests.
- GitHub memory sync service, schemas, encrypted record format and retrieval/index pipeline remain to be implemented.
- The Cloudflare gateway has no configured inference providers; online chat cannot generate answers until real providers are connected.
- The Android Gradle build, APK, signing and CI result remain unverified.
- Model download, checksum validation, runtime-load tests and device benchmarks remain outstanding.
- Production auth, tenant isolation, quotas, rate limits, audit persistence, monitoring, privacy/retention controls, restore drills and security testing remain release gates.

A source push is not a successful build. A deployed gateway is not a functioning inference service. Offline support is not complete until an installed model answers a prompt with networking disabled.
