export type NexusMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export type NexusChatOptions = {
  model?: string;
  temperature?: number;
  max_tokens?: number;
  nexus?: {
    mode?: "single" | "hybrid";
    agents?: string[];
    synthesis_agent?: string;
    review_rounds?: number;
  };
};

export class NexusClient {
  private readonly baseUrl: string;

  constructor(
    baseUrl: string,
    private readonly apiKey: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    if (!/^https?:\/\//i.test(this.baseUrl)) {
      throw new Error("NEXUS base URL must use http:// or https://");
    }
    if (!apiKey.trim()) throw new Error("NEXUS API key is required");
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await this.fetchImpl(`${this.baseUrl}${path}`, {
      ...init,
      headers: {
        "Authorization": `Bearer ${this.apiKey}`,
        "Content-Type": "application/json",
        ...init.headers,
      },
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`NEXUS API ${response.status}: ${body.slice(0, 500)}`);
    }
    return response.json() as Promise<T>;
  }

  listModels<T = unknown>(): Promise<T> {
    return this.request<T>("/models");
  }

  chatCompletion<T = unknown>(messages: NexusMessage[], options: NexusChatOptions = {}): Promise<T> {
    return this.request<T>("/chat/completions", {
      method: "POST",
      body: JSON.stringify({
        model: options.model ?? "nexus-hybrid",
        messages,
        temperature: options.temperature ?? 0.3,
        max_tokens: options.max_tokens ?? 1200,
        stream: false,
        nexus: options.nexus ?? { mode: "hybrid" },
      }),
    });
  }

  hybrid<T = unknown>(prompt: string, agents: string[], synthesisAgent: string): Promise<T> {
    return this.chatCompletion<T>([{ role: "user", content: prompt }], {
      model: "nexus-hybrid",
      nexus: { mode: "hybrid", agents, synthesis_agent: synthesisAgent, review_rounds: 0 },
    });
  }
}
