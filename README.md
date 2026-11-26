# WASTI NEXUS

**One project. One repository. Many AI brains. One orchestration API.**

WASTI NEXUS is an offline-first Android AI assistant and a self-hostable multi-model orchestration platform. It is designed to let personal apps and future customer-facing products connect to one API while selecting individual model backends or requesting a collective Hybrid answer.

> Project status: foundation in progress. This repository does not yet represent a production release. Features are only considered complete after implementation, automated tests, device/deployment verification and documented evidence.

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
cloudflare/              Optional Worker deployment adapter
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
uvicorn app.main:app --host 127.0.0.1 --port 8080
```

The API is then available at `http://127.0.0.1:8080`. Configure provider endpoints and credentials through environment variables; never commit secrets.

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
