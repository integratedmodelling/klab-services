"""Native async metadata parity; mocked advertisements are not scientific selection."""
import asyncio
import json

import httpx
import pytest

from klab_client import AsyncClient, AsyncResourceInspector, Client, Endpoint, ResourceInspector
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError)
from test_resource_inspector import MODEL, RESOURCE_SET, setup as sync_setup


def setup(*, payload=None, status=200, **options):
    calls, caps = [], {"serviceId": "resources-origin"}
    async def handler(request):
        calls.append(request)
        assert request.headers["authorization"] == "resource-secret"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        assert request.url.host == "resources.invalid" and request.method == "GET"
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json=caps)
        if status != 200:
            return httpx.Response(status, json={"message": "request failed"})
        value = payload(request) if payload else ([MODEL, MODEL] if "/list/" in request.url.path else
            RESOURCE_SET if "/resolve/" in request.url.path else MODEL)
        return httpx.Response(200, content=json.dumps(value))
    client = AsyncClient("https://runtime.invalid", "runtime-secret", runtime_service_id="home",
        resources=Endpoint("https://resources.invalid/base", "resource-secret"), http_transport=httpx.MockTransport(handler))
    return client, AsyncResourceInspector(client, "s.c", **options), calls, caps


def test_native_async_typed_parity_duplicates_partial_resolution_and_no_provider_follow():
    async def run():
        client, inspector, calls, _ = setup()
        async with client:
            a = await inspector.retrieve("fixture.elevation")
            listed = await inspector.list()
            resolved = await inspector.resolve("local:field")
        assert len(listed) == 2 and listed[0] == listed[1] == a
        assert a.advertised_observables[0].unit == "m" and a.listed_dependencies[0].optional is True
        assert a.origin.service_id == "resources-origin" and a.origin.scope == "s.c"
        assert a.version["major"] == 1 and "not a dependency closure" in a.summary()
        assert resolved.partial and resolved.results[0].version["major"] == 2
        assert resolved.services["remote-source"] == "https://untrusted.invalid/resources"
        assert "accessKey" not in resolved.raw["results"][0] and len(calls) == 9
        with pytest.raises(TypeError):
            a.raw["label"] = "overwrite"
        with pytest.raises(TypeError):
            resolved.services["other"] = "https://other.invalid"
    asyncio.run(run())


def test_same_payloads_produce_identical_sync_and_native_async_immutable_views():
    sync, inspector, _, _ = sync_setup()
    with sync:
        expected = (inspector.list(), inspector.retrieve("fixture.elevation"), inspector.resolve("local:field"))
    async def run():
        client, inspector, _, _ = setup()
        async with client:
            return (await inspector.list(), await inspector.retrieve("fixture.elevation"), await inspector.resolve("local:field"))
    assert asyncio.run(run()) == expected


@pytest.mark.parametrize("payload", [{"results": [{"resourceUrn": "local:field", "serviceId": 1}]},
    {"results": [], "services": {"provider": 3}}, {"results": [{"resourceUrn": ""}]}])
def test_malformed_async_resolution_views_are_refused(payload):
    async def run():
        client, inspector, _, _ = setup(payload=lambda _: payload)
        async with client:
            with pytest.raises(ProtocolError):
                await inspector.resolve("local:field")
    asyncio.run(run())


@pytest.mark.parametrize("method", ["list", "retrieve", "resolve"])
@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (404, MissingAssetError), (422, UnsupportedOperationError), (503, ServerError)])
def test_distinct_async_http_errors(method, status, error):
    async def run():
        client, inspector, _, _ = setup(status=status)
        async with client:
            with pytest.raises(error):
                await getattr(inspector, method)(*([] if method == "list" else ["fixture.elevation"]))
    asyncio.run(run())


@pytest.mark.parametrize("changes", [{"dependencies": {}}, {"observables": [None]},
    {"dependencies": [{"optional": "yes"}]}, {"observables": [{"urn": 1}]},
    {"label": 2}, {"urn": "different"}, {"resourceUrns": "not an array"}])
def test_async_malformed_metadata_refused(changes):
    async def run():
        client, inspector, _, _ = setup(payload=lambda _: MODEL | changes)
        async with client:
            with pytest.raises(ProtocolError):
                await inspector.retrieve("fixture.elevation")
    asyncio.run(run())


def test_async_missing_budget_redaction_and_drift_controls():
    async def run():
        for options, error in [({"payload": lambda _: None}, MissingAssetError),
            ({"max_items": 1}, ProtocolError), ({"max_response_bytes": 80}, ProtocolError)]:
            client, inspector, _, _ = setup(**options)
            async with client:
                with pytest.raises(error):
                    await (inspector.retrieve("fixture.elevation") if "payload" in options else inspector.list())
        client, inspector, _, _ = setup(payload=lambda _: {"urn": "local:field", "future": {"code": "resource-secret"}})
        async with client:
            a = await inspector.retrieve("local:field", "RESOURCE")
        assert a.raw["future"]["code"] == "[REDACTED]" and a.version is a.listed_dependencies is None
        caps = None
        def drift(_):
            caps["serviceId"] = "changed"
            return MODEL
        client, inspector, _, caps = setup(payload=drift)
        async with client:
            with pytest.raises(ProtocolError):
                await inspector.retrieve("fixture.elevation")
    asyncio.run(run())


def test_async_type_refusal_and_unsupported_class_do_not_send_requests():
    with Client("https://runtime.invalid", "test-only") as sync:
        with pytest.raises(InvalidRequestError):
            AsyncResourceInspector(sync)
    async def run():
        client, inspector, calls, _ = setup()
        async with client:
            with pytest.raises(UnsupportedOperationError):
                ResourceInspector(client)
            for method in (inspector.list, inspector.retrieve, inspector.resolve):
                with pytest.raises(UnsupportedOperationError):
                    await method(*(["NAMESPACE"] if method == inspector.list else ["urn", "NAMESPACE"]))
            assert not calls
    asyncio.run(run())


def test_cancelling_native_async_inspection_only_cancels_local_request():
    async def run():
        entered = asyncio.Event()
        paths = []
        async def handler(request):
            paths.append(request.url.path)
            entered.set()
            await asyncio.Event().wait()
        async with AsyncClient("https://runtime.invalid", "test-only",
            resources=Endpoint("https://resources.invalid", "resource-secret"),
            http_transport=httpx.MockTransport(handler)) as client:
            task = asyncio.create_task(AsyncResourceInspector(client).list())
            await asyncio.wait_for(entered.wait(), 1)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert len(paths) == 1 and paths[0].endswith("/public/capabilities")
    asyncio.run(run())
