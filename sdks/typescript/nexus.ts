export type NexusMessage = { role: "system" | "user" | "assistant"; content: string };
export type EncryptedMemorySnapshot = {
  snapshot_id: string;
  created_at: string;
  key_id: string;
  kdf: "PBKDF2-HMAC-SHA256";
  iterations: 600000;
  salt: string;
  nonce: string;
  ciphertext: string;
  content_type?: "conversation-summary" | "fact" | "preference" | "project-note" | "knowledge-reference";
  tags?: string[];
};
export type NexusResponse = {
  id: string;
  choices: Array<{ message: { role: "assistant"; content: string } }>;
  nexus?: Record<string, unknown>;
};

export class NexusClient {
  private readonly baseUrl: string;
  constructor(baseUrl: string, private readonly apiKey: string) {
    const normalized = baseUrl.replace(/\/$/, "");
    if (!normalized.startsWith("https://")) throw new Error("NEXUS base URL must use HTTPS");
    if (apiKey.length < 32) throw new Error("NEXUS API key is too short");
    this.baseUrl = normalized;
  }

  private async request<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(this.baseUrl + path, {
      method: body === undefined ? "GET" : "POST",
      headers: { Authorization: "Bearer " + this.apiKey, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error("NEXUS HTTP " + response.status + ": " + JSON.stringify(payload));
    return payload as T;
  }

  models() { return this.request<{ data: Array<{ id: string; object: string }> }>("/v1/models"); }

  chat(model: string, messages: NexusMessage[]) {
    return this.request<NexusResponse>("/v1/chat/completions", { model, messages, stream: false });
  }

  memorySnapshots(limit = 100) {
    const safeLimit = Math.max(1, Math.min(100, Math.trunc(limit)));
    return this.request<{ data: Array<{ snapshot_id: string; sha: string }> }>(
      "/v1/memory/snapshots?limit=" + safeLimit
    );
  }

  createMemorySnapshot(snapshot: EncryptedMemorySnapshot) {
    return this.request<{ object: string; snapshot_id: string; commit_sha: string | null }>(
      "/v1/memory/snapshots", snapshot
    );
  }

  memorySnapshot(snapshotId: string) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(snapshotId)) {
      throw new Error("Invalid memory snapshot UUID");
    }
    return this.request<{ object: string; data: EncryptedMemorySnapshot }>(
      "/v1/memory/snapshots/" + snapshotId
    );
  }

  hybrid(messages: NexusMessage[], agents?: string[], synthesisModel?: string, reviewRounds = 1) {
    return this.request<NexusResponse>("/v1/chat/completions", {
      model: "nexus-hybrid", messages, stream: false,
      nexus: { mode: "hybrid", agents: agents || [], synthesis_agent: synthesisModel, review_rounds: Math.max(0, Math.min(2, reviewRounds)) }
    });
  }
}
