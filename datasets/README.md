# NEXUS datasets

GitHub stores small, versioned, reproducible datasets and manifests. It does not train models or provide a training runtime.

## Layout

- `manifests/`: dataset manifests, source references, hashes, licence and schema.
- `evaluation/`: small, non-sensitive benchmark prompts and expected evaluation criteria.
- `training/`: curated, openly licensed, non-personal examples suitable for public distribution.

## Admission checklist

Every dataset must record:
- source and retrieval date;
- exact licence and redistribution permission;
- author/curator and revision;
- SHA-256 checksums for data files;
- intended use (training, fine-tuning, evaluation or retrieval);
- schema/version and validation results;
- PII, secrets, consent and copyright review.

Never upload private conversations, customer records, personal identifiers, proprietary company data, credentials or copied copyrighted corpora without explicit rights. Public repository datasets are downloadable by everyone.

Large corpora and model checkpoints do not belong in ordinary Git history. Keep only manifests, scripts and small permitted samples. Training must run on a separate machine/service with suitable compute, and resulting model artefacts must be licence-reviewed before distribution.
