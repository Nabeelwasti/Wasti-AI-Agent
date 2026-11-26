# WASTI NEXUS — model licensing, hosting and activation policy

Last reviewed: 2026-11-27. Engineering guidance, not legal advice. The authoritative licence is the licence file attached to the exact checkpoint and immutable revision selected for distribution.

## A weights licence is not a hosted inference entitlement

Apache-2.0 and MIT model-weight licences generally permit commercial use, modification and redistribution subject to their notice terms. They do not guarantee free vendor APIs, unlimited tokens, unlimited GPU compute, unlimited concurrency, identical terms for every family variant, or rights to every dataset, tokenizer, adapter, quantization, voice or output.

A permissive weight licence makes the checkpoint's weights open-licensed; it does not automatically make the complete AI system open source. Training code, training data, data provenance, evaluation pipeline, tokenizer, inference code and multimodal components may have separate publication/licence status. The registry therefore labels `open_weights`, `open_source_code_and_weights`, and review-gated entries separately.

Self-hosting avoids per-token charges from a model vendor, but hardware, power, cooling, networking, maintenance and capacity remain finite. Hosted APIs are separate services and may be paid, rate-limited or unavailable.

## Licence policy

| Licence/status | NEXUS decision |
|---|---|
| Apache-2.0 | Generally eligible for commercial use after exact checkpoint verification; retain licence and NOTICE/copyright material where supplied. |
| MIT | Generally eligible after exact checkpoint verification; retain copyright and MIT licence text. |
| Apache/MIT + usage policy | Review both the licence and applicable usage policy. |
| Community / modified / custom | Hold until exact distribution, attribution and commercial conditions are reviewed. |
| Non-commercial | Do not enable in a commercial product. |
| Unknown / pending | Do not expose to customers or automatically route tasks to it. |

White-labeling means WASTI NEXUS can own its UI identity. It does not mean removing upstream notices from redistributed weights or claiming to have created upstream models. Preserve creator, source, licence and attribution metadata in the app's Open Source Licences section and archive.

## Mobile and edge candidates

- **Gemma 4 E2B/E4B:** Apache-2.0 on the official model cards. Mobile/edge candidates only; Android runtime, GGUF, multimodal paths, memory, thermal and battery need device validation.
- **Qwen3 1.7B/4B:** Apache-2.0 on exact Qwen3 cards. Qwen3 4B BF16 is about 8 GB before runtime overhead; use a verified quantized artifact for mobile.
- **Phi-4-mini-instruct 3.8B:** MIT. The official Transformers card uses `trust_remote_code`; do not assume Arm/llama.cpp GGUF compatibility.
- **Granite 4.1 3B:** Apache-2.0 edge candidate; verify architecture/runtime and measured memory.
- **Whisper Tiny/Base:** MIT; offline after weights are downloaded. Benchmark Urdu, Roman Urdu, English and noisy audio.
- **Piper:** MIT engine, but each voice model has its own licence.
- **Kokoro-82M:** Apache-2.0 weights and MIT inference code; published voicepacks are English-focused. Do not promise Urdu TTS without a suitable licensed Urdu voice.

These are candidates, not certifications for the Redmi Note 14 Pro.

## Workstation, server and cluster models

| Model | Licence evidence | Hosting reality |
|---|---|---|
| Qwen3.8-27B | Apache-2.0 | Official BF16 repository about 55.6 GB; workstation/server. |
| DeepSeek V4.1 Flash | MIT weights | 763B total parameters; multi-GPU/cluster. Hosted API is separate and metered. |
| gpt-oss-20B/120B | Apache-2.0 + OpenAI usage policy | Suitable server hardware; 120B is large-memory. |
| Mistral Small 4 | Apache-2.0 | 119B total / 6.5B active; official docs estimate ~60 GB VRAM at FP4. |
| Devstral Small 2 24B | Apache-2.0 | Workstation/server coding agent. |
| GLM-5.3-Flash | MIT | 320B total / 18B active; cluster-scale. Hosted API is separate. |
| Granite 4.1/4.2 8B and 30B | Apache-2.0 | Workstation/server; significant RAM/VRAM. |
| OLMo 3.1 7B/32B | Apache-2.0 for exact checkpoints | Server candidates. |
| Muse Glimmer 30B | Apache-2.0 | Mac/PC with consumer GPU; not mobile. |
| MiMo-V2.6 Flash/Pro | MIT for exact published checkpoints such as MOPD Flash and Pro-RL | Large multimodal server candidates; do not infer licences for forks. |
| Tencent Hunyuan Hy3 | Apache-2.0 | 295B total / 21B active; cluster-scale. |

MoE active parameter count is not full model memory. Full weights, KV cache, context, quantization, runtime buffers and concurrency determine the real footprint.

## Restricted or held families

- **Llama 4 Scout/Maverick:** Llama 4 Community License, not Apache/MIT. Follow its conditions, including the prominent `Built with Llama` display where required, and its other distribution/use terms. Not eligible for this permissive-only collection.
- **Kimi K3 and MiniMax M3:** exact official checkpoint terms and alleged attribution/revenue thresholds must be verified. NEXUS marks them review-required; secondary blog summaries are not legal evidence.
- **FLUX.1 [dev]:** non-commercial licence; not approved for commercial NEXUS use. **FLUX.1 [schnell]** is Apache-2.0 and is the commercial candidate.
- **Qwen Flash-Next:** do not assume it has the Qwen3.8-27B Apache-2.0 licence.
- **DeepSeek R1 Distill:** inspect the exact distilled checkpoint and base-model licence.
- **Piper voices:** engine licence does not grant every voice.

## Hosted and free-tier distinctions

- Self-hosted Apache/MIT weights: no model-vendor token fee on your own machine, but hardware/power limits remain.
- DeepSeek, Z.ai, Mistral and other hosted APIs: separate service terms, pricing and rate limits.
- Cloudflare Workers AI: disabled in NEXUS.
- Cloudflare Worker relay: optional only; it has platform CPU, memory, request and plan limits.
- Google Colab free GPU + Unsloth: availability, GPU type, quota and session duration are dynamic and not guaranteed.
- Google Drive: user-controlled archive via Android SAF; it does not run inference.

## Activation checklist

Every model remains a candidate until all checks pass:

1. Pin the exact official checkpoint and immutable revision.
2. Capture the complete licence, NOTICE, usage policy and voice-specific terms.
3. Record original creator, source, parameter count, file size and SHA-256.
4. Verify tokenizer, chat template, architecture, quantization, projector and context requirements.
5. Calculate working-set RAM/VRAM and disk requirements.
6. Run offline smoke tests and task-specific evaluations.
7. Benchmark target hardware for latency, temperature, battery and crash stability.
8. Enable customer routing only with `commercial_allowed=true` and `license_status=verified` or `approved`.

Machine-readable source: [extended-model-registry.json](../models/extended-model-registry.json). The original twelve-model archive remains separate: [nexus-model-catalog.json](../android/app/src/main/assets/nexus-model-catalog.json).

## Primary source references

- Gemma 4: https://huggingface.co/google/gemma-4-E2B and https://huggingface.co/google/gemma-4-E4B
- Qwen3 4B: https://huggingface.co/Qwen/Qwen3-4B
- Qwen3.8 27B: https://huggingface.co/Qwen/Qwen3.8-27B
- Phi-4-mini: https://huggingface.co/microsoft/Phi-4-mini-instruct
- Granite: https://www.ibm.com/granite/docs/models/granite4-1
- gpt-oss: https://openai.com/open-models/ and https://huggingface.co/openai
- Mistral Small 4: https://docs.mistral.ai/models/mistral-small-4
- GLM-5.3-Flash: https://huggingface.co/zai-org/GLM-5.3-Flash
- Muse Glimmer: https://huggingface.co/meta-models/Muse-Glimmer-30B
- Hunyuan Hy3: https://github.com/Tencent-Hunyuan/Hy3
- DeepSeek V4.1 Flash: https://huggingface.co/deepseek-ai/DeepSeek-V4.1-Flash
- FLUX.1 schnell: https://huggingface.co/black-forest-labs/FLUX.1-schnell
- Kokoro: https://huggingface.co/hexgrad/Kokoro-82M
- Whisper: https://github.com/openai/whisper
- Llama 4 licence: https://www.llama.com/llama4/license/
- Colab FAQ: https://research.google.com/colaboratory/faq.html
