# NEXUS TypeScript SDK

A dependency-free client for server-side Node.js or trusted backend environments.

```ts
import { NexusClient } from "./index.js";

const nexus = new NexusClient(
  process.env.NEXUS_BASE_URL!,
  process.env.NEXUS_API_KEY!,
);

const result = await nexus.hybrid(
  "Create a practical launch plan for this product.",
  ["qwen", "llama", "gemma"],
  "qwen",
);
console.log(result);
```

**Security:** do not bundle a privileged NEXUS API key into browser JavaScript, public mobile builds or customer-distributed code. Call NEXUS from your backend, authenticate your own users there, enforce tenant permissions, and keep the NEXUS key server-side.
