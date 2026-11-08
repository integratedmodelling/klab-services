"""Native advertised schema + multipart Resources Job contract; no scientific inference."""
import asyncio
import hashlib
import inspect
import io
from pathlib import Path

import httpx
import pytest

from klab_client import AsyncClient, AsyncResourceImporter, Client, Endpoint, ResourceImporter
from klab_client.errors import (AuthorizationError, InvalidRequestError,
                                ProtocolError, SubmissionOutcomeUnknown, UnsupportedOperationError)
from klab_client.imports import _Upload

SECRET = "private-resource-import-credential"
MEDIA = "image/tiff;application=geotiff"
SCHEMA = {"knowledgeClass": "RESOURCE", "type": "STREAM", "schemaId": "raster.geotiff.import",
          "mediaTypes": [MEDIA], "description": "Raster import"}
ARGS = {"urn": "owned:test:resource", "schema_id": "raster.geotiff.import", "media_type": MEDIA,
        "allow_write": True}


def test_import_completion_without_binding_refuses_before_job_or_network():
    with Client('https://runtime.invalid', http_transport=httpx.MockTransport(
            lambda request: pytest.fail('Missing binding must not send HTTP'))) as client:
        importer = ResourceImporter(client, 'owned-user-scope')
        with pytest.raises(ProtocolError, match='binding was not established'):
            importer._finish(17, None, SCHEMA['schemaId'], ARGS['urn'],
                             lambda *args, **kwargs: pytest.fail('Must not create an unbound Job'))


class Wire:
    def __init__(self, schemas=None, acknowledgement=17):
        self.schemas = {"raster.geotiff": [dict(SCHEMA)]} if schemas is None else schemas
        self.acknowledgement = acknowledgement
        self.calls = []
        self.importer = None
        self.on_upload = None

    def __call__(self, request):
        self.calls.append((request.method, request.url.path))
        assert request.headers["Authorization"] == SECRET
        assert request.headers["klab-scope"] == "owned-user-scope"
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json={"serviceId": "resources-id", "importSchemata": self.schemas})
        if request.method == "POST":
            assert request.url.path == "/resources/import/raster.geotiff.import/owned:test:resource"
            assert request.headers["content-type"].startswith("multipart/form-data; boundary=")
            body = request.content
            assert b'name="file"; filename="upload.tif"' in body or b'filename="upload.bin"' in body
            assert b"private-source-name" not in body
            if self.on_upload:
                self.on_upload(request)
            return httpx.Response(200, json=self.acknowledgement)
        if request.url.path.endswith("/jobs/status/17"):
            return httpx.Response(200, json={"status": "FINISHED"})
        assert request.url.path.endswith("/jobs/retrieve/17")
        return httpx.Response(200, json={"empty": False, "results": [{"resourceUrn": "served:resource"}]})


def perform(asynchronous, wire, operation, **options):
    kwargs = {"resources": Endpoint("https://resources.invalid/resources", SECRET),
              "http_transport": httpx.MockTransport(wire)}
    if asynchronous:
        async def scenario():
            async with AsyncClient("https://runtime.invalid/runtime", **kwargs) as client:
                importer = AsyncResourceImporter(client, "owned-user-scope", **options)
                wire.importer = importer
                value = operation(importer)
                return await value if inspect.isawaitable(value) else value
        return asyncio.run(scenario())
    with Client("https://runtime.invalid/runtime", **kwargs) as client:
        importer = ResourceImporter(client, "owned-user-scope", **options)
        wire.importer = importer
        return operation(importer)


@pytest.fixture
def source(tmp_path):
    path = tmp_path / "private-source-name.tif"
    path.write_bytes(bytes(range(256)) * 600)
    return path


@pytest.mark.parametrize("asynchronous", [False, True])
def test_real_multipart_encoding_job_receipt_and_result(asynchronous, source):
    wire = Wire()
    receipt = perform(asynchronous, wire, lambda importer: importer.upload(source, **ARGS))
    assert receipt.source_sha256 == hashlib.sha256(source.read_bytes()).hexdigest()
    assert receipt.source_bytes == source.stat().st_size
    assert receipt.service_id == "resources-id"
    assert receipt.job.service == "resources" and receipt.job.id == 17
    assert SECRET not in repr(receipt)
    assert wire.calls == [("GET", "/resources/public/capabilities"),
                          ("POST", "/resources/import/raster.geotiff.import/owned:test:resource")]


@pytest.mark.parametrize("asynchronous", [False, True])
def test_poll_actual_resources_job_without_runtime_or_resubmission(asynchronous, source):
    wire = Wire()
    async def async_operation(importer):
        receipt = await importer.upload(source, **ARGS)
        return await receipt.job.result()
    def sync_operation(importer):
        return importer.upload(source, **ARGS).job.result()
    result = perform(asynchronous, wire, async_operation if asynchronous else sync_operation)
    assert result["results"][0]["resourceUrn"] == "served:resource"
    assert len(wire.calls) == 4
    assert all(path.startswith("/resources/") for _, path in wire.calls)


@pytest.mark.parametrize("asynchronous", [False, True])
@pytest.mark.parametrize("changes", [{"allow_write": False}, {"allow_write": 1}, {"urn": ""},
                                      {"schema_id": SECRET}, {"media_type": "not-a-media-type"}])
def test_bad_requests_never_contact_a_service(asynchronous, source, changes):
    wire = Wire()
    with pytest.raises(InvalidRequestError):
        perform(asynchronous, wire, lambda importer: importer.upload(source, **(ARGS | changes)))
    assert not wire.calls


@pytest.mark.parametrize("asynchronous", [False, True])
@pytest.mark.parametrize("schemas", [{}, {"component.kar": [SCHEMA | {"knowledgeClass": "COMPONENT"}]},
    {"raster": [SCHEMA | {"type": "PROPERTIES"}]}, {"raster": [SCHEMA, SCHEMA]}])
def test_unadvertised_unsupported_or_ambiguous_never_upload(asynchronous, source, schemas):
    wire = Wire(schemas)
    with pytest.raises(UnsupportedOperationError):
        perform(asynchronous, wire, lambda importer: importer.upload(source, **ARGS))
    assert len(wire.calls) == 1


@pytest.mark.parametrize("schemas", [[], {"x": {}}, {"x": [None]},
    {"x": [SCHEMA | {"schemaId": "bad/path"}]}, {"x": [SCHEMA | {"mediaTypes": None}]},
    {"x": [SCHEMA | {"mediaTypes": []}]}, {"x": [SCHEMA | {"description": 1}]},
    {"x": [SCHEMA | {"mediaTypes": ["invalid"]}]},
    {"x": [SCHEMA | {"schemaId": SECRET}]}, {"x": [SCHEMA] * 257}])
def test_malformed_schema_is_protocol_error(schemas):
    with pytest.raises(ProtocolError):
        perform(False, Wire(schemas), lambda importer: importer.formats())


def test_descriptions_redacted_and_missing_description_preserved():
    formats = perform(False, Wire({"x": [SCHEMA | {"description": SECRET}, SCHEMA | {"description": None}]}),
                      lambda importer: importer.formats())
    assert formats[0].description == "[REDACTED]"
    assert formats[1].description is None


@pytest.mark.parametrize("asynchronous", [False, True])
@pytest.mark.parametrize("ack", [None, False, -1, 0, "17", {}, 2**63])
def test_malformed_acknowledgement_means_unknown_not_replay(asynchronous, source, ack):
    wire = Wire(acknowledgement=ack)
    with pytest.raises(SubmissionOutcomeUnknown):
        perform(asynchronous, wire, lambda importer: importer.upload(source, **ARGS))
    assert len(wire.calls) == 2


@pytest.mark.parametrize("asynchronous", [False, True])
def test_authorization_denial_is_not_unknown_or_retried(asynchronous, source):
    wire = Wire()
    original = wire.__call__
    def handler(request):
        if request.method == "POST":
            wire.calls.append(("POST", request.url.path))
            return httpx.Response(403, text="forbidden " + SECRET)
        return original(request)
    with pytest.raises(AuthorizationError) as error:
        perform(asynchronous, handler, lambda importer: importer.upload(source, **ARGS))
    assert SECRET not in str(error.value)
    assert len(wire.calls) == 2


@pytest.mark.parametrize("kind", ["absent", "empty", "too-large", "directory"])
def test_local_file_preflight_never_posts(source, kind):
    path = source
    if kind == "absent":
        path = source.with_name("absent")
    elif kind == "empty":
        path.write_bytes(b"")
    elif kind == "directory":
        path = source.parent
    wire = Wire()
    with pytest.raises(InvalidRequestError):
        perform(False, wire, lambda importer: importer.upload(path, **ARGS), max_source_bytes=1)
    assert all(method == "GET" for method, _ in wire.calls)


@pytest.mark.parametrize("asynchronous", [False, True])
def test_changed_binding_after_upload_means_unknown(asynchronous, source):
    wire = Wire()
    wire.on_upload = lambda request: wire.importer.client._service_id_cache.update(resources="changed")
    with pytest.raises(SubmissionOutcomeUnknown):
        perform(asynchronous, wire, lambda importer: importer.upload(source, **ARGS))


def test_formats_identity_pin_refuses_changed_service():
    def operation(importer):
        importer.formats()
        importer._binding = ("https://old.invalid", "old")
        return importer.formats()
    with pytest.raises(ProtocolError, match="identity changed"):
        perform(False, Wire(), operation)


def test_upload_reader_bounds_and_refuses_replay():
    upload = _Upload(io.BytesIO(b"abc"), 3)
    assert upload.seek(0) == 0
    assert upload.read() == b"abc"
    with pytest.raises(OSError, match="replayed"):
        upload.seek(0)
    with pytest.raises(OSError, match="grew"):
        _Upload(io.BytesIO(b"abc"), 1).read(3)


def test_client_and_budget_validation():
    with pytest.raises(InvalidRequestError):
        AsyncResourceImporter(object())
    with pytest.raises(InvalidRequestError):
        ResourceImporter(object())
    with pytest.raises(InvalidRequestError):
        perform(False, Wire(), lambda importer: None, max_source_bytes=True)


@pytest.mark.parametrize("asynchronous", [False, True])
@pytest.mark.parametrize("failure", ["invalid-json", "http500", "disconnect"])
def test_ambiguous_upload_never_replays(asynchronous, source, failure):
    wire = Wire()
    def handler(request):
        if request.method == "POST":
            wire.calls.append(("POST", request.url.path))
            if failure == "disconnect":
                raise httpx.ReadError("lost reply")
            return httpx.Response(500 if failure == "http500" else 200, text="malformed " + SECRET)
        return wire(request)
    with pytest.raises(SubmissionOutcomeUnknown) as error:
        perform(asynchronous, handler, lambda importer: importer.upload(source, **ARGS))
    assert SECRET not in str(error.value)
    assert len(wire.calls) == 2


def test_async_local_cancellation_preserves_unknown_note_and_closes_file(source, monkeypatch):
    wire = Wire()
    def handler(request):
        if request.method == "POST":
            raise asyncio.CancelledError()
        return wire(request)
    opened = []
    original = Path.open
    def capture(path, *args, **kwargs):
        stream = original(path, *args, **kwargs)
        opened.append(stream)
        return stream
    monkeypatch.setattr(Path, "open", capture)
    with pytest.raises(asyncio.CancelledError) as caught:
        perform(True, handler, lambda importer: importer.upload(source, **ARGS))
    assert any("outcome unknown" in note for note in caught.value.__notes__)
    assert all(stream.closed for stream in opened)


@pytest.mark.parametrize("primary", [False, True])
def test_source_cleanup_preserves_primary_or_refuses_replay(source, monkeypatch, primary):
    real_open = Path.open
    class BadClose:
        def __init__(self):
            self.stream = real_open(source, "rb")
        def fileno(self):
            return self.stream.fileno()
        def close(self):
            self.stream.close()
            raise OSError("private cleanup detail")
    monkeypatch.setattr(Path, "open", lambda *a, **k: BadClose())
    original = RuntimeError("primary")
    def operation(importer):
        with importer._file(source):
            if primary:
                raise original
    with pytest.raises(RuntimeError if primary else SubmissionOutcomeUnknown) as caught:
        perform(False, Wire(), operation)
    if primary:
        assert caught.value is original
        assert caught.value.__notes__ == ["Import source cleanup failed"]


def test_extensionless_source_uses_safe_filename(source):
    new_path = source.with_suffix("")
    source.rename(new_path)
    assert perform(False, Wire(), lambda importer: importer.upload(new_path, **ARGS)).source_bytes > 0


@pytest.mark.parametrize("asynchronous", [False, True])
def test_truncated_source_cannot_be_confirmed(asynchronous, source, monkeypatch):
    original = _Upload.read
    def truncated(upload, size=-1):
        if upload.count:
            return b""
        return original(upload, size)
    monkeypatch.setattr(_Upload, "read", truncated)
    with pytest.raises(SubmissionOutcomeUnknown):
        perform(asynchronous, Wire(), lambda importer: importer.upload(source, **ARGS))
