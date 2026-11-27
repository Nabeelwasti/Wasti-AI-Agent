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


## Current Android chat-data controls

- Conversation history is stored only in the app-private directory and encrypted with AES-256-GCM using a non-exportable Android Keystore key.
- AtomicFile protects history writes against partial replacement. The app caps the store at 100 sessions, 500 turns per session and 5 MB.
- Chat history is not automatically uploaded, included in GitHub memory snapshots or backed up to a remote provider. The user can export one conversation as Markdown to a location selected through Android's document picker; that export is readable plaintext at the chosen destination.
- The encrypted chat store and passphrase-encrypted memory snapshot format are separate systems with separate keys and recovery behavior.
- Model output is treated as untrusted text. The Markdown renderer does not execute HTML, scripts or model-generated actions.

## Current gateway controls

- Customer mode is the default when `NEXUS_DEPLOYMENT_MODE` is absent and fails closed without the `NEXUS_RATE_LIMIT` binding.
- Scoped client keys cannot access the personal-memory vault; only the master gateway key can.
- Provider endpoints require HTTPS, reject URL credentials/query/hash and block localhost/local/internal names, IPv6 literals and private/reserved IPv4 literals. Provider configuration is owner-controlled. Hostname checks alone do not guarantee protection against DNS rebinding; only trusted provider endpoints should be configured.
- SSE streaming is restricted to one explicitly selected model. Auto/Hybrid streaming is rejected rather than weakening fallback or falsely reporting progress.
- The Worker validates inline image MIME/base64, image count and size, total text and request size; remote image URLs are rejected.
- The Cloudflare Worker is a relay only. It has no Workers AI binding and stores no model weights.

## Standards used as engineering baselines

- OWASP MASVS: storage, cryptography, authentication, network, platform, code, resilience and privacy controls. This project has not been formally MASVS-assessed or certified.
- OWASP API Security Top 10: object/function authorization, authentication, resource consumption, security configuration and SSRF controls.
- OWASP Top 10 for LLM Applications: prompt injection, sensitive-information disclosure, insecure output handling, excessive agency, supply-chain risk and unbounded consumption. No general-purpose tool execution is enabled; a future tool system must use explicit external authorization and approval.
- GitHub CodeQL security-extended analysis runs for Java/Kotlin, Python and JavaScript/TypeScript. A successful scan uploads analysis; findings still require human triage.

## Known security gaps

- No app-level biometric lock for chat history; device lock and Android app sandbox remain the current protection boundary.
- No encrypted cross-device chat-history export/import or recovery drill.
- No per-customer production tenant provisioning, incident response, alerting or retention/deletion workflow is certified.
- CodeQL findings have not yet been individually triaged. Do not equate successful workflow completion with zero vulnerabilities.
