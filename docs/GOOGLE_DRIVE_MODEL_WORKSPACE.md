# Google Drive model workspace

## Purpose

Google Drive is the private model archive for WASTI NEXUS. The Android app lets the owner select one dedicated Drive folder, then can stream the twelve pinned public GGUF files from Hugging Face into that folder and later copy a selected file into app-private storage for offline inference.

The twelve exact GGUF artifacts total **9,586,646,112 bytes (about 8.93 GiB)**, excluding notices and future revisions. The reported 250 GB free in the owner's Drive is comfortably sufficient.

## One-time setup

1. Create a dedicated folder in Google Drive named `WASTI-NEXUS-MODEL-WORKSPACE`.
2. Install/update the Google Drive Android app and sign in.
3. Open WASTI NEXUS and choose **Connect Google Drive folder**.
4. Select that dedicated folder in Android's system folder picker.
5. Tap **Archive all 12 models to Drive · 9.59 GB** and confirm. Use Wi-Fi and keep the app open until the transfer finishes.
6. Once archived, use **Download from Drive** on any model card to install it on the phone. The app verifies the exact byte length and SHA-256 again before local installation.

If the Drive document provider does not grant write access or cannot stream large files, the app reports that clearly; the owner can upload the same exact filenames through the Google Drive app or desktop client instead.

## How access works — no AI API key and no Google OAuth client

The app uses Android's system folder picker with `ACTION_OPEN_DOCUMENT_TREE` / `ActivityResultContracts.OpenDocumentTree`. Google Drive appears as a document provider when its Android app/provider is installed and signed in. Android grants NEXUS access only to the selected folder. The app persists read/write permission when the provider grants it; read-only permission still allows installation from files already in Drive.

The public model files are fetched from immutable Hugging Face revisions over HTTPS. No Hugging Face token, Google Drive API key, service-account key, Google OAuth client, Cloudflare credential or AI API key is required for the model archive or local inference. The owner's Google account sign-in in the Drive app is separate from an AI API key.

Official Android references:
- [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider) — cloud document providers and folder access.
- [Access documents and other files](https://developer.android.com/training/data-storage/shared/documents-files) — user-selected folder permissions and persistent URI grants.
- [ACTION_OPEN_DOCUMENT_TREE](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT_TREE) — directory selection.

## Catalogue and integrity contract

The app bundles `android/app/src/main/assets/nexus-model-catalog.json` with twelve WASTI NEXUS-branded collection entries. Each entry preserves the upstream model name, creator, repository, licence and attribution. The models are open-weight candidates under Apache-2.0 or MIT licence evidence; WASTI NEXUS does not claim to have trained or authored them.

All twelve entries have:
- An immutable 40-character source repository revision.
- An exact GGUF filename and quantization.
- Exact byte length.
- SHA-256 from the upstream Hugging Face linked-object metadata.
- A conservative minimum available RAM estimate.
- A pinned public HTTPS source.

The catalogue marks all twelve as downloadable. CI rechecks each pinned revision, source file, byte length, SHA-256 and permissive base-model/GGUF licence metadata. See [WASTI NEXUS model library](WASTI_NEXUS_MODEL_LIBRARY.md) and [third-party model notices](THIRD_PARTY_MODEL_NOTICES.md) for the complete list and provenance.

## Archive and installation behaviour

- **Archive all 12:** streams one public model at a time from its pinned Hugging Face revision into the selected Drive folder. The source byte count and SHA-256 are checked before the temporary Drive file is promoted to its final filename.
- Existing same-sized Drive files are SHA-256 checked before being skipped. A bad or incomplete existing file is replaced.
- Archive progress is reported across the full 9.59 GB collection. Concurrency is deliberately one model at a time to limit memory and thermal pressure.
- The app uses temporary `.part` files. Failed or cancelled source transfers remove the incomplete temporary file. Transfers restart from zero; byte-range resume is not claimed.
- **Download from Drive:** streams the selected document into an app-private `.part` file, verifies exact byte count and SHA-256, then atomically promotes it to the installed-model directory.
- Removing a local model does not delete the Drive archive.
- A model is loaded only after storage/RAM preflight and GGUF metadata checks. A pinned file being downloadable does not certify that it performs well on every phone.
- Once installed, local inference works in airplane mode without Drive, an AI API key or Cloudflare availability.

## Current implementation state

The Android app includes a scoped Drive folder picker, persisted URI permission, a twelve-model branded catalogue, one-tap sequential archive to Drive, and a verified Drive-to-device installation path. CI tests the model catalogue, central registry, pinned source manifests, Android build and JVM tests. Physical Drive-provider write behaviour, local model loading, RAM, thermal behaviour, latency and battery still require testing on the target Redmi Note 14 Pro.

## Licence and distribution rules

The shortlist excludes the earlier non-commercial/restricted candidates. The source audit found Apache-2.0 or MIT evidence for all twelve base models and their GGUF repositories, with OLMo 2's base-model Apache-2.0 notice retained even though its GGUF repository has no separate licence tag. Preserve all upstream notices. The registry conservatively does not approve redistribution. Do not expose model weights to customers or redistribute them until a separate licence-text and distribution review is completed.

Google Drive is storage, not a GPU inference service. Downloaded weights run locally on the phone; an online inference endpoint for customer products still requires a compute host.
