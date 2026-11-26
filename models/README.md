# Model archive

This folder is the versioned registry and acquisition tooling for all 12 target model families. It does not store large model weights in Git.

## Current status
- 12 families catalogued.
- Candidate checkpoint IDs and initial licence screening recorded in `registry.json` and `LICENSE_REVIEW.md`.
- No immutable revisions have yet been pinned.
- No model checkpoint has been downloaded or checksum-verified.
- Redistribution remains unapproved until exact licence files and revisions are reviewed.

## Download workflow
1. Review the exact checkpoint and its terms.
2. If access is gated, sign in to Hugging Face and accept the publisher's terms yourself.
3. Set `HF_TOKEN` in your local shell or credential store (never commit it).
4. Resolve current upstream commit SHAs with `python pin_revisions.py` (dry run), review the output, then run `python pin_revisions.py --write`.
5. Run `python download_all.py --output /path/to/private-model-archive`.
6. Verify generated SHA-256 manifests and test loading each checkpoint.
7. Keep the complete archive on a PC/external drive; copy only device-compatible quantized builds to Android.

The download script deliberately refuses to follow a moving `main` branch. It does not bypass gated access or accept terms on your behalf.

## Storage and commercial use
Full-precision checkpoints can occupy many gigabytes each. Do not put weights in normal Git commits. GitHub stores source, manifests and scripts; the actual weights belong in controlled external storage. Cloudflare R2's free allowance is not enough for a complete 12-model archive.

Open weights are not automatically unrestricted open source. Command R and StableLM candidates currently have non-commercial restrictions; keep them out of customer/commercial inference unless you obtain commercial rights. See `LICENSE_REVIEW.md`.
