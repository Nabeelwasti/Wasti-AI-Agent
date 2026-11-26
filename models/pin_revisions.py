#!/usr/bin/env python3
"""Resolve current upstream heads to immutable SHAs before model download."""
import argparse, json, os
from pathlib import Path
from huggingface_hub import HfApi

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--registry",default=str(Path(__file__).with_name("registry.json")))
    parser.add_argument("--write",action="store_true",help="Write resolved SHAs into the registry")
    args=parser.parse_args()
    path=Path(args.registry)
    data=json.loads(path.read_text(encoding="utf-8"))
    api=HfApi(token=os.getenv("HF_TOKEN"))
    failed=[]
    changed=False
    for item in data.get("families",[]):
        if item.get("revision"):
            print(f"PINNED {item['id']}: {item['revision']}")
            continue
        repo_id=item.get("repo_id")
        if not repo_id:
            failed.append((item.get("id","?"),"missing repo_id"))
            continue
        try:
            info=api.model_info(repo_id=repo_id,revision="main")
            sha=getattr(info,"sha",None)
            if not sha or len(sha)<40:
                raise RuntimeError("Hugging Face did not return an immutable commit SHA")
            print(f"RESOLVED {item['id']}: {repo_id} -> {sha}")
            if args.write:
                item["revision"]=sha
                changed=True
        except Exception as exc:
            failed.append((item.get("id","?"),type(exc).__name__+": "+str(exc)))
    if args.write and changed:
        path.write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8")
        print("Registry updated. Review the pinned revisions before downloading.")
    elif not args.write:
        print("Dry run only. Re-run with --write to pin these immutable revisions.")
    if failed:
        for family,error in failed: print(f"FAILED {family}: {error}")
        raise SystemExit(1)

if __name__=="__main__": main()
