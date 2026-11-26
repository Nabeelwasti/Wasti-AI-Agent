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

## Private access without an API key

The Android app uses the system Storage Access Framework folder picker. The owner chooses the dedicated Google Drive workspace folder once; Android grants NEXUS scoped read access to that folder and the app persists the URI permission. This is not an AI API key, Google API key, OAuth client, or service-account credential. The app does not request access to the owner's entire Drive.

Google Drive must be available as an Android document provider (normally through the Google Drive app) and the owner must be signed in. If the provider is unavailable, the owner can still use local models already installed on the phone or import a GGUF manually.

Official Android references:
- [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider) — cloud document providers and folder access.
- [Access documents and other files](https://developer.android.com/training/data-storage/shared/documents-files) — user-selected folder permissions and persistent URI grants.
- [ACTION_OPEN_DOCUMENT_TREE](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT_TREE) — directory selection.

Do not make the model folder public. The user-selected folder grant keeps access scoped to the dedicated workspace.

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

1. Use `ACTION_OPEN_DOCUMENT_TREE` so the owner explicitly selects the Drive workspace folder.
2. Persist only the read permission for that selected folder; do not request broad Drive access.
3. Load the bundled model catalogue and resolve exact approved filenames.
4. Find exactly one matching file within the selected folder tree; reject duplicate names.
5. Stream to app-private temporary storage with progress and cancellation.
6. Check provider-reported size when available, exact streamed byte length and SHA-256.
7. Atomically promote only verified files; preserve the previous installed model after a failed transfer.
8. Parse GGUF metadata and perform runtime, ABI, RAM/context and thermal preflight before offering Load.
9. Remove local copies without deleting the original Drive archive.

The current SAF stream path restarts an interrupted transfer from zero. Do not claim resumable byte-range support unless the selected document provider exposes and passes a tested range contract. Once installed, local inference must work when the owner is signed out of Drive, the phone is offline, and Cloudflare is unavailable.

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
