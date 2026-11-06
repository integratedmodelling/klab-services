"""Existing-route scope/root/origin selection acceptance; not new scope semantics."""
import asyncio
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context, ObservationRequest
from klab_client.errors import AuthorizationError, InvalidRequestError
from test_vertical import client_args, control, geometry


def test_same_backend_context_reads_use_current_credentials_and_foreign_binding_is_zero_http(simulator):
    runtime = simulator["endpoints"]["runtime"]
    with Client(runtime, **client_args(simulator)) as owner:
        session = owner.create_session()
        context = session.create_context()
        try:
            observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
            query = {"type": "QUERY", "resultType": "OBSERVATION", "id": observation.id, "limit": 2}
            with Client(runtime, **client_args(simulator)) as reader:
                rows = reader.runtime.query_knowledge_graph(query, context)
                assert len(rows) == 1 and rows[0]["id"] == observation.id
            bob = client_args(simulator, "sim-token-bob")
            bob["agent_name"] = "bob"
            with Client(runtime, **bob) as unauthorized:
                with pytest.raises(AuthorizationError):
                    unauthorized.runtime.query_knowledge_graph(query, context)
            before = sum(row["path"].endswith("/api/v1/query") for row in control(simulator)["trace"])
            with Client(runtime + "-foreign", runtime_service_id=simulator["service_ids"]["runtime"]) as foreign:
                selected = Context(foreign, {"id": context.id, "serviceId": simulator["service_ids"]["runtime"]})
                with pytest.raises(InvalidRequestError):
                    owner.runtime.query_knowledge_graph(query, selected)
            with pytest.raises(InvalidRequestError):
                Context(owner, context.configuration, token="other.context")
            with pytest.raises(InvalidRequestError):
                context.within(2**63)
            after = sum(row["path"].endswith("/api/v1/query") for row in control(simulator)["trace"])
            assert after == before  # No foreign query, scope recreation, or unsliced/rebound retry.
        finally:
            context.release()
            session.release()
    assert not any(control(simulator)["counts"].values())


def test_native_async_same_backend_read_and_foreign_binding_use_existing_query_route(simulator):
    async def run():
        runtime = simulator["endpoints"]["runtime"]
        async with AsyncClient(runtime, **client_args(simulator)) as owner:
            session = await owner.create_session()
            context = await session.create_context()
            try:
                observation = await (await context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))).result(timeout=2)
                query = {"type": "QUERY", "resultType": "OBSERVATION", "id": observation.id, "limit": 2}
                async with AsyncClient(runtime, **client_args(simulator)) as reader:
                    rows = await reader.runtime.query_knowledge_graph(query, context)
                    assert len(rows) == 1 and rows[0]["id"] == observation.id
                arguments = client_args(simulator, "sim-token-bob")
                arguments["agent_name"] = "bob"
                async with AsyncClient(runtime, **arguments) as unauthorized:
                    with pytest.raises(AuthorizationError):
                        await unauthorized.runtime.query_knowledge_graph(query, context)
                before = sum(row["path"].endswith("/api/v1/query") for row in control(simulator)["trace"])
                async with AsyncClient(runtime + "-foreign", runtime_service_id=simulator["service_ids"]["runtime"]) as foreign:
                    selected = AsyncContext(foreign, {"id": context.id, "serviceId": simulator["service_ids"]["runtime"]})
                    with pytest.raises(InvalidRequestError):
                        await owner.runtime.query_knowledge_graph(query, selected)
                assert before == sum(row["path"].endswith("/api/v1/query") for row in control(simulator)["trace"])
            finally:
                await context.release()
                await session.release()
    asyncio.run(run())
    assert not any(control(simulator)["counts"].values())
