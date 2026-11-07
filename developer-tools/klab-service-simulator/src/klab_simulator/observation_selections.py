"""Finite entity/relationship fixture over source-defined scope headers and queries.

Not a Reasoner/compiler: exactly two declared synthetic observables are admitted.
Endpoint existence/ownership is independently checked by this service, not the SDK.
"""
from copy import deepcopy
import re

from .state import ContractError, OBS, GEO

OCLASS = "org.integratedmodelling.common.knowledge.ObservableImpl"
CCLASS = "org.integratedmodelling.common.knowledge.ConceptImpl"


def observable(name):
    if name not in ("synthetic:Entity", "synthetic:Connection"):
        raise ContractError(404, "Observable outside declared synthetic selection vocabulary")
    relation = name == "synthetic:Connection"
    return {"@CLASS": OCLASS, "urn": name, "contextualization": "CONNECTION" if relation else "INSTANTIATION",
        "semantics": {"@CLASS": CCLASS, "urn": name,
            "type": ["OBSERVABLE", "COUNTABLE", "RELATIONSHIP" if relation else "SUBJECT"]}}


def _selection(state, root, scope, headers):
    parts = scope.split("#")[0].split(".")[2:]
    parent = int(parts[-1]) if parts else None
    if parent is not None and (root, parent) not in state.observations:
        raise ContractError(404, "Focused observation is absent from this owned root")
    values = tuple(headers.get(key) for key in ("klab-source-observation", "klab-target-observation"))
    if values == (None, None): return parent, None
    if any(not isinstance(value, str) or re.fullmatch(r"[1-9][0-9]{0,18}", value) is None for value in values):
        raise ContractError(422, "Both persisted endpoint headers required")
    source, target = map(int, values)
    if source == target or max(source, target) > 2**63 - 1:
        raise ContractError(422, "Distinct persisted endpoint IDs required")
    for identity in (source, target):
        item = state.observations.get((root, identity))
        if item is None: raise ContractError(404, "Relationship endpoint absent from owned root")
        if item["observable"]["urn"] != "synthetic:Entity":
            raise ContractError(422, "Declared fixture endpoints must be entities")
    return parent, (source, target)


def respond(state, service, method, route, principal, scope, headers, data, body):
    if service == "reasoner" and method == "POST" and route == "/api/v1/resolve/observable":
        return observable(body.decode("utf-8")), "application/json"
    if service == "runtime" and route == "/api/v1/observation/value":
        state._context(scope, principal)
        raise ContractError(422, "Declared entity/relationship fixture has no scientific numeric storage")
    if service not in ("runtime", "resolver") or method != "POST" or route not in ("/api/v1/submit", "/api/v1/resolve", "/api/v1/query"):
        return None
    root, _ = state._context(scope, principal)
    if headers.get("klab-service") != state.service_ids["runtime"]:
        raise ContractError(403, "Originating Runtime does not match")
    parent, endpoints = _selection(state, root, scope, headers)
    if not isinstance(data, dict): raise ContractError(422, "Structured selection request required")
    if route == "/api/v1/query":
        if "id" in data and data.get("resultType") == "OBSERVATION":
            row = state.observations.get((root, data["id"]))
            return [deepcopy(row)] if row else [], "application/json"
        expected_source = {"type": "OBSERVATION", "id": parent} if parent is not None else {"type": "SCOPE"}
        if (data.get("type") != "QUERY" or data.get("resultType") != "OBSERVATION"
                or data.get("source") != expected_source or data.get("relationship") != "HAS_CHILD"
                or data.get("depth") != 1 or data.get("minimumDepth") != 1
                or type(data.get("limit")) is not int or not 1 <= data["limit"] <= 129
                or type(data.get("offset")) is not int or not 0 <= data["offset"] <= 2**31 - 1):
            raise ContractError(422, "Only explicit bounded direct-child collection is supported")
        rows = [row for (context, _), row in state.observations.items() if context == root
            and row["metadata"]["delegate"]["simulator:parent"] == parent]
        rows.sort(key=lambda row: row["id"])
        return deepcopy(rows[data["offset"]:data["offset"] + data["limit"]]), "application/json"
    request = data.get("observation")
    if not isinstance(request, dict): raise ContractError(422, "Observation envelope required")
    name = request.get("observable", {}).get("urn")
    declared = observable(name)
    if request["observable"] != declared or data.get("resolutionConstraints"):
        raise ContractError(422, "Only exact synthetic observables without additional constraints admitted")
    if (name == "synthetic:Connection") != (endpoints is not None):
        raise ContractError(422, "Connection requires both endpoints; entity creation does not use between")
    receipt = {"parent": parent, "source": endpoints[0] if endpoints else None, "target": endpoints[1] if endpoints else None,
        "evidence": "SIMULATED_PROVISIONAL_NOT_NATIVE_SCIENTIFIC_EXECUTION"}
    if service == "resolver":
        result = {"resolutionOutcome": "RESOLVED", "empty": False, "notifications": [], "resolvedCoverage": 1.0,
            "computation": [], "requirements": {"results": []}, "selection": receipt}
    else:
        identity = state.next_observation; state.next_observation += 1
        result = {"@CLASS": OBS, "id": identity, "urn": root + "." + str(identity),
            "name": request.get("name"), "observable": declared,
            "geometry": {"@CLASS": GEO, "scalar": True, "dimensions": []},
            "empty": False, "resolvedCoverage": 1.0, "notifications": [],
            "metadata": {"delegate": {"simulator:parent": parent, "simulator:selection": receipt}}}
    return state._job(service, root, principal, result), "application/json"
