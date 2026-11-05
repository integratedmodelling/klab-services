import pytest

from workflows.async_source_previews import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_async_actual_java_four_family_source_previews_are_non_authoring(functional_state):
    assert run(functional_state)["passed"]
