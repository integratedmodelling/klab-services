"""Parent-owned acceptance seam; no operating-system service is stopped here."""
from unittest.mock import Mock

import pytest

import run_reliability_acceptance as runner


def test_start_without_owned_stop_is_rejected(tmp_path):
    (tmp_path / "start-runtime.request").touch()
    spawn = Mock()
    with pytest.raises(RuntimeError, match="sequence"):
        runner.runtime_control(tmp_path, {}, spawn, [])
    spawn.assert_not_called()


def test_owned_stop_start_is_once_and_restart_readiness_precedes_ack(tmp_path, monkeypatch):
    old, new, spawn = Mock(), Mock(), Mock()
    spawn.return_value = new
    launches = {"runtime": (old, ["captured-java", "captured.args"], tmp_path, "/runtime/public/capabilities")}
    actions = []
    (tmp_path / "stop-runtime.request").touch()
    runner.runtime_control(tmp_path, launches, spawn, actions)
    old.terminate.assert_called_once(); old.wait.assert_called_once_with(timeout=20)
    assert actions == ["stop"] and (tmp_path / "stop-runtime.done").is_file()
    (tmp_path / "start-runtime.request").touch()
    def ready(*args):
        assert not (tmp_path / "start-runtime.done").exists()
        assert args[2] is new
    monkeypatch.setattr(runner, "ready", ready)
    runner.runtime_control(tmp_path, launches, spawn, actions)
    runner.runtime_control(tmp_path, launches, spawn, actions)
    assert actions == ["stop", "start"] and launches["runtime"][0] is new
    spawn.assert_called_once_with("runtime-restarted", ["captured-java", "captured.args"], tmp_path)
    old.terminate.assert_called_once()


def test_unready_restart_never_acknowledges_success(tmp_path, monkeypatch):
    (tmp_path / "start-runtime.request").touch()
    launches = {"runtime": (Mock(), [], tmp_path, "/ready")}
    monkeypatch.setattr(runner, "ready", Mock(side_effect=RuntimeError("unready")))
    actions = ["stop"]
    with pytest.raises(RuntimeError, match="unready"):
        runner.runtime_control(tmp_path, launches, Mock(), actions)
    assert actions == ["stop"] and not (tmp_path / "start-runtime.done").exists()
