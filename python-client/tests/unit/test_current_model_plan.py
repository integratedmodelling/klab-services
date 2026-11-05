from types import SimpleNamespace

import pytest

from workflows.current_model_plan import select_candidate, verify_result


def model(urn="consolidation.scenario.example", **changes):
    return SimpleNamespace(**({"urn": urn, "origin": "owned-resources", "namespace": None,
        "project_name": None, "version": None, "raw": {},
        "selection": "advertised candidate; not evidence of Resolver selection or execution",
        "dependency_completeness": "unknown: listed metadata only; not a dependency closure"} | changes))


def test_selects_unique_canonical_namespace_not_first_or_label_without_inventing_metadata():
    selected = model()
    inspector = SimpleNamespace(list=lambda kind: [model("unrelated"), selected], retrieve=lambda urn, kind: selected)
    found, listed = select_candidate(inspector)
    assert found is selected and len(listed) == 2 and found.version is None


@pytest.mark.parametrize("defect", ["none", "duplicate", "origin", "identity", "metadata", "selection"])
def test_unproved_ambiguous_or_invented_candidate_does_not_pass(defect):
    listed = [] if defect == "none" else [model(), model()] if defect == "duplicate" else [model()]
    selected = model()
    if defect == "origin":
        selected.origin = "foreign"
    if defect == "identity":
        selected.urn = "another"
    if defect == "metadata":
        selected.version = "invented"
    if defect == "selection":
        selected.selection = "guaranteed executable"
    with pytest.raises(AssertionError):
        select_candidate(SimpleNamespace(list=lambda kind: listed, retrieve=lambda urn, kind: selected))


def evidence(urn, outcome="SUCCESS", phase="resolution plan"):
    node = SimpleNamespace(raw={"metadata": {"kind": "model", "urn": urn}})
    return SimpleNamespace(activities=[SimpleNamespace(outcome=outcome,
        charts=[SimpleNamespace(phase=phase, nodes=[node])])], partial=True)


def test_successful_recorded_plan_identity_and_independent_cells_are_separate_gates():
    candidate = model()
    assert verify_result(candidate, evidence(candidate.urn), "m", (234.5,) * 20) == [candidate.urn]


@pytest.mark.parametrize("defect", ["other-model", "failed", "not-plan", "wrong-values", "wrong-unit", "wrong-count"])
def test_request_or_advertisement_alone_never_proves_result_selection(defect):
    candidate = model()
    result = evidence("unrelated" if defect == "other-model" else candidate.urn,
        outcome="FAILED" if defect == "failed" else "SUCCESS", phase="request" if defect == "not-plan" else "resolution plan")
    values = (1.0,) * 20 if defect == "wrong-values" else (234.5,) * (19 if defect == "wrong-count" else 20)
    with pytest.raises(AssertionError):
        verify_result(candidate, result, "cm" if defect == "wrong-unit" else "m", values)
