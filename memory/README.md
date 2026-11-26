# NEXUS memory storage

This repository is public. The only permitted personal-memory payload is **client-encrypted AES-256-GCM ciphertext**. GitHub exposes file contents, commit history, paths and metadata to everyone.

## Layout

- `schemas/`: versioned record contracts.
- `personal/encrypted/`: immutable ciphertext snapshots written by the Worker.
- `project/`: only non-sensitive, intentionally public project knowledge may be added here.
- `exports/`: no plaintext personal/customer exports. Keep recovery material outside this public repository.

## Rules

1. Encrypt on the trusted client before any network request. The Worker must never receive plaintext.
2. Derive the AES-256 key from a user-controlled recovery passphrase using PBKDF2-HMAC-SHA256 with 600,000 iterations and a fresh 16-byte salt per snapshot. Use AES-256-GCM with a fresh random 12-byte nonce. Never upload the passphrase.
3. Keep encryption keys outside GitHub and Cloudflare. Never upload keys, passphrases, API keys, GitHub tokens or recovery codes.
4. Batch related memory entries into one bounded snapshot to reduce commits and API calls.
5. Keep local encrypted snapshots so Offline mode continues to work without internet.
6. Public paths, timestamps, tags, record counts and commit history are metadata; keep them non-sensitive.
7. The current vault is single-user. Do not use it for customer records or multi-tenant data.
8. Cross-device restore uses the same user-controlled recovery passphrase. The passphrase must be retained securely by the user; if forgotten, snapshots cannot be decrypted.

## API

- `GET /v1/memory/snapshots`
- `POST /v1/memory/snapshots`
- `GET /v1/memory/snapshots/{uuid}`

The API lists newest snapshots first and supports cross-device restore when the user enters the same recovery passphrase on each device.

The Cloudflare Worker forwards directly to GitHub's Contents API. It does not use KV, D1, R2, Durable Objects or Vectorize.
