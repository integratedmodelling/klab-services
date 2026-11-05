"""Only current native Resources GET-retrieve null bodies establish missing assets."""
import asyncio

import httpx
import pytest

from klab_client import AsyncClient, Client, Endpoint
from klab_client.errors import MissingAssetError, ProtocolError


def client(native_async, handler):
    return (AsyncClient if native_async else Client)("https://runtime.invalid", "test-only", runtime_service_id="home",
        resources=Endpoint("https://resources.invalid", "resource-secret"), http_transport=httpx.MockTransport(handler))


@pytest.mark.parametrize("native_async", [False, True])
def test_native_zero_byte_null_asset_is_typed_missing_after_positive_lookup(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        return (httpx.Response(200, json={"urn": "known.model"}) if request.url.path.endswith("known.model")
            else httpx.Response(200, content=b""))
    async def run():
        async with client(True, handler) as api:
            assert (await api.resources.retrieve("known.model", "MODEL")).urn == "known.model"
            with pytest.raises(MissingAssetError) as caught:
                await api.resources.retrieve("absent.model", "MODEL")
            assert caught.value.status_code == 200
    if native_async:
        asyncio.run(run())
    else:
        with client(False, handler) as api:
            assert api.resources.retrieve("known.model", "MODEL").urn == "known.model"
            with pytest.raises(MissingAssetError) as caught:
                api.resources.retrieve("absent.model", "MODEL")
            assert caught.value.status_code == 200
    assert len(calls) == 2 and all(r.method == "GET" for r in calls)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("body", [b" ", b"not JSON", b"{", b"[]"])
def test_nonempty_invalid_asset_payloads_stay_protocol_errors(native_async, body):
    def handler(request):
        return httpx.Response(200, content=body)
    async def run():
        async with client(True, handler) as api:
            with pytest.raises(ProtocolError):
                await api.resources.retrieve("absent.model", "MODEL")
    if native_async:
        asyncio.run(run())
    else:
        with client(False, handler) as api:
            with pytest.raises(ProtocolError):
                api.resources.retrieve("absent.model", "MODEL")


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("service,method,route,status", [
    ("resources", "GET", "/api/v1/list/MODEL", 200),
    ("runtime", "GET", "/api/v1/retrieve/MODEL/absent", 200),
    ("resources", "POST", "/api/v1/retrieve/MODEL/absent", 200),
    ("resources", "GET", "/api/v1/retrieve/", 200),
    ("resources", "GET", "/api/v1/retrieve/MODEL/", 200),
    ("resources", "GET", "/api/v1/retrieve/MODEL/absent", 201),
    ("resources", "GET", "/api/v1/retrieve/MODEL/absent", 204)])
def test_other_service_route_method_status_empty_responses_are_not_absence(native_async, service, method, route, status):
    def handler(request):
        return httpx.Response(status, content=b"")
    async def run():
        async with client(True, handler) as api:
            with pytest.raises(ProtocolError):
                await api.transport.request(service, method, route)
    if native_async:
        asyncio.run(run())
    else:
        with client(False, handler) as api:
            with pytest.raises(ProtocolError):
                api.transport.request(service, method, route)


@pytest.mark.parametrize("native_async", [False, True])
def test_text_mode_empty_asset_response_keeps_explicit_text_contract(native_async):
    def handler(request):
        return httpx.Response(200, content=b"")
    async def run():
        async with client(True, handler) as api:
            return await api.transport.request("resources", "GET", "/api/v1/retrieve/MODEL/absent", response="text")
    if native_async:
        assert asyncio.run(run()) == ""
    else:
        with client(False, handler) as api:
            assert api.transport.request("resources", "GET", "/api/v1/retrieve/MODEL/absent", response="text") == ""
