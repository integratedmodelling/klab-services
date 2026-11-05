import pytest

from workflows.native_stream_workload import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_bounded_actual_java_native_stream_and_interruption_safety(functional_state):
    assert run(functional_state)["passed"]
