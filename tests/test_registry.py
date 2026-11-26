import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "models" / "registry.json"
ANDROID_CATALOG = ROOT / "android" / "app" / "src" / "main" / "assets" / "nexus-model-catalog.json"
EXPECTED = {
    "qwen",
    "qwen-1.5b",
    "qwen-coder-1.5b",
    "qwen3-0.6b",
    "qwen3-1.7b",
    "smollm2-135m",
    "smollm2-360m",
    "smollm2-1.7b",
    "tinyllama-1.1b",
    "granite-2b",
    "deepseek-r1-1.5b",
    "olmo2-1b",
}


def load_registry():
    data = json.loads(REGISTRY.read_text(encoding="utf-8"))
    assert isinstance(data, dict)
    assert data.get("schema_version") == 2
    assert data.get("brand") == "WASTI NEXUS"
    assert isinstance(data.get("families"), list)
    return data["families"]


def test_registry_contains_exactly_twelve_unique_permissive_candidates():
    families = load_registry()
    ids = [item.get("id") for item in families]
    assert len(families) == 12
    assert len(ids) == len(set(ids))
    assert set(ids) == EXPECTED

    for item in families:
        licence = item["license_id"].lower()
        assert "apache-2.0" in licence or "mit" in licence
        assert item["redistribution"] == "not-approved"


def test_every_family_has_upstream_and_gguf_candidate_metadata():
    for item in load_registry():
        assert item.get("family")
        assert re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", item.get("repo_id", ""))
        assert item.get("source_url", "").startswith("https://huggingface.co/")
        assert item.get("license_id")
        assert item.get("license_review")

        candidate = item.get("local_candidate")
        assert isinstance(candidate, dict)
        assert re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", candidate.get("repo_id", ""))
        assert candidate.get("file_name", "").endswith(".gguf")
        assert candidate.get("quantization") in {"Q4_K_M", "Q3_K_M"}
        assert candidate.get("estimated_size_bytes", 0) > 0
        assert candidate.get("minimum_available_ram_gb", 0) > 0
        assert candidate.get("commercial_allowed") is True
        assert candidate.get("redistribution_allowed") is False
        assert candidate.get("download_status") == "not-downloaded"


def test_all_twelve_candidates_have_pinned_immutable_integrity_manifests():
    for family in load_registry():
        candidate = family["local_candidate"]
        assert re.fullmatch(r"[0-9a-f]{40}", candidate["revision"]), family["id"]
        assert isinstance(candidate["estimated_size_bytes"], int)
        assert candidate["estimated_size_bytes"] > 0
        assert re.fullmatch(r"[0-9a-f]{64}", candidate["sha256"]), family["id"]
        assert candidate["estimated_size_is_approximate"] is False
        assert candidate["runtime_validation"] == "pending_device_test"


def test_registry_and_android_catalogue_reference_the_same_twelve_gguf_manifests():
    registry_by_file = {
        item["local_candidate"]["file_name"]: item["local_candidate"]
        for item in load_registry()
    }
    catalog = json.loads(ANDROID_CATALOG.read_text(encoding="utf-8"))
    android_by_file = {item["file_name"]: item for item in catalog["models"]}

    assert len(registry_by_file) == 12
    assert registry_by_file.keys() == android_by_file.keys()
    for filename, candidate in registry_by_file.items():
        model = android_by_file[filename]
        assert candidate["revision"] == model["source_revision"]
        assert candidate["estimated_size_bytes"] == model["size_bytes"]
        assert candidate["sha256"] == model["sha256"]
        assert candidate["quantization"] == model["quantization"]


def test_twelve_model_archive_size_is_exact():
    total = sum(item["local_candidate"]["estimated_size_bytes"] for item in load_registry())
    assert total == 9586646112
