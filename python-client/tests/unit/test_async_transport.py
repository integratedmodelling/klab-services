"""Native async I/O contracts, not scientific or deployed-service acceptance."""
import asyncio
import time
from types import SimpleNamespace

import httpx
import pytest

from klab_client._async_transport import AsyncTransport
from klab_client.errors import (AuthenticationError, AuthorizationError, ConfigurationError,
                               InvalidRequestError, MissingAssetError, ProtocolError,
                               ServerError, SubmissionOutcomeUnknown, TransportError)
from klab_client.transport import Endpoint


class Bytes(httpx.AsyncByteStream):
    def __init__(self, chunks, *, error=None, wait=None):
        self.chunks, self.error, self.wait = chunks, error, wait
        self.closed = False

    async def __aiter__(self):
        if self.wait:
            self.wait.set()
            await asyncio.Event().wait()
        for chunk in self.chunks:
            yield chunk
        if self.error:
            raise self.error

    async def aclose(self):
        self.closed = True


def transport(handler, **kwargs):
    return AsyncTransport({"runtime": Endpoint("https://runtime.invalid/runtime", "private-key"),
                           "reasoner": Endpoint("https://reasoner.invalid", "other-key")},
                          http_transport=httpx.MockTransport(handler), **kwargs)


@pytest.mark.parametrize("response", ["json", "text"])
@pytest.mark.parametrize("bounded", [False, True])
def test_origin_headers_encoding_and_response_closure(response, bounded):
    async def scenario():
        body = Bytes([b'{"valid":', b'false}'])
        async def handler(request):
            assert str(request.url) == "https://runtime.invalid/runtime/read?q=1"
            assert request.headers["Authorization"] == "private-key"
            assert request.headers["klab-scope"] == "s.c"
            assert request.headers["klab-service"] == "home"
            assert request.headers["Content-Type"] == "text/plain"
            assert request.content == b"definition"
            return httpx.Response(200, headers={"Content-Encoding": "identity", "Content-Length": "15"}, stream=body)
        async with transport(handler) as client:
            result = await client.request("runtime", "POST", "/read", scope="s.c", service_id="home",
                                          text="definition", params={"q": 1}, response=response,
                                          max_response_bytes=100 if bounded else None)
            assert result == ('{"valid":false}' if response == "text" else {"valid": False})
            assert body.closed
        assert client._http.is_closed
    asyncio.run(scenario())


@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (404, MissingAssetError), (422, InvalidRequestError), (307, ServerError),
    (500, ServerError)])
@pytest.mark.parametrize("ambiguous", [False, True])
def test_shared_error_mapping_redaction_and_no_redirect_retry(status, error, ambiguous):
    async def scenario():
        calls = []
        async def handler(request):
            calls.append(request)
            return httpx.Response(status, text="private-key other-key scoped-secret", headers={"Location": "https://untrusted.invalid"})
        async with transport(handler) as client:
            client.sensitive_values = ("scoped-secret",)
            expected = SubmissionOutcomeUnknown if ambiguous and status >= 500 else error
            with pytest.raises(expected) as caught:
                await client.request("runtime", "POST", "/write", ambiguous=ambiguous)
            assert "private-key" not in str(caught.value)
            assert "other-key" not in str(caught.value)
            assert "scoped-secret" not in str(caught.value)
            if not (ambiguous and status >= 500):
                assert caught.value.status_code == status
            assert len(calls) == 1
    asyncio.run(scenario())


@pytest.mark.parametrize("route,budget", [("//untrusted", None), ("/../escape", None),
                                        ("relative", None), ("/read", 0), ("/read", True)])
def test_invalid_requests_do_not_send(route, budget):
    async def scenario():
        async def handler(request):
            pytest.fail("Invalid request must not send")
        async with transport(handler) as client:
            with pytest.raises(InvalidRequestError):
                await client.request("runtime", "GET", route, max_response_bytes=budget)
    asyncio.run(scenario())


@pytest.mark.parametrize("mode", ["encoding", "budget", "json", "serialization", "network"])
@pytest.mark.parametrize("ambiguous", [False, True])
def test_failure_paths_close_and_do_not_replay(mode, ambiguous):
    async def scenario():
        calls, body = [], Bytes([b"invalid json" if mode == "json" else b"0123456789"])
        async def handler(request):
            calls.append(request)
            if mode == "network":
                raise httpx.ReadError("private-key must not leak")
            return httpx.Response(200, headers={"Content-Encoding": "gzip" if mode == "encoding" else "identity"}, stream=body)
        async with transport(handler) as client:
            expected = (InvalidRequestError if mode == "serialization" else
                        SubmissionOutcomeUnknown if mode == "network" and ambiguous else
                        TransportError if mode == "network" else ProtocolError)
            with pytest.raises(expected) as caught:
                await client.request("runtime", "POST", "/read", ambiguous=ambiguous,
                    json={"bad": object()} if mode == "serialization" else None,
                    max_response_bytes=4 if mode in {"encoding", "budget"} else None)
            assert "private-key" not in str(caught.value)
            assert len(calls) == (0 if mode == "serialization" else 1)
            if mode not in {"serialization", "network"}:
                assert body.closed
    asyncio.run(scenario())


@pytest.mark.parametrize("ambiguous", [False, True])
@pytest.mark.parametrize("cancel", [False, True])
def test_inflight_cancellation_and_total_deadline_close_body(ambiguous, cancel):
    async def scenario():
        entered = asyncio.Event()
        body = Bytes([], wait=entered)
        calls = []
        async def handler(request):
            calls.append(request)
            return httpx.Response(200, stream=body)
        async with transport(handler) as client:
            task = asyncio.create_task(client.request("runtime", "POST", "/write", ambiguous=ambiguous,
                deadline=None if cancel else time.monotonic() + .05))
            await asyncio.wait_for(entered.wait(), 1)
            if cancel:
                task.cancel()
                with pytest.raises(asyncio.CancelledError) as caught:
                    await task
                assert bool(getattr(caught.value, "__notes__", [])) == ambiguous
            else:
                with pytest.raises(SubmissionOutcomeUnknown if ambiguous else TransportError):
                    await task
            assert len(calls) == 1 and body.closed
    asyncio.run(scenario())


def test_credential_deadline_and_sync_callback_rejection_happen_before_http():
    async def scenario():
        async def handler(request):
            pytest.fail("No request allowed")
        async with transport(handler) as client:
            with pytest.raises(TransportError):
                await client.request("runtime", "POST", "/write", ambiguous=True, deadline=time.monotonic() - 1)
            client.credential_resolver = lambda *args: pytest.fail("Must not invoke blocking resolver")
            with pytest.raises(ConfigurationError):
                await client.request("runtime", "GET", "/read")
            async def slow(*args):
                await asyncio.Event().wait()
            client.credential_resolver = slow
            with pytest.raises(TransportError):
                await client.request("runtime", "POST", "/write", ambiguous=True, deadline=time.monotonic() + .01)
    asyncio.run(scenario())


@pytest.mark.parametrize("resolver_kind", ["function", "object"])
def test_native_async_resolvers_and_endpoint_credential_isolation(resolver_kind):
    async def scenario():
        async def callback(service, url, deadline):
            assert service == "reasoner" and url == "https://reasoner.invalid"
            assert deadline is None
            return "scoped-key"
        class Callback:
            async def __call__(self, *args):
                return await callback(*args)
        async def handler(request):
            assert request.headers["Authorization"] == "scoped-key"
            return httpx.Response(200, json={"ok": True})
        async with transport(handler) as client:
            client.credential_resolver = callback if resolver_kind == "function" else Callback()
            assert await client.request("reasoner", "GET", "/read") == {"ok": True}
    asyncio.run(scenario())


@pytest.mark.parametrize("status", [200, 401, 500])
@pytest.mark.parametrize("exit_kind", ["early", "error", "network", "consumed"])
def test_stream_lifetime_and_body_not_echoed(status, exit_kind):
    async def scenario():
        body = Bytes([b"private-key"], error=httpx.ReadError("private-key") if exit_kind == "network" else None)
        async def handler(request):
            assert request.headers["Accept-Encoding"] == "identity"
            assert request.headers["klab-scope"] == "s.c"
            return httpx.Response(status, stream=body)
        async with transport(handler) as client:
            expected = (AuthenticationError if status == 401 else ServerError if status == 500 else
                        RuntimeError if exit_kind == "error" else TransportError if exit_kind == "network" else None)
            async def use():
                async with client.stream("runtime", "/export", media_type="image/tiff", scope="s.c", service_id="home") as response:
                    if exit_kind == "error":
                        raise RuntimeError("consumer failure")
                    if exit_kind in {"network", "consumed"}:
                        async for chunk in response.aiter_raw():
                            assert chunk == b"private-key"
            if expected:
                with pytest.raises(expected) as caught:
                    await use()
                assert "private-key" not in str(caught.value)
            else:
                await use()
            assert body.closed
    asyncio.run(scenario())


def test_explicit_no_credential_requests_and_reused_stream_fail_honestly():
    async def scenario():
        async def handler(request):
            assert "Authorization" not in request.headers
            return httpx.Response(200, stream=Bytes([b"{}"]))
        async with AsyncTransport({"runtime": Endpoint("https://runtime.invalid")},
                                  http_transport=httpx.MockTransport(handler)) as client:
            assert await client.request("runtime", "GET", "/read") == {}
            async with client.stream("runtime", "/export", media_type="image/tiff") as response:
                assert await response.aread() == b"{}"
            with pytest.raises(ProtocolError, match="unread stream"):
                async with client.stream("runtime", "/export", media_type="image/tiff") as response:
                    await response.aread()
                    async for _ in response.aiter_raw():
                        pytest.fail("Consumed response must not be replayed")
    asyncio.run(scenario())


@pytest.mark.parametrize("phase", ["credentials", "chunk", "empty", "success"])
def test_deadline_checks_also_cover_immediately_ready_awaitables(phase, monkeypatch):
    from klab_client import _async_transport
    # A coroutine may finish without yielding to the scheduler. Explicit monotonic
    # checks must still enforce the total deadline, including an empty response.
    readings = iter({"credentials": [0, 0, 101], "chunk": [0, 0, 0, 101],
                     "empty": [0, 0, 0, 101], "success": [0] * 6}[phase])
    monkeypatch.setattr(_async_transport, "time", SimpleNamespace(monotonic=lambda: next(readings)))
    async def scenario():
        calls = []
        async def handler(request):
            calls.append(request)
            return httpx.Response(200, stream=Bytes([] if phase == "empty" else [b"{}"]))
        async with transport(handler) as client:
            if phase == "success":
                assert await client._request("runtime", "POST", "/write", deadline=100, ambiguous=True) == {}
            else:
                with pytest.raises(TransportError if phase == "credentials" else SubmissionOutcomeUnknown):
                    await client._request("runtime", "POST", "/write", deadline=100, ambiguous=True)
            assert len(calls) == (0 if phase == "credentials" else 1)
    asyncio.run(scenario())


@pytest.mark.parametrize("consumer_error", [False, True])
def test_async_close_failure_preserves_the_primary_error(consumer_error):
    async def scenario():
        client = transport(lambda request: httpx.Response(200, json={}))
        original_close = client.aclose
        async def failed_close():
            raise OSError("cleanup error")
        client.aclose = failed_close
        try:
            if consumer_error:
                with pytest.raises(ValueError, match="primary") as caught:
                    async with client:
                        raise ValueError("primary")
                assert caught.value.__notes__ == ["Async transport cleanup failed: OSError"]
            else:
                with pytest.raises(OSError, match="cleanup error"):
                    async with client:
                        pass
        finally:
            await original_close()
    asyncio.run(scenario())


@pytest.mark.parametrize("limit", [0, -1, True, 1.5])
def test_invalid_concurrency_does_not_construct_http_client(limit):
    with pytest.raises(ConfigurationError):
        AsyncTransport({}, max_concurrency=limit)


def test_queued_deadline_and_cancellation_never_send_or_take_another_slot():
    async def scenario():
        entered, release = asyncio.Event(), asyncio.Event()
        calls = []
        async def handler(request):
            path = request.url.path.removeprefix("/runtime")
            calls.append(path)
            if path == "/first":
                entered.set()
                await release.wait()
            return httpx.Response(200, json={})
        async with transport(handler, max_concurrency=1) as client:
            first = asyncio.create_task(client.request("runtime", "GET", "/first"))
            await asyncio.wait_for(entered.wait(), 1)
            with pytest.raises(TransportError, match="HTTP slot"):
                await client.request("runtime", "POST", "/never-sent", ambiguous=True, deadline=time.monotonic() + .01)
            queued = asyncio.create_task(client.request("runtime", "POST", "/cancelled-before-send", ambiguous=True))
            await asyncio.sleep(0)
            queued.cancel()
            with pytest.raises(asyncio.CancelledError) as caught:
                await queued
            assert not getattr(caught.value, "__notes__", [])
            assert calls == ["/first"]
            release.set()
            assert await first == {}
            async with client.stream("runtime", "/stream", media_type="image/tiff"):
                with pytest.raises(TransportError, match="HTTP slot"):
                    await client.request("runtime", "GET", "/stream-slot-not-free", deadline=time.monotonic() + .01)
            assert await client.request("runtime", "GET", "/last") == {}
            assert calls == ["/first", "/stream", "/last"]
    asyncio.run(scenario())


@pytest.mark.parametrize("credential", [None, "fresh-secret", 123, "", "bad\r\nheader"])
def test_resolved_credentials_are_validated_and_redacted_before_error_reporting(credential):
    async def scenario():
        calls = []
        async def handler(request):
            calls.append(request)
            if credential is None:
                assert "Authorization" not in request.headers
            else:
                assert request.headers["Authorization"] == credential
            return httpx.Response(403, text="fresh-secret private-key")
        async def resolve(*args):
            return credential
        async with transport(handler) as api:
            api.credential_resolver = resolve
            if credential in (None, "fresh-secret"):
                with pytest.raises(AuthorizationError) as caught:
                    await api.request("runtime", "GET", "/read")
                assert "fresh-secret" not in str(caught.value) if credential else "private-key" not in str(caught.value)
                assert len(calls) == 1
            else:
                with pytest.raises(ConfigurationError):
                    await api.request("runtime", "POST", "/not-sent", ambiguous=True)
                assert calls == []
    asyncio.run(scenario())
