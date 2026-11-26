#!/usr/bin/env python3
"""Download a pinned Hugging Face snapshot and generate a SHA-256 manifest."""
import argparse, hashlib, json
from pathlib import Path
from huggingface_hub import snapshot_download

def sha256(path: Path) -> str:
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(8*1024*1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('repo_id')
    parser.add_argument('--revision',required=True,help='Immutable upstream commit SHA')
    parser.add_argument('--output',default='./archive')
    args=parser.parse_args()
    target=Path(args.output).resolve()/args.repo_id.replace('/','__')
    target.mkdir(parents=True,exist_ok=True)
    snapshot=Path(snapshot_download(repo_id=args.repo_id,revision=args.revision,local_dir=str(target)))
    files=[]
    for path in sorted(snapshot.rglob('*')):
        if path.is_file() and '.cache' not in path.parts:
            files.append({'path':str(path.relative_to(snapshot)),'bytes':path.stat().st_size,'sha256':sha256(path)})
    manifest={'repo_id':args.repo_id,'revision':args.revision,'files':files}
    (target/'NEXUS-MANIFEST.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps({'archive':str(target),'file_count':len(files),'manifest':str(target/'NEXUS-MANIFEST.json')},indent=2))

if __name__=='__main__': main()
