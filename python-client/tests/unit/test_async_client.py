"""Native async workflow/DTO parity. Mocked responses are not scientific acceptance."""
import asyncio
from copy import deepcopy
from decimal import Decimal
import json
from pathlib import Path

import httpx
import pytest

from klab_client import Client, ContextOptions, ObservationOptions, ObservationRequest, ResourceInspector, SemanticExplorer
from klab_client.async_client import AsyncClient, AsyncContext, AsyncJob, AsyncSession, AsyncReasoner
from klab_client.dto import observable_from_wire, observation_from_wire
from klab_client.errors import (ConfigurationError, InvalidRequestError, JobCancelledError,
                               JobFailedError, JobUnavailableError, MissingAssetError,
                               ProtocolError, SubmissionOutcomeUnknown, TransportError,
                               UnsupportedOperationError, WaitTimeout)
from klab_client.transport import Endpoint


def observation():
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    # Match the existing typed facade's explicit current activity fixture.
    payload["observable"]["contextualization"] = "MEASURE"
    return payload


def client(handler, **kwargs):
    return AsyncClient("https://runtime.invalid", "runtime-secret", agent_name="scientist",
        reasoner=Endpoint("https://reasoner.invalid", "reasoner-secret"),
        resources=Endpoint("https://resources.invalid", "resources-secret"),
        resolver=Endpoint("https://resolver.invalid", "resolver-secret"),
        runtime_service_id="runtime-id", poll_interval=.001, http_transport=httpx.MockTransport(handler), **kwargs)


def test_complete_typed_async_workflow_and_shared_scientific_decoding():
    async def scenario():
        calls, states = [], iter(["WAITING", "FINISHED"])
        payload = observation()
        async def handler(request):
            calls.append(request)
            path = request.url.path
            if path == "/createSession":
                body = json.loads(request.content)
                assert len(body["configuration"]["id"]) == 32
                return httpx.Response(200, text="session")
            if path == "/createContext":
                assert json.loads(request.content)["configuration"]["name"] == "async context"
                return httpx.Response(200, json={"id": "session.context", "serviceId": "runtime-id"})
            if path == "/api/v1/resolve/observable":
                assert request.headers["Authorization"] == "reasoner-secret"
                return httpx.Response(200, json=payload["observable"])
            if path == "/api/v1/submit":
                assert request.headers["klab-scope"] == "session.context.8#9"
                assert request.headers["klab-service"] == "runtime-id"
                assert json.loads(request.content)["resolutionConstraints"][0]["type"] == "Observer"
                return httpx.Response(200, json=1)
            if path == "/jobs/status/1":
                return httpx.Response(200, json={"status": next(states)})
            if path == "/jobs/retrieve/1":
                return httpx.Response(200, json=payload)
            if path == "/api/v1/observation/value":
                offset = json.loads(request.content)["offset"]
                return httpx.Response(200, text=["0", "null", "123.25"][offset])
            if path in {"/releaseContext", "/releaseSession"}:
                expected = "session.context" if path == "/releaseContext" else "session"
                assert request.headers["klab-scope"] == expected
                return httpx.Response(200, json=True)
            raise AssertionError(path)
        async with client(handler) as api:
            session = await api.create_session()
            context = await session.create_context(options=ContextOptions(name="async context"))
            focused = context.within(8)
            job = await focused.observe(ObservationRequest("geography:Elevation in m", options=ObservationOptions(observer=9)))
            assert isinstance(job, AsyncJob) and isinstance(job.scope, AsyncContext)
            result = await job.wait(timeout=1)
            values = await result.fetch_data([0, 1, 2], curve="D2_YX")
            assert values.values == (Decimal(0), None, Decimal("123.25"))
            assert values.units == "m" and result.id == 42
            assert await focused.release() and await session.release()
            assert job.to_handle().runtime_id == "runtime-id"
        assert api.transport._http.is_closed
        assert len(calls) == 12
    asyncio.run(scenario())


@pytest.mark.parametrize("state,error", [("ABORTED", JobFailedError), ("INTERRUPTED", JobCancelledError),
                                       ("EMPTY", JobUnavailableError), ("UNKNOWN", ProtocolError)])
def test_shared_job_states_not_relabelled_as_success(state, error):
    async def scenario():
        async with client(lambda request: httpx.Response(200, json={"status": state})) as api:
            with pytest.raises(error):
                await AsyncContext(api, {"id": "s.c"}).job(1).result(1)
    asyncio.run(scenario())


@pytest.mark.parametrize("kind", ["invalid_timeout", "timeout", "cancel_wait", "retrieve_timeout", "network"])
def test_wait_boundaries_retain_handle_and_never_cancel_or_replay(kind):
    async def scenario():
        calls, entered = [], asyncio.Event()
        finished = False
        async def handler(request):
            calls.append(request.url.path)
            if kind == "network":
                raise httpx.ReadError("transport")
            if request.url.path.startswith("/jobs/status"):
                entered.set()
                return httpx.Response(200, json={"status": "FINISHED" if finished or kind == "retrieve_timeout" else "WAITING"})
            if kind == "retrieve_timeout":
                await asyncio.Event().wait()
            return httpx.Response(200, json=observation())
        async with client(handler) as api:
            job = AsyncContext(api, {"id": "s.c"}).job(1)
            if kind == "invalid_timeout":
                with pytest.raises(InvalidRequestError):
                    await job.result(-1)
                assert not calls
            elif kind == "cancel_wait":
                task = asyncio.create_task(job.result())
                await entered.wait()
                task.cancel()
                with pytest.raises(asyncio.CancelledError) as caught:
                    await task
                assert "retained" in caught.value.__notes__[0]
                finished = True
                assert (await job.result(1)).id == 42
            else:
                with pytest.raises(TransportError if kind == "network" else WaitTimeout) as caught:
                    await job.result(.015)
                if kind != "network":
                    assert caught.value.job is job
            assert not any("submit" in route or "cancel" in route or "release" in route for route in calls)
    asyncio.run(scenario())


@pytest.mark.parametrize("accepted", [True, False, "true"])
def test_cancel_acceptance_does_not_claim_interruption(accepted):
    async def scenario():
        async with client(lambda request: httpx.Response(200, json=accepted)) as api:
            job = AsyncContext(api, {"id": "s.c"}).job(1)
            if isinstance(accepted, bool):
                assert await job.cancel() is accepted
                assert job.cancellation_requested is accepted
            else:
                with pytest.raises(ProtocolError):
                    await job.cancel()
    asyncio.run(scenario())


def test_zero_wait_timeout_preserves_known_job_without_any_http():
    async def scenario():
        async def handler(request):
            pytest.fail("An expired local wait must not poll, cancel or submit")
        async with client(handler) as api:
            job = AsyncContext(api, {"id": "s.c"}).job(7)
            with pytest.raises(WaitTimeout) as caught:
                await job.result(0)
            assert caught.value.job is job
    asyncio.run(scenario())


@pytest.mark.parametrize("payload", [{"empty": True}, {"notifications": [{"level": "Error", "message": "failure"}]}])
def test_failed_result_notifications_stay_failures(payload):
    async def scenario():
        async def handler(request):
            return httpx.Response(200, json={"status": "FINISHED"} if "status" in request.url.path else payload)
        async with client(handler) as api:
            with pytest.raises(JobFailedError):
                await AsyncContext(api, {"id": "s.c"}).job(1).result()
    asyncio.run(scenario())


def test_scope_advertisement_and_all_read_only_service_facades():
    async def scenario():
        payload, calls = observation(), []
        async def handler(request):
            calls.append(request)
            path = request.url.path
            service = request.url.host.split('.')[0]
            if path == "/public/capabilities":
                return httpx.Response(200, json={"serviceId": service + "-id"})
            if path == "/public/status":
                return httpx.Response(200, json={"operational": True})
            if path == "/notifyUserScope":
                return httpx.Response(200, json=True)
            if path == "/api/v1/connect":
                return httpx.Response(200, json={"id": "s.c", "serviceId": "runtime-id"})
            if path == "/api/v1/contexts":
                return httpx.Response(200, json=[{"configuration": {"id": "s.c"}}])
            if path == "/api/v1/resolve/concept":
                return httpx.Response(200, json=payload["observable"]["semantics"])
            if path.startswith("/api/v1/retrieve"):
                return httpx.Response(200, json={"urn": "asset"})
            if path.startswith("/api/v1/list"):
                return httpx.Response(200, json=[{"urn": "asset"}])
            if path.startswith("/api/v1/resolve/RESOURCE"):
                return httpx.Response(200, json={"results": [{"resourceUrn": "asset"}]})
            if path == "/api/v1/query":
                return httpx.Response(200, json=[{"id": 42}])
            if path in {"/api/v1/resource", "/jobs/retrieve/3"}:
                return httpx.Response(200, json={"urn": "dataflow"})
            if path == "/api/v1/resources":
                return httpx.Response(200, json=[{"urn": "resource"}])
            if path in {"/api/v1/resolve", "/api/v1/submit"}:
                return httpx.Response(200, json=3)
            if path == "/jobs/status/3":
                return httpx.Response(200, json={"status": "FINISHED"})
            raise AssertionError(path)
        async with client(handler) as api:
            assert len(await api.initialize_user_scope()) == 4
            context = await api.attach_context("s.c")
            assert not context.owned
            assert (await api.runtime.connect_context({"id": "s.c"})).id == "s.c"
            assert (await api.runtime.get_context_info())[0].session_id == "s"
            assert (await api.reasoner.resolve_concept("geography:Elevation")).urn
            assert (await api.resources.retrieve("asset")).urn == "asset"
            assert len(await api.resources.list()) == 1
            assert (await api.resources.resolve("asset")).urns == ["asset"]
            assert await api.runtime.query_knowledge_graph({"type": "QUERY"}, context) == [{"id": 42}]
            raw = observation_from_wire(payload, context)
            assert (await api.resolver.resolve(raw, context)).result_kind == "dataflow"
            job = await api.resolver.resolve(raw, context)
            assert await job.result() == {"urn": "dataflow"}
            assert await api.resolver.submit_resource(raw, context) == {"urn": "dataflow"}
            assert await api.resolver.get_submitted_resources(context) == [{"urn": "resource"}]
            assert (await api.runtime.submit(raw, context)).id == 3
        assert len([r for r in calls if r.url.path == "/notifyUserScope"]) == 4
    asyncio.run(scenario())


@pytest.mark.parametrize("operation,payload,error", [
    ("retrieve", None, MissingAssetError), ("list", {}, ProtocolError),
    ("resolve", {"results": None}, ProtocolError), ("contexts", {}, ProtocolError),
    ("query", {}, ProtocolError), ("submitted", {}, ProtocolError),
    ("release", "true", ProtocolError), ("session", "s.c", ProtocolError),
    ("advertise", False, ProtocolError),
])
def test_malformed_service_responses_are_not_empty_successes(operation, payload, error):
    async def scenario():
        async def handler(request):
            if request.url.path == "/public/capabilities":
                return httpx.Response(200, json={"serviceId": request.url.host.split('.')[0] + "-id"})
            if request.url.path == "/public/status":
                return httpx.Response(200, json={})
            return httpx.Response(200, content=json.dumps(payload))
        async with client(handler) as api:
            context = AsyncContext(api, {"id": "s.c"})
            operations = {"retrieve": lambda: api.resources.retrieve("asset"), "list": api.resources.list,
                "resolve": lambda: api.resources.resolve("asset"), "contexts": api.runtime.get_context_info,
                "query": lambda: api.runtime.query_knowledge_graph({}, context),
                "submitted": lambda: api.resolver.get_submitted_resources(context),
                "release": context.release, "session": api.create_session, "advertise": api.initialize_user_scope}
            with pytest.raises(error):
                await operations[operation]()
    asyncio.run(scenario())


def test_invalid_scope_binding_and_unimplemented_operations_do_not_send():
    async def scenario():
        async def handler(request):
            pytest.fail("No HTTP permitted")
        async with client(handler) as api:
            context = AsyncContext(api, {"id": "s.c"})
            raw = observation_from_wire(observation(), context)
            for operation in [lambda: api.attach_context("s.c.1"),
                lambda: context.observe(ObservationRequest(observable_from_wire(observation()["observable"])), options=ObservationOptions()),
                lambda: context.fetch_data(raw, [-1])]:
                with pytest.raises(InvalidRequestError):
                    await operation()
            for operation in [lambda: api.resources.contextualize(None), lambda: api.resolver.encode_dataflow(None),
                lambda: api.runtime.query_knowledge_graph("query", context), lambda: api.restore_handle(None),
                lambda: api.export_formats(raw), lambda: api.export_to_file(raw, "not-created", media_type="image/tiff")]:
                with pytest.raises(UnsupportedOperationError):
                    await operation()
            with pytest.raises(InvalidRequestError):
                with api:
                    pytest.fail("Synchronous context entry must fail")
            with pytest.raises(InvalidRequestError):
                api.__exit__(None, None, None)
            for inspector in (ResourceInspector, SemanticExplorer):
                with pytest.raises(UnsupportedOperationError):
                    inspector(api)
    asyncio.run(scenario())


def test_from_env_constructs_only_native_transport(monkeypatch):
    monkeypatch.setenv("KLAB_RUNTIME_URL", "https://runtime.invalid")
    async def scenario():
        api = AsyncClient.from_env()
        assert not isinstance(api, Client)
        assert isinstance(api.transport._http, httpx.AsyncClient)
        await api.close()
        assert api.transport._http.is_closed
    asyncio.run(scenario())


@pytest.mark.parametrize("mode", ["runtime_id", "void", "nothing", "missing_agent", "submit_unknown"])
def test_identity_semantic_and_submission_failures(mode):
    async def scenario():
        raw, calls = deepcopy(observation()), []
        if mode == "void":
            raw["observable"]["artifactType"] = "VOID"
        if mode == "nothing":
            raw["observable"]["semantics"]["type"] = ["NOTHING"]
        async def handler(request):
            calls.append(request)
            if mode == "runtime_id":
                return httpx.Response(200, json={"serviceId": "other-runtime"} if "capabilities" in request.url.path else {})
            if request.url.path == "/api/v1/resolve/observable":
                return httpx.Response(200, json=raw["observable"])
            raise httpx.ReadError("request outcome unknown")
        async with client(handler) as api:
            context = AsyncContext(api, {"id": "s.c"})
            if mode == "runtime_id":
                with pytest.raises(ConfigurationError):
                    await api.initialize_user_scope()
            elif mode in {"void", "nothing"}:
                with pytest.raises(MissingAssetError):
                    await api.reasoner.resolve_observable("unknown")
            else:
                if mode == "missing_agent":
                    api.agent_name = None
                with pytest.raises(ConfigurationError if mode == "missing_agent" else SubmissionOutcomeUnknown):
                    await context.submit(raw)
                assert len(calls) == (0 if mode == "missing_agent" else 1)
    asyncio.run(scenario())


def test_sync_clients_cannot_accidentally_send_inside_async_objects():
    def forbidden(request):
        pytest.fail("Mixing clients must fail before any I/O")
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(forbidden)) as sync:
        for construct in (lambda: AsyncContext(sync, {"id": "s.c"}), lambda: AsyncSession(sync, "s"),
                          lambda: AsyncJob(sync, 1, "s.c"), lambda: AsyncReasoner(sync)):
            with pytest.raises(ConfigurationError):
                construct()


@pytest.mark.parametrize("resolved", [False, True])
@pytest.mark.parametrize("observer", [None, 9])
def test_async_typed_request_wire_matches_existing_sync_facade(resolved, observer):
    from klab_client import Context
    wire = observation()
    records = []
    def handler(request):
        records.append((request.url.path, request.headers.get("klab-scope"), request.headers.get("klab-service"),
                        request.content))
        if request.url.path.endswith("/resolve/observable"):
            return httpx.Response(200, json=wire["observable"])
        return httpx.Response(200, json=7)
    request = ObservationRequest(observable_from_wire(wire["observable"]) if resolved else "geography:Elevation in m",
                                 options=ObservationOptions(observer=observer))
    with Client("https://runtime.invalid", "runtime-secret", agent_name="scientist", runtime_service_id="runtime-id",
                reasoner=Endpoint("https://reasoner.invalid", "reasoner-secret"), http_transport=httpx.MockTransport(handler)) as sync:
        Context(sync, {"id": "s.c"}).within(8).observe(request)
    expected = records[:]
    records.clear()
    async def scenario():
        async with client(handler) as api:
            job = await AsyncContext(api, {"id": "s.c"}).within(8).observe(request)
            assert isinstance(job, AsyncJob)
    asyncio.run(scenario())
    assert records == expected


def test_cancelling_one_async_wait_leaves_sibling_job_usable():
    async def scenario():
        entered = asyncio.Event()
        calls = []
        async def handler(request):
            calls.append(request.url.path)
            if request.url.path == "/jobs/status/1":
                entered.set()
                await asyncio.Event().wait()
            return httpx.Response(200, json={"status": "FINISHED"} if "status" in request.url.path else observation())
        async with client(handler, max_concurrency=2) as api:
            context = AsyncContext(api, {"id": "s.c"})
            first = asyncio.create_task(context.job(1).result())
            await asyncio.wait_for(entered.wait(), 1)
            second = asyncio.create_task(context.job(2).result(timeout=1))
            first.cancel()
            with pytest.raises(asyncio.CancelledError):
                await first
            assert (await second).id == 42
            assert not any("/cancel/" in path or "release" in path for path in calls)
    asyncio.run(scenario())
