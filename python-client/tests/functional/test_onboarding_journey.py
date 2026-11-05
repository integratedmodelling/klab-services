import pytest

from workflows.onboarding_journey import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_current_tuple_connection_and_fresh_process_journey(functional_state):
    assert run(functional_state)["passed"]
