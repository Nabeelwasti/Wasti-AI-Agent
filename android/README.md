# Android client

Native Kotlin + Jetpack Compose app with online single/Hybrid routing and a local GGUF inference path.

## Local inference

- Runtime: Arm AI Chat `com.arm:ai-chat:0.1.0`, an Android library built on llama.cpp.
- Model format: GGUF.
- Import: Android Storage Access Framework; selected GGUF files are copied into app-private storage and their metadata is parsed before install.
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

**Important:** in-app model catalogue downloads, resumable background transfers and automatic SHA-256 verification on imported files are not implemented yet. They remain explicit release gates before we advertise one-tap model installation.

## Online inference

Configure the HTTPS gateway URL and bearer key in the app. The key is encrypted with Android Keystore AES-GCM. Online mode requests the shared NEXUS gateway. The deployed gateway currently exposes Gemma 4 26B A4B through Cloudflare Workers AI and passed an authenticated live chat-generation smoke test. The app can save and restore encrypted conversation snapshots locally, sync/restore ciphertext through the Worker once `NEXUS_GITHUB_TOKEN` is configured, and restore on another device using the same recovery passphrase. The passphrase is not stored or uploaded by NEXUS.

## Toolchain

- JDK 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.2.20
- Android SDK 36
- minSdk 33 (Android 13+)

Open `android/` in Android Studio or run the CI Gradle build. The native library integration has been compiled in CI. Real-device model load, offline generation, RAM/thermal behaviour and upgrade tests are still required before calling offline mode device-certified.
