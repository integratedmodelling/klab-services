import pytest

from workflows.canonical_hierarchy import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_source_defined_canonical_core_alias_hierarchy_and_identity(functional_state):
    assert run(functional_state)["passed"]
