"""Advertised export contracts, incremental file IO and failure-safe publication."""
from dataclasses import FrozenInstanceError
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import tracemalloc
import threading

import httpx
import pytest

from klab_client import Client, Context, ExportIOError
from klab_client.dto import observation_from_wire
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, TransportError, UnsupportedOperationError)

MIME = "application/geo+json"


class Stream(httpx.SyncByteStream):
    def __init__(self, chunks, error=None, callback=None):
        self.chunks, self.error, self.callback = chunks, error, callback
        self.closed, self.iterated = False, False

    def __iter__(self):
        self.iterated = True
        for chunk in self.chunks:
            if self.callback:
                self.callback()
            yield chunk
        if self.error:
            raise self.error

    def close(self):
        self.closed = True


def schema(**changes):
    return {"schemaId": "geospatial.io.geojson", "knowledgeClass": "OBSERVATION",
        "type": "STREAM", "mediaTypes": [MIME], "extensions": ["geojson", "json"],
        "description": "Substantial geometry", **changes}


def capabilities(entries=None):
    return {"serviceId": "home", "exportSchemata": {"geospatial.io": [schema()] if entries is None else entries}}


def asset(client):
    value = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    value["urn"] = "s.c:individuals:named"  # Export derives the supported context-qualified numeric ID.
    return observation_from_wire(value, Context(client, {"id": "s.c", "serviceId": "home"}, token="s.c.8#3"))


def client(stream, *, headers=None, status=200, advertised=None, calls=None):
    def handler(request):
        if calls is not None:
            calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json=capabilities() if advertised is None else advertised)
        assert request.url.path == "/runtime/export/OBSERVATION/s.c.42"
        assert request.headers["accept"] == MIME
        assert request.headers["accept-encoding"] == "identity"
        assert request.headers["authorization"] == "issued-secret"
        assert request.headers["klab-scope"] == "s.c.8#3"
        assert request.headers["klab-service"] == "home"
        return httpx.Response(status, headers={"content-type": MIME, **(headers or {})}, stream=stream)
    return Client("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
        http_transport=httpx.MockTransport(handler))


def test_streamed_export_is_published_only_after_close_and_keeps_exact_bytes(tmp_path):
    destination = tmp_path / "result.geojson"
    stream = Stream([b'{"type":', b'"FeatureCollection"}'], callback=lambda: assert_absent(destination))
    calls = []
    with client(stream, headers={"content-length": "28", "content-type": MIME + "; charset=UTF-8"}, calls=calls) as current:
        result = asset(current).export_to_file(destination, media_type=MIME)
    assert destination.read_bytes() == b'{"type":"FeatureCollection"}'
    assert stream.closed and result.bytes_written == 28 and result.path == destination
    assert result.schema_id == "geospatial.io.geojson" and result.media_type == MIME
    with pytest.raises(FrozenInstanceError):
        result.bytes_written = 0
    assert len(calls) == 2 and not list(tmp_path.glob(".klab-export-*"))


def assert_absent(path):
    assert not path.exists()


def test_large_generated_response_has_bounded_client_memory(tmp_path):
    block = b"x" * 65536
    stream = Stream(block for _ in range(1024))
    with client(stream, headers={"content-length": str(64 * 1024 * 1024)}) as current:
        observation = asset(current)
        tracemalloc.start()
        try:
            result = observation.export_to_file(tmp_path / "large", media_type=MIME)
            peak = tracemalloc.get_traced_memory()[1]
        finally:
            tracemalloc.stop()
    assert result.bytes_written == 64 * 1024 * 1024 and stream.closed
    assert peak < 2 * 1024 * 1024  # 64 MiB delivered, without a response-sized client allocation.


@pytest.mark.parametrize("error", [httpx.ReadError("issued-secret"), KeyboardInterrupt()])
def test_interrupted_stream_preserves_existing_destination_and_closes_response(tmp_path, error):
    destination = tmp_path / "existing"
    destination.write_bytes(b"original")
    stream = Stream([b"partial"], error=error)
    with client(stream) as current:
        with pytest.raises(TransportError if isinstance(error, httpx.ReadError) else KeyboardInterrupt) as failure:
            asset(current).export_to_file(destination, media_type=MIME, overwrite=True)
        assert "issued-secret" not in str(failure.value)
    assert stream.closed and destination.read_bytes() == b"original"
    assert not list(tmp_path.glob(".klab-export-*"))


@pytest.mark.parametrize("headers,status,chunks", [
    ({"content-type": "image/png"}, 200, [b"data"]),
    ({"content-type": "image/tiff;application=other"}, 200, [b"data"]),
    ({"content-type": ""}, 200, [b"data"]),
    ({"content-encoding": "gzip"}, 200, [b"data"]),
    ({}, 206, [b"partial"]), ({"content-length": "20"}, 200, [b"short"]),
    ({"content-length": "1"}, 200, [b"long"]), ({"content-length": "-1"}, 200, [b"data"]),
    ({"content-length": "9" * 20}, 200, [b"data"]), ({"content-length": "١".encode("utf-8")}, 200, [b"data"]),
    ({}, 200, [])])
def test_bad_representation_or_incomplete_content_never_publishes(tmp_path, headers, status, chunks):
    stream = Stream(chunks)
    with client(stream, headers=headers, status=status) as current:
        with pytest.raises(ProtocolError):
            asset(current).export_to_file(tmp_path / "result", media_type=MIME)
    assert stream.closed and not list(tmp_path.iterdir())


@pytest.mark.parametrize("declared", [None, "100"])
def test_optional_byte_limit_rejects_declared_or_actual_excess(tmp_path, declared):
    stream = Stream([b"too much"])
    with client(stream, headers={} if declared is None else {"content-length": declared}) as current:
        with pytest.raises(ProtocolError, match="limit"):
            asset(current).export_to_file(tmp_path / "result", media_type=MIME, max_bytes=2)
    assert stream.closed and not list(tmp_path.iterdir())
    assert stream.iterated is (declared is None)


@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (404, MissingAssetError), (422, InvalidRequestError),
    (500, ServerError), (302, ServerError)])
def test_http_errors_are_distinct_and_binary_error_bodies_are_not_buffered(tmp_path, status, error):
    stream = Stream([b"issued-secret" * 100000])
    with client(stream, status=status, headers={"location": "https://evil.invalid"}) as current:
        with pytest.raises(error) as failure:
            asset(current).export_to_file(tmp_path / "result", media_type=MIME)
        assert "issued-secret" not in str(failure.value)
    assert stream.closed and not stream.iterated and not list(tmp_path.iterdir())


@pytest.mark.parametrize("policy", [{"overwrite": "yes"}, {"max_bytes": True}, {"max_bytes": 0}, {"max_bytes": -1}])
def test_invalid_policy_is_local(tmp_path, policy):
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(lambda r: pytest.fail("No HTTP"))) as current:
        with pytest.raises(InvalidRequestError):
            current.export_to_file(asset(current), tmp_path / "x", media_type=MIME, **policy)


@pytest.mark.parametrize("mime", [None, "", "*/*", "image/png,image/tiff", "image/png\r\nsecret", "image/png; charset=é", "image/png;a=1;a=2"])
def test_invalid_media_type_is_local(tmp_path, mime):
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(lambda r: pytest.fail("No HTTP"))) as current:
        with pytest.raises(InvalidRequestError):
            current.export_to_file(asset(current), tmp_path / "x", media_type=mime)


def test_destination_policy_precedes_http_and_overwrite_is_explicit(tmp_path):
    destination = tmp_path / "existing"
    destination.write_bytes(b"original")
    with client(Stream([b"new"])) as current:
        with pytest.raises(ExportIOError):
            asset(current).export_to_file(destination, media_type=MIME)
        with pytest.raises(ExportIOError):
            asset(current).export_to_file(tmp_path / "missing/child", media_type=MIME)
        result = asset(current).export_to_file(destination, media_type=MIME, overwrite=True, max_bytes=3)
    assert destination.read_bytes() == b"new" and result.bytes_written == 3


@pytest.mark.parametrize("operation", ["write", "fsync", "link", "replace"])
def test_file_errors_preserve_original_and_cleanup(tmp_path, monkeypatch, operation):
    import klab_client.exports as implementation
    destination = tmp_path / "existing"
    destination.write_bytes(b"original")
    monkeypatch.setattr(implementation.os, operation, lambda *a: (_ for _ in ()).throw(OSError("issued-secret")))
    with client(Stream([b"complete"])) as current:
        with pytest.raises(ExportIOError) as failure:
            asset(current).export_to_file(destination if operation == "replace" else tmp_path / "new", media_type=MIME,
                overwrite=operation == "replace")
        assert "issued-secret" not in str(failure.value)
    assert destination.read_bytes() == b"original" and not list(tmp_path.glob(".klab-export-*"))


def test_partial_os_writes_are_completed(tmp_path, monkeypatch):
    import klab_client.exports as implementation
    original = implementation.os.write
    monkeypatch.setattr(implementation.os, "write", lambda fd, view: original(fd, view[:2]))
    with client(Stream([b"abcdef"])) as current:
        result = asset(current).export_to_file(tmp_path / "file", media_type=MIME)
    assert result.path.read_bytes() == b"abcdef"


@pytest.mark.parametrize("bad", [None, [], {"library": None}, {"library": [None]},
    {"library": [schema(schemaId="")]}, {"library": [schema(mediaTypes="bad")]},
    {"library": [schema(extensions=[1])]}, {"library": [schema(description=1)]},
    {"library": [schema(mediaTypes=["bad"])]}])
def test_malformed_capability_schemas_are_protocol_errors(bad):
    with client(Stream([]), advertised={"serviceId": "home", "exportSchemata": bad}) as current:
        with pytest.raises(ProtocolError):
            current.export_formats(asset(current))


def test_schema_overloads_deduplicate_but_different_ids_are_ambiguous(tmp_path):
    advertised = capabilities([schema(), schema(), schema(knowledgeClass="COMPONENT"), schema(schemaId="other")])
    with client(Stream([]), advertised=advertised) as current:
        formats = current.export_formats(asset(current))
        assert len(formats) == 2 and formats[0].extensions == ("geojson", "json")
        with pytest.raises(FrozenInstanceError):
            formats[0].schema_id = "changed"
        with pytest.raises(UnsupportedOperationError):
            asset(current).export_to_file(tmp_path / "x", media_type=MIME)
    with client(Stream([]), advertised={"serviceId": "home"}) as current:
        assert current.export_formats(asset(current)) == ()
        with pytest.raises(UnsupportedOperationError):
            asset(current).export_to_file(tmp_path / "x", media_type=MIME)


def test_known_foreign_binding_and_invalid_assets_fail_before_http(tmp_path):
    with client(Stream([])) as current:
        observation = asset(current)
        with Client("https://runtime.invalid/runtime", runtime_service_id="other",
                    http_transport=httpx.MockTransport(lambda r: pytest.fail("No foreign HTTP"))) as foreign:
            with pytest.raises(InvalidRequestError):
                foreign.export_to_file(observation, tmp_path / "x", media_type=MIME)
        for invalid in (None, "id"):
            with pytest.raises(InvalidRequestError):
                current.export_to_file(invalid, tmp_path / "x", media_type=MIME)
        observation.id = 0
        with pytest.raises(InvalidRequestError):
            current.export_formats(observation)
        observation.id = 42
        observation.metadata["im:storage-binding-source"] = 42
        with pytest.raises(UnsupportedOperationError):
            current.export_formats(observation)
        observation._context = None
        with pytest.raises(UnsupportedOperationError):
            observation.export_to_file(tmp_path / "x", media_type=MIME)


def test_capability_identity_contradiction_is_rejected_before_export():
    with client(Stream([]), advertised={"serviceId": "foreign", "exportSchemata": {}}) as current:
        with pytest.raises(InvalidRequestError, match="identities"):
            current.export_formats(asset(current))


def test_unread_stream_protocol_error_closes_and_cleans(tmp_path):
    def handler(request):
        if request.url.path.endswith("capabilities"):
            return httpx.Response(200, json=capabilities())
        return httpx.Response(200, headers={"content-type": MIME}, content=b"already consumed")
    with Client("https://runtime.invalid/runtime", runtime_service_id="home", http_transport=httpx.MockTransport(handler)) as current:
        with pytest.raises(ProtocolError, match="unread"):
            asset(current).export_to_file(tmp_path / "x", media_type=MIME)
    assert not list(tmp_path.iterdir())


def test_invalid_destination_type_is_local():
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(lambda r: pytest.fail("No HTTP"))) as current:
        with pytest.raises(ExportIOError):
            current.export_to_file(asset(current), None, media_type=MIME)


@pytest.mark.parametrize("as_path", [False, True])
@pytest.mark.parametrize("invalid_parent", [False, True])
def test_nul_destination_is_a_local_export_error_before_http(tmp_path, as_path, invalid_parent):
    destination = tmp_path / "bad\x00name"
    if invalid_parent:
        destination /= "result"
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(lambda r: pytest.fail("No HTTP"))) as current:
        with pytest.raises(ExportIOError, match="Invalid export destination"):
            asset(current).export_to_file(destination if as_path else str(destination), media_type=MIME)
    assert not list(tmp_path.iterdir())


def test_zero_byte_os_write_is_a_file_error_not_an_infinite_loop(tmp_path, monkeypatch):
    import klab_client.exports as implementation
    monkeypatch.setattr(implementation.os, "write", lambda *a: 0)
    with client(Stream([b"complete"])) as current:
        with pytest.raises(ExportIOError):
            asset(current).export_to_file(tmp_path / "result", media_type=MIME)
    assert not list(tmp_path.iterdir())


def test_cleanup_failure_after_publication_reports_complete_destination(tmp_path, monkeypatch):
    original = Path.unlink
    def denied(path, *a, **kw):
        if path.name.startswith(".klab-export-"):
            raise PermissionError("issued-secret")
        return original(path, *a, **kw)
    with monkeypatch.context() as patch:
        patch.setattr(Path, "unlink", denied)
        with client(Stream([b"complete"])) as current:
            with pytest.raises(ExportIOError, match="cleanup") as failure:
                asset(current).export_to_file(tmp_path / "result", media_type=MIME)
            assert "issued-secret" not in str(failure.value)
    assert (tmp_path / "result").read_bytes() == b"complete"
    for temporary in tmp_path.glob(".klab-export-*"):
        temporary.unlink()


def test_requested_media_parameters_must_match_response(tmp_path):
    requested = "image/tiff;application=geotiff"
    stream = Stream([b"data"])
    def handler(request):
        if request.url.path.endswith("capabilities"):
            return httpx.Response(200, json=capabilities([schema(mediaTypes=[requested])]))
        assert request.headers["accept"] == requested
        return httpx.Response(200, headers={"content-type": "image/tiff;application=other"}, stream=stream)
    with Client("https://runtime.invalid/runtime", runtime_service_id="home", http_transport=httpx.MockTransport(handler)) as current:
        with pytest.raises(ProtocolError, match="representation"):
            asset(current).export_to_file(tmp_path / "x", media_type=requested)
    assert stream.closed and not list(tmp_path.iterdir())


@pytest.mark.parametrize("route", ["https://evil.invalid", "//evil.invalid", "/../export"])
def test_low_level_stream_rejects_nonlocal_routes_before_http(route):
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(lambda r: pytest.fail("No HTTP"))) as current:
        with pytest.raises(InvalidRequestError):
            with current.transport.stream("runtime", route, media_type=MIME):
                pytest.fail("Invalid route was accepted")


def test_low_level_stream_optional_headers_and_response_closure():
    stream = Stream([b"data"])
    def handler(request):
        assert "authorization" not in request.headers and "klab-scope" not in request.headers
        assert "klab-service" not in request.headers
        return httpx.Response(200, headers={"content-type": MIME}, stream=stream)
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(handler)) as current:
        with current.transport.stream("runtime", "/export", media_type=MIME) as response:
            assert b"".join(response.iter_raw()) == b"data"
    assert stream.closed


def test_concurrent_no_clobber_exports_publish_one_complete_result(tmp_path):
    destination = tmp_path / "shared"
    barrier = threading.Barrier(2)
    payloads = (b'{"complete":"first"}', b'{"complete":"second"}')
    def download(payload):
        stream = Stream([payload], callback=lambda: barrier.wait(timeout=10))
        with client(stream) as current:
            try:
                asset(current).export_to_file(destination, media_type=MIME)
                return "published"
            except ExportIOError:
                return "refused"
    with ThreadPoolExecutor(max_workers=2) as executor:
        outcomes = list(executor.map(download, payloads))
    assert sorted(outcomes) == ["published", "refused"]
    assert destination.read_bytes() in payloads and not list(tmp_path.glob(".klab-export-*"))


def test_capabilities_supply_verified_home_header_when_id_was_not_preconfigured(tmp_path):
    stream = Stream([b"complete"])
    with client(stream) as current:
        current.runtime_service_id = None
        result = asset(current).export_to_file(tmp_path / "result", media_type=MIME)
        assert result.bytes_written == 8  # Handler also asserts the verified home Runtime header.
        assert current.runtime_service_id is None  # Discovery does not silently rewrite caller configuration.
