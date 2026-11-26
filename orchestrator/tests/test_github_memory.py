import asyncio
import json
import uuid
from datetime import datetime, timezone

import httpx
import pytest

from app.github_memory import GitHubMemoryError, GitHubMemoryStore
from app.main import MemorySnapshotInput


def configure_memory(monkeypatch):
    monkeypatch.setenv("NEXUS_MEMORY_REPOSITORY", "Nabeelwasti/Wasti-AI-Agent")
    monkeypatch.setenv("NEXUS_GITHUB_TOKEN", "ghp_" + "x" * 36)
    monkeypatch.setenv("NEXUS_MEMORY_BRANCH", "main")


def test_memory_store_requires_explicit_repository_and_token(monkeypatch):
    monkeypatch.delenv("NEXUS_MEMORY_REPOSITORY", raising=False)
    monkeypatch.delenv("NEXUS_GITHUB_TOKEN", raising=False)
    with pytest.raises(GitHubMemoryError):
        GitHubMemoryStore()


def test_snapshot_path_rejects_path_traversal(monkeypatch):
    configure_memory(monkeypatch)
    store = GitHubMemoryStore()
    with pytest.raises(GitHubMemoryError):
        store._path("../../secrets")


def test_snapshot_input_is_client_encrypted_and_timezone_aware():
    snapshot_id = uuid.uuid4()
    record = MemorySnapshotInput(
        snapshot_id=snapshot_id,
        created_at=datetime.now(timezone.utc),
        key_id="device-key-2026",
        nonce="YWJjZGVmZ2hpamts",
        ciphertext="MDEyMzQ1Njc4OWFiY2RlZg",
    ).encrypted_record()
    assert record["tenant_scope"] == "personal"
    assert record["cipher"]["algorithm"] == "AES-256-GCM"
    assert "plaintext" not in json.dumps(record).lower()


def test_snapshot_input_rejects_naive_datetime():
    with pytest.raises(ValueError):
        MemorySnapshotInput(
            snapshot_id=uuid.uuid4(),
            created_at=datetime(2026, 1, 1),
            key_id="device-key-2026",
            nonce="YWJjZGVmZ2hpamts",
            ciphertext="MDEyMzQ1Njc4OWFiY2RlZg",
        ).encrypted_record()


def test_save_snapshot_writes_one_immutable_encrypted_file(monkeypatch):
    configure_memory(monkeypatch)
    snapshot_id = str(uuid.uuid4())
    seen = {}

    async def handler(request):
        if request.method == "GET":
            return httpx.Response(200, json={"private": True})
        seen["method"] = request.method
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        return httpx.Response(201, json={"commit": {"sha": "a" * 40}})

    store = GitHubMemoryStore(transport=httpx.MockTransport(handler))
    record = {
        "schema_version": "1.0",
        "record_id": snapshot_id,
        "tenant_scope": "personal",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "cipher": {
            "algorithm": "AES-256-GCM",
            "key_id": "device-key-2026",
            "nonce": "YWJjZGVmZ2hpamts",
            "ciphertext": "MDEyMzQ1Njc4OWFiY2RlZg",
        },
    }
    result = asyncio.run(store.save(record))
    assert result["snapshot_id"] == snapshot_id
    assert result["commit_sha"] == "a" * 40
    assert seen["method"] == "PUT"
    assert seen["url"].endswith(f"memory/personal/encrypted/{snapshot_id}.json")
    stored = json.loads(__import__("base64").b64decode(seen["body"]["content"]))
    assert stored["cipher"]["ciphertext"] == "MDEyMzQ1Njc4OWFiY2RlZg"

def test_public_repository_is_rejected_before_memory_write(monkeypatch):
    configure_memory(monkeypatch)

    async def handler(request):
        return httpx.Response(200, json={"private": False})

    store = GitHubMemoryStore(transport=httpx.MockTransport(handler))
    record = {
        "record_id": str(uuid.uuid4()),
        "cipher": {"ciphertext": "encrypted"},
    }
    with pytest.raises(GitHubMemoryError, match="must be private"):
        asyncio.run(store.save(record))

