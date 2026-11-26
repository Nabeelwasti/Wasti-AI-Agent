# WASTI NEXUS — verified implementation status

**Snapshot:** 2026-11-26  
**Current main commit:** `7bbb6058e082fba6390fc249714f16d7098dce2f`  
**Android release:** `0.4.0` (`versionCode 4`)  
**State:** Release candidate — physical-device validation pending

## Latest merged work and CI evidence

- [PR #28 — Complete twelve-model WASTI NEXUS library and Google Drive archive](https://github.com/Nabeelwasti/Wasti-AI-Agent/pull/28) merged to `main` as `7bbb6058e082fba6390fc249714f16d7098dce2f`.
- [Main CI run #45874322916](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45874322916) completed successfully: Python/registry tests, 12/12 Hugging Face model-manifest audit, Cloudflare Worker syntax/tests, Android debug build, Android JVM tests and deployed authenticated gateway smoke test all passed.
- [Signed Android release run #45874322911](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45874322911) completed successfully. The protected permanent keystore was decoded, the 0.4.0 release APK was assembled, APK signature verification passed, and the signed artifact was uploaded.
- [Download signed APK artifact](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/45874322911/artifacts/15144522128) — artifact `wasti-nexus-signed-release-apk`, 17,327,456 bytes, expires **2026-12-26**. Open the artifact link, download the ZIP and extract the APK.
- APK signature: v2 verified; RSA 4096-bit key. Certificate SHA-256 is `10a4e2d8278c946c8af3584b01045c8d6405430a9598f873a17a1201544e8636`, matching the previously established permanent signing certificate. Subject: `CN=Syed Nabeel wasti, OU=Wasti Nexus, O=ThriveBridge Growth Solutions, L=Lahore, ST=Punjab, C=PK`.
- The signed-release workflow now runs automatically on trusted pushes that change Android/Gradle build inputs; manual dispatch remains available. Documentation-only changes do not trigger another APK build.

## Product architecture

- One monorepo: https://github.com/Nabeelwasti/Wasti-AI-Agent, default branch `main`.
- Native Kotlin/Jetpack Compose Android app, package `ai.wasti.nexus`, min SDK 33, target SDK 36.
- Arm AI Chat `com.arm:ai-chat:0.1.0` provides in-process GGUF/llama.cpp local inference. Termux is not required at runtime.
- Google Drive is the sole private model archive. No model weights are stored in GitHub or Cloudflare R2.
- Modes: **Auto** prefers a compatible installed local model and may use the optional authenticated hosted gateway if local inference fails; **Offline** uses local inference only; **Online** explicitly uses the hosted gateway.
- Drive is storage, not an inference server. The twelve Drive models run locally on the phone. An online endpoint for all twelve models in future customer products would require a compute host; Drive alone cannot execute model inference. There is no unlimited free hosted GPU service.

## Twelve-model library

The curated collection contains twelve compact open-weight models under Apache-2.0 or MIT licence evidence:

1. Qwen2.5 0.5B Instruct
2. Qwen2.5 1.5B Instruct
3. Qwen2.5 Coder 1.5B
4. Qwen3 0.6B
5. Qwen3 1.7B
6. SmolLM2 135M
7. SmolLM2 360M
8. SmolLM2 1.7B
9. TinyLlama 1.1B Chat
10. Granite 3.1 2B Instruct
11. DeepSeek R1 Distill 1.5B
12. OLMo 2 1B Instruct

All twelve exact GGUF artefacts now have:
- Immutable 40-character Hugging Face source revisions.
- Exact filenames and Q3_K_M or Q4_K_M quantization.
- Exact byte lengths and SHA-256 values.
- Permissive base-model/GGUF licence metadata evidence.
- A minimum available-RAM estimate and a separate `pending_device_test` runtime marker.

The total exact model-file size is **9,586,646,112 bytes (about 8.93 GiB)**. CI audits all twelve pinned files against their immutable public source revisions and confirms size, SHA-256, licence evidence and non-gated access. All twelve are marked ready for archive/download; this does **not** certify inference performance on the Redmi Note 14 Pro.

WASTI NEXUS is the app and collection brand, not the original model author. Original names, authors, repositories, licences and notices are preserved. “Open-weight” does not guarantee that every training-data/process element meets every formal Open Source AI definition. See:
- [Model library](WASTI_NEXUS_MODEL_LIBRARY.md)
- [Third-party model notices and exact manifests](THIRD_PARTY_MODEL_NOTICES.md)

## Google Drive archive and offline install

- The app uses Android's system folder picker (Storage Access Framework) to grant access only to the selected private Drive folder.
- The app attempts to persist read/write access. Read-only access still permits installation from files already in Drive; the archive-all action requires write permission.
- The **Archive all 12 models to Drive · 9.59 GB** action streams one pinned public Hugging Face GGUF at a time into the selected Drive folder. It requires internet and Drive write permission, but no Hugging Face token, AI API key, Google Drive API key, OAuth client or service-account key.
- Each source transfer checks exact byte length and SHA-256 before promoting its temporary file. Existing files are SHA-256 checked before being skipped. Incomplete temporary files are removed; interrupted transfers restart from zero rather than claiming byte-range resume.
- **Download from Drive** copies a selected file into app-private storage and verifies exact size and SHA-256 again before installation.
- Wi-Fi is strongly recommended for the 9.59 GB archive. Keep the app open during the sequential archive. If the Drive provider does not grant write access or cannot handle large streamed files, the app reports this and the same pinned filenames can be uploaded through the Google Drive app/desktop client.
- Local inference after installation works without Drive, an AI API key or Cloudflare availability.

## Cloudflare hosted gateway

- Worker: `wasti-nexus-gateway`; base URL: https://wasti-nexus-gateway.wastinabeel99.workers.dev
- Two optional Cloudflare Workers AI routes are configured: `@cf/google/gemma-4-26b-a4b-it` and `@cf/qwen/qwen3.8-27b`.
- The post-merge main CI authenticated generation smoke test passed for the deployed gateway. No raw provider key is embedded in the APK.
- Cloudflare usage is metered; free-tier allowances are not unlimited and may change. The gateway is optional and is not required for Drive storage or local inference.
- The two hosted gateway models are not the same thing as the twelve GGUF files archived in Google Drive. To serve all twelve remotely, a separately hosted inference runtime with suitable compute is required.

## Memory, security and operational boundaries

- Android memory snapshots use PBKDF2-HMAC-SHA256 and AES-256-GCM with a user-controlled recovery passphrase.
- The Worker transports encrypted envelopes; the passphrase is not sent to the Worker. Live remote write/list/read/restore and cross-device recovery still need a full drill.
- The personal repository is not a tenant-isolated customer data service. Before public customer use, implement end-user authentication, short-lived token exchange, tenant isolation, strict quotas, abuse controls, monitoring, privacy/retention disclosures, incident response and recovery procedures.
- Preserve all upstream licence/copyright notices. The registry conservatively does not approve redistribution. Do not expose or redistribute model binaries to customers until exact licence texts and intended distribution rights receive a separate review.

## Remaining production gates

1. **Drive provider test:** on the Redmi, connect the private Drive folder, confirm persisted write permission, run Archive all 12 over Wi-Fi, interrupt one transfer and retry, then verify all twelve files appear.
2. **Integrity test:** install at least the Qwen2.5 0.5B starter from Drive, then deliberately test a wrong-size/wrong-checksum file and confirm rejection.
3. **Offline inference:** enable airplane mode and run a local chat turn. Test unload/reload and process restart.
4. **All-model device fit:** load each model individually and record available RAM, load success, context length, latency, thermal behaviour and battery impact. The 2B-class model may not fit comfortably on every Redmi Note 14 Pro variant.
5. **Upgrade/signing:** install the signed 0.4.0 APK over the previously signed app and confirm the permanent signing certificate allows the upgrade.
6. **Memory recovery:** complete encrypted snapshot write/list/read/restore and cross-device recovery.
7. **Customer service readiness:** complete authentication, tenant isolation, quotas, abuse controls, monitoring, privacy and incident response before exposing customer-facing service.

## Honest conclusion

The 0.4.0 app is merged into `main`, the twelve model manifests are pinned and CI-audited, the one-tap Drive archive path is implemented, the main CI is green, and a permanent RSA-4096-signed APK with the established certificate has been built and uploaded. It is a strong **release candidate**, not yet fully production-certified: actual Google Drive provider writes and local inference/performance on the physical Redmi remain untested. Those are now the remaining owner-side validation steps.
