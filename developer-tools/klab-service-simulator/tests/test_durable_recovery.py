"""SIMULATED_PROVISIONAL: owned process crash/restart, never native Java durability."""
import json
import sqlite3
from urllib.parse import urlsplit

import pytest

from klab_client import Client, ObservationRequest, load_handle, save_handle
from klab_worker import Field, WorkerError
from klab_simulator.persistence import APPLICATION_ID, Store, snapshot, restore
from klab_simulator.server import make_server
from klab_simulator.state import State
from conftest import launch
from test_vertical import client_args, control, geometry
from test_worker_vertical import clients, field


def test_owned_process_restart_restores_sdk_results_and_fences_uncertain_worker_output(tmp_path):
    path = tmp_path / "owned.klab-sim.sqlite"
    script = tmp_path / "double.py"
    script.write_text('import json,sys\nx=json.load(sys.stdin)\nx["values"]=[2*v if ok else None for v,ok in zip(x["values"],x["valid"])]\njson.dump(x,sys.stdout)\n')
    with launch(state_file=path) as ready:
        port = urlsplit(ready["endpoints"]["runtime"]).port
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as scientist:
            session = scientist.create_session()
            context = session.create_context()
            job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            observation = job.result(timeout=2)
            save_handle(context, tmp_path / "context.json")
            save_handle(job, tmp_path / "job.json")
            save_handle(observation, tmp_path / "observation.json")
            unfinished_job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            save_handle(unfinished_job, tmp_path / "unfinished-job.json")
            with clients(ready, context) as owner, clients(ready, context, worker=True) as worker:
                worker.register("original", operation="double")
                pending = owner.submit(field(), operation="double")
                old_lease = worker.claim("original")
                finished = owner.submit(field(), operation="double")
                worker.run_once("original", script)
                cancelled = owner.submit(field(), operation="double")
                owner.cancel(cancelled["task_id"])
    # launch terminated only the owned simulator process; no remote mutation replay.
    with sqlite3.connect(path) as database:
        assert database.execute("PRAGMA application_id").fetchone()[0] == APPLICATION_ID
        payload = database.execute("SELECT payload FROM checkpoint WHERE id=1").fetchone()[0]
        assert b"sim-token-" not in payload and old_lease["lease"].encode() not in payload
    with launch(port=port, state_file=path) as restarted:
        assert restarted["service_ids"] == ready["service_ids"]
        with Client(restarted["endpoints"]["runtime"], **client_args(restarted)) as scientist:
            restored_context = scientist.restore_handle(load_handle(tmp_path / "context.json"))
            restored_job = scientist.restore_handle(load_handle(tmp_path / "job.json"))
            restored_observation = scientist.restore_handle(load_handle(tmp_path / "observation.json"))
            assert restored_job.result(timeout=2).id == restored_observation.id
            assert restored_observation.fetch_data((0, 19, 380, 399)).values == (100, 157, 138, 195)
            unfinished = scientist.restore_handle(load_handle(tmp_path / "unfinished-job.json"))
            assert unfinished.id == unfinished_job.id
            assert unfinished.result(timeout=2).fetch_data((0, 399)).values == (100, 195)
            with clients(restarted, restored_context) as owner, clients(restarted, restored_context, worker=True) as worker:
                assert owner.status(pending["task_id"])["state"] == "RECOVERY_REQUIRED"
                assert owner.status(pending["task_id"])["output"] is None
                assert owner.status(cancelled["task_id"])["state"] == "CANCELLED"
                assert Field.from_dict(owner.status(finished["task_id"])["output"]).values == (0, -7, None, 14.5, 3, 16)
                worker.register("replacement", operation="double")
                with clients(restarted, restored_context, principal="bob") as forbidden:
                    with pytest.raises(WorkerError, match="403"):
                        forbidden.status(finished["task_id"])
                assert worker.claim("replacement")["state"] == "IDLE"
                with pytest.raises(WorkerError, match="409"):
                    worker.publish(old_lease, field(), implementation_sha256="0" * 64)
                with pytest.raises(WorkerError, match="explicit"):
                    owner.recover_task(pending["task_id"])
                owner.recover_task(pending["task_id"], allow_recompute=True)
                assert worker.run_once("replacement", script)["state"] == "PUBLISHED"
                with pytest.raises(WorkerError, match="409"):
                    owner.recover_task(pending["task_id"], allow_recompute=True)
                worker.release("replacement")
            assert not any(row["path"].endswith(("/submit", "/createSession", "/createContext")) for row in control(restarted)["trace"])
            restored_context.release()
            scientist.runtime.release_session(session.id)
        assert control(restarted)["workers"] == control(restarted)["worker_tasks"] == 0
        assert not any(control(restarted)["counts"].values())
    # Explicit cleanup is durable too; a third process cannot resurrect released work.
    with launch(port=port, state_file=path) as empty:
        assert not any(control(empty)["counts"].values())
        assert control(empty)["worker_tasks"] == 0


def test_state_is_exclusively_owned_and_wrong_tuple_corruption_or_live_custody_never_reset(tmp_path):
    path = tmp_path / "exclusive.klab-sim.sqlite"
    first = make_server(0, state_file=path)
    try:
        with pytest.raises((OSError, ValueError)):
            make_server(0, state_file=path)
    finally:
        first.server_close()
    before = path.read_bytes()
    with pytest.raises(ValueError, match="tuple"):
        make_server(0, seed=2, state_file=path)
    assert path.read_bytes() == before
    with pytest.raises(ValueError): make_server(0, mode="replay", state_file=path)
    with pytest.raises(ValueError): make_server(0, state_file=tmp_path / ".klab" / "live.klab-sim.sqlite")
    foreign = tmp_path / "foreign.klab-sim.sqlite"
    with sqlite3.connect(foreign) as database:
        database.execute("CREATE TABLE unrelated (x INTEGER)")
    before = foreign.read_bytes()
    with pytest.raises(ValueError, match="not owned"):
        make_server(0, state_file=foreign)
    assert foreign.read_bytes() == before


def test_failed_checkpoint_rolls_back_mutation_without_acknowledging_partial_state(tmp_path, monkeypatch):
    state = State()
    state.store = Store(tmp_path / "failure.klab-sim.sqlite", state)
    before = snapshot(state)
    try:
        def fail(_): raise OSError("synthetic disk failure must not be disclosed")
        monkeypatch.setattr(state.store, "save", fail)
        status, payload, _ = state.request("POST", "/runtime/createSession", {"Authorization": "sim-token-alice", "Content-Type": "application/json"}, b'{}')
        assert status == 507 and "synthetic disk failure" not in json.dumps(payload)
        assert snapshot(state) == before
    finally:
        state.store.close()


def test_corrupt_checkpoint_fails_closed_without_reset_or_lease_resurrection(tmp_path):
    path = tmp_path / "corrupt.klab-sim.sqlite"
    owned = make_server(0, state_file=path)
    owned.server_close()
    with sqlite3.connect(path) as database:
        database.execute("UPDATE checkpoint SET payload=? WHERE id=1", (b'{"version":1}',))
    before = path.read_bytes()
    with pytest.raises(ValueError, match="checksum"):
        make_server(0, state_file=path)
    assert path.read_bytes() == before


def test_shutdown_refuses_new_mutations_instead_of_acknowledging_volatile_work(tmp_path):
    owned = make_server(0, state_file=tmp_path / "shutdown.klab-sim.sqlite")
    state = owned.state
    owned.server_close()
    code, _, _ = state.request("POST", "/runtime/createSession", {"Authorization": "sim-token-alice"}, b'{}')
    assert code == 503 and not state.sessions


def test_actual_sqlite_write_refusal_rolls_back_and_retains_committed_checkpoint(tmp_path):
    state = State()
    state.store = Store(tmp_path / "readonly.klab-sim.sqlite", state)
    before = snapshot(state)
    committed = state.store.connection.execute("SELECT payload,digest FROM checkpoint WHERE id=1").fetchone()
    try:
        state.store.connection.execute("PRAGMA query_only=ON")
        status, _, _ = state.request("POST", "/runtime/createSession", {"Authorization": "sim-token-alice", "Content-Type": "application/json"},
            b'{"configuration":{"id":"ownedfailed"}}')
        assert status == 507 and snapshot(state) == before
        assert state.store.connection.execute("SELECT payload,digest FROM checkpoint WHERE id=1").fetchone() == committed
    finally:
        state.store.close()


@pytest.mark.parametrize("failure", ["version", "seed", "map", "pair", "counter", "worldview", "task-id", "task-state", "duplicate-pair"])
def test_invalid_retained_state_never_partially_restores_or_reuses_identity(failure):
    state = State()
    before = snapshot(state)
    invalid = snapshot(state)
    if failure == "version": invalid["version"] = True
    elif failure == "seed": invalid["seed"] = 2
    elif failure == "map": invalid["simple"]["contexts"] = []
    elif failure == "pair": invalid["pairs"]["jobs"] = [[[]]]
    elif failure == "counter": invalid["counters"]["next_job"] = 0
    elif failure == "worldview": invalid["worldview_id"] = None
    elif failure == "task-id": invalid["worker_tasks"] = {"arbitrary": {"state": "QUEUED"}}
    elif failure == "task-state":
        invalid["worker_tasks"] = {"worker-task-1": {"state": "INVENTED"}}
        invalid["next_worker_task"] = 2
    else:
        invalid["pairs"]["jobs"] = [[["runtime", 1], {}], [["runtime", 1], {}]]
        invalid["counters"]["next_job"] = 2
    with pytest.raises(ValueError): restore(state, invalid)
    assert snapshot(state) == before
