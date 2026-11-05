import pytest

from workflows.async_handle_restoration import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_original_saved_handles_restored_natively_in_fresh_async_process(functional_state):
    assert run(functional_state)["passed"]
