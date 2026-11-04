"""Independent chart selection vectors, not real-Java scientific acceptance."""
from types import SimpleNamespace as N

from workflows.request_plan import model_urns


def activity(outcome="SUCCESS", phase="resolution plan", metadata=None):
    return N(outcome=outcome, charts=[N(phase=phase, nodes=[N(raw={"metadata": metadata or {
        "kind": "model", "urn": "expected.namespace.model"}})])])


def test_only_successful_recorded_resolution_models_count():
    evidence = N(activities=[activity(), activity(), activity(outcome="FAILED"),
        activity(phase="contextualization plan"), activity(metadata={"kind": "observation", "urn": "not-a-model"})])
    assert model_urns(evidence) == ["expected.namespace.model"]


def test_absent_or_non_successful_plan_is_not_proof_of_selection():
    assert model_urns(N(activities=[])) == []
    assert model_urns(N(activities=[activity(outcome="UNKNOWN")])) == []
    assert model_urns(N(activities=[activity(phase="contextualization plan")])) == []
