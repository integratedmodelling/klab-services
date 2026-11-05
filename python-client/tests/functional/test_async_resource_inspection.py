import pytest

from workflows.async_resource_inspection import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_async_actual_java_model_inspection_is_read_only(functional_state):
    assert run(functional_state)["passed"]
