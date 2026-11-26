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
