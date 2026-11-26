import test from "node:test";
import assert from "node:assert/strict";
import worker from "../../src/index.js";

test("legacy edge entrypoint delegates to the canonical WASTI NEXUS gateway", async () => {
  const health = await worker.fetch(new Request("https://nexus.example/healthz"), {});
  assert.equal(health.status, 200);
  const payload = await health.json();
  assert.equal(payload.service, "wasti-nexus-gateway");
  assert.equal(payload.status, "ok");
});

test("legacy edge entrypoint ignores Workers AI and exposes no models without a configured provider", async () => {
  const key = "legacy-edge-test-key-".padEnd(40, "x");
  let aiCalls = 0;
  const response = await worker.fetch(new Request("https://nexus.example/v1/models", {
    headers: { Authorization: "Bearer " + key },
  }), {
    NEXUS_API_KEY: key,
    AI: { async run() { aiCalls += 1; return { response: "unused" }; } },
  });
  assert.equal(response.status, 200);
  const ids = (await response.json()).data.map(model => model.id);
  assert.deepEqual(ids, []);
  assert.equal(aiCalls, 0);
});
