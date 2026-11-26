import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "models" / "registry.json"
EXPECTED = {
    "llama", "qwen", "gemma", "deepseek", "mistral", "phi",
    "granite", "glm", "command-r", "falcon", "smollm", "stablelm",
}


def load_registry():
    data = json.loads(REGISTRY.read_text(encoding="utf-8"))
    assert isinstance(data, dict)
    assert isinstance(data.get("families"), list)
    return data["families"]


def test_registry_contains_exactly_twelve_unique_families():
    families = load_registry()
    ids = [item.get("id") for item in families]
    assert len(families) == 12
    assert len(ids) == len(set(ids))
    assert set(ids) == EXPECTED


def test_every_family_has_candidate_and_license_metadata():
    for item in load_registry():
        assert item.get("family")
        assert re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", item.get("repo_id", ""))
        assert item.get("license_id")
        assert item.get("license_review")
        assert item.get("redistribution") in {"not-approved", "approved", "conditional"}


def test_pinned_revisions_are_immutable_commit_shas():
    for item in load_registry():
        revision = item.get("revision")
        if revision:
            assert re.fullmatch(r"[0-9a-f]{40}", revision), item["id"]


def test_registry_does_not_claim_unverified_weights_are_downloaded():
    for item in load_registry():
        if not item.get("files"):
            assert item.get("download_status") == "not-downloaded"


def test_noncommercial_candidates_are_not_approved_for_redistribution():
    by_id = {item["id"]: item for item in load_registry()}
    assert by_id["command-r"]["redistribution"] == "not-approved"
    assert by_id["stablelm"]["redistribution"] == "not-approved"

def test_small_local_gguf_candidate_is_registered_with_explicit_metadata():
    qwen = next(item for item in load_registry() if item["id"] == "qwen")
    candidate = qwen["local_candidate"]
    assert candidate["repo_id"] == "Qwen/Qwen2.5-0.5B-Instruct-GGUF"
    assert candidate["file_name"].endswith(".gguf")
    assert candidate["quantization"] == "Q4_K_M"
    assert candidate["estimated_size_bytes"] > 0
    assert candidate["license_id"] == "Apache-2.0"
    assert candidate["download_status"] == "not-downloaded"
    if candidate.get("revision"):
        assert re.fullmatch(r"[0-9a-f]{40}", candidate["revision"])

def test_all_twelve_families_have_a_pinned_candidate_definition_for_local_install():
    for family in load_registry():
        candidate = family.get("local_candidate")
        assert isinstance(candidate, dict), family["id"]
        assert re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", candidate.get("repo_id", ""))
        assert candidate.get("file_name", "").endswith(".gguf")
        assert candidate.get("quantization") == "Q4_K_M"
        assert candidate.get("estimated_size_bytes", 0) > 0
        assert candidate.get("minimum_available_ram_gb", 0) > 0
        assert isinstance(candidate.get("commercial_allowed"), bool)
        assert candidate.get("redistribution_allowed") is False
        assert candidate.get("download_status") == "not-downloaded"
        if candidate.get("revision"):
            assert re.fullmatch(r"[0-9a-f]{40}", candidate["revision"])


def test_noncommercial_local_candidates_are_marked_blocked_for_customer_use():
    by_id = {item["id"]: item["local_candidate"] for item in load_registry()}
    assert by_id["command-r"]["commercial_allowed"] is False
    assert by_id["stablelm"]["commercial_allowed"] is False
    assert by_id["glm"]["commercial_allowed"] is False

def test_qwen_phone_candidate_is_pinned_and_checksum_verified():
    qwen = next(item for item in load_registry() if item["id"] == "qwen")
    candidate = qwen["local_candidate"]
    assert candidate["revision"] == "12145bd1d629190a4d44254073650877954d02c9"
    assert candidate["sha256"] == "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
