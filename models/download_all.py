#!/usr/bin/env python3
"""Download all 12 pinned model snapshots to a private external archive."""
import argparse, json, subprocess, sys
from pathlib import Path

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",default="./nexus-model-archive")
    parser.add_argument("--registry",default=str(Path(__file__).with_name("registry.json")))
    parser.add_argument("--dry-run",action="store_true")
    args=parser.parse_args()
    registry=json.loads(Path(args.registry).read_text(encoding="utf-8"))
    families=registry.get("families",[])
    if len(families)!=12:
        raise SystemExit(f"Expected 12 registered families; found {len(families)}")
    missing=[x.get("id","?") for x in families if not x.get("repo_id") or not x.get("revision")]
    if missing:
        raise SystemExit("Refusing to download moving/unpinned checkpoints. Add immutable revision SHAs for: "+", ".join(missing))
    print("Private archive only. This script does not publish or redistribute weights.")
    for item in families:
        print(f"{item['id']}: {item['repo_id']} @ {item['revision']}")
        if args.dry_run: continue
        command=[sys.executable,str(Path(__file__).with_name("download_snapshot.py")),
                 item["repo_id"],"--revision",item["revision"],"--output",args.output]
        subprocess.run(command,check=True)
    print("All pinned snapshots downloaded. Verify manifests and run model-load tests before marking any entry archived.")

if __name__=="__main__": main()
