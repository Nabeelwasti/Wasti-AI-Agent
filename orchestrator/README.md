# NEXUS Orchestrator

This is the first working API foundation. It provides authenticated health, agent/model discovery and OpenAI-compatible non-streaming chat completions.

## Run

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export NEXUS_API_KEY="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
export NEXUS_ALLOW_LOCAL_HTTP=1  # development only; production providers must use HTTPS
export NEXUS_AGENTS_JSON='{"qwen":{"base_url":"http://127.0.0.1:8001/v1","model":"Qwen2.5-1.5B-Instruct"}}'
export NEXUS_SYNTHESIS_AGENT=qwen
uvicorn app.main:app --host 127.0.0.1 --port 8080
```

Use `Authorization: Bearer $NEXUS_API_KEY`. The API intentionally refuses protected endpoints if the key is unset or left at the example placeholder.

## Hybrid

Set multiple agent entries and set `NEXUS_SYNTHESIS_AGENT` to one configured agent ID, or pass `nexus.synthesis_agent` in the request. Hybrid analysis runs configured agents concurrently. A single final synthesis is produced only when a synthesis backend is available. Review-round requests are currently reported as not implemented; they are not silently simulated.
