"""Small standard-library client for the WASTI NEXUS gateway."""
import json
from urllib.request import Request, urlopen
from urllib.error import HTTPError

class NexusClient:
    def __init__(self, base_url: str, api_key: str, timeout: int = 90):
        self.base_url = base_url.rstrip("/")
        if not self.base_url.startswith("https://"):
            raise ValueError("NEXUS base URL must use HTTPS")
        if len(api_key) < 32:
            raise ValueError("NEXUS API key is too short")
        self.api_key = api_key
        self.timeout = timeout

    def _request(self, path: str, payload=None):
        data = None if payload is None else json.dumps(payload).encode()
        req = Request(self.base_url + path, data=data, method="GET" if data is None else "POST",
                      headers={"Authorization": "Bearer " + self.api_key,
                               "Content-Type": "application/json"})
        try:
            with urlopen(req, timeout=self.timeout) as response:
                return json.loads(response.read().decode())
        except HTTPError as exc:
            detail = exc.read().decode(errors="replace")
            raise RuntimeError(f"NEXUS HTTP {exc.code}: {detail}") from exc

    def models(self):
        return self._request("/v1/models")

    def chat(self, model: str, messages: list[dict]):
        return self._request("/v1/chat/completions",
                             {"model": model, "messages": messages, "stream": False})

    def memory_snapshots(self, limit: int = 100):
        safe_limit = max(1, min(100, int(limit)))
        return self._request(f"/v1/memory/snapshots?limit={safe_limit}")

    def create_memory_snapshot(self, encrypted_snapshot: dict):
        required = {"snapshot_id", "created_at", "key_id", "nonce", "ciphertext"}
        if not isinstance(encrypted_snapshot, dict) or not required.issubset(encrypted_snapshot):
            raise ValueError("Pass a complete client-encrypted AES-256-GCM snapshot envelope")
        return self._request("/v1/memory/snapshots", encrypted_snapshot)

    def memory_snapshot(self, snapshot_id: str):
        import re
        if not re.fullmatch(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}", snapshot_id):
            raise ValueError("Invalid memory snapshot UUID")
        return self._request("/v1/memory/snapshots/" + snapshot_id)

    def hybrid(self, messages: list[dict], agents=None, synthesis_agent=None, review_rounds=1):
        return self._request("/v1/chat/completions", {
            "model": "nexus-hybrid", "messages": messages, "stream": False,
            "nexus": {"mode": "hybrid", "agents": agents or [],
                      "synthesis_agent": synthesis_agent,
                      "review_rounds": max(0, min(2, review_rounds))}
        })
