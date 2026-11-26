# Initial model licence review

Research date: 2026-11-26. This is an engineering screening note, not legal advice. Before any redistribution or customer deployment, preserve the exact upstream licence file and model card from the pinned checkpoint revision and complete a formal review.

## Candidate checkpoints

| Family | Candidate | Initial licence finding | Customer/commercial use |
|---|---|---|---|
| Llama | [meta-llama/Llama-3.2-1B-Instruct](https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct) | Meta Llama 3.2 Community License; gated access and attribution obligations | Conditional; comply with agreement, AUP and attribution |
| Qwen | [Qwen/Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct) | Apache-2.0 | Generally permitted with notices |
| Gemma | [google/gemma-4-E2B-it](https://huggingface.co/google/gemma-4-E2B-it) | Apache-2.0 | Generally permitted with notices |
| DeepSeek | [deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B](https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B) | MIT; derived from Qwen 2.5, preserve upstream/base notices | Generally permitted subject to both sets of terms |
| Mistral | [mistralai/Mistral-7B-Instruct-v0.3](https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.3) | Apache-2.0 | Generally permitted with notices |
| Phi | [microsoft/Phi-3.5-mini-instruct](https://huggingface.co/microsoft/Phi-3.5-mini-instruct) | MIT | Generally permitted with notices |
| Granite | [ibm-granite/granite-3.1-2b-instruct](https://huggingface.co/ibm-granite/granite-3.1-2b-instruct) | Apache-2.0 | Generally permitted with notices |
| GLM | [zai-org/glm-4-9b-chat](https://huggingface.co/zai-org/glm-4-9b-chat) | GLM-4 custom terms, attribution and use restrictions | Conditional; review exact agreement before commercial use |
| Command R | [CohereLabs/c4ai-command-r7b-12-2024](https://huggingface.co/CohereLabs/c4ai-command-r7b-12-2024) | CC-BY-NC-4.0 and Cohere acceptable-use terms | **Do not use in customer/commercial products without a separate commercial grant** |
| Falcon | [tiiuae/Falcon3-1B-Instruct](https://huggingface.co/tiiuae/Falcon3-1B-Instruct) | TII Falcon-LLM License 2.0 | Conditional; review full TII terms |
| SmolLM | [HuggingFaceTB/SmolLM2-1.7B-Instruct](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct) | Apache-2.0 | Generally permitted with notices |
| StableLM | [stabilityai/stablelm-2-1_6b-chat](https://huggingface.co/stabilityai/stablelm-2-1_6b-chat) | Stability AI Non-Commercial Research Community License | **Do not use as a hosted customer API without a commercial grant from Stability AI** |

## Important distinctions

- These are open-weight checkpoints, not all OSI-approved open-source models.
- “Free to download” does not mean “free for any commercial use”.
- Private archival copies do not grant ownership of the upstream weights.
- Keep Command R and StableLM disabled in customer-facing agent pools unless written commercial rights are obtained.
- Keep Llama, Gemma, Falcon and GLM conditional until their complete terms, attribution and access conditions are reviewed.
- Apache-2.0 and MIT candidates still require notices, provenance and applicable third-party/base-model notices.

## Evidence links

- Llama model card and agreement: https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct
- Qwen model card: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct
- Gemma 4 model card: https://huggingface.co/google/gemma-4-E2B-it
- DeepSeek model card: https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B
- Mistral model card: https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.3
- Phi model card: https://huggingface.co/microsoft/Phi-3.5-mini-instruct
- Granite model card: https://huggingface.co/ibm-granite/granite-3.1-2b-instruct
- GLM licence: https://huggingface.co/zai-org/glm-4-9b-chat
- Command R model card: https://huggingface.co/CohereLabs/c4ai-command-r7b-12-2024
- Falcon model card: https://huggingface.co/tiiuae/Falcon3-1B-Instruct
- SmolLM model card: https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct
- StableLM model card and licence: https://huggingface.co/stabilityai/stablelm-2-1_6b-chat
