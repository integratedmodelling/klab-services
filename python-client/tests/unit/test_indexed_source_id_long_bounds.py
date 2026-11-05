"""Native source identity domain, not evidence that mocked source IDs exist remotely."""
import asyncio
import json
from pathlib import Path

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.dto import observation_from_wire
from klab_client.errors import ProtocolError, UnsupportedOperationError


def read(native_async, source_id, calls, *, detached=False):
    def observation(context):
        payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
        payload["id"] = source_id
        payload["observable"]["contextualization"] = "MEASURE"
        result = observation_from_wire(payload, context)
        if detached:
            result.metadata["im:storage-binding-source"] = 1
        return result
    def handler(request):
        calls.append(json.loads(request.content))
        return httpx.Response(200, text="234.5")
    async def run():
        async with AsyncClient("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
            http_transport=httpx.MockTransport(handler)) as api:
            return await observation(AsyncContext(api, {"id": "s.c", "serviceId": "runtime-id"})).fetch_data([0, 0])
    if native_async:
        return asyncio.run(run())
    with Client("https://runtime.invalid", "test-only", runtime_service_id="runtime-id",
        http_transport=httpx.MockTransport(handler)) as api:
        return observation(Context(api, {"id": "s.c", "serviceId": "runtime-id"})).fetch_data([0, 0])


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("source_id", [2**63, 2**100])
def test_oversized_native_source_identity_is_refused_before_read_http(native_async, source_id):
    calls = []
    with pytest.raises(ProtocolError):
        read(native_async, source_id, calls)
    assert not calls


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("source_id", [1, 2**63 - 1])
def test_positive_native_source_identity_wire_boundaries_remain_exact(native_async, source_id):
    calls = []
    data = read(native_async, source_id, calls)
    assert [point["sourceId"] for point in calls] == [source_id, source_id]
    assert data.observation_id == source_id and data.offsets == (0, 0)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("source_id,detached,error", [(0, False, UnsupportedOperationError),
    (-1, False, ProtocolError), (True, False, ProtocolError), (1, True, UnsupportedOperationError)])
def test_existing_source_identity_and_consumer_binding_refusals_remain_local(native_async, source_id, detached, error):
    calls = []
    with pytest.raises(error):
        read(native_async, source_id, calls, detached=detached)
    assert not calls
