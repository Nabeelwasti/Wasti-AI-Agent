# Security and autonomy policy

## Principles

- Least privilege by default; user-authorized broad capability is not an OS security bypass.
- No secret keys, plaintext personal data, customer records or model weights in Git. Public GitHub storage may contain client-encrypted ciphertext only, with non-sensitive metadata; encryption keys must never be committed.
- Deny-by-default tool registry with explicit schemas and per-tool permissions.
- Separate personal, project and customer tenants at storage and retrieval layers.
- Audit tool invocation, model participation, approvals and data export.
- Rate limits, request size limits, timeouts and bounded agent fan-out.
- Emergency stop and revocation controls belong in the client and service.
- Sensitive or irreversible actions require an explicit user confirmation policy.

## Android realities

Android permissions are granted by the operating system and may require runtime prompts, special settings, foreground-service declarations, role ownership or privileged/system signing. An ordinary app cannot grant itself every permission or bypass platform sandboxing. NEXUS will request only relevant permissions, explain why, and degrade gracefully when unavailable.

## Agent threat model

Treat model output, retrieved documents, tool results and external content as untrusted input. Prompt injection must not grant new capabilities or override policy. Tool arguments are validated against schemas and authorization is checked outside the model. Agents cannot mint credentials, approve their own sensitive actions, disable audit logs or expand their own permissions.

## Deployment checklist

- TLS enabled; authentication required on non-public routes.
- Per-user/per-tenant authorization on every request.
- Secrets in environment/secret manager, never source control.
- CORS allowlist, request limits, rate limits and abuse monitoring.
- No raw chain-of-thought storage or exposure; store concise rationale/provenance instead.
- Backup encryption and restore drills.
- Dependency and container scanning.
- Data retention, deletion and customer export policies documented.


## Encrypted GitHub memory

Memory snapshots use a user-controlled passphrase, PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt), and AES-256-GCM with a random 12-byte nonce. Only ciphertext and non-secret KDF metadata are sent to the Worker/GitHub. The passphrase must never be sent to the gateway or stored in repository files. The same passphrase enables cross-device restore; forgetting it means the snapshots cannot be decrypted. The public repository exposes ciphertext, paths, timestamps, tags and commit history.
