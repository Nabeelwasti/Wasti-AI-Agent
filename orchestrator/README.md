# NEXUS Orchestrator

Authenticated FastAPI service exposing health, agent/model discovery and OpenAI-compatible non-streaming chat completions.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export NEXUS_API_KEY="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
export NEXUS_ALLOW_LOCAL_HTTP=1  # loopback development only
export NEXUS_AGENTS_JSON='{"qwen":{"base_url":"http://127.0.0.1:8001/v1","model":"Qwen2.5-1.5B-Instruct","commercial_allowed":true}}'
export NEXUS_SYNTHESIS_AGENT=qwen
export NEXUS_DEPLOYMENT_MODE=personal  # use only when your use is non-commercial/private
uvicorn app.main:app --host 127.0.0.1 --port 8080
```

Use `Authorization: Bearer $NEXUS_API_KEY`. Protected endpoints fail closed if the key is unset or still the example placeholder.

## Hybrid collaboration

Configure multiple agent IDs in `NEXUS_AGENTS_JSON`. Hybrid mode runs independent first-pass analysis concurrently, then up to two bounded peer-review rounds, and finally one configured synthesis agent. Use `model: "nexus-hybrid"` or `nexus.mode: "hybrid"`. The response reports successful participants, failed agents, review outputs and timing. If no synthesis agent is configured, the API returns a clearly labelled aggregation rather than pretending an LLM synthesized it.

Provider URLs must use HTTPS in production. Loopback HTTP is available only when `NEXUS_ALLOW_LOCAL_HTTP=1` is explicitly set for local development. Provider credentials should be supplied through environment variables named by `api_key_env`.
