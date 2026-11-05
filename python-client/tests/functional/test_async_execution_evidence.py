import pytest

from workflows.async_execution_evidence import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_async_original_work_activity_and_plan_inspection(functional_state):
    assert run(functional_state)["passed"]
