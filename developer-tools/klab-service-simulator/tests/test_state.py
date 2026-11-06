"""Finite pure protocol/state tests; no timer or sleeps determine correctness."""
from copy import deepcopy
import json

import pytest

from klab_client import RectangularGeometry
from klab_simulator.server import make_server
from klab_simulator.state import State, observable


def call(state, method, route, data=None, *, owner="alice", scope="", service="runtime"):
    headers = {"Authorization": "sim-token-" + owner, "Content-Type": "application/json",
               "klab-scope": scope, "klab-service": state.service_ids["runtime"]}
    return state.request(method, "/" + service + route, headers,
                         json.dumps(data).encode() if data is not None else b"")[:2]


def setup(state):
    assert call(state, "POST", "/createSession", {"configuration": {"id": "s1"}}) == (200, "s1")
    status, config = call(state, "POST", "/createContext", {}, scope="s1")
    assert status == 200
    return config["id"]


def proposal():
    return {"observation": {"observable": observable(),
                            "geometry": RectangularGeometry(0, 2000, 0, 2000, 20, 20).to_geometry().to_wire()}}


def test_clockless_states_materialization_cancellation_and_no_idempotency_invention():
    state = State(seed=23)
    scope = setup(state)
    status, job = call(state, "POST", "/api/v1/submit", proposal(), scope=scope)
    assert status == 200 and state.counts()["observations"] == 0
    assert call(state, "GET", f"/jobs/retrieve/{job}", scope=scope)[0] == 409
    for expected in ("WAITING", "STARTED", "FINISHED"):
        assert call(state, "GET", f"/jobs/status/{job}", scope=scope)[1]["status"] == expected
    assert state.counts()["observations"] == 1
    assert call(state, "GET", f"/jobs/cancel/{job}", scope=scope) == (200, False)
    assert call(state, "GET", f"/jobs/status/{job}", scope=scope)[1]["status"] == "FINISHED"
    second = call(state, "POST", "/api/v1/submit", proposal(), scope=scope)[1]
    assert second != job  # duplicates are separate submissions, not invented replay safety
    assert call(state, "GET", f"/jobs/cancel/{second}", scope=scope) == (200, True)
    assert call(state, "GET", f"/jobs/status/{second}", scope=scope)[1]["status"] == "INTERRUPTED"
    assert call(state, "GET", "/releaseSession", scope="s1") == (200, True)
    assert not any(state.counts().values())


def test_owner_map_blocks_foreign_scope_and_wrong_service_jobs():
    state = State()
    scope = setup(state)
    job = call(state, "POST", "/api/v1/submit", proposal(), scope=scope)[1]
    assert call(state, "GET", f"/jobs/status/{job}", scope=scope, owner="bob")[0] == 403
    assert call(state, "GET", f"/jobs/status/{job}", scope=scope, service="resolver")[0] == 404
    call(state, "POST", "/createSession", {"configuration": {"id": "s2"}})
    other = call(state, "POST", "/createContext", {}, scope="s2")[1]["id"]
    assert call(state, "GET", f"/jobs/status/{job}", scope=other)[0] == 403
    assert call(state, "GET", "/releaseContext", scope=scope, owner="bob")[0] == 403
    assert call(state, "POST", "/api/v1/connect", {"configuration": {"id": scope}}, owner="bob")[0] == 403


@pytest.mark.parametrize("mutate", [
    lambda p: p["observation"]["geometry"].update(dimensions=[]),
    lambda p: p["observation"]["geometry"]["dimensions"][0].update(shape=[2, 2]),
    lambda p: p["observation"]["geometry"]["dimensions"][0]["parameters"]["delegate"].update(proj="EPSG:4326"),
    lambda p: p["observation"]["geometry"]["dimensions"][0]["parameters"]["delegate"].update(shape="wrong"),
    lambda p: p["observation"]["geometry"]["dimensions"][0]["parameters"]["delegate"].update(sgrid="99 m"),
    lambda p: p["observation"].update(observable={"urn": "geography:Temperature"}),
])
def test_no_silent_geometry_units_or_semantic_substitution(mutate):
    state = State()
    scope = setup(state)
    payload = proposal()
    mutate(payload)
    assert call(state, "POST", "/api/v1/submit", payload, scope=scope)[0] in (400, 422)
    assert state.counts()["observations"] == 0


def test_resource_budgets_and_trace_never_grow_without_bound():
    state = State()
    for index in range(16):
        assert call(state, "POST", "/createSession", {"configuration": {"id": f"s{index}"}})[0] == 200
    assert call(state, "POST", "/createSession", {"configuration": {"id": "overflow"}})[0] == 429
    assert call(state, "POST", "/createSession", {"configuration": {"id": "s0"}})[0] == 409
    for _ in range(4100):
        state.request("GET", "/unknown?secret=do-not-log", {"Authorization": "not-a-production-secret"})
    assert len(state.trace) == 4096
    assert "do-not-log" not in json.dumps(list(state.trace))
    assert "not-a-production-secret" not in json.dumps(list(state.trace))


@pytest.mark.parametrize("enabled", [False, True])
def test_reset_requires_explicit_test_mode_and_control_identity(enabled):
    state = State(test_mode=enabled)
    scope = setup(state)
    assert state.request("POST", "/__sim__/reset", {"Authorization": "sim-token-alice"})[0] == 403
    status, result, _ = state.request("POST", "/__sim__/reset", {"Authorization": "sim-admin-control"})
    assert status == (200 if enabled else 403)
    if enabled:
        assert not any(state.counts().values())
        assert setup(state) == scope  # explicit reset is not a production incarnation promise


@pytest.mark.parametrize("port", [8091, 8092, 8093, 8094, 8382, 7687, -1, 65536, True])
def test_never_bind_production_or_invalid_ports(port):
    with pytest.raises(ValueError):
        make_server(port)


def test_no_implicit_real_or_hybrid_proxy_and_unknown_routes_fail():
    with pytest.raises(ValueError):
        make_server(mode="real")
    with pytest.raises(ValueError):
        make_server(mode="hybrid")
    state = State()
    assert call(state, "GET", "/unknown")[0] == 501
    assert call(state, "POST", "/createSession", [1])[0] == 400
    assert state.request("GET", "/resources/api/v1/list/MODEL", {"Authorization": "unrecognized"})[0] == 401


@pytest.mark.parametrize("changed", [{"sourceId": True}, {"offset": True},
    {"semantics": {"unit": "mm", "observable": "geography:Elevation in m"}},
    {"curve": "D2_XY"}, {"slice": {"type": "TEMPORAL_INTERVAL", "start": 0, "end": 1}}])
def test_unimplemented_read_mediation_time_or_fill_curve_is_not_synthetic_success(changed):
    state = State()
    scope = setup(state)
    job = call(state, "POST", "/api/v1/submit", proposal(), scope=scope)[1]
    for _ in range(3):
        call(state, "GET", f"/jobs/status/{job}", scope=scope)
    request = {"sourceId": 1, "offset": 0, "semantics": {"unit": "m", "observable": "geography:Elevation in m"},
               "curve": "UNSPECIFIED", "slice": {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}}
    assert call(state, "POST", "/api/v1/observation/value", request | changed, scope=scope)[0] == 422


def test_unimplemented_persistence_and_model_constraints_fail_closed():
    state = State()
    scope = setup(state)
    assert call(state, "POST", "/createContext", {"configuration": {"persistence": "PERMANENT"}}, scope="s1")[0] == 422
    request = proposal() | {"resolutionConstraints": [{"type": "Scenarios", "data": ["unknown"]}]}
    assert call(state, "POST", "/api/v1/submit", request, scope=scope)[0] == 422


def test_nonuniform_fixture_is_pinned_independent_and_does_not_guess_native_point_mapping():
    from klab_simulator.fixtures import nonuniform_fixture
    from conftest import REPO, ROOT
    fixture, native = nonuniform_fixture()
    assert fixture["shape"] == [4, 5] and len(native) == 1008
    assert json.loads((ROOT / "src/klab_simulator/fixtures/synthetic-nonuniform-oracle.json").read_bytes()) == json.loads(
        (REPO / "python-client/tests/fixtures/nonuniform-export.json").read_bytes())
    state = State("nonuniform-export")
    assert state.grid["shape"] == [4, 5] and state.origin == "SYNTHETIC_SIMULATOR"
    scope = setup(state)
    assert call(state, "POST", "/api/v1/observation/value", {"sourceId": 1, "offset": 0}, scope=scope)[0] == 422
