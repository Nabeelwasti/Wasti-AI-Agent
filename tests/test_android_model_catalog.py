import json
import re
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


def test_all_twelve_models_have_pinned_download_manifests():
    models = load_catalog()["models"]

    assert len(models) == 12
    for model in models:
        assert model["status"] == "ready", model["id"]
        assert re.fullmatch(r"[0-9a-f]{40}", model["source_revision"])
        assert isinstance(model["size_bytes"], int) and model["size_bytes"] > 0
        assert re.fullmatch(r"[0-9a-f]{64}", model["sha256"])
        assert model["runtime_validation"] == "pending_device_test"
    assert sum(model["size_bytes"] for model in models) == 9586646112


def test_model_ids_and_integrity_fields_are_unique_and_complete():
    models = load_catalog()["models"]
    assert len({model["id"] for model in models}) == 12
    assert len({model["file_name"] for model in models}) == 12
    assert len({model["sha256"] for model in models}) == 12


def test_selected_models_use_permissive_upstream_licence_families():
    models = load_catalog()["models"]
    for model in models:
        licence = model["license"].lower()
        assert "apache-2.0" in licence or "mit" in licence
        assert "non-commercial" not in licence
        assert "research only" not in licence
