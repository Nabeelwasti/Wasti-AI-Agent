# NEXUS memory and knowledge files

GitHub is the versioned file source for curated knowledge, small datasets and optional encrypted memory snapshots. It is not the live memory database or inference/training engine.

## Storage tiers

1. Android private app storage: encrypted SQLite is the fast offline working memory and local conversation store.
2. GitHub private repository: batched encrypted snapshots and versioned curated knowledge/dataset files.
3. Retrieval: local index for offline use; online index can be rebuilt from the same committed source files.

## Privacy and access

- Keep personal and customer data in private repositories only.
- Encrypt personal/customer memory on the device before upload. Encryption keys never enter Git.
- Use separate tenant namespaces and verify tenant authorization before reading files.
- GitHub App credentials or a narrowly scoped fine-grained token are server-side secrets only.
- Never embed a GitHub write token in an APK, frontend JavaScript or SDK distributed to customers.
- Never commit passwords, API keys, auth tokens, private keys or unencrypted sensitive records.
- Use batch snapshots and commit SHA provenance. Do not make one commit per chat message.
- Respect GitHub API limits and resolve concurrent-write conflicts; use a local queue when offline or rate-limited.

## Suggested paths

```
memory/
  schemas/
  personal/encrypted/
  projects/<project-id>/
  tenants/<tenant-id>/encrypted/
datasets/
  training/
  eval/
  manifests/
```

Personal memory sync is optional. Local-only mode must work without GitHub credentials or network access.
