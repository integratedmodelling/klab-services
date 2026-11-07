"""Public collection/endpoint contracts, independent wire data (no simulator import)."""
import asyncio
from copy import deepcopy
from dataclasses import FrozenInstanceError, replace
import json

import httpx
import pytest

from klab_client import (Client, AsyncClient, Context, AsyncContext, Endpoint,
    Handle, HandleFormatError, ObservationPage, RelationshipSelection, to_handle)
from klab_client.dto import observation_from_wire
from klab_client.errors import ConfigurationError, InvalidRequestError, ProtocolError


def wire(identity=1):
    return {"@CLASS": "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl",
        "id": identity, "urn": "s.c." + str(identity), "empty": False, "notifications": [],
        "observable": {"urn": "synthetic:Entity", "contextualization": "INSTANTIATION",
            "semantics": {"urn": "synthetic:Entity", "type": ["SUBJECT", "COUNTABLE"]}},
        "geometry": {"scalar": True, "dimensions": []}, "metadata": {}}


def client(handler, native_async=False, **kwargs):
    constructor = AsyncClient if native_async else Client
    return constructor("https://runtime.test", "current-secret", runtime_service_id="runtime-id",
        agent_name="owner", http_transport=httpx.MockTransport(handler), **kwargs)


def root(api, token=None):
    cls = AsyncContext if isinstance(api, AsyncClient) else Context
    return cls(api, {"id": "s.c", "serviceId": "runtime-id"}, owned=True, token=token)


def offline(_): pytest.fail("Invalid/offline operation must not contact HTTP")


def test_selection_is_offline_immutable_origin_qualified_and_within_clears_it():
    with client(offline) as api:
        context = root(api, "s.c.8#9")
        a = observation_from_wire(wire(1), context); b = observation_from_wire(wire(2), context)
        selected = context.between(a, b)
        assert selected.relationship_selection == RelationshipSelection(1, 2, ("https://runtime.test", "s.c", "runtime-id"))
        assert selected.get_context_id() == "s.c.8#9" and context.relationship_selection is None
        with pytest.raises(FrozenInstanceError): selected.relationship_selection.source_id = 3
        assert selected.within(1).relationship_selection is None
        reference = to_handle(selected)
        assert reference.version == 2 and Handle.loads(reference.dumps()) == reference
        assert reference.source_observation_id == 1 and reference.target_observation_id == 2
        assert "secret" not in reference.dumps() and to_handle(context).version == 1


@pytest.mark.parametrize("source,target", [(True, 2), (0, 2), (-1, 2), (1.5, 2), ("1", 2), (2**63, 2), (1, 1), (1, None)])
def test_invalid_persisted_endpoint_ids_refuse_before_http(source, target):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): root(api).between(source, target)


def test_unbound_detached_foreign_root_origin_and_identity_refuse_before_http():
    with client(offline) as api, client(offline) as peer:
        context = root(api)
        for bound in (None, Context(api, {"id": "s.other"}), root(peer)):
            observation = observation_from_wire(wire(), bound)
            if bound is not None and bound.id == context.id:
                peer.transport.endpoints["runtime"] = Endpoint("https://different.test", "new-secret")
            with pytest.raises(InvalidRequestError): context.between(observation, 2)
        observation = observation_from_wire(wire(), context)
        observation.metadata["im:storage-binding-source"] = "detached"
        with pytest.raises(InvalidRequestError): context.between(observation, 2)
        api.runtime_service_id = None
        with pytest.raises(ConfigurationError): context.between(1, 2)


@pytest.mark.parametrize("origin", [None, (), (1, "s.c", None), ("https://runtime.test", 1, None), ("https://runtime.test", "s.c", 1)])
def test_relationship_origin_requires_typed_tuple(origin):
    with pytest.raises(InvalidRequestError): RelationshipSelection(1, 2, origin)


def test_changed_context_and_job_endpoint_bindings_refuse():
    from klab_client import Job
    with client(offline) as api:
        selected = root(api).between(1, 2)
        job = Job(api, 7, selected)
        selected._relationship_selection = replace(selected.relationship_selection, target_id=3)
        with pytest.raises(ProtocolError, match="binding changed"): job.status()
        api.runtime_service_id = "changed"
        with pytest.raises(InvalidRequestError): selected.observations()


@pytest.mark.parametrize("native_async", [False, True])
def test_public_page_exact_shape_binding_pagination_and_no_inferred_completeness(native_async):
    def handler(request):
        assert request.headers["Authorization"] == "current-secret"
        assert request.headers["klab-source-observation"] == "1" and request.headers["klab-target-observation"] == "2"
        value = json.loads(request.content)
        assert value == {"type": "QUERY", "resultType": "OBSERVATION", "source": {"type": "SCOPE"},
            "relationship": "HAS_CHILD", "depth": 1, "minimumDepth": 1, "offset": 0, "limit": 2}
        return httpx.Response(200, json=[wire(1), wire(2)])
    async def run():
        async with client(handler, True) as api:
            selected = root(api).between(1, 2)
            page = await selected.observations(limit=1)
            assert isinstance(page, ObservationPage) and page.next_offset == 1 and page.partial
            assert page.items[0]._context is selected and page.scope == "s.c"
    if native_async: asyncio.run(run())
    else:
        with client(handler) as api:
            selected = root(api).between(1, 2); page = selected.observations(limit=1)
            assert page.next_offset == 1 and page.items[0]._context is selected and page.partial


@pytest.mark.parametrize("kwargs", [{"limit": True}, {"limit": 0}, {"limit": 129}, {"offset": -1},
    {"offset": True}, {"offset": 2**31}, {"max_response_bytes": 0}, {"max_response_bytes": 2**25}])
def test_collection_budget_refusals_are_offline(kwargs):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): root(api).observations(**kwargs)


@pytest.mark.parametrize("rows", [None, {}, [wire(1), wire(2), wire(3)], [wire(1), wire(1)], [wire(0)], [wire(True)]])
def test_bad_collection_arrays_or_nonunique_unpersisted_ids_refuse(rows):
    with client(lambda _: httpx.Response(200, json=rows)) as api:
        with pytest.raises((InvalidRequestError, ProtocolError)): root(api).observations(limit=1)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("change", ["scope", "selection"])
def test_mutated_scope_or_selection_during_response_refuses(native_async, change):
    selected = None
    def handler(_):
        if change == "selection": selected._relationship_selection = replace(selected.relationship_selection, target_id=3)
        else: selected.context_id = "s.c.4"
        return httpx.Response(200, json=[wire()])
    async def run():
        nonlocal selected
        async with client(handler, True) as api:
            selected = root(api).between(1, 2)
            with pytest.raises(ProtocolError): await selected.observations()
    if native_async: asyncio.run(run())
    else:
        with client(handler) as api:
            selected = root(api).between(1, 2)
            with pytest.raises(ProtocolError): selected.observations()


def test_focus_query_and_empty_result_are_explicit():
    def handler(request):
        assert json.loads(request.content)["source"] == {"type": "OBSERVATION", "id": 8}
        return httpx.Response(200, json=[])
    with client(handler) as api:
        page = root(api, "s.c.8").observations()
        assert page.items == () and page.next_offset is None and page.partial


def test_version2_saved_reference_pairing_and_v1_bytes_stay_strict():
    with client(offline) as api:
        reference = to_handle(root(api).between(1, 2))
        values = reference.to_dict()
        for key, value in (("source_observation_id", 0), ("target_observation_id", True), ("target_observation_id", 1)):
            with pytest.raises(HandleFormatError): Handle.loads(json.dumps({**values, key: value}))
        v1 = to_handle(root(api))
        with pytest.raises(HandleFormatError): replace(v1, source_observation_id=1)
        assert "source_observation_id" not in v1.dumps()


@pytest.mark.parametrize("pair", [(1,), [1, 2], (1, True), (1, 1)])
def test_transport_refuses_invalid_header_pairs(pair):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError):
            api.transport.request("runtime", "POST", "/api/v1/query", scope="s.c", service_id="runtime-id", relationship=pair)


def test_selection_dto_invalid_identity_and_foreign_returned_observation_refuse():
    with client(offline) as api:
        selected = root(api).between(1, 2)
        selected._relationship_selection = object()
        with pytest.raises(InvalidRequestError): selected.relationship_selection
    payload = deepcopy(wire()); payload["urn"] = "other.root.1"
    with client(lambda _: httpx.Response(200, json=[payload])) as api:
        with pytest.raises(InvalidRequestError): root(api).observations()


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("kind", ["context", "observation", "job"])
def test_version2_restoration_uses_current_credentials_looks_up_both_endpoints_and_never_submits(native_async, kind):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["Authorization"] == "current-secret"
        path = request.url.path
        if path.endswith("/capabilities"): return httpx.Response(200, json={"serviceId": "runtime-id"})
        if path.endswith("/connect"): return httpx.Response(200, json={"id": "s.c", "serviceId": "runtime-id"})
        if path.endswith("/query"):
            identity = json.loads(request.content)["id"]
            return httpx.Response(200, json=[wire(identity)])
        if "/status/" in path: return httpx.Response(200, json={"status": "FINISHED"})
        assert "/retrieve/" in path
        return httpx.Response(200, json=wire(3))
    kwargs = {"observation_id": 3} if kind == "observation" else ({"job_id": 7, "service": "runtime",
        "service_id": "runtime-id", "service_url": "https://runtime.test", "result_kind": "observation"} if kind == "job" else {})
    reference = Handle(kind=kind, runtime_id="runtime-id", runtime_url="https://runtime.test", context_id="s.c", scope="s.c",
        version=2, source_observation_id=1, target_observation_id=2, **kwargs)
    async def run():
        async with client(handler, True) as api:
            restored = await api.restore_handle(reference)
            context = restored if kind == "context" else restored._context if kind == "observation" else restored.scope
            if kind == "job": assert (await restored.result(timeout=2)).id == 3
            assert context.relationship_selection == RelationshipSelection(1, 2, ("https://runtime.test", "s.c", "runtime-id"))
            assert not context.owned and to_handle(restored).version == 2
    if native_async: asyncio.run(run())
    else:
        with client(handler) as api:
            restored = api.restore_handle(reference)
            context = restored if kind == "context" else restored._context if kind == "observation" else restored.scope
            if kind == "job": assert restored.result(timeout=2).id == 3
            assert context.relationship_selection.source_id == 1 and not context.owned
            assert to_handle(restored).version == 2
    assert {json.loads(r.content)["id"] for r in calls if r.url.path.endswith("/query")} >= {1, 2}
    assert all(r.method == "GET" or r.url.path.endswith(("/connect", "/query")) for r in calls)
