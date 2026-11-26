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
        assert candidate.get("quantization") == "Q4_K_M"
        assert candidate.get("estimated_size_bytes", 0) > 0
        assert candidate.get("minimum_available_ram_gb", 0) > 0
        assert candidate.get("commercial_allowed") is True
        assert candidate.get("redistribution_allowed") is False
        assert candidate.get("download_status") == "not-downloaded"


def test_only_qwen_starter_has_a_pinned_integrity_manifest():
    by_id = {item["id"]: item for item in load_registry()}
    candidate = by_id["qwen"]["local_candidate"]
    assert candidate["revision"] == "12145bd1d629190a4d44254073650877954d02c9"
    assert candidate["sha256"] == "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    assert candidate["estimated_size_bytes"] == 491400032

    for model_id, item in by_id.items():
        if model_id != "qwen":
            assert item["local_candidate"]["revision"] is None
            assert item["local_candidate"]["sha256"] is None


def test_registry_and_android_catalogue_reference_the_same_twelve_gguf_files():
    registry_files = {
        item["local_candidate"]["file_name"] for item in load_registry()
    }
    catalog = json.loads(ANDROID_CATALOG.read_text(encoding="utf-8"))
    android_files = {item["file_name"] for item in catalog["models"]}
    assert len(registry_files) == 12
    assert registry_files == android_files
