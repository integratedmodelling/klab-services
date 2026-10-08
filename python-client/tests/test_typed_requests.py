"""Behavioral contracts for the typed facade, using actual HTTP codecs."""
from dataclasses import FrozenInstanceError
import json
from pathlib import Path

import httpx
import pytest

from klab_client import (Client, Context, ContextOptions, Endpoint, GridOptions,
                         ObservationOptions, ObservationRequest, RectangularGeometry,
                         ResolutionConstraint, GeometryImpl, ObservationImpl, Session)
from klab_client.dto import observable_from_wire, observation_from_wire, observation_to_wire
from klab_client.errors import (InvalidRequestError, UnsupportedOperationError, ConfigurationError,
    AuthenticationError, AuthorizationError, MissingAssetError, ProtocolError, ServerError,
    SubmissionOutcomeUnknown, JobFailedError, JobCancelledError, JobUnavailableError, WaitTimeout)


@pytest.fixture
def wire():
    payload = json.loads((Path(__file__).parent / "fixtures/elevation.json").read_text())
    payload["observable"]["contextualization"] = "MEASURE"
    return payload


def client(handler, **kwargs):
    return Client("https://runtime.invalid/runtime", "secret", agent_name="scientist",
                  runtime_service_id="runtime-id", reasoner=Endpoint("https://reasoner.invalid/reasoner", "secret"),
                  http_transport=httpx.MockTransport(handler), **kwargs)


def test_observe_resolved_preserves_focused_observer_scope_and_constraints(wire):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.url.path == "/runtime/api/v1/submit"
        assert request.headers["klab-scope"] == "s.c.8.9#3"
        assert request.headers["klab-service"] == "runtime-id"
        posted = json.loads(request.content)
        assert posted["agentName"] == "scientist"
        assert posted["observation"]["observable"] == wire["observable"]
        assert {c["type"]: c["data"] for c in posted["resolutionConstraints"]} == {
            "ResolutionNamespace": ["python.fixture"], "ResolutionProject": ["fixture"], "Scenarios": ["scenario"]}
        assert all(c["@CLASS"].endswith("ResolutionConstraintImpl") for c in posted["resolutionConstraints"])
        return httpx.Response(200, json=4)
    with client(handler) as current:
        context = Context(current, {"id": "s.c"}, token="s.c.8.9#3")
        request = ObservationRequest(observable_from_wire(wire["observable"]),
            options=ObservationOptions(namespace="python.fixture", project="fixture", scenarios=["scenario"]))
        job = context.observe(request)
        assert job.id == 4 and job.scope.get_context_id() == "s.c.8.9#3"
    assert len(calls) == 1  # close made no remote mutation


def test_typed_objects_are_defensive_and_wire_encoding_repeatable(wire):
    metadata = {"nested": [1, 2]}
    observable = observable_from_wire(wire["observable"])
    request = ObservationRequest(observable, metadata=metadata, extensions={"futureField": {"v": 1}})
    first = request.to_wire(observable)
    metadata["nested"].append(3)
    observable.raw["urn"] = "mutated"
    first["metadata"]["delegate"]["nested"].append(4)
    assert request.to_wire()["metadata"]["delegate"]["nested"] == [1, 2]
    assert request.to_wire()["observable"]["urn"] != "mutated"
    with pytest.raises(FrozenInstanceError):
        request.urn = "changed"


def test_context_options_encode_grid_only_at_context_creation():
    options = ContextOptions(name="grid", persistence="EXPLICIT_ACTION",
        grid=GridOptions(anchor_x=200000, anchor_y=6000000, span=100, projection="EPSG:3857"))
    assert options.to_wire()["gridDefinition"]["span"] == 100
    assert options.to_wire()["persistence"] == "EXPLICIT_ACTION"


@pytest.mark.parametrize("kind", ["UsingModel", "Whitelist", "BlackList", "Geometry", "Unknown"])
def test_unsupported_constraints_never_silently_degrade(kind):
    with pytest.raises(UnsupportedOperationError):
        ResolutionConstraint(kind, ["asset"])


def test_selected_model_is_explicitly_unsupported():
    with pytest.raises(UnsupportedOperationError, match="model"):
        ObservationOptions(selected_model="model:ignored")


def test_reserved_extensions_are_rejected_before_http(wire):
    with pytest.raises(InvalidRequestError):
        ObservationRequest(observable_from_wire(wire["observable"]), extensions={"id": 99})


@pytest.mark.parametrize("kind,values", [
    ("Scenarios", ["one", "two"]), ("ResolutionProject", ["project"]),
    ("ResolutionNamespace", ["namespace"]), ("Observer", [9])])
def test_each_constraint_is_defensive(kind, values):
    source = list(values)
    constraint = ResolutionConstraint(kind, source)
    source.append("changed")
    assert constraint.to_wire()["data"] == values
    constraint.to_wire()["data"].clear()
    assert constraint.values == tuple(values)


@pytest.mark.parametrize("kind,values", [
    ("Scenarios", "text"), ("Scenarios", []), ("Scenarios", [" "]),
    ("Scenarios", ["one", "one"]), ("ResolutionProject", ["a", "b"]),
    ("ResolutionNamespace", [1]), ("Observer", [True]), ("Observer", [0]),
    ("Observer", [-1]), ("Observer", [2**63]), ("Observer", [1.0])])
def test_invalid_constraints(kind, values):
    with pytest.raises(InvalidRequestError):
        ResolutionConstraint(kind, values)


@pytest.mark.parametrize("changes", [
    {"scenarios": "bad"}, {"constraints": "bad"}, {"constraints": ["bad"]},
    {"namespace": ""}, {"project": 9}, {"scenarios": [True]}, {"observer": True},
    {"namespace": "a", "constraints": [ResolutionConstraint("ResolutionNamespace", ["b"])]},
    {"observer": 1, "constraints": [ResolutionConstraint("Observer", [2])]}])
def test_invalid_options(changes):
    with pytest.raises(InvalidRequestError):
        ObservationOptions(**changes)


def test_observer_object_is_bound_defensive_and_never_foreign(wire):
    with client(lambda r: httpx.Response(200, json=7)) as current:
        context = Context(current, {"id": "s.c"}, token="s.c.8")
        observer = observation_from_wire(wire, context)
        options = ObservationOptions(observer=observer)
        observer.id = 99
        assert options.observer == 42
        job = context.observe(observable_from_wire(wire["observable"]), options=options)
        assert job.scope.get_context_id() == "s.c.8#42"
        same = context.observe(observable_from_wire(wire["observable"]),
            options=ObservationOptions(constraints=[ResolutionConstraint("Observer", [42])]))
        assert same.scope.get_context_id() == "s.c.8#42"
        for foreign in (Context(current, {"id": "s.other"}),):
            with pytest.raises(InvalidRequestError, match="different"):
                foreign.observe(observable_from_wire(wire["observable"]), options=options)
        with Client("https://other.invalid") as foreign_client:
            with pytest.raises(InvalidRequestError, match="different"):
                Context(foreign_client, {"id": "s.c"}).observe("definition", options=options)
        with pytest.raises(InvalidRequestError, match="conflicts"):
            Context(current, {"id": "s.c"}, token="s.c#9").observe("definition", options=options)
        matched = Context(current, {"id": "s.c"}, token="s.c#42")
        assert matched.observe(observable_from_wire(wire["observable"]), options=options).scope.get_context_id() == "s.c#42"
    observer._context = None
    with pytest.raises(InvalidRequestError, match="bound"):
        ObservationOptions(observer=observer)


@pytest.mark.parametrize("label,value", [
    ("west", True), ("east", float("inf")), ("north", float("nan")),
    ("south", "0"), ("west", 10**400), ("columns", True), ("rows", 0)])
def test_rectangle_invalid_numbers(label, value):
    arguments = dict(west=0, east=500, south=0, north=400, columns=5, rows=4)
    arguments[label] = value
    with pytest.raises(InvalidRequestError):
        RectangularGeometry(**arguments)


def test_rectangle_bounds_and_square_cell_contract():
    with pytest.raises(InvalidRequestError):
        RectangularGeometry(0, 0, 0, 400, 5, 4)
    with pytest.raises(InvalidRequestError):
        RectangularGeometry(0, 500, 1, 0, 5, 4)
    with pytest.raises(UnsupportedOperationError):
        RectangularGeometry(0, 501, 0, 400, 5, 4)
    geometry = RectangularGeometry(0, 500, 0, 400, 5, 4).to_geometry()
    assert geometry.shape == (5, 4)
    assert geometry.raw["dimensions"][0]["parameters"]["delegate"]["sgrid"] == "100.0 m"


@pytest.mark.parametrize("values", [
    (-1e308, 1e308, 0, 400, 5, 4),
    (0, 500, -1e308, 1e308, 5, 4),
    (0, 500, 0, 400, 2**62, 4),
    (0, 5e-324, 0, 400, 5, 4),
    (0, 500, 0, 5e-324, 5, 4)])
def test_rectangle_derived_extents_and_cell_sizes_are_finite(values):
    with pytest.raises(InvalidRequestError):
        RectangularGeometry(*values)


@pytest.mark.parametrize("changes", [{"span": 0}, {"span": -1}, {"span": float("inf")},
    {"anchor_y": False}, {"projection": ""}, {"strict": 0}, {"snap": "yes"}])
def test_invalid_grid(changes):
    arguments = dict(anchor_x=0, anchor_y=0, span=100)
    arguments.update(changes)
    with pytest.raises(InvalidRequestError):
        GridOptions(**arguments)


@pytest.mark.parametrize("changes", [{"name": " "}, {"persistence": "unknown"}, {"persistence": {}},
    {"grid": 99}, {"grid": ""}, {"extensions": {"owner": "other"}}, {"extensions": [1]}])
def test_invalid_context_options(changes):
    with pytest.raises(InvalidRequestError):
        ContextOptions(**changes)


def test_context_creation_facade_and_raw_compatibility():
    calls = []
    def handler(request):
        calls.append(json.loads(request.content))
        assert request.url.path == "/runtime/createContext"
        assert request.headers["klab-scope"] == "s"
        return httpx.Response(200, json={"id": "s.c"})
    with client(handler) as current:
        session = Session(current, "s")
        assert session.create_context(options=ContextOptions()).owned
        assert session.create_context(options=ContextOptions(grid="namespace.grid", extensions={"future": [1]})).owned
        assert calls[1]["configuration"]["gridUrn"] == "namespace.grid"
        assert calls[1]["configuration"]["future"] == [1]
        for arguments in ({"options": "bad"}, {"options": ContextOptions(), "configuration": {}},
                          {"options": ContextOptions(), "name": "different"}):
            with pytest.raises(InvalidRequestError):
                session.create_context(**arguments)
    assert len(calls) == 2
    with pytest.raises(UnsupportedOperationError):
        ContextOptions(observer=1)


@pytest.mark.parametrize("changes", [
    {"options": {}}, {"urn": 1}, {"name": " "}, {"observable": " "},
    {"observable": None}, {"geometry": {}}, {"metadata": {1: "bad"}},
    {"metadata": []}, {"extensions": {"observable": {}}},
    {"metadata": {"value": float("nan")}}, {"metadata": {"value": object()}}])
def test_invalid_request_structure(wire, changes):
    arguments = {"observable": observable_from_wire(wire["observable"])}
    arguments.update(changes)
    with pytest.raises(InvalidRequestError):
        ObservationRequest(**arguments)


def test_circular_json_is_rejected(wire):
    recursive = {}
    recursive["loop"] = recursive
    with pytest.raises(InvalidRequestError, match="JSON"):
        ObservationRequest(observable_from_wire(wire["observable"]), metadata=recursive)


@pytest.mark.parametrize("activity", [None, "VOID", "FUTURE_ACTIVITY", {}, True])
def test_unknown_activities_cannot_be_invented(wire, activity):
    wire["observable"]["contextualization"] = activity
    observable = observable_from_wire({**wire["observable"], "contextualization": None})
    observable.raw["contextualization"] = activity
    with pytest.raises(UnsupportedOperationError):
        ObservationRequest(observable)


def test_error_notifications_and_locally_invented_semantics_are_rejected(wire):
    from klab_client import ConceptImpl, ObservableImpl
    with pytest.raises(UnsupportedOperationError):
        ObservationRequest(ObservableImpl(ConceptImpl("local")))
    for parent in (wire["observable"], wire["observable"]["semantics"]):
        parent["notifications"] = [{"level": "ERROR", "message": "secret denied"}]
        with pytest.raises(ServerError) as error:
            ObservationRequest(observable_from_wire(wire["observable"]))
        assert "secret" not in str(error.value)
        parent["notifications"] = []


def test_checked_geometry_snapshot_and_old_new_payload_equivalence(wire):
    observable = observable_from_wire(wire["observable"])
    geometry = RectangularGeometry(0, 500, 0, 400, 5, 4).to_geometry()
    request = ObservationRequest(observable, urn="", name="test", geometry=geometry)
    old = ObservationImpl(urn="", observable=observable, name="test", geometry=geometry)
    assert request.to_wire() == observation_to_wire(old)
    geometry.raw["dimensions"].clear()
    assert request.to_wire()["geometry"]["dimensions"]
    rectangular = ObservationRequest(observable, geometry=RectangularGeometry(0, 500, 0, 400, 5, 4))
    assert rectangular.to_wire()["geometry"]["dimensions"][0]["shape"] == [5, 4]


def test_string_resolution_is_explicit_and_preserves_extensions(wire):
    calls = []
    def handler(request):
        calls.append(request.url.path)
        if request.url.path.endswith("/resolve/observable"):
            assert request.content == b"geography:Elevation in m"
            return httpx.Response(200, json=wire["observable"])
        assert json.loads(request.content)["observation"].get("future") in ({"v": None}, None)
        return httpx.Response(200, json=8)
    request = ObservationRequest("geography:Elevation in m", extensions={"future": {"v": None}})
    with pytest.raises(InvalidRequestError, match="Resolve"):
        request.to_wire()
    with client(handler) as current:
        assert Context(current, {"id": "s.c"}).observe(request).id == 8
        assert Context(current, {"id": "s.c"}).observe("geography:Elevation in m").id == 8
    assert calls == ["/reasoner/api/v1/resolve/observable", "/runtime/api/v1/submit"] * 2


def test_validation_and_unconfigured_reasoner_prevent_mutation(wire):
    with Client("https://runtime.invalid", agent_name="scientist",
                http_transport=httpx.MockTransport(lambda r: pytest.fail("No HTTP allowed"))) as current:
        context = Context(current, {"id": "s.c"})
        with pytest.raises(ConfigurationError, match="reasoner"):
            context.observe("definition")
        with pytest.raises(InvalidRequestError, match="already"):
            context.observe(ObservationRequest(observable_from_wire(wire["observable"])), options=ObservationOptions())
        with pytest.raises(InvalidRequestError, match="root"):
            Context(current, {"id": "s.c"}, token="s.other").observe("definition")
    with pytest.raises(TypeError):
        ObservationOptions(unrecognized=True)


@pytest.mark.parametrize("code,error", [(401, AuthenticationError), (403, AuthorizationError),
    (404, MissingAssetError), (503, SubmissionOutcomeUnknown)])
def test_submit_errors_are_classified_and_never_retried(wire, code, error):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(code, text="secret")
    with client(handler) as current:
        with pytest.raises(error) as caught:
            Context(current, {"id": "s.c"}).observe(observable_from_wire(wire["observable"]))
        assert "secret" not in str(caught.value)
    assert len(calls) == 1


def test_lost_submission_outcome_and_resolution_failure(wire):
    calls = []
    def lost(request):
        calls.append(request.url.path)
        raise httpx.ReadTimeout("secret", request=request)
    with client(lost) as current:
        with pytest.raises(SubmissionOutcomeUnknown):
            Context(current, {"id": "s.c"}).observe(observable_from_wire(wire["observable"]))
    assert len(calls) == 1
    calls.clear()
    def unresolved(request):
        calls.append(request.url.path)
        return httpx.Response(200, json={**wire["observable"], "notifications": [{"level": "ERROR", "message": "secret"}]})
    with client(unresolved) as current:
        with pytest.raises(ServerError):
            Context(current, {"id": "s.c"}).observe("definition")
    assert calls == ["/reasoner/api/v1/resolve/observable"]


@pytest.mark.parametrize("state,error", [("ABORTED", JobFailedError), ("INTERRUPTED", JobCancelledError),
    ("EMPTY", JobUnavailableError), ("UNKNOWN", ProtocolError)])
def test_typed_facade_preserves_job_terminal_states(wire, state, error):
    def handler(request):
        return httpx.Response(200, json=5 if request.url.path.endswith("/submit") else {"status": state})
    with client(handler) as current:
        job = Context(current, {"id": "s.c"}).observe(observable_from_wire(wire["observable"]))
        with pytest.raises(error):
            job.result(1)


def test_typed_wait_timeout_keeps_handle_and_can_resume(wire, monkeypatch):
    now = [0.0]
    monkeypatch.setattr("time.monotonic", lambda: now[0])
    monkeypatch.setattr("time.sleep", lambda duration: now.__setitem__(0, now[0] + duration))
    finished = [False]
    def handler(request):
        if request.url.path.endswith("/submit"):
            return httpx.Response(200, json=5)
        if "/status/" in request.url.path:
            return httpx.Response(200, json={"status": "FINISHED" if finished[0] else "WAITING"})
        return httpx.Response(200, json=wire)
    with client(handler, poll_interval=.1) as current:
        job = Context(current, {"id": "s.c"}).observe(observable_from_wire(wire["observable"]))
        with pytest.raises(WaitTimeout) as stopped:
            job.result(.2)
        assert stopped.value.job is job
        finished[0] = True
        assert job.result(1).id == 42
