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
  assert.equal(JSON.stringify(data).includes(token), false);
});

test("Workers AI binding provides first-party brains without third-party provider API keys", async () => {
  const calls = [];
  const configured = {
    ...env,
    AI: {
      async run(model, input) {
        calls.push({ model, input });
        return { response: "A local test answer from Workers AI." };
      },
    },
  };
  const health = await worker.fetch(new Request("https://nexus.example/healthz"), configured);
  const healthData = await health.json();
  assert.equal(healthData.configured_models, 1);
  assert.deepEqual(healthData.model_ids, ["nexus-gemma-4-26b-a4b"]);
  const catalogue = await worker.fetch(authedRequest("/v1/models"), configured);
  assert.equal(catalogue.status, 200);
  const catalogueData = await catalogue.json();
  const ids = catalogueData.data.map(item => item.id);
  assert.deepEqual(ids, ["nexus-gemma-4-26b-a4b"]);
  const firstParty = catalogueData.data[0];
  assert.equal(firstParty.license, "Apache-2.0");
  assert.equal(firstParty.context_window, 256000);
  assert.deepEqual(firstParty.pricing_usd_per_million_tokens, { input: 0.10, output: 0.30 });
  assert.equal(ids.some(id => id.includes("llama-3.2")), false);
  const response = await worker.fetch(
    authedRequest("/v1/chat/completions", "POST", {
      model: "nexus-gemma-4-26b-a4b",
      messages: [{ role: "user", content: "Hello" }],
      stream: false,
    }),
    configured,
  );
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.choices[0].message.content, "A local test answer from Workers AI.");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].model, "@cf/google/gemma-4-26b-a4b-it");
  assert.equal(calls[0].input.messages[0].content, "Hello");
});

test("scoped per-application keys can use chat but cannot access personal memory", async () => {
  const clientKey = "nexus-client-key-for-mobile-app-0123456789";
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clientKey));
  const sha256 = [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, "0")).join("");
  const configured = {
    ...env,
    NEXUS_CLIENT_KEYS_JSON: JSON.stringify({
      keys: [{ id: "mobile-app", sha256, scopes: ["models", "chat"] }],
    }),
    AI: {
      async run() {
        return { response: "Scoped client inference works." };
      },
    },
  };
  const clientRequest = (path, method = "GET", body = undefined) => new Request("https://nexus.example" + path, {
    method,
    headers: {
      Authorization: "Bearer " + clientKey,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

  const models = await worker.fetch(clientRequest("/v1/models"), configured);
  assert.equal(models.status, 200);
  assert.equal((await models.json()).data[0].id, "nexus-gemma-4-26b-a4b");

  const chat = await worker.fetch(clientRequest("/v1/chat/completions", "POST", {
    model: "nexus-gemma-4-26b-a4b",
    messages: [{ role: "user", content: "hello" }],
    stream: false,
  }), configured);
  assert.equal(chat.status, 200);
  assert.equal((await chat.json()).choices[0].message.content, "Scoped client inference works.");

  const memory = await worker.fetch(clientRequest("/v1/memory/snapshots"), configured);
  assert.equal(memory.status, 403);
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
          models: [{ id: "unsafe-model", enabled: true, commercial_allowed: true }],
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
          models: [{ id: "redirect-model", enabled: true, commercial_allowed: true }],
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
          {id: "restricted-model", enabled: true, commercial_allowed: false},
          {id: "approved-model", enabled: true, commercial_allowed: true},
        ],
      }],
    }),
  };
  const response = await worker.fetch(authedRequest("/v1/models"), configured);
  assert.equal(response.status, 200);
  const ids = (await response.json()).data.map(model => model.id);
  assert.deepEqual(ids, ["approved-model"]);
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
