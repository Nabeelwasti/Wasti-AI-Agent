# WASTI NEXUS model library

## Identity and attribution

WASTI NEXUS is the product identity, app interface, model workspace and orchestration layer. The models listed here remain independent upstream models. The app may present them in the WASTI NEXUS collection, but must preserve original model names, authors, source repositories, licence texts, and required notices. Do not claim that Nabeel Wasti or WASTI NEXUS trained or authored these upstream weights.

These are **open-weight models selected for permissive upstream licences**. This description does not claim that every model satisfies every definition of Open Source AI, since training-data and full training-process openness vary.

## Curated twelve-model collection

Candidates use Q4_K_M where available and Q3_K_M for selected smaller repositories whose pinned revision exposes Q3_K_M but not Q4_K_M. Public file-size values in this guide are rounded for readability. All twelve exact GGUF files now have immutable revisions, exact byte lengths and SHA-256 values pinned in the app catalogue and central registry. All twelve are marked downloadable; physical-device runtime validation remains pending.

| # | WASTI NEXUS collection name | Upstream model | Licence | Approx. GGUF size | Current state |
|---:|---|---|---|---:|---|
| 1 | Qwen2.5 0.5B Instruct | Qwen/Qwen2.5-0.5B-Instruct | Apache-2.0 | 491 MB | Ready; exact manifest pinned |
| 2 | Qwen2.5 1.5B Instruct | Qwen/Qwen2.5-1.5B-Instruct | Apache-2.0 | 1.12 GB (Q4_K_M) | Ready; exact manifest pinned; runtime test pending |
| 3 | Qwen2.5 Coder 1.5B | Qwen/Qwen2.5-Coder-1.5B-Instruct | Apache-2.0 | 1.12 GB (Q4_K_M) | Ready; exact manifest pinned; runtime test pending |
| 4 | Qwen3 0.6B | Qwen/Qwen3-0.6B | Apache-2.0 | 414 MB (Q3_K_M) | Ready; exact manifest pinned; runtime test pending |
| 5 | Qwen3 1.7B | Qwen/Qwen3-1.7B | Apache-2.0 | 1.07 GB (Q3_K_M) | Ready; exact manifest pinned; runtime test pending |
| 6 | SmolLM2 135M | HuggingFaceTB/SmolLM2-135M-Instruct | Apache-2.0 | 94 MB (Q3_K_M) | Ready; exact manifest pinned; runtime test pending |
| 7 | SmolLM2 360M | HuggingFaceTB/SmolLM2-360M-Instruct | Apache-2.0 | 271 MB (Q4_K_M) | Ready; exact manifest pinned; runtime test pending |
| 8 | SmolLM2 1.7B | HuggingFaceTB/SmolLM2-1.7B-Instruct | Apache-2.0 | 1.06 GB (Q4_K_M) | Ready; exact manifest pinned; runtime test pending |
| 9 | TinyLlama 1.1B Chat | TinyLlama/TinyLlama-1.1B-Chat-v1.0 | Apache-2.0 | 548 MB (Q3_K_M) | Ready; exact manifest pinned; runtime test pending |
| 10 | Granite 3.1 2B Instruct | ibm-granite/granite-3.1-2b-instruct | Apache-2.0 | 1.55 GB (Q4_K_M) | Ready; exact manifest pinned; runtime test pending |
| 11 | DeepSeek R1 Distill 1.5B | deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B | MIT + Qwen base Apache-2.0 | 924 MB (Q3_K_M) | Ready; exact manifest pinned; runtime test pending |
| 12 | OLMo 2 1B Instruct | allenai/OLMo-2-0425-1B-Instruct | Apache-2.0 | 936 MB (Q4_K_M) | Ready; exact manifest pinned; runtime test pending |

The displayed sizes are rounded for readability; the app uses the exact byte counts and SHA-256 values pinned in `nexus-model-catalog.json`, never these rounded table values.

## Why these models

- **Small-device tier:** SmolLM2 135M, SmolLM2 360M, Qwen2.5 0.5B, Qwen3 0.6B and TinyLlama 1.1B.
- **Balanced tier:** Qwen2.5 1.5B, Qwen2.5 Coder 1.5B, Qwen3 1.7B, SmolLM2 1.7B, DeepSeek R1 Distill 1.5B and OLMo 2 1B.
- **Higher-memory tier:** Granite 3.1 2B. The app's runtime RAM preflight remains authoritative; a file fitting in Drive does not mean it fits in phone RAM.

The Redmi Note 14 Pro must be treated as an 8-GB-class mobile environment unless the exact device variant and available RAM are measured. Start with Qwen2.5 0.5B. Do not promise all models can run concurrently or that larger models will load reliably on the phone. Models are installed and loaded one at a time.

## Sources

- [Qwen2.5 0.5B GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF)
- [Qwen2.5 1.5B GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF)
- [Qwen2.5 Coder 1.5B GGUF](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF)
- [Qwen3 0.6B GGUF](https://huggingface.co/tensorblock/Qwen_Qwen3-0.6B-GGUF)
- [Qwen3 1.7B GGUF](https://huggingface.co/tensorblock/Qwen_Qwen3-1.7B-GGUF)
- [SmolLM2 135M GGUF](https://huggingface.co/tensorblock/SmolLM2-135M-Instruct-GGUF)
- [SmolLM2 360M GGUF](https://huggingface.co/lmstudio-community/SmolLM2-360M-Instruct-GGUF)
- [SmolLM2 1.7B GGUF](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF)
- [TinyLlama 1.1B GGUF](https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF)
- [Granite 3.1 2B GGUF](https://huggingface.co/mradermacher/granite-3.1-2b-instruct-GGUF)
- [DeepSeek R1 Distill 1.5B GGUF](https://huggingface.co/tensorblock/DeepSeek-R1-Distill-Qwen-1.5B-GGUF)
- [OLMo 2 1B Instruct GGUF](https://huggingface.co/allenai/OLMo-2-0425-1B-Instruct-GGUF)
- [Hugging Face licence guidance](https://huggingface.co/docs/hub/repositories-licenses)
- [llama.cpp supported model architectures](https://github.com/ggml-org/llama.cpp)

## Release gate and current verification

All twelve source artifacts now have immutable revisions, exact sizes, SHA-256 values, and permissive base-model licence evidence recorded in the catalogue. CI rechecks those manifests against the public source. The remaining release gate is Android runtime and physical-device validation:

1. Exact GGUF file exists in the stated source repository.
2. Immutable 40-character source revision is pinned.
3. Exact file size in bytes is recorded.
4. SHA-256 is independently verified against the exact upstream artifact.
5. Base-model and quantized-repository licence texts are archived and reviewed for personal use, redistribution and intended commercial use.
6. GGUF architecture, tokenizer/chat template and the bundled Arm AI Chat runtime are verified.
7. Minimum available RAM and conservative context length are set.
8. A test transfer from the owner's private Drive folder passes size/hash checks.
9. A local inference smoke test passes on a supported Android device.

All twelve catalogue entries are now marked `ready` for verified Drive-to-device download. This does not mean the weights are already in the owner's Drive or that every model has passed inference on the Redmi. The owner must place the exact files in the private workspace, and physical-device runtime/RAM/thermal testing remains the final validation gate. Do not rename or remove upstream notices. Do not put model binaries in GitHub or Cloudflare storage; Google Drive remains the private model archive.
