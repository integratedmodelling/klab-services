"""RC-P2.2: native BaseService temporal export query, never a new chunk protocol."""
import asyncio
import json
from pathlib import Path

import httpx
import pytest

from klab_client import (AsyncClient, AsyncContext, Client, Context, ScanSlice,
                         async_export_for_analysis, export_for_analysis)
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError, UnsupportedOperationError

MEDIA = "image/tiff;application=geotiff"
SELECTED = ScanSlice("TEMPORAL_TRANSITION", "month:2014-01", 1388534400000, 1391212800000)


class Stream(httpx.SyncByteStream, httpx.AsyncByteStream):
    def __iter__(self):
        yield b"selected native protocol bytes"

    async def __aiter__(self):
        yield b"selected native protocol bytes"


def asset(client):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["urn"] = "s.c.42"
    context_type = AsyncContext if isinstance(client, AsyncClient) else Context
    return observation_from_wire(payload, context_type(client, {"id": "s.c", "serviceId": "home"}))


def configured(mode, calls, *, expected=SELECTED, on_capability=None, status=200):
    def handler(request):
        calls.append(request)
        if request.url.path.endswith("/public/capabilities"):
            if on_capability:
                on_capability()
            return httpx.Response(200, json={"serviceId": "home", "exportSchemata": {"fixture": [{
                "schemaId": "test.native", "knowledgeClass": "OBSERVATION", "mediaTypes": [MEDIA], "extensions": ["tif"]}]}})
        assert request.url.path == "/runtime/export/OBSERVATION/s.c.42"
        expected_params = {} if expected is None else {"storageEvent": expected.key, "storageStart": str(expected.start), "storageEnd": str(expected.end)}
        assert dict(request.url.params) == expected_params
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        return httpx.Response(status, stream=Stream(), headers={"content-type": MEDIA})
    client_type = AsyncClient if mode == "async" else Client
    return client_type("https://runtime.invalid/runtime", "test-only-token", runtime_service_id="home", http_transport=httpx.MockTransport(handler))


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_selected_temporal_export_transmits_native_fields_and_retains_requested_selection(mode, tmp_path):
    calls = []
    if mode == "async":
        async def run():
            async with configured(mode, calls) as client:
                return await asset(client).export_to_file(tmp_path / "temporal.tif", media_type=MEDIA, slice=SELECTED)
        result = asyncio.run(run())
    else:
        with configured(mode, calls) as client:
            result = asset(client).export_to_file(tmp_path / "temporal.tif", media_type=MEDIA, slice=SELECTED)
    assert result.path.read_bytes() == b"selected native protocol bytes"
    assert result.requested_slice == SELECTED
    assert len(calls) == 2  # Capabilities + one selected export; never retry unsliced.


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("selected", [None, ScanSlice("TEMPORAL_TRANSITION", "edge", -(2**63), 2**63 - 1)])
def test_default_is_unselected_and_java_long_bounds_are_not_float_coerced(mode, selected, tmp_path):
    calls = []
    if mode == "async":
        async def run():
            async with configured(mode, calls, expected=selected) as client:
                return await asset(client).export_to_file(tmp_path / "selected.tif", media_type=MEDIA, slice=selected)
        result = asyncio.run(run())
    else:
        with configured(mode, calls, expected=selected) as client:
            result = asset(client).export_to_file(tmp_path / "selected.tif", media_type=MEDIA, slice=selected)
    assert result.requested_slice == selected


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_mutable_slice_input_is_snapshotted_before_capability_io(mode, tmp_path):
    calls, selected = [], SELECTED.to_wire()
    def change():
        selected.update(key="changed", start=0, end=1)
    if mode == "async":
        async def run():
            async with configured(mode, calls, on_capability=change) as client:
                return await async_export_for_analysis(asset(client), tmp_path / "associated.tif", slice=selected)
        artifact = asyncio.run(run())
    else:
        with configured(mode, calls, on_capability=change) as client:
            artifact = export_for_analysis(asset(client), tmp_path / "associated.tif", slice=selected)
    assert selected["key"] == "changed" and artifact.export.requested_slice == SELECTED
    assert artifact.observation.observation_id == 42 and artifact.source_units == "m"


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("selected,error", [
    (ScanSlice.initialization(), UnsupportedOperationError),
    (ScanSlice("INPUT_CHANGE", "input", 0, 1), UnsupportedOperationError),
    (ScanSlice("EVENT", "event", 0, 0), UnsupportedOperationError),
    (SELECTED.to_wire() | {"revision": 3}, UnsupportedOperationError),
    (SELECTED.to_wire() | {"start": True}, InvalidRequestError),
    (SELECTED.to_wire() | {"end": 2**63}, InvalidRequestError),
    (SELECTED.to_wire() | {"key": "x" * 4097}, InvalidRequestError),
    (SELECTED.to_wire() | {"key": "bad\nkey"}, InvalidRequestError),
    (SELECTED.to_wire() | {"key": "bad\x7fkey"}, InvalidRequestError),
    (SELECTED.to_wire() | {"key": "bad\ud800key"}, InvalidRequestError),
    (SELECTED.to_wire() | {"key": "test-only-token"}, InvalidRequestError),
])
def test_unsupported_or_malformed_selection_fails_before_any_http(mode, selected, error, tmp_path):
    calls = []
    if mode == "async":
        async def run():
            async with configured(mode, calls) as client:
                with pytest.raises(error):
                    await asset(client).export_to_file(tmp_path / "bad.tif", media_type=MEDIA, slice=selected)
        asyncio.run(run())
    else:
        with configured(mode, calls) as client:
            with pytest.raises(error):
                asset(client).export_to_file(tmp_path / "bad.tif", media_type=MEDIA, slice=selected)
    assert not calls and not list(tmp_path.iterdir())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_missing_selected_state_is_not_retried_without_selection(mode, tmp_path):
    calls = []
    destination = tmp_path / "preserved.tif"
    destination.write_bytes(b"original user result")
    if mode == "async":
        async def run():
            async with configured(mode, calls, status=422) as client:
                with pytest.raises(InvalidRequestError):
                    await asset(client).export_to_file(destination, media_type=MEDIA, overwrite=True, slice=SELECTED)
        asyncio.run(run())
    else:
        with configured(mode, calls, status=422) as client:
            with pytest.raises(InvalidRequestError):
                asset(client).export_to_file(destination, media_type=MEDIA, overwrite=True, slice=SELECTED)
    assert len(calls) == 2 and destination.read_bytes() == b"original user result"
    assert not list(tmp_path.glob(".klab-export-*"))
