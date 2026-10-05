from copy import deepcopy
from decimal import Decimal
import json
from pathlib import Path

import httpx
import pytest

from klab_client import Client, Context, Endpoint, GeometryImpl, ObservationImpl, ReasonerImpl
from klab_client.client import scope_token
from klab_client.dto import decode_cell, observable_from_wire, observation_from_wire, storage_semantics
from klab_client.errors import (
    AuthenticationError, AuthorizationError, ConfigurationError, InvalidRequestError,
    JobCancelledError, JobFailedError, JobUnavailableError, ProtocolError,
    ServerError, SubmissionOutcomeUnknown, UnsupportedOperationError, WaitTimeout,
)


@pytest.fixture
def observation():
    return json.loads((Path(__file__).parent / "fixtures" / "elevation.json").read_text())


def make_client(handler, **kwargs):
    return Client("https://runtime.invalid", "runtime-secret", agent_name="scientist",
                  reasoner=Endpoint("https://reasoner.invalid", "reasoner-secret"),
                  resources=Endpoint("https://resources.invalid", "resources-secret"),
                  resolver=Endpoint("https://resolver.invalid", "resolver-secret"),
                  runtime_service_id="runtime-id", poll_interval=.001,
                  http_transport=httpx.MockTransport(handler), **kwargs)


def test_complete_workflow_requests_and_data(observation):
    calls = []
    status = iter(["WAITING", "FINISHED"])

    def handler(request):
        calls.append(request)
        path = request.url.path
        if path == "/createSession":
            assert json.loads(request.content) == {"configuration": {
                "@CLASS": "org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl",
                "name": "test"}, "serviceIds": []}
            assert "klab-scope" not in request.headers
            return httpx.Response(200, text="session")
        if path == "/createContext":
            assert request.headers["klab-scope"] == "session"
            assert json.loads(request.content)["configuration"]["persistence"] == "ONE_OFF"
            return httpx.Response(200, json={"id": "session.context", "notifications": []})
        if path == "/api/v1/resolve/observable":
            assert request.headers["Content-Type"] == "text/plain"
            assert request.content == b"geography:Elevation in m"
            assert request.headers["Authorization"] == "reasoner-secret"
            return httpx.Response(200, json=observation["observable"])
        assert request.headers["Authorization"] == "runtime-secret"
        if path == "/api/v1/submit":
            envelope = json.loads(request.content)
            assert envelope["agentName"] == "scientist"
            assert envelope["resolutionConstraints"] == []
            assert envelope["observation"]["id"] == -1
            assert envelope["observation"]["@CLASS"] == observation["@CLASS"]
            assert envelope["observation"]["observable"] == observation["observable"]
            assert request.headers["klab-scope"] == "session.context.8"
            assert request.headers["klab-service"] == "runtime-id"
            return httpx.Response(200, json=1)
        if path == "/jobs/status/1":
            return httpx.Response(200, json={"status": next(status), "stackTrace": None})
        if path == "/jobs/retrieve/1":
            return httpx.Response(200, text=json.dumps(observation), headers={"Content-Type": "text/plain"})
        if path == "/api/v1/observation/value":
            point = json.loads(request.content)
            assert point == {"sourceId": 42, "slice": {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0},
                             "curve": "D2_YX", "geometry": None, "semantics": None, "offset": 0, "rate": None}
            return httpx.Response(200, text="123.25")
        if path == "/releaseContext":
            assert request.headers["klab-scope"] == "session.context"
            return httpx.Response(200, json=True)
        if path == "/releaseSession":
            assert request.headers["klab-scope"] == "session"
            return httpx.Response(200, json=True)
        raise AssertionError(path)

    with make_client(handler) as client:
        session = client.create_session(name="test")
        context = session.create_context()
        observable = client.reasoner.resolve_observable("geography:Elevation in m")
        focused = context.within(8)
        job = focused.submit(ObservationImpl(urn="", observable=observable))
        result = job.result(timeout=1)
        assert result.id == 42 and result.units == "m"
        assert result.metadata["im:commit"] == 7
        assert result.get_value() is None
        data = result.fetch_data([0], curve="D2_YX")
        assert data.values == (Decimal("123.25"),)
        assert data.units == "m"
        assert focused.release() and session.release()
    assert len(calls) == 10  # close added no remote operation


@pytest.mark.parametrize("code,error", [(401, AuthenticationError), (403, AuthorizationError), (400, InvalidRequestError)])
def test_errors_redact_credentials(code, error):
    with make_client(lambda r: httpx.Response(code, text="runtime-secret reasoner-secret")) as client:
        with pytest.raises(error) as caught:
            client.runtime.capabilities()
        assert "secret" not in str(caught.value)


@pytest.mark.parametrize("payload", [{}, {"status": None}, {"status": "SUCCEEDED"}, []])
def test_malformed_status(payload):
    with make_client(lambda r: httpx.Response(200, json=payload)) as client:
        with pytest.raises(ProtocolError):
            Context(client, {"id": "s.c"}).job(1).status()


@pytest.mark.parametrize("state,error", [("ABORTED", JobFailedError), ("INTERRUPTED", JobCancelledError), ("EMPTY", JobUnavailableError)])
def test_job_terminal_states(state, error):
    with make_client(lambda r: httpx.Response(200, json={"status": state, "stackTrace": "reasoner-secret diagnostic"})) as client:
        with pytest.raises(error) as caught:
            Context(client, {"id": "s.c"}).job(2).result(1)
        assert caught.value.job_id == 2
        assert "secret" not in str(caught.value)


def test_timeout_preserves_job_and_can_resume(observation):
    finished = False
    def handler(request):
        if "status" in request.url.path:
            return httpx.Response(200, json={"status": "FINISHED" if finished else "WAITING"})
        return httpx.Response(200, json=observation)
    with make_client(handler) as client:
        job = Context(client, {"id": "s.c"}).job(1)
        with pytest.raises(WaitTimeout) as caught:
            job.result(.005)
        assert caught.value.job is job
        finished = True
        assert job.result(1).id == 42


@pytest.mark.parametrize("accepted,state", [(True, "INTERRUPTED"), (False, "FINISHED"), (True, "FINISHED")])
def test_cancellation_races(observation, accepted, state):
    def handler(request):
        if "cancel" in request.url.path:
            return httpx.Response(200, json=accepted)
        if "status" in request.url.path:
            return httpx.Response(200, json={"status": state})
        return httpx.Response(200, json=observation)
    with make_client(handler) as client:
        job = Context(client, {"id": "s.c"}).job(1)
        assert job.cancel() is accepted
        assert job.cancellation_requested is accepted
        if state == "INTERRUPTED":
            with pytest.raises(JobCancelledError):
                job.result(1)
        else:
            assert job.result(1).id == 42


def test_ambiguous_submission_is_not_retried(observation):
    calls = []
    def handler(request):
        calls.append(request)
        raise httpx.ReadTimeout("runtime-secret", request=request)
    with make_client(handler) as client:
        with pytest.raises(SubmissionOutcomeUnknown) as caught:
            Context(client, {"id": "s.c"}).submit(ObservationImpl(
                urn="", observable=observable_from_wire(observation["observable"])))
        assert "secret" not in str(caught.value)
    assert len(calls) == 1


def test_attach_and_local_close_do_not_release():
    paths = []
    def handler(request):
        paths.append(request.url.path)
        assert json.loads(request.content)["configuration"]["id"] == "s.c"
        return httpx.Response(200, json={"id": "s.c", "notifications": []})
    with make_client(handler) as client:
        context = client.attach_context("s.c")
        assert not context.owned
    assert paths == ["/api/v1/connect"]


def test_redirect_never_forwards_credential():
    requests = []
    def handler(request):
        requests.append(request)
        return httpx.Response(302, headers={"Location": "https://other.invalid"})
    with make_client(handler) as client:
        with pytest.raises(ServerError):
            client.runtime.capabilities()
    assert len(requests) == 1


@pytest.mark.parametrize("mutate", [lambda p: p.pop("observable"), lambda p: p.update(id="42"),
                                    lambda p: p["geometry"].update(dimensions="bad")])
def test_incompatible_observation(observation, mutate):
    mutate(observation)
    with pytest.raises(ProtocolError):
        observation_from_wire(observation)


def test_finished_does_not_hide_server_error_notification(observation):
    observation["notifications"] = [{"level": "ERROR", "message": "runtime-secret missing model"}]
    def handler(request):
        return httpx.Response(200, json={"status": "FINISHED"} if "status" in request.url.path else observation)
    with make_client(handler) as client:
        with pytest.raises(JobFailedError, match="missing model") as caught:
            Context(client, {"id": "s.c"}).job(1).result(1)
        assert "secret" not in str(caught.value)


@pytest.mark.parametrize("text,kind,expected", [("null", "NUMBER", None), ("0", "NUMBER", 0),
    ("9007199254740993", "NUMBER", 9007199254740993), ("false", "BOOLEAN", False),
    ("geography:Forest", "CONCEPT", "geography:Forest"), ("1.25", "NUMBER", Decimal("1.25"))])
def test_scientific_cell_decoding(text, kind, expected):
    assert decode_cell(text, kind) == expected


def test_native_metadata_and_unit_mediation_request(observation):
    mm_observable = deepcopy(observation["observable"])
    mm_observable["urn"] = "geography:Elevation in mm"
    mm_observable["unit"]["definition"] = "mm"
    semantics = storage_semantics(observable_from_wire(mm_observable))
    def handler(request):
        point = json.loads(request.content)
        assert point["semantics"] == {"observable": "geography:Elevation in mm", "unit": "mm",
            "range": "", "currency": "", "contextualDimensions": "{}", "meaning": "geography:Elevation|null|"}
        return httpx.Response(200, text="1250")
    with make_client(handler) as client:
        result = observation_from_wire(observation, Context(client, {"id": "s.c"}))
        result.raw["futureField"] = "kept"
        assert result.fetch_data([0], semantics=semantics).values == (1250,)


def test_explicit_unsupported_and_unconfigured_methods():
    with pytest.raises(ConfigurationError):
        ReasonerImpl().resolve_concept("im:Thing")
    with make_client(lambda r: pytest.fail("Must not make a request")) as client:
        with pytest.raises(UnsupportedOperationError):
            client.resources.contextualize(None, None, None, None)
        with pytest.raises(UnsupportedOperationError):
            client.resolver.encode_dataflow(None)
        with pytest.raises(UnsupportedOperationError):
            GeometryImpl().encode(object())
        with pytest.raises(UnsupportedOperationError):
            client.runtime.query_knowledge_graph("query", None)


def test_scope_and_secret_configuration():
    assert scope_token("s.c.42#9") == "s.c.42#9"
    with pytest.raises(InvalidRequestError):
        scope_token("s.c.bad")
    with pytest.raises(ConfigurationError):
        Endpoint("https://user:secret@runtime.invalid")
    assert "secret" not in repr(Endpoint("https://runtime.invalid", "secret"))


def test_discovery_and_resolver_contracts(observation):
    seen = []
    def handler(request):
        path = request.url.path
        seen.append(path)
        if path == "/public/capabilities":
            return httpx.Response(200, json={"serviceId": "server-id", "type": "RUNTIME", "future": 7})
        if path == "/api/v1/resolve/concept":
            assert request.content == b"geography:Elevation"
            return httpx.Response(200, json=observation["observable"]["semantics"])
        if path == "/api/v1/list/RESOURCE":
            return httpx.Response(200, json=[{"urn": "asset:urn", "adapterType": "raster"}])
        if path.startswith("/api/v1/retrieve/RESOURCE/"):
            return httpx.Response(200, json={"urn": "asset:urn", "adapterType": "raster"})
        if path.startswith("/api/v1/resolve/RESOURCE/"):
            return httpx.Response(200, json={"results": [{"resourceUrn": "asset:urn", "serviceId": "resources-id"}],
                                           "services": {"resources-id": "https://untrusted.invalid"}})
        if path == "/api/v1/contexts":
            return httpx.Response(200, json=[{"configuration": {"id": "s.c"}, "observationCount": 1}])
        if path == "/api/v1/query":
            assert json.loads(request.content)["resultType"] == "OBSERVATION"
            return httpx.Response(200, json=[observation])
        assert request.url.host == "resolver.invalid"
        assert request.headers["Authorization"] == "resolver-secret"
        assert request.headers["klab-service"] == "runtime-id"
        assert request.headers["klab-scope"] == "s.c"
        if path == "/api/v1/resolve":
            assert json.loads(request.content)["observation"]["observable"] == observation["observable"]
            return httpx.Response(200, json=3)
        if path == "/jobs/status/3":
            return httpx.Response(200, json={"status": "FINISHED"})
        if path == "/jobs/retrieve/3":
            return httpx.Response(200, json={"resolutionOutcome": "RESOLVED", "actuators": []})
        if path == "/api/v1/resource":
            return httpx.Response(200, json={"urn": "asset:contextual"})
        if path == "/api/v1/resources":
            return httpx.Response(200, json=[{"urn": "asset:contextual"}])
        pytest.fail(path)

    with make_client(handler) as client:
        for service in (client.runtime, client.reasoner, client.resources, client.resolver):
            assert service.capabilities().raw["future"] == 7
        assert client.reasoner.resolve_concept("geography:Elevation").get_type()
        assert client.resources.list()[0].raw["adapterType"] == "raster"
        assert client.resources.retrieve("asset:urn").urn == "asset:urn"
        assert client.resources.resolve("asset:urn").urns == ["asset:urn"]
        assert client.runtime.get_context_info()[0].raw["observationCount"] == 1
        context = Context(client, {"id": "s.c"})
        assert client.runtime.query_knowledge_graph({"resultType": "OBSERVATION"}, context)[0]["id"] == 42
        local = ObservationImpl(urn="", observable=observable_from_wire(observation["observable"]))
        assert client.resolver.resolve(local, context).result(1)["resolutionOutcome"] == "RESOLVED"
        assert client.resolver.submit_resource(local, context)["urn"] == "asset:contextual"
        assert client.resolver.get_submitted_resources(context)[0]["urn"] == "asset:contextual"
    assert len(seen) == 15


@pytest.mark.parametrize("response", [httpx.Response(200, text="not JSON"),
    httpx.Response(200, json={"id": "s.c", "notifications": [{"message": "no valid session", "level": "ERROR"}]})])
def test_malformed_and_negative_context(response):
    with make_client(lambda r: response) as client:
        with pytest.raises((ProtocolError, ServerError)):
            client.attach_context("s.c")


def test_application_empty_job_result(observation):
    observation["empty"] = True
    with make_client(lambda r: httpx.Response(200, json={"status": "FINISHED"} if "status" in r.url.path else observation)) as client:
        with pytest.raises(JobFailedError, match="empty"):
            Context(client, {"id": "s.c"}).job(1).result(1)


def test_explicit_temporal_slice_and_native_missingness(observation):
    observation["geometry"]["dimensions"] = [{"type": "TIME", "shape": [2]}]
    def handler(request):
        assert json.loads(request.content)["slice"] == {"type": "TEMPORAL_TRANSITION", "key": "1000-2000", "start": 1000, "end": 2000}
        return httpx.Response(200, text="null")
    with make_client(handler) as client:
        result = observation_from_wire(observation, Context(client, {"id": "s.c"}))
        with pytest.raises(InvalidRequestError, match="Temporal"):
            result.fetch_data([0])
        assert result.fetch_data([0], slice={"type": "TEMPORAL_TRANSITION", "key": "1000-2000", "start": 1000, "end": 2000}).values == (None,)


def test_submission_5xx_is_ambiguous_and_not_retried(observation):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(502, text="runtime-secret gateway failure")
    with make_client(handler) as client:
        with pytest.raises(SubmissionOutcomeUnknown) as caught:
            Context(client, {"id": "s.c"}).submit(ObservationImpl(
                urn="", observable=observable_from_wire(observation["observable"])))
        assert "secret" not in str(caught.value)
    assert len(calls) == 1
