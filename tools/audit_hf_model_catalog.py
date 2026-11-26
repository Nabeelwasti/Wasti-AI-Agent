#!/usr/bin/env python3
"""Verify every pinned public Hugging Face GGUF artifact without downloading weights."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "android/app/src/main/assets/nexus-model-catalog.json"
USER_AGENT = "WASTI-NEXUS-Model-Catalog-Audit/1.2"
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
REVISION_RE = re.compile(r"^[0-9a-f]{40}$")
PERMISSIVE = {"apache-2.0", "mit"}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def clean_hash(value: str | None) -> str | None:
    if not value:
        return None
    value = value.strip().strip('"').strip("'").lower()
    return value if SHA256_RE.fullmatch(value) else None


def request_headers(url: str):
    request = Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
    try:
        with build_opener(NoRedirect).open(request, timeout=35) as response:
            return response.status, response.headers, None
    except HTTPError as error:
        return error.code, error.headers, error.headers.get("Location")
    except (URLError, TimeoutError, OSError) as error:
        return None, {}, str(error)


def get_model_info(repo_id: str) -> dict:
    url = f"https://huggingface.co/api/models/{quote(repo_id, safe='/')}"
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=35) as response:
        return json.loads(response.read().decode("utf-8"))


def model_license(info: dict | None) -> str | None:
    if not info:
        return None
    card = info.get("cardData") or {}
    licence = card.get("license")
    if licence:
        return str(licence).lower()
    for tag in info.get("tags") or []:
        if str(tag).startswith("license:"):
            return str(tag).split(":", 1)[1].lower()
    return None


def inspect_model(model: dict) -> dict:
    source = model.get("source", "")
    parts = [part for part in __import__("urllib.parse", fromlist=["urlparse"]).urlparse(source).path.split("/") if part]
    if len(parts) < 2:
        return {"id": model.get("id"), "error": "invalid Hugging Face source URL"}
    repo_id = "/".join(parts[:2])

    revision = model.get("source_revision")
    if not isinstance(revision, str) or not REVISION_RE.fullmatch(revision):
        return {"id": model.get("id"), "error": "catalogue source_revision is not an immutable commit SHA"}

    try:
        metadata = get_model_info(repo_id)
    except (HTTPError, URLError, TimeoutError, OSError, ValueError) as error:
        return {"id": model.get("id"), "repo_id": repo_id, "error": f"GGUF metadata failed: {error}"}

    filename = model["file_name"]
    artifact_url = (
        f"https://huggingface.co/{quote(repo_id, safe='/')}/resolve/"
        f"{revision}/{quote(filename, safe='')}?download=true"
    )
    status, headers, location = request_headers(artifact_url)
    size_text = headers.get("X-Linked-Size") or headers.get("Content-Length") or headers.get("x-linked-size")
    size = int(size_text) if size_text and str(size_text).isdigit() else None
    if size is not None and size <= 0:
        size = None
    sha = clean_hash(headers.get("X-Linked-Etag") or headers.get("ETag"))

    if status in (301, 302, 303, 307, 308) and location:
        final_status, final_headers, _ = request_headers(location)
        if size is None:
            final_size = final_headers.get("Content-Length")
            size = int(final_size) if final_size and str(final_size).isdigit() else None
            if size is not None and size <= 0:
                size = None
        if sha is None:
            sha = clean_hash(final_headers.get("X-Linked-Etag") or final_headers.get("ETag"))
        if final_status not in (200, 206):
            status = final_status

    base_id = model.get("upstream_model")
    try:
        base_info = get_model_info(base_id) if base_id else None
    except (HTTPError, URLError, TimeoutError, OSError, ValueError):
        base_info = None

    gguf_license = model_license(metadata)
    base_license = model_license(base_info)
    expected_size = model.get("size_bytes")
    expected_sha = clean_hash(model.get("sha256"))
    file_exists = status in (200, 206, 302, 303, 307, 308)
    size_matches = size is not None and size == expected_size
    sha_matches = sha is not None and sha == expected_sha
    permissive = gguf_license in PERMISSIVE or base_license in PERMISSIVE
    gated = bool(metadata.get("gated")) or bool(base_info and base_info.get("gated"))

    return {
        "id": model.get("id"),
        "repo_id": repo_id,
        "pinned_revision": revision,
        "latest_repo_revision": metadata.get("sha"),
        "latest_revision_matches_pin": metadata.get("sha") == revision,
        "file_name": filename,
        "http_status": status,
        "file_exists_at_pinned_revision": file_exists,
        "size_bytes": size,
        "catalogue_size_matches": size_matches,
        "sha256": sha,
        "catalogue_sha256_matches": sha_matches,
        "gguf_repo_license": gguf_license,
        "base_model": base_id,
        "base_model_license": base_license,
        "permissive_license_evidence": permissive,
        "gated": gated,
        "manifest_verified": bool(file_exists and size_matches and sha_matches and permissive and not gated),
    }


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    models = catalog["models"]
    print("WASTI_NEXUS_HF_MODEL_AUDIT_BEGIN")
    errors = 0
    for model in models:
        result = inspect_model(model)
        print(json.dumps(result, sort_keys=True))
        if not result.get("manifest_verified"):
            errors += 1
    print(json.dumps({
        "audit_complete": True,
        "candidate_count": len(models),
        "manifest_errors": errors,
        "notice": "The report verifies source manifests only; Android device runtime validation remains separate."
    }, sort_keys=True))
    print("WASTI_NEXUS_HF_MODEL_AUDIT_END")
    return 0


if __name__ == "__main__":
    sys.exit(main())
