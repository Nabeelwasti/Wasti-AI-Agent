import asyncio

import pytest

from app import main


def test_endpoint_for_v1_base():
    assert main.endpoint_for("https://localhost:8000/v1") == "https://localhost:8000/v1/chat/completions"


def test_endpoint_for_root_base():
    assert main.endpoint_for("https://localhost:8000") == "https://localhost:8000/v1/chat/completions"


def test_review_rounds_are_bounded():
    assert main.NexusOptions(review_rounds=2).review_rounds == 2
    with pytest.raises(Exception):
        main.NexusOptions(review_rounds=3)


def test_external_http_provider_is_rejected(monkeypatch):
    monkeypatch.setenv("NEXUS_ALLOW_LOCAL_HTTP", "0")
    with pytest.raises(ValueError):
        main.validate_provider_url("http://example.com/v1")


def test_local_http_requires_explicit_development_switch(monkeypatch):
    monkeypatch.setenv("NEXUS_ALLOW_LOCAL_HTTP", "1")
    main.validate_provider_url("http://127.0.0.1:8000/v1")


def test_hybrid_collective_runs_peer_review(monkeypatch):
    monkeypatch.setenv("NEXUS_SYNTHESIS_AGENT", "qwen")
    monkeypatch.setattr(main, "agents_config", lambda: {
        "qwen": {"base_url": "https://qwen.example/v1", "model": "qwen-test"},
        "llama": {"base_url": "https://llama.example/v1", "model": "llama-test"},
    })

    async def fake_call_agent(agent_id, config, messages, temperature, max_tokens):
        return {"agent": agent_id, "model": config["model"], "content": f"answer from {agent_id}"}

    monkeypatch.setattr(main, "call_agent", fake_call_agent)
    request = main.ChatRequest(
        model="nexus-hybrid",
        messages=[main.Message(role="user", content="Make a plan")],
        nexus=main.NexusOptions(
            mode="hybrid",
            agents=["qwen", "llama"],
            review_rounds=1,
            synthesis_agent="qwen",
        ),
    )
    response = asyncio.run(main.chat_completions(request))
    assert response["model"] == "nexus-hybrid"
    assert response["nexus"]["participating_agents"] == ["qwen", "llama"]
    assert response["nexus"]["review_rounds_completed"] == 1
    assert len(response["nexus"]["peer_review_reports"]) == 2
    assert response["choices"][0]["message"]["content"] == "answer from qwen"
