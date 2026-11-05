"""Fresh-process reads of retained owned TEST work, never scientific replay."""
from decimal import Decimal
import json
import os
from pathlib import Path
import subprocess
import sys

from klab_client import Client, Endpoint, load_handle
from .scientific_checks import require


def expected_values():
    # Independent north-first YX oracle, not copied from a Java result/handle.
    return tuple(None if value is None else Decimal(value) for value in (
        "0", "2.25", "-100", "3", "5", "-2.5", None, "4", "-0.125", "9",
        "10.125", "-9", "12.5", "22", "-8", "16777217.125", "11", None, "31", "41"))


def client(config):
    return Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
        **{name: Endpoint(config["urls"][name], config["token"])
           for name in ("reasoner", "resources", "resolver")})


def allowed_request(call, context_id):
    """The existing attach route is POST, but must name the original root."""
    path = call["path"]
    if call["method"] == "GET":
        return (path.endswith("/public/capabilities") or
            any(part in path for part in ("/jobs/status/", "/jobs/retrieve/")))
    if call["method"] == "POST":
        if path.endswith("/api/v1/connect"):
            return call.get("context_id") == context_id
        return any(path.endswith(route) for route in ("/api/v1/observation/value", "/api/v1/query"))
    return False


def recover(state, directory):
    """Read original server work using new local objects and separate credentials."""
    config = json.loads((state / "scientist.json").read_text())
    context_reference = load_handle(directory / "context.json")
    calls = []
    with client(config) as api:
        api.initialize_user_scope(local_federation=True)
        # Setup is explicit. Recovery may attach to the saved root and read it,
        # never create, submit, cancel, release or replay an ambiguous mutation.
        def capture(request):
            call = {"method": request.method, "path": request.url.path}
            if call["path"].endswith("/api/v1/connect"):
                call["context_id"] = json.loads(request.content).get("id")
            calls.append(call)
            require(allowed_request(call, context_reference.context_id),
                "Recovery attempted an unexpected operation or a different context")
        api.transport._http.event_hooks["request"].append(capture)
        context = api.restore_handle(context_reference)
        observation = api.restore_handle(load_handle(directory / "observation.json"))
        job = api.restore_handle(load_handle(directory / "job.json"))
        require(not context.owned and not observation._context.owned,
            "Recovery acquired disposal ownership")
        require(observation._context.get_context_id() == context.get_context_id(),
            "Recovery changed observation focus")
        recovered = job.result(120)
        require(recovered.id == observation.id, "Retained Job returned a different observation")
        require(observation.fetch_data(range(20), curve="D2_YX").values == expected_values(),
            "Fresh-process cells differ from the independent oracle")
        require(recovered.fetch_data(range(20), curve="D2_YX").values == expected_values(),
            "Retained Job data differs from independent observation data")
        report = {"passed": True, "pid": os.getpid(), "job_id": job.id,
            "observation_id": observation.id, "scope": context.get_context_id(),
            "verified_cells": 20, "owned": context.owned}
    # Client.close must remain local too; audit after the context manager exits.
    require(all(allowed_request(call, context_reference.context_id) for call in calls),
        "Recovery or local close performed an unexpected operation")
    report.update(calls=calls, local_close_only=True, submissions=0)
    (directory / "recovered.json").write_text(json.dumps(report, indent=2))
    return report


def run_child(state, directory):
    script = Path(__file__).parents[1] / "recover_completed_work.py"
    env = {name: value for name, value in os.environ.items() if not name.startswith("KLAB_")}
    command = [sys.executable, str(script), "--state-dir", str(state), "--handles-dir", str(directory)]
    process = subprocess.run(command, env=env, capture_output=True, timeout=180,
        **({"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}))
    # Private evidence only; never include credentials/traceback contents in a
    # caller-facing exception. The isolated runner owns the state directory ACL.
    (directory / "child.stdout").write_bytes(process.stdout)
    (directory / "child.stderr").write_bytes(process.stderr)
    require(process.returncode == 0, "Fresh-process recovery failed; inspect private child evidence")
    report = json.loads((directory / "recovered.json").read_text())
    require(report["passed"] and report["pid"] != os.getpid(), "Recovery was not a separate process")
    return report
