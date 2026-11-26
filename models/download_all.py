#!/usr/bin/env python3
"""Download only explicitly selected local GGUF candidates.

This compatibility entry point intentionally never downloads all twelve full
upstream checkpoints. Select one or more family IDs with --family.
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", action="append", required=True,
                        help="Explicit family ID; repeat to select multiple local candidates")
    parser.add_argument("--registry", default=str(Path(__file__).with_name("registry.json")))
    parser.add_argument("--output", default="./nexus-local-models")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    registry = json.loads(Path(args.registry).read_text(encoding="utf-8"))
    families = {item.get("id"): item for item in registry.get("families", [])}
    unknown = sorted(set(args.family) - set(families))
    if unknown:
        raise SystemExit("Unknown family ID(s): " + ", ".join(unknown))

    selected = []
    for family_id in dict.fromkeys(args.family):
        family = families[family_id]
        candidate = family.get("local_candidate")
        if not isinstance(candidate, dict):
            raise SystemExit(f"No local GGUF candidate registered for {family_id}")
        if not candidate.get("revision"):
            raise SystemExit(
                f"{family_id} local candidate is not pinned. Run "
                f"python models/pin_revisions.py --family {family_id}, review it, "
                f"then --write."
            )
        selected.append(family_id)

    downloader = Path(__file__).with_name("download_local_candidate.py")
    print("Only explicitly selected GGUF files will be downloaded; primary full checkpoints are excluded.")
    for family_id in selected:
        print(f"{family_id}: {families[family_id]['local_candidate']['repo_id']}")
        if args.dry_run:
            continue
        subprocess.run(
            [sys.executable, str(downloader), family_id,
             "--registry", args.registry, "--output", args.output],
            check=True,
        )


if __name__ == "__main__":
    main()
