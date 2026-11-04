"""Process-local Job binding guards; no server incarnation/retention claims."""
import asyncio

import httpx
import pytest

from klab_client import AsyncClient, Client, Context, Endpoint
from klab_client.async_client import AsyncContext, AsyncJob
from klab_client.client import Job
from klab_client.errors import ProtocolError, SubmissionOutcomeUnknown
from klab_client.handles import to_handle


def setup(asynchronous=False, service="runtime", callback=None):
    calls = []
    def handler(request):
        calls.append(request)
        if callback:
            callback(request)
        if "/status/" in request.url.path:
            return httpx.Response(200, json={"status": "FINISHED"})
        if "/cancel/" in request.url.path:
            return httpx.Response(200, json=True)
        return httpx.Response(200, json={"answer": 42})
    cls = AsyncClient if asynchronous else Client
    api = cls("https://runtime.example", "runtime-secret", runtime_service_id="runtime-a",
        resolver=Endpoint("https://resolver.example", "resolver-secret"),
        http_transport=httpx.MockTransport(handler))
    api._service_id_cache.update(runtime="runtime-a", resolver="resolver-a")
    context = (AsyncContext if asynchronous else Context)(api, {"id": "s.c", "serviceId": "runtime-a"})
    job = (AsyncJob if asynchronous else Job)(api, 5, context, service=service)
    return api, context, job, calls


def drift(api, context, job, field):
    if field == "job_id": job.id = 6
    elif field == "job_id_type": job.id = 5.0
    elif field == "invalid_token": context.context_id = "bad token"
    elif field == "configuration_identity": context.configuration["serviceId"] = "runtime-b"
    elif field == "configuration_id": context.configuration["id"] = "s.other"
    elif field == "configuration_type": context.configuration = None
    elif field == "scope": job.scope = "s.other"
    elif field == "scope_token": context.context_id = "s.other"
    elif field == "scope_client": context.client = object()
    elif field == "service": job.service = "resolver"
    elif field == "runtime_id": api.runtime_service_id = "runtime-b"
    elif field == "origin": api.transport.endpoints[job.service] = Endpoint("https://other.example", "other-secret")
    elif field == "credential": api.transport.endpoints[job.service] = Endpoint(api.transport.endpoint(job.service).url, "changed-secret")
    elif field == "peer_id": api._service_id_cache[job.service] = "changed-peer"
    elif field == "client": job.client = object()
    elif field == "transport": api.transport = object()
    elif field == "decoder": job._decoder = lambda value: value
    else: job.result_kind = "observation"


FIELDS = ["job_id", "job_id_type", "scope", "scope_token", "scope_client", "invalid_token", "configuration_identity",
          "configuration_id", "configuration_type", "service", "runtime_id", "origin",
          "credential", "peer_id", "client", "transport", "decoder", "result_kind"]


@pytest.mark.parametrize("field", FIELDS)
@pytest.mark.parametrize("operation", ["status", "result", "cancel", "to_handle", "direct_handle"])
def test_sync_drift_fails_before_any_request_or_handle_creation(field, operation):
    api, context, job, calls = setup()
    transport = api.transport
    with api:
        try:
            drift(api, context, job, field)
            with pytest.raises(ProtocolError):
                to_handle(job) if operation == "direct_handle" else getattr(job, operation)()
            assert not calls and not job.cancellation_requested
        finally:
            api.transport = transport


@pytest.mark.parametrize("field", FIELDS)
@pytest.mark.parametrize("operation", ["status", "result", "cancel", "to_handle"])
def test_async_drift_fails_before_any_request_or_handle_creation(field, operation):
    async def run():
        api, context, job, calls = setup(True)
        transport = api.transport
        async with api:
            try:
                drift(api, context, job, field)
                with pytest.raises(ProtocolError):
                    if operation == "to_handle": job.to_handle()
                    else: await getattr(job, operation)()
                assert not calls and not job.cancellation_requested
            finally:
                api.transport = transport
    asyncio.run(run())


@pytest.mark.parametrize("asynchronous", [False, True])
@pytest.mark.parametrize("service", ["runtime", "resolver"])
def test_unchanged_binding_retains_exact_scope_route_credential_and_home_header(asynchronous, service):
    async def run():
        api, _, job, calls = setup(asynchronous, service)
        if asynchronous:
            async with api:
                assert (await job.status()).status == "FINISHED"
                assert await job.result() == {"answer": 42}
                assert await job.cancel() is True
        else:
            with api:
                assert job.status().status == "FINISHED"
                assert job.result() == {"answer": 42}
                assert job.cancel() is True
        assert job.cancellation_requested
        assert all(call.url.host == service + ".example" and call.headers["klab-scope"] == "s.c"
            and call.headers["klab-service"] == "runtime-a" and call.headers["authorization"] == service + "-secret"
            for call in calls)
    asyncio.run(run())


@pytest.mark.parametrize("asynchronous", [False, True])
@pytest.mark.parametrize("operation", ["status", "retrieve", "cancel"])
def test_mid_response_drift_is_not_decoded_rebound_or_retried(asynchronous, operation):
    async def run():
        state = {}
        def change(request):
            if "/" + operation + "/" in request.url.path:
                state["context"].context_id = "s.other"
        api, context, job, calls = setup(asynchronous, callback=change)
        state["context"] = context
        error = SubmissionOutcomeUnknown if operation == "cancel" else ProtocolError
        if asynchronous:
            async with api:
                with pytest.raises(error):
                    await (job.cancel() if operation == "cancel" else job.result())
        else:
            with api, pytest.raises(error):
                job.cancel() if operation == "cancel" else job.result()
        assert len(calls) == (2 if operation == "retrieve" else 1)
        assert not job.cancellation_requested
        assert all(call.headers["klab-scope"] == "s.c" for call in calls)
    asyncio.run(run())


@pytest.mark.parametrize("asynchronous", [False, True])
def test_custom_decoder_cannot_change_binding_and_return_a_rebound_result(asynchronous):
    async def run():
        api, context, _, calls = setup(asynchronous)
        def decoder(payload):
            context.context_id = "s.other"
            return payload
        job = (AsyncJob if asynchronous else Job)(api, 5, context, decoder=decoder)
        if asynchronous:
            async with api:
                with pytest.raises(ProtocolError): await job.result()
        else:
            with api, pytest.raises(ProtocolError): job.result()
        assert len(calls) == 2
    asyncio.run(run())


def test_unknown_cache_can_be_learned_but_existing_known_peer_cannot_drift():
    api, context, _, calls = setup()
    with api:
        api._service_id_cache.clear()
        job = Job(api, 5, context)
        api._service_id_cache.update(runtime="runtime-a")
        assert job.status().status == "FINISHED"
        assert "runtime-secret" not in repr(job._binding)
        context.configuration["serviceId"] = "changed"
        with pytest.raises(ProtocolError): job.status()
    assert len(calls) == 1


@pytest.mark.parametrize("service", ["runtime", "resolver"])
@pytest.mark.parametrize("field", ["home_origin", "home_cache", "peer_cache_removed"])
def test_resolver_and_home_bindings_are_checked_independently(service, field):
    api, _, job, calls = setup(service=service)
    with api:
        if field == "home_origin":
            api.transport.endpoints["runtime"] = Endpoint("https://replacement.example", "changed-secret")
        elif field == "home_cache":
            api._service_id_cache["runtime"] = "changed-runtime"
        else:
            del api._service_id_cache[service]
        with pytest.raises(ProtocolError): job.status()
    assert not calls
