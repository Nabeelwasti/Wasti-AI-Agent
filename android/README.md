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

## Online inference

Configure the HTTPS gateway URL and bearer key in the app. The key is encrypted with Android Keystore AES-GCM. Online Hybrid mode requests the shared NEXUS gateway; the gateway currently has no inference providers configured. The app can save encrypted conversation snapshots locally, restore them on the same device, and sync/restore ciphertext through the Worker once `NEXUS_GITHUB_TOKEN` is configured. Cross-device recovery is not implemented.

## Toolchain

- JDK 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.2.20
- Android SDK 36
- minSdk 33 (Android 13+)

Open `android/` in Android Studio or run the CI Gradle build. The native library integration has been added to source, but a successful APK build and real-device inference test are still required before calling offline mode verified.
