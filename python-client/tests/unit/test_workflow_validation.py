"""Fault injection of shared scenarios, including optimization and failed cleanup."""
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest


def run_probe(tmp_path, suite, corruption, optimize):
    root = Path(__file__).parents[2]
    env = dict(os.environ, PYTHONOPTIMIZE=str(optimize), PYTHONDONTWRITEBYTECODE="1",
        PYTHONPATH=str(root / "tools"))
    process = subprocess.run([sys.executable, "-B", str(root / "tests/fixtures/workflow_process.py"),
        suite, corruption, str(tmp_path)], env=env, capture_output=True, text=True, timeout=30)
    return process, json.loads(process.stdout)


@pytest.mark.parametrize("optimize", [0, 1])
@pytest.mark.parametrize("corruption", ["valid", "identity", "coordinates", "context-false",
    "context-raises", "session-raises", "identity-and-cleanup", "create-context",
    "clustered", "crossed", "hole", "fresh-clustered", "fresh-crossed", "fresh-hole", "fresh-urn",
    "rotated", "reversed", "fresh-rotated", "fresh-reversed"])
def test_geojson_validation_and_owned_cleanup_survive_optimization(tmp_path, corruption, optimize):
    process, record = run_probe(tmp_path, "geojson", corruption, optimize)
    assert record["optimization"] == optimize
    assert record["release_calls"] == (["session"] if corruption == "create-context" else ["context", "session"])
    valid = corruption in ("valid", "rotated", "reversed", "fresh-rotated", "fresh-reversed")
    assert record["passed"] is valid
    assert process.returncode == (0 if valid else 1)
    if corruption == "identity-and-cleanup":
        assert record["error_type"] == "AssertionError" and "identity" in record["message"]
        assert record["notes"] == ["Owned cleanup failed: AssertionError"]
    elif corruption == "coordinates":
        assert "geographic reference" in record["message"]
    elif corruption in ("clustered", "crossed", "fresh-clustered", "fresh-crossed"):
        assert "topology/corners" in record["message"]
    elif corruption in ("hole", "fresh-hole"):
        assert "without holes" in record["message"]
    elif corruption == "fresh-urn":
        assert "identity" in record["message"]


@pytest.mark.parametrize("start", range(4))
@pytest.mark.parametrize("reverse", [False, True])
def test_reference_accepts_all_boundary_rotations_and_windings(start, reverse):
    from copy import deepcopy
    import importlib.util
    from types import SimpleNamespace
    from workflows.geojson_export import validate_reference
    path = Path(__file__).parents[1] / "fixtures/workflow_process.py"
    spec = importlib.util.spec_from_file_location("workflow_reference", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    payload = deepcopy(module.REFERENCE)
    vertices = payload["features"][0]["geometry"]["coordinates"][0][:-1]
    if reverse:
        vertices.reverse()
    vertices = vertices[start:] + vertices[:start]
    payload["features"][0]["geometry"]["coordinates"][0] = [*vertices, vertices[0]]
    original = deepcopy(payload)
    feature = validate_reference(payload, SimpleNamespace(id=42, urn="s.c.42"))
    assert feature == payload["features"][0] and payload == original


@pytest.mark.parametrize("optimize", [0, 1])
@pytest.mark.parametrize("suite,corruption,message", [
    ("resume", "identity", "in-flight Job identity"),
    ("resume", "failed-report", "verification failed"),
    ("provider", "identity", "home runtime ID")])
def test_recovery_and_provider_identity_checks_cannot_be_optimized_away(tmp_path, suite, corruption, message, optimize):
    process, record = run_probe(tmp_path, suite, corruption, optimize)
    assert process.returncode == 1 and not record["passed"]
    assert record["error_type"] == "AssertionError" and message in record["message"]
    assert record["optimization"] == optimize


def test_multiple_cleanup_failures_do_not_mask_the_primary_error():
    from types import SimpleNamespace
    from workflows.scientific_checks import release_owned
    calls = []
    def failed(name):
        calls.append(name)
        raise OSError("test-only-token")
    scopes = [SimpleNamespace(release=lambda name=name: failed(name)) for name in ("context", "session")]
    primary = ValueError("scientific failure")
    release_owned(*scopes, failure=primary)
    assert calls == ["context", "session"]
    assert str(primary) == "scientific failure"
    assert primary.__notes__ == ["Owned cleanup failed: OSError"] * 2
    assert "test-only-token" not in str(primary.__notes__)
