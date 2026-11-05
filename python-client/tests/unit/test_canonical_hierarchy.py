from copy import deepcopy

import pytest

from workflows.canonical_hierarchy import validate


def snapshot():
    digest = "a" * 64
    caps = {"serviceId": "owned-reasoner", "worldviewId": "opaque-worldview-id", "worldviewUrn": "consolidation",
        "worldviewCommitment": {"version": 1, "worldviewId": "opaque-worldview-id", "ontologies": {"consolidation": digest}},
        "knowledgeRevision": 1, "semanticSessionId": "owned-session", "semanticContentFingerprint": "supplied-fingerprint"}
    generation = {"inputGeneration": 2, "acceptedGeneration": 1, "acceptedSources": {"ONTOLOGY:consolidation": digest}}
    status = {"metadata": {"delegate": {"semantic.candidate": generation}},
        "advisories": [{"message": "retained quality warning"}]}
    report = {"passed": True, "read_failures": [], "scientific_submissions": 0, "sessions_contexts_created": 0,
        "records": [{"label": "capabilities-before", "value": caps}, {"label": "status-before", "value": status}]}
    stamp = {"service_id": "owned-reasoner", "worldview_id": "opaque-worldview-id", "revision": 1,
        "scope": None, "service_url": "http://127.0.0.1:38091/reasoner"}
    def item(urn, alias=None):
        return {"urn": urn, "knowledge": dict(stamp), "raw": {"metadata": {"delegate": {"klab:localAlias": alias}}}}
    for child, parent, target in (("consolidation:Region", "consolidation:Subject", "odo:Subject"),
                                  ("consolidation:Scientist", "consolidation:Agent", "odo:Agent")):
        values = {"resolve-child": item(child), "resolve-parent": item(target), "parents": [item(target, parent)],
            "children": [item(child)], "child-is-parent": True, "parent-is-child": False,
            "child-satisfiable": True, "parent-satisfiable": True}
        report["records"].extend({"label": child + ":" + label, "value": value} for label, value in values.items())
    report["records"].extend([{"label": "capabilities-after", "value": deepcopy(caps)},
                              {"label": "status-after", "value": deepcopy(status)}])
    return report


def row(data, label):
    return next(item for item in data["records"] if item["label"] == label)


def test_canonical_targets_opaque_worldview_identity_and_warning_are_preserved():
    result = validate(snapshot(), "a" * 64)
    assert result["passed"] and result["observed_identity"]["worldviewId"] == "opaque-worldview-id"
    assert result["bounded_canonical_hierarchy"][0]["canonical_parent"] == "odo:Subject"
    assert result["retained_advisories"][0] == [{"message": "retained quality warning"}]
    assert not result["worldview_warning_resolved"] and result["whole_worldview_validity"] == "NOT_CLAIMED"


@pytest.mark.parametrize("defect", ["literal", "parent", "alias", "child", "reverse", "satisfiable",
    "stamp", "scope", "worldview", "revision", "commitment", "generation", "accepted-source", "mutation"])
def test_incorrect_or_inferred_relationship_and_identity_never_pass(defect):
    data = snapshot()
    parents = row(data, "consolidation:Region:parents")["value"]
    if defect == "literal":
        row(data, "consolidation:Region:resolve-parent")["value"]["urn"] = "consolidation:Subject"
    if defect == "parent":
        parents[0]["urn"] = "unrelated:Parent"
    if defect == "alias":
        parents[0]["raw"]["metadata"]["delegate"] = {}
    if defect == "child":
        row(data, "consolidation:Region:children")["value"][0]["urn"] = "unrelated:Child"
    if defect == "reverse":
        row(data, "consolidation:Region:parent-is-child")["value"] = True
    if defect == "satisfiable":
        row(data, "consolidation:Region:child-satisfiable")["value"] = False
    if defect in ("stamp", "scope"):
        parents[0]["knowledge"]["worldview_id" if defect == "stamp" else "scope"] = "changed"
    if defect in ("worldview", "revision", "commitment"):
        caps = row(data, "capabilities-after")["value"]
        caps[{"worldview": "worldviewId", "revision": "knowledgeRevision", "commitment": "worldviewCommitment"}[defect]] = "changed"
    if defect in ("generation", "accepted-source"):
        state = row(data, "status-after")["value"]["metadata"]["delegate"]["semantic.candidate"]
        if defect == "generation":
            state["acceptedGeneration"] = 3
        else:
            state["acceptedSources"]["ONTOLOGY:consolidation"] = "b" * 64
    if defect == "mutation":
        data["scientific_submissions"] = 1
    with pytest.raises(AssertionError):
        validate(data, "a" * 64)
