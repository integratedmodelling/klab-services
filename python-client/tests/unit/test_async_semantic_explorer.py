"""Native transport and checked DTO parity; mocked semantics are not scientific inference."""
import asyncio
from copy import deepcopy
import json

import httpx
import pytest

from klab_client import AsyncClient, AsyncSemanticExplorer, Client, Endpoint, SemanticExplorer, StaleKnowledgeError
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError)
from test_semantic_explorer import CAPABILITIES, CONCEPT, PAGE, setup as sync_setup


def setup(*, caps=None, page=None, response=None, status=200, **options):
    calls = []
    caps = deepcopy(CAPABILITIES) if caps is None else caps
    async def handler(request):
        calls.append(request)
        assert request.headers["authorization"] == "reasoner-secret"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "runtime-id"
        assert request.url.host == "reasoner.invalid"
        path = request.url.path
        if path.endswith("/public/capabilities"):
            return httpx.Response(200, json=caps)
        if status != 200:
            return httpx.Response(status, json={"message": "failure"})
        if response:
            return httpx.Response(200, content=json.dumps(response(request)))
        if path.endswith("/authority/search"):
            payload = PAGE if page is None else page
        elif path.endswith("/authority/documentation"):
            payload = {"text/html": "https://untrusted.invalid/document"}
        elif path.endswith("/resolve/observable"):
            payload = {"urn": "TAXA:123", "semantics": CONCEPT, "artifactType": "CONCEPT"}
        elif path.endswith(("/parents", "/children")):
            payload = [CONCEPT]
        elif path.endswith(("/subsumes", "/satisfiable")):
            payload = True
        else:
            payload = CONCEPT
        # Raw wire bytes deliberately exercise malformed/nonfinite server payloads;
        # HTTPX's json= encoder would reject NaN inside the mock before decoding.
        return httpx.Response(200, content=json.dumps(payload))
    api = AsyncClient("https://runtime.invalid", "runtime-secret", runtime_service_id="runtime-id",
        reasoner=Endpoint("https://reasoner.invalid/base", "reasoner-secret"), http_transport=httpx.MockTransport(handler))
    return api, AsyncSemanticExplorer(api, "s.c", **options), calls, caps


def test_all_native_async_operations_match_sync_immutable_views_and_routes():
    sync, explorer, _ = sync_setup()
    with sync:
        expected = (explorer.resolve_concept("TAXA:123"), explorer.resolve_observable("TAXA:123"),
            explorer.parents("TAXA:123"), explorer.children("TAXA:123"), explorer.is_a("fixture:Oak", "fixture:Tree"),
            explorer.satisfiable("TAXA:123"))
        page = explorer.search_authority("TAXA", "oak")
        candidate = page.select("123")
        docs, selected = explorer.documentation(candidate), explorer.resolve_candidate(candidate)
    async def run():
        api, explorer, calls, _ = setup()
        async with api:
            actual = (await explorer.resolve_concept("TAXA:123"), await explorer.resolve_observable("TAXA:123"),
                await explorer.parents("TAXA:123"), await explorer.children("TAXA:123"),
                await explorer.is_a("fixture:Oak", "fixture:Tree"), await explorer.satisfiable("TAXA:123"))
            assert actual == expected
            found = await explorer.search_authority("TAXA", "oak")
            choice = found.select("123")
            assert found.raw == page.raw and choice.raw == candidate.raw and found.knowledge == page.knowledge
            assert await explorer.documentation(choice) == docs and await explorer.resolve_candidate(choice) == selected
            with pytest.raises(TypeError):
                actual[0].raw["urn"] = "changed"
        definitions = [r.content.decode() for r in calls if r.url.path.endswith("/resolve/concept")]
        assert definitions[-4:] == ["fixture:Oak", "fixture:Tree", "TAXA:123", "TAXA:123"]
        assert all(r.url.host == "reasoner.invalid" for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("status", ["OK", "UNSUPPORTED", "UNAVAILABLE", "FAILED"])
def test_authority_empty_success_is_distinct_from_unavailable_statuses(status):
    async def run():
        api, explorer, _, _ = setup(page={"status": status, "matches": [], "total": 0, "nextOffset": -1,
            "notifications": [] if status == "OK" else [{"level": "ERROR", "message": "not available"}]})
        async with api:
            result = await explorer.search_authority("TAXA", "nothing")
            assert result.status == status and not result.matches
            with pytest.raises(InvalidRequestError):
                result.select("123")
    asyncio.run(run())


@pytest.mark.parametrize("change", [{"serviceId": "other"}, {"worldviewId": "other"}, {"knowledgeRevision": 5}])
def test_stale_and_foreign_candidates_are_refused_without_selection_replay(change):
    async def run():
        api, explorer, calls, caps = setup()
        async with api:
            choice = (await explorer.search_authority("TAXA", "oak")).select("123")
            other = AsyncSemanticExplorer(api, "s.c")
            before = len(calls)
            with pytest.raises(InvalidRequestError):
                await other.documentation(choice)
            assert len(calls) == before
            caps.update(change)
            with pytest.raises(StaleKnowledgeError):
                await explorer.resolve_candidate(choice)
            assert not any(r.url.path.endswith("/resolve/concept") for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("revision", [None, 0])
def test_unknown_revision_is_not_a_lease(revision):
    async def run():
        api, explorer, _, _ = setup(caps=CAPABILITIES | {"knowledgeRevision": revision})
        async with api:
            result = await explorer.resolve_concept("TAXA:123")
            assert result.knowledge.revision is None and result.knowledge.freshness.startswith("unknown:")
    asyncio.run(run())


@pytest.mark.parametrize("status,error", [(401, AuthenticationError), (403, AuthorizationError),
    (404, MissingAssetError), (503, ServerError)])
def test_native_semantic_remote_errors_remain_distinct(status, error):
    async def run():
        api, explorer, _, _ = setup(status=status)
        async with api:
            with pytest.raises(error):
                await explorer.resolve_concept("TAXA:123")
    asyncio.run(run())


@pytest.mark.parametrize("payload,error", [(CONCEPT | {"urn": "owl:Nothing"}, MissingAssetError),
    (CONCEPT | {"type": ["FUTURE_CLASS"]}, UnsupportedOperationError),
    (CONCEPT | {"urn": "reasoner-secret"}, ProtocolError), ({}, ProtocolError)])
def test_missing_malformed_secret_or_unknown_classification_is_not_a_valid_result(payload, error):
    async def run():
        api, explorer, _, _ = setup(response=lambda _: payload)
        async with api:
            with pytest.raises(error):
                await explorer.resolve_concept("TAXA:123")
    asyncio.run(run())


@pytest.mark.parametrize("operation,payload", [("parents", {}), ("children", [CONCEPT, CONCEPT]),
    ("is_a", 1), ("satisfiable", "true")])
def test_hierarchy_budgets_and_boolean_predicates_remain_strict(operation, payload):
    async def run():
        api, explorer, _, _ = setup(max_items=1,
            response=lambda r: CONCEPT if r.url.path.endswith("/resolve/concept") else payload)
        async with api:
            with pytest.raises(ProtocolError):
                await getattr(explorer, operation)(*(["one", "two"] if operation == "is_a" else ["one"]))
    asyncio.run(run())


def test_native_type_preflight_cancellation_and_no_documentation_url_following():
    with Client("https://runtime.invalid", "test-only") as sync:
        with pytest.raises(InvalidRequestError):
            AsyncSemanticExplorer(sync)
    async def run():
        api, explorer, calls, _ = setup()
        async with api:
            with pytest.raises(UnsupportedOperationError):
                SemanticExplorer(api)
            with pytest.raises(InvalidRequestError):
                await explorer.search_authority("TAXA", "oak", offset=-1)
            assert not calls
        entered, paths = asyncio.Event(), []
        async def handler(request):
            paths.append(request.url.path)
            entered.set()
            await asyncio.Event().wait()
        async with AsyncClient("https://runtime.invalid", "test-only", reasoner=Endpoint("https://reasoner.invalid", "token"),
            http_transport=httpx.MockTransport(handler)) as api:
            task = asyncio.create_task(AsyncSemanticExplorer(api).parents("known"))
            await asyncio.wait_for(entered.wait(), 1)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        assert paths == ["/public/capabilities"]
    asyncio.run(run())


@pytest.mark.parametrize("page", [PAGE | {"status": "FUTURE"}, PAGE | {"total": -1},
    PAGE | {"nextOffset": 0}, PAGE | {"matches": [PAGE["matches"][0], PAGE["matches"][0]]},
    PAGE | {"matches": [PAGE["matches"][0] | {"score": float("nan")}]},
    PAGE | {"matches": [PAGE["matches"][0] | {"authorityName": "OTHER"}]}])
def test_malformed_authority_page_stays_protocol_error(page):
    async def run():
        api, explorer, _, _ = setup(page=page)
        async with api:
            with pytest.raises(ProtocolError):
                await explorer.search_authority("TAXA", "oak")
    asyncio.run(run())


@pytest.mark.parametrize("links", [{"text/html": "file:///private"}, {"bad": "https://example.invalid"},
    {"text/html": "https://user:pass@example.invalid"}, {"text/html": 1}])
def test_documentation_descriptors_are_checked_without_following_urls(links):
    async def run():
        api, explorer, calls, _ = setup()
        async with api:
            choice = (await explorer.search_authority("TAXA", "oak")).select("123")
            async def response(request):
                calls.append(request)
                return httpx.Response(200, json=CAPABILITIES if request.url.path.endswith("/public/capabilities") else links)
            api.transport._http._transport = httpx.MockTransport(response)
            with pytest.raises(ProtocolError):
                await explorer.documentation(choice)
            assert all(r.url.host == "reasoner.invalid" for r in calls)
    asyncio.run(run())


def test_inflight_drift_byte_budget_and_redacted_display_remain_checked():
    async def run():
        caps = deepcopy(CAPABILITIES)
        def drift(request):
            caps["knowledgeRevision"] += 1
            return CONCEPT
        api, explorer, _, _ = setup(caps=caps, response=drift)
        async with api:
            with pytest.raises(StaleKnowledgeError):
                await explorer.resolve_concept("known")
        api, explorer, _, _ = setup(max_response_bytes=16)
        async with api:
            with pytest.raises(ProtocolError):
                await explorer.resolve_concept("known")
        api, explorer, _, _ = setup(response=lambda _: CONCEPT | {"displayLabel": "reasoner-secret"})
        async with api:
            result = await explorer.resolve_concept("known")
            assert "reasoner-secret" not in result.summary() and result.label == "[REDACTED]"
    asyncio.run(run())
