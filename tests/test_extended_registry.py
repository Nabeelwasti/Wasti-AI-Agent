import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "models" / "extended-model-registry.json"
ANDROID_ASSET = ROOT / "android" / "app" / "src" / "main" / "assets" / "nexus-model-registry.json"

def test_extended_registry_and_android_asset_are_identical_and_unique():
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    android = json.loads(ANDROID_ASSET.read_text(encoding="utf-8"))
    assert registry == android
    assert registry["schema_version"] == 2
    assert registry["entry_count"] == len(registry["entries"]) == 50
    ids = [item["id"] for item in registry["entries"]]
    assert len(ids) == len(set(ids))

def test_each_extended_entry_has_explicit_hosting_licence_and_activation_state():
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    for item in registry["entries"]:
        assert item["name"] and item["creator"] and item["source_url"].startswith("https://")
        assert item["license"] and item["license_status"] in {"verified", "review_required", "restricted"}
        assert isinstance(item["capabilities"], list) and item["capabilities"]
        assert isinstance(item["requires_internet_for_download"], bool)
        assert isinstance(item["requires_internet_for_self_hosted_inference"], bool)
        assert isinstance(item["self_hosted_possible"], bool)
        assert item["openness_class"]
        assert item["hosted_api_free_unlimited"] is False
        assert item["internet_required_for_hosted_api"] is True
        assert item["self_hosting_cost"]
        assert item["status"] in {"candidate_needs_artifact_pin", "blocked_pending_review"}
        assert item["runtime_validation"] == "not_started"
        assert item["exact_checkpoint"] is None
        assert item["sha256"] is None

def test_non_permissive_and_uncertain_entries_are_not_customer_approved():
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    for item in registry["entries"]:
        if item["license_status"] != "verified":
            assert item["commercial_use"].startswith("Not approved") or item["license_status"] == "restricted"
