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
    assert ranked == ["text"]  # explicitly marked general fallback, not a vision specialist

def test_license_policy_excludes_uncleared_agents():
    messages = [{"role": "user", "content": "Explain this"}]
    config = {"approved": {"capabilities": ["chat"]}, "blocked": {"capabilities": ["chat"]}}
    task, ranked = rank_agents(messages, config, lambda item: item is config["approved"])
    assert task == "general"
    assert ranked == ["approved"]

def test_unknown_prompt_routes_to_general_chat():
    assert classify_task([{"role": "user", "content": "Hello there"}]) == "general"
