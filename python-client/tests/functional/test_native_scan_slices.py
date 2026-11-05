import pytest

from workflows.native_scan_slices import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_typed_initialization_matches_actual_java_values_and_dict_metadata(functional_state):
    assert run(functional_state)["passed"]
