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


def recover(state, directory):
    """Read original server work using new local objects and separate credentials."""
    config = json.loads((state / "scientist.json").read_text())
    calls = []
    with client(config) as api:
        api.initialize_user_scope(local_federation=True)
        # Setup is explicit. From this point recovery must be read-only, including
        # on failure: no submit, creation, remote release or credential replay.
        api.transport._http.event_hooks["request"].append(lambda request: calls.append(
            {"method": request.method, "path": request.url.path}))
        context = api.restore_handle(load_handle(directory / "context.json"))
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
    require(all((call["method"] == "GET" or
        call["method"] == "POST" and any(call["path"].endswith(path)
            for path in ("/api/v1/observation/value", "/api/v1/query")))
        and "release" not in call["path"].lower()
        for call in calls), "Recovery or local close performed a remote mutation")
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
