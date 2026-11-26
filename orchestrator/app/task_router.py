from __future__ import annotations

import re
from typing import Any, Callable

TASK_PATTERNS = (
    ("image_generation", ("generate image", "create image", "draw", "illustration", "flux")),
    ("speech_to_text", ("transcribe", "speech to text", "voice note", "audio transcription")),
    ("text_to_speech", ("read aloud", "text to speech", "speak this", "voice output")),
    ("vision", ("analyze image", "what is in this picture", "image input", "photo", "visual inspection")),
    ("coding", ("write code", "debug", "program", "python", "kotlin", "javascript", "compile", "stack trace")),
    ("fine_tuning", ("fine tune", "fine-tune", "lora", "qlora", "train an adapter", "dataset")),
    ("reasoning", ("compare", "evaluate", "plan", "strategy", "analyze", "research", "reason")),
)

TASK_CAPABILITIES = {
    "image_generation": {"image-generation"}, "speech_to_text": {"speech-to-text"},
    "text_to_speech": {"text-to-speech"}, "vision": {"vision"},
    "coding": {"coding"}, "fine_tuning": {"training"},
    "reasoning": {"reasoning"}, "general": {"chat"},
}

def classify_task(messages: list[dict[str, Any]]) -> str:
    text = " ".join(str(m.get("content", "")) for m in messages if m.get("role") in {"user", "system"}).lower()
    for task, phrases in TASK_PATTERNS:
        if any(re.search(r"(?<!\w)" + re.escape(p) + r"(?!\w)", text) for p in phrases):
            return task
    return "general"

def rank_agents(messages: list[dict[str, Any]], config: dict[str, dict[str, Any]], allowed: Callable[[dict[str, Any]], bool]) -> tuple[str, list[str]]:
    task = classify_task(messages)
    required = TASK_CAPABILITIES[task]
    ranked: list[tuple[int, int, str]] = []
    for agent_id, agent in config.items():
        if not allowed(agent):
            continue
        capabilities = {str(v).lower() for v in agent.get("capabilities", ["chat"])}
        if task != "general" and required.isdisjoint(capabilities):
            continue
        try:
            priority = int(agent.get("priority", 100))
        except (TypeError, ValueError):
            priority = 100
        ranked.append((priority, 0 if task in capabilities else 1, agent_id))
    ranked.sort()
    if task in {"coding", "reasoning"}:
        known = {item[2] for item in ranked}
        fallbacks = []
        for agent_id, agent in config.items():
            if agent_id in known or not allowed(agent):
                continue
            if "chat" in {str(v).lower() for v in agent.get("capabilities", ["chat"])}:
                try:
                    priority = int(agent.get("priority", 100))
                except (TypeError, ValueError):
                    priority = 100
                fallbacks.append((priority, 2, agent_id))
        ranked.extend(sorted(fallbacks))
    return task, [item[2] for item in ranked]
