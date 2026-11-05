import json
from types import SimpleNamespace

import pytest

from klab_client.semantics import KnowledgeState
from workflows.hierarchy_diagnostic import allowed_call, collect


class Explorer:
    def __init__(self, defect=None):
        self.defect = defect
        self.stamp = KnowledgeState("https://reasoner.invalid", "owned", None, "consolidation", None)
    def result(self, urn):
        return SimpleNamespace(urn=urn, knowledge=self.stamp, raw={"urn": urn})
    def resolve_concept(self, definition):
        return self.result(definition)
    def parents(self, definition):
        if self.defect == "read-failure":
            raise ValueError("private error must not appear")
        parent = {"consolidation:Region": "consolidation:Subject", "consolidation:Scientist": "consolidation:Agent"}[definition]
        return [self.result("actual:DifferentParent" if self.defect == "divergence" else parent)]
    def children(self, definition):
        return [self.result({"consolidation:Subject": "consolidation:Region", "consolidation:Agent": "consolidation:Scientist"}[definition])]
    def is_a(self, first, second):
        return first in ("consolidation:Region", "consolidation:Scientist")
    def satisfiable(self, definition):
        return True


@pytest.mark.parametrize("defect", [None, "divergence", "read-failure"])
def test_capture_persists_actuals_before_comparison_and_never_promotes_acceptance(defect):
    snapshots = []
    save = lambda report: snapshots.append(json.loads(json.dumps(report)))
    if defect == "read-failure":
        with pytest.raises(AssertionError, match="incomplete"):
            collect(Explorer(defect), lambda route: {"supplied": route}, save)
        result = snapshots[-1]
        assert not result["passed"] and len(result["read_failures"]) == 2
        assert "private error" not in json.dumps(snapshots)
    else:
        result = collect(Explorer(defect), lambda route: {"supplied": route}, save)
        assert result["passed"]
        assert all(pair["assessment"] == ("OBSERVED_DIVERGENCE" if defect else "OBSERVED_SOURCE_MATCH")
            for pair in result["pairs"])
    assert len(result["records"]) == 20 and result["hierarchy_acceptance"] == "NOT_CLAIMED"
    early = next(s for s in snapshots if any(row["label"].endswith(":parents") for row in s["records"]))
    assert early["pairs"] == []
    assert result["records"][2]["value"]["knowledge"]["revision"] is None
    if defect == "divergence":
        assert result["pairs"][0]["actual_parents"] == ["actual:DifferentParent"]


@pytest.mark.parametrize("method,path,allowed", [
    ("GET", "/reasoner/public/status", True), ("POST", "/reasoner/api/v1/parents", True),
    ("POST", "/reasoner/api/v1/subsumes", True), ("POST", "/reasoner/api/v1/publish", False),
    ("POST", "/runtime/api/v1/createContext", False), ("POST", "/runtime/api/v1/submit", False),
    ("DELETE", "/reasoner/api/v1/parents", False), ("GET", "/foreign/public/status", False)])
def test_diagnostic_strict_read_only_query_route_allowlist(method, path, allowed):
    assert allowed_call(method, path) is allowed
