# Android client

Native Kotlin + Jetpack Compose app with online single/Hybrid routing and a local GGUF inference path.

## Connection modes

- **Auto (default):** tries the online gateway first. If the gateway URL/key is missing, the network request fails, or the gateway returns 408, 429 or a 5xx response, it falls back to the selected local GGUF model when one is loaded.
- **Online:** uses the configured HTTPS gateway only. Authentication/permission errors (401/403) remain visible rather than being silently hidden by local fallback.
- **Offline:** uses the local inference engine directly and does not call the gateway.
- Online conversation context is bounded and contains only prior online turns. Local-only and offline-fallback turns are not sent to the gateway.

## Local inference

- Runtime: Arm AI Chat `com.arm:ai-chat:0.1.0`, an Android library built on llama.cpp.
- Model format: GGUF.
- Import: Android Storage Access Framework; selected GGUF files are copied into app-private storage and their metadata is parsed before install.
- One-tap catalogue install: Qwen2.5-0.5B-Instruct Q4_K_M downloads from its pinned public Hugging Face revision without an AI/provider API key. The app supports HTTP Range resume into an app-private `.part` file, bounded size/storage checks, live progress, full-file SHA-256 verification, GGUF metadata validation and safe finalization that preserves an existing installation. Interrupted network transfers retain their partial bytes for retry; checksum- or metadata-invalid files are rejected and deleted.
- Model manager: multiple installed files can be selected, loaded one at a time, unloaded and removed.
- Runtime lifecycle: load, chat, unload and release native memory.
- Preflight: file size/storage checks and a conservative available-RAM estimate before model load.
- Offline requests use the local inference engine directly and do not call the gateway.

The first phone-sized candidate is Qwen2.5-0.5B-Instruct Q4_K_M (approximately 491 MB). See [../models/README.md](../models/README.md) for the pinned download workflow. The model is not bundled in the APK.


## Obtain the first model without Termux

Termux is **not required** to run NEXUS or perform local inference. The first Qwen2.5-0.5B Q4_K_M GGUF can be downloaded in the Android browser from an upstream model host:

- [Qwen official GGUF repository (Hugging Face)](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/tree/main)
- [Qwen GGUF on ModelScope](https://modelscope.cn/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF)

Choose the exact file `qwen2.5-0.5b-instruct-q4_k_m.gguf` (about 491 MB). The ModelScope page is an alternative host, not yet a checksum-verified mirror in the registry; use the pinned upstream SHA-256 in `models/registry.json` to verify the exact file before relying on it. The Android import flow currently validates GGUF metadata but does not yet compare the imported file against the registry SHA-256.

After the browser download completes, open NEXUS → **Offline → Import GGUF** and select the file from Downloads. NEXUS copies it into app-private storage. Keep at least 1 GB free after import. The guarded Python downloader remains an optional developer/reproducibility tool; it is not part of the Android runtime and does not require Termux on the phone if run on another computer.

**Current limits:** the first in-app one-tap installer is the verified Qwen 0.5B candidate only. Resume is available when the app is reopened and the user taps download again; this is not a background WorkManager transfer. A full multi-family catalogue, and automatic SHA-256 verification for manually imported files are not implemented. Device-level inference, speed, memory and thermal tests are still required.

## Online inference

Online inference is optional. Local model downloads and offline chat do not require a NEXUS API key. Configure the HTTPS gateway URL and a dedicated client bearer key only when you choose to use the protected hosted gateway. The key is encrypted with Android Keystore AES-GCM. Online mode requests the shared NEXUS gateway. The deployed gateway currently exposes Gemma 4 26B A4B through Cloudflare Workers AI; the account-level model call and public gateway health/model-catalogue smoke test passed. An authenticated chat-completion request through the Worker itself has not yet been verified because the GitHub Actions `NEXUS_API_KEY` secret is not configured. The app can save and restore encrypted conversation snapshots locally and sync/restore ciphertext through the Worker using the configured GitHub token, but a live memory round-trip and cross-device recovery drill have not been verified. The passphrase is not stored or uploaded by NEXUS.

## Toolchain

- JDK 17
- Android Gradle Plugin 9.3.1
- Gradle 9.7.0
- Kotlin Compose plugin 2.4.21 (AGP built-in Kotlin)
- Android SDK 36 / Build Tools 36.0.0
- minSdk 33 (Android 13+)

Open `android/` in Android Studio or run the CI Gradle build. The native library integration has been compiled in CI. Real-device model load, offline generation, RAM/thermal behaviour and upgrade tests are still required before calling offline mode device-certified.


## Model health and chat experience (0.5.2)

- The selected app-private GGUF model is loaded automatically after launch, import or model selection; a manual load step is no longer the normal path. A failed load remains visible with the exact runtime error and a retry action.
- The Model Health panel lists all twelve catalogue entries and distinguishes loaded, installed-but-not-loaded, manually imported (GGUF metadata checked but pinned checksum not verified), incomplete transfer and not-installed states. A file in Downloads or a Drive folder is not yet installed into app-private runtime storage until imported/copied by NEXUS.
- Chat messages support text selection, copy actions, and editing a prior user prompt. Active local generation can be stopped; request progress shows task classification and completion/failure state.
- The current native Arm AI Chat integration loads one local GGUF model at a time and exposes text generation only. Local multi-model debate, image understanding, audio, file parsing, background parallel agents and a full tool execution framework are not claimed as implemented by this release. Online Hybrid can orchestrate only the distinct models actually configured on the connected gateway. UI progress reports actual workflow stages, not private chain-of-thought.
- Device-specific load success still depends on Android RAM, thermals and the exact GGUF. A catalogue file being present is not proof that its native runtime can initialize it; the status panel surfaces the load result.
