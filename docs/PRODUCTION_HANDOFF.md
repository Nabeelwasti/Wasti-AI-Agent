# WASTI NEXUS — production handoff

**Current app source:** 0.5.17 (versionCode 22)
**Purpose:** separate work that can be completed in source/CI from steps that require the owner's hardware, hosting decision or physical device.

## What is already verified

- Android source builds and its JVM tests pass in GitHub Actions for the app-code commit.
- The owner-signed 0.5.17 APK was built and its APK signature was verified by CI.
- The Cloudflare Worker has authentication, customer-mode rate limiting, encrypted-memory integration and a self-hosted-provider relay. Its current settings and bindings were inspected without reading secret values.
- Worker tests cover request validation, auth, rate limiting, provider endpoint checks, image handling, cancellation, memory boundaries and the NEXUS function-call protocol.
- Model-generated Android actions remain limited to a fixed allowlist and require an explicit user approval for each action.
- The Worker now rejects function calling unless the selected model explicitly declares the tools capability. This prevents a text-only model from being treated as an agent-capable model.

## Owner actions required before online AI is usable

### 1. Choose where inference will run

The Worker is only a secure relay. It does not contain model weights and does not run inference.

Choose one:
- **Phone only:** use the app's native/offline GGUF runtime. No Cloudflare provider setup or vendor API key is needed. This is the simplest private option, but the model is limited by phone RAM, thermal and battery constraints.
- **Your own computer/server:** run an OpenAI-compatible inference server on a machine you control. It must stay online whenever you want remote inference.
- **Hosted GPU/server:** rent or use a server you control. Open-weight models may be free to download, but compute, electricity, storage, network and hosting are not automatically free.

Before selecting a host, record its OS, CPU, RAM, GPU and VRAM (if any), available disk, and whether it can remain online. Choose the model only after matching those resources to the model's documented requirements.

### 2. Provide the provider details (never send secrets in chat)

For each provider, the configuration needs:
- A public HTTPS OpenAI-compatible base URL ending in /v1.
- The exact upstream model ID returned by that server.
- A provider bearer token, if the server requires one.
- Exact model checkpoint/revision and licence evidence.
- Confirmed capabilities: chat, and only when actually supported, vision and/or tools.

In Cloudflare Worker settings, add the non-secret NEXUS_PROVIDERS_JSON variable using the schema in [Provider setup](PROVIDER_SETUP.md). Add the provider token as the Worker secret named by that provider's api_key_env. Keep the existing NEXUS_API_KEY, NEXUS_GITHUB_TOKEN, NEXUS_RATE_LIMIT, customer deployment mode and memory settings. Do not replace or remove them.

Do not paste the provider token, master NEXUS key, GitHub token or keystore passwords into a public issue, repository file or chat. The provider endpoint and model ID are safe to share; secrets belong in Cloudflare's secret manager.

After configuration, test in this order:
1. GET /healthz returns healthy.
2. Authenticated GET /v1/models lists only the expected approved model IDs.
3. An authenticated text prompt succeeds through the app.
4. A small image test succeeds only for a verified vision model.
5. Function calling succeeds only for a tested model declaring tools; each Android action still requires approval.
6. Configure at least two distinct approved models before enabling Hybrid.

### 3. Test on the actual Redmi Note 14 Pro

Download the signed APK from the [0.5.17 signed release workflow](https://github.com/Nabeelwasti/Wasti-AI-Agent/actions/runs/46123389034). Download the wasti-nexus-signed-release-apk artifact ZIP and extract the APK. Install it as an upgrade over the existing NEXUS installation; do not uninstall first or clear app data.

Run this checklist and report any failure with the screen recording or exact error text:
- App opens; existing chat history and settings remain present.
- Install one compatible GGUF, load it, chat in airplane mode, stop generation, switch model and reopen the app.
- Test large-model load rejection and low-memory behaviour without repeatedly forcing the phone to crash.
- Test Wi-Fi, mobile data, airplane mode and network loss during generation.
- Test text, PDF, scanned PDF, Office and image attachments within documented size/page limits.
- Test reminders, reboot restoration, notification access, contact lookup, dialer/SMS handoff, location, Health Connect, PackageInstaller, PiP and split-screen.
- Revoke each granted permission in Android Settings and reopen the related feature; the app should fail safely and explain what is missing.
- Test personal memory add, retrieve, delete and clear. Confirm online-memory sharing remains off unless deliberately enabled.
- Test app upgrade without uninstalling and confirm signing identity is unchanged.

Some Android/HyperOS features can behave differently on physical hardware. A CI APK build cannot certify them.

## Security and operations before customer launch

- Review GitHub Security → Code scanning alerts. A successful CodeQL workflow means analysis ran; it does not prove there are zero alerts.
- Decide whether NEXUS is personal-only or will serve other users. Customer deployment needs tenant identity/isolation, scoped client keys, quotas, abuse controls, data retention/deletion rules and an incident-response owner.
- Establish provider uptime/monitoring, request and cost limits, backups and a recovery drill for encrypted memory.
- Back up the Android release keystore in at least two secure offline locations. Losing it can prevent future signed upgrades.
- Keep model checkpoint, revision, licence and capability evidence for every model. Do not advertise unverified models or modalities.

## What cannot be completed remotely

No agent can honestly perform the final Redmi/HyperOS acceptance test without the physical phone, and no online model can be enabled without an inference host and its configuration. Those are real external dependencies, not missing code switches. Until these gates pass, describe 0.5.17 as a **signed release candidate**, not a fully production-certified product.
