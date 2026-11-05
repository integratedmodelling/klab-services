"""Native wire-domain checks; mocked maxlong reads do not prove cell existence."""
import asyncio
import json
from pathlib import Path

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError


def observation(context):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["observable"]["contextualization"] = "MEASURE"
    return observation_from_wire(payload, context)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("offsets", [[2**63], [0, 2**63], [2**100, 0]])
def test_out_of_long_offsets_rejected_before_any_http(native_async, offsets):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(200, text="234.5")
    async def scenario():
        async with AsyncClient("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(AsyncContext(client, {"id": "s.c", "serviceId": "runtime-id"}))
            with pytest.raises(InvalidRequestError):
                await result.fetch_data(iter(offsets))
    if native_async:
        asyncio.run(scenario())
    else:
        with Client("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(Context(client, {"id": "s.c", "serviceId": "runtime-id"}))
            with pytest.raises(InvalidRequestError):
                result.fetch_data(iter(offsets))
    assert not calls


@pytest.mark.parametrize("native_async", [False, True])
def test_zero_maxlong_duplicates_and_bounded_iterable_domain_remain_exact(native_async):
    seen = []
    offsets = [0, 2**63 - 1, 0] + [1] * 253
    def handler(request):
        seen.append(json.loads(request.content)["offset"])
        return httpx.Response(200, text="234.5")
    async def scenario():
        async with AsyncClient("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(AsyncContext(client, {"id": "s.c", "serviceId": "runtime-id"}))
            for invalid in ([], [True], [-1], [1.0], range(257)):
                with pytest.raises(InvalidRequestError):
                    await result.fetch_data(invalid)
            assert not seen
            return await result.fetch_data(iter(offsets))
    if native_async:
        values = asyncio.run(scenario())
    else:
        with Client("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as client:
            result = observation(Context(client, {"id": "s.c", "serviceId": "runtime-id"}))
            for invalid in ([], [True], [-1], [1.0], range(257)):
                with pytest.raises(InvalidRequestError):
                    result.fetch_data(invalid)
            assert not seen
            values = result.fetch_data(iter(offsets))
    assert seen == offsets and values.offsets == tuple(offsets) and len(values.values) == 256
