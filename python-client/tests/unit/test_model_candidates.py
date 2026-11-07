"""SIMULATED_PROVISIONAL SDK-only protocol1 fixtures; JAVA-GAP-0003 native NOT_TESTED."""
import asyncio
import hashlib
import json

import httpx
import pytest

from klab_client import AsyncClient, Client, Endpoint
from klab_client.errors import InvalidRequestError, MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError
from klab_client.model_candidates import (SOURCE, HASH, ModelCandidateQuery, _json, _catalog,
    _capabilities, _descriptor, inspect_model_candidates, async_inspect_model_candidates)

URL = "https://example.test/resources"
TOKEN = "private-candidate-test-token"


def namespace():
    return {"@CLASS": "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl",
        "urn": "synthetic.namespace", "projectName": "synthetic.project", "lastUpdateTimestamp": 7,
        "inactive": False, "statements": [{"@CLASS": "org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl",
            "urn": "synthetic.namespace.model"}]}


def descriptor(kind="MODEL", source=None):
    source = json.dumps(namespace()) if source is None else source
    return {"knowledgeClass": kind, "serviceId": "resources-1", "resourceUrn": "synthetic.namespace" if kind == "NAMESPACE" else "synthetic.namespace.model",
        "projectUrn": "synthetic.project", "timestamp": 7, "metadata": {SOURCE: source,
            HASH: hashlib.sha256(source.encode("utf-8", errors="surrogatepass")).hexdigest()}}


def response():
    return {"empty": False, "results": [descriptor()], "namespaces": [descriptor("NAMESPACE")], "notifications": []}


def capabilities(**changes):
    return {"type": "RESOURCES", "serviceId": "resources-1", "modelCandidateProtocol": 1, **changes}


def encoded(value):
    return json.dumps(value).encode()


@pytest.mark.parametrize("native_async", [False, True])
def test_candidate_catalog_uses_advertised_supplier_route_without_workflow_headers(native_async):
    calls = []
    query = ModelCandidateQuery(namespaces=("synthetic.namespace",), projects=("synthetic.project",))
    def handler(request):
        calls.append(request)
        assert request.headers["Authorization"] == TOKEN
        assert all(h not in request.headers for h in ("klab-scope", "klab-service", "klab-resolution-cycle", "klab-transaction"))
        assert request.url.host == "example.test"
        if request.method == "GET": return httpx.Response(200, json=capabilities())
        assert request.url.path == "/resources/api/v1/models/candidates" and json.loads(request.content) == query.to_wire()
        return httpx.Response(200, json=response())
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, resources=Endpoint(URL, TOKEN), http_transport=httpx.MockTransport(handler)) as client:
            return await async_inspect_model_candidates(client, query)
    if native_async: result = asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, resources=Endpoint(URL, TOKEN), http_transport=httpx.MockTransport(handler)) as client:
            result = inspect_model_candidates(client, query)
    assert len(calls) == 2 and result.provider_id == "resources-1" and result.provider_url == URL
    candidate = result.candidates[0]
    assert candidate.model_urn == "synthetic.namespace.model" and candidate.namespace_urn == "synthetic.namespace"
    assert candidate.source_json == json.dumps(namespace()) and "NOT_SEMANTIC_ADMISSION" in result.evidence


@pytest.mark.parametrize("version", [None, True, 0, 2, "1"])
def test_unadvertised_supplier_is_not_queried_or_legacy_fallback(version):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(200, json=capabilities(modelCandidateProtocol=version))
    with Client("https://runtime.test", TOKEN, resources=Endpoint(URL, TOKEN), http_transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(UnsupportedOperationError): inspect_model_candidates(client, ModelCandidateQuery())
    assert len(calls) == 1


@pytest.mark.parametrize("raw", [None, b'x' * (1024 * 1024 + 1), b'\xff', b'{', b'{"a":1,"a":2}', b'Infinity', b'1e999'])
def test_bounded_duplicate_free_finite_utf8(raw):
    with pytest.raises(ProtocolError): _json(raw)


def test_finite_optional_source_numbers_remain_exact_decoded_json():
    assert _json(b'{"number":1.25}') == {"number": 1.25}


def test_escaped_source_credential_leaf_refuses_without_rewriting_hashed_source():
    value = namespace(); value["metadata"] = {"note": TOKEN}
    source = json.dumps(value).replace("private-candidate", "\\u0070rivate-candidate")
    assert TOKEN not in source
    with pytest.raises(ProtocolError) as denied:
        _descriptor(descriptor(source=source), "MODEL", "resources-1", lambda v: v.replace(TOKEN, "REDACTED"))
    assert TOKEN not in str(denied.value)


@pytest.mark.parametrize("changes", [{"type": "RUNTIME"}, {"serviceId": ""}, {"serviceId": TOKEN}])
def test_supplier_identity_refuses(changes):
    with pytest.raises(ProtocolError): _capabilities(encoded(capabilities(**changes)), lambda v: v.replace(TOKEN, "REDACTED"))


@pytest.mark.parametrize("values", [["x"], ("x", "x"), (True,), ("",), ("has space",), ("x" * 257,), tuple(str(n) for n in range(33))])
def test_invalid_selectors_refuse(values):
    with pytest.raises(InvalidRequestError): ModelCandidateQuery(namespaces=values)


@pytest.mark.parametrize("changes", [{"knowledgeClass": "RESOURCE"}, {"serviceId": "foreign"}, {"resourceUrn": ""},
    {"projectUrn": "bad project"}, {"timestamp": True}, {"timestamp": -1}, {"metadata": None}])
def test_descriptor_identity_refuses(changes):
    with pytest.raises(ProtocolError): _descriptor({**descriptor(), **changes}, "MODEL", "resources-1", lambda v: v)


@pytest.mark.parametrize("case", ["missing_source", "missing_hash", "bad_hash", "credential", "surrogate", "source_not_object", "namespace_class", "namespace_urn", "project", "timestamp", "inactive", "statements", "absent_model", "namespace_member"])
def test_source_identity_hash_and_membership_refuse(case):
    row = descriptor(); source = namespace(); kind = "MODEL"
    if case == "missing_source": row["metadata"][SOURCE] = None
    elif case == "missing_hash": row["metadata"][HASH] = None
    elif case == "bad_hash": row["metadata"][HASH] = "0" * 64
    elif case == "credential": row = descriptor(source=TOKEN)
    elif case == "surrogate": row = descriptor(source="\ud800")
    elif case == "source_not_object": row = descriptor(source="[]")
    else:
        if case == "namespace_class": source["@CLASS"] = "unknown"
        elif case == "namespace_urn": source["urn"] = ""
        elif case == "project": source["projectName"] = "foreign"
        elif case == "timestamp": source["lastUpdateTimestamp"] = 8
        elif case == "inactive": source["inactive"] = True
        elif case == "statements": source["statements"] = None
        elif case == "absent_model": source["statements"] = []
        else: kind = "NAMESPACE"; source["urn"] = "other"
        row = descriptor(kind, json.dumps(source))
    with pytest.raises(ProtocolError): _descriptor(row, kind, "resources-1", lambda v: v.replace(TOKEN, "REDACTED"))


def test_complete_catalog_empty_errors_duplicate_or_mismatched_namespaces():
    query = ModelCandidateQuery()
    assert _catalog(encoded({"results": [], "namespaces": [], "empty": True}), "resources-1", URL, query, lambda v: v).candidates == ()
    for value in (None, {}, {**response(), "empty": True}, {**response(), "results": [descriptor()] * 129},
        {**response(), "namespaces": [descriptor("NAMESPACE")] * 2}, {**response(), "namespaces": []},
        {**response(), "results": [descriptor()] * 2}):
        with pytest.raises(ProtocolError): _catalog(encoded(value), "resources-1", URL, query, lambda v: v)
    with pytest.raises(ServerError) as error:
        _catalog(encoded({**response(), "notifications": [{"level": "ERROR", "message": TOKEN}]}), "resources-1", URL, query, lambda v: v.replace(TOKEN, "REDACTED"))
    assert TOKEN not in str(error.value)


def test_preflight_and_exact_identity_secrets():
    with Client("https://runtime.test", TOKEN) as client:
        for query, timeout in ((None, 10), (ModelCandidateQuery(), True), (ModelCandidateQuery(), 0), (ModelCandidateQuery(), float("nan"))):
            with pytest.raises(InvalidRequestError): inspect_model_candidates(client, query, timeout=timeout)
        with pytest.raises(InvalidRequestError): asyncio.run(async_inspect_model_candidates(client, ModelCandidateQuery()))
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN) as client:
            with pytest.raises(InvalidRequestError): inspect_model_candidates(client, ModelCandidateQuery())
    asyncio.run(run())
    source = namespace(); source["urn"] = TOKEN; source["statements"][0]["urn"] = TOKEN
    row = descriptor(source=json.dumps(source)); row["resourceUrn"] = TOKEN
    with pytest.raises(ProtocolError): _descriptor(row, "MODEL", "resources-1", lambda v: "REDACTED" if v == TOKEN else v)


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("status", [404, 500])
def test_advertised_but_failing_supplier_is_not_silently_empty_or_fallback(native_async, status):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(200, json=capabilities()) if request.method == "GET" else httpx.Response(status, text="Synthetic supplier failure")
    async def run():
        async with AsyncClient("https://runtime.test", TOKEN, resources=Endpoint(URL, TOKEN), http_transport=httpx.MockTransport(handler)) as client:
            with pytest.raises(MissingAssetError if status == 404 else ServerError) as failure: await async_inspect_model_candidates(client, ModelCandidateQuery())
            assert getattr(failure.value, "status_code", None) == status
    if native_async: asyncio.run(run())
    else:
        with Client("https://runtime.test", TOKEN, resources=Endpoint(URL, TOKEN), http_transport=httpx.MockTransport(handler)) as client:
            with pytest.raises(MissingAssetError if status == 404 else ServerError) as failure: inspect_model_candidates(client, ModelCandidateQuery())
            assert getattr(failure.value, "status_code", None) == status
    assert len(calls) == 2
