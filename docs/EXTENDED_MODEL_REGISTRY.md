# Extended model and capability registry

This registry expands the twelve verified Android archive entries without falsely marking unpinned models as downloadable or runnable. It is separate from `android/app/src/main/assets/nexus-model-catalog.json`.

## Activation rules
- The original twelve GGUFs remain intact.
- New entries are discovery candidates until exact checkpoint, immutable revision, size, SHA-256, licence, tokenizer, architecture, runtime adapter and benchmark are verified.
- Mobile candidates must pass device memory, thermal, latency, battery and crash tests.
- Large reasoning/coding models belong on a remote inference host; they are not downloaded automatically to a phone.
- Whisper, Piper and Kokoro are separate speech pipelines; FLUX.1 schnell is an image-generation pipeline; Unsloth/Colab is a training workflow.
- Customer deployment is blocked until commercial rights and upstream usage-policy review are complete.

## Added model families

See [extended-model-registry.json](../models/extended-model-registry.json): Gemma 4 E2B/E4B, Qwen3.5 4B, Phi-4-mini, Granite 4.1 3B/8B/30B, Qwen3.8 27B, DeepSeek V4.1 Flash, gpt-oss 20B/120B, Mistral Small 4, Devstral Small 2, GLM-5.3-Flash, OLMo 3.1 7B/32B, Muse Glimmer, Xiaomi MiMo-V2.6 Flash/Pro, Tencent Hunyuan Hy3, FLUX.1 schnell, Whisper Tiny/Base/Small, Piper, Kokoro and Unsloth/Colab.

## Placement
1. Phone: only small quantized chat/coding models and speech models that fit measured RAM and thermal budgets.
2. Termux: optional local Linux tools and inference processes; it does not bypass Android app sandboxing.
3. Cloudflare edge: authentication, quotas, request validation, routing, streaming relay and job dispatch.
4. Dedicated inference host: large LLMs, vision, image generation, long-context work and training.
5. Google Drive: private archive for models, datasets, adapters, evaluations and exported GGUFs. No Cloudflare R2 or other Cloudflare object storage.

## Automatic orchestration
The orchestrator classifies task intent, selects only configured and licence-eligible agents with matching capabilities, and attempts them in priority order. Failed specialists fall back to configured general chat agents. Missing modalities return an explicit unavailable result; a text model is not presented as completing image, audio or training work.

## Termux and Android control
A future paired bridge must be loopback-only, explicitly paired, permission-scoped, auditable and require approval for sensitive actions. Termux:API exposes selected Android APIs to Termux scripts; it does not grant unrestricted access to other apps' private files or OS internals. No unrestricted or silent control is promised.

## Cloud compute
Cloudflare Workers is the edge control plane, not a GPU host. Heavy inference requires a separately provisioned inference host. Use quotas, spending caps, queue limits, deadlines, cancellation and circuit breakers. Never claim unlimited free compute.
