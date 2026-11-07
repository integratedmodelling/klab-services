"""Experimental reference checkpoints; NOT Java scientific commit/lineage proof."""
import asyncio
from concurrent.futures import ThreadPoolExecutor
import json

import pytest

from klab_client import Client, AsyncClient, ObservationRequest
from klab_client.errors import AuthorizationError, ServerError
from klab_simulator.revision_snapshots import PROTOCOL, KIND, PREFIX
from conftest import launch
from test_vertical import client_args, control, geometry


def options(ready, context):
    return dict(scope=context.id, service_id=ready["service_ids"]["runtime"], max_response_bytes=16384)


def test_two_clients_checkpoint_cas_conflict_history_refresh_and_owner_cleanup():
    with launch("revision-convergence") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
            session = owner.create_session(); context = session.create_context()
            route = PREFIX + "checkpoint"
            body = {"protocol": PROTOCOL, "expected_revision": 0}
            try:
                with Client(ready["endpoints"]["runtime"], **client_args(ready)) as reader:
                    attached = reader.attach_context(context.id)
                    first = owner.transport.request("runtime", "POST", route, json=body, ambiguous=True, **options(ready, context))
                    assert first["revision"] == 1 and first["observation_ids"] == []
                    request = ObservationRequest("geography:Elevation in m", geometry=geometry())
                    waiting = context.observe(request)
                    assert waiting.status().status == "WAITING"
                    old = reader.transport.request("runtime", "GET", route, params={"protocol": PROTOCOL}, **options(ready, attached))
                    assert old == first  # Polling alone does not invent a new checkpoint.
                    observed = waiting.result(timeout=2)
                    with pytest.raises(ServerError) as conflict:
                        reader.transport.request("runtime", "POST", route, json=body, **options(ready, attached))
                    assert conflict.value.status_code == 409
                    second = owner.transport.request("runtime", "POST", route,
                        json={**body, "expected_revision": 1}, **options(ready, context))
                    assert second["revision"] == 2 and second["previous_revision"] == 1
                    assert second["observation_ids"] == [observed.id]
                    assert second["kind"] == KIND and not second["durable"] and not second["complete_lineage"]
                    assert first["observation_ids"] == []  # Retained local DTO not rewritten on refresh.
                    historical = reader.transport.request("runtime", "GET", route,
                        params={"protocol": PROTOCOL, "revision": 1}, **options(ready, attached))
                    assert historical == first
                    fresh = reader.transport.request("runtime", "GET", route,
                        params={"protocol": PROTOCOL}, **options(ready, attached))
                    assert fresh == second
                assert owner.transport.request("runtime", "GET", route,
                    params={"protocol": PROTOCOL}, **options(ready, context)) == second
                with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as foreign:
                    with pytest.raises(AuthorizationError):
                        foreign.transport.request("runtime", "GET", route,
                            params={"protocol": PROTOCOL}, **options(ready, context))
            finally:
                context.release(); session.release()
        state = control(ready)
        assert not any(state["counts"].values()) and state["revision_contexts"] == 0
        assert sum(row["path"].endswith("/submit") for row in state["trace"]) == 1


def test_independent_concurrent_clients_have_one_cas_winner_and_async_exact_history():
    with launch("revision-convergence") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
            session = owner.create_session(); context = session.create_context()
            route = PREFIX + "checkpoint"
            try:
                def write(_):
                    with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                        try:
                            return client.transport.request("runtime", "POST", route,
                                json={"protocol": PROTOCOL, "expected_revision": 0}, **options(ready, context))
                        except ServerError as error:
                            assert error.status_code == 409
                            return "CONFLICT"
                with ThreadPoolExecutor(max_workers=2) as pool: outcomes = list(pool.map(write, range(2)))
                assert outcomes.count("CONFLICT") == 1
                winner = next(value for value in outcomes if value != "CONFLICT")
                async def read():
                    async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as reader:
                        return await reader.transport.request("runtime", "GET", route,
                            params={"protocol": PROTOCOL, "revision": 1}, **options(ready, context))
                assert asyncio.run(read()) == winner
            finally:
                context.release(); session.release()
        assert control(ready)["revision_contexts"] == 0


def test_fixture_history_retention_and_strict_intake_in_process():
    from klab_simulator.state import State
    state = State("revision-convergence"); state.contexts["s.c"] = {"owner": "alice"}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c",
        "klab-service": state.service_ids["runtime"], "Content-Type": "application/json"}
    route = "/runtime" + PREFIX + "checkpoint"
    def post(value): return state.request("POST", route, headers, json.dumps(value).encode())
    assert state.request("GET", route + "?protocol=" + PROTOCOL, headers)[0] == 410
    for expected in range(9):
        assert post({"protocol": PROTOCOL, "expected_revision": expected})[1]["revision"] == expected + 1
    assert state.request("GET", route + "?protocol=" + PROTOCOL + "&revision=1", headers)[0] == 410
    assert post({"protocol": PROTOCOL, "expected_revision": 0})[0] == 409
    for bad in (None, {}, {"protocol": PROTOCOL, "expected_revision": True},
            {"protocol": PROTOCOL, "expected_revision": -1}, {"protocol": "other", "expected_revision": 9}):
        assert post(bad)[0] == 422
    for query in ("", "?protocol=wrong", "?protocol=" + PROTOCOL + "&revision=0",
            "?protocol=" + PROTOCOL + "&revision=1&revision=2", "?protocol=" + PROTOCOL + "&revision=x"):
        assert state.request("GET", route + query, headers)[0] == 422
    assert state.request("POST", route, headers, b'{"protocol":1,"protocol":2}')[0] == 422
    assert state.request("POST", route, headers, b'bad')[0] == 422
    assert state.request("POST", route, headers, b'x' * 4097)[0] == 413
    assert state.request("DELETE", route, headers)[0] == 404
    assert state.request("POST", route + "?unexpected=1", headers)[0] == 404
    assert state.request("GET", route + "?protocol=" + PROTOCOL, headers)[1]["revision"] == 9
    assert state.request("GET", route + "?protocol=" + PROTOCOL + "&revision=2", headers)[1]["revision"] == 2
    assert state.request("GET", "/runtime" + PREFIX + "unknown", headers)[0] == 404
    assert state.request("GET", route + "?protocol=" + PROTOCOL,
        {**headers, "klab-scope": "s.c.1"})[0] == 403
    state.observations = {("s.c", identity): {} for identity in range(257)}
    assert post({"protocol": PROTOCOL, "expected_revision": 9})[0] == 429
    assert state.revision_snapshots.rows["s.c"][-1]["revision"] == 9


def test_fixture_refuses_other_scenarios_or_durable_storage_without_native_claim():
    from klab_simulator.state import State, ContractError
    for scenario in ("elevation", "revision-convergence"):
        state = State(scenario)
        if scenario == "revision-convergence": state.store = object()
        with pytest.raises(ContractError) as caught:
            state.revision_snapshots.request("GET", PREFIX + "checkpoint", "s.c", "alice", {}, None)
        assert caught.value.status == 409
