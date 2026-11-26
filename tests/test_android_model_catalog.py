import json
from pathlib import Path


CATALOG_PATH = (
    Path(__file__).resolve().parents[1]
    / "android"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "nexus-model-catalog.json"
)


def load_catalog():
    return json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


def test_wasti_nexus_catalog_has_twelve_unique_model_candidates():
    catalog = load_catalog()
    models = catalog["models"]

    assert catalog["schema_version"] == 1
    assert catalog["storage"] == "google-drive-saf"
    assert catalog["brand"] == "WASTI NEXUS"
    assert len(models) == 12
    assert len({model["id"] for model in models}) == 12
    assert all(model["display_name"].startswith("WASTI NEXUS") for model in models)


def test_only_fully_pinned_qwen_starter_is_downloadable():
    models = load_catalog()["models"]
    ready = [model for model in models if model["status"] == "ready"]

    assert [model["id"] for model in ready] == ["qwen-0.5b"]
    for model in ready:
        assert model["source_revision"]
        assert isinstance(model["size_bytes"], int) and model["size_bytes"] > 0
        assert len(model["sha256"]) == 64


def test_unverified_candidates_are_blocked_until_all_integrity_fields_exist():
    models = load_catalog()["models"]
    pending = [model for model in models if model["status"] == "pending_verification"]

    assert len(pending) == 11
    for model in pending:
        assert model["size_bytes"] is None
        assert model["sha256"] is None
        assert model["source_revision"] is None
        assert model["source"].startswith("https://huggingface.co/")
        assert model["upstream_model"]


def test_selected_models_use_permissive_upstream_licence_families():
    models = load_catalog()["models"]
    for model in models:
        licence = model["license"].lower()
        assert "apache-2.0" in licence or "mit" in licence
        assert "non-commercial" not in licence
        assert "research only" not in licence
