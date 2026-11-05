import hashlib
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

import run_reliability_acceptance as runner
from workflows import strategy_authoring as workflow
from klab_client.errors import MissingAssetError


@pytest.mark.parametrize("mutation", [None, "test", "algorithm", "extra", "hash", "source", "wrongclass"])
def test_overlay_requires_exact_accepted_source_and_main_only_whitelist(tmp_path, monkeypatch, mutation):
    directory = tmp_path / "classes"
    directory.mkdir()
    name = "org/integratedmodelling/klab/services/resources/ResourcesProvider.class"
    if mutation == "wrongclass":
        name = "org/integratedmodelling/klab/services/resources/ResourcesProviderTest.class"
    path = directory / name
    path.parent.mkdir(parents=True)
    path.write_bytes(b"explicit controlled compiled class identity")
    source = "klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java"
    record = {"schema": 1, "source_path": source, "test_classes_included": False,
        "scientific_algorithms_or_access_grants_changed": False, "accepted_integration_head": "accepted",
        "source_git_blob": "blob", "classes_directory": str(directory),
        "classes_sha256": {name: hashlib.sha256(path.read_bytes()).hexdigest()}}
    if mutation == "test":
        record["test_classes_included"] = True
    if mutation == "algorithm":
        record["scientific_algorithms_or_access_grants_changed"] = True
    if mutation == "extra":
        (directory / "TestController.class").write_bytes(b"must not reach classpath")
    if mutation == "hash":
        path.write_bytes(b"changed")
    manifest = tmp_path / "overlay.json"
    manifest.write_text(json.dumps(record))
    commands = []
    monkeypatch.setattr(runner.subprocess, "run", lambda command, **kwargs: commands.append(command))
    def output(command, **kwargs):
        return "different" if mutation == "source" and command[1] == "hash-object" else "blob"
    monkeypatch.setattr(runner.subprocess, "check_output", output)
    if mutation:
        with pytest.raises(ValueError):
            runner.accepted_resource_overlay(tmp_path, manifest)
    else:
        assert runner.accepted_resource_overlay(tmp_path, manifest)[0] == directory
        assert any("origin/feature/python-experiment-runner" in c for c in commands)


def test_overlay_is_explicit_before_any_service_start(tmp_path):
    with pytest.raises(ValueError):
        runner.accepted_resource_overlay(tmp_path, None)


def test_denied_asset_absence_requires_actual_missing_not_generic_failure():
    def missing(*args):
        raise MissingAssetError("absent")
    api = SimpleNamespace(resources=SimpleNamespace(retrieve=missing))
    workflow.require_missing(api, "new", "PROJECT")
    api.resources.retrieve = lambda *args: SimpleNamespace(urn="unexpected")
    with pytest.raises(AssertionError):
        workflow.require_missing(api, "new", "PROJECT")


def test_cleanup_does_not_accept_empty_acknowledgement():
    api = SimpleNamespace(transport=SimpleNamespace(request=lambda *args, **kwargs: []))
    with pytest.raises(AssertionError):
        workflow.cleanup(api, "PROJECT", "owned")
