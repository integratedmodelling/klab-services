"""Binary Provider Job references retain supplier identity, never payload or authority."""
import asyncio
from dataclasses import replace

import httpx
import pytest

from klab_client import (Client, AsyncClient, Context, Endpoint, Handle, to_handle,
    ProviderDataJob, AsyncProviderDataJob, HandleFormatError, HandleIdentityError)
from klab_client.errors import ConfigurationError, JobUnavailableError, AuthorizationError
from klab_client.provider_data import ProviderData


def reference():
    return Handle(kind="job", runtime_id="runtime-id", runtime_url="https://runtime.test",
        context_id="s.c", scope="s.c", job_id=7, service="resources", service_id="supplier-id",
        service_url="https://resources.test", result_kind="provider-data")


def options(handler):
    return dict(resources=Endpoint("https://resources.test", "current-resource-secret"),
        runtime_service_id="runtime-id", http_transport=httpx.MockTransport(handler), poll_interval=.001)


@pytest.mark.parametrize("service,kind", [("resources", "mapping"), ("resources", "observation"),
    ("resources", "dataflow"), ("runtime", "provider-data"), ("resolver", "provider-data")])
def test_binary_handle_decoder_service_pairing_is_strict(service, kind):
    with pytest.raises(HandleFormatError): replace(reference(), service=service, result_kind=kind)


def test_binary_job_save_requires_known_supplier_identity_and_stays_offline(tmp_path):
    from klab_client import save_handle, load_handle
    def offline(_): pytest.fail("Offline handle creation must not contact service")
    with Client("https://runtime.test", "current-runtime-secret", **options(offline)) as client:
        job = ProviderDataJob(client, 7, Context(client, {"id": "s.c"}), service="resources", result_kind="provider-data")
        with pytest.raises(ConfigurationError): to_handle(job)
        client._service_id_cache["resources"] = "supplier-id"
        target = tmp_path / "provider.json"
        save_handle(job, target)
        assert load_handle(target) == reference()
        assert "secret" not in target.read_text() and "payload" not in target.read_text()


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("mode", ["success", "supplier-drift", "runtime-drift", "expired", "denied", "url-drift"])
def test_restore_provider_job_uses_current_credentials_and_binary_decoder_without_replay(native_async, mode):
    trace = []
    data = ProviderData.from_fields(dict(name="fixture", geometry="1", observable="synthetic:Value",
        identity="fixture", fillingCurve=None, empty=False, notifications=[], metadata={},
        instances=None, doubleData=[1.25], longData=None, intData=None, floatData=None, dataKey=None))
    def handler(request):
        trace.append(request)
        # Native attach uses POST/connect; this reconnects existing state, never
        # POST/contextualize or submit/resolve/recreate.
        assert request.method == ("POST" if request.url.path.endswith("/connect") else "GET")
        resource = request.url.host == "resources.test"
        assert request.headers["Authorization"] == ("current-resource-secret" if resource else "current-runtime-secret")
        if request.url.path.endswith("/capabilities"):
            identity = "supplier-id" if resource else "runtime-id"
            if mode == ("supplier-drift" if resource else "runtime-drift"): identity = "changed"
            return httpx.Response(200, json={"serviceId": identity})
        if request.url.path.endswith("/connect"):
            return httpx.Response(403, text="denied") if mode == "denied" else httpx.Response(200, json={"id": "s.c", "serviceId": "runtime-id"})
        assert resource and request.headers["klab-service"] == "runtime-id" and request.headers["klab-scope"] == "s.c"
        if "/status/" in request.url.path:
            return httpx.Response(200, json={"status": "EMPTY" if mode == "expired" else "FINISHED"})
        assert request.url.path == "/jobs/retrieveData/7"
        return httpx.Response(200, content=data.payload)
    saved = replace(reference(), service_url="https://different.test") if mode == "url-drift" else reference()
    expected = (HandleIdentityError if mode in ("supplier-drift", "runtime-drift", "url-drift") else
        JobUnavailableError if mode == "expired" else AuthorizationError if mode == "denied" else None)
    async def run():
        async with AsyncClient("https://runtime.test", "current-runtime-secret", **options(handler)) as client:
            if expected:
                with pytest.raises(expected): await client.restore_handle(saved)
            else:
                job = await client.restore_handle(saved)
                assert isinstance(job, AsyncProviderDataJob) and not job.scope.owned
                assert (await job.result(timeout=2)).payload == data.payload
    if native_async: asyncio.run(run())
    else:
        with Client("https://runtime.test", "current-runtime-secret", **options(handler)) as client:
            if expected:
                with pytest.raises(expected): client.restore_handle(saved)
            else:
                job = client.restore_handle(saved)
                assert isinstance(job, ProviderDataJob) and not job.scope.owned
                assert job.result(timeout=2).payload == data.payload
    if mode == "url-drift": assert not trace
    if mode in ("supplier-drift", "runtime-drift"):
        assert all(request.url.path.endswith("/capabilities") for request in trace)
