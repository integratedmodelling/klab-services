"""Opt-in Python/Java interoperability and owned Runtime restart acceptance."""
import json

import pytest

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_reasoner_publishes_accepted_semantic_knowledge(functional_state, record_property):
    from workflows.develop_compatibility import require_semantic_readiness
    report = require_semantic_readiness(functional_state)
    record_property("semantic_readiness", json.dumps(report))


def test_python_and_java_services_work_before_and_after_runtime_restart(functional_state, record_property):
    from workflows.develop_compatibility import verify
    report = verify(functional_state)
    assert report["passed"]
    assert report["before"]["verified_cells"] == report["after"]["verified_cells"] == 40
    assert report["runtime_pid_before"] != report["runtime_pid_after"]
    record_property("develop_compatibility", json.dumps(report))
