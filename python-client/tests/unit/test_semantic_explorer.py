"""Source-derived contracts: AuthorityBindings, DTOs and ReasonerController at milestone base."""
from copy import deepcopy
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import Client, Endpoint, SemanticExplorer, StaleKnowledgeError
from klab_client._inspection import bounds, freeze, text
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError)
from klab_client.semantics import _locator

CONCEPT = {"@CLASS": "org.integratedmodelling.common.knowledge.ConceptImpl", "urn": "TAXA:123",
    "type": ["IDENTITY"], "displayLabel": "<oak>", "notifications": []}
CAPABILITIES = {"serviceId": "reasoner-1", "worldviewId": "worldview-1", "knowledgeRevision": 4,
    "consistent": True, "authorityBindings": [{"localId": "TAXA", "providerUrn": "authority.taxa"}]}
MATCH = {"id": "123", "authorityName": "TAXA", "locator": "TAXA:123", "label": "oak",
    "description": "A candidate", "score": 1.0, "notifications": [], "aliases": ["TREES:oak"]}
PAGE = {"status": "OK", "matches": [MATCH], "total": 1, "nextOffset": -1, "notifications": []}


def setup(*, page=None, capabilities=None, response=None, status=200, **options):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["authorization"] == "reasoner-secret"
        assert request.headers["klab-scope"] == "s.c"
        assert request.headers["klab-service"] == "runtime-id"
        assert request.headers["accept-encoding"] == "identity"
        path = request.url.path
        if path.endswith("/public/capabilities"):
            return httpx.Response(200, json=CAPABILITIES if capabilities is None else capabilities)
        if status != 200:
            return httpx.Response(status, json={"message": "failure"})
        if path.endswith("/authority/search"):
            return httpx.Response(200, content=json.dumps(PAGE if page is None else page), headers={"Content-Type": "application/json"})
        if response is not None:
            return httpx.Response(200, content=json.dumps(response(request)), headers={"Content-Type": "application/json"})
        if path.endswith("/authority/documentation"):
            return httpx.Response(200, json={"text/html": "https://untrusted.invalid/document"})
        if path.endswith("/resolve/observable"):
            return httpx.Response(200, json={"urn": "TAXA:123", "semantics": CONCEPT, "artifactType": "CONCEPT"})
        if path.endswith(("/parents", "/children")):
            return httpx.Response(200, json=[CONCEPT])
        if path.endswith(("/subsumes", "/satisfiable")):
            return httpx.Response(200, json=True)
        return httpx.Response(200, json=CONCEPT)
    client = Client("https://runtime.invalid", "runtime-secret", runtime_service_id="runtime-id",
        reasoner=Endpoint("https://reasoner.invalid/base", "reasoner-secret"), http_transport=httpx.MockTransport(handler))
    return client, SemanticExplorer(client, "s.c", **options), calls


def test_search_explicit_selection_documentation_resolution_and_safe_summaries():
    client, explorer, calls = setup()
    with client:
        page = explorer.search_authority("TAXA", "oak")
        candidate = page.select("123")
        assert page.status == "OK" and page.total == 1 and page.next_offset is None
        assert candidate.raw["aliases"] == ("TREES:oak",)
        assert candidate.score == 1.0 and candidate.description == "A candidate"
        assert candidate.knowledge.scope == "s.c" and candidate.knowledge.revision == 4
        links = explorer.documentation(candidate)
        assert links["text/html"] == "https://untrusted.invalid/document"
        result = explorer.resolve_candidate(candidate)
        assert result.urn == "TAXA:123" and result.kind == "concept"
        assert "&lt;oak&gt;" in result._repr_html_() and "<oak>" not in result._repr_html_()
        assert "provider list total 1" in page.summary() and "TAXA:123" in page._repr_html_()
        assert "TAXA:123" in candidate._repr_html_()
        assert all(request.url.host == "reasoner.invalid" for request in calls)
        assert json.loads(calls[1].content) == {"authority": "TAXA", "query": "oak", "filter": None, "offset": 0, "limit": 20}
        documentation = next(request for request in calls if request.url.path.endswith("/authority/documentation"))
        assert dict(documentation.url.params) == {"authority": "TAXA", "identity": "123"}
        with pytest.raises(TypeError):
            candidate.raw["label"] = "changed"
        with pytest.raises(InvalidRequestError):
            page.select("oak")


def test_fresh_resolution_hierarchy_and_directional_predicates():
    client, explorer, calls = setup()
    with client:
        assert explorer.resolve_concept("TAXA:123").urn == "TAXA:123"
        assert explorer.resolve_observable("TAXA:123").kind == "observable"
        assert len(explorer.parents("TAXA:123")) == len(explorer.children("TAXA:123")) == 1
        assert explorer.is_a("fixture:Oak", "fixture:Tree") is True
        assert explorer.satisfiable("TAXA:123") is True
    request = next(request for request in calls if request.url.path.endswith("/subsumes"))
    assert json.loads(request.content) == [CONCEPT, CONCEPT]
    definitions = [request.content.decode() for request in calls if request.url.path.endswith("/resolve/concept")]
    assert definitions[-3:] == ["fixture:Oak", "fixture:Tree", "TAXA:123"]


@pytest.mark.parametrize("state", ["OK", "UNSUPPORTED", "UNAVAILABLE", "FAILED"])
def test_empty_success_is_not_other_states(state):
    payload = {"status": state, "matches": [], "total": 0, "nextOffset": -1,
        "notifications": [] if state == "OK" else [{"message": "not available", "level": "ERROR"}]}
    client, explorer, _ = setup(page=payload)
    with client:
        page = explorer.search_authority("TAXA", "nothing")
        assert page.status == state and not page.matches
        with pytest.raises(InvalidRequestError):
            page.select("123")


def test_pagination_provider_count_and_no_cache():
    client, explorer, calls = setup(page={**PAGE, "total": 5, "nextOffset": 3})
    with client:
        result = explorer.search_authority("TAXA", "oak", filter="GENUS", offset=2, limit=1)
        assert result.next_offset == 3
        explorer.search_authority("TAXA", "oak", filter="GENUS", offset=2, limit=1)
    assert len(calls) == 6


@pytest.mark.parametrize("change", [{"knowledgeRevision": 5}, {"worldviewId": "new"}, {"serviceId": "new"}])
def test_stale_selection_and_inflight_drift_reject(change):
    caps = deepcopy(CAPABILITIES)
    client, explorer, _ = setup(capabilities=caps)
    with client:
        candidate = explorer.search_authority("TAXA", "oak").select("123")
        caps.update(change)
        with pytest.raises(StaleKnowledgeError):
            explorer.resolve_candidate(candidate)
    caps = deepcopy(CAPABILITIES)
    def response(request):
        caps.update(change)
        return CONCEPT
    client, explorer, _ = setup(capabilities=caps, response=response)
    with client, pytest.raises(StaleKnowledgeError):
        explorer.resolve_concept("TAXA:123")


@pytest.mark.parametrize("revision", [None, 0])
def test_unknown_revision_guarantee_explicit(revision):
    client, explorer, _ = setup(capabilities={**CAPABILITIES, "knowledgeRevision": revision})
    with client:
        state = explorer.resolve_concept("TAXA:123").knowledge
    assert state.revision is None and state.freshness.startswith("unknown:")


@pytest.mark.parametrize("caps", [{**CAPABILITIES, "consistent": False}, {**CAPABILITIES, "consistent": None},
    {**CAPABILITIES, "knowledgeRevision": True}, {**CAPABILITIES, "knowledgeRevision": -1},
    {**CAPABILITIES, "worldviewId": None}, {**CAPABILITIES, "serviceId": ""}])
def test_invalid_knowledge_fails(caps):
    client, explorer, _ = setup(capabilities=caps)
    with client, pytest.raises(ProtocolError):
        explorer.resolve_concept("TAXA:123")


@pytest.mark.parametrize("score", [10**400, -(10**400)], ids=["too-large", "too-small"])
def test_oversized_authority_scores_are_reported_as_protocol_errors(score):
    page = {**PAGE, "matches": [{**MATCH, "score": score}]}
    client, explorer, _ = setup(page=page)
    with client, pytest.raises(ProtocolError, match="score"):
        explorer.search_authority("TAXA", "oak")


@pytest.mark.parametrize("kwargs", [{"authority": "authority.taxa"}, {"query": ""}, {"query": "x" * 257},
    {"query": "😀" * 129}, {"filter": ""}, {"offset": -1}, {"offset": True}, {"offset": 10001},
    {"limit": 0}, {"limit": 101}, {"limit": True}])
def test_bad_search_input_never_sends(kwargs):
    client, explorer, calls = setup()
    with client, pytest.raises(InvalidRequestError):
        explorer.search_authority(**{"authority": "TAXA", "query": "oak", **kwargs})
    assert not calls


@pytest.mark.parametrize("changes", [{"status": []}, {"status": "NEW"}, {"matches": None}, {"matches": [MATCH] * 21},
    {"total": True}, {"total": -1}, {"total": 0}, {"nextOffset": 0}, {"nextOffset": 10001},
    {"nextOffset": None}, {"nextOffset": -2}, {"status": "FAILED"}, {"notifications": None}])
def test_invalid_search_page(changes):
    client, explorer, _ = setup(page={**PAGE, **changes})
    with client, pytest.raises(ProtocolError):
        explorer.search_authority("TAXA", "oak")


@pytest.mark.parametrize("change", [{"id": None}, {"authorityName": "OTHER"}, {"locator": "TAXA:999"},
    {"score": True}, {"score": float("inf")}, {"score": "1"}, {"label": 1}, {"description": []}])
def test_invalid_candidate(change):
    client, explorer, _ = setup(page={**PAGE, "matches": [{**MATCH, **change}]})
    with client, pytest.raises(ProtocolError):
        explorer.search_authority("TAXA", "oak")


def test_duplicate_identity_and_foreign_candidate_reject():
    client, explorer, _ = setup(page={**PAGE, "matches": [MATCH, MATCH], "total": 2})
    with client, pytest.raises(ProtocolError):
        explorer.search_authority("TAXA", "oak")
    client, explorer, _ = setup()
    with client:
        candidate = explorer.search_authority("TAXA", "oak").select("123")
        other = SemanticExplorer(client, "s.c")
        with pytest.raises(InvalidRequestError):
            other.documentation(candidate)
        with pytest.raises(InvalidRequestError):
            explorer.resolve_candidate(None)


@pytest.mark.parametrize("links", [{"text/html": "file:///private"}, {"text/html": "https://u:p@example.org"},
    {"text/html": "https:///missing-host"}, {"invalid": "https://example.org"}, {"text/html": 3}])
def test_documentation_rejects_malformed_without_fetch(links):
    client, explorer, _ = setup(response=lambda request: links)
    with client:
        candidate = explorer.search_authority("TAXA", "oak").select("123")
        with pytest.raises(ProtocolError):
            explorer.documentation(candidate)


@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (404, MissingAssetError), (503, ServerError)])
def test_transport_failures_not_empty(status, error):
    client, explorer, _ = setup(status=status)
    with client, pytest.raises(error):
        explorer.search_authority("TAXA", "oak")


@pytest.mark.parametrize("value,error", [(None, ProtocolError), ({**CONCEPT, "urn": "owl:Nothing"}, MissingAssetError),
    ({**CONCEPT, "type": ["NOTHING"]}, MissingAssetError), ({**CONCEPT, "type": ["NEW"]}, UnsupportedOperationError),
    ({**CONCEPT, "displayLabel": []}, ProtocolError)])
def test_unknown_or_unresolved_semantics(value, error):
    client, explorer, _ = setup(response=lambda request: value)
    with client, pytest.raises(error):
        explorer.resolve_concept("TAXA:123")


@pytest.mark.parametrize("method", ["parents", "children", "is_a", "satisfiable"])
def test_malformed_neighbors_and_predicates(method):
    def response(request):
        return CONCEPT if request.url.path.endswith("/resolve/concept") else None
    client, explorer, _ = setup(response=response)
    with client, pytest.raises(ProtocolError):
        getattr(explorer, method)(*("TAXA:123", "TAXA:456") if method == "is_a" else ("TAXA:123",))


def test_budget_redaction_harmless_extensions_and_error_notifications():
    payload = {**CONCEPT, "token": "secret", "future": [{"value": "reasoner-secret"}, True, None, 2.5]}
    client, explorer, _ = setup(response=lambda request: payload)
    with client:
        result = explorer.resolve_concept("TAXA:123")
        assert "token" not in result.raw and result.raw["future"][0]["value"] == "[REDACTED]"
    client, explorer, _ = setup(max_response_bytes=100)
    with client, pytest.raises(ProtocolError, match="byte budget"):
        explorer.resolve_concept("TAXA:123")
    client, explorer, _ = setup(page={**PAGE, "notifications": [{"level": "ERROR", "message": "reasoner-secret"}]})
    with client, pytest.raises(ServerError, match="REDACTED"):
        explorer.search_authority("TAXA", "oak")


@pytest.mark.parametrize("code", ["oak", "AA", "123", "A:B", "A B", "a]b\\c"])
def test_java_authority_terminal_encoding(code):
    locator = _locator("TAXA", code)
    assert locator.startswith("TAXA:")
    assert locator == ("TAXA:" + code if code in ("oak", "AA", "123") else "TAXA:[" + code.replace("\\", "\\\\").replace("]", "\\]") + "]")


def test_common_projection_limits_and_safe_text():
    for value in (0, True, 11):
        with pytest.raises(InvalidRequestError):
            bounds(value, 10, "budget")
    for value in (None, "", "\n", "\x7f", "x" * 11):
        with pytest.raises(InvalidRequestError):
            text(value, "input", 10)
    for value in (float("nan"), object(), {1: "not a text key"}):
        with pytest.raises(ProtocolError):
            freeze(value)
    nested = None
    for _ in range(34):
        nested = [nested]
    with pytest.raises(ProtocolError):
        freeze(nested)
    assert freeze({"count": 0, "valid": False, "password": "secret", "accessKey": "secret"}) == {"count": 0, "valid": False}


def test_optional_fields_absent_and_candidate_errors():
    candidate = {"id": "123", "authorityName": "TAXA", "locator": "TAXA:123"}
    client, explorer, _ = setup(page={**PAGE, "matches": [candidate]}, response=lambda request: {**CONCEPT, "displayLabel": None})
    with client:
        selected = explorer.search_authority("TAXA", "123").select("123")
        assert selected.label is selected.description is selected.score is None
        assert selected.summary() == "TAXA:123"
        assert "—" not in explorer.resolve_candidate(selected).summary()
        invalid = replace(selected, raw={**selected.raw, "notifications": [{"message": "bad identity", "level": "ERROR"}]})
        with pytest.raises(ServerError):
            explorer.resolve_candidate(invalid)


def test_documentation_drift_and_hierarchy_item_budget():
    caps = deepcopy(CAPABILITIES)
    def documents(request):
        caps["knowledgeRevision"] += 1
        return {}
    client, explorer, _ = setup(capabilities=caps, response=documents)
    with client:
        candidate = explorer.search_authority("TAXA", "oak").select("123")
        with pytest.raises(StaleKnowledgeError):
            explorer.documentation(candidate)
    def neighbors(request):
        return CONCEPT if request.url.path.endswith("/resolve/concept") else [CONCEPT, CONCEPT]
    client, explorer, _ = setup(response=neighbors, max_items=1)
    with client, pytest.raises(ProtocolError, match="item budget"):
        explorer.parents("TAXA:123")
