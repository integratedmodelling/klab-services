"""RC-P1.5/CAP04/CAP18: native scope selection cannot cross roots or Java longs."""
import asyncio
from contextlib import asynccontextmanager

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.client import scope_token
from klab_client.errors import InvalidRequestError, ProtocolError


def test_shared_request_preparation_checks_non_context_scope_root_independently():
    # The public Context rejects this earlier; the pure preparation boundary must
    # still reject a scope-like adapter whose id disagrees with its selected token.
    from types import SimpleNamespace
    from klab_client.requests import ObservationRequest, _prepare_observe
    scope = SimpleNamespace(get_context_id=lambda: "s.c.7", id="s.other")
    with pytest.raises(InvalidRequestError, match="root context identity"):
        _prepare_observe(scope, ObservationRequest("geography:Elevation in m"))


def test_job_scope_identity_checks_malformed_configuration_independently():
    # Job tokenization now rejects malformed Contexts first. Retain direct evidence
    # for this independent fingerprint validator rather than excluding its guard.
    from types import SimpleNamespace
    from klab_client._job_binding import _scope_identity
    with pytest.raises(ProtocolError, match="configuration identity is malformed"):
        _scope_identity(SimpleNamespace(client=object(), configuration=None))


@asynccontextmanager
async def configured(mode):
    calls = []
    def handler(request):
        calls.append(request)
        raise AssertionError("Invalid selection must not reach HTTP")
    client_type = AsyncClient if mode == "async" else Client
    context_type = AsyncContext if mode == "async" else Context
    client = client_type("https://runtime.invalid", http_transport=httpx.MockTransport(handler))
    if mode == "async":
        async with client:
            yield client, context_type, calls
    else:
        with client:
            yield client, context_type, calls


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_focus_rejects_id_beyond_java_long_before_producing_a_view(mode):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            context = context_type(client, {"id": "s.c"})
            with pytest.raises(InvalidRequestError):
                context.within(2**63)
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("token", ["s.other", "", "s.c.9223372036854775808", "s.c#9223372036854775808"])
def test_context_token_is_an_explicit_valid_selection_of_its_own_root(mode, token):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            with pytest.raises(InvalidRequestError):
                context_type(client, {"id": "s.c"}, token=token)
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_server_root_cannot_smuggle_an_observer_selection_as_configuration_id(mode):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            with pytest.raises(ProtocolError):
                context_type(client, {"id": "s.c#3"})
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_mutated_configuration_cannot_mislabel_a_retained_selected_scope(mode):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            context = context_type(client, {"id": "s.c"}, token="s.c.7")
            context.configuration["id"] = "s.other"
            with pytest.raises(InvalidRequestError):
                scope_token(context)
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_default_and_exact_java_long_focus_observer_boundary_remain_supported_without_io(mode):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            maximum = 2**63 - 1
            root = context_type(client, {"id": "s.c"})
            assert scope_token(root) == "s.c" and scope_token(None) is None
            selected = context_type(client, {"id": "s.c"}, token=f"s.c.1#{maximum}")
            nested = selected.within(maximum)
            assert scope_token(nested) == f"s.c.1.{maximum}#{maximum}"
            assert nested.configuration is selected.configuration
            assert type(nested) is context_type
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("token", ["s.c\x00", "s.c\x7f", "s.c." + "9" * 10000,
    "s.c#" + "9" * 10000, "s.c.9223372036854775808", "s.c#9223372036854775808"])
def test_raw_scope_header_checks_controls_and_bounded_long_conversion(token):
    with pytest.raises(InvalidRequestError):
        scope_token(token)


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("configuration", [None, {}, {"id": "s.other"}])
def test_raw_runtime_query_refuses_mutated_context_before_any_http(mode, configuration):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            context = context_type(client, {"id": "s.c"})
            context.configuration = configuration
            with pytest.raises(InvalidRequestError):
                result = client.runtime.query_knowledge_graph({"type": "QUERY", "resultType": "OBSERVATION"}, context)
                if mode == "async":
                    await result
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_valid_maximum_selected_header_is_transmitted_exactly_on_existing_query_route(mode):
    calls = []
    token = f"s.c.{2**63 - 1}#{2**63 - 1}"
    def handler(request):
        calls.append(request)
        assert request.url.path == "/runtime/api/v1/query" and request.headers["klab-scope"] == token
        return httpx.Response(200, json=[])
    async def run():
        client_type = AsyncClient if mode == "async" else Client
        context_type = AsyncContext if mode == "async" else Context
        client = client_type("https://runtime.invalid/runtime", http_transport=httpx.MockTransport(handler))
        if mode == "async":
            async with client:
                context = context_type(client, {"id": "s.c"}, token=token)
                assert await client.runtime.query_knowledge_graph({"type": "QUERY", "resultType": "OBSERVATION"}, context) == []
        else:
            with client:
                context = context_type(client, {"id": "s.c"}, token=token)
                assert client.runtime.query_knowledge_graph({"type": "QUERY", "resultType": "OBSERVATION"}, context) == []
    asyncio.run(run())
    assert len(calls) == 1  # Wire precision, not proof those IDs exist or are authorized.


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("mismatch", ["origin", "runtime-id"])
def test_raw_runtime_query_cannot_route_a_foreign_bound_context(mode, mismatch):
    async def run():
        async with configured(mode) as (client, context_type, calls):
            client.runtime_service_id = "home"
            client_type = AsyncClient if mode == "async" else Client
            other = client_type("https://foreign.invalid" if mismatch == "origin" else "https://runtime.invalid",
                                runtime_service_id="other" if mismatch == "runtime-id" else "home",
                                http_transport=httpx.MockTransport(lambda request: pytest.fail("No foreign HTTP allowed")))
            context = context_type(other, {"id": "s.c", "serviceId": other.runtime_service_id})
            try:
                with pytest.raises(InvalidRequestError):
                    result = client.runtime.query_knowledge_graph({"type": "QUERY", "resultType": "OBSERVATION"}, context)
                    if mode == "async":
                        await result
            finally:
                if mode == "async":
                    await other.aclose()
                else:
                    other.close()
            assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_same_backend_context_from_another_client_uses_only_current_request_credentials(mode):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["authorization"] == "destination-token"
        assert request.headers["klab-scope"] == "s.c.7#8" and request.headers["klab-service"] == "home"
        return httpx.Response(200, json=[])
    async def run():
        client_type = AsyncClient if mode == "async" else Client
        context_type = AsyncContext if mode == "async" else Context
        source = client_type("https://runtime.invalid/runtime", "source-token", runtime_service_id="home",
                             http_transport=httpx.MockTransport(lambda request: pytest.fail("No source-client HTTP")))
        destination = client_type("https://runtime.invalid/runtime", "destination-token", runtime_service_id="home",
                                  http_transport=httpx.MockTransport(handler))
        context = context_type(source, {"id": "s.c", "serviceId": "home"}, token="s.c.7#8")
        try:
            query = {"type": "QUERY", "resultType": "OBSERVATION"}
            if mode == "async":
                assert await destination.runtime.query_knowledge_graph(query, context) == []
            else:
                assert destination.runtime.query_knowledge_graph(query, context) == []
        finally:
            if mode == "async":
                await destination.aclose()
                await source.aclose()
            else:
                destination.close()
                source.close()
    asyncio.run(run())
    assert len(calls) == 1  # Routing/credential choice only; server ACL remains authoritative.
