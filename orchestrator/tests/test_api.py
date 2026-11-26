import os
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
    assert response.json()["data"] == [{"id": "nexus-hybrid", "object": "model", "owned_by": "wasti-nexus", "name": "NEXUS Hybrid collective"}]

def test_hybrid_does_not_fake_participation_when_unconfigured():
    response = client.post("/v1/chat/completions",
        headers={"Authorization": "Bearer " + os.environ["NEXUS_API_KEY"]},
        json={"model": "nexus-hybrid", "messages": [{"role": "user", "content": "Test"}]})
    assert response.status_code == 503
