from __future__ import annotations

import base64
import json
import os
import re
from typing import Any
from urllib.parse import quote

import httpx

_SNAPSHOT_ID = re.compile(r"^[0-9a-fA-F-]{36}$")
_REPOSITORY = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class GitHubMemoryError(RuntimeError):
    pass


class GitHubMemoryStore:
    """Stores client-encrypted snapshot files in an explicitly public GitHub repository. Plaintext is never accepted by this adapter."""

    def __init__(self, transport: httpx.AsyncBaseTransport | None = None):
        self.repository = os.getenv("NEXUS_MEMORY_REPOSITORY", "").strip()
        self.token = os.getenv("NEXUS_GITHUB_TOKEN", "").strip()
        self.branch = os.getenv("NEXUS_MEMORY_BRANCH", "main").strip() or "main"
        self.prefix = os.getenv("NEXUS_MEMORY_PREFIX", "memory/personal/encrypted").strip("/")
        self.transport = transport
        if not _REPOSITORY.fullmatch(self.repository):
            raise GitHubMemoryError("NEXUS_MEMORY_REPOSITORY must be owner/repository")
        if len(self.token) < 20:
            raise GitHubMemoryError("NEXUS_GITHUB_TOKEN is missing or too short")
        if not self.prefix or ".." in self.prefix.split("/"):
            raise GitHubMemoryError("NEXUS_MEMORY_PREFIX is invalid")

    def _path(self, snapshot_id: str) -> str:
        if not _SNAPSHOT_ID.fullmatch(snapshot_id):
            raise GitHubMemoryError("snapshot_id must be a UUID")
        return f"{self.prefix}/{snapshot_id.lower()}.json"

    def _client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url="https://api.github.com",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "WASTI-NEXUS-Memory",
            },
            timeout=httpx.Timeout(20.0, connect=8.0),
            follow_redirects=False,
            transport=self.transport,
        )

    async def _ensure_public(self, client: httpx.AsyncClient) -> None:
        response = await client.get(f"/repos/{self.repository}")
        if response.status_code != 200:
            raise GitHubMemoryError(f"GitHub repository access check failed with HTTP {response.status_code}")
        if response.json().get("private") is not False:
            raise GitHubMemoryError("Memory repository must remain public for the selected GitHub storage mode")

    async def save(self, record: dict[str, Any]) -> dict[str, Any]:
        snapshot_id = str(record.get("record_id", ""))
        path = self._path(snapshot_id)
        encoded = base64.b64encode(
            (json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        ).decode("ascii")
        url = f"/repos/{self.repository}/contents/{quote(path, safe='/')}"
        body = {
            "message": f"memory: add encrypted snapshot {snapshot_id}",
            "content": encoded,
            "branch": self.branch,
        }
        async with self._client() as client:
            await self._ensure_public(client)
            response = await client.put(url, json=body)
        if response.status_code == 422:
            raise GitHubMemoryError("Snapshot already exists or GitHub rejected the file; IDs are immutable")
        if response.status_code not in (200, 201):
            raise GitHubMemoryError(f"GitHub memory write failed with HTTP {response.status_code}")
        payload = response.json()
        return {
            "snapshot_id": snapshot_id,
            "commit_sha": (payload.get("commit") or {}).get("sha"),
            "path": path,
        }

    async def get(self, snapshot_id: str) -> dict[str, Any]:
        path = self._path(snapshot_id)
        url = f"/repos/{self.repository}/contents/{quote(path, safe='/')}"
        async with self._client() as client:
            await self._ensure_private(client)
            response = await client.get(url, params={"ref": self.branch})
        if response.status_code == 404:
            raise GitHubMemoryError("Memory snapshot not found")
        if response.status_code != 200:
            raise GitHubMemoryError(f"GitHub memory read failed with HTTP {response.status_code}")
        payload = response.json()
        if payload.get("encoding") != "base64" or not isinstance(payload.get("content"), str):
            raise GitHubMemoryError("GitHub returned an unsupported file encoding")
        try:
            raw = base64.b64decode(payload["content"], validate=False)
            record = json.loads(raw.decode("utf-8"))
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise GitHubMemoryError("Stored memory snapshot is corrupt") from exc
        if not isinstance(record, dict) or record.get("record_id") != snapshot_id.lower():
            raise GitHubMemoryError("Stored memory snapshot identity does not match its path")
        return record

    async def list(self, limit: int = 100) -> list[dict[str, Any]]:
        safe_limit = max(1, min(int(limit), 100))
        url = f"/repos/{self.repository}/contents/{quote(self.prefix, safe='/')}"
        async with self._client() as client:
            await self._ensure_private(client)
            response = await client.get(url, params={"ref": self.branch})
        if response.status_code == 404:
            return []
        if response.status_code != 200:
            raise GitHubMemoryError(f"GitHub memory listing failed with HTTP {response.status_code}")
        payload = response.json()
        if not isinstance(payload, list):
            raise GitHubMemoryError("GitHub memory path is not a directory")
        items = []
        for item in payload:
            name = item.get("name", "") if isinstance(item, dict) else ""
            match = re.fullmatch(r"([0-9a-fA-F-]{36})\.json", name)
            if match and item.get("type") == "file":
                items.append({"snapshot_id": match.group(1).lower(), "sha": item.get("sha")})
            if len(items) >= safe_limit:
                break
        return items
