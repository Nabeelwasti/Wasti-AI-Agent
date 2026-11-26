# Architecture

## System shape

NEXUS is a monorepo with separable deployable components:

1. **Android client** — Kotlin/Compose UI, local model runtime, encrypted local data, Android capability adapters.
2. **Orchestrator API** — OpenAI-compatible HTTP facade, model/agent registry, routing, bounded Hybrid workflow and audit events.
3. **Model archive tooling** — pinned checkpoint manifests, license evidence, download/verification utilities. Weights are never committed to Git.
4. **Optional Cloudflare edge adapter** — forwards authenticated requests to a protected orchestrator origin or future Workers AI bindings. It is not required for offline operation.
5. **Storage adapters** — local encrypted database, optional remote metadata/sync, separately encrypted backup objects.

## Hybrid request lifecycle

1. Validate request size, authentication, tenant, mode and agent allowlist.
2. Resolve configured agents. Unconfigured agents are excluded and reported, never simulated.
3. Dispatch independent analysis concurrently with bounded fan-out and timeouts.
4. Optionally run a bounded peer-review round over collected findings.
5. Synthesize using a configured synthesis backend. If unavailable, return a deterministic structured aggregation with an explicit notice rather than claim an LLM synthesis occurred.
6. Return final answer plus participation, errors, latency and trace metadata.

## API contract

- `GET /healthz` — liveness.
- `GET /v1/models` — configured agent/model catalog.
- `POST /v1/chat/completions` — OpenAI-compatible chat request with optional `nexus` extension.
- `GET /v1/agents` — configured agent status (no secrets).

## Provider configuration

Set `NEXUS_AGENTS_JSON` to a JSON object mapping public agent IDs to provider configuration. Example:

```json
{
  "qwen": {"base_url":"http://127.0.0.1:8001/v1","model":"Qwen2.5-1.5B-Instruct","api_key_env":"QWEN_API_KEY"},
  "llama": {"base_url":"http://127.0.0.1:8002/v1","model":"Llama-3.2-1B-Instruct","api_key_env":"LLAMA_API_KEY"}
}
```

Each endpoint must implement OpenAI-compatible `POST /chat/completions`. A local vLLM, llama.cpp server or trusted hosted provider can be connected. The orchestrator does not download or execute arbitrary models from a prompt.

## Online endpoint

Deploy the orchestrator to a host you control and place TLS + authentication in front of it. Your apps use one base URL, for example `https://ai-api.example.com/v1`. A custom domain and actual deployment are separate operational steps; this repository does not claim a live endpoint until deployment and smoke tests succeed.

## Data boundaries

Personal, project and customer data use distinct tenant and vault scopes. Retrieval must enforce tenant filters before context reaches a model. Provider calls disclose data to the configured provider; deployments must document this and support local-only routing.
