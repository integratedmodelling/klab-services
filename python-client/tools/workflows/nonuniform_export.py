"""Independent native-file and installed-reader oracle; no exporter/algorithm replacement."""
import hashlib
import json
import math
from pathlib import Path

from klab_client import ContextOptions, ObservationRequest, RectangularGeometry, open_raster

ORACLE = Path(__file__).resolve().parents[2] / "tests/fixtures/nonuniform-export.json"


def verify(path, *, oracle_path=ORACLE):
    import numpy as np
    import rasterio
    expected = json.loads(oracle_path.read_text(encoding="utf-8"))
    digest = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    table = expected["north_first"]
    valid = np.array([[cell is not None for cell in row] for row in table], dtype=bool)
    values = np.array([[float("nan") if cell is None else cell for cell in row] for row in table])
    def require(condition, message):
        if not condition:
            raise AssertionError(message)
    with rasterio.open(path) as native:
        require((native.height, native.width) == tuple(expected["shape"]) and native.count == 1, "Native dimensions differ")
        require(native.crs is not None and native.crs.to_epsg() == expected["crs_epsg"], "Native CRS differs")
        require(native.dtypes == (expected["dtype"],), "Native precision differs")
        require(tuple(native.transform)[:6] == tuple(expected["transform"]), "Native orientation/transform differs")
        require(native.units == (expected["units"],), "Native units differ")
        require(native.nodata is not None and math.isnan(native.nodata), "Native missingness tag differs")
        require(np.array_equal(native.read_masks(1) != 0, valid), "Native masks differ")
        require(np.array_equal(native.read(1)[valid], values[valid]), "Native values differ")
        for row in range(native.height):
            for column in range(native.width):
                require(native.xy(row, column) == (expected["x"][column], expected["y"][row]), "Native coordinates differ")
    analysis = open_raster(path)
    require(analysis.descriptor.shape == tuple(expected["shape"]), "Reader dimensions differ")
    require(analysis.descriptor.units == expected["units"], "Reader units differ")
    require(np.array_equal(analysis.dataset.x, expected["x"]) and np.array_equal(analysis.dataset.y, expected["y"]), "Reader coordinates differ")
    require(np.array_equal(analysis.dataset.valid.values, valid), "Reader validity differs")
    require(np.array_equal(analysis.dataset.value.values[valid], values[valid]), "Reader values/precision differ")
    require(int((analysis.dataset.valid & (analysis.dataset.value == 0)).sum()) == expected["valid_zero_cells"], "Valid zero count differs")
    require(int((~analysis.dataset.valid).sum()) == expected["missing_cells"], "Missing count differs")
    require(hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, "Oracle/reader modified native bytes")
    return {"cells": int(valid.size), "missing": int((~valid).sum()), "zero": expected["valid_zero_cells"],
            "sha256": digest, "oracle_sha256": hashlib.sha256(oracle_path.read_bytes()).hexdigest(),
            "multi_shard": "NOT_PROVEN", "revision_consistency": "UNKNOWN"}


def run(client, state):
    session = context = None
    result = {}
    try:
        session = client.create_session(name="owned-nonuniform-export-oracle")
        context = session.create_context(options=ContextOptions(name="single-shard synthetic oracle", persistence="EXPLICIT_ACTION"))
        region = context.observe(ObservationRequest("consolidation:Region",
            geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
        observation = context.within(region).observe("consolidation:Elevation of consolidation:Region in m").result(120)
        expected = json.loads(ORACLE.read_text(encoding="utf-8"))
        read = observation.fetch_data(expected["native_offsets"], curve="D2_YX")
        if [None if value is None else float(value) for value in read.values] != expected["native_values"]:
            raise AssertionError("Independent ordered native offsets differ")
        path = state / "nonuniform.tif"
        artifact = observation.export_to_file(path, media_type="image/tiff;application=geotiff", max_bytes=1024 * 1024)
        result.update(verify(path), bytes=artifact.bytes_written, schema=artifact.schema_id,
                      exporter_formats=[format.media_type for format in client.export_formats(observation)])
        return result
    finally:
        import sys
        primary = sys.exc_info()[1]
        failures = []
        for label, owned in (("context", context), ("session", session)):
            if owned is not None:
                try:
                    if owned.release() is not True:
                        failures.append(label)
                except Exception:
                    failures.append(label)
        if failures:
            if primary is not None:
                primary.add_note("Owned oracle cleanup unconfirmed")
            else:
                raise RuntimeError("Owned oracle cleanup unconfirmed")
        result["owned_releases_confirmed"] = not failures


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.path), indent=2))
