"""Native scoped activity reads and plan DTOs, not durable root commit or scientific proof."""
import asyncio
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, async_fetch_execution_evidence, fetch_execution_evidence
from klab_client.dto import observation_from_wire
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    ProtocolError, ServerError, UnsupportedOperationError)
from test_execution_evidence import ACT, OBS, CHART, activity, link, observation_payload, setup as sync_setup


def setup(*, rows=None, links=None, status=200):
    calls = []
    async def handler(request):
        calls.append(request)
        assert request.url.path == "/runtime/api/v1/query" and request.method == "POST"
        assert request.headers["authorization"] == "issued-secret"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        query = json.loads(request.content)
        if query["resultType"] == "OBSERVATION":
            return httpx.Response(200, json=[observation_payload()])
        if query["resultType"] == "LINK":
            return httpx.Response(200, json=[link()] if links is None else links)
        assert query["resultType"] == "ACTIVITY" and query["limit"] == 2
        return httpx.Response(status, content=json.dumps([activity(metadata={"delegate": {"im:dataflow-graph": CHART}},
            observationUrn="s.c.42")] if rows is None else rows))
    api = AsyncClient("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
        http_transport=httpx.MockTransport(handler))
    observation = observation_from_wire(observation_payload(), AsyncContext(api, {"id": "s.c", "serviceId": "home"}))
    return api, observation, calls


def test_native_async_full_view_and_query_parity_keep_failure_plan_and_unknown_commit():
    sync, observation, expected_calls = sync_setup()
    with sync:
        expected = fetch_execution_evidence(observation)
    async def run():
        api, observation, calls = setup()
        async with api:
            result = await async_fetch_execution_evidence(observation)
        assert replace(result, provenance=replace(result.provenance, fetched_at=expected.provenance.fetched_at)) == expected
        assert [json.loads(r.content) for r in calls] == [json.loads(r.content) for r in expected_calls]
        assert result.activities[0].outcome == "FAILURE" and result.partial and result.provenance.partial
        assert result.activities[0].charts[0].phase == "contextualization plan"
        assert "not durable root commit" in result.summary() and "FAILURE" in result._repr_html_()
        with pytest.raises(TypeError):
            result.activities[0].raw["type"] = "SIMULATION"
    asyncio.run(run())


def test_missing_metadata_empty_neighborhood_budgets_and_deduplication_are_explicit():
    async def run():
        for options, count in [({"rows": []}, 3), ({"links": []}, 2),
            ({"links": [link(), link()]}, 3), ({"links": [link(8), link(9)]}, 3)]:
            api, observation, calls = setup(**options)
            async with api:
                result = await async_fetch_execution_evidence(observation, activity_limit=1)
                assert result.partial and len(calls) == count
                if options == {"rows": []}:
                    assert len(result.activities[0].missing) == 3 and result.activities[0].charts == ()
                if options == {"links": []}:
                    assert "not proof of no computation" in result.diagnostics[0]
                if options == {"links": [link(8), link(9)]}:
                    assert "budget reached" in result.diagnostics[0]
    asyncio.run(run())


@pytest.mark.parametrize("rows", [{}, [activity(), activity()], [activity(9)], [activity(urn="foreign")],
    [{"@CLASS": OBS, "id": 8}], [activity(observationUrn="other")], [activity(metadata=[])],
    [activity(metadata={"delegate": []})], [activity(metadata={"im:dataflow-graph": {"root": None}})]])
def test_activity_identity_association_metadata_and_plan_errors_are_refused(rows):
    async def run():
        api, observation, _ = setup(rows=rows)
        async with api:
            with pytest.raises(ProtocolError):
                await async_fetch_execution_evidence(observation)
    asyncio.run(run())


@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (422, UnsupportedOperationError), (503, ServerError)])
def test_native_scoped_activity_errors_are_not_missing_plan_success(status, error):
    async def run():
        api, observation, calls = setup(status=status)
        async with api:
            with pytest.raises(error):
                await async_fetch_execution_evidence(observation)
        assert len(calls) == 3
    asyncio.run(run())


@pytest.mark.parametrize("options", [{"activity_limit": 0}, {"activity_limit": True}, {"activity_limit": 65},
    {"max_nodes": 0}, {"max_links": 8193}])
def test_activity_and_chart_budget_options_fail_before_network(options):
    async def run():
        api, observation, calls = setup()
        async with api:
            with pytest.raises(InvalidRequestError):
                await async_fetch_execution_evidence(observation, **options)
        assert not calls
    asyncio.run(run())


def test_plan_budgets_redaction_and_conflicting_activity_identity():
    async def run():
        api, observation, _ = setup()
        async with api:
            with pytest.raises(ProtocolError):
                await async_fetch_execution_evidence(observation, max_nodes=1)
        api, observation, _ = setup(rows=[activity(metadata={"token": "private", "safe": "issued-secret",
            "im:resolution-graph": CHART, "im:dataflow-graph": CHART})])
        async with api:
            result = await async_fetch_execution_evidence(observation)
            assert len(result.activities[0].charts) == 2 and result.activities[0].missing == ()
            assert "token" not in result.activities[0].raw["metadata"]
            assert result.activities[0].raw["metadata"]["safe"] == "[REDACTED]"
        api, observation, calls = setup(links=[link(), link() | {"source": activity(urn="conflicting")}])
        async with api:
            with pytest.raises(ProtocolError):
                await async_fetch_execution_evidence(observation)
        assert len(calls) == 2
    asyncio.run(run())


def test_cross_facade_types_and_native_activity_cancellation_never_resolve_or_cancel_work():
    sync, observation, _ = sync_setup()
    with sync:
        with pytest.raises(InvalidRequestError):
            asyncio.run(async_fetch_execution_evidence(observation))
    async def run():
        api, observation, calls = setup()
        async with api:
            with pytest.raises(InvalidRequestError):
                fetch_execution_evidence(observation)
            assert not calls
            entered = asyncio.Event()
            old = api.transport._http._transport
            async def handler(request):
                if json.loads(request.content)["resultType"] != "ACTIVITY":
                    return await old.handle_async_request(request)
                calls.append(request)
                entered.set()
                await asyncio.Event().wait()
            api.transport._http._transport = httpx.MockTransport(handler)
            task = asyncio.create_task(async_fetch_execution_evidence(observation))
            await asyncio.wait_for(entered.wait(), 1)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
            assert len(calls) == 3 and all(r.url.path.endswith("/api/v1/query") for r in calls)
    asyncio.run(run())


def test_actual_java_guard_retains_missing_diagrams_and_rejects_commit_or_identity_claims():
    from workflows.async_execution_evidence import validate_evidence
    sync, observation, _ = sync_setup(rows=[activity()])
    with sync:
        result = fetch_execution_evidence(observation)
    reference = result.provenance.observation
    rows = validate_evidence(result, reference)
    assert rows[0]["chart_phases"] == [] and len(rows[0]["missing"]) == 2
    for changed in (replace(result, partial=False), replace(result, durable_root_commit="committed"),
        replace(result, activities=()), replace(result, provenance=replace(result.provenance, consistency="pinned"))):
        with pytest.raises(AssertionError):
            validate_evidence(changed, reference)
