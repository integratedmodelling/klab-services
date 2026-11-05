"""Independent native-file and installed-reader oracle; no exporter/algorithm replacement."""
import hashlib
import json
import math
from pathlib import Path
import shutil

from klab_client import ContextOptions, ObservationRequest, RectangularGeometry, open_raster, fetch_execution_evidence
from klab_client.errors import SubmissionOutcomeUnknown
from .request_plan import model_urns

ORACLE = Path(__file__).resolve().parents[2] / "tests/fixtures/nonuniform-export.json"


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify(path, *, oracle_path=ORACLE):
    import numpy as np
    import rasterio
    expected = json.loads(oracle_path.read_text(encoding="utf-8"))
    before = digest(path)
    table = expected["north_first"]
    valid = np.array([[cell is not None for cell in row] for row in table], dtype=bool)
    values = np.array([[float("nan") if cell is None else cell for cell in row] for row in table])
    def require(condition, message):
        if not condition:
            raise AssertionError(message)
    with rasterio.Env(GDAL_PAM_ENABLED="NO", GDAL_DISABLE_READDIR_ON_OPEN="EMPTY_DIR",
                      GDAL_GEOREF_SOURCES="INTERNAL"), rasterio.open(path, driver="GTiff") as native:
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
    require(digest(path) == before, "Oracle/reader modified native bytes")
    return {"cells": int(valid.size), "missing": int((~valid).sum()), "zero": expected["valid_zero_cells"],
            "sha256": before, "oracle_sha256": digest(oracle_path),
            "multi_shard": "NOT_PROVEN", "revision_consistency": "UNKNOWN"}


def negative_metadata(path, state):
    """L2 rejection of controlled copies; never repair or modify native Java bytes."""
    import rasterio
    original = digest(path)
    rejected = []
    for name in ("missing-units", "wrong-units", "bad-crs", "missing-nodata"):
        copy = state / (name + ".tif")
        shutil.copyfile(path, copy)
        with rasterio.open(copy, "r+") as target:
            if name == "missing-units":
                target.set_band_unit(1, "")
            elif name == "wrong-units":
                target.set_band_unit(1, "mm")
            elif name == "bad-crs":
                target.crs = "EPSG:4326"
            else:
                target.nodata = None
        try:
            verify(copy)
        except AssertionError:
            rejected.append(name)
        else:
            raise AssertionError("Invalid controlled metadata was silently accepted")
    if digest(path) != original:
        raise AssertionError("Negative controls changed original Java artifact")
    return {"tier": "L2", "rejected": rejected, "original_unchanged": True}


def run(client, state):
    session = context = None
    ids = ("n-values", "n-geography", "n-model-input", "n-metadata-negative", "n-cleanup")
    result = {"expectations": {key: {"status": "NOT_RUN", "reason": "Not reached"} for key in ids}}
    primary = None
    current = "n-values"
    def passed(key, reason):
        result["expectations"][key] = {"status": "PASS", "reason": reason}
    try:
        session = client.create_session(name="owned-nonuniform-export-oracle")
        context = session.create_context(options=ContextOptions(name="single-shard synthetic oracle", persistence="EXPLICIT_ACTION"))
        region = context.observe(ObservationRequest("consolidation:Region",
            geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
        observation = context.within(region).observe("consolidation:Elevation of consolidation:Region in m").result(120)
        expected = json.loads(ORACLE.read_text(encoding="utf-8"))
        read = observation.fetch_data(expected["native_offsets"], curve="D2_YX")
        if (read.units != "m" or read.offsets != tuple(expected["native_offsets"])
                or [None if value is None else float(value) for value in read.values] != expected["native_values"]):
            raise AssertionError("Independent ordered native offsets differ")
        passed("n-values", "Exact independent native offsets/values/units; valid zero distinct from null")
        current = "n-model-input"
        evidence = fetch_execution_evidence(observation)
        urns = model_urns(evidence)
        if (not any(u.startswith(("consolidation.fixture.", "consolidation.fixture:")) for u in urns)
                or evidence.provenance.observation != observation.to_handle()):
            raise AssertionError("Selected model or request-associated observation differs")
        result.update(selected_models=urns, observation=observation.to_handle().to_dict(),
            activity_ids=[a.id for a in evidence.activities], plan_partial=evidence.partial,
            durable_root_commit=evidence.durable_root_commit,
            input_source="unchanged NonuniformExportFixture.java; exact test source/class and model hashes in harness receipt")
        passed("n-model-input", "Successful recorded plan models associated to exact requested observation handle")
        current = "n-geography"
        path = state / "nonuniform.tif"
        artifact = observation.export_to_file(path, media_type="image/tiff;application=geotiff", max_bytes=1024 * 1024)
        result.update(verify(path), bytes=artifact.bytes_written, schema=artifact.schema_id,
                       exporter_formats=[format.media_type for format in client.export_formats(observation)])
        passed("n-geography", "Independent north-first matrix, CRS/edges/centres/orientation/float64/masks/units match native TIFF")
        current = "n-metadata-negative"
        result["metadata_negative"] = negative_metadata(path, state)
        passed("n-metadata-negative", "L2 controlled copies reject missing/wrong units, bad CRS, missing nodata; native bytes unchanged")
        return result
    except BaseException as error:
        primary = error
        result["error_type"] = type(error).__name__
        result["expectations"][current] = {"status": "FAIL", "reason": type(error).__name__}
        raise
    finally:
        failures = []
        for label, owned in (("context", context), ("session", session)):
            if owned is not None and not isinstance(primary, SubmissionOutcomeUnknown):
                try:
                    if owned.release() is not True:
                        failures.append(label)
                except Exception:
                    failures.append(label)
        result["owned_releases_confirmed"] = not failures and not isinstance(primary, SubmissionOutcomeUnknown)
        result["expectations"]["n-cleanup"] = {"status": "PASS" if result["owned_releases_confirmed"] else "FAIL",
            "reason": "Owned scopes released" if result["owned_releases_confirmed"] else "Unknown outcome or cleanup unconfirmed; no replay"}
        success = primary is None and not failures
        result["axes"] = {axis: {"status": "PASS" if success else "NOT_RUN",
            "reason": "Bounded synthetic Java numerical use case; not provider accuracy" if success else "Inspect per-check failure; axis not certified"}
            for axis in ("python", "java_contract", "scientific", "security_lifecycle", "environment_artifacts")}
        (state / "nonuniform-science.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if failures:
            if primary is not None:
                primary.add_note("Owned oracle cleanup unconfirmed")
            else:
                raise RuntimeError("Owned oracle cleanup unconfirmed")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.path), indent=2))
