import os
import json
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def setup_function():
    os.environ["NEXUS_API_KEY"] = "nexus-test-secret-" + "x" * 40
    os.environ["NEXUS_AGENTS_JSON"] = "{}"

def test_healthz_is_public():
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json()["service"] == "wasti-nexus-orchestrator"

def test_models_requires_bearer_key():
    assert client.get("/v1/models").status_code == 401

def test_models_only_reports_configured_agents():
    response = client.get("/v1/models", headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]})
    assert response.status_code == 200
    assert response.json()["data"] == []

def test_hybrid_does_not_fake_participation_when_unconfigured():
    response = client.post("/v1/chat/completions",
        headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]},
        json={"model": "nexus-hybrid", "messages": [{"role": "user", "content": "Test"}]})
    assert response.status_code == 503

def test_models_expose_auto_only_when_a_licensed_provider_is_configured():
    os.environ["NEXUS_AGENTS_JSON"] = json.dumps({
        "qwen-local": {"model": "qwen3-4b-q4", "base_url": "https://inference.example/v1", "capabilities": ["chat", "coding"], "commercial_allowed": True, "license_status": "verified"}
    })
    response = client.get("/v1/models", headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]})
    assert response.status_code == 200
    data = response.json()["data"]
    assert data[0]["id"] == "qwen-local"
    assert data[0]["capabilities"] == ["chat", "coding"]
    assert data[0]["inference"] == "self-hosted"
    assert data[-1]["id"] == "nexus-auto"
    assert not any(item["id"] == "nexus-hybrid" for item in data)


def test_customer_mode_hides_pending_or_restricted_licences():
    os.environ["NEXUS_AGENTS_JSON"] = json.dumps({
        "pending": {"model": "unreviewed", "base_url": "https://inference.example/v1", "capabilities": ["chat"], "commercial_allowed": True, "license_status": "review_required"}
    })
    response = client.get("/v1/models", headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]})
    assert response.status_code == 200
    assert response.json()["data"] == []


def test_auto_mode_falls_back_after_first_provider_failure(monkeypatch):
    os.environ["NEXUS_AGENTS_JSON"] = json.dumps({
        "coder": {"model": "coder-model", "base_url": "https://coder.example/v1", "capabilities": ["coding", "chat"], "commercial_allowed": True, "license_status": "verified", "priority": 1},
        "general": {"model": "general-model", "base_url": "https://general.example/v1", "capabilities": ["chat"], "commercial_allowed": True, "license_status": "verified", "priority": 2}
    })
    calls = []
    async def fake_call(agent_id, config, messages, temperature, max_tokens):
        calls.append(agent_id)
        if agent_id == "coder":
            raise RuntimeError("temporary provider failure")
        return {"agent": agent_id, "model": config["model"], "content": "Fallback succeeded."}
    monkeypatch.setattr("app.main.call_agent", fake_call)
    response = client.post("/v1/chat/completions",
        headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]},
        json={"model": "nexus-auto", "messages": [{"role": "user", "content": "Debug this Python stack trace"}], "nexus": {"mode": "auto"}})
    assert response.status_code == 200
    data = response.json()
    assert data["choices"][0]["message"]["content"] == "Fallback succeeded."
    assert data["nexus"]["selected_agent"] == "general"
    assert data["nexus"]["failed_agents"][0]["agent"] == "coder"
    assert calls == ["coder", "general"]


def test_auto_mode_fails_closed_for_image_without_vision_provider(monkeypatch):
    os.environ["NEXUS_AGENTS_JSON"] = json.dumps({
        "text": {"model": "text-model", "base_url": "https://text.example/v1", "capabilities": ["chat"], "commercial_allowed": True, "license_status": "verified"}
    })
    async def should_not_call(*args, **kwargs):
        raise AssertionError("text-only provider must not be called for image input")
    monkeypatch.setattr("app.main.call_agent", should_not_call)
    response = client.post("/v1/chat/completions",
        headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]},
        json={"model": "nexus-auto", "messages": [{"role": "user", "content": "Analyze this image"}], "nexus": {"mode": "auto"}})
    assert response.status_code == 503

