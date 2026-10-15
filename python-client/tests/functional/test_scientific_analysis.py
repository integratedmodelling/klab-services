"""Actual owned compute -> restore -> raster analysis -> recorded lineage, not mocked HTTP."""
import json

import pytest

from workflows.scientific_analysis import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_scientific_analysis_and_recorded_origins(functional_state, record_property):
    evidence = run(functional_state)
    assert evidence["verified_cells"] == 20 and evidence["partial"]
    assert evidence["recorded_links"] > 0 and evidence["consistency"].startswith("unknown")
    record_property("scientific_analysis_provenance", json.dumps(evidence))


def test_release_notebook_journey_uses_connections_and_recovers_the_original_field(functional_state, record_property):
    evidence = run(functional_state, connection=True)
    assert evidence["verified_cells"] == 20 and evidence["valid_sum"] == 409
    assert evidence["connection_path"] == "certificate to issued-token fresh-process recovery"
    assert evidence["valid_nonowner_positive_control"]
    assert evidence["unauthorized_export"] == evidence["unauthorized_provenance"] == "AuthorizationError"
    assert evidence["recorded_relationships"] and evidence["partial"]
    record_property("release_notebook_workflow", json.dumps(evidence))
