"""Preflight errors must not allocate state, credentials or subprocesses."""
import hashlib
import json
from pathlib import Path
import socket
from types import SimpleNamespace

import pytest

import run_reliability_acceptance as runner


def arguments(tmp_path, monkeypatch):
    repo = tmp_path / "server"
    core = repo / "klab.services.reasoner/src/main/resources/knowledge/odo.owl"
    core.parent.mkdir(parents=True)
    core.write_bytes(b"preflight-only source pin; not a scientific ontology")
    monkeypatch.setattr(runner, "CORE", hashlib.sha256(core.read_bytes()).hexdigest())
    monkeypatch.setattr(runner, "os", SimpleNamespace(name="nt"))
    def forbidden(*args, **kwargs):
        raise AssertionError("Preflight failure attempted a subprocess")
    monkeypatch.setattr(runner.subprocess, "Popen", forbidden)
    archives = []
    for i in range(2):
        path = tmp_path / f"archive{i}.kar"
        path.write_bytes(bytes([i]))
        archives.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest = tmp_path / "components.json"
    manifest.write_text(json.dumps(archives))
    return SimpleNamespace(server_repo=repo, components=manifest, state_dir=tmp_path / "fresh", java=Path("unused"), scenario="nonuniform-export")


def test_existing_state_is_not_overwritten(tmp_path, monkeypatch):
    args = arguments(tmp_path, monkeypatch)
    args.state_dir.mkdir()
    sentinel = args.state_dir / "owned-by-someone-else"
    sentinel.write_text("preserved")
    with pytest.raises(ValueError):
        runner.run(args)
    assert sentinel.read_text() == "preserved"


def test_mismatching_core_or_archive_pin_never_allocates_state(tmp_path, monkeypatch):
    args = arguments(tmp_path, monkeypatch)
    monkeypatch.setattr(runner, "CORE", "0" * 64)
    with pytest.raises(ValueError, match="ontology pin"):
        runner.run(args)
    assert not args.state_dir.exists()


def test_archive_pin_never_silently_falls_back(tmp_path, monkeypatch):
    args = arguments(tmp_path, monkeypatch)
    data = json.loads(args.components.read_text())
    data[1]["sha256"] = "0" * 64
    args.components.write_text(json.dumps(data))
    with pytest.raises(ValueError, match="Component pin"):
        runner.run(args)
    assert not args.state_dir.exists()


def test_occupied_port_never_replaces_a_service(tmp_path, monkeypatch):
    args = arguments(tmp_path, monkeypatch)
    with socket.socket() as owned_elsewhere:
        owned_elsewhere.bind(("127.0.0.1", 0))
        owned_elsewhere.listen()
        monkeypatch.setattr(runner, "PORTS", {"reasoner": owned_elsewhere.getsockname()[1]})
        with pytest.raises(OSError):
            runner.run(args)
    assert not args.state_dir.exists()
