# Model catalogue and private archive

This directory tracks all 12 target model families, exact candidate repositories, pinned revisions, licence evidence and acquisition tooling. **It does not contain model weights.**

## Current verified state

- 12 model families are catalogued.
- Llama 3.2 1B and Gemma 4 E2B have immutable upstream revisions pinned.
- The other ten candidates still need immutable revision resolution.
- No checkpoint has been downloaded or checksum-verified.
- Redistribution is not approved for any candidate at this stage.
- The registry's `download_status` values are authoritative; do not infer download success from a revision being pinned.

## Download workflow

1. Review the exact checkpoint, model card and licence.
2. For gated repositories, sign in to Hugging Face and accept the publisher's terms yourself.
3. Install dependencies with `python -m pip install -r models/requirements.txt`.
4. Set `HF_TOKEN` in your local shell or credential store; never commit it.
5. Run `python models/pin_revisions.py` as a dry run, review the output, then run `python models/pin_revisions.py --write`.
6. Review the registry diff and run `python models/download_all.py --dry-run`.
7. Download to a private PC/external-drive archive with `python models/download_all.py --output /path/to/private-model-archive`.
8. Verify every generated SHA-256 manifest and test each checkpoint before marking it downloaded.
9. Quantize/convert only when the model's licence and conversion workflow permit it. Keep original checkpoints in the private archive.

The scripts refuse moving branches and do not bypass gated access or accept terms on your behalf.

## Android storage policy

The Android app must never download all 12 models automatically. It will expose a catalogue with separate states: available online, downloadable to device, installed, incompatible, and licence-restricted. Device fit is based on measured/estimated peak memory, model size, context/KV-cache budget, ABI and runtime compatibility—not storage space alone.

With approximately 3 GB free RAM, start with one small quantized model in the 0.5B–1B class and a conservative 1K–2K context. This is a cautious starting point, not a guarantee: Android system pressure, chipset, thermal state and model architecture change the real limit. The app must benchmark before recommending larger models and must unload the active model before switching.

All other configured models remain accessible through online routing when a lawful provider is configured. Free availability, quotas and provider terms vary; do not promise unlimited free inference.

## Where files belong

- GitHub repository: source, model registry, manifests, checksums, small versioned evaluation/training datasets, licence notices and reproducible scripts.
- Private local archive: full upstream checkpoints and large quantized files that exceed repository limits.
- Android private app storage: only the user-selected compatible quantized models and encrypted personal memory.
- Cloudflare: web application assets and the online API gateway only; no memory, dataset or model-weight storage.

GitHub is version control, not a high-throughput database or training runtime. Keep private user memory in a private repository, encrypt sensitive records before commit, batch writes, and respect API/rate limits. Never place customer data or secrets in a public repository.

## Licence and commercial use

Open weights are not automatically unrestricted open source. Command R and StableLM remain disabled for customer/commercial inference unless separate commercial rights are obtained. Llama, Gemma, Falcon and GLM remain conditional pending exact checkpoint terms and notices. See [LICENSE_REVIEW.md](LICENSE_REVIEW.md).
