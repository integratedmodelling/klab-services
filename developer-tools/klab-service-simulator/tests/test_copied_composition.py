"""Three owned HTTP services, copied reference inputs; NOT native live federation."""
import asyncio
from contextlib import ExitStack
from copy import deepcopy
import json

import pytest

from klab_client import Client, AsyncClient, ObservationRequest
from klab_client.read_plans import open_read_plan, read_plan_shard, assemble_read_plan, release_read_plan
from klab_client.errors import AuthorizationError, ServerError
from klab_simulator.composition import PROTOCOL, PREFIX, REVISION_KIND, _digest
from conftest import launch
from test_vertical import client_args, control, geometry


def capture(observation):
    plan = open_read_plan(observation, allow_experimental=True)
    try:
        data = assemble_read_plan(plan, tuple(read_plan_shard(plan, spec.index, allow_experimental=True) for spec in plan.shards))
        return {"origin": {"runtime_url": plan.source.runtime_url, "runtime_id": plan.source.runtime_id,
            "context_id": plan.source.context_id, "observation_id": plan.source.observation_id},
            "epoch": plan.epoch, "plan_id": plan.plan_id, "revision": plan.revision, "revision_kind": plan.revision_kind,
            "descriptor": {"shape": list(plan.shape), "crs": plan.crs, "transform": list(plan.transform),
                "units": plan.units, "dtype": plan.dtype, "order": plan.order},
            "values": list(data.values), "valid": list(data.valid)}
    finally:
        release_read_plan(plan, allow_experimental=True)


def body(sources):
    return {"protocol": PROTOCOL, "operation": "PAIRWISE_SUM_REFERENCE", "sources": sources,
        "expected_vector": [{key: source[key] for key in ("origin", "epoch", "plan_id", "revision", "revision_kind")} for source in sources]}


def test_three_owned_services_publish_c_result_with_pinned_origins_and_preserve_a_b_on_detach():
    with ExitStack() as stack:
        ready = [stack.enter_context(launch(scenario, seed=seed)) for scenario, seed in
            (("elevation", 11), ("zero-negative-nodata", 22), ("copied-composition", 33))]
        clients = [stack.enter_context(Client(item["endpoints"]["runtime"], **client_args(item))) for item in ready]
        sessions = [client.create_session() for client in clients]
        contexts = [session.create_context() for session in sessions]
        try:
            observed = [context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                for context in contexts[:2]]
            sources = [capture(observation) for observation in observed]
            assert sources[0]["origin"]["observation_id"] == sources[1]["origin"]["observation_id"]
            assert sources[0]["origin"]["runtime_id"] != sources[1]["origin"]["runtime_id"]
            options = dict(scope=contexts[2].id, service_id=ready[2]["service_ids"]["runtime"], max_response_bytes=131072)
            with Client(ready[0]["endpoints"]["runtime"], **client_args(ready[0], "sim-token-bob")) as denied_source:
                with pytest.raises(AuthorizationError): denied_source.restore_handle(observed[0].to_handle())
            assert control(ready[2])["compositions"] == 0
            before = [control(item)["counts"] for item in ready[:2]]
            output = clients[2].transport.request("runtime", "POST", PREFIX + "create", json=body(sources), ambiguous=True, **options)
            assert output["origin"] == {"runtime_id": ready[2]["service_ids"]["runtime"], "context_id": contexts[2].id}
            assert output["source_vector"] == body(sources)["expected_vector"]
            assert not output["live_federation"] and not output["durable"]
            assert output["source_authority"] == "CALLER_ASSERTED_COPIED_REFERENCE_NOT_REMOTE_GRANT"
            expected = [a + b if va and vb else None for a, b, va, vb in
                zip(sources[0]["values"], sources[1]["values"], sources[0]["valid"], sources[1]["valid"])]
            assert output["values"] == expected and len(expected) == 400
            async def inspect():
                async with AsyncClient(ready[2]["endpoints"]["runtime"], **client_args(ready[2])) as reader:
                    return await reader.transport.request("runtime", "GET", PREFIX + output["result_id"], **options)
            assert asyncio.run(inspect()) == output
            with Client(ready[2]["endpoints"]["runtime"], **client_args(ready[2], "sim-token-bob")) as foreign:
                with pytest.raises(AuthorizationError):
                    foreign.transport.request("runtime", "GET", PREFIX + output["result_id"], **options)
            drifted = body(deepcopy(sources)); drifted["expected_vector"][0]["revision"] = "0" * 64
            with pytest.raises(ServerError) as error:
                clients[2].transport.request("runtime", "POST", PREFIX + "create", json=drifted, **options)
            assert error.value.status_code == 409 and control(ready[2])["compositions"] == 1
            released = clients[2].transport.request("runtime", "DELETE", PREFIX + output["result_id"], ambiguous=True, **options)
            assert released == {"released": True, "source_mutations": 0}
            assert control(ready[2])["compositions"] == 0
            assert [control(item)["counts"] for item in ready[:2]] == before
            for source, observation in zip(sources, observed):
                assert capture(observation)["revision"] == source["revision"]
            assert all(sum(row["path"].endswith("/submit") for row in control(item)["trace"]) == 1 for item in ready[:2])
            assert not any(row["path"].endswith("/submit") for row in control(ready[2])["trace"])
        finally:
            for context in reversed(contexts): context.release()
            for session in reversed(sessions): session.release()
        assert all(not any(control(item)["counts"].values()) for item in ready)


def sample(runtime="A"):
    values, valid = [0., -2.5, None, 1.], [True, True, False, True]
    return {"origin": {"runtime_url": "https://source.test/" + runtime, "runtime_id": runtime,
        "context_id": "s.c", "observation_id": 1}, "epoch": "a" * 32, "plan_id": "b" * 32,
        "revision": _digest(values, valid), "revision_kind": REVISION_KIND,
        "descriptor": {"shape": [2, 2], "crs": "EPSG:3857", "transform": [1, 0, 0, 0, -1, 2],
            "units": "m", "dtype": "float64", "order": "ROW_MAJOR_NORTH_TO_SOUTH"}, "values": values, "valid": valid}


def test_direct_atomicity_bad_sources_vectors_descriptors_and_release():
    from klab_simulator.state import State
    state = State("copied-composition"); state.contexts["s.c"] = {"owner": "alice"}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "klab-service": state.service_ids["runtime"], "Content-Type": "application/json"}
    route = "/runtime" + PREFIX
    def create(value): return state.request("POST", route + "create", headers, json.dumps(value).encode())
    source = sample(); other = sample("B")
    for malformed in (None, {}, {**source, "revision_kind": "native"}, {**source, "origin": {}},
            {**source, "origin": {**source["origin"], "runtime_url": "https://secret@source.test"}},
            {**source, "epoch": "invalid"}, {**source, "descriptor": {}},
            {**source, "valid": [1, True, False, True]}, {**source, "values": [1., 2., 3., 4.]},
            {**source, "revision": "0" * 64}):
        value = body([source, other]); value["sources"][0] = malformed
        assert create(value)[0] == 422 and not state.compositions.rows
    assert create(None)[0] == 422
    stale = body([source, other]); stale["expected_vector"][0]["revision"] = "0" * 64
    assert create(stale)[0] == 409
    assert state.request("GET", route + "1", {**headers, "klab-scope": "s.c.1"})[0] == 403
    assert create(body([source, source]))[0] == 422
    self_source = sample(state.service_ids["runtime"])
    assert create(body([self_source, other]))[0] == 422
    other_units = deepcopy(other); other_units["descriptor"]["units"] = "mm"
    assert create(body([source, other_units]))[0] == 422
    huge = sample(); huge["values"] = [1e308, 1e308, None, 1e308]; huge["revision"] = _digest(huge["values"], huge["valid"])
    huge_b = deepcopy(huge); huge_b["origin"]["runtime_id"] = "B"
    assert create(body([huge, huge_b]))[0] == 422 and not state.compositions.rows
    for _ in range(8): assert create(body([source, other]))[0] == 200
    assert create(body([source, other]))[0] == 429
    assert state.request("GET", route + "1", headers)[1]["result_id"] == "1"
    assert state.request("DELETE", route + "1", headers)[1]["source_mutations"] == 0
    assert state.request("GET", route + "1", headers)[0] == 410
    assert state.request("PATCH", route + "1", headers)[0] == 404
    assert state.request("GET", route + "bad", headers)[0] == 404
    assert state.request("POST", route + "create", headers, b'{"protocol":1,"protocol":2}')[0] == 422
    assert state.request("POST", route + "create", headers, b'x' * 262145)[0] == 413
    assert state.request("GET", route + "2?query=unsupported", headers)[0] == 422


def test_composition_refuses_unselected_scenario_or_durable_storage():
    from klab_simulator.state import State, ContractError
    for scenario in ("elevation", "copied-composition"):
        state = State(scenario)
        if scenario == "copied-composition": state.store = object()
        with pytest.raises(ContractError) as caught:
            state.compositions.request("POST", PREFIX + "create", "s.c", "alice", {}, None)
        assert caught.value.status == 409
