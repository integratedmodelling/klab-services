"""SDK-only proposed synthetic lifecycle wire tests; no simulator import or execution."""
import asyncio
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import Client, AsyncClient
from klab_client.handles import to_handle
from klab_client.errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown
from klab_client.managed_agents import (PROTOCOL, EXECUTION, _json, _status, _options, _binding,
    _command_status, create_managed_reference, read_managed_reference, command_managed_reference,
    async_create_managed_reference, async_read_managed_reference, async_command_managed_reference)


def payload(**changes):
    return {"protocol": PROTOCOL, "execution": EXECUTION, "agent_id": "a" * 32, "epoch": "b" * 32,
        "scope": "s.c", "runtime_id": "runtime", "revision": 1, "state": "READY", "fixture": "idle",
        "cleanup_confirmed": False, **changes}


def wire(**changes):
    return json.dumps(payload(**changes)).encode()


def options():
    return {"agent_name": "alice", "runtime_service_id": "runtime"}


def bound(client):
    context = client._context({"id": "s.c", "serviceId": "runtime"})
    return context, _status(wire(), context, to_handle(context)).reference


@pytest.mark.parametrize("native_async", [False, True])
def test_real_sdk_only_transport_explicit_commands_terminal_cleanup_and_purge(native_async):
    responses = [payload(), payload(state="RUNNING", revision=2), payload(state="STOPPING", revision=3),
        payload(state="STOPPED", revision=4), payload(state="STOPPED", revision=5, cleanup_confirmed=True),
        {"protocol": PROTOCOL, "agent_id": "a" * 32, "epoch": "b" * 32, "purged": True}]
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "runtime"
        if request.method == "POST":
            body = json.loads(request.content)
            assert body["protocol"] == PROTOCOL
            if len(calls) == 1: assert body == {"protocol": PROTOCOL, "fixture": "idle"}
            else: assert body["epoch"] == "b" * 32 and body["expected_revision"] in (1, 2, 5)
        else:
            assert request.url.params["protocol"] == PROTOCOL and request.url.params["epoch"] == "b" * 32
        return httpx.Response(200, json=responses[len(calls) - 1])
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **options()) as client:
            context, _ = bound(client)
            created = await async_create_managed_reference(context, allow_experimental=True)
            ref = created.reference
            assert (await async_command_managed_reference(ref, "start", expected_revision=1, allow_experimental=True)).state == "RUNNING"
            assert (await async_command_managed_reference(ref, "stop", expected_revision=2, allow_experimental=True)).state == "STOPPING"
            terminal = await async_read_managed_reference(ref, allow_experimental=True)
            assert terminal.state == "STOPPED" and not terminal.cleanup_confirmed
            cleaned = await async_read_managed_reference(ref, allow_experimental=True)
            assert cleaned.cleanup_confirmed
            assert await async_command_managed_reference(ref, "purge", expected_revision=5, allow_experimental=True)
    if native_async: asyncio.run(run())
    else:
        with Client("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **options()) as client:
            context, _ = bound(client)
            created = create_managed_reference(context, allow_experimental=True)
            ref = created.reference
            assert command_managed_reference(ref, "start", expected_revision=1, allow_experimental=True).state == "RUNNING"
            assert command_managed_reference(ref, "stop", expected_revision=2, allow_experimental=True).state == "STOPPING"
            assert not read_managed_reference(ref, allow_experimental=True).cleanup_confirmed
            assert read_managed_reference(ref, allow_experimental=True).cleanup_confirmed
            assert command_managed_reference(ref, "purge", expected_revision=5, allow_experimental=True)
    assert len(calls) == 6


@pytest.mark.parametrize("raw", [None, b"\xff", b"{", b'{"x":1,"x":2}', b'NaN', b'x' * 65537])
def test_malformed_wire_refuses(raw):
    with pytest.raises(ProtocolError): _json(raw)


@pytest.mark.parametrize("changes", [{"extra": 1}, {"protocol": "native"}, {"execution": "JAVA_EXECUTION"},
    {"scope": "foreign"}, {"runtime_id": "foreign"}, {"agent_id": "bad"}, {"epoch": "bad"},
    {"revision": True}, {"revision": 0}, {"revision": 17}, {"state": "UNKNOWN"}, {"fixture": "arbitrary source"},
    {"cleanup_confirmed": 1}, {"cleanup_confirmed": True}])
def test_identity_schema_and_cleanup_are_never_inferred(changes):
    with Client("https://example.test/runtime", "synthetic", **options()) as client:
        context, _ = bound(client)
        with pytest.raises(ProtocolError): _status(wire(**changes), context, to_handle(context))


def test_preflight_wrong_binding_commands_and_transport_types_refuse_without_requests():
    with Client("https://example.test/runtime", "synthetic", **options()) as client:
        context, ref = bound(client)
        with pytest.raises(InvalidRequestError): _binding(None, True)
        with pytest.raises(InvalidRequestError): create_managed_reference(context)
        with pytest.raises(InvalidRequestError): create_managed_reference(context, fixture="behavior arbitrary", allow_experimental=True)
        with pytest.raises(InvalidRequestError): _options(None, True)
        with pytest.raises(InvalidRequestError): _options(replace(ref, agent_id="bad"), True)
        with pytest.raises(InvalidRequestError): _options(replace(ref, source=replace(ref.source, runtime_id="foreign")), True)
        for command, revision in (("restart", 1), ("start", True), ("start", 0), ("start", 17)):
            with pytest.raises(InvalidRequestError): command_managed_reference(ref, command, expected_revision=revision, allow_experimental=True)
        with pytest.raises(ProtocolError): _status(wire(agent_id="c" * 32), context, to_handle(context), ref)
        with pytest.raises(ProtocolError): _command_status(wire(), ref, "start", 1)
        with pytest.raises(InvalidRequestError): asyncio.run(async_create_managed_reference(context, allow_experimental=True))
        with pytest.raises(InvalidRequestError): asyncio.run(async_read_managed_reference(ref, allow_experimental=True))
        with pytest.raises(InvalidRequestError): asyncio.run(async_command_managed_reference(ref, "start", expected_revision=1, allow_experimental=True))
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", **options()) as client:
            context, ref = bound(client)
            with pytest.raises(InvalidRequestError): create_managed_reference(context, allow_experimental=True)
            with pytest.raises(InvalidRequestError): read_managed_reference(ref, allow_experimental=True)
            with pytest.raises(InvalidRequestError): command_managed_reference(ref, "start", expected_revision=1, allow_experimental=True)
    asyncio.run(run())


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("operation", ["create", "start", "purge"])
@pytest.mark.parametrize("failure", ["disconnect", "bad_confirmation"])
def test_unknown_or_unconfirmed_mutations_do_not_replay(native_async, operation, failure):
    calls = []
    def handler(request):
        calls.append(request)
        if failure == "disconnect": raise httpx.ReadError("Synthetic lost acknowledgement", request=request)
        if operation == "create": return httpx.Response(200, json=payload(state="RUNNING", revision=2))
        if operation == "start": return httpx.Response(200, json=payload())
        return httpx.Response(200, json={"protocol": PROTOCOL, "agent_id": "a" * 32, "epoch": "b" * 32, "purged": 1})
    expected = SubmissionOutcomeUnknown if failure == "disconnect" else ProtocolError
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **options()) as client:
            context, ref = bound(client)
            with pytest.raises(expected):
                if operation == "create": await async_create_managed_reference(context, allow_experimental=True)
                else: await async_command_managed_reference(ref, operation, expected_revision=1, allow_experimental=True)
    if native_async: asyncio.run(run())
    else:
        with Client("https://example.test/runtime", "synthetic", http_transport=httpx.MockTransport(handler), **options()) as client:
            context, ref = bound(client)
            with pytest.raises(expected):
                if operation == "create": create_managed_reference(context, allow_experimental=True)
                else: command_managed_reference(ref, operation, expected_revision=1, allow_experimental=True)
    assert len(calls) == 1
