# WASTI NEXUS

**One project. One repository. Many AI brains. One orchestration API.**

WASTI NEXUS is an offline-first Android AI assistant and a self-hostable multi-model orchestration platform. It is designed to let personal apps and future customer-facing products connect to one API while selecting individual model backends or requesting a collective Hybrid answer.

> Project status: active foundation. The Cloudflare gateway is deployed at `https://wasti-nexus-gateway.wastinabeel99.workers.dev`, but no model provider is connected yet. The Android build, model archive and production release remain unverified.

## Product goals

- One repository for Android, orchestration API, model registry, deployment, documentation and tests.
- Twelve model-family catalog: Llama, Qwen, Gemma, DeepSeek, Mistral, Phi, Granite, GLM, Command R, Falcon, SmolLM and StableLM.
- Actual checkpoint acquisition with pinned revisions, license review, file manifests and SHA-256 verification. Large weights are stored outside ordinary Git history.
- OpenAI-compatible API surface where practical: `/v1/models` and `/v1/chat/completions`.
- Hybrid mode: parallel independent analysis, bounded peer review, synthesis and transparent participation reporting.
- Native Android app with local inference, model lifecycle management, encrypted memory and user-controlled Android integrations.
- Optional self-hosted/cloud deployment. Offline Android operation must not depend on the cloud.
- Strict separation of personal, project and customer data.

## Repository layout

```
android/                 Native Android application
orchestrator/            OpenAI-compatible multi-model API
models/                  Family registry, manifests and acquisition tools
cloudflare/              Deployed Cloudflare Worker gateway source
sdks/                    TypeScript and Python integration clients
docs/                    Architecture, security, operations and API guide
tests/                   Cross-component and contract tests
.github/workflows/       CI workflows
```

## Quick start (orchestrator)

Requirements: Python 3.11+.

```bash
cd orchestrator
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell:
# .venv\Scripts\Activate.ps1
pip install -r requirements.txt
export NEXUS_API_KEY="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
export NEXUS_AGENTS_JSON='{}'
uvicorn app.main:app --host 127.0.0.1 --port 8080
```

The local API is then available at `http://127.0.0.1:8080`. Protected endpoints require `Authorization: Bearer $NEXUS_API_KEY`. Configure trusted provider endpoints and credentials through environment variables; never commit secrets.

## Deployed gateway

Base URL: `https://wasti-nexus-gateway.wastinabeel99.workers.dev` (do not append `/v1`). The Worker is deployed and the workers.dev subdomain is enabled. No model providers are configured yet, so `/v1/models` currently reports no available inference brains. See [docs/API.md](docs/API.md) for provider setup and examples.

## Reusable SDKs

See [sdks/README.md](sdks/README.md), [sdks/typescript/nexus.ts](sdks/typescript/nexus.ts) and [sdks/python/nexus_client.py](sdks/python/nexus_client.py).

## Hybrid API

`POST /v1/chat/completions` accepts standard chat messages plus optional extensions:

```json
{
  "model": "nexus-hybrid",
  "messages": [{"role": "user", "content": "Compare these options and recommend one."}],
  "nexus": {
    "mode": "hybrid",
    "agents": ["qwen", "llama", "gemma"],
    "review_rounds": 1
  }
}
```

The response reports which configured agents actually participated. If no agent backend is configured, the API returns a clear service error; it never fabricates model participation.

## Security model

NEXUS aims for broad capabilities, not security bypasses. Android runtime permissions, special access, platform restrictions, provider terms and applicable law remain authoritative. Tool execution is deny-by-default, scoped, auditable and approval-gated for sensitive or irreversible actions. Customer tenants must be isolated from personal memory and from one another.

## Model licensing and distribution

A family name is not a license grant. Each exact checkpoint must be reviewed independently for its license, intended use, redistribution rights and required notices. This project does not claim that all listed model families are unrestricted open source. Do not publish weights unless their terms permit it.

## Development status

See [docs/ROADMAP.md](docs/ROADMAP.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/SECURITY.md](docs/SECURITY.md) and [models/registry.json](models/registry.json).

## License

Application source licensing will be finalized separately from third-party model licenses. Third-party weights and assets retain their own terms.
