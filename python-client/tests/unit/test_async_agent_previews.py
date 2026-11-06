"""RC-P4.5/CAP14 native awaited source preview, not execution or compilation."""
import klab_client
import asyncio
from dataclasses import FrozenInstanceError
import hashlib
import json

import pytest
import httpx

from klab_client import AsyncAgentPreviews, AgentPreviews, AsyncClient, Endpoint
from klab_client.agents import BEHAVIOR_CLASS
from klab_client.errors import InvalidRequestError, ProtocolError, ServerError, TransportError, UnsupportedOperationError
from test_agent_previews import SOURCE, setup


def test_native_awaited_agent_preview_is_public():
    assert hasattr(klab_client, "AsyncAgentPreviews")


def test_awaited_preview_retains_exact_source_receipt_and_nonexecution_flags():
    async def run():
        source = " \t\n" + SOURCE
        client, calls = setup(async_mode=True, parsed_change={"sourceCode": SOURCE.strip()},
                              result_change={"metadata": {"nested": [1]}})
        async with client:
            result = await AsyncAgentPreviews(client).preview(source)
        assert result.source_sha256 == hashlib.sha256(source.encode()).hexdigest()
        assert result.parser_source_sha256 == hashlib.sha256(SOURCE.strip().encode()).hexdigest()
        assert result.parser_edge_whitespace_trimmed and result.resources_id == "resources-id"
        assert result.runtime_id == "runtime-id" and result.java_code == "public class Preview {}"
        assert result.execution == "NOT_STARTED_NOT_REGISTERED_NOT_JAVA_COMPILED"
        with pytest.raises(FrozenInstanceError):
            result.behavior_urn = "changed"
        with pytest.raises(TypeError):
            result.raw["metadata"]["nested"] = ()
        posts = [r for r in calls if r.method == "POST"]
        assert len(posts) == 2 and posts[0].content == source.encode()
        assert json.loads(posts[1].content) == {"behavior": json.loads(posts[1].content)["behavior"],
            "compileOnly": True, "reportJavaCode": True, "doNotStart": True,
            "doNotBindSession": True, "doNotBindObservation": True, "observationId": -1}
        assert len(calls) == 8 and not hasattr(result, "start")
    asyncio.run(run())


@pytest.mark.parametrize("service,phase", [("resources", 2), ("runtime", 2), ("resources", 3), ("runtime", 3)])
def test_awaited_preview_identity_drift_has_no_replay(service, phase):
    async def run():
        client, calls = setup(async_mode=True, identity_change=(service, phase))
        async with client:
            with pytest.raises(ProtocolError):
                await AsyncAgentPreviews(client).preview(SOURCE)
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == phase - 2
    asyncio.run(run())


@pytest.mark.parametrize("service", ["resources", "runtime"])
def test_awaited_preview_lost_response_has_no_retry(service):
    async def run():
        client, calls = setup(async_mode=True, drop=service)
        async with client:
            with pytest.raises(TransportError):
                await AsyncAgentPreviews(client).preview(SOURCE)
        assert sum(r.method == "POST" and r.url.host.startswith(service) for r in calls) == 1
    asyncio.run(run())


@pytest.mark.parametrize("options,source", [({}, None), ({}, " "), ({}, "runtime-secret"), ({}, "bad\ud800source"),
    ({"max_source_bytes": 1}, SOURCE), ({"max_response_bytes": 1}, SOURCE),
    ({"max_source_bytes": 0}, SOURCE), ({"max_response_bytes": True}, SOURCE)])
def test_awaited_preview_invalid_intake_is_zero_http(options, source):
    async def run():
        client, calls = setup(async_mode=True)
        async with client:
            with pytest.raises(InvalidRequestError):
                await AsyncAgentPreviews(client, **options).preview(source)
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("options,error", [({"parsed_change": {"sourceCode": "changed"}}, ProtocolError),
    ({"parsed_change": {"behaviorType": "SCRIPT"}}, UnsupportedOperationError),
    ({"parsed_change": {"serviceId": "other"}}, ProtocolError),
    ({"parsed_change": {"notifications": [{"level": "ERROR", "message": "invalid"}]}}, ServerError),
    ({"result_change": {"alive": True}}, ProtocolError),
    ({"result_change": {"urn": "registered"}}, ProtocolError),
    ({"result_change": {"javaCode": "runtime-secret"}}, InvalidRequestError),
    ({"result_change": {"notifications": [{"level": "ERROR", "message": "invalid"}]}}, ServerError)])
def test_awaited_preview_fails_closed_on_parser_and_translation(options, error):
    async def run():
        client, calls = setup(async_mode=True, **options)
        async with client:
            with pytest.raises(error):
                await AsyncAgentPreviews(client).preview(SOURCE)
        assert sum(r.url.path == "/api/v1/instantiate/agent" for r in calls) == int("result_change" in options)
    asyncio.run(run())


def test_matching_facades_required_before_io():
    async def run():
        client, calls = setup(async_mode=True)
        async with client:
            with pytest.raises(InvalidRequestError):
                AgentPreviews(client)
        assert not calls
    asyncio.run(run())
    client, calls = setup()
    with client:
        with pytest.raises(InvalidRequestError):
            AsyncAgentPreviews(client)
    assert not calls


@pytest.mark.parametrize("stage", ["parse", "translate"])
def test_awaited_preview_cancellation_is_local_without_replay_or_cleanup_writes(stage):
    async def run():
        calls, dispatched = [], asyncio.Event()
        async def handler(request):
            calls.append(request)
            service = request.url.host.split(".")[0]
            if request.url.path == "/public/capabilities":
                return httpx.Response(200, json={"serviceId": service + "-id"})
            if (stage == "parse" and service == "resources") or service == "runtime":
                dispatched.set()
                await asyncio.Event().wait()
            return httpx.Response(200, json={"@CLASS": BEHAVIOR_CLASS, "urn": "python.preview",
                "serviceId": "resources-id", "sourceCode": SOURCE, "behaviorType": "BEHAVIOR"})
        async with AsyncClient("https://runtime.invalid", resources=Endpoint("https://resources.invalid"),
                               http_transport=httpx.MockTransport(handler)) as client:
            task = asyncio.create_task(AsyncAgentPreviews(client).preview(SOURCE))
            await asyncio.wait_for(dispatched.wait(), timeout=2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert sum(r.method == "POST" for r in calls) == (1 if stage == "parse" else 2)
        assert all(r.method in ("GET", "POST") for r in calls)
        assert not any("release" in r.url.path or "cancel" in r.url.path for r in calls)
    asyncio.run(run())
