"""Pinned real-input QA only; NOT a client/Java scientific acceptance test.

Opt in with -m live and KLAB_PAL_INPUT_DIR; no network or service writes.
Missing fixtures fail explicitly rather than silently passing scientific gates.
"""
from collections import Counter
import hashlib
import json
import math
import os
from pathlib import Path

import pytest

pytestmark = [pytest.mark.live, pytest.mark.functional]
RECEIPT = Path(__file__).resolve().parents[2] / "docs/research/pal/assumed-input-receipts.json"


def pinned_file(root, name, expected):
    path = root / name
    assert path.is_file(), f"Missing pinned PaL input: {name}"
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    assert digest.hexdigest() == expected, f"Changed/unverified PaL input: {name}"
    return path


@pytest.mark.parametrize("patch_id", ["equal_patch", "mixed_patch"])
def test_pinned_real_inputs_reproduce_native_center_qa(patch_id, record_property):
    directory = os.environ.get("KLAB_PAL_INPUT_DIR")
    assert directory, "Set KLAB_PAL_INPUT_DIR to the explicitly prepared pinned-file directory"
    # Optional analysis dependencies are required, not skipped, for this selected suite.
    import numpy as np
    import rasterio
    from rasterio.windows import Window, from_bounds

    receipt = json.loads(RECEIPT.read_text(encoding="utf-8"))
    patch = next(p for p in receipt["patches"] if p["id"] == patch_id)
    root = Path(directory)
    sources = {p["name"]: pinned_file(root, p["name"], p["sha256"])
               for p in receipt["qa_source_inputs"]}
    radiation = pinned_file(root, f"radiation-{patch_id}.tif", patch["sha256"])
    record_property("claim", "REAL_INPUT_PREREQUISITE_QA_NOT_JAVA_SCIENTIFIC_ACCEPTANCE")
    record_property("patch", patch_id)
    record_property("radiation_sha256", patch["sha256"])
    with (rasterio.open(sources["com_50m_wgs84.tif"]) as main,
          rasterio.open(sources["com2_50m_wgs84.tif"]) as over,
          rasterio.open(radiation) as rad):
        assert str(main.crs) == str(over.crs) == str(rad.crs) == patch["crs"]
        assert list(rad.shape) == patch["shape"]
        assert list(rad.bounds) == patch["bounds"]
        assert list(rad.transform)[:6] == patch["transform"]
        assert rad.count == 1 and rad.dtypes == (patch["dtype"],)
        assert rad.nodata == patch["nodata"] and rad.units == (patch["tiff_units"],)
        values = rad.read(1, masked=True)
        assert values.count() == patch["raw_valid_pixels"]
        assert values.size - values.count() == patch["raw_missing_pixels"]
        assert np.isfinite(values.compressed()).all()
        assert np.count_nonzero(values.compressed() == 0) == patch["raw_valid_zero_pixels"]
        bounds = rad.bounds
        window = from_bounds(*bounds, transform=main.transform)
        r0, c0 = max(0, math.floor(window.row_off)), max(0, math.floor(window.col_off))
        r1 = min(main.height, math.ceil(window.row_off + window.height))
        c1 = min(main.width, math.ceil(window.col_off + window.width))
        assert 0 < (r1 - r0) * (c1 - c0) <= 1000, "Unexpected QA window size"
        counts, pairs = Counter(), Counter()
        for row in range(r0, r1):
            for col in range(c0, c1):
                x, y = main.xy(row, col)
                if not (bounds.left <= x < bounds.right and bounds.bottom < y <= bounds.top):
                    continue
                counts["centers"] += 1
                mv = main.read(1, window=Window(col, row, 1, 1), masked=True)[0, 0]
                ov = next(over.sample([(x, y)], masked=True))[0]
                rv = next(rad.sample([(x, y)], masked=True))[0]
                if any(np.ma.is_masked(v) for v in (mv, ov, rv)):
                    counts["missing"] += 1
                    continue
                counts["joint_valid"] += 1
                counts["equal" if mv == ov else "different"] += 1
                pairs[f"{int(mv)}/{int(ov)}"] += 1
        assert {key: counts[key] for key in patch["main_center_input_qa"]} == patch["main_center_input_qa"]
        assert dict(pairs) == patch["raw_main_overstory_pair_counts"]
