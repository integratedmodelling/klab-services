import pytest

from workflows.deleted_state_recovery import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_deleted_state_listing_witness_and_survivor_recovery_without_replay(functional_state):
    assert run(functional_state)["passed"]
