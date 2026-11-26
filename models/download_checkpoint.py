#!/usr/bin/env python3
"""Download a reviewed, revision-pinned model snapshot and write SHA-256 manifest.

This tool intentionally refuses unreviewed or unpinned registry entries. It does
not redistribute model files or change their upstream licenses.
"""
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path
from huggingface_hub import snapshot_download

ROOT = Path(__file__).resolve().parent
REGISTRY = ROOT / "registry.json"
ARCHIVE = ROOT / "archive"

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", required=True)
    args = parser.parse_args()
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    item = next((x for x in registry["families"] if x["id"] == args.family), None)
    if item is None:
        raise SystemExit(f"Unknown family: {args.family}")
    if item.get("license_review") != "approved" or not item.get("license_id"):
        raise SystemExit("Blocked: exact checkpoint license has not been reviewed and approved in registry.json")
    revision = item.get("revision")
    if not revision or len(revision) < 7:
        raise SystemExit("Blocked: pin an immutable upstream commit revision in registry.json first")
    target = ARCHIVE / item["id"]
    snapshot = Path(snapshot_download(repo_id=item["repo_id"], revision=revision,
                                      local_dir=target, local_dir_use_symlinks=False))
    records = []
    for path in sorted(p for p in snapshot.rglob("*") if p.is_file()):
        records.append({"path": path.relative_to(snapshot).as_posix(),
                        "size_bytes": path.stat().st_size, "sha256": sha256(path)})
    manifest = {"family": item["family"], "repo_id": item["repo_id"],
                "revision": revision, "license_id": item["license_id"],
                "commercial_use": item.get("commercial_use", "unknown"),
                "redistribution": item.get("redistribution", "unknown"),
                "files": records}
    (target / "nexus-checksums.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Downloaded and hashed {len(records)} files into {target}")
    print("Review upstream terms and keep this archive private unless redistribution is permitted.")

if __name__ == "__main__":
    main()
