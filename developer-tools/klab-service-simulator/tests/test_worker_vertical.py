"""SIMULATED_PROVISIONAL/JAVA-GAP-0017: real HTTP, separate worker, no Java science."""
from contextlib import contextmanager
import hashlib
import json

import httpx
import pytest

from klab_client import Client
from klab_worker import Field, WorkerClient, WorkerError, execute
from klab_worker.protocol import PROTOCOL
from test_vertical import client_args, control


@contextmanager
def owned_scope(ready):
    with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
        client.initialize_user_scope()
        session = client.create_session()
        context = session.create_context()
        try:
            yield context
        finally:
            context.release(); session.release()


def field():
    return Field((2, 3), ("y", "x"), ((50, -50), (10, 20, 30)), "EPSG:3857", "m",
        (0, -3.5, None, 7.25, 1.5, 8), (True, True, False, True, True, True))


def clients(ready, context, *, worker=False, principal="alice"):
    return WorkerClient(ready["endpoints"]["runtime"], f"sim-token-{'worker-' if worker else ''}{principal}",
        scope=context.id, allow_experimental=True)


def test_scoped_worker_receives_declared_input_executes_isolated_and_publishes_atomic_output(simulator, tmp_path):
    script = tmp_path / "trusted_model.py"
    script.write_text('import json,sys\nx=json.load(sys.stdin)\nx["values"]=[v*2 if ok else None for v,ok in zip(x["values"],x["valid"])]\njson.dump(x,sys.stdout)\n')
    with owned_scope(simulator) as context, clients(simulator, context) as owner, clients(simulator, context, worker=True) as worker:
        assert worker.register("example", operation="double")["state"] == "REGISTERED"
        submitted = owner.submit(field(), operation="double")
        task = worker.claim("example")
        assert task["task_id"] == submitted["task_id"]
        assert Field.from_dict(task["input"]) == field()
        assert owner.status(task["task_id"])["output"] is None
        result, source_hash = execute(script, Field.from_dict(task["input"]))
        assert worker.publish(task, result, implementation_sha256=source_hash)["state"] == "PUBLISHED"
        status = owner.status(task["task_id"])
        assert Field.from_dict(status["output"]).values == (0, -7, None, 14.5, 3, 16)
        assert status["provenance"]["implementation_sha256"] == hashlib.sha256(script.read_bytes()).hexdigest()
        assert status["provenance"]["origin"] == "SYNTHETIC_SIMULATOR"
        with pytest.raises(WorkerError, match="409"):
            worker.publish(task, result, implementation_sha256=source_hash)
        # A fresh connection reattaches explicitly, no task resubmission/replay.
        with clients(simulator, context) as reader:
            assert reader.status(task["task_id"])["state"] == "PUBLISHED"
        assert worker.release("example")["state"] == "RELEASED"
    state = control(simulator)
    assert state["workers"] == state["worker_tasks"] == state["counts"]["contexts"] == 0


def test_worker_denial_version_cancellation_and_failed_publish_leave_no_partial_output(simulator):
    with owned_scope(simulator) as context, clients(simulator, context) as owner, clients(simulator, context, worker=True) as worker:
        with pytest.raises(WorkerError, match="403"): owner.register("example", operation="double")
        with pytest.raises(WorkerError, match="403"): worker.submit(field(), operation="double")
        with clients(simulator, context, worker=True, principal="bob") as foreign:
            with pytest.raises(WorkerError): foreign.register("example", operation="double")
        worker.register("example", operation="double")
        submitted = owner.submit(field(), operation="double")
        task = worker.claim("example")
        url = simulator["endpoints"]["runtime"] + "/experimental/worker/v1/tasks/" + task["task_id"] + "/publish"
        headers = {"Authorization": "sim-token-worker-alice", "klab-scope": context.id, "X-Klab-Worker-Protocol": PROTOCOL}
        bad = field().to_dict(); bad["unit"] = "mm"
        payload = {"lease": task["lease"], "output": bad, "input_sha256": "0" * 64, "implementation_sha256": "0" * 64}
        assert httpx.post(url, headers=headers, json=payload, trust_env=False).status_code == 422
        assert owner.status(task["task_id"])["output"] is None
        assert owner.cancel(submitted["task_id"])["state"] == "CANCELLED"
        with pytest.raises(WorkerError, match="409"): worker.publish(task, field(), implementation_sha256="0" * 64)
        assert owner.status(task["task_id"])["output"] is None
        headers["X-Klab-Worker-Protocol"] = "invented-version"
        assert httpx.post(url, headers=headers, json=payload, trust_env=False).status_code == 409
        worker.release("example")


def test_worker_release_aborts_inflight_work_without_automatic_redelivery(simulator):
    with owned_scope(simulator) as context, clients(simulator, context) as owner, clients(simulator, context, worker=True) as worker:
        worker.register("first", operation="double")
        owner.submit(field(), operation="double")
        task = worker.claim("first")
        worker.release("first")
        worker.register("replacement", operation="double")
        assert worker.claim("replacement")["state"] == "IDLE"
        assert owner.status(task["task_id"])["state"] == "ABORTED"
        assert owner.status(task["task_id"])["output"] is None


def test_failed_worker_acknowledges_failure_without_publishing_or_replaying(simulator, tmp_path):
    script = tmp_path / "fails.py"
    script.write_text('raise RuntimeError("synthetic-private-error-not-forwarded")\n')
    with owned_scope(simulator) as context, clients(simulator, context) as owner, clients(simulator, context, worker=True) as worker:
        worker.register("first", operation="double")
        task = owner.submit(field(), operation="double")
        with pytest.raises(WorkerError, match="process failed"):
            worker.run_once("first", script)
        result = owner.status(task["task_id"])
        assert result["state"] == "FAILED" and result["output"] is None and result["provenance"] is None
        assert worker.run_once("first", script)["state"] == "IDLE"
        worker.release("first")
