"""Controlled harness projection tests only; not real service/scientific evidence."""
import json
from pathlib import Path
from types import SimpleNamespace
import zipfile

import pytest

import run_reliability_acceptance as harness
import run_scientific_acceptance as adapter
from scientific_scenario import AXES, ScenarioError
from test_scientific_scenario import contract  # noqa: F401


@pytest.mark.parametrize("explicit,code,expected", [(True, 0, "PASS"), (False, 0, "NOT_RUN"), (False, 1, "FAIL")])
def test_harness_success_requires_explicit_checks_and_axes(tmp_path, contract, monkeypatch, explicit, code, expected):
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps(contract))
    state = tmp_path / "state"
    monkeypatch.setattr(adapter, "PACKAGE", tmp_path)
    monkeypatch.setattr(harness, "PACKAGE", harness.PACKAGE)
    monkeypatch.setattr(adapter, "installed_wheel", lambda _: "f" * 64)
    def run(args):
        state.mkdir()
        science = {}
        if explicit:
            passed = {"status": "PASS", "reason": "Controlled assertion; unit test only"}
            science = {"expectations": {"values": passed}, "axes": dict.fromkeys(AXES, passed)}
        result = {"passed": code == 0, "server_head": "a" * 40, "science": science,
            "owned_processes_closed": True, "existing_java_unchanged": True, "listeners_closed": True,
            "java_version": "21", "components": [{"sha256": "c" * 64}]}
        (state / "result.json").write_text(json.dumps(result))
        (state / "scientist.json").write_text(json.dumps({"username": "owner", "token": "do-not-copy-token"}))
        return code
    monkeypatch.setattr(harness, "run", run)
    args = SimpleNamespace(manifest=manifest, wheel=tmp_path / "wheel", state_dir=state)
    assert adapter.execute(args) == (expected != "PASS")
    receipt = json.loads((state / "scientific-execution.json").read_text())
    assert receipt["status"] == expected
    assert "do-not-copy-token" not in (state / "scientific-sanitized.json").read_text()
    assert receipt["identities"]["auth_principal"] == "owner"


def test_missing_input_prevents_harness_invocation(tmp_path, contract, monkeypatch):
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps(contract))
    (tmp_path / "oracle.json").unlink()
    monkeypatch.setattr(adapter, "PACKAGE", tmp_path)
    monkeypatch.setattr(harness, "run", lambda _: pytest.fail("Must not execute"))
    with pytest.raises(ScenarioError):
        adapter.execute(SimpleNamespace(manifest=manifest))


@pytest.mark.parametrize("tamper", [False, True])
def test_wheel_identity_is_source_and_installed_bytes_not_filename(tmp_path, monkeypatch, tamper):
    import klab_client
    package = tmp_path / "package"
    source = package / "src/klab_client"
    source.mkdir(parents=True)
    installed = tmp_path / "installed/klab_client"
    installed.mkdir(parents=True)
    for root in (source, installed):
        (root / "__init__.py").write_bytes(b"# exact source\n")
    wheel = tmp_path / "candidate.whl"
    with zipfile.ZipFile(wheel, "w") as archive:
        archive.writestr("klab_client/__init__.py", b"# exact source\n")
    monkeypatch.setattr(adapter, "PACKAGE", package)
    monkeypatch.setattr(klab_client, "__file__", str(installed / "__init__.py"))
    if tamper:
        (installed / "__init__.py").write_bytes(b"# wrong\n")
        with pytest.raises(ScenarioError):
            adapter.installed_wheel(wheel)
    else:
        assert len(adapter.installed_wheel(wheel)) == 64
