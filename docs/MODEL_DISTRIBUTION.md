# WASTI NEXUS model distribution and archive policy

## Chosen storage architecture

**Google Drive is the sole owner model archive and model-file distribution source. Cloudflare R2, Cloudflare storage products and GitHub Releases are not part of the NEXUS model-storage design.** Cloudflare Workers AI remains an optional inference fallback only; it does not store or serve the twelve personal GGUF checkpoints.

The owner's Google Drive subscription currently has approximately 250 GB available. The twelve exact GGUF artifacts total 9,586,646,112 bytes (about 8.93 GiB), excluding licence notices and future revisions. This is comfortably within the reported Drive capacity, but capacity does not guarantee that every model can run on the phone.

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

Keep the model catalogue in the Android app assets and review it in the monorepo. It contains model metadata and exact workspace filenames, never Drive file IDs, access tokens or private credentials. Each downloadable entry must include:

- stable model ID and family;
- exact GGUF filename and quantization;
- exact filename expected inside the selected Drive workspace;
- exact byte length and SHA-256;
- upstream repository and immutable revision;
- model-card and licence URLs plus archived licence notice;
- redistribution/commercial-use decision and reviewer status;
- GGUF architecture, runtime compatibility, context recommendation and minimum-memory estimate;
- manifest schema version and catalogue revision.

A model is not downloadable in the app until its exact workspace filename, byte length, SHA-256, immutable source revision, licence review and runtime compatibility are all verified. Null or missing fields mean **not ready**. Do not manufacture filenames, checksums or licence approvals.

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

## Model licensing and the twelve-model collection

The curated shortlist is twelve open-weight candidates selected for permissive upstream licences: Apache-2.0 or MIT (DeepSeek R1 Distill also inherits Qwen base-model terms). This is not a claim that every training pipeline satisfies every formal Open Source AI definition. The list, original model names, upstream authors, exact GGUF repositories and sizes are documented in [WASTI NEXUS model library](WASTI_NEXUS_MODEL_LIBRARY.md) and [third-party model notices](THIRD_PARTY_MODEL_NOTICES.md). WASTI NEXUS is the app and collection identity, not the original model author. Preserve each upstream licence and base-model notice. Do not redistribute or expose a checkpoint to customers until the exact source revision, licence text and rights for the intended use have been verified.

## Current verified state

- One monorepo remains the source of truth for app code, registry, manifests, tests and licence evidence.
- All twelve selected GGUF artifacts have immutable source revisions, exact byte lengths and SHA-256 values pinned in the registry and Android catalogue.
- The Android app now includes the scoped SAF folder picker, persisted read permission, a twelve-model catalogue UI and an exact length/SHA-256 verified Drive copy/install path.
- The twelve exact model files total 9,586,646,112 bytes. The app now has an **Archive all 12 models to Drive** action that streams them sequentially from pinned public Hugging Face revisions into the owner's selected private Drive folder, without AI API keys. If the Drive provider does not grant write access, the app reports a manual-upload fallback.
- All twelve models have immutable source revisions, exact byte lengths, SHA-256 values and permissive base-model licence evidence pinned; all twelve are marked ready for verified Drive-to-device download. Physical-device runtime, memory, thermal and latency validation remains pending.
- A real Google Drive provider transfer, permission-revocation recovery and airplane-mode install test on the target Redmi are still outstanding.

The app implementation is in review and must pass CI and target-device testing before being called production-certified.

## Operational note

Google Drive is storage, not a GPU inference service. Once downloaded, local inference runs on the phone and does not consume Cloudflare Workers AI inference quotas. Drive API access and transfers remain subject to Google's service terms and usage limits; large model downloads should use streaming, bounded concurrency, resumable transfers where supported, and exponential backoff.
