"""Meaningful scientific rejection and rate-gating checks without an optional core dependency."""
import importlib.util
import math
from pathlib import Path
import sys

import pytest


def benchmark():
    tools = Path(__file__).parents[2] / "tools"
    sys.path.insert(0, str(tools))
    try:
        spec = importlib.util.spec_from_file_location("geotiff_benchmark", tools / "geotiff_throughput.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        sys.path.remove(str(tools))


class Raster:
    width, height, count = 5, 4, 1
    transform = (100, 0, 200000, 0, -100, 6000400, 0, 0, 1)
    bounds = (200000, 6000000, 200500, 6000400)
    dtypes = ("float32",)
    units = (None,)
    nodata = math.nan
    class CRS:
        def to_epsg(self):
            return 3857
    crs = CRS()

    def __init__(self):
        # North-to-south rows, independently written expected values.
        self.values = [[3, 13, 23, 33, 43], [2, 12, 22, 32, 42],
            [1, 11, math.nan, 31, 41], [0, 10, 20, 30, 40]]
        self.masks = [[255] * 5 for _ in range(4)]
        self.masks[2][2] = 0

    def xy(self, row, column):
        return 200050 + column * 100, 6000350 - row * 100

    def read(self, band):
        assert band == 1
        return self.values

    def read_masks(self, band):
        assert band == 1
        return self.masks

    def tags(self):
        return {"AREA_OR_POINT": "Area"}


def test_reference_raster_checks_world_mapping_zero_missingness_and_absent_units():
    result = benchmark().validate_dataset(Raster())
    assert result["verified_cells"] == 20 and result["valid_zero_cells"] == result["missing_nan_cells"] == 1
    assert result["mask_checked"] and result["file_unit"] is None and result["nodata_tag"] == "NaN"


@pytest.mark.parametrize("corruption", ["flip", "zero", "missing", "mask", "transform", "crs", "shape", "dtype", "center"])
def test_corrupt_scientific_result_cannot_be_verified(corruption):
    raster = Raster()
    if corruption == "flip":
        raster.values.reverse()
    elif corruption == "zero":
        raster.values[3][0] = math.nan
    elif corruption == "missing":
        raster.values[2][2] = 0
    elif corruption == "mask":
        raster.masks[3][0] = 0
    elif corruption == "transform":
        raster.transform = (100, 0, 200000, 0, 100, 6000000)
    elif corruption == "crs":
        raster.crs = None
    elif corruption == "shape":
        raster.width = 4
    elif corruption == "dtype":
        raster.dtypes = ("uint8",)
    else:
        raster.xy = lambda row, column: (0, 0)
    with pytest.raises(AssertionError):
        benchmark().validate_dataset(raster)


def test_missing_nodata_metadata_fails_corrected_exporter_contract():
    raster = Raster()
    raster.nodata = None
    raster.masks = [[255] * 5 for _ in range(4)]
    with pytest.raises(AssertionError, match="advertise NaN nodata"):
        benchmark().validate_dataset(raster)


@pytest.mark.parametrize("file_unit", [None, "m", "degree_angle"])
def test_source_measurement_unit_must_survive_export(file_unit):
    raster = Raster()
    raster.units = (file_unit,)
    if file_unit == "degree_angle":
        assert benchmark().validate_dataset(raster, "degree_angle")["file_unit"] == file_unit
    else:
        with pytest.raises(AssertionError, match="measurement unit"):
            benchmark().validate_dataset(raster, "degree_angle")


def test_failure_invalidates_workflow_cell_and_byte_rates():
    records = [{"outcome": "verified", "elapsed_seconds": 1, "verified_cells": 20, "exported_bytes": 1000},
        {"outcome": "failed", "elapsed_seconds": 2}]
    result = benchmark().summary(records, 3)
    assert not result["acceptance_passed"]
    assert not any(key.endswith("per_second") or key.endswith("per_wall_second") for key in result)


def test_rates_use_actual_workflow_wall_interval_not_export_stage_sum():
    records = [{"outcome": "verified", "elapsed_seconds": 1, "verified_cells": 20, "exported_bytes": 1000} for _ in range(2)]
    result = benchmark().summary(records, 4)
    assert result["verified_workflows_per_second"] == .5
    assert result["verified_cells_per_second"] == 10 and result["verified_exported_bytes_per_wall_second"] == 500
