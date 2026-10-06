import importlib.util
from pathlib import Path
import subprocess
from types import SimpleNamespace

import pytest

from klab_client.errors import (AuthorizationError, MissingAssetError, AuthenticationError,
    ServerError, TransportError, ProtocolError, WaitTimeout)


def load_tool(name):
    path = Path(__file__).parents[1] / "tools" / (name + ".py")
    spec = importlib.util.spec_from_file_location("reviewed_" + name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize("payload,expected", [("absent", 0), ("different", 1), ("inspection_failed", 1), ("alive_after_kill", 1), ("stopped", 0)])
def test_cleanup_distinguishes_dead_unverifiable_and_confirmed(tmp_path, monkeypatch, payload, expected):
    tool = load_tool("local_stack")
    (tmp_path / "runtime.pid").write_text("12345")
    inspections = [0]
    kills = []
    def inspect(pid):
        inspections[0] += 1
        if payload == "inspection_failed":
            raise RuntimeError("CIM failed")
        if payload == "absent" or payload == "stopped" and inspections[0] > 1:
            return {"exists": False}
        return {"exists": True, "command_line": "different" if payload == "different" else str(tmp_path / "runtime.args")}
    monkeypatch.setattr(tool, "inspect_process", inspect)
    monkeypatch.setattr(tool.subprocess, "run", lambda *args, **kw: kills.append(args) or SimpleNamespace(returncode=0))
    assert tool.stop(tmp_path, ["runtime"]) == expected
    if payload in ("absent", "different", "inspection_failed"):
        assert not kills


@pytest.mark.parametrize("interrupted", [False, True])
def test_timed_out_or_interrupted_step_terminates_owned_tree(monkeypatch, interrupted):
    tool = load_tool("run_local_acceptance")
    killed = []
    calls = [0]
    class Child:
        pid = 123
        def wait(self, timeout):
            calls[0] += 1
            if calls[0] == 1:
                if interrupted:
                    raise KeyboardInterrupt()
                raise subprocess.TimeoutExpired("owned step", timeout)
            return 0
    monkeypatch.setattr(tool.subprocess, "Popen", lambda *a, **kw: Child())
    monkeypatch.setattr(tool.subprocess, "run", lambda command, **kw: killed.append(command))
    with pytest.raises(KeyboardInterrupt if interrupted else subprocess.TimeoutExpired):
        tool.run_command(["fake"], cwd=".", env={}, log=None, timeout=.01)
    assert killed == [["taskkill.exe", "/PID", "123", "/T", "/F"]]


def test_partial_startup_failure_still_requests_owned_cleanup(tmp_path, monkeypatch):
    import httpx
    import json
    import os
    import sys
    tool = load_tool("run_local_acceptance")
    java = tmp_path / "java.exe"
    java.write_text("not executed")
    state = tmp_path / "new-state"
    calls = []
    class Hub:
        terminated = False
        def poll(self): return None
        def terminate(self): self.terminated = True
        def wait(self, timeout): return 0
    hub = Hub()
    class HTTP:
        def __init__(self, **kwargs): pass
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def get(self, url): return httpx.Response(200)
    monkeypatch.setattr(tool, "os", SimpleNamespace(name="nt", environ=dict(os.environ), path=os.pathsep))
    monkeypatch.setattr(tool.os, "pathsep", os.pathsep, raising=False)
    monkeypatch.setattr(tool, "run_command", lambda command, **kwargs: 1 if "start" in command else 0)
    monkeypatch.setattr(tool.subprocess, "Popen", lambda *args, **kwargs: hub)
    monkeypatch.setattr(tool.subprocess, "run", lambda command, **kwargs: calls.append(command) or SimpleNamespace(returncode=0))
    monkeypatch.setattr(httpx, "Client", HTTP)
    monkeypatch.setattr(sys, "argv", ["run_local_acceptance.py", "--java", str(java), "--worldview", str(tmp_path), "--state-dir", str(state)])
    assert tool.main() == 1
    assert any("stop" in command for command in calls)
    assert hub.terminated
    report = json.loads((state / "run-report.json").read_text())
    assert not report["passed"] and report["cleanup_exit_code"] == 0


def security_tool(monkeypatch):
    monkeypatch.syspath_prepend(str(Path(__file__).parents[1] / "tools"))
    # Optional fixture dependency is imported only by explicit local-tool tests.
    pytest.importorskip("cryptography")
    return load_tool("verify_fixture")


@pytest.mark.parametrize("error", [ServerError("HTTP 500 database failure"), TransportError("offline"),
    ProtocolError("bad response"), AuthenticationError("expired token")])
def test_security_failure_is_not_access_denial(monkeypatch, error):
    tool = security_tool(monkeypatch)
    def fail():
        raise error
    with pytest.raises(type(error)):
        tool.require_access_denied(fail)


def test_explicit_acl_denial_and_unexpected_success(monkeypatch):
    tool = security_tool(monkeypatch)
    for error in (AuthorizationError("HTTP 403"), MissingAssetError("HTTP 404")):
        def fail():
            raise error
        assert tool.require_access_denied(fail) == type(error).__name__
    with pytest.raises(AssertionError, match="succeeded"):
        tool.require_access_denied(lambda: object())
