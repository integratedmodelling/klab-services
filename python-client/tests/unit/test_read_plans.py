"""SIMULATED_PROVISIONAL/JAVA-GAP-0006/0014: SDK-only reference wire and denial tests."""
import asyncio
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import Client, AsyncClient
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown
from klab_client.read_plans import (PROTOCOL, REVISION_KIND, _cells_digest, _json, _plan, _shard, _options,
    open_read_plan, read_plan_shard, release_read_plan, assemble_read_plan,
    async_open_read_plan, async_read_plan_shard, async_release_read_plan)


def manifest():
    values, valid = [0.0, -2.5, None, 7.0], [True, True, False, True]
    return {"protocol": PROTOCOL, "plan_id": "a" * 32, "epoch": "b" * 32,
        "revision": _cells_digest(values, valid), "revision_kind": REVISION_KIND,
        "observation_id": 1, "scope": "s.c", "runtime_id": "runtime", "dtype": "float64", "shape": [2, 2],
        "crs": "EPSG:3857", "transform": [100, 0, 0, 0, -100, 200], "order": "ROW_MAJOR_NORTH_TO_SOUTH",
        "units": "m", "fixture_origin": "SYNTHETIC_SIMULATOR", "shards": [
            {"index": n, "start": n * 2, "count": 2, "sha256": _cells_digest(values[n*2:n*2+2], valid[n*2:n*2+2])} for n in range(2)]}


def block(index=0):
    plan = manifest()
    values, valid = ([0.0, -2.5], [True, True]) if index == 0 else ([None, 7.0], [False, True])
    return {"protocol": PROTOCOL, "plan_id": plan["plan_id"], "epoch": plan["epoch"], "revision": plan["revision"],
        "index": index, "start": index * 2, "values": values, "valid": valid, "sha256": _cells_digest(values, valid)}


def bound(client):
    context = client._context({"id": "s.c", "serviceId": "runtime"})
    observation = observation_from_wire({"@CLASS": "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl",
        "id": 1, "urn": "s.c.1", "name": "synthetic", "empty": False,
        "observable": {"urn": "synthetic:Code", "semantics": {"urn": "synthetic:Code", "type": []}}}, context=context)
    return context, observation


def client_options():
    return {"agent_name": "alice", "runtime_service_id": "runtime"}


@pytest.mark.parametrize("native_async", [False, True])
def test_sdk_only_real_transport_open_all_shards_assemble_and_release(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "runtime"
        if request.method == "POST":
            assert request.url.path.endswith("/open")
            body = json.loads(request.content)
            assert body == {"protocol": PROTOCOL, "observation_id": 1, "shard_cells": 2, "lease_seconds": 30, "max_cells": 4}
            return httpx.Response(200, json=manifest())
        assert request.url.params["protocol"] == PROTOCOL and request.url.params["epoch"] == "b" * 32
        if request.method == "DELETE":
            return httpx.Response(200, json={"protocol": PROTOCOL, "released": True, "plan_id": "a" * 32, "epoch": "b" * 32})
        return httpx.Response(200, json=block(int(request.url.path.rsplit("/", 1)[1])))
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **client_options()) as client:
            _, observation = bound(client)
            plan = await async_open_read_plan(observation, shard_cells=2, max_cells=4, allow_experimental=True)
            shards = tuple([await async_read_plan_shard(plan, n, allow_experimental=True) for n in (0, 1)])
            data = assemble_read_plan(plan, shards)
            assert data.values == (0, -2.5, None, 7) and data.valid == (True, True, False, True)
            assert await async_release_read_plan(plan, allow_experimental=True)
    if native_async:
        asyncio.run(run())
    else:
        with Client("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **client_options()) as client:
            _, observation = bound(client)
            plan = open_read_plan(observation, shard_cells=2, max_cells=4, allow_experimental=True)
            shards = tuple(read_plan_shard(plan, n, allow_experimental=True) for n in (0, 1))
            assert assemble_read_plan(plan, shards).values == (0, -2.5, None, 7)
            assert release_read_plan(plan, allow_experimental=True)
    assert len(calls) == 4


@pytest.mark.parametrize("raw", [None, b'\xff', b'{}{}', b'{"x":1,"x":2}', b'NaN', b'x' * 65537])
def test_bad_wire_never_establishes_a_grant(raw):
    with pytest.raises(ProtocolError): _json(raw)


@pytest.mark.parametrize("change", [{"extra": 1}, {"protocol": "native"}, {"plan_id": "bad"}, {"epoch": "bad"},
    {"revision": "bad"}, {"revision_kind": "COMMITTED"}, {"fixture_origin": "JAVA_SCIENCE"}, {"observation_id": True},
    {"observation_id": 2}, {"scope": "other.scope"}, {"runtime_id": "foreign"}, {"dtype": "int64"}, {"shape": [0, 2]},
    {"shape": [True, 2]}, {"shape": [2]}, {"crs": ""}, {"order": "UNKNOWN"}, {"transform": [0] * 6},
    {"transform": [100, 1, 0, 0, -100, 200]}, {"transform": [100, 0, 0, 0, -100, float("nan")]},
    {"units": 1}, {"shards": []}, {"shards": None}])
def test_untrusted_manifest_identity_descriptor_and_layout_refuse(change):
    with Client("https://example.test/runtime", "synthetic", **client_options()) as client:
        context, observation = bound(client)
        from klab_client.handles import to_handle
        with pytest.raises(ProtocolError): _plan({**manifest(), **change}, context, to_handle(observation), 2, 4)


@pytest.mark.parametrize("change", [{"index": 1}, {"start": 1}, {"count": 0}, {"count": 1}, {"sha256": "bad"}, {"extra": 1}])
def test_overlapping_or_partial_shard_specs_refuse(change):
    with Client("https://example.test/runtime", "synthetic", **client_options()) as client:
        context, observation = bound(client)
        from klab_client.handles import to_handle
        value = manifest(); value["shards"][0].update(change)
        with pytest.raises(ProtocolError): _plan(value, context, to_handle(observation), 2, 4)


@pytest.mark.parametrize("change", [{"protocol": "native"}, {"plan_id": "c" * 32}, {"epoch": "c" * 32},
    {"revision": "c" * 64}, {"index": True}, {"index": 1}, {"start": 1}, {"values": []}, {"valid": []},
    {"valid": [1, True]}, {"values": [True, -2.5]}, {"values": [2**54, -2.5]}, {"values": [float("inf"), -2.5]},
    {"values": [0, -2.5], "valid": [False, True]}, {"sha256": "bad"}, {"extra": 1}])
def test_changed_shard_identity_cells_masks_and_hashes_never_form_partial_success(change):
    with Client("https://example.test/runtime", "synthetic", **client_options()) as client:
        context, observation = bound(client)
        from klab_client.handles import to_handle
        plan = _plan(manifest(), context, to_handle(observation), 2, 4)
        with pytest.raises(ProtocolError): _shard(plan, 0, {**block(), **change})


def test_preflight_binding_budgets_layout_and_complete_assembly_fail_closed():
    from klab_client.handles import to_handle
    from klab_client.read_plans import _binding
    with Client("https://example.test/runtime", "synthetic", **client_options()) as client:
        context, observation = bound(client)
        source = to_handle(observation)
        plan = _plan(manifest(), context, source, 2, 4)
        for target in (None, object()):
            with pytest.raises(InvalidRequestError): open_read_plan(target, allow_experimental=True)
        with pytest.raises(InvalidRequestError): open_read_plan(observation)
        with pytest.raises(InvalidRequestError): _binding(object(), True)
        for options in ({"shard_cells": True}, {"shard_cells": 0}, {"lease_seconds": 61}, {"max_cells": 0}):
            with pytest.raises(InvalidRequestError): open_read_plan(observation, allow_experimental=True, **options)
        with pytest.raises(InvalidRequestError): _options(None, True)
        with pytest.raises(InvalidRequestError): _options(replace(plan, source=replace(source, runtime_url="https://foreign.test")), True)
        value = manifest(); value["shards"] = value["shards"][:1]
        with pytest.raises(ProtocolError): _plan(value, context, source, 2, 4)
        with pytest.raises(ProtocolError): _plan(manifest(), context, source, 2, 3)
        shards = tuple(_shard(plan, n, block(n)) for n in (0, 1))
        for target, parts in ((None, shards), (plan, list(shards)), (plan, shards[:1])):
            with pytest.raises(InvalidRequestError): assemble_read_plan(target, parts)
        with pytest.raises(ProtocolError): assemble_read_plan(plan, (None, shards[1]))
        bad = replace(plan, revision="c" * 64)
        with pytest.raises(ProtocolError): assemble_read_plan(bad, tuple(replace(s, revision=bad.revision) for s in shards))
        for index in (True, -1, 2):
            with pytest.raises(InvalidRequestError): read_plan_shard(plan, index, allow_experimental=True)
            with pytest.raises(InvalidRequestError): asyncio.run(async_read_plan_shard(plan, index, allow_experimental=True))
        with pytest.raises(InvalidRequestError): asyncio.run(async_open_read_plan(observation, allow_experimental=True))
        with pytest.raises(InvalidRequestError): asyncio.run(async_read_plan_shard(plan, 0, allow_experimental=True))
        with pytest.raises(InvalidRequestError): asyncio.run(async_release_read_plan(plan, allow_experimental=True))
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", **client_options()) as client:
            context, observation = bound(client)
            plan = _plan(manifest(), context, to_handle(observation), 2, 4)
            with pytest.raises(InvalidRequestError): open_read_plan(observation, allow_experimental=True)
            with pytest.raises(InvalidRequestError): read_plan_shard(plan, 0, allow_experimental=True)
            with pytest.raises(InvalidRequestError): release_read_plan(plan, allow_experimental=True)
    asyncio.run(run())


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("operation", ["open", "release"])
@pytest.mark.parametrize("failure", ["disconnect", "unconfirmed"])
def test_mutating_grant_operations_never_retry_unknown_outcomes(native_async, operation, failure):
    from klab_client.handles import to_handle
    calls = []
    def handler(request):
        calls.append(request)
        if failure == "disconnect":
            raise httpx.ReadError("Synthetic lost acknowledgement", request=request)
        return httpx.Response(200, json={})
    expected = SubmissionOutcomeUnknown if failure == "disconnect" else ProtocolError
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **client_options()) as client:
            context, observation = bound(client)
            plan = _plan(manifest(), context, to_handle(observation), 2, 4)
            with pytest.raises(expected):
                if operation == "open": await async_open_read_plan(observation, allow_experimental=True)
                else: await async_release_read_plan(plan, allow_experimental=True)
    if native_async:
        asyncio.run(run())
    else:
        with Client("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **client_options()) as client:
            context, observation = bound(client)
            plan = _plan(manifest(), context, to_handle(observation), 2, 4)
            with pytest.raises(expected):
                if operation == "open": open_read_plan(observation, allow_experimental=True)
                else: release_read_plan(plan, allow_experimental=True)
    assert len(calls) == 1


@pytest.mark.parametrize("native_async", [False, True])
def test_release_requires_exact_boolean_not_numeric_confirmation(native_async):
    from klab_client.handles import to_handle
    def handler(_):
        return httpx.Response(200, json={"protocol": PROTOCOL, "released": 1, "plan_id": "a" * 32, "epoch": "b" * 32})
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **client_options()) as client:
            context, observation = bound(client)
            plan = _plan(manifest(), context, to_handle(observation), 2, 4)
            with pytest.raises(ProtocolError): await async_release_read_plan(plan, allow_experimental=True)
    if native_async:
        asyncio.run(run())
    else:
        with Client("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **client_options()) as client:
            context, observation = bound(client)
            plan = _plan(manifest(), context, to_handle(observation), 2, 4)
            with pytest.raises(ProtocolError): release_read_plan(plan, allow_experimental=True)
