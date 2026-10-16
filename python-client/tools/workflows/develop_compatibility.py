"""Python/Java acceptance and a Runtime-only restart on an explicitly owned TEST stack."""
import json
from pathlib import Path
import sys
import time

from klab_client import Session
from .compute_and_analyze import initialize, make_client, sample
from .scientific_checks import require


def owned_process(state, name):
    """Never stop a shared process merely because its PID appears in a state file."""
    import psutil
    state = Path(state).resolve()
    process = psutil.Process(int((state / (name + ".pid")).read_text()))
    require("@" + str(state / (name + ".args")) in process.cmdline(),
            "Process is not owned by the supplied TEST state")
    return process.pid, Path(process.exe())


def server_checkout(state):
    """A restart must use the selected compiled server, not whichever checkout owns Python."""
    fixture = json.loads((state / "runtime-fixture.json").read_text())
    classes = Path(fixture["classes"]).resolve()
    require(classes.parts[-3:] == ("klab.services.runtime", "target", "test-classes"),
            "Runtime fixture must identify an explicit compiled server checkout")
    server = classes.parents[2]
    expected = (server / "klab.services.runtime/target/classes").as_posix()
    require(expected in (state / "runtime.args").read_text(),
            "Running Runtime classpath differs from the selected server checkout")
    return server


def scientific_roundtrip(state, label):
    config = json.loads((state / "scientist.json").read_text())
    initialize(config)
    with make_client(config) as client:
        session = client.create_session(name="develop-compatibility-" + label)
    primary = None
    try:
        report = sample(config, session.id, 910000 if label == "before" else 910001)
        require(report["outcome"] == "verified", "Python/Java scientific roundtrip failed: " + report.get("error", "no diagnostic supplied"))
        require(report["verified_cells"] == 40, "Value and unit-conversion oracle was not exercised")
        return report
    except BaseException as error:
        primary = error
        raise
    finally:
        try:
            with make_client(config) as client:
                require(Session(client, session.id).release(), "Compatibility session cleanup failed")
        except BaseException as cleanup:
            if primary is None:
                raise
            # Keep the original operation failure (including cancellation) as the
            # reported exception, while making cleanup failure visible and redacted.
            primary.add_note("Compatibility session cleanup also failed: " +
                             type(cleanup).__name__)


def require_semantic_readiness(state, timeout=60):
    """HTTP 200/local-authoring operational status is not accepted scientific knowledge."""
    config = json.loads((state / "scientist.json").read_text())
    deadline = time.monotonic() + timeout
    with make_client(config) as client:
        while True:
            status = client.transport.request("reasoner", "GET", "/public/status")
            (state / "develop-readiness.json").write_text(client.transport.redact(json.dumps(status, indent=2)))
            metadata = status.get("metadata", {})
            metadata = metadata.get("delegate", metadata)
            candidate = metadata.get("semantic.candidate")
            if isinstance(candidate, dict):
                if candidate.get("acceptedGeneration", 0) > 0:
                    return candidate
                detail = client.transport.redact(json.dumps(candidate))
                require(candidate.get("logicalStatus") not in {"NOT_RUN", "INVALID", "UNRESOLVED", "FAILED"},
                        "Reasoner has no accepted semantic generation: " + detail)
            elif status.get("operational") is True:
                return {"legacy_operational": True}
            require(time.monotonic() < deadline,
                    "Reasoner did not publish accepted semantic knowledge within the readiness deadline: "
                    + client.transport.redact(json.dumps(candidate)))
            time.sleep(.2)


def verify(state):
    from cold_context_check import prepare, check
    state = Path(state).resolve()
    require((state / "fixture-authority.json").is_file(), "Requires an explicitly owned TEST authority")
    # Check every process before any mutation. Restart only Runtime; retain graph/services.
    processes = {name: owned_process(state, name) for name in
                 ("graphdb", "resources", "reasoner", "resolver", "runtime")}
    runtime_pid, java = processes["runtime"]
    require(java.is_file(), "Running Runtime Java executable is unavailable")
    server = server_checkout(state)
    readiness = require_semantic_readiness(state)
    before = scientific_roundtrip(state, "before")
    prepared = prepare(state)
    require(prepared["prepared"], "Persistent ACL fixture preparation failed")
    launcher = Path(__file__).resolve().parents[1] / "local_stack.py"
    from run_local_acceptance import run_command
    for action in ("stop", "start"):
        command = [sys.executable, str(launcher), action, "--only", "runtime", "--repo", str(server), "--state-dir", str(state)]
        if action == "start":
            command.extend(["--java", str(java)])
        code = run_command(command, cwd=launcher.parent.parent, env=None, log=None, timeout=180)
        require(code == 0, "Owned Runtime " + action + " failed")
    restarted_pid, _ = owned_process(state, "runtime")
    require(restarted_pid != runtime_pid, "Runtime was not actually restarted")
    for name, (pid, _) in processes.items():
        if name != "runtime":
            require(owned_process(state, name)[0] == pid, "Restart replaced another service")
    restored = check(state)
    require(restored["passed"], "Cold context permission checks failed")
    after = scientific_roundtrip(state, "after")
    report = {"passed": True, "readiness": readiness, "before": before, "after": after, "restart": restored,
              "server_repo": str(server),
              "runtime_pid_before": runtime_pid, "runtime_pid_after": restarted_pid,
              "scope": "Real local Java services and Python client; controlled TEST credentials/models; not a native Java client or production Hub test"}
    (state / "develop-compatibility.json").write_text(json.dumps(report, indent=2))
    return report
