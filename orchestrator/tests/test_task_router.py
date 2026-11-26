from app.task_router import classify_task, rank_agents

def test_classifies_coding_and_routes_specialist_before_general_fallback():
    messages = [{"role": "user", "content": "Please debug this Python stack trace"}]
    config = {
        "general": {"capabilities": ["chat"], "priority": 1},
        "coder": {"capabilities": ["coding", "chat"], "priority": 20},
    }
    task, ranked = rank_agents(messages, config, lambda _: True)
    assert task == "coding"
    assert ranked == ["coder", "general"]

def test_vision_does_not_claim_text_only_model_is_a_vision_model():
    messages = [{"role": "user", "content": "Analyze image and identify the object"}]
    config = {"text": {"capabilities": ["chat"]}}
    task, ranked = rank_agents(messages, config, lambda _: True)
    assert task == "vision"
    assert ranked == []  # no text-only fallback may claim to perform vision

def test_license_policy_excludes_uncleared_agents():
    messages = [{"role": "user", "content": "Explain this"}]
    config = {"approved": {"capabilities": ["chat"]}, "blocked": {"capabilities": ["chat"]}}
    task, ranked = rank_agents(messages, config, lambda item: item is config["approved"])
    assert task == "general"
    assert ranked == ["approved"]

def test_unknown_prompt_routes_to_general_chat():
    assert classify_task([{"role": "user", "content": "Hello there"}]) == "general"

def test_structured_image_content_requires_vision_capability():
    messages = [{"role": "user", "content": [{"type": "text", "text": "Describe this"}, {"type": "image_url", "image_url": {"url": "data:image/png;base64,AAAA"}}]}]
    config = {"text": {"capabilities": ["chat"]}, "vision": {"capabilities": ["chat", "vision"]}}
    task, ranked = rank_agents(messages, config, lambda _: True)
    assert task == "vision"
    assert ranked == ["vision"]


def test_structured_audio_content_requires_speech_capability():
    messages = [{"role": "user", "content": [{"type": "input_audio", "audio": {"data": "AAAA"}}]}]
    config = {"text": {"capabilities": ["chat"]}, "speech": {"capabilities": ["speech-to-text"]}}
    task, ranked = rank_agents(messages, config, lambda _: True)
    assert task == "speech_to_text"
    assert ranked == ["speech"]

def test_task_specific_priority_overrides_global_priority():
    messages = [{"role": "user", "content": "Please write code for this"}]
    config = {
        "general": {"capabilities": ["chat"], "priority": 1, "task_priority": {"coding": 50}},
        "coder": {"capabilities": ["coding", "chat"], "priority": 20, "task_priority": {"coding": 0}},
    }
    task, ranked = rank_agents(messages, config, lambda _: True)
    assert task == "coding"
    assert ranked == ["coder", "general"]
