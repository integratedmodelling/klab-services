import asyncio
from dataclasses import FrozenInstanceError
from decimal import Decimal
import json
from pathlib import Path

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context, ScanSlice
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError
from klab_client.scan_slices import _slice_to_wire


@pytest.mark.parametrize("event_type,start,end", [
    ("INITIALIZATION", 0, 0), ("INITIALIZATION", 2, -2),
    ("TEMPORAL_TRANSITION", -1000, 0), ("TEMPORAL_TRANSITION", -(2**63), 2**63 - 1),
    ("EVENT", 0, 0), ("EVENT", -20, -10),
    ("INPUT_CHANGE", 42, 42), ("INPUT_CHANGE", -100, 100)])
def test_exact_native_types_intervals_and_long_edges(event_type, start, end):
    value = ScanSlice(event_type, "opaque-key:not-derived", start, end)
    assert _slice_to_wire(value) == {"type": event_type, "key": "opaque-key:not-derived", "start": start, "end": end}
    assert _slice_to_wire(value.to_wire()) == value.to_wire()


INVALID = [
    {"type": "OTHER"}, {"type": None}, {"type": []}, {"key": " \t\n"}, {"key": ""}, {"key": None},
    {"start": True}, {"start": 0.0}, {"start": "0"}, {"start": None},
    {"start": -(2**63) - 1}, {"end": 2**63}, {"end": False},
    {"type": "TEMPORAL_TRANSITION", "start": 1, "end": 1},
    {"type": "TEMPORAL_TRANSITION", "start": 2, "end": 1},
    {"type": "INPUT_CHANGE", "start": 2, "end": 1},
    {"type": "EVENT", "start": 2, "end": 1},
]


@pytest.mark.parametrize("changes", INVALID)
def test_typed_and_dict_validation_refuse_same_contract_errors(changes):
    fields = {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0} | changes
    with pytest.raises(InvalidRequestError):
        ScanSlice(**fields)
    with pytest.raises(InvalidRequestError):
        _slice_to_wire(fields)


def test_initialization_factory_is_immutable_and_wire_dict_is_detached():
    value = ScanSlice.initialization()
    assert value == ScanSlice("INITIALIZATION", "0-0", 0, 0)
    with pytest.raises(FrozenInstanceError):
        value.start = 4
    wire = value.to_wire()
    wire["key"] = "changed"
    assert value.key == "0-0"
    raw = value.to_wire() | {"extension": "retained"}
    result = _slice_to_wire(raw)
    raw["start"] = 10
    assert result["start"] == 0 and result["extension"] == "retained"


@pytest.mark.parametrize("value", [None, [], "time", {}, {"type": "EVENT"}])
def test_non_slice_and_missing_fields_refused(value):
    with pytest.raises(InvalidRequestError):
        _slice_to_wire(value)


def observation(context):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["observable"]["contextualization"] = "MEASURE"
    return observation_from_wire(payload, context)


@pytest.mark.parametrize("native_async", [False, True])
def test_sync_async_reads_use_native_slice_and_refuse_invalid_before_http(native_async):
    calls = []
    selected = ScanSlice("INPUT_CHANGE", "committed-input-key", -100, -100)
    async def scenario():
        async with AsyncClient("https://runtime.invalid", "token", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(AsyncContext(client, {"id": "s.c", "serviceId": "runtime-id"}))
            for changes in INVALID:
                with pytest.raises(InvalidRequestError):
                    await result.fetch_data([0], slice=ScanSlice.initialization().to_wire() | changes)
            assert not calls
            data = await result.fetch_data([0, 0], slice=selected)
            assert data.slice == selected.to_wire() and data.values == (Decimal("123.25"),) * 2
    def handler(request):
        assert request.url.path == "/api/v1/observation/value"
        assert request.headers["klab-scope"] == "s.c"
        # This is a direct read on the home Runtime, not a peer resolution.
        assert request.url.host == "runtime.invalid"
        assert json.loads(request.content)["slice"] == selected.to_wire()
        calls.append(request)
        return httpx.Response(200, text="123.25")
    if native_async:
        asyncio.run(scenario())
    else:
        with Client("https://runtime.invalid", "token", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(Context(client, {"id": "s.c", "serviceId": "runtime-id"}))
            for changes in INVALID:
                with pytest.raises(InvalidRequestError):
                    result.fetch_data([0], slice=ScanSlice.initialization().to_wire() | changes)
            assert not calls
            data = result.fetch_data([0, 0], slice=selected)
            assert data.slice == selected.to_wire() and data.values == (Decimal("123.25"),) * 2
    assert len(calls) == 2
