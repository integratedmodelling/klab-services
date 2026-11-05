import pytest

from workflows.model_source_authoring import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_owned_model_containing_namespace_source_and_advertised_identity(functional_state):
    assert run(functional_state)["passed"]
