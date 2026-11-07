"""SIMULATED_PROVISIONAL/JAVA-GAP-0016/0013; no complete native lineage/commit claim."""
import asyncio
import json

import pytest

from klab_client import (Client, AsyncClient, Context, ObservationRequest, fetch_provenance,
    async_fetch_provenance)
from klab_client.errors import AuthorizationError
from conftest import launch
from test_vertical import client_args, control, geometry


def test_two_clients_observe_partial_dependency_and_plan_without_inventing_revision_or_replay():
    with launch("provenance-dependencies") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
            session = owner.create_session(); context = session.create_context()
            try:
                request = ObservationRequest("geography:Elevation in m", geometry=geometry())
                first = context.observe(request).result(timeout=2)
                retained = fetch_provenance(first, limit=1)
                assert len(retained.links) == 1 and retained.next_offset is None
                assert retained.partial and "unknown" in retained.consistency
                with Client(ready["endpoints"]["runtime"], **client_args(ready)) as reader:
                    attached = reader.attach_context(context.id)
                    restored_first = reader.restore_handle(first.to_handle())
                    second_job = attached.observe(request)
                    assert second_job.status().status == "WAITING"
                    # An uncompleted fixture observation is not a committed dependency.
                    assert len(fetch_provenance(restored_first).links) == 1
                    second = second_job.result(timeout=2)
                    second_handle = second.to_handle()
                    page1 = fetch_provenance(restored_first, limit=1)
                    assert page1.next_offset == 1 and page1.links[0].relationship == "MEASURED"
                    page2 = fetch_provenance(restored_first, limit=1, offset=page1.next_offset)
                    assert page2.next_offset is None and page2.links[0].relationship == "CONTRIBUTED_TO"
                    edge = page2.links[0]
                    assert edge.source.id == first.id and edge.source.kind == "observation"
                    assert edge.target.id == second.id and edge.properties["native_revision"] is None
                    assert edge.properties["evidence_source"] == "SIMULATED_PROVISIONAL"
                    view = fetch_provenance(second)
                    assert {link.relationship for link in view.links} == {"MEASURED", "CONTRIBUTED_TO", "RESOLVED"}
                    plan = next(link for link in view.links if link.relationship == "RESOLVED")
                    assert plan.source.kind == "actuator" and plan.evidence == "planned resolution"
                    assert all(link.evidence == "recorded relationship" for link in view.links if link is not plan)
                # Reader closure neither releases source nor resubmits either Job.
                restored_second = owner.restore_handle(second_handle)
                assert len(fetch_provenance(restored_second).links) == 3
                assert len(fetch_provenance(first).links) == 2
                assert len(retained.links) == 1 and retained.partial  # Immutable old local evidence, not convergence.
                assert fetch_provenance(first, offset=999).links == ()
                assert fetch_provenance(first, offset=999).partial  # Empty page is not complete lineage proof.
                with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as denied:
                    foreign = Context(denied, {"id": context.id, "serviceId": ready["service_ids"]["runtime"]})
                    with pytest.raises(AuthorizationError):
                        denied.runtime.query_knowledge_graph({"resultType": "LINK", "limit": 64}, foreign)
            finally:
                context.release(); session.release()
        state = control(ready)
        assert not any(state["counts"].values())
        assert sum(row["path"].endswith("/submit") for row in state["trace"]) == 2


def test_native_async_provenance_fixture_is_root_scoped_and_never_composes_contexts():
    with launch("provenance-dependencies") as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                session = await client.create_session(); contexts = []
                try:
                    for _ in range(2): contexts.append(await session.create_context())
                    request = ObservationRequest("geography:Elevation in m", geometry=geometry())
                    first = await (await contexts[0].observe(request)).result(timeout=2)
                    other = await (await contexts[1].observe(request)).result(timeout=2)
                    views = await asyncio.gather(async_fetch_provenance(first), async_fetch_provenance(other))
                    assert all(len(view.links) == 1 and view.links[0].relationship == "MEASURED" for view in views)
                    second = await (await contexts[0].observe(request)).result(timeout=2)
                    view = await async_fetch_provenance(second)
                    assert len(view.links) == 3 and view.partial and "unknown" in view.consistency
                    assert not any(link.source.id == other.id and link.source.kind == "observation" for link in view.links)
                    assert len((await async_fetch_provenance(other)).links) == 1
                finally:
                    for context in contexts: await context.release()
                    await session.release()
        asyncio.run(run())
        assert not any(control(ready)["counts"].values())


@pytest.mark.parametrize("children", [None, [], [None], [{}] * 65,
    [{"source": None}], [{"source": {"id": True, "type": "OBSERVATION"}}],
    [{"source": {"id": -1, "type": "OBSERVATION"}}], [{"target": {"id": 1, "type": "UNKNOWN"}}]])
def test_malformed_or_unbounded_graph_children_refuse_explicitly(children):
    from klab_simulator.state import State
    state = State("provenance-dependencies"); state.contexts["s.c"] = {"owner": "alice"}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "Content-Type": "application/json"}
    body = json.dumps({"resultType": "LINK", "children": children}).encode()
    assert state.request("POST", "/runtime/api/v1/query", headers, body)[0] == 422
    assert not state.observations and not state.jobs


def test_empty_fixture_is_only_an_empty_page_not_a_native_lineage_claim():
    from klab_simulator.state import State
    state = State("provenance-dependencies"); state.contexts["s.c"] = {"owner": "alice"}
    status, result, _ = state.request("POST", "/runtime/api/v1/query",
        {"Authorization": "sim-token-alice", "klab-scope": "s.c", "Content-Type": "application/json"}, b'{"resultType":"LINK"}')
    assert status == 200 and result == []


def test_declared_edges_order_filter_and_typed_endpoints_in_process():
    from klab_simulator.state import State
    from klab_simulator.provenance_fixture import rows, matches, validate_children
    state = State("provenance-dependencies")
    state.observations = {("s.c", 2): {"urn": "second"}, ("other.c", 3): {"urn": "foreign"},
        ("s.c", 1): {"urn": "first"}}
    edges = rows(state, "s.c")
    assert [edge["relationship"] for edge in edges] == ["MEASURED", "MEASURED", "CONTRIBUTED_TO", "RESOLVED"]
    assert edges[2]["source"]["id"] == 1 and edges[2]["target"]["id"] == 2
    assert edges[2]["properties"]["native_revision"] is None
    for edge in edges:
        assert matches(edge, {})
        assert not matches(edge, {"relationship": "UNKNOWN"})
        for side, kind in (("target", "OBSERVATION"), ("source",
                "ACTIVITY" if edge["relationship"] == "MEASURED" else "ACTUATOR" if edge["relationship"] == "RESOLVED" else "OBSERVATION")):
            selector = {side: {"id": edge[side]["id"], "type": kind}}
            validate_children([selector])
            assert matches(edge, selector)
            assert not matches(edge, {side: {"id": edge[side]["id"], "type": "COHORT"}})
            assert not matches(edge, {side: {"id": 999, "type": kind}})
    validate_children([{}])
    # Other scenarios retain their pre-existing insertion order and measurement-only fixture.
    state.scenario = "elevation"
    assert [edge["target"]["id"] for edge in rows(state, "s.c")] == [2, 1]
