"""User outcome: resume the original work in another authorized process."""
import json

import pytest

from workflows.resume_saved_work import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_real_saved_handles_between_fresh_python_processes(functional_state, record_property):
    record_property("saved_handles", json.dumps(run(functional_state)))
