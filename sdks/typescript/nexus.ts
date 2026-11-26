export type NexusMessage = { role: "system" | "user" | "assistant"; content: string };
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

  hybrid(messages: NexusMessage[], agents?: string[], synthesisModel?: string, reviewRounds = 1) {
    return this.request<NexusResponse>("/v1/chat/completions", {
      model: "nexus-hybrid", messages, stream: false,
      nexus: { mode: "hybrid", agents: agents || [], synthesis_agent: synthesisModel, review_rounds: Math.max(0, Math.min(2, reviewRounds)) }
    });
  }
}
