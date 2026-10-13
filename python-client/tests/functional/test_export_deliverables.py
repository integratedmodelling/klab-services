"""User outcome: scientifically verified GIS deliverables from saved work."""
import json

import pytest

from workflows.geojson_export import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_native_geojson_export_preserves_identity_and_known_geometry(functional_state, record_property):
    record_property("native_export", json.dumps(run(functional_state)))


def test_native_geotiff_export_preserves_values_units_and_mask(functional_state, record_property):
    from argparse import Namespace
    from uuid import uuid4
    from workflows.export_deliverables import run as run_geotiff
    report = functional_state / ("functional-geotiff-" + uuid4().hex + ".json")
    args = Namespace(fixture_config=functional_state / "scientist.json",
        server_state_dir=functional_state, work_dir=functional_state, report=report,
        samples=1, duration=0, warmup=0, mode="full-roundtrip", concurrency=1)
    assert run_geotiff(args) == 0, report.read_text()
    evidence = json.loads(report.read_text())
    assert evidence["summary"]["verified_samples"] == 1 and evidence["session_cleanup"]
    record_property("native_geotiff", json.dumps(evidence))
