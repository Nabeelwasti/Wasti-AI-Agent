# Google Drive model workspace

## Purpose

Google Drive is the owner's model-file archive. WASTI NEXUS Android uses the Android Storage Access Framework (SAF) to let the owner select one dedicated Drive folder. The app receives scoped permission to that folder, reads the approved model catalogue, copies a selected GGUF into app-private storage, verifies it, and runs it locally.

The owner reports approximately 250 GB available in a 400 GB subscription. The curated twelve-model Q3_K_M/Q4_K_M collection has a preliminary combined size of roughly 10 GB using rounded public model-card figures; exact total bytes will be known only after each artifact is pinned. This is comfortably within the reported Drive capacity. Storage capacity does not make a model compatible with the phone or grant redistribution rights.

## Folder layout

Create a dedicated folder in Google Drive:

`WASTI-NEXUS-MODEL-WORKSPACE/`

- `qwen2.5-0.5b-instruct-q4_k_m.gguf` — first verified phone-sized model.
- `models/` — future verified GGUF checkpoints, optionally grouped by family.
- `licenses/` — model-card snapshots, licence texts and attribution notices.
- `checksums/` — SHA-256 and exact byte-length manifests.
- `quarantine/` — incomplete or unreviewed artifacts; never expose these as downloadable.

The app searches the selected folder and up to four nested directory levels for the exact filename registered in its catalogue. Keep only one copy of each approved filename in the workspace. Keep personal documents, memory snapshots, customer data and credentials outside this folder.

## How access works — no API key and no Google OAuth client

The app uses Android's system folder picker with `ACTION_OPEN_DOCUMENT_TREE` / `ActivityResultContracts.OpenDocumentTree`. Google Drive appears as a document provider when its Android app/provider is installed and signed in. The owner chooses the dedicated workspace folder once. Android grants NEXUS access only to the selected folder, and the app persists the read permission across restarts.

This is **not an AI API key, Google API key, service-account key or OAuth client configuration**. No Cloudflare credential is involved. NEXUS does not request broad access to the owner's entire Drive. The owner can revoke the folder permission through Android settings or reconnect another folder inside the app.

Official Android references:
- [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider) — cloud document providers and folder access.
- [Access documents and other files](https://developer.android.com/training/data-storage/shared/documents-files) — user-selected folder permissions and persistent URI grants.
- [ACTION_OPEN_DOCUMENT_TREE](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT_TREE) — directory selection.

## Catalogue and integrity contract

The app currently bundles `android/app/src/main/assets/nexus-model-catalog.json`. It contains twelve curated open-weight candidates selected for permissive upstream licences (Apache-2.0 or MIT, including inherited base-model notices). Only a model with status `ready`, an exact filename, exact byte length and a 64-character SHA-256 is downloadable. Eleven candidates remain pending verification; the app refuses to download them.

The Qwen2.5-0.5B-Instruct Q4_K_M entry is pinned to upstream revision `12145bd1d629190a4d44254073650877954d02c9`, file size 491,400,032 bytes, and SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`. The owner must place this exact file in the selected Drive workspace. A similarly named file with a different size or hash is rejected.

Each future downloadable entry must include stable model ID, family, exact filename, quantization, Drive filename, exact byte length, SHA-256, immutable upstream revision, model-card and licence references, approved use status, GGUF architecture/runtime compatibility and conservative device-memory requirements. Missing values mean not ready.

## Android download behaviour

- The user selects the Drive folder through the Android system picker; only that folder is granted to the app.
- NEXUS lists the twelve bundled catalogue entries and clearly marks pending, restricted and downloadable states.
- A selected GGUF is streamed from the Drive document provider to an app-private `.part` file with progress and cancellation.
- The app checks provider-reported length when available, exact streamed byte count and SHA-256.
- Only a fully verified file is promoted to the installed-model directory. Failed transfers delete the partial file and preserve any existing installed model.
- Storage preflight reserves additional free space; GGUF metadata and runtime/RAM checks still apply before loading.
- The app does not delete or modify the original Drive archive when a local model is removed.
- The current SAF stream implementation restarts an interrupted transfer from zero. It does not claim byte-range resume because document providers do not expose a uniform HTTP Range contract.
- After installation, local inference works in airplane mode without Google Drive access, an AI API key or Cloudflare availability.

## Current implementation state

The Android app now includes a scoped Drive folder picker, persisted folder read permission, a twelve-model branded catalogue UI, a verified Qwen Drive copy path, exact length/SHA-256 checks and local install/load. The WASTI NEXUS collection preserves every upstream model's name, creator, source and licence; it does not claim that NEXUS trained these models. The other eleven catalogue entries are deliberately pending until their exact GGUF artifact, immutable revision, byte length, licence and checksum are validated. See [WASTI NEXUS model library](WASTI_NEXUS_MODEL_LIBRARY.md) for the curated list and sources.

The owner still needs to create the folder and upload the exact Qwen GGUF file. This repository connection cannot upload binaries into the owner's Drive. A real Drive-provider transfer, cancellation, permission revocation and airplane-mode install drill must be performed on the target Redmi before this feature is called device-certified.

## Licence gate

Open weights are not automatically unrestricted. Review the exact base model and quantized GGUF repository terms before uploading each artifact. Command R and StableLM remain non-commercial in the current screening; Llama, Gemma, Falcon and GLM have additional conditions. Keep restricted candidates unavailable for customer/commercial products unless separate rights permit the intended use.
