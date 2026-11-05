import pytest

from workflows.async_semantic_exploration import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_async_actual_java_semantic_inspection_is_non_computing(functional_state):
    assert run(functional_state)["passed"]
