#!/usr/bin/env python3
"""Download one pinned GGUF local candidate, not every model family."""
import argparse
import hashlib
import json
from pathlib import Path

from huggingface_hub import snapshot_download


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("family_id", help="Registry family ID, e.g. qwen")
    parser.add_argument("--registry", default=str(Path(__file__).with_name("registry.json")))
    parser.add_argument("--output", default="./nexus-local-models")
    args = parser.parse_args()

    registry = json.loads(Path(args.registry).read_text(encoding="utf-8"))
    family = next((item for item in registry.get("families", []) if item.get("id") == args.family_id), None)
    if not family:
        raise SystemExit(f"Unknown model family: {args.family_id}")
    candidate = family.get("local_candidate")
    if not isinstance(candidate, dict):
        raise SystemExit(f"No local GGUF candidate is registered for {args.family_id}")
    repo_id = candidate.get("repo_id")
    revision = candidate.get("revision")
    file_name = candidate.get("file_name")
    if not repo_id or not revision or not file_name:
        raise SystemExit("Local candidate requires repo_id, immutable revision and file_name. Run pin_revisions.py first.")

    output = Path(args.output).resolve() / args.family_id
    output.mkdir(parents=True, exist_ok=True)
    snapshot_download(
        repo_id=repo_id,
        revision=revision,
        allow_patterns=[file_name],
        local_dir=str(output),
    )
    model_path = output / file_name
    if not model_path.is_file():
        raise SystemExit(f"Expected GGUF file was not downloaded: {model_path}")
    digest = sha256(model_path)
    expected = candidate.get("sha256")
    if expected and expected != digest:
        raise SystemExit("Downloaded GGUF SHA-256 does not match the registry value")
    manifest = {
        "family_id": args.family_id,
        "repo_id": repo_id,
        "revision": revision,
        "file": file_name,
        "bytes": model_path.stat().st_size,
        "sha256": digest,
        "quantization": candidate.get("quantization"),
        "license_id": candidate.get("license_id"),
    }
    manifest_path = output / "NEXUS-MODEL-MANIFEST.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"model_path": str(model_path), "manifest": str(manifest_path), "sha256": digest}, indent=2))


if __name__ == "__main__":
    main()
