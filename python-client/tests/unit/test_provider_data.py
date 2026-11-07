"""Source-shaped raw Avro provider Job fixtures, never real Java acceptance."""
import asyncio
from types import SimpleNamespace as NS

import httpx
import pytest

from klab_client import Client, AsyncClient, Context
from klab_client.api.runtime import GeometryImpl
from klab_client.api.primitives import KlabAsset
from klab_client.errors import (AuthorizationError, InvalidRequestError, JobFailedError,
    JobCancelledError, ProtocolError, SubmissionOutcomeUnknown, WaitTimeout)
from klab_client.provider_data import ProviderData
from klab_client import _provider_avro as codec

TOKEN = "provider-fixture-secret"


def value():
    return dict(name="fixture", geometry="1", observable="synthetic:Value", identity="resource-fixture",
        fillingCurve="DN_LINEAR", empty=False, notifications=[], metadata={"unit": "m"},
        instances=None, doubleData=[0., -2.5], longData=None, intData=None, floatData=None, dataKey=None)


def arguments(client):
    return (KlabAsset("urn:synthetic:resource"), NS(get_observable=lambda: NS(urn="synthetic:Value")),
        GeometryImpl(), Context(client, {"id": "s.c", "serviceId": "runtime-1"}))


def client_args(handler):
    from klab_client import Endpoint
    return dict(resources=Endpoint("https://provider.test", TOKEN), runtime_service_id="runtime-1",
        poll_interval=.001, http_transport=httpx.MockTransport(handler))


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("media", ["application/octet-stream", None])
def test_binary_request_and_data_specific_job_route_with_explicit_input(native_async, media):
    trace = []
    raw = codec._encode(codec._INSTANCE, value())
    def handler(request):
        trace.append(request)
        assert request.headers["Authorization"] == TOKEN and request.headers["klab-scope"] == "s.c"
        assert request.headers["klab-service"] == "runtime-1"
        if request.method == "POST":
            assert request.url.host == "provider.test" and request.url.path == "/contextualize"
            assert request.headers["Content-Type"] == "application/octet-stream"
            assert codec._decode(codec._REQUEST, request.content) == dict(observable="synthetic:Value",
                geometry="1", startTime=1, endTime=2, resourceUrns=["urn:synthetic:resource"], inputData=value())
            return httpx.Response(200, text="7")
        if "/status/" in request.url.path: return httpx.Response(200, json={"status": "FINISHED"})
        assert request.url.path == "/jobs/retrieveData/7" and request.headers["Accept"] == "application/octet-stream"
        assert request.headers["Accept-Encoding"] == "identity"
        return httpx.Response(200, content=raw, headers={"Content-Type": media} if media else {})
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, **client_args(handler)) as client:
            job = await client.resources.contextualize(*arguments(client), start_time=1, end_time=2, input_data=ProviderData(raw))
            return await job.result(timeout=2)
    if native_async: result = asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
            result = client.resources.contextualize(*arguments(client), start_time=1, end_time=2,
                input_data=ProviderData(raw)).result(timeout=2)
    assert result.payload == raw and result.fields["doubleData"] == (0., -2.5)
    with pytest.raises(TypeError): result.fields["metadata"]["unit"] = "different"
    assert TOKEN not in repr(result) and "resource-fixture" not in repr(result)
    assert len(trace) == 3


def test_explicit_provider_field_builder_has_no_guessed_defaults_or_extra_dependencies():
    result = ProviderData.from_fields(value())
    assert result.fields["doubleData"] == (0., -2.5)
    assert codec._decode(codec._INSTANCE, result.payload) == value()
    with pytest.raises(InvalidRequestError): ProviderData.from_fields({"doubleData": [1.]})


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("ack", [None, True, -1, "7", 0])
def test_invalid_post_acknowledgement_is_unknown_and_never_replayed(native_async, ack):
    trace = []
    def handler(request):
        trace.append(request)
        assert codec._decode(codec._REQUEST, request.content)["inputData"] is None
        return httpx.Response(200, json=ack) if ack is not None else httpx.Response(200, content=b"malformed")
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, **client_args(handler)) as client:
            with pytest.raises(SubmissionOutcomeUnknown): await client.resources.contextualize(*arguments(client))
    if native_async: asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
            with pytest.raises(SubmissionOutcomeUnknown): client.resources.contextualize(*arguments(client))
    assert len(trace) == 1


@pytest.mark.parametrize("mode,error", [("media", ProtocolError), ("malformed", ProtocolError),
    ("secret", ProtocolError), ("error", JobFailedError), ("denied", AuthorizationError)])
def test_provider_results_fail_explicitly_without_scientific_or_empty_fallback(mode, error):
    def handler(request):
        if request.method == "POST": return httpx.Response(200, json=7)
        if "/status/" in request.url.path: return httpx.Response(200, json={"status": "FINISHED"})
        data = value()
        if mode == "secret": data["metadata"]["note"] = TOKEN
        if mode == "error": data["notifications"] = [dict(message="failed " + TOKEN,
            activityUrn=None, metadata=None, code=None, level="ERROR")]
        return httpx.Response(403 if mode == "denied" else 200,
            content=b"invalid" if mode == "malformed" else codec._encode(codec._INSTANCE, data),
            headers={"Content-Type": "application/json" if mode == "media" else "application/octet-stream"})
    with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
        job = client.resources.contextualize(*arguments(client))
        with pytest.raises(error) as caught: job.result(timeout=2)
        assert TOKEN not in str(caught.value)


def test_provider_preflight_is_zero_http_and_does_not_require_simulator():
    def handler(_): raise AssertionError("Invalid provider request must not send HTTP")
    with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
        args = arguments(client)
        for position, replacement in ((0, None), (1, None), (1, NS(get_observable=lambda: NS(urn=""))),
                (2, None), (2, NS(encode=lambda: "")), (3, None)):
            changed = list(args); changed[position] = replacement
            with pytest.raises(InvalidRequestError): client.resources.contextualize(*changed)
        for kwargs in ({"start_time": True}, {"start_time": 2, "end_time": 1}, {"input_data": {}},
                {"end_time": 2**63}):
            with pytest.raises(InvalidRequestError): client.resources.contextualize(*args, **kwargs)


def test_explicit_scope_token_is_not_a_permission_grant_and_server_denial_survives():
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["klab-scope"] == "s.c"
        return httpx.Response(403, text="synthetic denial")
    with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
        args = list(arguments(client)); args[-1] = "s.c"
        with pytest.raises(AuthorizationError): client.resources.contextualize(*args)
    assert len(calls) == 1


@pytest.mark.parametrize("native_async", [False, True])
def test_post_origin_change_is_unknown_not_a_new_binding_or_replay(native_async):
    calls = []; holder = []
    def handler(request):
        calls.append(request)
        holder[0].runtime_service_id = "changed-runtime"
        return httpx.Response(200, json=7)
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, **client_args(handler)) as client:
            holder.append(client)
            with pytest.raises(SubmissionOutcomeUnknown): await client.resources.contextualize(*arguments(client))
    if native_async: asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
            holder.append(client)
            with pytest.raises(SubmissionOutcomeUnknown): client.resources.contextualize(*arguments(client))
    assert len(calls) == 1


def test_provider_explicit_token_job_local_timeout_cancel_and_terminal_state():
    trace = []; cancelled = []
    def handler(request):
        trace.append(request)
        if request.method == "POST": return httpx.Response(200, json=7)
        if "/cancel/" in request.url.path:
            cancelled.append(True); return httpx.Response(200, json=True)
        assert "/status/" in request.url.path
        return httpx.Response(200, json={"status": "INTERRUPTED" if cancelled else "WAITING"})
    with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
        args = list(arguments(client)); args[-1] = "s.c"
        job = client.resources.contextualize(*args)
        with pytest.raises(WaitTimeout): job.result(timeout=0)
        assert len(trace) == 1  # Local wait timeout neither submits nor cancels remotely.
        assert job.cancel() is True
        with pytest.raises(JobCancelledError): job.result(timeout=2)
        assert len(trace) == 3


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("kwargs", [{"binary": "not bytes"}, {"binary": b"x" * (8 * 1024 * 1024 + 1)},
    {"binary": b"x", "text": "conflict"}, {"binary": b"x", "json": {}}, {"binary": b"x", "files": {}},
    {"text": "\ud800"}, {"text": 1}])
def test_binary_transport_preflight_refuses_competing_or_unbounded_bodies(native_async, kwargs):
    def handler(_): raise AssertionError("Invalid binary body must not send HTTP")
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, **client_args(handler)) as client:
            with pytest.raises(InvalidRequestError): await client.transport.request("resources", "POST", "/contextualize", **kwargs)
    if native_async: asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
            with pytest.raises(InvalidRequestError): client.transport.request("resources", "POST", "/contextualize", **kwargs)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("mode", ["disconnect", "http500", "oversized_ack"])
def test_uncertain_binary_submission_is_not_retried_or_replaced(native_async, mode):
    trace = []
    def handler(request):
        trace.append(request)
        if mode == "disconnect": raise httpx.ReadError("synthetic disconnect", request=request)
        return httpx.Response(500 if mode == "http500" else 200, content=b"x" * 4097 if mode == "oversized_ack" else b"uncertain")
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, **client_args(handler)) as client:
            with pytest.raises(SubmissionOutcomeUnknown): await client.resources.contextualize(*arguments(client))
    if native_async: asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, **client_args(handler)) as client:
            with pytest.raises(SubmissionOutcomeUnknown): client.resources.contextualize(*arguments(client))
    assert len(trace) == 1
