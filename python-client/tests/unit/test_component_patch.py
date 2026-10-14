"""Patch integrity and non-destructive component-build entry point guards."""
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from build_geospatial_component import build, load_patch


def test_branch_patch_is_pinned_to_the_reviewed_component_tree():
    manifest, patch = load_patch()
    assert manifest["base_commit"] == "8d4ef8665016761eb12a504a304f02eb3b55f1c7"
    assert manifest["reviewed_commit"] == "a1d36d30bde17d853de71e0cc7d285e597666e4c"
    assert manifest["patched_tree"] == "f05b578a14c707d8fd34348e56adc9a96bf25d66"
    assert patch.is_file()


def test_modified_patch_fails_before_a_build(tmp_path):
    manifest, patch = load_patch()
    (tmp_path / "manifest.json").write_text(json.dumps(manifest))
    (tmp_path / manifest["patch"]).write_bytes(patch.read_bytes() + b"\nchanged\n")
    with pytest.raises(ValueError, match="explicit hash"):
        load_patch(tmp_path)


def test_unknown_manifest_version_is_not_silently_applied(tmp_path):
    (tmp_path / "manifest.json").write_text(json.dumps({"schema_version": 99}))
    with pytest.raises(ValueError, match="Unsupported"):
        load_patch(tmp_path)


def test_existing_work_directory_is_preserved_without_running_git(tmp_path, monkeypatch):
    import build_geospatial_component as implementation
    monkeypatch.setattr(implementation.subprocess, "Popen", lambda *a, **kw: pytest.fail("No Git/build invocation"))
    marker = tmp_path / "keep"
    marker.write_text("existing work")
    with pytest.raises(ValueError, match="fresh work directory"):
        build(SimpleNamespace(work_dir=tmp_path))
    assert marker.read_text() == "existing work" and list(tmp_path.iterdir()) == [marker]
