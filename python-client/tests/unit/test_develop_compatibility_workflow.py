"""Offline safety and oracle regressions for the owned Java/Python functional suite."""
import json
from pathlib import Path
import sys
from types import SimpleNamespace

import pytest

from workflows import develop_compatibility as workflow


def test_readiness_can_borrow_real_connection_without_test_credentials_or_closing_it(tmp_path, monkeypatch):
    accepted = {"acceptedGeneration": 1, "logicalStatus": "VALID"}
    client = SimpleNamespace(transport=SimpleNamespace(request=lambda *args: {
        "metadata": {"delegate": {"semantic.candidate": accepted}}}, redact=lambda value: value))
    monkeypatch.setattr(workflow, "make_client", lambda *args: pytest.fail("Do not read or create TEST credentials"))
    assert workflow.require_semantic_readiness(tmp_path, client=client) == accepted
    assert not (tmp_path / "scientist.json").exists()
    assert json.loads((tmp_path / "develop-readiness.json").read_text())["metadata"]["delegate"]["semantic.candidate"] == accepted


@pytest.mark.parametrize("logical", ["UNRESOLVED", "FAILED"])
def test_borrowed_connection_does_not_weaken_original_missing_import_or_domain_gate(tmp_path, logical):
    client = SimpleNamespace(transport=SimpleNamespace(request=lambda *args: {
        "operational": True, "metadata": {"delegate": {"semantic.candidate": {
            "acceptedGeneration": 0, "logicalStatus": logical, "diagnostics": ["original blocker"]}}}},
        redact=lambda value: value))
    with pytest.raises(AssertionError, match="original blocker"):
        workflow.require_semantic_readiness(tmp_path, client=client)


@pytest.mark.parametrize("primary_kind", ["scientific", "cancelled", "none"])
def test_roundtrip_preserves_primary_failure_when_release_also_fails(tmp_path, monkeypatch, primary_kind):
    (tmp_path / "scientist.json").write_text("{}")
    monkeypatch.setattr(workflow, "initialize", lambda config: None)
    class Client:
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def create_session(self, **kwargs): return SimpleNamespace(id="owned")
    monkeypatch.setattr(workflow, "make_client", lambda config: Client())
    primary = KeyboardInterrupt() if primary_kind == "cancelled" else AssertionError("original scientific failure")
    def sample(*args):
        if primary_kind != "none":
            raise primary
        return {"outcome": "verified", "verified_cells": 40}
    monkeypatch.setattr(workflow, "sample", sample)
    def release():
        raise RuntimeError("secret cleanup detail must not be copied")
    monkeypatch.setattr(workflow, "Session", lambda *args: SimpleNamespace(release=release))
    expected = RuntimeError if primary_kind == "none" else type(primary)
    with pytest.raises(expected) as captured:
        workflow.scientific_roundtrip(tmp_path, "before")
    if primary_kind != "none":
        assert captured.value is primary
        assert primary.__notes__ == ["Compatibility session cleanup also failed: RuntimeError"]


def test_process_must_match_the_explicit_state_before_it_can_be_restarted(tmp_path, monkeypatch):
    (tmp_path / "runtime.pid").write_text("123")
    fake = SimpleNamespace(pid=123, exe=lambda: "java.exe", cmdline=lambda: ["java.exe", "@other-state/runtime.args"])
    monkeypatch.setitem(sys.modules, "psutil", SimpleNamespace(Process=lambda pid: fake))
    with pytest.raises(AssertionError, match="not owned"):
        workflow.owned_process(tmp_path, "runtime")
    fake.cmdline = lambda: ["java.exe", "@" + str(tmp_path.resolve() / "runtime.args")]
    assert workflow.owned_process(tmp_path, "runtime") == (123, Path("java.exe"))


def test_missing_owned_authority_fails_before_process_lookup(tmp_path, monkeypatch):
    monkeypatch.setattr(workflow, "owned_process", lambda *args: pytest.fail("Must not inspect or stop processes"))
    with pytest.raises(AssertionError, match="owned TEST authority"):
        workflow.verify(tmp_path)


def test_restart_keeps_the_explicit_server_checkout_and_rejects_mismatched_classpaths(tmp_path):
    server = tmp_path / "selected-server"
    classes = server / "klab.services.runtime/target/test-classes"
    classes.mkdir(parents=True)
    (tmp_path / "runtime-fixture.json").write_text(json.dumps({"classes": str(classes)}))
    (tmp_path / "runtime.args").write_text("-cp\nother-server/target/classes")
    with pytest.raises(AssertionError, match="classpath differs"):
        workflow.server_checkout(tmp_path)
    (tmp_path / "runtime.args").write_text((server / "klab.services.runtime/target/classes").as_posix())
    assert workflow.server_checkout(tmp_path) == server.resolve()


@pytest.mark.parametrize("outcome,cells", [("failed", 40), ("verified", 0), ("verified", 20)])
def test_roundtrip_requires_the_full_value_and_unit_oracle_and_cleans_session(tmp_path, monkeypatch, outcome, cells):
    (tmp_path / "scientist.json").write_text(json.dumps({}))
    released = []

    class Client:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

        def create_session(self, **kwargs):
            return SimpleNamespace(id="owned-session")

    monkeypatch.setattr(workflow, "initialize", lambda config: None)
    monkeypatch.setattr(workflow, "make_client", lambda config: Client())
    monkeypatch.setattr(workflow, "sample", lambda *args: {"outcome": outcome, "verified_cells": cells})
    monkeypatch.setattr(workflow, "Session", lambda client, id: SimpleNamespace(release=lambda: released.append(id) or True))
    with pytest.raises(AssertionError):
        workflow.scientific_roundtrip(tmp_path, "before")
    assert released == ["owned-session"]


@pytest.mark.parametrize("stop_code", [0, 1])
def test_restart_is_runtime_only_bounded_and_failure_is_not_a_pass(tmp_path, monkeypatch, stop_code):
    import cold_context_check
    import run_local_acceptance
    (tmp_path / "fixture-authority.json").write_text("{}")
    java = tmp_path / "java.exe"
    java.touch()
    restarted = []
    commands = []
    monkeypatch.setattr(workflow, "owned_process", lambda state, name:
        (102 if name == "runtime" and restarted else 101, java))
    monkeypatch.setattr(workflow, "scientific_roundtrip", lambda state, label: {"verified_cells": 40})
    monkeypatch.setattr(workflow, "require_semantic_readiness", lambda state: {"acceptedGeneration": 1})
    monkeypatch.setattr(workflow, "server_checkout", lambda state: tmp_path / "explicit-server")
    monkeypatch.setattr(cold_context_check, "prepare", lambda state: {"prepared": True})
    monkeypatch.setattr(cold_context_check, "check", lambda state: {"passed": True})

    def run(command, **kwargs):
        commands.append(command)
        assert kwargs["timeout"] == 180
        assert command[command.index("--only") + 1] == "runtime"
        assert command[command.index("--repo") + 1] == str(tmp_path / "explicit-server")
        if command[2] == "start":
            restarted.append(True)
        return stop_code if command[2] == "stop" else 0

    monkeypatch.setattr(run_local_acceptance, "run_command", run)
    if stop_code:
        with pytest.raises(AssertionError, match="stop failed"):
            workflow.verify(tmp_path)
        assert len(commands) == 1
        assert not (tmp_path / "develop-compatibility.json").exists()
    else:
        report = workflow.verify(tmp_path)
        assert report["passed"]
        assert report["runtime_pid_before"] == 101
        assert report["runtime_pid_after"] == 102
        assert [command[2] for command in commands] == ["stop", "start"]


@pytest.mark.parametrize("wrapped", [False, True])
@pytest.mark.parametrize("logical", ["NOT_RUN", "INVALID", "UNRESOLVED", "FAILED", "CANCELLED", "VALID"])
def test_http_operational_without_accepted_semantics_is_not_scientific_readiness(tmp_path, monkeypatch, logical, wrapped):
    (tmp_path / "scientist.json").write_text("{}")
    metadata = {"semantic.candidate": {"acceptedGeneration": 0, "logicalStatus": logical}}
    if wrapped:
        metadata = {"@CLASS": "org.integratedmodelling.klab.api.collections.impl.MetadataImpl", "delegate": metadata}

    class Client:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

        transport = SimpleNamespace(request=lambda *args: {"operational": True, "metadata": metadata}, redact=lambda value: value)

    monkeypatch.setattr(workflow, "make_client", lambda config: Client())
    with pytest.raises(AssertionError, match="accepted semantic"):
        workflow.require_semantic_readiness(tmp_path, timeout=0)


def test_readiness_waits_for_publication_instead_of_treating_invalidation_as_permanent(tmp_path, monkeypatch):
    (tmp_path / "scientist.json").write_text("{}")
    pending = {"acceptedGeneration": 0, "logicalStatus": "CANCELLED"}
    accepted = {"acceptedGeneration": 1, "logicalStatus": "VALID"}
    responses = iter([pending, accepted])

    class Client:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

        transport = SimpleNamespace(request=lambda *args: {"metadata": {"delegate": {
            "semantic.candidate": next(responses)}}}, redact=lambda value: value)

    monkeypatch.setattr(workflow, "make_client", lambda config: Client())
    monkeypatch.setattr(workflow.time, "sleep", lambda seconds: None)
    assert workflow.require_semantic_readiness(tmp_path) == accepted
