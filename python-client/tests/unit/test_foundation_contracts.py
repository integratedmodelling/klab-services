"""Public foundation success/error contracts retained during the branch-wide polish pass."""
import json
from pathlib import Path
from types import SimpleNamespace

import httpx
import pytest

from klab_client import Client, Context, ObservationImpl, UserScopeImpl
from klab_client.client import scope_token
from klab_client.dto import observation_from_wire
from klab_client.errors import (ConfigurationError, InvalidRequestError, ProtocolError,
    TransportError, UnsupportedOperationError)


def wire():
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["urn"] = "s.c.42"
    return payload


def client(handler=None, **kwargs):
    return Client("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
                  http_transport=httpx.MockTransport(handler or (lambda r: pytest.fail("Invalid input reached HTTP"))), **kwargs)


def asset(current):
    return observation_from_wire(wire(), Context(current, {"id": "s.c", "serviceId": "home"}))


def test_user_scope_is_not_a_context_header_and_explicit_scope_objects_are_validated():
    assert scope_token(UserScopeImpl("scientist")) is None
    assert scope_token(SimpleNamespace(id="s.c.4#9")) == "s.c.4#9"
    for value in (object(), SimpleNamespace(id=None), SimpleNamespace(id="s.c.bad")):
        with pytest.raises(InvalidRequestError):
            scope_token(value)


@pytest.mark.parametrize("id", [0, -1, True, "7"])
def test_invalid_job_or_focus_id_cannot_reach_transport(id):
    with client() as current:
        context = Context(current, {"id": "s.c"})
        with pytest.raises(ProtocolError):
            context.job(id)
        with pytest.raises(InvalidRequestError):
            context.within(id)


@pytest.mark.parametrize("id", ["s", "s.c.7", "s.", "s.c bad"])
def test_invalid_context_and_attach_root_ids_reject_before_http(id):
    with client() as current:
        with pytest.raises(ProtocolError):
            Context(current, {"id": id})
        with pytest.raises(InvalidRequestError):
            current.attach_context(id)


@pytest.mark.parametrize("trace", [[], 7])
def test_job_status_rejects_malformed_trace_without_guessing_success(trace):
    with client(lambda r: httpx.Response(200, json={"status": "ABORTED", "stackTrace": trace})) as current:
        with pytest.raises(ProtocolError):
            Context(current, {"id": "s.c"}).job(1).status()


@pytest.mark.parametrize("ack", ["true", 1, None])
def test_cancellation_requires_a_boolean_acknowledgement(ack):
    with client(lambda r: httpx.Response(200, json=ack)) as current:
        job = Context(current, {"id": "s.c"}).job(1)
        with pytest.raises(ProtocolError):
            job.cancel()
        assert not job.cancellation_requested


@pytest.mark.parametrize("timeout", [-1, float("inf"), float("nan")])
def test_invalid_wait_budgets_do_not_poll(timeout):
    with client() as current:
        with pytest.raises(InvalidRequestError):
            Context(current, {"id": "s.c"}).job(1).result(timeout)


def test_default_wait_has_no_deadline_and_returns_the_original_result():
    calls = []
    def handler(request):
        calls.append(request.url.path)
        return httpx.Response(200, json={"status": "FINISHED"} if "/status/" in request.url.path else wire())
    with client(handler) as current:
        job = Context(current, {"id": "s.c"}).job(1)
        assert "id=1" in repr(job) and "issued-secret" not in repr(job)
        result = job.result()
        assert result.id == 42 and result._context is job.scope
    assert calls == ["/runtime/jobs/status/1", "/runtime/jobs/retrieve/1"]


@pytest.mark.parametrize("timeout", [None, 30])
def test_transport_failure_before_wait_expiry_does_not_become_wait_timeout(timeout):
    calls = []
    def handler(request):
        calls.append(request)
        raise httpx.ConnectError("private diagnostics", request=request)
    with client(handler) as current:
        with pytest.raises(TransportError) as caught:
            Context(current, {"id": "s.c"}).job(1).result(timeout)
        assert "private" not in str(caught.value)
    assert len(calls) == 1


@pytest.mark.parametrize("changes,error", [({"id": 0}, UnsupportedOperationError),
    ({"id": -1}, ProtocolError), ({"id": "42"}, ProtocolError),
    ({"metadata": {"im:storage-binding-source": 41}}, UnsupportedOperationError)])
def test_sparse_reads_do_not_invent_native_bindings(changes, error):
    with client() as current:
        observation = asset(current)
        for key, value in changes.items():
            setattr(observation, key, value)
        with pytest.raises(error):
            observation.fetch_data([0])


@pytest.mark.parametrize("kwargs", [{"curve": "INVENTED"}, {"slice": {}}, {"slice": []},
    {"slice": {"type": "EVENT", "key": "event", "start": True, "end": 1}},
    {"semantics": []}, {"semantics": {"observable": "elevation", "unit": 7}}])
def test_malformed_read_selectors_reject_before_http(kwargs):
    with client() as current:
        with pytest.raises(InvalidRequestError):
            asset(current).fetch_data([0], **kwargs)


def test_temporal_reads_require_explicit_selection():
    with client() as current:
        observation = asset(current)
        observation.geometry.raw["dimensions"] = [{"type": "TIME", "shape": [2]}]
        with pytest.raises(InvalidRequestError, match="Temporal"):
            observation.fetch_data([0])


def test_environment_configuration_is_explicit_and_does_no_network(monkeypatch):
    import os
    for name in list(os.environ):
        if name.startswith("KLAB_"):
            monkeypatch.delenv(name)
    with pytest.raises(ConfigurationError, match="KLAB_RUNTIME_URL"):
        Client.from_env()
    monkeypatch.setenv("KLAB_RUNTIME_URL", "https://runtime.invalid/runtime")
    monkeypatch.setenv("KLAB_TIMEOUT", "not-a-number")
    with pytest.raises(ConfigurationError, match="numbers"):
        Client.from_env()
    for name, value in {"KLAB_TIMEOUT": "12", "KLAB_POLL_INTERVAL": "0.25", "KLAB_AGENT_NAME": "scientist",
        "KLAB_RUNTIME_TOKEN": "runtime-secret", "KLAB_REASONER_URL": "https://reasoner.invalid/reasoner",
        "KLAB_REASONER_TOKEN": "reasoner-secret", "KLAB_SERVICE_IDS": "home,reasoner", "KLAB_RUNTIME_SERVICE_ID": "home"}.items():
        monkeypatch.setenv(name, value)
    with Client.from_env() as current:
        assert current.agent_name == "scientist" and current.runtime_service_id == "home"
        assert current.service_ids == ("home", "reasoner")
        assert current.transport.timeout == 12 and current.transport.poll_interval == .25
        assert current.transport.endpoint("reasoner").credential == "reasoner-secret"


def test_peer_setup_checks_runtime_identity_and_acknowledgement():
    def response(request, identity="home", ack=False):
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": identity})
        if request.url.path.endswith("/status"):
            return httpx.Response(200, json={"available": True, "operational": True})
        return httpx.Response(200, json=ack)
    with client(lambda r: response(r, identity="different-home")) as current:
        with pytest.raises(ConfigurationError, match="home runtime"):
            current.initialize_user_scope()
    with client(response) as current:
        with pytest.raises(ProtocolError, match="not accepted"):
            current.initialize_user_scope()


@pytest.mark.parametrize("id", ["", "s.c", "s#invalid", "has space"])
def test_create_session_does_not_accept_a_malformed_server_id(id):
    with client(lambda r: httpx.Response(200, text=id)) as current:
        with pytest.raises(ProtocolError):
            current.create_session()


def test_empty_context_and_missing_provenance_agent_do_not_become_success():
    with client(lambda r: httpx.Response(200, json={"id": "s.c", "empty": True})) as current:
        with pytest.raises(ProtocolError, match="empty"):
            current.attach_context("s.c")
        with pytest.raises(ConfigurationError, match="agent_name"):
            Context(current, {"id": "s.c"}).submit(ObservationImpl("", asset(current).observable))


def test_raw_scoped_submission_retains_its_context_and_does_not_acquire_ownership():
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["klab-scope"] == "s.c.7#9"
        assert request.headers["klab-service"] == "home"
        return httpx.Response(200, json=5)
    with client(handler, agent_name="scientist") as current:
        job = current.runtime.submit(ObservationImpl("", asset(current).observable), "s.c.7#9")
        assert job.id == 5 and job.scope.id == "s.c" and not job.scope.owned
        assert job.scope.get_context_id() == "s.c.7#9"
    assert len(calls) == 1
