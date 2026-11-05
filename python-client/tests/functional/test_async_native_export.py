import pytest

from workflows.async_native_export import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_actual_java_geojson_and_substantial_native_async_stream(functional_state):
    assert run(functional_state)["passed"]
