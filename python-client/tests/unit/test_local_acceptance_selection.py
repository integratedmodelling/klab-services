"""No-process regressions for explicit server selection and readiness-only isolation."""
import json
import os
import sys
from types import SimpleNamespace

import pytest
import httpx

from test_local_tools import load_tool


@pytest.mark.parametrize("readiness_only", [False, True])
@pytest.mark.parametrize("cleanup_failure", [False, True])
def test_selected_server_used_for_build_prepare_start_and_cleanup(tmp_path, monkeypatch, readiness_only, cleanup_failure):
    tool = load_tool("run_local_acceptance")
    server = tmp_path / "selected-server"
    server.mkdir()
    for name in ("pom.xml", "mvnw.cmd"):
        (server / name).touch()
    state = tmp_path / "state"
    java = tmp_path / "java.exe"
    java.touch()
    monkeypatch.setattr(tool, "os", SimpleNamespace(name="nt", environ=os.environ, pathsep=os.pathsep))
    monkeypatch.setattr(tool, "checkout_identity", lambda root: {"directory": str(root), "commit": "selected-sha", "dirty": False})
    commands = []
    def run(command, **kwargs):
        commands.append((command, kwargs.get("cwd")))
        return SimpleNamespace(returncode=1 if cleanup_failure and "stop" in command else 0)
    monkeypatch.setattr(tool, "run_command", lambda command, **kwargs: run(command, **kwargs).returncode)
    monkeypatch.setattr(tool.subprocess, "run", run)
    class Hub:
        pid = 123
        def poll(self): return None
        def terminate(self): pass
        def wait(self, **kwargs): return 0
    monkeypatch.setattr(tool.subprocess, "Popen", lambda *args, **kwargs: Hub())
    original_http = httpx.Client
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: original_http(
        transport=httpx.MockTransport(lambda request: httpx.Response(200)), **kwargs))
    argv = ["runner", "--java", str(java), "--worldview", str(tmp_path), "--server-repo", str(server),
            "--state-dir", str(state), "--build"]
    if readiness_only:
        argv.append("--readiness-only")
    monkeypatch.setattr(sys, "argv", argv)
    assert tool.main() == int(cleanup_failure)
    maven = [(cmd, cwd) for cmd, cwd in commands if str(server / "mvnw.cmd") in cmd]
    assert len(maven) == 3 and all(cwd == server for _, cwd in maven)
    prepare = next(cmd for cmd, _ in commands if "tools/fixture_hub.py" in cmd)
    assert prepare[prepare.index("--server-repo") + 1] == str(server)
    stack = [cmd for cmd, _ in commands if "tools/local_stack.py" in cmd]
    assert stack and all(cmd[cmd.index("--repo") + 1] == str(server) for cmd in stack)
    report = json.loads((state / "run-report.json").read_text())
    assert report["checkouts"]["server"]["directory"] == str(server)
    assert report["passed"] is not cleanup_failure
    assert report["scientific_acceptance"] == (not readiness_only and not cleanup_failure)
    if readiness_only:
        assert "--readiness-only" in prepare
        assert {cmd[cmd.index("--only") + 1] for cmd in stack} == {"graphdb", "resources", "reasoner"}
        assert not any("tools/throughput_probe.py" in cmd for cmd, _ in commands)
        assert any("semantic-readiness" in cmd for cmd, _ in commands)


def test_checkout_identity_records_dirty_merge_without_changing_git(tmp_path, monkeypatch):
    tool = load_tool("run_local_acceptance")
    commands = []
    def git(command, **kwargs):
        commands.append(command)
        output = " M tracked.py" if "status" in command else "merge-sha" if "MERGE_HEAD" in command else "head-sha"
        return SimpleNamespace(stdout=output, returncode=0)
    monkeypatch.setattr(tool.subprocess, "run", git)
    assert tool.checkout_identity(tmp_path) == {"directory": str(tmp_path), "commit": "head-sha", "dirty": True, "merge_parent": "merge-sha"}
    assert all(command[1] in {"status", "rev-parse"} for command in commands)


def test_readiness_fixture_needs_no_runtime_controls_but_full_fixture_still_does(tmp_path, monkeypatch):
    tool = load_tool("fixture_hub")
    worldview = tmp_path / "imod"
    (worldview / "META-INF").mkdir(parents=True)
    (worldview / "META-INF/manifest.json").write_text("{}")
    monkeypatch.setattr(tool.subprocess, "run", lambda *args, **kwargs:
                        SimpleNamespace(stdout="608bef150ced0a109db98a5aad64ba4461beaa54"))
    server = tmp_path / "no-runtime-controls"
    with pytest.raises(RuntimeError, match="Runtime test fixtures"):
        tool.prepare(tmp_path / "full", worldview, server)
    state = tmp_path / "readiness"
    tool.prepare(state, worldview, server, readiness_only=True)
    assert (state / "scientist.json").is_file()
    assert not (state / "runtime-fixture.json").exists()
    assert not (state / "controls").exists()
