# Model catalogue and acquisition tools

This directory tracks all 12 target model families, exact candidate GGUF repositories, pinned revisions, licence evidence and acquisition tooling. **It does not contain model weights.**

## Current verified state

- 12 model families are catalogued.
- Llama 3.2 1B and Gemma 4 E2B have immutable upstream revisions pinned.
- The other ten candidates still need immutable revision resolution.
- No checkpoint has been downloaded or checksum-verified.
- Redistribution is not approved for any candidate at this stage.
- The registry's `download_status` values are authoritative; do not infer download success from a revision being pinned.

## Selected local-model download workflow

1. Review the exact checkpoint, model card and licence.
2. For gated repositories, sign in to Hugging Face and accept the publisher's terms yourself.
3. Install dependencies with `python -m pip install -r models/requirements.txt`.
4. Set `HF_TOKEN` in your local shell or credential store; never commit it.
5. Run `python models/pin_revisions.py` as a dry run, review the output, then run `python models/pin_revisions.py --write`.
6. Review the registry diff and run `python models/download_all.py --family qwen --dry-run`.
7. Download only the selected local GGUF candidate with `python models/download_all.py --family qwen --output /path/to/nexus-local-models`.
8. Verify the generated SHA-256 manifest and test the checkpoint on the target device before marking it downloaded.
9. Repeat with another explicit `--family` only when storage, available RAM, runtime compatibility and licence allow it. The script never downloads all twelve primary full checkpoints.

The scripts refuse moving branches and do not bypass gated access or accept terms on your behalf. The bulk compatibility command requires explicit family IDs and only downloads their registered GGUF files.

## First phone-sized local candidate

A small Qwen candidate is registered under the Qwen family:

- Repository: `Qwen/Qwen2.5-0.5B-Instruct-GGUF`
- File: `qwen2.5-0.5b-instruct-q4_k_m.gguf`
- Quantization: Q4_K_M
- Approximate file size: 491 MB
- Licence: Apache-2.0 (preserve the exact repository/base-model notices)
- Pinned revision: `12145bd1d629190a4d44254073650877954d02c9`
- Verified upstream SHA-256: `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`

This is the first candidate to test with approximately 3 GB of free RAM. The upstream revision and SHA-256 are pinned, but the file has not yet been downloaded to your phone or device-validated. The app's memory check is a preflight estimate, not a benchmark guarantee.

To pin only this family's primary and local-candidate repositories, then download only the selected GGUF file:

```bash
python -m pip install -r models/requirements.txt
python models/pin_revisions.py --family qwen
# Review the resolved SHAs, then:
python models/pin_revisions.py --family qwen --write
python models/download_local_candidate.py qwen --output /path/to/nexus-local-models
```

The download script filters to the exact GGUF filename; it does not download the other quantizations or all twelve families. The resulting manifest contains the exact revision, file size and SHA-256. Copy that GGUF file to the phone and import it from the Offline section.


## Local GGUF catalogue for all 12 families

All twelve families now have a candidate GGUF repository and an exact Q4_K_M filename recorded in `registry.json`. These are candidate definitions, not verified downloads. Each repository revision must be pinned and checked before downloading. Approximate file sizes are from the upstream model cards/file listings; available-RAM figures are conservative planning estimates, not guarantees.

| Family | Q4_K_M file size | Estimated free RAM | Customer-use status |
|---|---:|---:|---|
| Qwen 2.5 0.5B | ~491 MB | ~2.5 GB | Candidate; Apache-2.0 |
| Llama 3.2 1B | ~808 MB | ~3.2 GB | Meta licence/gated base; review required |
| Gemma 3 1B | ~806 MB | ~3.2 GB | Gemma terms; review required |
| DeepSeek R1 Distill 1.5B | ~1.12 GB | ~4 GB | Apache-2.0 GGUF repo; preserve DeepSeek/Qwen notices and review |
| Mistral 7B v0.3 | ~4.37 GB | ~8 GB | Apache-2.0 candidate |
| Phi 3.5 Mini | ~2.39 GB | ~5 GB | MIT candidate |
| Granite 3.1 2B | ~1.55 GB | ~4.5 GB | Apache-2.0 candidate |
| GLM-4 9B | ~6.25 GB | ~10 GB | Custom GLM terms; review required |
| Command R7B | ~5.06 GB | ~8 GB | CC-BY-NC-4.0; blocked for commercial/customer use |
| Falcon3 1B | ~1.06 GB | ~3.5 GB | Custom TII terms; review required |
| SmolLM2 1.7B | ~1.06 GB | ~4 GB | Apache-2.0 candidate |
| StableLM 2 1.6B | ~961 MB | ~4 GB | Non-commercial research terms; blocked for customer use |

With approximately **3 GB currently available RAM**, begin with Qwen 0.5B only. Llama 1B and Gemma 3 1B may be later experiments, but test actual free RAM and thermal behaviour first. Larger candidates remain online-first until a higher-memory device is available. Freeing storage does not create RAM; the app will refuse model loads when its RAM preflight fails.

All candidates are manually installable from their upstream GGUF repositories. The Android model manager supports multiple installed GGUF files, one active model at a time, and removal to reclaim app storage. It does not yet auto-download the catalogue inside the app.

## Android storage policy

The Android app must never download all 12 models automatically. It will expose a catalogue with separate states: available online, downloadable to device, installed, incompatible, and licence-restricted. Device fit is based on measured/estimated peak memory, model size, context/KV-cache budget, ABI and runtime compatibility—not storage space alone.

With approximately 3 GB free RAM, start with one small quantized model in the 0.5B–1B class and a conservative 1K–2K context. This is a cautious starting point, not a guarantee: Android system pressure, chipset, thermal state and model architecture change the real limit. The app must benchmark before recommending larger models and must unload the active model before switching.

All other configured models remain candidates for online routing only when a currently available lawful provider is configured. The deployed NEXUS gateway currently exposes Gemma 4 26B A4B through Workers AI; the twelve-family local registry is not the same as twelve live cloud endpoints. Free availability, quotas and provider terms vary; do not promise unlimited free inference.

## Where files belong

- GitHub repository: source, model registry, manifests, checksums, small versioned evaluation/training datasets, licence notices and reproducible scripts.
- Private local archive: full upstream checkpoints and large quantized files that exceed repository limits.
- Android private app storage: only the user-selected compatible quantized models and encrypted personal memory.
- Cloudflare Workers AI: online inference only; it does not accept arbitrary custom GGUF uploads. A separate R2 bucket may be used as an optional model-file archive/distribution origin, subject to licence and privacy checks. R2 is not currently provisioned for NEXUS.

GitHub is version control, not a high-throughput database or training runtime. This deployment intentionally stores only client-encrypted memory snapshots in the public repository. GitHub exposes ciphertext and metadata publicly; never place plaintext customer/personal data or secrets here. Batch writes and respect API/rate limits.

## Licence and commercial use

Open weights are not automatically unrestricted open source. Command R and StableLM remain disabled for customer/commercial inference unless separate commercial rights are obtained. Llama, Gemma, Falcon and GLM remain conditional pending exact checkpoint terms and notices. See [LICENSE_REVIEW.md](LICENSE_REVIEW.md).
