"""Declared finite ranking/lattice effects, not Java model admission or a compiler.

Three numeric fixtures share one quality; lexical scores mirror inspected
PrioritizerImpl.computeLexicalScope, with declared ties and observer compatibility.
Only a4x4metre square and two anchored lattice spans are admitted.
"""
from copy import deepcopy
from typing import Any

from .observation_selections import OCLASS, CCLASS
from .state import ContractError, OBS, GEO

MODELS = (("synthetic.base", "synthetic.default", 0),
    ("synthetic.alternate", "synthetic.alternate-project", 10),
    ("synthetic.scenario", "synthetic.scenario-project", 20))
CONSTRAINT = "org.integratedmodelling.klab.api.services.resolver.objects.ResolutionConstraintImpl"


def observable(name):
    if name not in ("synthetic:Signal in m", "synthetic:ObserverA", "synthetic:ObserverB"):
        raise ContractError(404, "Outside declared material-selection vocabulary")
    agent = name != "synthetic:Signal in m"
    return {"@CLASS": OCLASS, "urn": name, "artifactType": "OBJECT" if agent else "NUMBER", "contextualization": "INSTANTIATION" if agent else "MEASURE",
        **({} if agent else {"unit": {"definition": "m"}}), "semantics": {"@CLASS": CCLASS, "urn": name,
        "type": ["OBSERVABLE", "AGENT", "COUNTABLE", "SUBJECT"] if agent else ["OBSERVABLE", "QUALITY", "QUANTIFIABLE"]}}


def _grid(configuration):
    if configuration.get("gridUrn") is not None:
        raise ContractError(422, "Named grid outside this finite reference")
    grid = configuration.get("gridDefinition", {"anchor": "POINT (0 0)", "projection": "EPSG:3857", "span": 1, "strict": True, "snap": False})
    if (not isinstance(grid, dict) or set(grid) != {"anchor", "projection", "span", "strict", "snap"}
            or grid["anchor"] not in ("POINT (0 0)", "POINT (0.0 0.0)")
            or grid["projection"] != "EPSG:3857" or type(grid["span"]) not in (int, float)
            or grid["span"] not in (1, 2) or grid["strict"] is not True or grid["snap"] is not False):
        raise ContractError(422, "Only declared origin-anchored one/two-metre lattices admitted")
    return deepcopy(grid)


def _constraints(data):
    rows = data.get("resolutionConstraints", [])
    if not isinstance(rows, list): raise ContractError(422, "Constraint array required")
    result = {}
    for row in rows:
        if not isinstance(row, dict) or row.get("@CLASS") != CONSTRAINT:
            raise ContractError(422, "Source-shaped constraint required")
        kind, values = row.get("type"), row.get("data")
        if (kind not in ("ResolutionNamespace", "ResolutionProject", "Scenarios", "Observer") or kind in result
                or not isinstance(values, list) or not values or (kind != "Scenarios" and len(values) != 1)):
            raise ContractError(422, "Unsupported, duplicate or malformed constraint")
        if kind == "Observer":
            if type(values[0]) is not int or not 1 <= values[0] <= 2**63 - 1:
                raise ContractError(422, "Positive persisted observer required")
        elif any(not isinstance(value, str) or not value.strip() for value in values) or len(set(values)) != len(values):
            raise ContractError(422, "Distinct nonblank constraint values required")
        result[kind] = values
    return result


def _plan(state, root, scope, request, constraints):
    if len(scope.split("#")[0].split(".")) != 2:
        raise ContractError(422, "Focused scopes are outside this finite root-lattice reference")
    grid = _grid(state.contexts[root]["configuration"])
    geometry = request.get("geometry", {})
    dimensions = geometry.get("dimensions", []) if isinstance(geometry, dict) else []
    dimension = dimensions[0] if isinstance(dimensions, list) and len(dimensions) == 1 and isinstance(dimensions[0], dict) else {}
    parameters = dimension.get("parameters")
    delegate = parameters.get("delegate") if isinstance(parameters, dict) else None
    if (dimension.get("type") != "SPACE" or dimension.get("shape") != [4, 4] or not isinstance(delegate, dict)
            or delegate.get("shape") !=
                "EPSG:3857 POLYGON ((0 0,0 4,4 4,4 0,0 0))"):
        raise ContractError(422, "Explicit declared4x4metre rectangular request required")
    observer = constraints.get("Observer", [None])[0]
    marker = scope.partition("#")[2]
    if marker != (str(observer) if observer is not None else ""):
        raise ContractError(422, "Observer constraint and selected scope must agree")
    bias = 0
    if observer is not None:
        selected = state.observations.get((root, observer))
        if selected is None: raise ContractError(404, "Observer absent from owned root")
        name = selected["observable"]["urn"]
        if name not in ("synthetic:ObserverA", "synthetic:ObserverB"):
            raise ContractError(422, "Selected observation is not a declared agent")
        bias = 100 if name == "synthetic:ObserverB" else 0
    namespace = constraints.get("ResolutionNamespace", [None])[0]
    project = constraints.get("ResolutionProject", [None])[0]
    scenarios = constraints.get("Scenarios", [])
    # These are preferences, not hard namespace/project restrictions or forced models.
    ranked = [(100 if ns in scenarios else 75 if ns == namespace else 50 if pr == project else 0, -index, ns, pr, offset)
        for index, (ns, pr, offset) in enumerate(MODELS)]
    score, _, ns, pr, offset = max(ranked)
    span = grid["span"]; count = int(4 / span)
    coordinates = [(span * (column + .5), span * (row + .5)) for row in range(count) for column in range(count)]
    values = [x + 2 * y + offset + bias for x, y in coordinates]
    return {"evidence": "SIMULATED_PROVISIONAL_NOT_NATIVE_ADMISSION", "model": ns + ".model",
        "namespace": ns, "project": pr, "lexicalScore": score, "observer": observer,
        "grid": grid, "shape": [count, count], "inputCoordinates": coordinates,
        "operator": "x + 2*y + model_offset + observer_bias", "modelOffset": offset,
        "observerBias": bias}, values


def respond(state, service, method, route, principal, scope, headers, data, body):
    if service == "reasoner" and method == "POST" and route == "/api/v1/resolve/observable":
        return observable(body.decode("utf-8")), "application/json"
    if service == "runtime" and method == "POST" and route == "/createContext":
        state._owner(state.sessions, scope, principal)
        configuration = (data or {}).get("configuration", {})
        if not isinstance(configuration, dict) or configuration.get("persistence", "ONE_OFF") != "ONE_OFF":
            raise ContractError(422, "Only explicit owned ONE_OFF context supported")
        grid = _grid(configuration)
        if len(state.contexts) >= 32: raise ContractError(429, "Context budget exceeded")
        identity = f"{scope}.c{state.next_context}"; state.next_context += 1
        config = {"id": identity, "serviceId": state.service_ids["runtime"], "persistence": "ONE_OFF", "gridDefinition": grid}
        state.contexts[identity] = {"owner": principal, "configuration": config}
        return config, "application/json"
    if service not in ("runtime", "resolver") or method != "POST" or route not in ("/api/v1/submit", "/api/v1/resolve", "/api/v1/observation/value"):
        return None
    root, _ = state._context(scope, principal)
    if not route.endswith("/value") and headers.get("klab-service") != state.service_ids["runtime"]:
        raise ContractError(403, "Originating Runtime does not match")
    if headers.get("klab-source-observation") is not None or headers.get("klab-target-observation") is not None:
        raise ContractError(422, "Material selection fixture does not admit relationship selection")
    if not isinstance(data, dict): raise ContractError(422, "Structured request required")
    if route.endswith("/value"):
        source_id, offset = data.get("sourceId"), data.get("offset")
        if type(source_id) is not int or (root, source_id) not in state.observations:
            raise ContractError(404, "Selected numeric observation absent")
        values = state.observations[(root, source_id)]["metadata"]["delegate"].get("simulator:values", [])
        if (type(offset) is not int or not 0 <= offset < len(values) or data.get("semantics") is not None
                or data.get("curve") != "UNSPECIFIED" or data.get("slice") !=
                {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}):
            raise ContractError(422, "Only declared native-unit initialization offsets admitted")
        return str(values[offset]), "text/plain"
    request = data.get("observation")
    if not isinstance(request, dict) or not isinstance(request.get("observable"), dict):
        raise ContractError(422, "Observation with exact declared observable required")
    declared = observable(request["observable"].get("urn"))
    if request["observable"] != declared: raise ContractError(422, "Forged observable refused")
    constraints = _constraints(data)
    agent = declared["urn"] != "synthetic:Signal in m"
    plan: dict[str, Any]
    if agent:
        if constraints or "#" in scope: raise ContractError(422, "Unselected declared agent creation only")
        plan, values = {"evidence": "SIMULATED_PROVISIONAL_NOT_NATIVE_ADMISSION", "agent": declared["urn"]}, []
    else:
        plan, values = _plan(state, root, scope, request, constraints)
    if service == "resolver":
        return state._job(service, root, principal, {"resolutionOutcome": "RESOLVED", "empty": False,
            "resolvedCoverage": 1.0, "notifications": [], "computation": [], "requirements": {"results": []},
            "selection": plan}), "application/json"
    identity = state.next_observation; state.next_observation += 1
    geometry: dict[str, Any] = {"@CLASS": GEO, "scalar": True, "dimensions": []} if agent else deepcopy(request["geometry"])
    if not agent:
        geometry["dimensions"][0]["shape"] = plan["shape"]
        geometry["dimensions"][0]["parameters"]["delegate"]["sgrid"] = f"{plan['grid']['span']} m"
    result = {"@CLASS": OBS, "id": identity, "urn": root + "." + str(identity), "observable": declared,
        "geometry": geometry, "empty": False, "resolvedCoverage": 1.0, "notifications": [],
        "metadata": {"delegate": {"simulator:selection": plan, "simulator:values": values}}}
    return state._job(service, root, principal, result), "application/json"
