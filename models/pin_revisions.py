#!/usr/bin/env python3
"""Resolve upstream model repositories to immutable commit SHAs."""
import argparse
import json
import os
import re
from pathlib import Path

from huggingface_hub import HfApi

SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--registry", default=str(Path(__file__).with_name("registry.json")))
    parser.add_argument("--write", action="store_true", help="Write resolved SHAs into the registry")
    args = parser.parse_args()

    path = Path(args.registry)
    data = json.loads(path.read_text(encoding="utf-8"))
    api = HfApi(token=os.getenv("HF_TOKEN"))
    failed: list[tuple[str, str]] = []
    changed = False

    targets = []
    for family in data.get("families", []):
        targets.append((family, family.get("repo_id"), f"{family.get('id', '?')}/primary"))
        local = family.get("local_candidate")
        if isinstance(local, dict):
            targets.append((local, local.get("repo_id"), f"{family.get('id', '?')}/local_candidate"))

    for target, repo_id, label in targets:
        revision = target.get("revision")
        if revision:
            if not SHA_PATTERN.fullmatch(revision):
                failed.append((label, "existing revision is not a 40-character immutable SHA"))
            else:
                print(f"PINNED {label}: {revision}")
            continue
        if not repo_id:
            failed.append((label, "missing repo_id"))
            continue
        try:
            info = api.model_info(repo_id=repo_id, revision="main")
            sha = getattr(info, "sha", None)
            if not isinstance(sha, str) or not SHA_PATTERN.fullmatch(sha):
                raise RuntimeError("Hugging Face did not return a valid immutable commit SHA")
            print(f"RESOLVED {label}: {repo_id} -> {sha}")
            if args.write:
                target["revision"] = sha
                changed = True
        except Exception as exc:
            failed.append((label, f"{type(exc).__name__}: {exc}"))

    if args.write and changed:
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        print("Registry updated. Review the pinned revisions before downloading.")
    elif not args.write:
        print("Dry run only. Re-run with --write to pin these immutable revisions.")

    if failed:
        for label, error in failed:
            print(f"FAILED {label}: {error}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
