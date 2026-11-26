/**
 * Optional edge proxy for a separately hosted NEXUS orchestrator.
 * Set ORIGIN_BASE_URL as a Worker secret/variable, e.g. https://api-origin.example.com.
 * The client Authorization header is forwarded; the Worker never stores user API keys.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/healthz") {
      return Response.json({ status: "ok", service: "wasti-nexus-edge" });
    }
    if (!url.pathname.startsWith("/v1/")) {
      return new Response("Not found", { status: 404 });
    }
    if (!env.ORIGIN_BASE_URL) {
      return Response.json({ error: "ORIGIN_BASE_URL is not configured" }, { status: 503 });
    }
    const origin = new URL(url.pathname + url.search, env.ORIGIN_BASE_URL);
    const headers = new Headers(request.headers);
    headers.delete("host");
    headers.delete("cf-connecting-ip");
    const upstream = await fetch(new Request(origin, {
      method: request.method,
      headers,
      body: ["GET", "HEAD"].includes(request.method) ? undefined : request.body,
      redirect: "manual"
    }));
    const responseHeaders = new Headers(upstream.headers);
    responseHeaders.set("X-Content-Type-Options", "nosniff");
    responseHeaders.delete("server");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders
    });
  }
};
