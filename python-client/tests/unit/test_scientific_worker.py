"""L1 controlled admission tests, never worker provisioning or L4 acceptance."""
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

import run_scientific_worker as worker
from scientific_scenario import ScenarioError, sha


def args(tmp_path, execute=False):
    return SimpleNamespace(resources=tmp_path / "resources.json", worker="owned-test-worker",
        output=tmp_path / "fresh", execute=execute)


def test_missing_operator_manifest_is_blocked_not_green(tmp_path):
    value = args(tmp_path)
    assert worker.execute(value) == 2
    receipt = json.loads((value.output / "worker-receipt.json").read_text())
    assert receipt["delivery_state"] == "BLOCKED_EXTERNAL" and receipt["scheduled_L4"] == "NOT_RUN"
    assert receipt["scenarios"] == [] and receipt["cleanup"] == "NOT_RUN"


@pytest.mark.parametrize("failure", ["worker", "source", "tamper", "missing"])
def test_invalid_authority_source_or_artifact_never_executes(tmp_path, monkeypatch, failure):
    monkeypatch.setattr(worker, "WINDOWS", True)
    file = tmp_path / "artifact"
    file.write_bytes(b"original")
    manifest = {"schema_version": 1, "authorization": {"worker": "owned", "isolated": True, "reference": "test consent"},
        "source_sha": "a" * 40, "python_version": worker.platform.python_version(), "files": {str(file): sha(file)}}
    if failure == "worker":
        manifest["authorization"]["worker"] = "foreign"
    if failure == "source":
        manifest["source_sha"] = "b" * 40
    if failure == "tamper":
        file.write_bytes(b"changed")
    if failure == "missing":
        file.unlink()
    path = tmp_path / "manifest.json"
    path.write_text(json.dumps(manifest))
    monkeypatch.setattr(worker, "subprocess", SimpleNamespace(check_output=lambda cmd, **kw: "a" * 40 if 'rev-parse' in cmd else ""))
    with pytest.raises(worker.PrerequisiteUnavailable if failure == "missing" else ScenarioError):
        worker.preflight(path, worker="owned", repo=tmp_path)


@pytest.mark.parametrize("fail", [False, True])
def test_serial_existing_adapters_and_independent_cleanup_no_replay(tmp_path, monkeypatch, fail):
    value = args(tmp_path, execute=True)
    value.resources.write_text("{}")
    monkeypatch.setattr(worker, "preflight", lambda *a, **kw: {"source_sha": "a" * 40,
        "authorization": {"reference": "unit-test"}, "java": "java", "wheel": "wheel", "components": "components"})
    monkeypatch.setattr(worker, "restrict_output", lambda _: None)
    calls, cleaned = [], []
    def run(command, **kw):
        calls.append(command)
        state = Path(command[-1]);state.mkdir()
        (state / "scientific-execution.json").write_text(json.dumps({"status": "FAIL" if fail else "PASS",
            "run_id": state.name, "cleanup": {"status": "PASS"}}))
        return SimpleNamespace(returncode=int(fail))
    monkeypatch.setattr(worker, "subprocess", SimpleNamespace(run=run, CREATE_NO_WINDOW=0,
        SubprocessError=worker.subprocess.SubprocessError))
    monkeypatch.setattr(worker, "independent_cleanup", lambda state: cleaned.append(state.name))
    assert worker.execute(value) == int(fail)
    assert len(calls) == (1 if fail else 2) and len(cleaned) == (0 if fail else 2)
    assert not (tmp_path / ".scientific-worker.lock").exists()
    receipt = json.loads((value.output / "worker-receipt.json").read_text())
    assert receipt["scheduled_L4"] == "NOT_RUN"
    assert receipt["delivery_state"] == ("FAILED" if fail else "LOCALLY_ACCEPTED_BOUNDED")


def test_foreign_lock_is_preserved(tmp_path, monkeypatch):
    value = args(tmp_path, execute=True);value.resources.write_text("{}")
    lock = tmp_path / ".scientific-worker.lock";lock.write_text("foreign-owner")
    monkeypatch.setattr(worker, "preflight", lambda *a, **kw: {"source_sha": "a" * 40, "authorization": {"reference": "test"}})
    monkeypatch.setattr(worker, "restrict_output", lambda _: None)
    assert worker.execute(value) == 2 and lock.read_text() == "foreign-owner"
