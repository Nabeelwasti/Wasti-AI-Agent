# Roadmap and release gates

This is a living checklist. A task is complete only with implementation and evidence.

## Phase 0 — Repository foundation
- [x] Single monorepo created.
- [x] Initial architecture and security principles.
- [ ] CI and test gates.

## Phase 1 — Orchestration API
- [ ] OpenAI-compatible models and chat endpoints.
- [ ] Configurable provider adapters.
- [ ] Parallel agent analysis and bounded review.
- [ ] Hybrid synthesis and honest participation metadata.
- [ ] Authentication, tenant isolation, rate limiting and observability.
- [ ] Contract and integration tests.

## Phase 2 — Model archive
- [ ] Exact checkpoint selected for each of 12 families.
- [ ] License and redistribution review for every exact checkpoint.
- [ ] Pinned revision, snapshot download and SHA-256 manifests.
- [ ] GGUF/runtime conversion where license and tooling permit.
- [ ] Archive integrity and restore tests.

## Phase 3 — Android
- [ ] Compose application shell and API settings.
- [ ] Local inference runtime integration.
- [ ] Model download, storage budget and lifecycle manager.
- [ ] Hybrid UI and agent activity view.
- [ ] Encrypted Room vault and tenant/project separation.
- [ ] Permission center, tool registry, approvals and audit history.
- [ ] Device compatibility, battery, thermal and offline tests.

## Phase 4 — Cloud and customer products
- [ ] Independently deployable API with custom domain.
- [ ] Tenant authentication and customer workspace isolation.
- [ ] Encrypted backup and tested restore.
- [ ] SDK examples for Android, web and backend clients.

## Release gates
No production-ready claim until builds pass, security checks pass, model participation is verified, restore succeeds, Android real-device tests pass and a public endpoint is protected by authentication and monitoring.
