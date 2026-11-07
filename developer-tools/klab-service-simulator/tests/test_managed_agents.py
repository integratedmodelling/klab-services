"""JAVA-GAP-0008 synthetic reference; never parses or executes k.Actors source."""
import asyncio
from dataclasses import replace
import json
from copy import deepcopy

import pytest

from klab_client import Client, AsyncClient, Context
from klab_client.errors import AuthorizationError, ServerError
from klab_client.managed_agents import (create_managed_reference, read_managed_reference, command_managed_reference,
    async_create_managed_reference, async_read_managed_reference, async_command_managed_reference)
from test_vertical import client_args, control
from conftest import launch


@pytest.mark.parametrize("fixture,terminal", [("idle", "STOPPED"), ("finish", "FINISHED"), ("fail", "FAILED")])
def test_owned_managed_reference_single_use_terminal_and_separate_cleanup(simulator, fixture, terminal):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session(); context = session.create_context()
        try:
            created = create_managed_reference(context, fixture=fixture, allow_experimental=True)
            ref = created.reference
            assert created.state == "READY" and not created.cleanup_confirmed
            with pytest.raises(ServerError) as early:
                command_managed_reference(ref, "purge", expected_revision=created.revision, allow_experimental=True)
            assert early.value.status_code == 409
            running = command_managed_reference(ref, "start", expected_revision=created.revision, allow_experimental=True)
            with pytest.raises(ServerError) as stale:
                command_managed_reference(ref, "start", expected_revision=created.revision, allow_experimental=True)
            assert stale.value.status_code == 409
            if fixture == "idle":
                stopping = command_managed_reference(ref, "stop", expected_revision=running.revision, allow_experimental=True)
                assert stopping.state == "STOPPING" and not stopping.cleanup_confirmed
            with Client(simulator["endpoints"]["runtime"], **client_args(simulator, "sim-token-bob")) as foreign:
                denied = Context(foreign, {"id": context.id, "serviceId": simulator["service_ids"]["runtime"]})
                with pytest.raises(AuthorizationError): read_managed_reference(replace(ref, context=denied), allow_experimental=True)
            finished = read_managed_reference(ref, allow_experimental=True)
            assert finished.state == terminal and not finished.cleanup_confirmed
            cleaned = read_managed_reference(ref, allow_experimental=True)
            assert cleaned.state == terminal and cleaned.cleanup_confirmed
            with pytest.raises(ServerError) as replay:
                command_managed_reference(ref, "start", expected_revision=cleaned.revision, allow_experimental=True)
            assert replay.value.status_code == 409
            assert command_managed_reference(ref, "purge", expected_revision=cleaned.revision, allow_experimental=True)
            with pytest.raises(ServerError) as purged: read_managed_reference(ref, allow_experimental=True)
            assert purged.value.status_code == 410
        finally:
            context.release(); session.release()
    assert control(simulator)["managed_agents"] == 0 and not any(control(simulator)["counts"].values())


def test_async_deferred_stop_before_start_and_cleanup_on_scope_release(simulator):
    async def run():
        async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            session = await client.create_session(); context = await session.create_context()
            try:
                created = await async_create_managed_reference(context, allow_experimental=True)
                stopped = await async_command_managed_reference(created.reference, "stop", expected_revision=1, allow_experimental=True)
                assert stopped.state == "STOPPING"
                terminal = await async_read_managed_reference(created.reference, allow_experimental=True)
                assert terminal.state == "STOPPED" and not terminal.cleanup_confirmed
                cleaned = await async_read_managed_reference(created.reference, allow_experimental=True)
                assert cleaned.cleanup_confirmed
            finally:
                await context.release(); await session.release()
    asyncio.run(run())
    assert control(simulator)["managed_agents"] == 0


def test_explicit_sdk_rebind_inspects_same_agent_but_restart_loses_reference(tmp_path):
    from urllib.parse import urlsplit
    path = tmp_path / "agents.klab-sim.sqlite"
    with launch(state_file=path) as ready:
        port = urlsplit(ready["endpoints"]["runtime"]).port
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            created = create_managed_reference(context, allow_experimental=True)
            ref = created.reference
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as other:
            bound = other.attach_context(ref.source.scope)
            current = read_managed_reference(replace(ref, context=bound), allow_experimental=True)
            assert current.state == "READY" and current.revision == 1
    with launch(port=port, state_file=path) as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            bound = client.attach_context(ref.source.scope)
            with pytest.raises(ServerError) as lost: read_managed_reference(replace(ref, context=bound), allow_experimental=True)
            assert lost.value.status_code == 410 and control(ready)["managed_agents"] == 0
            bound.release(); client.runtime.release_session(session.id)


@pytest.mark.parametrize("operation", ["create", "start", "stop", "read", "purge", "reset"])
def test_failed_durable_requests_roll_back_reference_rows_and_epoch(operation):
    from klab_simulator.state import State
    from klab_simulator.persistence import snapshot
    state = State(); state.test_mode = True
    state.contexts["s.c"] = {"owner": "alice"}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "klab-service": state.service_ids["runtime"]}
    prefix = "/runtime/experimental/managed-agents/v1/"
    body = {"protocol": "klab.experimental.managed-agents/1", "fixture": "finish" if operation == "read" else "idle"}
    status, created, _ = state.request("POST", prefix + "create", headers, json.dumps(body).encode())
    assert status == 200
    query = "?protocol=" + body["protocol"] + "&epoch=" + state.managed_agents.epoch
    if operation in ("read", "purge"):
        state.request("POST", prefix + created["agent_id"] + "/" + ("start" if operation == "read" else "stop"), headers,
            json.dumps({"protocol": body["protocol"], "epoch": state.managed_agents.epoch, "expected_revision": 1}).encode())
    if operation == "purge":
        state.request("GET", prefix + created["agent_id"] + query, headers)
        state.request("GET", prefix + created["agent_id"] + query, headers)
    before = snapshot(state); epoch = state.managed_agents.epoch; rows = deepcopy(state.managed_agents.rows)
    class FailedStore:
        def save(self, _): raise OSError("Synthetic disk failure")
    state.store = FailedStore()
    if operation == "create": result = state.request("POST", prefix + "create", headers, json.dumps(body).encode())
    elif operation in ("start", "stop", "purge"):
        result = state.request("POST", prefix + created["agent_id"] + "/" + operation, headers,
            json.dumps({"protocol": body["protocol"], "epoch": epoch, "expected_revision": rows[created["agent_id"]]["revision"]}).encode())
    elif operation == "read":
        result = state.request("GET", prefix + created["agent_id"] + "?protocol=" + body["protocol"] + "&epoch=" + epoch, headers)
    else: result = state.request("POST", "/__sim__/reset", {"Authorization": "sim-admin-control"})
    assert result[0] == 507 and snapshot(state) == before
    assert state.managed_agents.epoch == epoch and state.managed_agents.rows == rows


def test_capacity_owner_root_runtime_epoch_and_unexecutable_intake_refusals():
    from klab_simulator.state import State
    state = State()
    state.contexts["s.c"] = {"owner": "alice"}; state.contexts["s.other"] = {"owner": "alice"}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "klab-service": state.service_ids["runtime"]}
    prefix = "/runtime/experimental/managed-agents/v1/"
    body = json.dumps({"protocol": "klab.experimental.managed-agents/1", "fixture": "idle"}).encode()
    for _ in range(16):
        status, row, _ = state.request("POST", prefix + "create", headers, body)
        assert status == 200
    assert state.request("POST", prefix + "create", headers, body)[0] == 429
    query = "?protocol=klab.experimental.managed-agents/1&epoch=" + state.managed_agents.epoch
    path = prefix + row["agent_id"]
    assert state.request("GET", path + query, {**headers, "klab-scope": "s.other"})[0] == 403
    assert state.request("GET", path + query, {**headers, "klab-service": "foreign"})[0] == 403
    assert state.request("GET", path + query, {**headers, "Authorization": "sim-token-bob"})[0] == 403
    assert state.request("GET", path + "?protocol=klab.experimental.managed-agents/1&epoch=" + "0" * 32, headers)[0] == 410
    for raw, expected in [(b"x" * 4097, 413), (b"{", 422), (b'{"protocol":1,"protocol":2}', 422),
        (json.dumps({"protocol": "klab.experimental.managed-agents/1", "fixture": "behavior arbitrary"}).encode(), 422)]:
        assert state.request("POST", prefix + "create", headers, raw)[0] == expected
    assert len(state.managed_agents.rows) == 16
