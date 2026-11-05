import pytest

from workflows.read_semantics_isolation import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_java_read_semantics_remain_stable_when_caller_mutates_request(functional_state):
    assert run(functional_state)["passed"]
