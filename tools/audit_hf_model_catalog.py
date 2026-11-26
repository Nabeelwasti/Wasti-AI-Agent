#!/usr/bin/env python3
"""Audit public Hugging Face GGUF artifact metadata without downloading weights.

The report is informational: a model is never marked ready by this script. The
catalogue still requires an independently verified SHA-256, immutable revision,
licence review, and device/runtime approval before enabling a download.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "android/app/src/main/assets/nexus-model-catalog.json"
USER_AGENT = "WASTI-NEXUS-Model-Catalog-Audit/1.0"
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def clean_hash(value: str | None) -> str | None:
    if not value:
        return None
    value = value.strip().strip('"').strip("'")
    return value.lower() if SHA256_RE.fullmatch(value) else None


def request_headers(url: str, method: str = "GET"):
    request = Request(url, method=method, headers={"User-Agent": USER_AGENT})
    try:
        with urlopen(request, timeout=35) as response:
            return response.status, response.headers, response.geturl()
    except HTTPError as error:
        return error.code, error.headers, error.headers.get("Location")
    except (URLError, TimeoutError, OSError) as error:
        return None, {}, str(error)


def inspect_model(model: dict) -> dict:
    source = model.get("source", "")
    parts = [part for part in urlparse(source).path.split("/") if part]
    if urlparse(source).netloc != "huggingface.co" or len(parts) < 2:
        return {"id": model.get("id"), "error": "invalid Hugging Face source URL"}

    repo_id = "/".join(parts[:2])
    info_url = f"https://huggingface.co/api/models/{quote(repo_id, safe='/')}"
    status, headers, _ = request_headers(info_url)
    if status != 200:
        return {"id": model.get("id"), "repo_id": repo_id, "error": f"model metadata HTTP {status}"}

    try:
        with urlopen(Request(info_url, headers={"User-Agent": USER_AGENT}), timeout=35) as response:
            metadata = json.loads(response.read().decode("utf-8"))
    except (URLError, TimeoutError, OSError, ValueError) as error:
        return {"id": model.get("id"), "repo_id": repo_id, "error": f"metadata parse failed: {error}"}

    revision = metadata.get("sha")
    if not isinstance(revision, str) or not re.fullmatch(r"[0-9a-f]{40}", revision):
        return {"id": model.get("id"), "repo_id": repo_id, "error": "missing immutable repository revision"}

    filename = model["file_name"]
    artifact_url = (
        f"https://huggingface.co/{quote(repo_id, safe='/')}/resolve/"
        f"{revision}/{quote(filename, safe='')}?download=true"
    )
    file_status, file_headers, location = request_headers(artifact_url, method="HEAD")
    size_text = (
        file_headers.get("X-Linked-Size")
        or file_headers.get("Content-Length")
        or file_headers.get("x-linked-size")
    )
    size = int(size_text) if size_text and str(size_text).isdigit() else None
    sha = clean_hash(file_headers.get("X-Linked-Etag") or file_headers.get("ETag"))

    # If the first response is a redirect, the immutable object URL may expose
    # the final content length. Never infer SHA-256 from an Xet chunk hash.
    if file_status in (301, 302, 303, 307, 308) and location:
        final_status, final_headers, _ = request_headers(location, method="HEAD")
        if size is None:
            final_size = final_headers.get("Content-Length")
            size = int(final_size) if final_size and str(final_size).isdigit() else None
        if sha is None:
            sha = clean_hash(final_headers.get("X-Linked-Etag") or final_headers.get("ETag"))
        if final_status not in (200, 206):
            file_status = final_status

    card = metadata.get("cardData") or {}
    licence = card.get("license")
    tags = metadata.get("tags") or []
    return {
        "id": model.get("id"),
        "repo_id": repo_id,
        "revision": revision,
        "file_name": filename,
        "http_status": file_status,
        "size_bytes": size,
        "sha256": sha,
        "license": licence,
        "license_tags": [tag for tag in tags if str(tag).startswith("license:")],
        "gated": bool(metadata.get("gated")),
        "file_exists": file_status in (200, 206, 302, 303, 307, 308),
        "sha256_header_available": sha is not None,
    }


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    pending = [model for model in catalog["models"] if model.get("status") != "ready"]
    print("WASTI_NEXUS_HF_MODEL_AUDIT_BEGIN")
    failures = 0
    for model in pending:
        result = inspect_model(model)
        print(json.dumps(result, sort_keys=True))
        if result.get("error") or not result.get("file_exists"):
            failures += 1
    print(json.dumps({
        "audit_complete": True,
        "candidate_count": len(pending),
        "metadata_or_file_errors": failures,
        "notice": "Informational only; no catalogue entry is promoted to ready."
    }, sort_keys=True))
    print("WASTI_NEXUS_HF_MODEL_AUDIT_END")
    return 0


if __name__ == "__main__":
    sys.exit(main())
