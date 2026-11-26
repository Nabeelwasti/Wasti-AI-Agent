# WASTI NEXUS third-party model notices

WASTI NEXUS is the app, orchestration layer and curated collection identity. It is not the author or trainer of the upstream model weights below. Preserve original model names, creators, licences, source repositories and attribution. Do not market the third-party weights as proprietary WASTI-trained models.

The catalogue contains open-weight checkpoints whose upstream licence metadata was audited as Apache-2.0 or MIT on 2026-11-26. This is not a claim that the full training data/process meets every formal Open Source AI definition. Commercial use and redistribution remain subject to the exact upstream licence texts and required notices. The registry conservatively marks redistribution as not approved until a separate release review.

## Exact model artefacts

| # | Collection model | Upstream model | Licence evidence | Quant | Exact bytes | Immutable GGUF revision | SHA-256 | GGUF repository |
|---:|---|---|---|---|---:|---|---|---|
| 1 | Qwen2.5 0.5B Instruct | `Qwen/Qwen2.5-0.5B-Instruct` | Apache-2.0 | Q4_K_M | 491,400,032 | `12145bd1d629190a4d44254073650877954d02c9` | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` | [Source](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) |
| 2 | Qwen2.5 1.5B Instruct | `Qwen/Qwen2.5-1.5B-Instruct` | Apache-2.0 | Q4_K_M | 1,117,320,736 | `91cad51170dc346986eccefdc2dd33a9da36ead9` | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` | [Source](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) |
| 3 | Qwen2.5 Coder 1.5B | `Qwen/Qwen2.5-Coder-1.5B-Instruct` | Apache-2.0 | Q4_K_M | 1,117,320,768 | `f86cb2c1fa58255f8052cc32aeede1b7482d4361` | `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046` | [Source](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF) |
| 4 | Qwen3 0.6B | `Qwen/Qwen3-0.6B` | Apache-2.0 | Q3_K_M | 413,978,720 | `40d7f049bd6a2736eccc84a3adc79cc321616da8` | `a87626e7acd86ff57db4fb48f6cbbbfd130c33f1a8093301c3f8b331162487d9` | [Source](https://huggingface.co/tensorblock/Qwen_Qwen3-0.6B-GGUF) |
| 5 | Qwen3 1.7B | `Qwen/Qwen3-1.7B` | Apache-2.0 | Q3_K_M | 1,073,242,208 | `77fee077ec2122a422479246f5cb81dcf6ab8c72` | `79af9a932c6659aabc06d6560b8404063c79f616b136953370f9c67853e162e0` | [Source](https://huggingface.co/tensorblock/Qwen_Qwen3-1.7B-GGUF) |
| 6 | SmolLM2 135M | `HuggingFaceTB/SmolLM2-135M-Instruct` | Apache-2.0 | Q3_K_M | 93,511,232 | `32db44d69cedb731dc0fc96f60e01a86c6f5919d` | `61c69fc5ce91982e26c625d43be5c3c7f0f774da22f4fa4e45c37a80a22ddad4` | [Source](https://huggingface.co/tensorblock/SmolLM2-135M-Instruct-GGUF) |
| 7 | SmolLM2 360M | `HuggingFaceTB/SmolLM2-360M-Instruct` | Apache-2.0 | Q4_K_M | 270,590,592 | `b4c62446992d1feec0ef87d60af9b12ac8922919` | `e55c2a5df7f8f6eed1a843ff709fa8008dbce65d82e57c5c06dec5ee8d060415` | [Source](https://huggingface.co/lmstudio-community/SmolLM2-360M-Instruct-GGUF) |
| 8 | SmolLM2 1.7B | `HuggingFaceTB/SmolLM2-1.7B-Instruct` | Apache-2.0 | Q4_K_M | 1,055,609,536 | `2d4a76a30b4af41ecd395c35725ac11688d4cfe4` | `decd2598bc2c8ed08c19adc3c8fdd461ee19ed5708679d1c54ef54a5a30d4f33` | [Source](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF) |
| 9 | TinyLlama 1.1B Chat | `TinyLlama/TinyLlama-1.1B-Chat-v1.0` | Apache-2.0 | Q3_K_M | 548,404,768 | `917a6091308fa80582a38686dea7c995c1932078` | `a7f5d8894019a758fdd516d4847a4a1fec5b18321d367b593d6d996a115b7bd7` | [Source](https://huggingface.co/tensorblock/TinyLlama-1.1B-Chat-v1.0-GGUF) |
| 10 | Granite 3.1 2B Instruct | `ibm-granite/granite-3.1-2b-instruct` | Apache-2.0 | Q4_K_M | 1,545,295,936 | `881ec3bfaa1187d6185ff9403f5f86b7d5ff4fde` | `2da9e0c3ca95b52b01474000891fbbf0c7f94fc8ab132dbf19028b6dd9fac579` | [Source](https://huggingface.co/mradermacher/granite-3.1-2b-instruct-GGUF) |
| 11 | DeepSeek R1 Distill 1.5B | `deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B` | MIT (DeepSeek) + Apache-2.0 (Qwen base) | Q3_K_M | 924,456,288 | `39e5becfd6cc844e0310bfed01a4aaf173e131bf` | `1bc36f5fb4f3985286f910871a748e345382584c66e2abb4b170ec50e8fa5d00` | [Source](https://huggingface.co/tensorblock/DeepSeek-R1-Distill-Qwen-1.5B-GGUF) |
| 12 | OLMo 2 1B Instruct | `allenai/OLMo-2-0425-1B-Instruct` | Apache-2.0 | Q4_K_M | 935,515,296 | `62f8c199538474c3e33ed5d7e0580abd66686a27` | `abd8187934a438fbf7cfff0a1de5b9d2793ce913f158794df1951dcba6c93cc6` | [Source](https://huggingface.co/allenai/OLMo-2-0425-1B-Instruct-GGUF) |

## Licence texts

- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [MIT License](https://opensource.org/license/mit/)

The DeepSeek R1 Distill checkpoint is based on Qwen and must retain the DeepSeek MIT and Qwen Apache-2.0 notices, as well as the quantized repository's Apache-2.0 notice. The OLMo 2 GGUF repository does not publish a separate licence tag in its model metadata; the upstream AllenAI OLMo 2 1B Instruct base model declares Apache-2.0. Keep that upstream licence and attribution with the artifact.

## Distribution and branding rules

1. Display the WASTI NEXUS collection identity alongside, not instead of, upstream model names.
2. Do not remove upstream copyright, licence or attribution notices.
3. Do not claim that the model weights were trained by WASTI NEXUS or Nabeel Wasti.
4. Do not redistribute the model binaries or offer them to customers until a separate licence and distribution review is completed.
5. Google Drive is the owner's private archive. The app copies only exact registered filenames and verifies exact byte length and SHA-256 before installation.
6. These pinned manifests verify the public GGUF source artefacts. They do not certify that every model has been loaded or performance-tested on the Redmi Note 14 Pro. Device runtime, memory, thermal, battery and latency validation remains outstanding.
