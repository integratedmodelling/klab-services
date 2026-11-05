"""Native awaited restoration through existing source-derived handle contracts."""
import asyncio
from copy import deepcopy
import json
from pathlib import Path

import httpx
import pytest

from klab_client import (AsyncClient, AsyncContext, AsyncJob, Endpoint, Handle,
    HandleFormatError, HandleIdentityError)
from klab_client.errors import (JobUnavailableError, MissingAssetError, ProtocolError,
    AuthenticationError, AuthorizationError)


def reference(**changes):
    return Handle(**(dict(kind="context", runtime_id="runtime-id", runtime_url="https://runtime.invalid/runtime",
        context_id="s.c", scope="s.c") | changes))


def job_reference(service="runtime"):
    return reference(kind="job", job_id=5, service=service,
        service_id="runtime-id" if service == "runtime" else "peer-id",
        service_url="https://" + service + ".invalid/" + service,
        result_kind="observation" if service == "runtime" else "dataflow")


def wire():
    value = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    value["urn"] = "s.c.42"
    return value


def client(handler):
    return AsyncClient("https://runtime.invalid/runtime", "issued-secret", agent_name="scientist",
        runtime_service_id="runtime-id", resolver=Endpoint("https://resolver.invalid/resolver", "peer-secret"),
        http_transport=httpx.MockTransport(handler))


def responder(calls, *, payload=None, status="FINISHED", runtime="runtime-id", peer="peer-id", configuration=None):
    async def handler(request):
        calls.append(request)
        path = request.url.path
        if path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": peer if request.url.host == "resolver.invalid" else runtime})
        if path.endswith("/connect"):
            return httpx.Response(200, json=configuration or {"id": "s.c", "serviceId": "runtime-id"})
        if path.endswith("/query"):
            return httpx.Response(200, json=[deepcopy(wire())] if payload is None else payload)
        if "/status/" in path:
            return httpx.Response(200, json={"status": status})
        if "/retrieve/" in path:
            return httpx.Response(200, json=wire() if request.url.host == "runtime.invalid" else {"resolutionOutcome": "RESOLVED"})
        pytest.fail("Unexpected request " + path)
    return handler


def test_native_observation_restore_uses_current_graph_and_unowned_async_scope():
    async def run():
        calls = []
        async with client(responder(calls)) as api:
            result = await api.restore_handle(reference(kind="observation", observation_id=42, scope="s.c.42"))
            assert result.id == 42 and isinstance(result._context, AsyncContext)
            assert result._context.owned is False and result._context.to_handle().scope == "s.c.42"
            # Explicit release is an authorized destructive API even for attached
            # sync/async scopes. Local close must not invoke it automatically.
        assert [r.url.path for r in calls] == ["/runtime/public/capabilities", "/runtime/api/v1/connect",
            "/runtime/api/v1/query", "/runtime/api/v1/query"]
        assert all(json.loads(r.content)["id"] == 42 for r in calls if r.url.path.endswith("/query"))
    asyncio.run(run())


@pytest.mark.parametrize("service", ["runtime", "resolver"])
def test_retained_native_job_routes_and_decoder_without_submit(service):
    async def run():
        calls = []
        async with client(responder(calls)) as api:
            job = await api.restore_handle(job_reference(service))
            assert isinstance(job, AsyncJob) and job.scope.owned is False
            result = await job.result(2)
            assert result.id == 42 if service == "runtime" else result["resolutionOutcome"] == "RESOLVED"
        assert not any("submit" in r.url.path or "release" in r.url.path for r in calls)
        assert all(r.url.host == service + ".invalid" and r.headers["klab-service"] == "runtime-id"
            for r in calls if "/jobs/" in r.url.path)
    asyncio.run(run())


@pytest.mark.parametrize("defect", ["boolean", "format", "origin", "peer-origin", "credential"])
def test_preflight_rejections_before_any_http(defect):
    async def run():
        ref = reference()
        kwargs = {}
        if defect == "boolean":
            kwargs["allow_relocation"] = 1
        if defect == "format":
            ref = {}
        if defect == "origin":
            ref = reference(runtime_url="https://foreign.invalid/runtime")
        if defect == "peer-origin":
            values = job_reference("resolver").to_dict()
            values["service_url"] = "https://foreign.invalid/resolver"
            ref = Handle.loads(json.dumps(values))
        if defect == "credential":
            ref = reference(runtime_id="issued-secret")
        async with client(lambda r: pytest.fail("Preflight must be offline")) as api:
            with pytest.raises((HandleFormatError, HandleIdentityError)):
                await api.restore_handle(ref, **kwargs)
    asyncio.run(run())


@pytest.mark.parametrize("defect", ["runtime", "peer", "context", "owner", "missing", "duplicate", "class", "id", "urn", "observer", "empty"])
def test_invalid_remote_identity_selection_or_retention_never_recreates_work(defect):
    async def run():
        calls = []
        payload = None
        config = None
        ref = reference(kind="observation", observation_id=42)
        if defect in ("peer", "empty"):
            ref = job_reference("resolver" if defect == "peer" else "runtime")
        if defect == "missing":
            payload = []
        if defect == "duplicate":
            payload = [wire(), wire()]
        if defect in ("class", "id", "urn"):
            payload = [wire() | { {"class": "@CLASS", "id": "id", "urn": "urn"}[defect]:
                {"class": "foreign.Class", "id": 99, "urn": "foreign.context.42"}[defect]}]
        if defect == "observer":
            ref = reference(scope="s.c#42")
        if defect == "context":
            config = {"id": "s.other", "serviceId": "runtime-id"}
        if defect == "owner":
            config = {"id": "s.c", "serviceId": "foreign"}
        async with client(responder(calls, payload=payload, configuration=config,
                runtime="foreign" if defect == "runtime" else "runtime-id",
                peer="foreign" if defect == "peer" else "peer-id", status="EMPTY" if defect == "empty" else "FINISHED")) as api:
            with pytest.raises((HandleIdentityError, ProtocolError, MissingAssetError, JobUnavailableError)):
                await api.restore_handle(ref)
        assert not any("submit" in r.url.path or "release" in r.url.path for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("status,exception", [(401, AuthenticationError), (403, AuthorizationError)])
def test_authentication_and_foreign_access_fail_without_retry(status, exception):
    async def run():
        calls = []
        async def handler(request):
            calls.append(request)
            return httpx.Response(status)
        async with client(handler) as api:
            with pytest.raises(exception):
                await api.restore_handle(reference())
        assert len(calls) == 1
    asyncio.run(run())


def test_local_wait_cancellation_after_restore_never_cancels_or_releases_remote_job():
    async def run():
        calls = []
        polled = asyncio.Event()
        base = responder(calls, status="STARTED")
        async def handler(request):
            response = await base(request)
            if "/status/" in request.url.path and len([r for r in calls if "/status/" in r.url.path]) > 1:
                polled.set()
            return response
        async with client(handler) as api:
            job = await api.restore_handle(job_reference())
            task = asyncio.create_task(job.result())
            await asyncio.wait_for(polled.wait(), 2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert not any(any(part in r.url.path for part in ("cancel", "release", "submit")) for r in calls)
    asyncio.run(run())


def test_explicit_relocation_still_requires_current_runtime_identity():
    async def run():
        calls = []
        ref = reference(runtime_url="https://previous.invalid/runtime")
        async with client(responder(calls)) as api:
            result = await api.restore_handle(ref, allow_relocation=True)
            assert isinstance(result, AsyncContext) and not result.owned
        assert all(r.url.host == "runtime.invalid" for r in calls)
        calls.clear()
        async with client(responder(calls, runtime="foreign")) as api:
            with pytest.raises(HandleIdentityError):
                await api.restore_handle(ref, allow_relocation=True)
        assert len(calls) == 1
    asyncio.run(run())
