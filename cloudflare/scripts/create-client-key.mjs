#!/usr/bin/env node
import { createHash, randomBytes } from "node:crypto";

function option(name, fallback = "") {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const id = option("--id");
const scopes = option("--scopes", "models,chat")
  .split(",")
  .map(value => value.trim())
  .filter(Boolean);
const allowed = new Set(["models", "chat"]);
if (!id || !/^[a-zA-Z0-9._-]{2,64}$/.test(id)) {
  console.error("Usage: node cloudflare/scripts/create-client-key.mjs --id <app-id> [--scopes models,chat]");
  process.exit(2);
}
if (!scopes.length || scopes.some(scope => !allowed.has(scope))) {
  console.error("Client keys may be granted only the models and chat scopes. Personal memory remains master-key only.");
  process.exit(2);
}

const clientKey = randomBytes(32).toString("base64url");
const sha256 = createHash("sha256").update(clientKey, "utf8").digest("hex");
const workerSecretEntry = { id, sha256, scopes, enabled: true };

console.log("Save this client key in the target app's secure secret store. It cannot be recovered later:");
console.log(clientKey);
console.log("\nAdd this entry to the existing NEXUS_CLIENT_KEYS_JSON secret (do not replace other app entries):");
console.log(JSON.stringify(workerSecretEntry, null, 2));
