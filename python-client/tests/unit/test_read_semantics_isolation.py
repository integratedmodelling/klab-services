import asyncio
from decimal import Decimal
import json
from pathlib import Path
from types import SimpleNamespace

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.dto import observation_from_wire


def native_semantics():
    return {"observable": "geography:Elevation in m", "unit": "m", "range": "",
        "currency": "", "contextualDimensions": "", "meaning": "geography:Elevation"}


def observation(context):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["observable"]["contextualization"] = "MEASURE"
    return observation_from_wire(payload, context)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("mutation", ["unit", "observable", "all-fields", "clear"])
def test_one_indexed_read_uses_one_semantics_snapshot_despite_caller_mutation(native_async, mutation):
    supplied = native_semantics()
    expected = dict(supplied)
    requests = []
    def handler(request):
        body = json.loads(request.content)
        requests.append(body)
        if len(requests) == 1:
            if mutation == "clear":
                supplied.clear()
            elif mutation == "all-fields":
                supplied.update(dict.fromkeys(supplied, "caller changed"))
            else:
                supplied[mutation] = "caller changed"
        return httpx.Response(200, text="234.5")
    async def scenario():
        async with AsyncClient("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(AsyncContext(client, {"id": "s.c", "serviceId": "runtime-id"}))
            return await result.fetch_data([0, 1, 0], semantics=supplied)
    if native_async:
        values = asyncio.run(scenario())
    else:
        with Client("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(Context(client, {"id": "s.c", "serviceId": "runtime-id"}))
            values = result.fetch_data([0, 1, 0], semantics=supplied)
    assert len(requests) == 3 and [r["offset"] for r in requests] == [0, 1, 0]
    assert all(r["semantics"] == expected for r in requests)
    assert values.observable == expected["observable"] and values.units == expected["unit"]
    assert values.values == (Decimal("234.5"),) * 3
    assert supplied != expected  # SDK must not undo the caller's mutation.


@pytest.mark.parametrize("native_async", [False, True])
def test_default_semantics_and_explicit_dict_remain_unmodified(native_async):
    supplied = native_semantics()
    original = dict(supplied)
    seen = []
    def handler(request):
        seen.append(json.loads(request.content)["semantics"])
        return httpx.Response(200, text="234.5")
    async def scenario():
        async with AsyncClient("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(AsyncContext(client, {"id": "s.c", "serviceId": "runtime-id"}))
            await result.fetch_data([0], semantics=supplied)
            return await result.fetch_data([0])
    if native_async:
        values = asyncio.run(scenario())
    else:
        with Client("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(Context(client, {"id": "s.c", "serviceId": "runtime-id"}))
            result.fetch_data([0], semantics=supplied)
            values = result.fetch_data([0])
    assert seen == [original, None] and supplied == original
    assert values.units == "m"


def test_live_fixture_request_uses_exposed_native_semantics_without_unit_guessing():
    from workflows.read_semantics_isolation import native_request
    raw = {"urn": "actual-native-urn", "unit": {"definition": "m", "contextual": False, "aggregatedDimensions": {}}}
    result = native_request(SimpleNamespace(units="m", observable=SimpleNamespace(raw=raw)))
    assert result == {"observable": "actual-native-urn", "unit": "m", "range": "", "currency": "", "contextualDimensions": "{}"}
    raw["unit"]["contextual"] = True
    with pytest.raises(AssertionError):
        native_request(SimpleNamespace(units="m", observable=SimpleNamespace(raw=raw)))
