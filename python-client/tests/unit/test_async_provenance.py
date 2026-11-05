"""Native async recorded-neighborhood queries; mocked links are not scientific lineage."""
import asyncio
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, async_fetch_provenance, fetch_provenance
from klab_client.dto import observation_from_wire
from klab_client.handles import HandleIdentityError
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError)
from test_provenance import ACT, OBS, link, payload, setup as sync_setup


def setup(rows=None, *, membership=None, status=200):
    calls = []
    async def handler(request):
        query = json.loads(request.content)
        calls.append((request, query))
        assert request.url.path == "/runtime/api/v1/query" and request.method == "POST"
        assert request.headers["authorization"] == "issued-secret"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        if query["resultType"] == "OBSERVATION":
            return httpx.Response(200, json=[payload()] if membership is None else membership)
        return httpx.Response(status, json=[link()] if rows is None else rows)
    api = AsyncClient("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
        http_transport=httpx.MockTransport(handler))
    observation = observation_from_wire(payload(), AsyncContext(api, {"id": "s.c", "serviceId": "home"}))
    return api, observation, calls


def test_native_async_view_and_exact_queries_match_sync_except_fetch_time():
    sync, observation, expected_calls = sync_setup()
    with sync:
        expected = fetch_provenance(observation)
    async def run():
        api, observation, calls = setup()
        async with api:
            view = await async_fetch_provenance(observation)
        assert replace(view, fetched_at=expected.fetched_at) == expected
        assert [q for _, q in calls] == [q for _, q in expected_calls]
        assert view.partial and view.consistency.startswith("unknown:") and view.fetched_at.tzinfo
        with pytest.raises(TypeError):
            view.links[0].properties["support"] = "changed"
    asyncio.run(run())


@pytest.mark.parametrize("relationship", ["MEASURED", "RESOLVED", "CONTEXTUALIZED_BY"])
def test_pagination_duplicates_empty_pages_and_plan_execution_distinction(relationship):
    async def run():
        api, observation, calls = setup([link(relationship=relationship)] * 3)
        async with api:
            view = await async_fetch_provenance(observation, limit=2, offset=3)
            assert len(view.links) == 2 and view.next_offset == 5 and view.offset == 3
            assert view.links[0].evidence == ("recorded relationship" if relationship == "MEASURED" else "planned resolution")
            await async_fetch_provenance(observation, limit=2, offset=5)
            assert len(calls) == 4 and calls[-1][1]["offset"] == 5
        api, observation, _ = setup([])
        async with api:
            view = await async_fetch_provenance(observation)
            assert view.links == () and view.partial and view.next_offset is None
    asyncio.run(run())


@pytest.mark.parametrize("membership,error", [([], MissingAssetError), ({}, ProtocolError),
    ([payload(), payload()], ProtocolError), ([payload() | {"id": 43}], HandleIdentityError),
    ([payload() | {"urn": "s.c.different"}], ProtocolError)])
def test_membership_refusal_precedes_link_query(membership, error):
    async def run():
        api, observation, calls = setup(membership=membership)
        async with api:
            with pytest.raises(error):
                await async_fetch_provenance(observation)
        assert len(calls) == 1
    asyncio.run(run())


@pytest.mark.parametrize("rows", [{}, [None], [link(relationship="UNKNOWN")], [link(sequence=True)],
    [link(properties=[])], [link(source={})], [link(target={"@CLASS": OBS, "id": 43})],
    [link(target={"@CLASS": OBS, "id": 42, "urn": "different"})],
    [link(source={"@CLASS": ACT, "id": 8, "type": "FUTURE", "outcome": "SUCCESS"})]])
def test_native_async_malformed_or_unrelated_links_are_not_empty_success(rows):
    async def run():
        api, observation, _ = setup(rows)
        async with api:
            with pytest.raises(ProtocolError):
                await async_fetch_provenance(observation)
    asyncio.run(run())


@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (404, MissingAssetError), (422, UnsupportedOperationError), (503, ServerError)])
def test_recorded_query_http_errors_remain_typed(status, error):
    async def run():
        api, observation, calls = setup(status=status)
        async with api:
            with pytest.raises(error):
                await async_fetch_provenance(observation)
        assert len(calls) == 2
    asyncio.run(run())


@pytest.mark.parametrize("options", [{"limit": 0}, {"limit": True}, {"limit": 129}, {"offset": -1},
    {"offset": 2**31}, {"max_response_bytes": 0}, {"max_response_bytes": True}])
def test_bounds_fail_locally(options):
    async def run():
        api, observation, calls = setup()
        async with api:
            with pytest.raises(InvalidRequestError):
                await async_fetch_provenance(observation, **options)
        assert not calls
    asyncio.run(run())


def test_redaction_byte_limits_type_refusal_and_detached_observation():
    sync, observation, _ = sync_setup()
    with sync:
        with pytest.raises(InvalidRequestError):
            asyncio.run(async_fetch_provenance(observation))
    async def run():
        api, observation, calls = setup([link(properties={"token": "private", "safe": "issued-secret"})])
        async with api:
            with pytest.raises(InvalidRequestError):
                fetch_provenance(observation)
            assert not calls
            view = await async_fetch_provenance(observation)
            assert view.links[0].properties == {"safe": "[REDACTED]"}
            with pytest.raises(ProtocolError):
                await async_fetch_provenance(observation, max_response_bytes=8)
            observation.metadata["im:storage-binding-source"] = 42
            before = len(calls)
            with pytest.raises(UnsupportedOperationError):
                await async_fetch_provenance(observation)
            assert len(calls) == before
    asyncio.run(run())


def test_native_cancellation_only_stops_local_membership_wait_without_remote_cancel():
    async def run():
        entered, calls = asyncio.Event(), []
        async def handler(request):
            calls.append(request.url.path)
            entered.set()
            await asyncio.Event().wait()
        async with AsyncClient("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
            http_transport=httpx.MockTransport(handler)) as api:
            observation = observation_from_wire(payload(), AsyncContext(api, {"id": "s.c", "serviceId": "home"}))
            task = asyncio.create_task(async_fetch_provenance(observation))
            await asyncio.wait_for(entered.wait(), 1)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert calls == ["/runtime/api/v1/query"]
    asyncio.run(run())
