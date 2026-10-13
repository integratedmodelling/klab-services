"""Prevent suite routing and compatibility commands drifting from shared scenarios."""
import ast
from pathlib import Path

import pytest

from run_functional_acceptance import SUITES, command


@pytest.mark.parametrize("suite", tuple(SUITES))
def test_named_suite_selects_existing_functional_test(suite):
    location, *node = SUITES[suite].split("::")
    file = Path(__file__).parents[2] / location
    tree = ast.parse(file.read_text())
    assert file.parent.name == "functional"
    if node:
        assert any(isinstance(item, ast.FunctionDef) and item.name == node[0] for item in tree.body)
    assert location in command([suite]) or SUITES[suite] in command([suite])


def test_old_commands_use_the_same_scenarios_as_functional_acceptance():
    import geotiff_throughput
    import workflow_benchmark
    from workflows import compute_and_analyze, export_deliverables
    assert workflow_benchmark.sample is compute_and_analyze.sample
    assert workflow_benchmark.read_sample is compute_and_analyze.read_sample
    assert geotiff_throughput.run is export_deliverables.run
    assert geotiff_throughput.validate_dataset is export_deliverables.validate_dataset
