"""Reinspect pinned public native bytes; never a new Java runtime acceptance run."""
import base64
import hashlib
import json
from pathlib import Path

from rasterio.io import MemoryFile


def test_historical_native_unit_defect_is_preserved_without_rewriting_values_or_bytes():
    path = Path(__file__).resolve().parents[1] / "fixtures/native-geotiff-missing-unit.json"
    capture = json.loads(path.read_text())
    payload = base64.b64decode(capture["base64"], validate=True)
    assert hashlib.sha256(payload).hexdigest() == capture["sha256"]
    assert capture["kind"] == "HISTORICAL_NATIVE_ARTIFACT_NOT_FRESH_JAVA_EXECUTION"
    with MemoryFile(payload) as memory:
        with memory.open() as dataset:
            assert (dataset.width, dataset.height, dataset.count) == (5, 4, 1)
            assert dataset.crs.to_epsg() == 3857
            assert tuple(dataset.transform)[:6] == (100, 0, 200000, 0, -100, 6000400)
            assert dataset.dtypes == ("float32",)
            assert dataset.read(1).ravel().tolist() == [capture["independent_constant_value"]] * 20
            assert dataset.units == (capture["observed_measurement_unit"],)
            assert dataset.units != (capture["expected_measurement_unit"],)  # Known historical REAL_FAIL remains.
            assert dataset.tags(1) == {}
    assert hashlib.sha256(payload).hexdigest() == capture["sha256"]
