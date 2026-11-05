"""Source-defined canonical core-alias assertions; no Python semantic inference."""
import hashlib
import json
from pathlib import Path

from .hierarchy_diagnostic import run as capture
from .scientific_checks import require

EXPECTED = (("consolidation:Region", "consolidation:Subject", "odo:Subject"),
            ("consolidation:Scientist", "consolidation:Agent", "odo:Agent"))
IDENTITY = ("serviceId", "worldviewId", "worldviewUrn", "worldviewCommitment",
            "knowledgeRevision", "semanticSessionId", "semanticContentFingerprint")


def validate(snapshot, ontology_sha256):
    require(snapshot.get("passed") is True and snapshot.get("read_failures") == [], "Incomplete diagnostic snapshot")
    rows = {row["label"]: row["value"] for row in snapshot["records"]}
    require(len(rows) == 20, "Expected all unique bounded response records")
    before, after = (rows["capabilities-" + suffix] for suffix in ("before", "after"))
    require(all(before.get(key) is not None and before[key] == after.get(key) for key in IDENTITY),
        "Observed Reasoner identity/commitment/revision changed or absent")
    require(type(before["knowledgeRevision"]) is int and before["knowledgeRevision"] > 0,
        "Required observed revision witness absent or invalid")
    require(before["worldviewUrn"] == "consolidation" and isinstance(before["worldviewId"], str)
        and bool(before["worldviewId"]), "Authored worldview URN or opaque identity differs")
    commitment = before["worldviewCommitment"]
    require(commitment.get("version") == 1 and commitment.get("worldviewId") == before["worldviewId"] and
        commitment.get("ontologies", {}).get("consolidation") == ontology_sha256,
        "Served ontology commitment differs from exact owned source")
    states = [rows["status-" + suffix] for suffix in ("before", "after")]
    generations = [item["metadata"]["delegate"]["semantic.candidate"] for item in states]
    require(generations[0] == generations[1] and type(generations[0].get("acceptedGeneration")) is int
        and generations[0]["acceptedGeneration"] > 0 and
        generations[0].get("acceptedSources", {}).get("ONTOLOGY:consolidation") == ontology_sha256,
        "Accepted generation/source witness changed, absent or differs")
    expected_stamp = {"service_id": before["serviceId"], "worldview_id": before["worldviewId"],
                      "revision": before["knowledgeRevision"]}
    checked = []
    stamp = rows["consolidation:Region:resolve-child"]["knowledge"]
    for child, local_parent, canonical in EXPECTED:
        values = {key: rows[child + ":" + key] for key in ("resolve-child", "resolve-parent", "parents", "children",
            "child-is-parent", "parent-is-child", "child-satisfiable", "parent-satisfiable")}
        require(values["resolve-child"]["urn"] == child and values["resolve-parent"]["urn"] == canonical,
            "Authored child/core-alias canonical target differs")
        parents, children = values["parents"], values["children"]
        require(canonical in {item["urn"] for item in parents} and child in {item["urn"] for item in children},
            "Canonical direct hierarchy differs from authored relationships")
        require(any(item["urn"] == canonical and item["raw"].get("metadata", {}).get("delegate", {}).get(
            "klab:localAlias") == local_parent for item in parents), "Canonical parent lost explicit local alias metadata")
        require(values["child-is-parent"] is True and values["parent-is-child"] is False and
            values["child-satisfiable"] is True and values["parent-satisfiable"] is True,
            "Directional specialization or satisfiability differs")
        for item in (values["resolve-child"], values["resolve-parent"], *parents, *children):
            require(item["knowledge"] == stamp and all(item["knowledge"].get(key) == value for key, value in expected_stamp.items()),
                "Hierarchy knowledge stamp differs from observed opaque service/worldview/revision")
        checked.append({"child": child, "authored_parent": local_parent, "canonical_parent": canonical,
            "actual_parents": [item["urn"] for item in parents], "actual_children": [item["urn"] for item in children],
            "forward_is_a": True, "reverse_is_a": False, "satisfiable": True})
    require(snapshot.get("scientific_submissions") == snapshot.get("sessions_contexts_created") == 0,
        "Unexpected scientific/session/context creation")
    return {"passed": True, "bounded_canonical_hierarchy": checked, "observed_identity": {key: before[key] for key in IDENTITY},
        "observed_generation": generations[0], "retained_advisories": [item.get("advisories", []) for item in states],
        "worldview_warning_resolved": False, "whole_worldview_validity": "NOT_CLAIMED",
        "model_plan_dependency_access_matrix": "NOT_TESTED", "atomic_snapshot": "NOT_CLAIMED",
        "scientific_submissions": 0, "sessions_contexts_created": 0}


def run(state):
    state = Path(state)
    capture(state)
    path = state / "hierarchy-diagnostic.json"
    snapshot = json.loads(path.read_text(encoding="utf-8"))
    ontology = state / "projects/consolidation/src/consolidation.kwv"
    report = validate(snapshot, hashlib.sha256(ontology.read_bytes()).hexdigest())
    report["diagnostic_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    (state / "canonical-hierarchy.json").write_text(json.dumps(report, indent=2))
    return report
