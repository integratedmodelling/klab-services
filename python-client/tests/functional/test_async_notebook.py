import pytest

from workflows.async_notebook import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_real_installed_async_notebook_java_journey_and_kernel_cleanup(functional_state):
    assert run(functional_state)["passed"]
