"""Bootstrap cleanup faults preserve denial/protocol/interrupt state and privacy."""
import traceback

import httpx
import pytest

from klab_client import HubCertificateProvider
from klab_client._bootstrap import MAX_BOOTSTRAP_BYTES, wire_request
from klab_client.errors import (AuthenticationError, AuthorizationError, ProtocolError, TransportError)
from test_connection import HUB, TOKEN, certificate, profile


class FaultStream(httpx.SyncByteStream):
    def __init__(self, content=b"{}", *, read_error=None, close_error=None):
        self.content = content
        self.read_error = read_error
        self.close_error = close_error
        self.close_calls = 0

    def __iter__(self):
        if self.read_error is not None:
            raise self.read_error
        yield self.content

    def close(self):
        self.close_calls += 1
        if self.close_error is not None:
            raise self.close_error


class FaultTransport(httpx.MockTransport):
    def __init__(self, handler, *, close_error=None):
        super().__init__(handler)
        self.close_error = close_error
        self.close_calls = 0

    def close(self):
        self.close_calls += 1
        if self.close_error is not None:
            raise self.close_error


def assert_private(error, *secrets):
    rendered = "".join(traceback.format_exception(error))
    assert all(secret not in rendered for secret in secrets)


@pytest.mark.parametrize("status,error_type", [(401, AuthenticationError), (403, AuthorizationError)])
def test_denial_survives_both_stream_and_client_close_failures(status, error_type):
    stream = FaultStream(close_error=OSError(TOKEN))
    transport = FaultTransport(lambda request: httpx.Response(status, stream=stream), close_error=RuntimeError(TOKEN))
    with pytest.raises(error_type, match="HTTP " + str(status)) as caught:
        wire_request(HUB, "POST", transport=transport)
    assert caught.value.__notes__ == ["Connection response cleanup failed: OSError",
                                     "Connection client cleanup failed: RuntimeError"]
    assert stream.close_calls == transport.close_calls == 1
    assert_private(caught.value, TOKEN)


def test_certificate_denial_survives_close_failure_without_signature_exposure(tmp_path):
    signature = "private-signature-DEF456"
    transport = FaultTransport(lambda request: httpx.Response(401), close_error=OSError(signature))
    provider = HubCertificateProvider(certificate(tmp_path), http_transport=transport)
    with pytest.raises(AuthenticationError, match="HTTP 401") as caught:
        provider.acquire(profile(hub_url=HUB))
    assert caught.value.__notes__ == ["Connection client cleanup failed: OSError"]
    assert transport.close_calls == 1
    assert_private(caught.value, signature)


@pytest.mark.parametrize("content", [b"invalid-json", b"[]"], ids=["malformed-json", "nonobject"])
def test_protocol_failure_survives_client_close_failure(content):
    stream = FaultStream(content)
    transport = FaultTransport(lambda request: httpx.Response(200, stream=stream), close_error=OSError(TOKEN))
    with pytest.raises(ProtocolError) as caught:
        wire_request(HUB, "GET", transport=transport)
    assert caught.value.__notes__ == ["Connection client cleanup failed: OSError"]
    assert stream.close_calls == transport.close_calls == 1
    assert_private(caught.value, TOKEN)


def test_metadata_limit_survives_both_close_failures():
    stream = FaultStream(b"x" * (MAX_BOOTSTRAP_BYTES + 1), close_error=OSError(TOKEN))
    transport = FaultTransport(lambda request: httpx.Response(200, stream=stream), close_error=OSError(TOKEN))
    with pytest.raises(ProtocolError, match="metadata budget") as caught:
        wire_request(HUB, "GET", transport=transport)
    assert len(caught.value.__notes__) == 2
    assert stream.close_calls == transport.close_calls == 1
    assert_private(caught.value, TOKEN)


def test_read_failure_is_mapped_before_cleanup_and_both_resources_close():
    stream = FaultStream(read_error=httpx.ReadTimeout(TOKEN), close_error=OSError(TOKEN))
    transport = FaultTransport(lambda request: httpx.Response(200, stream=stream), close_error=OSError(TOKEN))
    with pytest.raises(TransportError, match="transport failed") as caught:
        wire_request(HUB, "GET", transport=transport)
    assert len(caught.value.__notes__) == 2
    assert stream.close_calls == transport.close_calls == 1
    assert_private(caught.value, TOKEN)


def test_interrupt_remains_the_primary_exception_when_cleanup_fails():
    primary = KeyboardInterrupt("interrupted")
    stream = FaultStream(read_error=primary, close_error=OSError(TOKEN))
    transport = FaultTransport(lambda request: httpx.Response(200, stream=stream), close_error=OSError(TOKEN))
    with pytest.raises(KeyboardInterrupt) as caught:
        wire_request(HUB, "GET", transport=transport)
    assert caught.value is primary
    assert traceback.extract_tb(primary.__traceback__)[-1].name == "__iter__"
    assert len(primary.__notes__) == 2
    assert stream.close_calls == transport.close_calls == 1
    assert_private(primary, TOKEN)


@pytest.mark.parametrize("close_error", [OSError(TOKEN), RuntimeError(TOKEN)], ids=["os-error", "runtime-error"])
def test_iterator_EOF_close_failure_is_sanitized(close_error):
    stream = FaultStream(close_error=close_error)
    transport = FaultTransport(lambda request: httpx.Response(200, stream=stream))
    with pytest.raises(TransportError, match="transport failed") as caught:
        wire_request(HUB, "GET", transport=transport)
    assert stream.close_calls == transport.close_calls == 1
    assert_private(caught.value, TOKEN)


def test_successful_body_with_client_close_failure_reports_safe_transport_error():
    transport = FaultTransport(lambda request: httpx.Response(200, json={}), close_error=OSError(TOKEN))
    with pytest.raises(TransportError, match="cleanup failed") as caught:
        wire_request(HUB, "GET", transport=transport)
    assert caught.value.__notes__ == ["Connection client cleanup failed: OSError"]
    assert transport.close_calls == 1
    assert_private(caught.value, TOKEN)


def test_successful_body_attempts_client_close_after_response_close_failure(monkeypatch):
    response = httpx.Response(200, json={})
    closes = []
    def failed_close():
        closes.append("response")
        raise OSError(TOKEN)
    monkeypatch.setattr(response, "close", failed_close)
    transport = FaultTransport(lambda request: response, close_error=RuntimeError(TOKEN))
    with pytest.raises(TransportError, match="cleanup failed") as caught:
        wire_request(HUB, "GET", transport=transport)
    assert closes == ["response"] and transport.close_calls == 1
    assert caught.value.__notes__ == ["Connection response cleanup failed: OSError",
                                     "Connection client cleanup failed: RuntimeError"]
    assert_private(caught.value, TOKEN)


def test_standalone_close_interrupt_is_not_changed_into_authentication_failure():
    interrupt = KeyboardInterrupt("interrupted during cleanup")
    transport = FaultTransport(lambda request: httpx.Response(200, json={}), close_error=interrupt)
    with pytest.raises(KeyboardInterrupt) as caught:
        wire_request(HUB, "GET", transport=transport)
    assert caught.value is interrupt
    assert interrupt.__notes__ == ["Connection client cleanup failed: KeyboardInterrupt"]


def test_client_construction_failure_is_sanitized_without_unallocated_cleanup(monkeypatch):
    def failed_client(**kwargs):
        raise OSError(TOKEN)
    monkeypatch.setattr("klab_client._bootstrap.httpx.Client", failed_client)
    with pytest.raises(TransportError, match="transport failed") as caught:
        wire_request(HUB, "GET")
    assert_private(caught.value, TOKEN)
