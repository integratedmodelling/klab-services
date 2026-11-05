import pytest

from workflows.async_recorded_provenance import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_async_original_work_recorded_neighborhood_remains_partial(functional_state):
    assert run(functional_state)["passed"]
