import pytest

from workflows.hierarchy_diagnostic import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_bounded_reasoner_snapshot_completion_not_hierarchy_acceptance(functional_state):
    report = run(functional_state)
    assert report["passed"] and report["hierarchy_acceptance"] == "NOT_CLAIMED"
