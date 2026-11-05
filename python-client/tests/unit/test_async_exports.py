"""Native stream, cancellation and atomic publication contracts; no Java substitute."""
import asyncio
import json
import os
from pathlib import Path

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, ExportIOError
from klab_client.dto import observation_from_wire
from klab_client.errors import (AuthorizationError, InvalidRequestError, ProtocolError,
    TransportError, UnsupportedOperationError)

MIME = "application/geo+json"


class Stream(httpx.AsyncByteStream):
    def __init__(self, chunks=(b"native-bytes",), error=None):
        self.chunks, self.error, self.closed = chunks, error, False
    async def __aiter__(self):
        for chunk in self.chunks:
            yield chunk
        if self.error:
            raise self.error
    async def aclose(self):
        self.closed = True


def client(stream, *, headers=None, status=200, entries=None, calls=None, media_type=MIME):
    async def handler(request):
        if calls is not None:
            calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "home", "exportSchemata": {"native":
                [{"schemaId": "geojson", "knowledgeClass": "OBSERVATION", "mediaTypes": [MIME],
                  "extensions": ["json"]}] if entries is None else entries}})
        assert request.url.path == "/runtime/export/OBSERVATION/s.c.42"
        assert request.headers["accept"] == media_type and request.headers["accept-encoding"] == "identity"
        assert request.headers["klab-scope"] == "s.c.8#3" and request.headers["klab-service"] == "home"
        return httpx.Response(status, headers={"content-type": MIME, **(headers or {})}, stream=stream)
    return AsyncClient("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
        http_transport=httpx.MockTransport(handler))


def asset(api):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    return observation_from_wire(payload, AsyncContext(api, {"id": "s.c", "serviceId": "home"}, token="s.c.8#3"))


@pytest.mark.parametrize("overwrite", [False, True])
def test_exact_native_bytes_and_advertised_formats_published_after_close(tmp_path, overwrite):
    async def run():
        destination = tmp_path / "result.json"
        if overwrite:
            destination.write_bytes(b"old")
        stream = Stream([b"x" * 65536, b"tail"])
        async with client(stream, headers={"content-length": "65540"}) as api:
            formats = await api.export_formats(asset(api))
            assert formats[0].schema_id == "geojson"
            result = await api.export_to_file(asset(api), destination, media_type=MIME, overwrite=overwrite)
        assert destination.read_bytes() == b"x" * 65536 + b"tail"
        assert result.bytes_written == 65540 and stream.closed
        assert not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


@pytest.mark.parametrize("defect", ["type", "encoding", "partial", "length", "empty", "header-budget", "stream-budget", "transport", "denied"])
def test_stream_failure_preserves_destination_and_closes_response(tmp_path, defect):
    async def run():
        destination = tmp_path / "existing.json"
        destination.write_bytes(b"original")
        headers = {"type": {"content-type": "text/plain"}, "encoding": {"content-encoding": "gzip"},
            "length": {"content-length": "999"}, "header-budget": {"content-length": "999"}}.get(defect, {})
        stream = Stream([] if defect == "empty" else [b"native-bytes"],
            httpx.ReadError("private diagnostics") if defect == "transport" else None)
        expected = AuthorizationError if defect == "denied" else TransportError if defect == "transport" else ProtocolError
        async with client(stream, headers=headers, status=403 if defect == "denied" else 206 if defect == "partial" else 200) as api:
            with pytest.raises(expected):
                await api.export_to_file(asset(api), destination, media_type=MIME, overwrite=True,
                    max_bytes=5 if defect in ("header-budget", "stream-budget") else None)
        assert destination.read_bytes() == b"original" and stream.closed
        assert not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


@pytest.mark.parametrize("defect", ["media", "overwrite", "limit", "parent", "exists", "path", "nul", "unadvertised", "ambiguous"])
def test_invalid_or_unadvertised_requests_never_download(tmp_path, defect):
    async def run():
        calls = []
        destination = tmp_path / "new.json"
        if defect == "exists":
            destination.write_bytes(b"original")
        if defect == "parent":
            destination = tmp_path / "missing" / "new.json"
        if defect == "path":
            destination = object()
        if defect == "nul":
            destination = "bad\x00path"
        schema = {"schemaId": "other", "knowledgeClass": "OBSERVATION", "mediaTypes": [MIME]}
        entries = [] if defect == "unadvertised" else [schema, schema | {"schemaId": "second"}] if defect == "ambiguous" else None
        async with client(Stream(), entries=entries, calls=calls) as api:
            with pytest.raises((InvalidRequestError, ExportIOError, UnsupportedOperationError)):
                await api.export_to_file(asset(api), destination, media_type="invalid" if defect == "media" else MIME,
                    overwrite=1 if defect == "overwrite" else False, max_bytes=0 if defect == "limit" else None)
        assert not any("/export/" in r.url.path for r in calls)
    asyncio.run(run())


def test_native_local_task_cancellation_discards_partial_not_existing_destination(tmp_path):
    async def run():
        entered = asyncio.Event()
        class Waiting(Stream):
            async def __aiter__(self):
                yield b"x" * 65536
                entered.set()
                await asyncio.Event().wait()
        stream = Waiting()
        destination = tmp_path / "result.json"
        destination.write_bytes(b"original")
        async with client(stream) as api:
            task = asyncio.create_task(api.export_to_file(asset(api), destination, media_type=MIME, overwrite=True))
            await asyncio.wait_for(entered.wait(), 2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert destination.read_bytes() == b"original" and stream.closed
        assert not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


@pytest.mark.parametrize("defect", ["write", "zero", "fsync", "close", "replace", "link", "mkstemp"])
def test_local_io_failure_never_publishes_partial(tmp_path, monkeypatch, defect):
    import klab_client._async_exports as module
    async def run():
        destination = tmp_path / "result.json"
        stream = Stream()
        close = os.close
        def fail(*args, **kwargs):
            if defect == "close":
                close(args[0])
            raise OSError("private local diagnostics")
        target = "write" if defect in ("write", "zero") else defect
        with monkeypatch.context() as patch:
            if target == "mkstemp":
                patch.setattr(module.tempfile, target, fail)
            else:
                patch.setattr(module.os, target, (lambda *args: 0) if defect == "zero" else fail)
            async with client(stream) as api:
                with pytest.raises(ExportIOError):
                    await api.export_to_file(asset(api), destination, media_type=MIME, overwrite=defect == "replace")
        assert not destination.exists() and not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


def test_short_writes_are_completed(tmp_path, monkeypatch):
    import klab_client._async_exports as module
    write = os.write
    monkeypatch.setattr(module.os, "write", lambda fd, data: write(fd, data[:3]))
    async def run():
        async with client(Stream()) as api:
            result = await api.export_to_file(asset(api), tmp_path / "result.json", media_type=MIME)
        assert result.path.read_bytes() == b"native-bytes"
    asyncio.run(run())


@pytest.mark.parametrize("matches", [False, True])
def test_required_media_parameters_are_preserved(tmp_path, matches):
    media = MIME + "; charset=utf-8"
    schema = {"schemaId": "geojson", "knowledgeClass": "OBSERVATION", "mediaTypes": [media]}
    async def run():
        async with client(Stream(), media_type=media, entries=[schema],
                headers={"content-type": media if matches else MIME}) as api:
            if matches:
                assert (await api.export_to_file(asset(api), tmp_path / "result", media_type=media)).bytes_written
            else:
                with pytest.raises(ProtocolError):
                    await api.export_to_file(asset(api), tmp_path / "result", media_type=media)
    asyncio.run(run())


@pytest.mark.parametrize("primary", [False, True])
def test_temporary_cleanup_failure_preserves_original_or_reports_local_error(tmp_path, monkeypatch, primary):
    import klab_client._async_exports as module
    async def run():
        destination = tmp_path / "result.json"
        def fail(*args, **kwargs):
            raise OSError("private cleanup diagnostics")
        with monkeypatch.context() as patch:
            patch.setattr(module.Path, "unlink", fail)
            async with client(Stream(), status=403 if primary else 200) as api:
                with pytest.raises(AuthorizationError if primary else ExportIOError) as caught:
                    await api.export_to_file(asset(api), destination, media_type=MIME)
            if primary:
                assert caught.value.__notes__ == ["Export temporary cleanup failed: OSError"]
            assert "private cleanup" not in str(caught.value)
        for path in tmp_path.glob(".klab-export-*"):
            path.unlink()
    asyncio.run(run())


def test_secondary_close_failure_keeps_primary_stream_error(tmp_path, monkeypatch):
    import klab_client._async_exports as module
    close = os.close
    def failed_close(fd):
        close(fd)
        raise OSError("private close details")
    async def run():
        with monkeypatch.context() as patch:
            patch.setattr(module.os, "close", failed_close)
            async with client(Stream(error=httpx.ReadError("private transport"))) as api:
                with pytest.raises(TransportError) as caught:
                    await api.export_to_file(asset(api), tmp_path / "result", media_type=MIME)
            assert "Export descriptor cleanup failed: OSError" in caught.value.__notes__
        assert not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())


@pytest.mark.parametrize("written,length,valid", [(0, None, False), (0, 0, False),
    (12, None, True), (12, 12, True), (12, 11, False), (12, 13, False)])
def test_shared_completed_native_size_contract_is_directly_exercised(written, length, valid):
    from klab_client.exports import _validate_size
    if valid:
        assert _validate_size(written, length) is None
    else:
        with pytest.raises(ProtocolError):
            _validate_size(written, length)


@pytest.mark.parametrize("length", [None, "12"])
def test_small_complete_native_stream_reaches_publication_with_and_without_length(tmp_path, length):
    async def run():
        stream = Stream([b"native-bytes"])
        async with client(stream, headers={} if length is None else {"content-length": length}) as api:
            result = await api.export_to_file(asset(api), tmp_path / "result", media_type=MIME)
        assert result.path.read_bytes() == b"native-bytes" and result.bytes_written == 12
        assert stream.closed and not list(tmp_path.glob(".klab-export-*"))
    asyncio.run(run())
