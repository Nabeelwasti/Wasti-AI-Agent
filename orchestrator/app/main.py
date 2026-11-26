from __future__ import annotations

import asyncio
import hmac
import json
import os
import time
import uuid
from typing import Any

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

APP_VERSION = "0.1.0"
app = FastAPI(title="WASTI NEXUS Orchestrator", version=APP_VERSION)

class Message(BaseModel):
    role: str = Field(pattern="^(system|user|assistant)$")
    content: str | list[dict[str, Any]]

class NexusOptions(BaseModel):
    mode: str = Field(default="single", pattern="^(single|hybrid)$")
    agents: list[str] = Field(default_factory=list, max_length=12)
    review_rounds: int = Field(default=0, ge=0, le=2)
    synthesis_agent: str | None = None

class ChatRequest(BaseModel):
    model: str = "nexus-hybrid"
    messages: list[Message] = Field(min_length=1, max_length=100)
    temperature: float | None = Field(default=0.3, ge=0, le=2)
    max_tokens: int | None = Field(default=1200, ge=1, le=8192)
    stream: bool = False
    nexus: NexusOptions = Field(default_factory=NexusOptions)

def agents_config() -> dict[str, dict[str, Any]]:
    raw = os.getenv("NEXUS_AGENTS_JSON", "{}")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError("NEXUS_AGENTS_JSON must be valid JSON") from exc
    if not isinstance(data, dict):
        raise RuntimeError("NEXUS_AGENTS_JSON must be a JSON object")
    return data

def authenticate(authorization: str | None = Header(default=None)) -> None:
    expected = os.getenv("NEXUS_API_KEY", "").strip()
    if not expected or expected == "replace-with-a-long-random-secret":
        raise HTTPException(status_code=503, detail="API is not configured: set a strong NEXUS_API_KEY")
    supplied = authorization.removeprefix("Bearer ").strip() if authorization else ""
    if not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Invalid or missing bearer token")

def endpoint_for(base_url: str) -> str:
    base = base_url.rstrip("/")
    return base + "/chat/completions" if base.endswith("/v1") else base + "/v1/chat/completions"

async def call_agent(agent_id: str, config: dict[str, Any], messages: list[dict[str, Any]],
                     temperature: float | None, max_tokens: int | None) -> dict[str, Any]:
    base_url = config.get("base_url")
    model = config.get("model")
    if not base_url or not model:
        raise ValueError(f"Agent '{agent_id}' requires base_url and model")
    key_env = config.get("api_key_env")
    api_key = os.getenv(key_env, "") if key_env else config.get("api_key", "")
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    payload: dict[str, Any] = {"model": model, "messages": messages, "stream": False}
    if temperature is not None:
        payload["temperature"] = temperature
    if max_tokens is not None:
        payload["max_tokens"] = max_tokens
    timeout = float(os.getenv("NEXUS_DEFAULT_TIMEOUT_SECONDS", "45"))
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(endpoint_for(base_url), json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
    choices = data.get("choices") or []
    if not choices:
        raise ValueError(f"Agent '{agent_id}' returned no choices")
    content = (choices[0].get("message") or {}).get("content")
    if not isinstance(content, str) or not content.strip():
        raise ValueError(f"Agent '{agent_id}' returned empty or unsupported content")
    return {"agent": agent_id, "model": model, "content": content.strip(),
            "provider_id": data.get("id"), "latency_ms": None}

async def require_agents(ids: list[str], cfg: dict[str, dict[str, Any]]) -> list[str]:
    selected = list(dict.fromkeys(ids))
    if not selected:
        selected = list(cfg.keys())
    selected = selected[: int(os.getenv("NEXUS_MAX_AGENTS", "12"))]
    if not selected:
        raise HTTPException(status_code=503, detail="No AI agents are configured")
    missing = [x for x in selected if x not in cfg]
    if missing:
        raise HTTPException(status_code=400, detail={"message": "Unknown or unconfigured agents", "agents": missing})
    return selected

@app.get("/healthz")
async def healthz():
    return {"status": "ok", "service": "wasti-nexus-orchestrator", "version": APP_VERSION}

@app.get("/v1/agents", dependencies=[Depends(authenticate)])
async def list_agents():
    cfg = agents_config()
    return {"data": [{"id": key, "model": value.get("model"), "configured": bool(value.get("base_url"))}
                     for key, value in cfg.items()]}

@app.get("/v1/models", dependencies=[Depends(authenticate)])
async def list_models():
    cfg = agents_config()
    data = [{"id": key, "object": "model", "owned_by": "configured-provider",
             "name": value.get("model")} for key, value in cfg.items()]
    data.append({"id": "nexus-hybrid", "object": "model", "owned_by": "wasti-nexus",
                 "name": "NEXUS Hybrid collective"})
    return {"object": "list", "data": data}

@app.post("/v1/chat/completions", dependencies=[Depends(authenticate)])
async def chat_completions(req: ChatRequest):
    if req.stream:
        raise HTTPException(status_code=400, detail="Streaming is not implemented yet; set stream=false")
    serialized = json.dumps([m.model_dump() for m in req.messages], ensure_ascii=False)
    max_chars = int(os.getenv("NEXUS_MAX_REQUEST_CHARS", "30000"))
    if len(serialized) > max_chars:
        raise HTTPException(status_code=413, detail="Request exceeds configured character limit")
    cfg = agents_config()
    trace_id = str(uuid.uuid4())
    started = time.perf_counter()

    if req.nexus.mode == "single" and req.model != "nexus-hybrid":
        agent_id = req.model
        if agent_id not in cfg:
            raise HTTPException(status_code=404, detail=f"Model or agent '{agent_id}' is not configured")
        try:
            result = await call_agent(agent_id, cfg[agent_id], [m.model_dump() for m in req.messages],
                                      req.temperature, req.max_tokens)
        except (httpx.HTTPError, ValueError, KeyError) as exc:
            raise HTTPException(status_code=502, detail=f"Upstream agent failed: {str(exc)[:300]}") from exc
        return {"id": f"nexus-{trace_id}", "object": "chat.completion", "created": int(time.time()),
                "model": req.model, "choices": [{"index": 0, "message": {"role": "assistant", "content": result["content"]},
                "finish_reason": "stop"}], "nexus": {"trace_id": trace_id, "mode": "single",
                "participating_agents": [agent_id], "failed_agents": [], "latency_ms": int((time.perf_counter()-started)*1000)}}

    selected = await require_agents(req.nexus.agents, cfg)
    user_messages = [m.model_dump() for m in req.messages]
    semaphore = asyncio.Semaphore(12)
    async def run_one(agent_id: str):
        async with semaphore:
            t0 = time.perf_counter()
            try:
                result = await call_agent(agent_id, cfg[agent_id], user_messages, req.temperature, req.max_tokens)
                result["latency_ms"] = int((time.perf_counter()-t0)*1000)
                return result
            except Exception as exc:
                return {"agent": agent_id, "error": str(exc)[:300],
                        "latency_ms": int((time.perf_counter()-t0)*1000)}
    outcomes = await asyncio.gather(*(run_one(agent_id) for agent_id in selected))
    successful = [x for x in outcomes if "content" in x]
    failed = [x for x in outcomes if "error" in x]
    if not successful:
        raise HTTPException(status_code=502, detail={"message": "All configured agents failed",
                                                     "failed_agents": failed, "trace_id": trace_id})

    synth_id = req.nexus.synthesis_agent
    if not synth_id:
        synth_id = os.getenv("NEXUS_SYNTHESIS_AGENT", "").strip() or None
    if synth_id and synth_id not in cfg:
        raise HTTPException(status_code=400, detail=f"Synthesis agent '{synth_id}' is not configured")
    if synth_id:
        synthesis_prompt = (
            "You are the NEXUS synthesis agent. Produce one accurate, useful answer from the user's request "
            "and the specialist reports below. Resolve contradictions, do not invent consensus, mention material "
            "uncertainty, and do not expose private reasoning. Return only the final answer.\n\n"
            + "USER REQUEST:\n" + json.dumps(user_messages, ensure_ascii=False)
            + "\n\nSPECIALIST REPORTS:\n"
            + "\n\n".join(f"[{x['agent']} / {x['model']}]\n{x['content']}" for x in successful)
        )
        try:
            final = await call_agent(synth_id, cfg[synth_id],
                [{"role": "user", "content": synthesis_prompt}], req.temperature, req.max_tokens)
            answer = final["content"]
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Synthesis agent failed: {str(exc)[:300]}; specialist results are available in error context") from exc
    else:
        answer = "Hybrid analysis completed, but no synthesis agent is configured. Configure NEXUS_SYNTHESIS_AGENT or pass nexus.synthesis_agent to produce one refined collective answer.\n\n" + "\n\n".join(
            f"### {x['agent']} ({x['model']})\n{x['content']}" for x in successful)

    return {"id": f"nexus-{trace_id}", "object": "chat.completion", "created": int(time.time()),
            "model": "nexus-hybrid", "choices": [{"index": 0, "message": {"role": "assistant", "content": answer},
            "finish_reason": "stop"}], "nexus": {"trace_id": trace_id, "mode": "hybrid",
            "participating_agents": [x["agent"] for x in successful],
            "synthesis_agent": synth_id, "failed_agents": failed,
            "review_rounds_requested": req.nexus.review_rounds,
            "review_rounds_completed": 0,
            "note": "Peer-review rounds are reserved in the contract but not implemented in this foundation build.",
            "latency_ms": int((time.perf_counter()-started)*1000)}}
