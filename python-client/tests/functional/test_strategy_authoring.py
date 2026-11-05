import pytest

from workflows.strategy_authoring import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_owned_strategy_draft_source_permissions_and_cleanup(functional_state):
    assert run(functional_state)["passed"]
