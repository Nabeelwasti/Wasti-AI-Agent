# WASTI NEXUS model distribution and archive policy

## Keep model weights separate from the application source

Do not commit GGUF weights to ordinary Git history. GitHub rejects ordinary Git files over 100 MB, and Git LFS has separate storage/bandwidth quotas. GitHub Release assets support files smaller than 2 GiB each; this is suitable for selected quantized files only when the model licence permits redistribution. See the official [GitHub large-file guidance](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github) and [Release asset limits](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

Cloudflare Workers AI is an inference service, not a user-uploaded GGUF model host. It runs the models in Cloudflare's catalogue through the AI binding. The NEXUS Worker must not attempt to upload or execute arbitrary custom GGUF files on Workers AI.

## Recommended archive layout

- **Source repository:** Kotlin/C++ source, tests, model registry, provenance, licence notices, SHA-256 manifests and download scripts.
- **Private owner archive:** original checkpoints, fine-tuned adapters, training data and checkpoints. Google Drive or a private R2 bucket may be used for this purpose.
- **Public app-download origin (optional):** a dedicated R2 Standard bucket with a custom domain and only files whose licences permit redistribution. Publish a signed/immutable JSON manifest containing model ID, exact filename, byte length, SHA-256, quantization, context recommendation, ABI/runtime requirements, source revision, licence URL and download URL.
- **GitHub Release assets (optional):** only when the individual file is under 2 GiB and redistribution is permitted.
- **Android:** download only a selected model into app-private storage; verify byte length and SHA-256 before exposing it to the inference runtime.

R2's current Standard free tier is 10 GB-month storage, 1 million Class A operations/month and 10 million Class B operations/month, with no internet egress charge. It is not unlimited storage or unlimited operations. R2 storage is a separate product from Workers AI inference. See [official R2 pricing](https://developers.cloudflare.com/r2/pricing/).

Do not enable public bucket access on a private archive. A public app-download bucket should contain only approved redistributable artifacts, never personal memory, customer data, secrets or private training data.

## Sources that do not require Hugging Face

A model's upstream publisher or quantization maintainer may publish files through ModelScope, a project website, or a GitHub Release. Prefer a publisher-controlled source and pin the exact revision plus SHA-256. The registry and downloader must record the actual artifact source, not merely a family name.

Ollama's public library is a useful model catalogue, but its local blob API documents operations against an Ollama server. It is not automatically a stable public direct-download URL for a standalone GGUF file. Do not treat an Ollama tag/manifest as a verified Android GGUF asset without resolving and validating the actual blob and its model metadata.

Hugging Face remains one optional source for public and gated checkpoints; it is not required as a runtime dependency. For public Qwen GGUF artifacts, the acquisition script can use the immutable public repository revision and pinned SHA-256. Gated sources must never be bypassed.

## Android acquisition requirements

The Android app currently supports manual GGUF import and validation. It does not yet include a built-in model catalogue downloader or background-resume UI. Do not claim in-app model downloads are implemented until the app has:

1. A signed or otherwise integrity-protected manifest.
2. HTTPS-only URLs and an allow-list of approved hosts.
3. Resumable HTTP range downloads with partial-file handling.
4. Foreground/user-visible progress and cancellation.
5. Sufficient-storage checks before and during download.
6. Exact content-length and SHA-256 verification before atomic promotion from `.partial` to `.gguf`.
7. A cancellation/retry/reboot recovery test.
8. GGUF metadata parsing and runtime/device-fit preflight after download.
9. Licence notices presented before any restricted model is downloaded.

Android WorkManager supports long-running work with a foreground notification, but on Android 16 it can still be subject to job quotas. For a user-initiated large model transfer, evaluate Android's user-initiated data-transfer job guidance and test the target API levels. See [Android long-running work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## Training and fine-tuning

A phone browser can control a hosted notebook, but the phone does not perform the GPU training itself. Google Colab free GPU availability, model access, runtime duration and disk capacity are not guaranteed; a T4 GPU is not guaranteed for every free session. Fine-tune an eligible base model with a small LoRA/QLoRA adapter, checkpoint to storage frequently, export a compatible GGUF only after testing, and preserve base-model plus adapter provenance and licences. Never claim a model is "trained by NEXUS" unless the exact training run, data rights, adapter and evaluation evidence are retained.

## Current state

- One phone-sized Qwen2.5-0.5B Q4_K_M candidate is pinned with an upstream SHA-256.
- No model weights are hosted by this repository or the deployed Worker.
- The Android app imports a model manually; in-app downloads are a future implementation.
- A dedicated NEXUS R2 bucket and public model-download domain have not been provisioned.
