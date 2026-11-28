# WASTI NEXUS roadmap and release gates

A checkbox is complete only when the implementation exists and evidence is recorded in [STATUS.md](STATUS.md). The project remains one monorepo; routine implementation work should be committed directly to `main` in small, sequential, tested commits.

## Repository security

- [x] Scheduled and push-triggered CodeQL security-extended analysis for Java/Kotlin, Python and JavaScript/TypeScript; successful three-language run recorded in [STATUS.md](STATUS.md).
- [ ] Review and triage CodeQL findings; resolve all confirmed high/critical findings before production customer deployment.

## Android client

- [x] Kotlin/Compose app shell and local-first routing.
- [x] Native GGUF import, one-model lifecycle, automatic selected-model loading and visible model health.
- [x] Task-aware local model selection and resource-aware routing.
- [x] Local token streaming and Stop action.
- [x] Remote HTTP cancellation.
- [x] Copy/select/edit chat message controls.
- [x] Safe Markdown rendering for headings, emphasis, quotes, lists and fenced/inline code.
- [x] Gateway collaboration metadata panel using real provider outputs and orchestration metadata.
- [x] Bounded text/code attachments for local and remote prompts.
- [x] Bounded JPEG/PNG/WebP attachments for remote vision-capable providers.
- [ ] Real-device regression suite: install/upgrade, all model states, RAM, thermal, battery, airplane mode, Wi-Fi/LAN, mobile data and encrypted history recovery.
- [x] Encrypted local conversation history, search, open, rename, delete and Markdown export (AES-256-GCM / Android Keystore; local-only; no cross-device restore).
- [x] SSE remote token streaming for explicitly selected single models with upstream cancellation.
- [ ] Auto/Hybrid SSE progress, provider fallback and real-time inter-agent progress events.
- [x] Narrow deterministic device actions with per-action confirmation and conservative destructive-label rejection.
- [x] On-demand active notification reading with explicit Notification access; no background event storage or model forwarding.
- [ ] General permission-scoped tool registry, sandboxing, durable action audit, and revocation.
- [ ] General model-generated tool plans, multi-step execution, cancellation and postcondition verification.
- [x] Local persistent reminder manager with notification permission, inexact alarms, reboot/time-change restoration and delete/cancel.
- [x] Deterministic, strict natural-language reminder intents (relative minutes/hours/days; today/tomorrow with AM/PM) with an explicit confirmation dialog and local persistence.
- [ ] General model tool executor for other phone actions; reminder parsing is a narrow first tool, not general autonomy.
- [x] Integrate bounded PDF/OOXML/ODF text extraction and bundled on-device OCR/coarse visual labels into attachments and shared-storage search; scanned-PDF page OCR remains separate.
- [ ] Audio/video transcription, image generation and training runtimes (only when a verified implementation and licence exists).

## Gateway and orchestration

- [x] OpenAI-compatible model catalogue and chat-completion API.
- [x] Configurable self-hosted provider adapters and licence-aware model catalogue.
- [x] Auto task routing with capability matching and sequential fallback.
- [x] Hybrid parallel independent answers, bounded peer review and synthesis.
- [x] Truthful collaboration metadata and failure reporting.
- [x] Authentication, request size limits, provider timeouts and CORS allowlist.
- [x] Vision payload validation, capability-gated routing and bounded base64/image-size checks in Worker source.
- [x] Propagate client cancellation to upstream provider requests.
- [x] Single-model SSE stream proxy and Android incremental rendering.
- [x] Reject private/reserved provider endpoint addresses; require HTTPS and reject credential-bearing URLs.
- [x] Default customer-mode rate limiting and master-only personal-memory access.
- [x] Deploy latest Worker source and verify deployed version while preserving secrets/rate-limit bindings.
- [ ] Configure an owner-controlled provider catalogue and provider secret; the deployed Worker currently has no inference models configured.
- [ ] Authenticated end-to-end text inference smoke test against a real configured provider.
- [ ] Authenticated end-to-end vision inference smoke test against a verified vision provider (requires provider configuration).
- [ ] Auto/Hybrid SSE progress and live inter-agent events; single-model SSE is complete.
- [ ] Per-customer tenant isolation, quotas, operational monitoring and alerting.

## Model archive and governance

- [x] Preserve the original twelve pinned GGUF archive entries and their manifests.
- [x] Extended candidate registry and explicit licence/usage review status.
- [x] Android GGUF metadata validation and pinned Qwen starter download/Drive copy flow.
- [ ] Exact checkpoint, immutable revision, licence evidence, checksum and runtime adapter for every additional model.
- [ ] Per-model task quality, memory, speed, thermal and battery benchmarks.
- [ ] Verified vision, speech, image-generation and fine-tuning models/runtimes before enabling those tasks.
- [ ] Restore/interruption/retry/checksum-rejection tests for the Drive model archive on a physical device.

## Memory, tools and customer service

- [x] Client-side encrypted local memory snapshots.
- [x] Optional encrypted snapshot sync/restore API source.
- [ ] Live upload, restore and cross-device recovery drill.
- [ ] Documented retention, deletion and export policy.
- [ ] Permission-scoped tool registry and approvals.
- [ ] Sandboxed execution and complete audit trail.
- [ ] Customer identity, tenant isolation, key lifecycle, rate limits and abuse controls.
- [ ] Monitoring, incident response, backups and recovery runbooks.

## Release gates

A release can be signed after CI passes, but the product must not be labelled fully production-ready until:
1. Main CI and permanent-key signed release pass for the exact source revision.
2. Target-device tests pass without data loss or regression.
3. Updated Worker source is deployed and authenticated provider requests pass.
4. Memory recovery, security and operational drills pass.
5. All advertised model and modality capabilities have real evidence.
