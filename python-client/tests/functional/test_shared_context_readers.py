import pytest

from workflows.shared_context_readers import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_acl_authorized_concurrent_readers_preserve_original_owner_work(functional_state):
    assert run(functional_state)["passed"]
