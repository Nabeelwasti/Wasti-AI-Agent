import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const apiKey = "nexus-test-key-".padEnd(40, "x");
const token = "github-test-token-".padEnd(32, "x");
const snapshotId = "123e4567-e89b-42d3-a456-426614174000";
const env = {
  NEXUS_API_KEY: apiKey,
  NEXUS_GITHUB_TOKEN: token,
  NEXUS_MEMORY_REPOSITORY: "Nabeelwasti/Wasti-AI-Agent",
  NEXUS_MEMORY_BRANCH: "main",
  NEXUS_MEMORY_PREFIX: "memory/personal/encrypted",
  NEXUS_DEPLOYMENT_MODE: "customer",
  NEXUS_RATE_LIMIT: {
    async limit() { return { success: true }; },
  },
};

function encryptedInput(id = snapshotId) {
  return {
    snapshot_id: id,
    created_at: "2026-11-26T12:00:00+05:00",
    key_id: "pbkdf2-sha256-600k-v1",
    kdf: "PBKDF2-HMAC-SHA256",
    iterations: 600000,
    salt: "AAAAAAAAAAAAAAAAAAAAAA",
    nonce: "AAAAAAAAAAAAAAAA",
    ciphertext: "AAAAAAAAAAAAAAAAAAAAAA",
    content_type: "project-note",
    tags: ["nexus", "project-memory"],
  };
}

function authedRequest(path, method = "GET", body = undefined) {
  return new Request("https://nexus.example" + path, {
    method,
    headers: {
      Authorization: "Bearer " + apiKey,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

test("health route is public and does not expose provider secrets", async () => {
  const response = await worker.fetch(new Request("https://nexus.example/healthz"), env);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.service, "wasti-nexus-gateway");
  assert.equal(data.configured_models, 0);
  assert.deepEqual(data.model_ids, []);
  assert.equal(data.cloudflare_ai_enabled, false);
  assert.equal(data.inference_backend, "self-hosted-provider-relay-only");
  assert.equal(data.storage_backend, "google-drive-only-client-managed");
  assert.equal(JSON.stringify(data).includes(token), false);
});

test("Cloudflare AI bindings are ignored and no first-party hosted inference models are exposed", async () => {
  let calls = 0;
  const configured = { ...env, AI: { async run() { calls += 1; return { response: "must not run" }; } } };
  const health = await worker.fetch(new Request("https://nexus.example/healthz"), configured);
  const data = await health.json();
  assert.equal(data.cloudflare_ai_enabled, false);
  assert.equal(data.configured_models, 0);
  const catalogue = await worker.fetch(authedRequest("/v1/models"), configured);
  assert.deepEqual((await catalogue.json()).data, []);
  const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
    model: "nexus-gemma-4-26b-a4b",
    messages: [{ role: "user", content: "Hello" }],
    stream: false,
  }), configured);
  assert.equal(response.status, 404);
  assert.equal(calls, 0);
});
test("hybrid refuses to present one model as a multi-agent result", async () => {
  const configured = {
    ...env,
    NEXUS_PROVIDERS_JSON: JSON.stringify({
      providers: [{
        id: "one-provider",
        base_url: "https://provider.example/v1",
        api_key_env: "UPSTREAM_KEY",
        models: [{ id: "only-model", enabled: true, commercial_allowed: true, license_status: "verified" }],
      }],
    }),
    UPSTREAM_KEY: "test-provider-key",
  };
  const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
    model: "nexus-hybrid",
    messages: [{ role: "user", content: "Compare several perspectives." }],
    stream: false,
  }), configured);
  assert.equal(response.status, 503);
  const data = await response.json();
  assert.match(data.error.message, /at least two distinct configured model IDs/);
  assert.deepEqual(data.error.details.available_models.map(model => model.id), ["only-model"]);
});

test("self-hosted providers expose verified capabilities and are the only inference route", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (input, init = {}) => {
    calls.push({ url: String(input), init });
    return Response.json({ choices: [{ message: { content: "Self-hosted inference answer." } }] });
  };
  try {
    const configured = {
      ...env,
      NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
        id: "private-host", base_url: "https://inference.example/v1", api_key_env: "PRIVATE_HOST_KEY",
        models: [{ id: "private-qwen", upstream_model: "qwen3-4b-q4", enabled: true, commercial_allowed: true, license: "Apache-2.0", license_status: "verified", capabilities: ["chat", "coding", "reasoning"] }],
      }] }),
      PRIVATE_HOST_KEY: "private-host-secret",
    };
    const health = await worker.fetch(new Request("https://nexus.example/healthz"), configured);
    const healthData = await health.json();
    assert.equal(healthData.configured_models, 1);
    assert.equal(healthData.cloudflare_ai_enabled, false);
    const catalogue = await worker.fetch(authedRequest("/v1/models"), configured);
    const models = (await catalogue.json()).data;
    assert.equal(models[0].id, "private-qwen");
    assert.deepEqual(models[0].capabilities, ["chat", "coding", "reasoning"]);
    assert.equal(models[0].license, "Apache-2.0");
    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "private-qwen", messages: [{ role: "user", content: "Hello" }], stream: false,
    }), configured);
    assert.equal(response.status, 200);
    assert.equal((await response.json()).choices[0].message.content, "Self-hosted inference answer.");
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, "https://inference.example/v1/chat/completions");
    assert.equal(calls[0].init.redirect, "manual");
    assert.equal(calls[0].init.headers.Authorization, "Bearer private-host-secret");
  } finally { globalThis.fetch = originalFetch; }
});

test("hybrid uses two distinct self-hosted model IDs and reports actual participants", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (input, init = {}) => {
    const body = JSON.parse(init.body);
    calls.push({ url: String(input), model: body.model });
    return Response.json({ choices: [{ message: { content: "Answer from " + body.model } }] });
  };
  try {
    const configured = {
      ...env,
      NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
        id: "private-host", base_url: "https://inference.example/v1", api_key_env: "PRIVATE_HOST_KEY",
        models: [
          { id: "model-a", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat", "reasoning"] },
          { id: "model-b", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat", "reasoning"] },
        ],
      }] }),
      PRIVATE_HOST_KEY: "private-host-secret",
    };
    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "nexus-hybrid", messages: [{ role: "user", content: "Compare the two model perspectives." }],
      stream: false, nexus: { mode: "hybrid", review_rounds: 0 },
    }), configured);
    assert.equal(response.status, 200);
    const data = await response.json();
    assert.equal(data.model, "nexus-hybrid");
    assert.equal(data.nexus.mode, "hybrid");
    assert.deepEqual(data.nexus.participants.map(item => item.id).sort(), ["model-a", "model-b"]);
    assert.equal(calls.length, 3);
    assert.equal(calls.every(item => item.url === "https://inference.example/v1/chat/completions"), true);
  } finally { globalThis.fetch = originalFetch; }
});
test("scoped per-application keys can use self-hosted chat but cannot access personal memory", async () => {
  const originalFetch = globalThis.fetch;
  const clientKey = "nexus-client-key-for-mobile-app-0123456789";
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clientKey));
  const sha256 = [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, "0")).join("");
  globalThis.fetch = async () => Response.json({ choices: [{ message: { content: "Scoped client inference works." } }] });
  const configured = {
    ...env,
    PRIVATE_HOST_KEY: "private-host-secret",
    NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
      id: "private-host", base_url: "https://inference.example/v1", api_key_env: "PRIVATE_HOST_KEY",
      models: [{ id: "private-qwen", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat"] }],
    }] }),
    NEXUS_CLIENT_KEYS_JSON: JSON.stringify({ keys: [{ id: "mobile-app", sha256, scopes: ["models", "chat"] }] }),
  };
  const clientRequest = (path, method = "GET", body = undefined) => new Request("https://nexus.example" + path, {
    method,
    headers: { Authorization: "Bearer " + clientKey, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  try {
    const models = await worker.fetch(clientRequest("/v1/models"), configured);
    assert.equal(models.status, 200);
    assert.equal((await models.json()).data[0].id, "private-qwen");
    const chat = await worker.fetch(clientRequest("/v1/chat/completions", "POST", {
      model: "private-qwen", messages: [{ role: "user", content: "hello" }], stream: false,
    }), configured);
    assert.equal(chat.status, 200);
    assert.equal((await chat.json()).choices[0].message.content, "Scoped client inference works.");
    const memory = await worker.fetch(clientRequest("/v1/memory/snapshots"), configured);
    assert.equal(memory.status, 403);
  } finally { globalThis.fetch = originalFetch; }
});
test("external providers reject HTTP and credential-bearing base URLs", async () => {
  for (const base_url of ["http://provider.example/v1", "https://user:password@provider.example/v1"]) {
    const configured = {
      ...env,
      UPSTREAM_KEY: "upstream-test-key",
      NEXUS_PROVIDERS_JSON: JSON.stringify({
        providers: [{
          id: "unsafe-provider",
          base_url,
          api_key_env: "UPSTREAM_KEY",
          models: [{ id: "unsafe-model", enabled: true, commercial_allowed: true, license_status: "verified" }],
        }],
      }),
    };
    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "unsafe-model",
      messages: [{ role: "user", content: "hello" }],
      stream: false,
    }), configured);
    assert.equal(response.status, 503);
  }
});

test("external provider redirects are not followed", async () => {
  const originalFetch = globalThis.fetch;
  let redirectMode = null;
  globalThis.fetch = async (_url, init = {}) => {
    redirectMode = init.redirect;
    return new Response("redirect", { status: 302, headers: { Location: "https://attacker.example/collect" } });
  };
  try {
    const configured = {
      ...env,
      UPSTREAM_KEY: "upstream-test-key",
      NEXUS_PROVIDERS_JSON: JSON.stringify({
        providers: [{
          id: "redirect-provider",
          base_url: "https://provider.example/v1",
          api_key_env: "UPSTREAM_KEY",
          models: [{ id: "redirect-model", enabled: true, commercial_allowed: true, license_status: "verified" }],
        }],
      }),
    };
    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "redirect-model",
      messages: [{ role: "user", content: "hello" }],
      stream: false,
    }), configured);
    assert.equal(redirectMode, "manual");
    assert.equal(response.status, 502);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("customer API fails closed without the rate-limit binding", async () => {
  const response = await worker.fetch(authedRequest("/v1/models"), {
    ...env,
    NEXUS_RATE_LIMIT: undefined,
  });
  assert.equal(response.status, 503);
});

test("customer API returns 429 when the per-key rate limit is exceeded", async () => {
  const response = await worker.fetch(authedRequest("/v1/models"), {
    ...env,
    NEXUS_RATE_LIMIT: { async limit() { return { success: false }; } },
  });
  assert.equal(response.status, 429);
  assert.equal(response.headers.get("retry-after"), "60");
});

test("protected API rejects missing or invalid bearer tokens", async () => {
  const response = await worker.fetch(new Request("https://nexus.example/v1/models"), env);
  assert.equal(response.status, 401);
});

test("legacy master API keys containing URL-safe and punctuation characters remain accepted", async () => {
  const legacyKey = "legacy/master+token=with-punctuation-0123456789";
  const configured = { ...env, NEXUS_API_KEY: legacyKey };
  const request = new Request("https://nexus.example/v1/models", {
    headers: { Authorization: "Bearer " + legacyKey },
  });
  const response = await worker.fetch(request, configured);
  assert.equal(response.status, 200);
});

test("memory write, list and read round-trip only encrypted payloads", async () => {
  const originalFetch = globalThis.fetch;
  let storedContent = null;
  let storedFilename = null;
  let writes = 0;
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(input);
    if (url.pathname === "/repos/Nabeelwasti/Wasti-AI-Agent") {
      return Response.json({ private: false });
    }
    if (url.pathname.endsWith("/memory/personal/encrypted") && (init.method || "GET") === "GET") {
      return Response.json(storedContent ? [{
        name: storedFilename,
        type: "file",
        sha: "a".repeat(40),
      }] : []);
    }
    if (url.pathname.endsWith(".json") && init.method === "PUT") {
      writes += 1;
      storedFilename = url.pathname.split("/").pop();
      const payload = JSON.parse(init.body);
      storedContent = payload.content;
      return Response.json({ commit: { sha: "b".repeat(40) } }, { status: 201 });
    }
    if (storedFilename && url.pathname.endsWith(storedFilename) && (init.method || "GET") === "GET") {
      return Response.json({ encoding: "base64", content: storedContent });
    }
    throw new Error("Unexpected mocked GitHub request: " + url.pathname);
  };

  try {
    const write = await worker.fetch(authedRequest("/v1/memory/snapshots", "POST", encryptedInput()), env);
    assert.equal(write.status, 201);
    const written = await write.json();
    assert.equal(written.snapshot_id, snapshotId);
    assert.equal(written.commit_sha, "b".repeat(40));
    assert.equal(writes, 1);

    const listed = await worker.fetch(authedRequest("/v1/memory/snapshots"), env);
    assert.equal(listed.status, 200);
    assert.equal((await listed.json()).data[0].snapshot_id, snapshotId);

    const read = await worker.fetch(authedRequest("/v1/memory/snapshots/" + snapshotId), env);
    assert.equal(read.status, 200);
    const record = (await read.json()).data;
    assert.equal(record.cipher.algorithm, "AES-256-GCM");
    assert.equal(record.cipher.kdf, "PBKDF2-HMAC-SHA256");
    assert.equal(record.cipher.iterations, 600000);
    assert.equal(record.cipher.salt, encryptedInput().salt);
    assert.equal(record.cipher.ciphertext, encryptedInput().ciphertext);
    assert.equal(JSON.stringify(record).includes("plaintext"), false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("memory endpoint rejects malformed AES-GCM nonce before GitHub write", async () => {
  const originalFetch = globalThis.fetch;
  let writes = 0;
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(input);
    if (url.pathname === "/repos/Nabeelwasti/Wasti-AI-Agent") return Response.json({ private: false });
    if (init.method === "PUT") writes += 1;
    throw new Error("Unexpected request");
  };
  try {
    const body = encryptedInput();
    body.nonce = "short";
    const response = await worker.fetch(authedRequest("/v1/memory/snapshots", "POST", body), env);
    assert.equal(response.status, 400);
    assert.equal(writes, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("memory endpoint refuses a repository that is not public", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({ private: true });
  try {
    const response = await worker.fetch(authedRequest("/v1/memory/snapshots"), env);
    assert.equal(response.status, 503);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("memory endpoint fails closed when GitHub token is missing", async () => {
  const response = await worker.fetch(
    authedRequest("/v1/memory/snapshots"),
    { ...env, NEXUS_GITHUB_TOKEN: "" },
  );
  assert.equal(response.status, 503);
});

test("online console is served by the Worker without Cloudflare storage", async () => {
  const response = await worker.fetch(new Request("https://nexus.example/"), env);
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /text\/html/);
  const html = await response.text();
  assert.match(html, /WASTI NEXUS/);
  assert.match(html, /nexus-hybrid/);
  assert.match(response.headers.get("content-security-policy"), /frame-ancestors 'none'/);
});

test("customer catalogue hides models without explicit commercial clearance", async () => {
  const configured = {
    ...env,
    NEXUS_PROVIDERS_JSON: JSON.stringify({
      providers: [{
        id: "test-provider",
        models: [
          {id: "restricted-model", enabled: true, commercial_allowed: false, license_status: "review_required"},
          {id: "approved-model", enabled: true, commercial_allowed: true, license_status: "verified"},
        ],
      }],
    }),
  };
  const response = await worker.fetch(authedRequest("/v1/models"), configured);
  assert.equal(response.status, 200);
  const ids = (await response.json()).data.map(model => model.id);
  assert.deepEqual(ids, ["approved-model", "nexus-auto"]);
});

test("chat gateway rejects streaming before provider dispatch", async () => {
  const response = await worker.fetch(
    authedRequest("/v1/chat/completions", "POST", {
      model: "qwen",
      messages: [{role: "user", content: "hello"}],
      stream: true,
    }),
    env,
  );
  assert.equal(response.status, 400);
});


test("auto model is advertised and routes to a compatible self-hosted model", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (input, init = {}) => {
    const payload = JSON.parse(init.body);
    calls.push({ url: String(input), model: payload.model });
    return Response.json({ choices: [{ message: { content: "Auto-routed answer." } }] });
  };
  try {
    const configured = {
      ...env,
      NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
        id: "private-host",
        base_url: "https://inference.example/v1",
        api_key_env: "PRIVATE_HOST_KEY",
        models: [
          { id: "general-chat", upstream_model: "qwen-general", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat"] },
          { id: "qwen-coder", upstream_model: "qwen-coder-1.5b", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat", "coding"] },
        ],
      }] }),
      PRIVATE_HOST_KEY: "private-host-secret",
    };
    const catalogue = await worker.fetch(authedRequest("/v1/models"), configured);
    const ids = (await catalogue.json()).data.map(model => model.id);
    assert.deepEqual(ids, ["general-chat", "qwen-coder", "nexus-auto"]);

    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "nexus-auto",
      nexus: { mode: "auto" },
      messages: [{ role: "user", content: "Write Python code to sort a list." }],
      stream: false,
    }), configured);
    assert.equal(response.status, 200);
    const data = await response.json();
    assert.equal(data.model, "nexus-auto");
    assert.equal(data.nexus.mode, "auto");
    assert.equal(data.nexus.task, "coding");
    assert.equal(data.nexus.selected_model, "qwen-coder");
    assert.equal(calls.length, 1);
    assert.equal(calls[0].model, "qwen-coder-1.5b");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("auto mode refuses unsupported modalities instead of pretending a text model can handle them", async () => {
  const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
    model: "nexus-auto",
    nexus: { mode: "auto" },
    messages: [{ role: "user", content: "Generate image of a red car." }],
    stream: false,
  }), {
    ...env,
    NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
      id: "private-host",
      base_url: "https://inference.example/v1",
      api_key_env: "PRIVATE_HOST_KEY",
      models: [{ id: "text-only", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat"] }],
    }] }),
    PRIVATE_HOST_KEY: "private-host-secret",
  });
  assert.equal(response.status, 400);
  const data = await response.json();
  assert.match(data.error.message, /does not have a verified runtime/);
});


test("vision auto-routing selects only a configured vision-capable self-hosted model", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (input, init = {}) => {
    const payload = JSON.parse(init.body);
    calls.push(payload);
    return Response.json({ choices: [{ message: { content: "The image contains a test pattern." } }] });
  };
  try {
    const configured = {
      ...env,
      NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
        id: "private-host",
        base_url: "https://inference.example/v1",
        api_key_env: "PRIVATE_HOST_KEY",
        models: [
          { id: "text-only", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat"] },
          { id: "vision-brain", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat", "vision"] },
        ],
      }] }),
      PRIVATE_HOST_KEY: "private-host-secret",
    };
    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "nexus-auto",
      nexus: { mode: "auto" },
      messages: [{ role: "user", content: [
        { type: "text", text: "Describe this image." },
        { type: "image_url", image_url: { url: "data:image/png;base64,iVBORw0KGgo=" } },
      ] }],
      stream: false,
    }), configured);
    assert.equal(response.status, 200);
    const data = await response.json();
    assert.equal(data.nexus.task, "vision");
    assert.equal(data.nexus.selected_model, "vision-brain");
    assert.equal(calls.length, 1);
    assert.equal(calls[0].messages[0].content[1].image_url.url, "data:image/png;base64,iVBORw0KGgo=");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("single-brain image requests reject models without verified vision capability", async () => {
  const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
    model: "text-only",
    messages: [{ role: "user", content: [
      { type: "text", text: "Describe this." },
      { type: "image_url", image_url: { url: "data:image/jpeg;base64,/9j/2Q==" } },
    ] }],
    stream: false,
  }), {
    ...env,
    NEXUS_PROVIDERS_JSON: JSON.stringify({ providers: [{
      id: "private-host",
      base_url: "https://inference.example/v1",
      api_key_env: "PRIVATE_HOST_KEY",
      models: [{ id: "text-only", enabled: true, commercial_allowed: true, license_status: "verified", capabilities: ["chat"] }],
    }] }),
    PRIVATE_HOST_KEY: "private-host-secret",
  });
  assert.equal(response.status, 400);
  assert.match((await response.json()).error.message, /not verified as vision-capable/);
});

test("gateway rejects external image URLs and unsupported image MIME types", async () => {
  for (const url of ["https://example.com/image.png", "data:image/gif;base64,R0lGODlh"]) {
    const response = await worker.fetch(authedRequest("/v1/chat/completions", "POST", {
      model: "text-only",
      messages: [{ role: "user", content: [{ type: "image_url", image_url: { url } }] }],
      stream: false,
    }), env);
    assert.equal(response.status, 400);
  }
});
