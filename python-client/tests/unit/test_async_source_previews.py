"""Native async parser DTO parity, not Java grammar/semantic validity or persistence."""
import asyncio
import hashlib
import json

import httpx
import pytest

from klab_client import AsyncClient, AsyncSourcePreviews, Client, Endpoint, SourcePreview, SourcePreviews
from klab_client.authoring import ParsedSource
from klab_client.errors import InvalidRequestError, ProtocolError, ServerError, UnsupportedOperationError
from klab_client.source_previews import DOCUMENT_CLASSES
from test_source_previews import SOURCE, SOURCES, setup as sync_setup


def setup(*, changes=None, identity_change=None, callback=None):
    calls = []
    count = 0
    async def handler(request):
        nonlocal count
        calls.append(request)
        if callback:
            callback(request)
        assert request.url.host == "resources.example" and request.headers["authorization"] == "resources-secret"
        if request.url.path == "/public/capabilities":
            count += 1
            return httpx.Response(200, json={"serviceId": "changed" if count == identity_change else "resources-fixture"})
        kind = request.url.path.removeprefix("/parseAsset/")
        assert request.method == "POST" and kind in DOCUMENT_CLASSES and request.content == SOURCE.encode()
        return httpx.Response(200, content=json.dumps(SOURCES[kind] | (changes or {})).encode())
    api = AsyncClient("https://runtime.example", "runtime-secret",
        resources=Endpoint("https://resources.example", "resources-secret"), http_transport=httpx.MockTransport(handler))
    return api, calls


@pytest.mark.parametrize("kind", DOCUMENT_CLASSES)
def test_native_async_receipt_matches_sync_checked_immutable_view(kind):
    sync, _ = sync_setup()
    with sync:
        expected = SourcePreviews(sync).parse(SOURCE, kind=kind)
    async def run():
        api, calls = setup()
        async with api:
            receipt = await AsyncSourcePreviews(api).parse(SOURCE, kind=kind)
        assert isinstance(receipt, SourcePreview) and not isinstance(receipt, ParsedSource)
        assert receipt == expected and receipt.source_sha256 == hashlib.sha256(SOURCE.encode()).hexdigest()
        assert [r.url.path for r in calls] == ["/public/capabilities", "/parseAsset/" + kind, "/public/capabilities"]
        with pytest.raises(TypeError):
            receipt.raw["urn"] = "changed"
    asyncio.run(run())


@pytest.mark.parametrize("source", [None, b"source", "", " \n", "\ud800", "runtime-secret", "resources-secret", "x" * (1024 * 1024 + 1)])
def test_invalid_utf8_budget_or_credential_source_never_reaches_http(source):
    async def run():
        api, calls = setup()
        async with api:
            with pytest.raises(InvalidRequestError):
                await AsyncSourcePreviews(api).parse(source)
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("changes", [{"@CLASS": "unknown"}, {"serviceId": "other"}, {"sourceCode": "changed"},
    {"urn": "bad/../urn"}, {"metadata": {"secret": "resources-secret"}}, {"metadata": {"number": float("nan")}}])
def test_changed_parser_payload_is_refused(changes):
    async def run():
        api, _ = setup(changes=changes)
        async with api:
            with pytest.raises(InvalidRequestError if "secret" in changes.get("metadata", {}) else ProtocolError):
                await AsyncSourcePreviews(api).parse(SOURCE)
    asyncio.run(run())


def test_trim_policy_hashes_native_errors_and_identity_drift():
    async def run():
        api, _ = setup(changes={"sourceCode": SOURCE.strip(" \t\r\n")})
        async with api:
            previews = AsyncSourcePreviews(api)
            with pytest.raises(ProtocolError):
                await previews.parse(SOURCE)
            receipt = await previews.parse(SOURCE, allow_edge_trimming=True)
            assert receipt.edge_whitespace_trimmed and receipt.source_sha256 != receipt.parser_source_sha256
        api, _ = setup(changes={"notifications": [{"level": "Error", "message": "invalid parser input"}]})
        async with api:
            with pytest.raises(ServerError):
                await AsyncSourcePreviews(api).parse(SOURCE)
        api, _ = setup(identity_change=2)
        async with api:
            with pytest.raises(ProtocolError):
                await AsyncSourcePreviews(api).parse(SOURCE)
    asyncio.run(run())


def test_types_constructor_policy_and_endpoint_drift_refused_without_extra_requests():
    with Client("https://runtime.example", "test-only") as sync:
        with pytest.raises(InvalidRequestError):
            AsyncSourcePreviews(sync)
    async def run():
        api, calls = setup()
        async with api:
            with pytest.raises(InvalidRequestError):
                SourcePreviews(api)
            for options in ({"max_source_bytes": 0}, {"max_response_bytes": True}):
                with pytest.raises(InvalidRequestError):
                    AsyncSourcePreviews(api, **options)
            previews = AsyncSourcePreviews(api)
            with pytest.raises(UnsupportedOperationError):
                await previews.parse(SOURCE, kind="MODEL")
            with pytest.raises(InvalidRequestError):
                await previews.parse(SOURCE, allow_edge_trimming=1)
            assert not calls
            await previews.parse(SOURCE)
            count = len(calls)
            api.transport.endpoints["resources"] = Endpoint("https://other.invalid", "different-secret")
            with pytest.raises(ProtocolError):
                await previews.parse(SOURCE)
            assert len(calls) == count
    asyncio.run(run())


def test_native_cancellation_never_submits_or_cancels_remote_work():
    async def run():
        entered, calls = asyncio.Event(), []
        async def handler(request):
            calls.append(request.url.path)
            if request.url.path == "/public/capabilities":
                return httpx.Response(200, json={"serviceId": "resources-fixture"})
            entered.set()
            await asyncio.Event().wait()
        async with AsyncClient("https://runtime.example", "test-only", resources=Endpoint("https://resources.example", "token"),
            http_transport=httpx.MockTransport(handler)) as api:
            task = asyncio.create_task(AsyncSourcePreviews(api).parse(SOURCE))
            await asyncio.wait_for(entered.wait(), 1)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert calls == ["/public/capabilities", "/parseAsset/NAMESPACE"]
    asyncio.run(run())
