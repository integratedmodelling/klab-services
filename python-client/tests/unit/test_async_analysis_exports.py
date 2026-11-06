"""Native async scientific export association; no scientific extras or hidden loops."""
import asyncio
from dataclasses import FrozenInstanceError
import hashlib
import json
from pathlib import Path

import httpx
import pytest

import klab_client
from klab_client import AsyncClient, AsyncContext, Client, Context, async_export_for_analysis, export_for_analysis
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError, ProtocolError, TransportError


def observation(client):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["urn"] = "s.c.42"
    context_type = AsyncContext if isinstance(client, AsyncClient) else Context
    return observation_from_wire(payload, context_type(client, {"id": "s.c", "serviceId": "home"}))


def test_native_async_analysis_export_is_an_explicit_public_operation():
    assert hasattr(klab_client, "async_export_for_analysis")


def test_sync_analysis_export_refuses_async_binding_before_creating_a_coroutine(tmp_path):
    async def run():
        calls = []
        def handler(request):
            calls.append(request)
            raise AssertionError("Wrong facade must not perform I/O")
        async with AsyncClient("https://runtime.invalid/runtime", runtime_service_id="home",
                               http_transport=httpx.MockTransport(handler)) as client:
            with pytest.raises(InvalidRequestError):
                export_for_analysis(observation(client), tmp_path / "result.tif")
        assert not calls
    asyncio.run(run())


MEDIA = "image/tiff;application=geotiff"
BODY = b"exact protocol fixture bytes, not claimed scientific TIFF data"


class Stream(httpx.AsyncByteStream):
    def __init__(self, error=False, dispatched=None):
        self.error, self.dispatched, self.closed = error, dispatched, False

    async def __aiter__(self):
        yield BODY[:10]
        if self.dispatched is not None:
            self.dispatched.set()
            await asyncio.Event().wait()
        if self.error:
            raise httpx.ReadError("deliberate test interruption")
        yield BODY[10:]

    async def aclose(self):
        self.closed = True


def configured(stream, calls, media=MEDIA):
    async def handler(request):
        calls.append(request)
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json={"serviceId": "home", "exportSchemata": {"fixture": [{
                "schemaId": "test.native", "knowledgeClass": "OBSERVATION", "mediaTypes": [MEDIA], "extensions": ["tif"]}]}})
        assert request.url.path == "/runtime/export/OBSERVATION/s.c.42"
        assert request.headers["accept"] == MEDIA and request.headers["accept-encoding"] == "identity"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        return httpx.Response(200, headers={"content-type": media, "content-length": str(len(BODY))}, stream=stream)
    return AsyncClient("https://runtime.invalid/runtime", "test-only-token", runtime_service_id="home",
                       http_transport=httpx.MockTransport(handler))


def test_awaited_export_captures_exact_bytes_handle_units_without_scientific_extras(tmp_path):
    async def run():
        calls, stream = [], Stream()
        async with configured(stream, calls) as client:
            asset = observation(client)
            result = await async_export_for_analysis(asset, tmp_path / "result.tif", max_bytes=len(BODY))
            assert result.observation == asset.to_handle() and result.source_units == "m"
            assert result.sha256 == hashlib.sha256(BODY).hexdigest()
            assert result.export.bytes_written == len(BODY) and result.export.path.read_bytes() == BODY
            assert result.exported_at.tzinfo is not None
            with pytest.raises(FrozenInstanceError):
                result.sha256 = "changed"
        assert stream.closed and len(calls) == 2 and all(request.method == "GET" for request in calls)
        assert not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


@pytest.mark.parametrize("mode,error", [("interrupted", TransportError), ("wrong-media", ProtocolError), ("limit", ProtocolError)])
def test_async_analysis_failures_preserve_existing_file_and_never_return_an_artifact(tmp_path, mode, error):
    async def run():
        destination = tmp_path / "preserved.tif"
        destination.write_bytes(b"original user bytes")
        calls, stream = [], Stream(error=mode == "interrupted")
        async with configured(stream, calls, media="image/png" if mode == "wrong-media" else MEDIA) as client:
            with pytest.raises(error):
                await async_export_for_analysis(observation(client), destination, overwrite=True,
                    max_bytes=len(BODY) - 1 if mode == "limit" else len(BODY))
        assert stream.closed and len(calls) == 2
        assert destination.read_bytes() == b"original user bytes"
        assert not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


def test_async_analysis_cancellation_closes_stream_without_disposal_or_partial_publication(tmp_path):
    async def run():
        calls, dispatched = [], asyncio.Event()
        stream = Stream(dispatched=dispatched)
        async with configured(stream, calls) as client:
            task = asyncio.create_task(async_export_for_analysis(observation(client), tmp_path / "cancelled.tif"))
            await asyncio.wait_for(dispatched.wait(), timeout=2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert stream.closed and len(calls) == 2
        assert not (tmp_path / "cancelled.tif").exists() and not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


@pytest.mark.parametrize("unbound", [True, False])
def test_async_analysis_refuses_unbound_or_sync_bound_source_before_io(tmp_path, unbound):
    calls = []
    def handler(request):
        calls.append(request)
        raise AssertionError("Wrong facade must not perform I/O")
    with Client("https://runtime.invalid/runtime", runtime_service_id="home", http_transport=httpx.MockTransport(handler)) as client:
        asset = observation(client)
        if unbound:
            asset._context = None
        async def run():
            with pytest.raises(InvalidRequestError):
                await async_export_for_analysis(asset, tmp_path / "bad.tif")
        asyncio.run(run())
    assert not calls
