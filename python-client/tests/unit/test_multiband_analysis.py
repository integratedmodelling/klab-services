"""SIMULATED_PROVISIONAL/JAVA-GAP-0014: caller meaning, not native committed history."""
import pytest

pytest.importorskip("numpy", reason="Optional analysis extra")
pytest.importorskip("rasterio", reason="Optional analysis extra")
pytest.importorskip("xarray", reason="Optional analysis extra")

from klab_client import RasterBandAxis, open_raster
from klab_client.errors import InvalidRequestError, UnsupportedOperationError


def raster(tmp_path):
    import numpy as np
    import rasterio
    from rasterio.transform import from_origin
    path = tmp_path / "declared.tif"
    values = np.array([[[0, -2, -9999], [4, 5, 6]], [[10, 12, 14], [-9999, 18, 20]], [[20, 24, 28], [32, 36, 40]]], dtype="float32")
    with rasterio.open(path, "w", driver="GTiff", height=2, width=3, count=3,
            dtype="float32", crs="EPSG:3857", transform=from_origin(0, 200, 100, 100), nodata=-9999) as target:
        target.write(values)
        for band in (1, 2, 3):
            target.set_band_unit(band, "m")
    return path, values


def test_explicit_axis_retains_every_band_mask_units_and_layout(tmp_path):
    import numpy as np
    path, expected = raster(tmp_path)
    axis = RasterBandAxis("time", ("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z"), "Synthetic caller declaration, not committed native time")
    result = open_raster(path, band_axis=axis)
    assert result.descriptor.shape == (3, 2, 3) and result.descriptor.band_axis == axis
    assert result.dataset.value.dims == ("time", "y", "x")
    np.testing.assert_array_equal(result.dataset.value.values, expected)
    np.testing.assert_array_equal(result.dataset.valid.values, expected != -9999)
    assert result.dataset.value.attrs["units"] == "m"
    assert result.dataset.value.attrs["band_axis_source"] == "CALLER_DECLARED"
    assert result.dataset.coords["time"].values.tolist() == list(axis.coordinates)


def test_unselected_or_mismatched_band_meaning_cannot_look_like_2d_success(tmp_path):
    path, _ = raster(tmp_path)
    with pytest.raises(UnsupportedOperationError): open_raster(path)
    with pytest.raises(InvalidRequestError): open_raster(path, band_axis=RasterBandAxis("scenario", (1, 2), "caller"))
    with pytest.raises(InvalidRequestError): open_raster(path, band_axis={"guess": "time"})
    with pytest.raises(InvalidRequestError): open_raster(path, band_axis=RasterBandAxis("scenario", (1, 2, 3), "caller"), max_bytes=1)


@pytest.mark.parametrize("coordinates", [(1, 1), (True,), (float("nan"),), ("",), (1, "2"), [1, 2], (10**1000,)])
def test_ambiguous_or_mutable_axis_metadata_refuses(coordinates):
    with pytest.raises(InvalidRequestError): RasterBandAxis("time", coordinates, "caller")


def test_mixed_band_units_do_not_silently_coerce(tmp_path):
    import rasterio
    path, _ = raster(tmp_path)
    with rasterio.open(path, "r+") as target: target.set_band_unit(2, "mm")
    with pytest.raises(UnsupportedOperationError):
        open_raster(path, band_axis=RasterBandAxis("scenario", (1, 2, 3), "caller"))


@pytest.mark.parametrize("options", [
    {"name": "x"}, {"name": ""}, {"name": 1}, {"name": "a" * 65},
    {"coordinates": ()}, {"coordinates": tuple(range(257))}, {"coordinates": (object(),)},
    {"coordinates": ("a" * 257,)}, {"coordinates": (float("inf"),)},
    {"provenance": ""}, {"provenance": None}, {"provenance": "x" * 4097},
])
def test_invalid_axis_identity_bounds_and_provenance_fail_before_io(options):
    with pytest.raises(InvalidRequestError):
        RasterBandAxis(**{"name": "time", "coordinates": (1, 2, 3), "provenance": "caller", **options})


def test_nondefault_multiband_scales_require_mediation(tmp_path):
    import rasterio
    path, _ = raster(tmp_path)
    with rasterio.open(path, "r+") as target:
        target.scales = (1, 2, 1)
    with pytest.raises(UnsupportedOperationError):
        open_raster(path, band_axis=RasterBandAxis("scenario", (1, 2, 3), "caller"))


def test_one_explicitly_labelled_band_still_has_declared_axis(tmp_path):
    import numpy as np
    import rasterio
    from rasterio.transform import from_origin
    path = tmp_path / "one.tif"
    with rasterio.open(path, "w", driver="GTiff", height=1, width=1, count=1, dtype="int16",
            crs="EPSG:3857", transform=from_origin(0, 100, 100, 100)) as target:
        target.write(np.array([[[0]]], dtype="int16"))
    result = open_raster(path, band_axis=RasterBandAxis("scenario", ("control",), "caller"))
    assert result.descriptor.shape == (1, 1, 1) and result.dataset.value.values.tolist() == [[[0]]]
