import pytest

from workflows.native_codelist_retrieval import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_actual_java_codebook_metadata_is_retrieval_only(functional_state):
    assert run(functional_state)["passed"]
