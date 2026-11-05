"""Native retrieval wire preservation; controlled mock codes are not scientific mappings."""
import asyncio
from copy import deepcopy

import httpx
import pytest

from klab_client import AsyncClient, Client, Endpoint
from klab_client.api.primitives import KlabAsset
from klab_client.errors import (AuthenticationError, AuthorizationError, MissingAssetError,
    ProtocolError, ServerError, UnsupportedOperationError)

ROOT = "fixture:Kind"
CODELIST = {"urn": ROOT, "name": ROOT, "rootConceptId": ROOT, "type": "CONCEPT", "worldview": "fixture",
    "serviceId": "resources-id", "authorityId": "test-scheme", "authorityIds": ["test-scheme"],
    "entries": [{"authorityId": "test-scheme", "code": 0, "value": {"urn": "fixture:Zero"}, "preferred": True},
        {"authorityId": "test-scheme", "code": "0", "value": {"urn": "fixture:Zero"}, "preferred": False},
        {"authorityId": "test-scheme", "code": 2**63 - 1, "value": {"urn": "fixture:Upper"}, "preferred": True}],
    "metadata": {"provenance": "controlled native DTO shape; no original-study codebook"}}


def client(native_async, handler):
    return (AsyncClient if native_async else Client)("https://runtime.invalid", "runtime-token", runtime_service_id="runtime-id",
        resources=Endpoint("https://resources.invalid/base", "resource-token"), http_transport=httpx.MockTransport(handler))


@pytest.mark.parametrize("native_async", [False, True])
def test_explicit_native_codelist_retrieval_preserves_full_payload_and_typed_codes(native_async):
    seen = []
    def handler(request):
        seen.append(request)
        assert request.method == "GET" and request.url.raw_path.endswith(b"/CODELIST/fixture%3AKind")
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "runtime-id"
        assert request.headers["authorization"] == "resource-token"
        return httpx.Response(200, json=deepcopy(CODELIST))
    async def run():
        async with client(True, handler) as api:
            return await api.resources.retrieve(ROOT, "CODELIST", "s.c")
    if native_async:
        result = asyncio.run(run())
    else:
        with client(False, handler) as api:
            result = api.resources.retrieve(ROOT, "CODELIST", "s.c")
    assert isinstance(result, KlabAsset) and result.urn == ROOT and result.raw == CODELIST
    assert [e["code"] for e in result.raw["entries"]] == [0, "0", 2**63 - 1] and len(seen) == 1


@pytest.mark.parametrize("native_async", [False, True])
def test_codelist_list_and_resolve_remain_explicitly_unsupported_without_http(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        raise AssertionError("No codelist list/resolve contract was added")
    async def run():
        async with client(True, handler) as api:
            with pytest.raises(UnsupportedOperationError):
                await api.resources.list("CODELIST")
            with pytest.raises(UnsupportedOperationError):
                await api.resources.resolve(ROOT, knowledge_class="CODELIST")
    if native_async:
        asyncio.run(run())
    else:
        with client(False, handler) as api:
            with pytest.raises(UnsupportedOperationError):
                api.resources.list("CODELIST")
            with pytest.raises(UnsupportedOperationError):
                api.resources.resolve(ROOT, knowledge_class="CODELIST")
    assert not calls


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("status,body,error", [(200, b"", MissingAssetError), (200, b"null", MissingAssetError),
    (200, b"{}", ProtocolError), (200, b"badJSON", ProtocolError), (401, b"", AuthenticationError),
    (403, b"", AuthorizationError), (404, b"", MissingAssetError), (503, b"", ServerError)])
def test_native_codelist_absence_malformed_and_remote_errors_stay_typed(native_async, status, body, error):
    def handler(request):
        return httpx.Response(status, content=body)
    async def run():
        async with client(True, handler) as api:
            with pytest.raises(error):
                await api.resources.retrieve(ROOT, "CODELIST")
    if native_async:
        asyncio.run(run())
    else:
        with client(False, handler) as api:
            with pytest.raises(error):
                api.resources.retrieve(ROOT, "CODELIST")
