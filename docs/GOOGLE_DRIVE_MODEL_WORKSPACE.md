# Google Drive model workspace

## Purpose

This document defines the private Google Drive model archive for WASTI NEXUS. Google Drive stores the model binaries; the Android phone downloads a selected GGUF into app-private storage and runs it locally. Google Drive is not an inference runtime.

The owner currently reports approximately 250 GB free in a 400 GB subscription. The current twelve Q4_K_M candidates are estimated at approximately 26 GB combined, before additional versions and future models. This is ample capacity for the current catalogue, but the estimates must be replaced with actual verified file sizes before publication.

## Folder layout

Create a dedicated folder in the owner's Drive:

`WASTI-NEXUS-MODEL-WORKSPACE/`

- `catalogue/` — approved JSON model manifest.
- `models/<family>/<version>/` — immutable GGUF binaries.
- `licenses/<family>/` — licence text, model cards and attribution.
- `checksums/` — SHA-256 and byte-length manifests.
- `quarantine/` — incomplete or rejected files; never exposed to the app.

Keep personal documents, memory snapshots, customer data and credentials outside this folder.

## Private authentication

The production design uses the owner's Google account OAuth sign-in and read-only Drive API access. This is a storage authorization, not an AI API key. It does not authorize or meter AI inference.

The Android app should use Google's supported authorization flow to obtain short-lived access tokens, then call:

- `GET https://www.googleapis.com/drive/v3/files?q=...` to list approved files in the workspace folder.
- `GET https://www.googleapis.com/drive/v3/files/{fileId}?fields=id,name,size,mimeType,md5Checksum,modifiedTime,capabilities(canDownload)` to verify file metadata.
- `GET https://www.googleapis.com/drive/v3/files/{fileId}?alt=media` to stream binary model bytes.

Official docs: [Drive files.get](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/get), [download and export](https://developers.google.com/workspace/drive/api/guides/manage-downloads), [Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).

The broad `drive.readonly` scope is classified as restricted by Google. The app must request only the minimum scope required and follow the applicable consent-screen, testing and verification rules. Do not embed a service-account private key, refresh token or other long-lived credential in the APK. Do not scrape browser pages or use public `uc?export=download` URLs as the production transport.

A public "anyone with the link" folder is not the default because it exposes model files to anyone who obtains the link. The personal archive should remain private.

## Manifest requirements

The manifest is version-controlled in this repository and contains metadata only, not access tokens. Each downloadable model must have:

- stable model ID and family;
- exact GGUF filename and quantization;
- Drive file ID;
- exact byte length and SHA-256;
- upstream repository and immutable revision;
- model-card and licence URLs plus archived notices;
- licence and redistribution decision;
- GGUF architecture, supported runtime, context recommendation and minimum-memory estimate;
- manifest schema and catalogue revision.

Missing Drive IDs, checksums, byte lengths, immutable revisions or licence decisions mean the model is not downloadable. Never invent these values.

## Android acceptance criteria

- Owner can sign into the correct Google account and explicitly authorize read-only model access.
- Catalogue shows all twelve families but clearly distinguishes verified/downloadable, pending review, incompatible and restricted.
- Download is streamed to a temporary app-private file with progress, cancellation, retry and validated Range resume where supported by Drive.
- File metadata is checked before transfer; exact size and SHA-256 are checked after transfer.
- Only a verified file is atomically moved into the installed-model directory.
- GGUF metadata and device RAM/context/runtime compatibility are checked before loading.
- A failed or interrupted download never replaces a working installed model.
- Model switching unloads the previous native runtime before loading another.
- After download, local inference works in airplane mode without Google sign-in, a NEXUS API key, or Cloudflare availability.
- Signing out of Google does not delete already-installed local models.
- Removing a model from the phone never deletes its original Drive archive.

## Current implementation status

This is the target architecture, not a claim that the integration is already live. The current Android code has a pinned Qwen upstream downloader, manual GGUF import, and local inference. It does not yet contain Google OAuth, Drive folder discovery, Drive manifest browsing, or Drive binary download. No Drive folder ID, OAuth client configuration, model file IDs or uploaded model binaries have been supplied to this repository.

The GitHub connection available in this session cannot sign into or upload files to the owner's Google Drive. Therefore no model binary has been uploaded and no Drive transfer has been tested. These tasks must remain open until the owner account is connected and the Android integration is implemented and exercised.

## Model licence gate

Open weights are not automatically unrestricted. Review the exact base model and quantized GGUF repository terms before uploading or making each file downloadable. Command R and StableLM remain non-commercial in the current screening; Llama, Gemma, Falcon and GLM have additional terms or conditions requiring review. Keep any restricted checkpoint private and disabled for customer/commercial use unless the relevant rights permit it.
