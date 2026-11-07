"""SIMULATED_PROVISIONAL/JAVA-GAP-0009: private draft SDK contract, no publication."""
import asyncio
import hashlib

import httpx
import pytest

from klab_client import (Client, AsyncClient, DraftConflict, read_draft, write_draft,
    async_read_draft, async_write_draft)
from klab_client.errors import InvalidRequestError, ProtocolError, ServerError, SubmissionOutcomeUnknown
from klab_client.revision_drafts import PROTOCOL, _body, _decode, _prepare, _source


def row(source="draft", revision=1):
    return {"protocol": PROTOCOL, "name": "example", "revision": revision, "source": source,
        "source_sha256": hashlib.sha256(source.encode()).hexdigest(), "validation": "UNVALIDATED_PRIVATE_DRAFT"}


@pytest.mark.parametrize("source", [None, "", " ", "x" * 16385, "\ud800"])
def test_invalid_sources_are_not_sent(source):
    with pytest.raises(InvalidRequestError): _source(source)


@pytest.mark.parametrize("revision", [None, True, -1, 2**53, 1.5])
def test_cas_requires_exact_explicit_revision(revision):
    with pytest.raises(InvalidRequestError): _body("draft", revision)


@pytest.mark.parametrize("change", [{"extra": 1}, {"protocol": "native"}, {"name": "foreign"},
    {"revision": True}, {"revision": 0}, {"revision": 2**53}, {"validation": "VALIDATED"},
    {"source": ""}, {"source_sha256": "bad"}])
def test_decoder_rejects_unknown_validation_hash_and_identity(change):
    with pytest.raises(ProtocolError): _decode({**row(), **change}, "example")


@pytest.mark.parametrize("native_async", [False, True])
def test_real_sdk_wire_paths_never_retry_cas_or_ambiguous_writes(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["klab-scope"] == "s.c"
        assert request.headers["klab-service"] == "runtime"
        assert request.url.path == "/runtime/experimental/drafts/v1/example"
        if request.method == "GET":
            assert request.url.params["protocol"] == PROTOCOL
            return httpx.Response(200, json=row())
        value = __import__("json").loads(request.content)
        if value["expected_revision"] == 0: return httpx.Response(200, json=row(value["source"]))
        if value["expected_revision"] == 1: return httpx.Response(409)
        if value["expected_revision"] == 2: return httpx.Response(500)
        return httpx.Response(429)
    options = dict(agent_name="alice", runtime_service_id="runtime", http_transport=httpx.MockTransport(handler))
    async def run():
        async with AsyncClient("https://example.test/runtime", "synthetic", **options) as client:
            context = client._context({"id": "s.c", "serviceId": "runtime"})
            first = await async_write_draft(context, "example", "draft", expected_revision=0, allow_experimental=True)
            assert await async_read_draft(context, "example", allow_experimental=True) == first
            assert await async_read_draft(context, "example", revision=1, allow_experimental=True) == first
            for revision, error in [(1, DraftConflict), (2, SubmissionOutcomeUnknown), (3, ServerError)]:
                with pytest.raises(error): await async_write_draft(context, "example", "draft", expected_revision=revision, allow_experimental=True)
    if native_async:
        asyncio.run(run())
    else:
        with Client("https://example.test/runtime", "synthetic", **options) as client:
            context = client._context({"id": "s.c", "serviceId": "runtime"})
            first = write_draft(context, "example", "draft", expected_revision=0, allow_experimental=True)
            assert read_draft(context, "example", allow_experimental=True) == first
            assert read_draft(context, "example", revision=1, allow_experimental=True) == first
            for revision, error in [(1, DraftConflict), (2, SubmissionOutcomeUnknown), (3, ServerError)]:
                with pytest.raises(error): write_draft(context, "example", "draft", expected_revision=revision, allow_experimental=True)
    assert len(calls) == 6


@pytest.mark.parametrize("native_async", [False, True])
def test_wrong_transport_invalid_history_and_false_success_refuse(native_async):
    async def run():
        with pytest.raises(InvalidRequestError): await async_read_draft(object(), "example", allow_experimental=True)
        with pytest.raises(InvalidRequestError): await async_write_draft(object(), "example", "draft", expected_revision=0, allow_experimental=True)
        async with AsyncClient("https://example.test/runtime", "synthetic", agent_name="alice", runtime_service_id="runtime",
                http_transport=httpx.MockTransport(lambda _: httpx.Response(200, json=row(revision=2)))) as client:
            context = client._context({"id": "s.c", "serviceId": "runtime"})
            with pytest.raises(InvalidRequestError): await async_read_draft(context, "example", revision=0, allow_experimental=True)
            with pytest.raises(ProtocolError): await async_read_draft(context, "example", revision=1, allow_experimental=True)
            with pytest.raises(ProtocolError): await async_write_draft(context, "example", "draft", expected_revision=0, allow_experimental=True)
    if native_async:
        asyncio.run(run())
    else:
        with pytest.raises(InvalidRequestError): read_draft(object(), "example", allow_experimental=True)
        with pytest.raises(InvalidRequestError): write_draft(object(), "example", "draft", expected_revision=0, allow_experimental=True)
        with Client("https://example.test/runtime", "synthetic", agent_name="alice", runtime_service_id="runtime",
                http_transport=httpx.MockTransport(lambda _: httpx.Response(200, json=row(revision=2)))) as client:
            context = client._context({"id": "s.c", "serviceId": "runtime"})
            with pytest.raises(InvalidRequestError): read_draft(context, "example", revision=0, allow_experimental=True)
            with pytest.raises(ProtocolError): read_draft(context, "example", revision=1, allow_experimental=True)
            with pytest.raises(ProtocolError): write_draft(context, "example", "draft", expected_revision=0, allow_experimental=True)


def test_preflight_denies_paths_optin_and_unbound_context():
    with pytest.raises(InvalidRequestError): _prepare(object(), "example", False)
    with pytest.raises(InvalidRequestError): _prepare(object(), "example", True)
    with Client("https://example.test/runtime", "synthetic", agent_name="alice", runtime_service_id="runtime") as client:
        context = client._context({"id": "s.c", "serviceId": "runtime"})
        for name in (".", "..", "a/b", "", None):
            with pytest.raises(InvalidRequestError): _prepare(context, name, True)
