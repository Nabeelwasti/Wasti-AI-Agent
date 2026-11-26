# Extended model registry

The canonical machine-readable catalogue is [models/extended-model-registry.json](../models/extended-model-registry.json). It contains 48 requested model families and tools with creator, licence status, capabilities, commercial-use gate, phone/server/cluster placement, hosting and activation requirements.

Android bundles the same catalogue as `nexus-model-registry.json` and displays it in Settings → Model Governance. The separate `nexus-model-catalog.json` remains the only archive-ready catalogue for the original twelve pinned GGUF artifacts.

**A catalogue entry is not an installable model.** Every candidate requires an exact checkpoint, immutable revision, licence/usage-policy capture, checksum, runtime adapter and target-hardware benchmark before it can be enabled.

Cloudflare Workers AI is disabled. Model archive storage remains Google Drive SAF only. The Termux server and native Android runtime are local/self-hosted paths.
