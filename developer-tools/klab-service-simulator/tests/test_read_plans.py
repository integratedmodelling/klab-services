"""SIMULATED_PROVISIONAL/JAVA-GAP-0006/0014, reference hashes not native commit IDs."""
from dataclasses import replace
import json
import asyncio
from copy import deepcopy
from urllib.parse import urlsplit

import pytest

from klab_client import Client, Context, ObservationRequest
from klab_client.errors import AuthorizationError, InvalidRequestError, ProtocolError, ServerError
from klab_client.handles import to_handle
from klab_client.read_plans import open_read_plan, read_plan_shard, assemble_read_plan, release_read_plan
from klab_simulator.state import State
from conftest import launch
from test_vertical import client_args, control, geometry


def test_owned_multishard_snapshot_preserves400cells_zero_negative_mask_and_release():
    with launch("zero-negative-nodata") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            try:
                observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                with pytest.raises(InvalidRequestError): open_read_plan(observation)
                plan = open_read_plan(observation, shard_cells=73, allow_experimental=True)
                assert plan.shape == (20, 20) and len(plan.shards) == 6
                assert plan.crs == "EPSG:3857" and plan.transform == (100, 0, 0, 0, -100, 2000) and plan.units == "m"
                shards = tuple(read_plan_shard(plan, n, allow_experimental=True) for n in range(len(plan.shards)))
                data = assemble_read_plan(plan, shards)
                assert len(data.values) == 400 and data.values[:4] == (0, -7.5, None, None)
                assert data.valid[:4] == (True, True, False, False) and data.values[-1] == 195
                assert plan.revision_kind == "REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT"
                with pytest.raises(ProtocolError): assemble_read_plan(plan, tuple(reversed(shards)))
                with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as foreign:
                    denied = Context(foreign, {"id": context.id, "serviceId": ready["service_ids"]["runtime"]})
                    with pytest.raises(AuthorizationError): read_plan_shard(replace(plan, context=denied), 0, allow_experimental=True)
                assert release_read_plan(plan, allow_experimental=True)
                with pytest.raises(ServerError) as closed: read_plan_shard(plan, 0, allow_experimental=True)
                assert closed.value.status_code == 410
            finally:
                context.release(); session.release()
        assert control(ready)["read_plans"] == 0 and not any(control(ready)["counts"].values())


def test_owned_restart_discards_read_grants_without_reusing_or_replaying_them(tmp_path):
    path = tmp_path / "plans.klab-sim.sqlite"
    with launch(state_file=path) as ready:
        port = urlsplit(ready["endpoints"]["runtime"]).port
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
            reference = to_handle(observation)
            plan = open_read_plan(observation, allow_experimental=True)
    with launch(port=port, state_file=path) as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            context = client.attach_context(plan.source.scope)
            lost = replace(plan, context=context)
            with pytest.raises(ServerError) as expired: read_plan_shard(lost, 0, allow_experimental=True)
            assert expired.value.status_code == 410 and control(ready)["read_plans"] == 0
            restored = client.restore_handle(reference)
            new = open_read_plan(restored, allow_experimental=True)
            assert new.epoch != plan.epoch and new.plan_id != plan.plan_id and new.revision == plan.revision
            release_read_plan(new, allow_experimental=True)
            context.release(); client.runtime.release_session(session.id)


def test_ephemeral_read_snapshot_remains_pinned_and_expired_grants_fail_closed():
    state = State()
    state.contexts["s.c"] = {"owner": "alice"}; state.observations[("s.c", 1)] = {"id": 1}
    body = json.dumps({"protocol": "klab.experimental.read-plans/1", "observation_id": 1,
        "shard_cells": 128, "lease_seconds": 30, "max_cells": 4096}).encode()
    headers = {"klab-service": state.service_ids["runtime"]}
    plan = state.read_plans.open("s.c", body)
    state.values[0] = 999  # Explicit synthetic fixture-buffer alteration, not a native observation edit.
    row, _ = state.read_plans.request("GET", "/experimental/read-plans/v1/" + plan["plan_id"] + "/shards/0?protocol=klab.experimental.read-plans/1&epoch=" + plan["epoch"], "s.c", "alice", headers, b"")
    assert row["values"][0] == 100
    state.read_plans.rows[plan["plan_id"]]["expires"] = -1
    from klab_simulator.state import ContractError
    with pytest.raises(ContractError) as expired:
        state.read_plans.request("GET", "/experimental/read-plans/v1/" + plan["plan_id"] + "/shards/0?protocol=klab.experimental.read-plans/1&epoch=" + plan["epoch"], "s.c", "alice", headers, b"")
    assert expired.value.status == 410 and state.read_plans.rows == {}


def test_native_async_owned_read_plan_lifecycle(simulator):
    from klab_client import AsyncClient
    from klab_client.read_plans import async_open_read_plan, async_read_plan_shard, async_release_read_plan
    async def run():
        async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            session = await client.create_session(); context = await session.create_context()
            try:
                job = await context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
                observation = await job.result(timeout=2)
                plan = await async_open_read_plan(observation, allow_experimental=True)
                shards = tuple([await async_read_plan_shard(plan, n, allow_experimental=True) for n in range(len(plan.shards))])
                assert len(assemble_read_plan(plan, shards).values) == 400
                assert await async_release_read_plan(plan, allow_experimental=True)
            finally:
                await context.release(); await session.release()
    asyncio.run(run())
    assert control(simulator)["read_plans"] == 0


def owned_state():
    state = State()
    state.contexts["s.c"] = {"owner": "alice"}
    state.observations[("s.c", 1)] = {"id": 1}
    state.next_observation = 2
    body = {"protocol": "klab.experimental.read-plans/1", "observation_id": 1,
        "shard_cells": 128, "lease_seconds": 30, "max_cells": 4096}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "klab-service": state.service_ids["runtime"]}
    return state, body, headers


def test_capacity_response_budget_owner_epoch_and_scope_cleanup():
    from klab_simulator.state import ContractError
    state, body, headers = owned_state()
    for _ in range(16): state.read_plans.open("s.c", json.dumps(body).encode())
    with pytest.raises(ContractError) as full: state.read_plans.open("s.c", json.dumps(body).encode())
    assert full.value.status == 429 and len(state.read_plans.rows) == 16
    state.read_plans.rows.clear()
    body["shard_cells"] = 1
    original_values = state.values
    state.grid["shape"] = [40, 40]
    state.values = [100] * 1600
    with pytest.raises(ContractError) as oversized: state.read_plans.open("s.c", json.dumps(body).encode())
    assert oversized.value.status == 413 and not state.read_plans.rows
    state.grid["shape"] = [20, 20]
    state.values = original_values
    body["shard_cells"] = 128
    plan = state.read_plans.open("s.c", json.dumps(body).encode())
    path = "/runtime/experimental/read-plans/v1/" + plan["plan_id"] + "/shards/0?protocol=klab.experimental.read-plans/1&epoch="
    assert state.request("GET", path + "0" * 32, headers)[0] == 410
    state.contexts["s.other"] = {"owner": "alice"}
    assert state.request("GET", path + plan["epoch"], {**headers, "klab-scope": "s.other"})[0] == 403
    assert state.request("GET", path + plan["epoch"], {**headers, "klab-service": "foreign"})[0] == 403
    del state.contexts["s.c"]
    state.read_plans.cleanup()
    assert not state.read_plans.rows


@pytest.mark.parametrize("operation", ["open", "release", "reset"])
def test_failed_persistence_restores_ephemeral_grants_epoch_and_durable_state(operation):
    from klab_simulator.persistence import snapshot
    state, body, headers = owned_state()
    state.test_mode = True
    plan = state.read_plans.open("s.c", json.dumps(body).encode())
    before = snapshot(state)
    epoch, rows = state.read_plans.epoch, deepcopy(state.read_plans.rows)
    assert "read_plans" not in before
    class FailedStore:
        def save(self, _): raise OSError("Synthetic disk failure")
    state.store = FailedStore()
    if operation == "open":
        result = state.request("POST", "/runtime/experimental/read-plans/v1/open", headers, json.dumps(body).encode())
    elif operation == "release":
        result = state.request("DELETE", "/runtime/experimental/read-plans/v1/" + plan["plan_id"] + "?protocol=klab.experimental.read-plans/1&epoch=" + epoch, headers)
    else:
        result = state.request("POST", "/__sim__/reset", {"Authorization": "sim-admin-control"})
    assert result[0] == 507
    assert snapshot(state) == before and state.read_plans.epoch == epoch and state.read_plans.rows == rows


@pytest.mark.parametrize("change", [{"protocol": "native"}, {"observation_id": True}, {"observation_id": 0},
    {"shard_cells": True}, {"shard_cells": 129}, {"lease_seconds": 0}, {"lease_seconds": 61},
    {"max_cells": 399}, {"max_cells": 4097}, {"extra": 1}])
def test_invalid_intake_and_insufficient_cell_budgets_allocate_no_grants(change):
    state, body, headers = owned_state()
    status, _, _ = state.request("POST", "/runtime/experimental/read-plans/v1/open", headers, json.dumps({**body, **change}).encode())
    assert status == 422 and not state.read_plans.rows


@pytest.mark.parametrize("body,expected", [(b"x" * 4097, 413), (b"{", 422),
    (b'{"protocol":"a","protocol":"b"}', 422)])
def test_malformed_or_oversized_requests_allocate_no_grants(body, expected):
    state, _, headers = owned_state()
    assert state.request("POST", "/runtime/experimental/read-plans/v1/open", headers, body)[0] == expected
    assert not state.read_plans.rows


def test_reset_rotates_epoch_and_drops_grants_on_success():
    state, body, _ = owned_state()
    state.test_mode = True
    state.read_plans.open("s.c", json.dumps(body).encode())
    epoch = state.read_plans.epoch
    assert state.request("POST", "/__sim__/reset", {"Authorization": "sim-admin-control"})[0] == 200
    assert state.read_plans.epoch != epoch and not state.read_plans.rows
