"""Explicit awaited deferred registration; no broker or scientific execution."""
import klab_client
import asyncio
from dataclasses import FrozenInstanceError
import hashlib
import json

import httpx
import pytest

from klab_client import AsyncAgentExecutions, AgentExecutions, AsyncClient, AsyncContext, Endpoint
from klab_client.agents import AGENT_CLASS, BEHAVIOR_CLASS
from klab_client.errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown, UnsupportedOperationError
from test_agent_execution import scenario
from test_agent_previews import SOURCE


def factory(context, **kwargs):
    return AsyncAgentExecutions(context, **(dict(principal_key="test-principal", runtime_incarnation="test-incarnation") | kwargs))


def test_native_awaited_deferred_registration_is_public():
    assert hasattr(klab_client, "AsyncAgentExecutions")


def test_awaited_registration_preserves_exact_flags_source_identity_and_frozen_receipt():
    async def run():
        client, context, calls = scenario(async_mode=True, result_change={"metadata": {"nested": [1]}})
        async with client:
            creator = factory(context)
            agent = await creator.create(SOURCE, allow_execute=True)
            receipt = agent.receipt
            assert receipt.binding.agent_urn == "registered-agent" and receipt.binding.scope == "s.c"
            assert receipt.source_sha256 == hashlib.sha256(SOURCE.encode()).hexdigest()
            assert receipt.parser_source_sha256 == receipt.source_sha256
            assert receipt.messaging_connected and not receipt.parser_edge_whitespace_trimmed
            assert agent.view is None
            with pytest.raises(FrozenInstanceError):
                receipt.behavior_urn = "changed"
            with pytest.raises(TypeError):
                receipt.raw["metadata"]["nested"] = ()
            count = len(calls)
            with pytest.raises(InvalidRequestError):
                await creator.create(SOURCE, allow_execute=True)
            assert len(calls) == count
            agent.close()  # local only; broker control remains separately explicit synchronous I/O
        posts = [r for r in calls if r.method == "POST"]
        assert len(posts) == 2 and posts[0].content == SOURCE.encode()
        body = json.loads(posts[1].content)
        assert {k: v for k, v in body.items() if k != "behavior"} == {"compileOnly": False,
            "reportJavaCode": False, "doNotStart": True, "doNotBindSession": True,
            "doNotBindObservation": True, "observationId": -1}
        assert posts[1].headers["klab-scope"] == "s.c" and posts[1].headers["klab-service"] == "runtime-id"
        assert not any("release" in r.url.path or "createSession" in r.url.path for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("options,source,consent", [({}, None, True), ({}, "\ud800", True), ({}, " ", True),
    ({}, "runtime-secret", True), ({}, SOURCE, False), ({}, SOURCE, 1),
    ({"max_source_bytes": 1}, SOURCE, True), ({"max_source_bytes": 0}, SOURCE, True),
    ({"principal_key": None}, SOURCE, True), ({"runtime_incarnation": ""}, SOURCE, True)])
def test_invalid_registration_intake_never_reads_or_writes(options, source, consent):
    async def run():
        client, context, calls = scenario(async_mode=True)
        async with client:
            with pytest.raises(InvalidRequestError):
                await factory(context, **options).create(source, allow_execute=consent)
        assert not calls
    asyncio.run(run())


def test_matching_async_root_context_required_before_io():
    with pytest.raises(InvalidRequestError):
        factory(None)
    client, context, calls = scenario()
    with client:
        with pytest.raises(InvalidRequestError):
            factory(context)
    assert not calls
    async def run():
        client, context, calls = scenario(async_mode=True)
        async with client:
            with pytest.raises(InvalidRequestError):
                AgentExecutions(context, principal_key="test", runtime_incarnation="test")
            context.context_id = "s.c.7"
            with pytest.raises(InvalidRequestError):
                factory(context)
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("changes,error", [({"sourceCode": "changed"}, ProtocolError),
    ({"serviceId": "other"}, ProtocolError), ({"behaviorType": "APP"}, UnsupportedOperationError)])
def test_parser_errors_before_registration(changes, error):
    async def run():
        client, context, calls = scenario(async_mode=True, parsed_change=changes)
        async with client:
            with pytest.raises(error):
                await factory(context).create(SOURCE, allow_execute=True)
        assert not any(r.url.path == "/api/v1/instantiate/agent" for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("changes", [{"alive": True}, {"urn": None}, {"scopeId": "other"},
    {"startedAt": -1.0}, {"messagingConnected": "true"}, {"metadata": "runtime-secret"}])
def test_unconfirmed_registration_has_no_replay(changes):
    async def run():
        client, context, calls = scenario(async_mode=True, result_change=changes)
        async with client:
            creator = factory(context)
            with pytest.raises(SubmissionOutcomeUnknown):
                await creator.create(SOURCE, allow_execute=True)
            count = len(calls)
            with pytest.raises(InvalidRequestError):
                await creator.create(SOURCE, allow_execute=True)
            assert len(calls) == count
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == 1
    asyncio.run(run())


def test_lost_registration_reply_is_not_resent():
    async def run():
        client, context, calls = scenario(async_mode=True, drop="runtime")
        async with client:
            creator = factory(context)
            with pytest.raises(SubmissionOutcomeUnknown):
                await creator.create(SOURCE, allow_execute=True)
            with pytest.raises(InvalidRequestError):
                await creator.create(SOURCE, allow_execute=True)
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == 1
    asyncio.run(run())


@pytest.mark.parametrize("phase,error", [(1, ProtocolError), (2, ProtocolError), (3, SubmissionOutcomeUnknown)])
def test_runtime_drift_at_every_phase_is_not_rebound(phase, error):
    async def run():
        client, context, calls = scenario(async_mode=True, identity_change=("runtime", phase))
        async with client:
            with pytest.raises(error):
                await factory(context).create(SOURCE, allow_execute=True)
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == int(phase == 3)
    asyncio.run(run())


def controlled(stage, *, mutate=False):
    calls, gate = [], asyncio.Event()
    context = None
    counts = {"resources": 0, "runtime": 0}
    async def handler(request):
        calls.append(request)
        service = request.url.host.split(".")[0]
        if request.url.path == "/public/capabilities":
            counts[service] += 1
            if service == "runtime" and counts[service] == stage:
                gate.set()
                if mutate:
                    context.context_id = "s.c.7"
                else:
                    await asyncio.Event().wait()
            return httpx.Response(200, json={"serviceId": service + "-id"})
        if service == "resources":
            return httpx.Response(200, json={"@CLASS": BEHAVIOR_CLASS, "urn": "python.preview",
                "sourceCode": SOURCE, "behaviorType": "BEHAVIOR", "serviceId": "resources-id"})
        if stage == 4:
            gate.set()
            await asyncio.Event().wait()
        return httpx.Response(200, json={"@CLASS": AGENT_CLASS, "behaviorUrn": "python.preview",
            "viable": True, "alive": False, "startDeferred": True, "messagingConnected": False,
            "scopeId": "s.c", "observationId": -1, "startedAt": -1, "urn": "registered-agent"})
    client = AsyncClient("https://runtime.invalid", runtime_service_id="runtime-id",
        resources=Endpoint("https://resources.invalid"), http_transport=httpx.MockTransport(handler))
    context = AsyncContext(client, {"id": "s.c", "serviceId": "runtime-id"})
    return client, context, calls, gate


@pytest.mark.parametrize("stage", [2, 3])
def test_context_drift_across_await_never_sends_stale_registration_or_confirms_success(stage):
    async def run():
        client, context, calls, _ = controlled(stage, mutate=True)
        async with client:
            with pytest.raises(ProtocolError if stage == 2 else SubmissionOutcomeUnknown):
                await factory(context).create(SOURCE, allow_execute=True)
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == int(stage == 3)
    asyncio.run(run())


@pytest.mark.parametrize("stage", [3, 4])
def test_cancel_after_admission_never_allows_a_second_registration(stage):
    async def run():
        client, context, calls, gate = controlled(stage)
        async with client:
            creator = factory(context)
            task = asyncio.create_task(creator.create(SOURCE, allow_execute=True))
            await asyncio.wait_for(gate.wait(), timeout=2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
            count = len(calls)
            with pytest.raises(InvalidRequestError):
                await creator.create(SOURCE, allow_execute=True)
            assert len(calls) == count
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == 1
        assert not any("release" in r.url.path or "cancel" in r.url.path for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("stage", [1, 2])
def test_cancel_before_admission_keeps_explicit_future_attempt_available_without_auto_replay(stage):
    async def run():
        client, context, calls, gate = controlled(stage)
        async with client:
            creator = factory(context)
            task = asyncio.create_task(creator.create(SOURCE, allow_execute=True))
            await asyncio.wait_for(gate.wait(), timeout=2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
            assert not any(r.url.path == "/api/v1/instantiate/agent" for r in calls)
            agent = await creator.create(SOURCE, allow_execute=True)  # explicit caller action, not replay
            assert agent.receipt.binding.agent_urn == "registered-agent"
            agent.close()
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == 1
    asyncio.run(run())


def test_concurrent_registration_waiter_cannot_duplicate_an_admitted_write():
    async def run():
        client, context, calls, gate = controlled(4)
        async with client:
            creator = factory(context)
            first = asyncio.create_task(creator.create(SOURCE, allow_execute=True))
            await asyncio.wait_for(gate.wait(), timeout=2)
            second = asyncio.create_task(creator.create(SOURCE, allow_execute=True))
            first.cancel()
            with pytest.raises(asyncio.CancelledError):
                await first
            with pytest.raises(InvalidRequestError):
                await second
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == 1
    asyncio.run(run())
