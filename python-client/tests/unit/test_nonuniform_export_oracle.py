"""Oracle rejection tests use native files, not claimed live Java acceptance."""
import json
from pathlib import Path

import numpy as np
import pytest
import rasterio
from rasterio.transform import Affine

from workflows.nonuniform_export import verify


def write(tmp_path, *, change=None, units="m", **options):
    data = np.array([[16777217.125, 11, np.nan, 31, 41], [10.125, -9, 12.5, 22, -8],
                     [-2.5, np.nan, 4, -.125, 9], [0, 2.25, -100, 3, 5]], dtype="float64")
    if change:
        data = change(data)
    config = dict(driver="GTiff", width=data.shape[1], height=data.shape[0], count=1, dtype=data.dtype,
        crs="EPSG:3857", transform=Affine(100, 0, 200000, 0, -100, 6000400), nodata=np.nan)
    config.update(options)
    path = tmp_path / "native.tif"
    with rasterio.open(path, "w", **config) as target:
        target.write(data, 1)
        if units:
            target.set_band_unit(1, units)
    return path


def test_asymmetric_independent_known_values_masks_and_coordinates(tmp_path):
    path = write(tmp_path)
    result = verify(path)
    assert result["cells"] == 20 and result["missing"] == 2 and result["zero"] == 1
    assert result["multi_shard"] == "NOT_PROVEN" and result["revision_consistency"] == "UNKNOWN"
    assert len(result["sha256"]) == len(result["oracle_sha256"]) == 64


@pytest.mark.parametrize("change", [lambda a: a[::-1], lambda a: a[:, ::-1],
    lambda a: np.nan_to_num(a), lambda a: a.astype("float32").astype("float64"),
    lambda a: a[:3], lambda a: a + 1])
def test_wrong_orientation_masks_precision_shape_and_values_reject(tmp_path, change):
    with pytest.raises(AssertionError):
        verify(write(tmp_path, change=change))


@pytest.mark.parametrize("options", [{"crs": "EPSG:4326"}, {"units": None}, {"units": "mm"},
    {"nodata": None}, {"transform": Affine(100, 0, 200000, 0, 100, 6000400)}, {"count": 2}])
def test_inconsistent_scientific_metadata_rejects(tmp_path, options):
    with pytest.raises(AssertionError):
        verify(write(tmp_path, **options))


def test_native_point_oracle_is_separate_and_zero_not_null():
    oracle = json.loads((Path(__file__).parents[1] / "fixtures/nonuniform-export.json").read_text())
    assert oracle["native_values"] == [0, 5, None, -.125, 12.5, 16777217.125, 11, None, 41, 0]
    assert oracle["north_first"][3][0] == 0 and oracle["north_first"][0][2] is None
