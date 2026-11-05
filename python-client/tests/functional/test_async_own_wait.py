import pytest

from workflows.async_own_wait import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_current_async_own_wait_leaves_original_job_and_other_wait_intact(functional_state):
    assert run(functional_state)["passed"]
