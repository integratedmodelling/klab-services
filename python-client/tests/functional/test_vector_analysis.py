import json

import pytest

from workflows.vector_analysis import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_installed_vector_analysis_preserves_native_java_geometry(functional_state):
    report = run(functional_state)
    (functional_state / "vector-analysis.json").write_text(json.dumps(report, indent=2))
    assert report["passed"]
