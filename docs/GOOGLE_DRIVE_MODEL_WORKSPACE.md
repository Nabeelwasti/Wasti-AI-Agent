# WASTI NEXUS model distribution and archive policy

## Chosen storage architecture

**Google Drive is the sole owner model archive and model-file distribution source. Cloudflare R2, Cloudflare storage products and GitHub Releases are not part of the NEXUS model-storage design.** Cloudflare Workers AI remains an optional inference fallback only; it does not store or serve the twelve personal GGUF checkpoints.

The owner's Google Drive subscription currently has approximately 250 GB available. The twelve Q4_K_M candidate estimates in the registry total about 26 GB, before duplicate revisions and future additions. This is sufficient capacity for the current candidate set, but capacity does not approve a model's licence or guarantee that every model can run on the phone.

## Drive layout

Create one dedicated folder in the owner's Google Drive:

`WASTI-NEXUS-MODEL-WORKSPACE/`

Recommended structure:

- `catalogue/` — versioned JSON manifest and model notices.
- `models/<family>/<model-version>/` — exact GGUF files, each immutable after publication.
- `licenses/<family>/` — upstream licence text, model card snapshot and attribution notices.
- `checksums/` — SHA-256 manifest and acquisition provenance.
- `quarantine/` — incomplete, unreviewed or rejected artifacts; never exposed in the app catalogue.

Do not put personal documents, memory snapshots, customer data, credentials or unrelated Drive files in this folder.

## Private access without an AI API key

The intended production design is **Google account OAuth sign-in with read-only Drive access**. This is not an AI API key and does not charge per model inference. The app obtains a short-lived Google access token after the owner signs in, then calls the official Drive API to list/download only the approved workspace files. The token must be kept in memory or protected OS storage, refreshed through Google's supported flow, and never committed to Git or embedded as a static secret.

Use the narrowest practical scope. The Drive `drive.readonly` scope allows the app to find and download the owner's files, but Google classifies it as a restricted scope and its consent/verification requirements must be reviewed before public distribution. A private owner-only build can be tested with the owner's Google account in the OAuth consent screen's test-user configuration while the app is being developed. Do not request broad Drive access for unrelated app features.

Official references:
- [Drive files.get](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/get) — metadata and `alt=media` content retrieval.
- [Download and export files](https://developers.google.com/workspace/drive/api/guides/manage-downloads) — authorised binary downloads and download capability checks.
- [Choose Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) — scope sensitivity and consent requirements.

A public "anyone with the link" folder is **not** the default. It would expose every shared model file to anyone who obtains the link and would not be a private owner workspace. Do not use public sharing as a workaround for missing OAuth integration.

## Manifest contract

Keep the catalogue manifest in the monorepo so it can be reviewed and versioned. It contains metadata and Drive file IDs, never access tokens or private credentials. Each downloadable entry must include:

- stable model ID and family;
- exact GGUF filename and quantization;
- Google Drive file ID;
- exact byte length and SHA-256;
- upstream repository and immutable revision;
- model-card and licence URLs plus archived licence notice;
- redistribution/commercial-use decision and reviewer status;
- GGUF architecture, runtime compatibility, context recommendation and minimum-memory estimate;
- manifest schema version and catalogue revision.

A model is not downloadable in the app until its Drive file ID, byte length, SHA-256, immutable source revision, licence review and runtime compatibility are all verified. Null or missing fields mean **not ready**. Do not manufacture file IDs, checksums or licence approvals.

## Android download requirements

The Android client must:

1. Sign the owner into Google using the official OAuth flow; request only read access needed for the model workspace.
2. Load the signed-off catalogue manifest and resolve Drive file IDs.
3. Confirm the Drive file is downloadable and its metadata matches the manifest.
4. Download through the official Drive API with the OAuth access token; never scrape Drive web pages or rely on unofficial `uc?export=download` links.
5. Support HTTP Range resume only when the Drive response validates the requested range and stable file revision. Otherwise restart safely.
6. Stream to app-private temporary storage with progress, cancellation, storage checks and retry/backoff.
7. Verify exact byte length and SHA-256 before atomic promotion to the installed-model directory.
8. Parse GGUF metadata and perform runtime, ABI, RAM/context and thermal preflight before offering Load.
9. Preserve the previous installed model until the replacement passes all checks.
10. Offer model removal without deleting the original Drive archive.

A Google Drive access token is for storage access only. It must never be sent to Cloudflare Workers AI or used as an AI inference credential. Offline inference must work after a model is installed even when the Google account is signed out and the phone is offline.

## Model licensing and the twelve families

"Open weights" does not automatically mean unrestricted open-source software or permission to redistribute. The registry currently contains twelve research candidates, not twelve approved downloadable assets. Some entries have gated or conditional terms; Command R and StableLM are marked non-commercial in the current screening. Preserve each upstream licence and base-model notice. Do not upload, share or expose a checkpoint in Drive until its exact licence permits the intended storage, copying and use. Keep restricted candidates disabled for commercial/customer products unless the rights are separately obtained.

## Current verified state

- One monorepo remains the source of truth for app code, registry, manifests, tests and licence evidence.
- One Qwen2.5-0.5B-Instruct Q4_K_M source revision and SHA-256 are pinned in the registry.
- No model weights have been uploaded to the owner's Google Drive through this repository connection.
- No Google Drive OAuth client has been configured for the Android package.
- The Android app does not yet have the private Drive sign-in, manifest browser or generic Drive downloader.
- The current in-app Qwen installer still downloads from its pinned upstream source; it is not yet backed by the Google Drive workspace.
- No other eleven candidate files are approved for in-app download.

These are explicit implementation gates, not completed features. Do not describe Google Drive model browsing or downloads as live until the Android integration and real Drive transfer tests pass.

## Operational note

Google Drive is storage, not a GPU inference service. Once downloaded, local inference runs on the phone and does not consume Cloudflare Workers AI inference quotas. Drive API access and transfers remain subject to Google's service terms and usage limits; large model downloads should use streaming, bounded concurrency, resumable transfers where supported, and exponential backoff.
