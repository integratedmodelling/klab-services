"""Bounded actual Reasoner snapshots; capture completion is not hierarchy acceptance."""
from dataclasses import asdict
import json
from pathlib import Path

from klab_client import Client, Endpoint, SemanticExplorer
from klab_client.evidence import _plain
from .scientific_checks import require

PAIRS = (("consolidation:Region", "consolidation:Subject"),
         ("consolidation:Scientist", "consolidation:Agent"))


def allowed_call(method, path):
    return ((method == "GET" and path in ("/reasoner/public/capabilities", "/reasoner/public/status"))
        or (method == "POST" and path in ("/reasoner/api/v1/resolve/concept", "/reasoner/api/v1/parents",
            "/reasoner/api/v1/children", "/reasoner/api/v1/subsumes", "/reasoner/api/v1/satisfiable")))


def semantic_record(value):
    if type(value) is bool:
        return value
    if isinstance(value, (list, tuple)):
        return [semantic_record(item) for item in value]
    return {"urn": value.urn, "knowledge": asdict(value.knowledge),
        "freshness": value.knowledge.freshness, "raw": _plain(value.raw)}


def collect(explorer, public, save):
    report = {"passed": False, "diagnostic_only": True, "hierarchy_acceptance": "NOT_CLAIMED",
        "scientific_execution": "NOT_RUN", "records": [], "pairs": []}
    def capture(label, operation, *, semantic=False):
        try:
            value = operation()
            value = semantic_record(value) if semantic else _plain(value)
            row = {"label": label, "value": value}
        except Exception as error:
            row = {"label": label, "error_type": type(error).__name__}
        report["records"].append(row)
        save(report)  # Preserve the actual response before any expectation evaluation.
        return row
    capture("capabilities-before", lambda: public("capabilities"))
    capture("status-before", lambda: public("status"))
    for child, parent in PAIRS:
        rows = {}
        for label, operation in (
                ("resolve-child", lambda: explorer.resolve_concept(child)),
                ("resolve-parent", lambda: explorer.resolve_concept(parent)),
                ("parents", lambda: explorer.parents(child)),
                ("children", lambda: explorer.children(parent)),
                ("child-is-parent", lambda: explorer.is_a(child, parent)),
                ("parent-is-child", lambda: explorer.is_a(parent, child)),
                ("child-satisfiable", lambda: explorer.satisfiable(child)),
                ("parent-satisfiable", lambda: explorer.satisfiable(parent))):
            rows[label] = capture(child + ":" + label, operation, semantic=True)
        pair = {"child": child, "parent": parent, "source_expectation": "child directly specializes parent",
            "assessment": "UNRESOLVED_READ_FAILURE"}
        if not any("error_type" in row for row in rows.values()):
            parents = [item["urn"] for item in rows["parents"]["value"]]
            children = [item["urn"] for item in rows["children"]["value"]]
            checks = {"resolved_child_identity": rows["resolve-child"]["value"]["urn"] == child,
                "resolved_parent_identity": rows["resolve-parent"]["value"]["urn"] == parent,
                "parent_in_direct_parents": parent in parents, "child_in_direct_children": child in children,
                "forward_is_a": rows["child-is-parent"]["value"] is True,
                "reverse_is_a_false": rows["parent-is-child"]["value"] is False,
                "child_satisfiable": rows["child-satisfiable"]["value"] is True,
                "parent_satisfiable": rows["parent-satisfiable"]["value"] is True}
            pair.update(actual_parents=parents, actual_children=children, checks=checks,
                assessment="OBSERVED_SOURCE_MATCH" if all(checks.values()) else "OBSERVED_DIVERGENCE")
        report["pairs"].append(pair)
        save(report)
    capture("capabilities-after", lambda: public("capabilities"))
    capture("status-after", lambda: public("status"))
    report["read_failures"] = [row["label"] for row in report["records"] if "error_type" in row]
    report["passed"] = not report["read_failures"]
    report["generation_source_equivalence"] = "NOT_ESTABLISHED; retained supplied witnesses require separate interpretation"
    save(report)
    require(report["passed"], "Read-only hierarchy diagnostic incomplete; prior responses retained")
    return report


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    calls = []
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            reasoner=Endpoint(config["urls"]["reasoner"], config["token"]), timeout=15) as client:
        client.initialize_user_scope(local_federation=True)
        def audit(request):
            path = request.url.path
            require(allowed_call(request.method, path),
                "Diagnostic attempted a non-Reasoner or mutation operation")
            calls.append({"method": request.method, "path": path})
        client.transport._http.event_hooks["request"].append(audit)
        def save(report):
            output = report | {"calls": calls, "scientific_submissions": 0, "sessions_contexts_created": 0}
            encoded = json.dumps(output, indent=2, allow_nan=False)
            require(len(encoded.encode("utf-8")) <= 2 * 1024 * 1024, "Diagnostic snapshot exceeds byte budget")
            require(client.transport.redact(encoded) == encoded, "Diagnostic snapshot contains credentials")
            (state / "hierarchy-diagnostic.json").write_text(encoded, encoding="utf-8")
        report = collect(SemanticExplorer(client), lambda route: client.transport.request("reasoner", "GET",
            "/public/" + route, max_response_bytes=512 * 1024), save)
        report.update(scientific_submissions=0, sessions_contexts_created=0)
    return report
