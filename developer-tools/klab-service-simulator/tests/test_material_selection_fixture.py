"""Supplementary finite-fixture intake controls, not public SDK acceptance."""
from copy import deepcopy

import pytest

from klab_simulator import material_selections as fixture
from klab_simulator.state import State, ContractError


def state():
    result = State("material-selections")
    result.sessions["s"] = {"owner": "alice"}
    result.contexts["s.c"] = {"owner": "alice", "configuration": {"id": "s.c"}}
    for identity, name in ((1, "synthetic:ObserverA"), (2, "synthetic:ObserverB"), (3, "synthetic:Signal in m")):
        result.observations[("s.c", identity)] = {"observable": fixture.observable(name)}
    return result


def request():
    return {"observable": fixture.observable("synthetic:Signal in m"), "geometry": {
        "dimensions": [{"type": "SPACE", "shape": [4, 4], "parameters": {"delegate": {
            "shape": "EPSG:3857 POLYGON ((0 0,0 4,4 4,4 0,0 0))"}}}]}}


def constraint(kind, values):
    return {"@CLASS": fixture.CONSTRAINT, "type": kind, "data": values}


def call(s, *, service="runtime", route="/api/v1/submit", method="POST", principal="alice", scope="s.c", headers=None, data=None, body=b""):
    return fixture.respond(s, service, method, route, principal, scope,
        {"klab-service": s.service_ids["runtime"]} if headers is None else headers, data, body)


@pytest.mark.parametrize("grid", [None, {}, {"anchor": "POINT (1 0)", "projection": "EPSG:3857", "span": 1, "strict": True, "snap": False},
    {"anchor": "POINT (0 0)", "projection": "EPSG:4269", "span": 1, "strict": True, "snap": False},
    {"anchor": "POINT (0 0)", "projection": "EPSG:3857", "span": True, "strict": True, "snap": False},
    {"anchor": "POINT (0 0)", "projection": "EPSG:3857", "span": 3, "strict": True, "snap": False},
    {"anchor": "POINT (0 0)", "projection": "EPSG:3857", "span": 1, "strict": False, "snap": False},
    {"anchor": "POINT (0 0)", "projection": "EPSG:3857", "span": 1, "strict": True, "snap": True}])
def test_refuse_undeclared_grid(grid):
    with pytest.raises(ContractError): fixture._grid({"gridDefinition": grid})


@pytest.mark.parametrize("rows", [None, [None], [{}], [constraint("UsingModel", ["forced"])],
    [constraint("Observer", [])], [constraint("Observer", [True])], [constraint("Observer", [0])],
    [constraint("Observer", [2**63])], [constraint("Observer", [1, 2])],
    [constraint("Scenarios", "scenario")], [constraint("Scenarios", [None])],
    [constraint("Scenarios", [" "])], [constraint("Scenarios", ["same", "same"])],
    [constraint("ResolutionProject", ["project"]), constraint("ResolutionProject", ["again"])]] )
def test_constraint_refusals(rows):
    with pytest.raises(ContractError): fixture._constraints({"resolutionConstraints": rows})


@pytest.mark.parametrize("geometry", [None, {"dimensions": None}, {"dimensions": []}, {"dimensions": [None]},
    {"dimensions": [{"type": "TIME"}]}, {"dimensions": [{"type": "SPACE", "shape": [4, 4]}]},
    {"dimensions": [{"type": "SPACE", "shape": [4, 4], "parameters": {"delegate": None}}]}])
def test_undeclared_geometry_refuses(geometry):
    proposal = request(); proposal["geometry"] = geometry
    with pytest.raises(ContractError): fixture._plan(state(), "s.c", "s.c", proposal, {})


def test_source_score_preferences_ties_observer_and_grid_are_material_not_echoes():
    s = state()
    for constraints, expected in (({}, "synthetic.base"), ({"ResolutionNamespace": ["synthetic.alternate"]}, "synthetic.alternate"),
        ({"ResolutionProject": ["synthetic.alternate-project"]}, "synthetic.alternate"),
        ({"Scenarios": ["synthetic.scenario"]}, "synthetic.scenario")):
        plan, values = fixture._plan(s, "s.c", "s.c", request(), constraints)
        assert plan["namespace"] == expected and len(values) == 16
    plan, values = fixture._plan(s, "s.c", "s.c#2", request(), {"Observer": [2]})
    assert values[0] == 101.5 and plan["observerBias"] == 100
    plan, values = fixture._plan(s, "s.c", "s.c#1", request(), {"Observer": [1]})
    assert values[0] == 1.5 and plan["observerBias"] == 0
    s.contexts["s.c"]["configuration"]["gridDefinition"] = {**fixture._grid({}), "span": 2}
    plan, values = fixture._plan(s, "s.c", "s.c", request(), {})
    assert plan["shape"] == [2, 2] and values == [3, 5, 7, 9]
    for scope, constraints in (("s.c#2", {}), ("s.c#99", {"Observer": [99]}), ("s.c#3", {"Observer": [3]})):
        with pytest.raises(ContractError): fixture._plan(s, "s.c", scope, request(), constraints)
    with pytest.raises(ContractError, match="Focused scopes"):
        fixture._plan(s, "s.c", "s.c.3", request(), {})


def test_creation_routes_scope_guards_and_no_silent_unsupported_storage():
    s = state()
    assert call(s, service="reasoner", route="/api/v1/resolve/observable", body=b"synthetic:Signal in m")[0] == fixture.observable("synthetic:Signal in m")
    with pytest.raises(ContractError): fixture.observable("native:Unknown")
    with pytest.raises(ContractError): fixture._grid({"gridUrn": "unconfirmed"})
    assert fixture._grid({})["span"] == 1
    config, _ = call(s, route="/createContext", scope="s", data={"configuration": {}})
    assert config["gridDefinition"]["span"] == 1
    for bad in (None, {"persistence": "EXPLICIT_ACTION"}):
        with pytest.raises(ContractError): call(s, route="/createContext", scope="s", data={"configuration": bad})
    s.contexts.update({str(i): {} for i in range(32)})
    with pytest.raises(ContractError): call(s, route="/createContext", scope="s", data={})
    assert call(s, service="resources") is None and call(s, method="GET") is None and call(s, route="/other") is None
    with pytest.raises(ContractError): call(s, headers={}, data={})
    with pytest.raises(ContractError): call(s, principal="bob", data={})
    for key in ("klab-source-observation", "klab-target-observation"):
        with pytest.raises(ContractError): call(s, headers={"klab-service": s.service_ids["runtime"], key: "1"}, data={})
    for bad in (None, {}, {"observation": {}}, {"observation": {"observable": None}}):
        with pytest.raises(ContractError): call(s, data=bad)
    forged = request(); forged["observable"]["unit"] = {"definition": "km"}
    with pytest.raises(ContractError): call(s, data={"observation": forged})
    for service in ("runtime", "resolver"):
        for name in ("synthetic:ObserverA", "synthetic:Signal in m"):
            proposal = request() if "Signal" in name else {"observable": fixture.observable(name)}
            identity, _ = call(s, service=service, data={"observation": proposal})
            result = s.jobs[(service, identity)]["result"]
            if service == "runtime":
                s.observations[("s.c", result["id"])] = deepcopy(result)
                if "Signal" in name:
                    selected = result["id"]
                    assert result["geometry"]["dimensions"][0]["parameters"]["delegate"]["sgrid"] == "1 m"
            else: assert result["resolutionOutcome"] == "RESOLVED"
    agent = {"observation": {"observable": fixture.observable("synthetic:ObserverA")}}
    with pytest.raises(ContractError): call(s, scope="s.c#1", data=agent)
    with pytest.raises(ContractError): call(s, data={**agent, "resolutionConstraints": [constraint("Observer", [1])]})
    point = {"sourceId": selected, "offset": 0, "curve": "UNSPECIFIED", "slice": {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}}
    assert call(s, route="/api/v1/observation/value", data=point)[0] == "1.5"
    for changes in ({"sourceId": None}, {"sourceId": 999}, {"offset": True}, {"offset": -1}, {"offset": 16},
        {"semantics": {}}, {"curve": "D2_XY"}, {"slice": {}}):
        with pytest.raises(ContractError): call(s, route="/api/v1/observation/value", data={**point, **changes})
