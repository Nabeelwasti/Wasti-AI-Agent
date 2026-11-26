import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

test("health endpoint is public and reports configured model count", async () => {
  const response = await worker.fetch(new Request("https://nexus.example/healthz"), {});
  assert.equal(response.status, 200);
  assert.equal((await response.json()).configured_models, 0);
});

test("protected model catalog rejects missing API key", async () => {
  const response = await worker.fetch(new Request("https://nexus.example/v1/models"), {
    NEXUS_API_KEY: "a".repeat(40),
    NEXUS_PROVIDERS_JSON: '{"providers":[]}',
  });
  assert.equal(response.status, 401);
});

test("Hybrid request runs independent agents, review and synthesis", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (_url, init) => {
    const body = JSON.parse(init.body);
    const answer = body.messages.some((m) => String(m.content).includes("synthesis coordinator"))
      ? "collective final answer"
      : body.messages.some((m) => String(m.content).includes("bounded multi-agent review"))
        ? "peer review"
        : "independent answer from " + body.model;
    return new Response(JSON.stringify({
      choices: [{ message: { content: answer } }],
      usage: { total_tokens: 12 },
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const env = {
      NEXUS_API_KEY: "a".repeat(40),
      QWEN_KEY: "qwen-secret",
      LLAMA_KEY: "llama-secret",
      NEXUS_PROVIDERS_JSON: JSON.stringify({
        providers: [{
          id: "test-provider",
          base_url: "https://provider.example/v1",
          api_key_env: "QWEN_KEY",
          models: [
            { id: "qwen", upstream_model: "qwen-upstream", enabled: true },
            { id: "llama", upstream_model: "llama-upstream", enabled: true },
          ],
        }],
      }),
    };
    const request = new Request("https://nexus.example/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: "Bearer " + env.NEXUS_API_KEY, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "nexus-hybrid",
        messages: [{ role: "user", content: "Plan a project" }],
        nexus: { mode: "hybrid", agents: ["qwen", "llama"], synthesis_agent: "qwen", review_rounds: 1 },
      }),
    });
    const response = await worker.fetch(request, env);
    assert.equal(response.status, 200);
    const result = await response.json();
    assert.equal(result.choices[0].message.content, "collective final answer");
    assert.deepEqual(result.nexus.participating_agents, ["qwen", "llama"]);
    assert.equal(result.nexus.review_rounds_completed, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
