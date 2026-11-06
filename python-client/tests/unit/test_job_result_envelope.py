"""FINISHED is not proof of a well-formed or successful scientific result."""
import asyncio

import httpx
import pytest

from klab_client import Client
from klab_client.async_client import AsyncClient, AsyncJob
from klab_client.client import Job
from klab_client.errors import JobFailedError, ProtocolError


def retrieve(payload, asynchronous):
    calls, decoded = [], []

    def handler(request):
        calls.append((request.method, request.url.path))
        assert request.method == "GET"
        if request.url.path == "/jobs/status/17":
            return httpx.Response(200, json={"status": "FINISHED"})
        assert request.url.path == "/jobs/retrieve/17"
        return httpx.Response(200, json=payload)

    def decode(value):
        decoded.append(value)
        return value

    kwargs = {"runtime_service_id": "runtime-id", "http_transport": httpx.MockTransport(handler)}
    try:
        if asynchronous:
            async def scenario():
                async with AsyncClient("https://runtime.invalid", **kwargs) as client:
                    return await AsyncJob(client, 17, "session.context", decoder=decode).result()
            return asyncio.run(scenario()), decoded
        with Client("https://runtime.invalid", **kwargs) as client:
            return Job(client, 17, "session.context", decoder=decode).result(), decoded
    finally:
        # No retry, submission, cancellation, release or decoder after rejection.
        assert calls == [("GET", "/jobs/status/17"), ("GET", "/jobs/retrieve/17")]
        if ("empty" in payload and payload["empty"] is not False) or payload.get("notifications"):
            assert decoded == []


@pytest.mark.parametrize("asynchronous", [False, True], ids=["sync", "async"])
@pytest.mark.parametrize("empty", [None, 0, 1, 0.0, "false", "true", [], {}])
def test_nonboolean_empty_is_protocol_error(empty, asynchronous):
    with pytest.raises(ProtocolError, match="empty.*boolean"):
        retrieve({"empty": empty}, asynchronous)


@pytest.mark.parametrize("asynchronous", [False, True], ids=["sync", "async"])
@pytest.mark.parametrize("payload", [{}, {"empty": False}])
def test_missing_or_false_empty_preserves_decoder(payload, asynchronous):
    result, decoded = retrieve(payload, asynchronous)
    assert result == payload
    assert decoded == [payload]


@pytest.mark.parametrize("asynchronous", [False, True], ids=["sync", "async"])
@pytest.mark.parametrize("payload", [
    {"empty": True},
    {"empty": False, "notifications": [{"level": "Error", "message": "Remote computation failed"}]},
    {"empty": True, "notifications": [{"level": "Severe", "message": "Remote computation failed"}]},
])
def test_actual_empty_and_error_remain_job_failures(payload, asynchronous):
    with pytest.raises(JobFailedError) as caught:
        retrieve(payload, asynchronous)
    assert caught.value.job_id == 17
