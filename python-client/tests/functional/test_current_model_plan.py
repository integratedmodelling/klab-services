import pytest

from workflows.current_model_plan import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_canonical_advertised_model_matches_original_successful_served_plan(functional_state):
    assert run(functional_state)["passed"]
