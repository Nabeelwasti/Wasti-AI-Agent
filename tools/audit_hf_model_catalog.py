#!/usr/bin/env python3
"""Audit public Hugging Face GGUF metadata without downloading model weights.

This report is informational. It never promotes a model to downloadable; the
catalogue still requires exact integrity, licence, runtime and device review.
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
USER_AGENT = "WASTI-NEXUS-Model-Catalog-Audit/1.1"
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")
PERMISSIVE = {"apache-2.0", "mit"}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def clean_hash(value: str | None) -> str | None:
    if not value:
        return None
    value = value.strip().strip('"').strip("'")
    return value.lower() if SHA256_RE.fullmatch(value) else None


def request_headers(url: str, method: str = "HEAD"):
    request = Request(url, method=method, headers={"User-Agent": USER_AGENT})
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
    tags = info.get("tags") or []
    for tag in tags:
        if str(tag).startswith("license:"):
            return str(tag).split(":", 1)[1].lower()
    return None


def inspect_model(model: dict) -> dict:
    source = model.get("source", "")
    parts = [part for part in urlparse(source).path.split("/") if part]
    if urlparse(source).netloc != "huggingface.co" or len(parts) < 2:
        return {"id": model.get("id"), "error": "invalid Hugging Face source URL"}

    repo_id = "/".join(parts[:2])
    try:
        metadata = get_model_info(repo_id)
    except (HTTPError, URLError, TimeoutError, OSError, ValueError) as error:
        return {"id": model.get("id"), "repo_id": repo_id, "error": f"GGUF metadata failed: {error}"}

    revision = metadata.get("sha")
    if not isinstance(revision, str) or not re.fullmatch(r"[0-9a-f]{40}", revision):
        return {"id": model.get("id"), "repo_id": repo_id, "error": "missing immutable repository revision"}

    siblings = metadata.get("siblings") or []
    filenames = [item.get("rfilename", "") for item in siblings]
    expected = model["file_name"]
    if expected in filenames:
        filename = expected
    else:
        q4_files = [
            name for name in filenames
            if name.lower().endswith(".gguf") and "q4_k_m" in name.lower()
        ]
        filename = q4_files[0] if len(q4_files) == 1 else None
    if not filename:
        return {
            "id": model.get("id"),
            "repo_id": repo_id,
            "revision": revision,
            "expected_file_name": expected,
            "sibling_count": len(filenames),
            "gguf_files": [name for name in filenames if name.lower().endswith(".gguf")][:40],
            "q4_k_m_files": [
                name for name in filenames
                if name.lower().endswith(".gguf") and "q4_k_m" in name.lower()
            ],
            "error": "expected GGUF filename not found and no unique Q4_K_M candidate exists",
        }

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
    if size is not None and size <= 0:
        size = None
    sha = clean_hash(file_headers.get("X-Linked-Etag") or file_headers.get("ETag"))

    if file_status in (301, 302, 303, 307, 308) and location:
        final_status, final_headers, _ = request_headers(location, method="HEAD")
        if size is None:
            final_size = final_headers.get("Content-Length")
            size = int(final_size) if final_size and str(final_size).isdigit() else None
            if size is not None and size <= 0:
                size = None
        if sha is None:
            sha = clean_hash(final_headers.get("X-Linked-Etag") or final_headers.get("ETag"))
        if final_status not in (200, 206):
            file_status = final_status

    base_id = model.get("upstream_model")
    base_info = None
    if base_id:
        try:
            base_info = get_model_info(base_id)
        except (HTTPError, URLError, TimeoutError, OSError, ValueError):
            base_info = None

    repo_license = model_license(metadata)
    base_license = model_license(base_info)
    tags = metadata.get("tags") or []
    return {
        "id": model.get("id"),
        "repo_id": repo_id,
        "revision": revision,
        "expected_file_name": expected,
        "resolved_file_name": filename,
        "file_name_matches_catalogue": filename == expected,
        "http_status": file_status,
        "size_bytes": size,
        "sha256": sha,
        "gguf_repo_license": repo_license,
        "base_model": base_id,
        "base_model_license": base_license,
        "license_tags": [tag for tag in tags if str(tag).startswith("license:")],
        "permissive_license_evidence": {
            "gguf_repo": repo_license in PERMISSIVE,
            "base_model": base_license in PERMISSIVE,
        },
        "gated": bool(metadata.get("gated")) or bool(base_info and base_info.get("gated")),
        "file_exists": file_status in (200, 206, 302, 303, 307, 308),
        "sha256_header_available": sha is not None,
    }


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    pending = [model for model in catalog["models"] if model.get("status") != "ready"]
    print("WASTI_NEXUS_HF_MODEL_AUDIT_BEGIN")
    errors = 0
    for model in pending:
        result = inspect_model(model)
        print(json.dumps(result, sort_keys=True))
        if result.get("error") or not result.get("file_exists"):
            errors += 1
    print(json.dumps({
        "audit_complete": True,
        "candidate_count": len(pending),
        "metadata_or_file_errors": errors,
        "notice": "Informational only; no catalogue entry is promoted to ready."
    }, sort_keys=True))
    print("WASTI_NEXUS_HF_MODEL_AUDIT_END")
    return 0


if __name__ == "__main__":
    sys.exit(main())
