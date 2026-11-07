"""Finite source-shaped protocol1 fixtures; no Reasoner admission or selection.

JAVA-GAP-0003. Supplier project READ policy is synthetic; namespace/model visibility
mirrors the inspected ModelCandidateRequest.visible predicates, not a native run.
"""
import hashlib
import json

from .state import ContractError

SOURCE = "klab.model.candidate.source.v1"
HASH = "klab.model.candidate.sourceHash.v1"


def candidates(state, principal, headers, body):
    if state.scenario != "candidate-sources":
        raise ContractError(404, "Source-candidate protocol is not advertised by this fixture")
    if any(headers.get(key) for key in ("klab-scope", "klab-service", "klab-resolution-cycle", "klab-transaction")):
        raise ContractError(422, "Supplier source query must not delegate a Runtime workflow")
    if len(body) > 32768:
        raise ContractError(413, "Candidate selector byte budget exceeded")
    try:
        def unique(items):
            value = {}
            for key, item in items:
                if key in value:
                    raise ValueError("Duplicate candidate selector")
                value[key] = item
            return value
        request = json.loads(body, object_pairs_hook=unique)
    except (ValueError, UnicodeError, RecursionError):
        raise ContractError(422, "Invalid candidate selector JSON") from None
    if (not isinstance(request, dict) or set(request) != {"version", "namespaces", "projects", "scenarios"}
            or type(request["version"]) is not int or request["version"] != 1
            or any(not isinstance(request[k], list) or len(request[k]) > 32
                or any(not isinstance(v, str) or not v or len(v) > 256 for v in request[k])
                for k in ("namespaces", "projects", "scenarios"))):
        raise ContractError(422, "Only bounded protocol1 candidate selectors are supported")
    result = {"empty": True, "namespaces": [], "results": [], "services": {}, "notifications": []}
    fixtures = (
        ("synthetic.public", "synthetic.shared", "PUBLIC", None, False, False),
        ("synthetic.private", "synthetic.alice", "PRIVATE", "alice", False, False),
        ("synthetic.project", "synthetic.alice", "PROJECT_PRIVATE", "alice", False, False),
        ("synthetic.scenario", "synthetic.shared", "PUBLIC", None, True, False),
        ("synthetic.inactive", "synthetic.shared", "PUBLIC", None, False, True))
    for urn, project, visibility, owner, scenario, inactive in sorted(fixtures):
        if inactive or owner is not None and principal != owner:
            continue
        same_namespace, same_project = urn in request["namespaces"], project in request["projects"]
        visible = (same_namespace if visibility == "PRIVATE" else False if visibility == "PROJECT_PRIVATE" and not same_project
            else same_namespace or urn in request["scenarios"] or not scenario)
        if not visible:
            continue
        model = {"@CLASS": "org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl", "urn": urn + ".model", "scope": visibility}
        source = {"@CLASS": "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl", "urn": urn,
            "projectName": project, "lastUpdateTimestamp": 7, "inactive": False, "scenario": scenario, "statements": [model]}
        encoded = json.dumps(source, sort_keys=True, separators=(",", ":"))
        digest = hashlib.sha256(encoded.encode("utf-8")).hexdigest()
        for kind, identity, collection in (("NAMESPACE", urn, "namespaces"), ("MODEL", model["urn"], "results")):
            result[collection].append({"serviceId": state.service_ids["resources"], "resourceUrn": identity,
                "projectUrn": project, "timestamp": 7, "knowledgeClass": kind,
                "metadata": {SOURCE: encoded, HASH: digest}, "notifications": []})
    result["empty"] = not result["results"]
    return result, "application/json"
