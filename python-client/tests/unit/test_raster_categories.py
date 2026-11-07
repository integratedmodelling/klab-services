"""SIMULATED_PROVISIONAL/JAVA-GAP-0014, explicit local categories not native metadata."""
from dataclasses import replace

import pytest

np = pytest.importorskip("numpy", reason="Optional analysis extra")
pytest.importorskip("rasterio", reason="Optional analysis extra")
pytest.importorskip("xarray", reason="Optional analysis extra")

from klab_client import RasterCategories, categorize_raster, open_raster
from klab_client.errors import InvalidRequestError, UnsupportedOperationError


def raster(tmp_path):
    import rasterio
    from rasterio.transform import from_origin
    path = tmp_path / "codes.tif"
    with rasterio.open(path, "w", driver="GTiff", width=3, height=2, count=1, dtype="int16",
            nodata=-32768, crs="EPSG:3857", transform=from_origin(0, 200, 100, 100)) as target:
        target.write(np.array([[[0, -2, -32768], [7, 0, -2]]], dtype="int16"))
    return open_raster(path)


def dictionary():
    return RasterCategories(((0, "bare"), (-2, "forest"), (7, "water")), "Explicit synthetic caller dictionary")


def test_labels_preserve_all_raw_codes_mask_zero_negative_and_source(tmp_path):
    source = raster(tmp_path)
    result = categorize_raster(source, dictionary())
    assert result.dataset.category.values.tolist() == [["bare", "forest", ""], ["water", "bare", "forest"]]
    np.testing.assert_array_equal(result.dataset.value.values, source.dataset.value.values)
    np.testing.assert_array_equal(result.dataset.valid.values, source.dataset.valid.values)
    assert result.dataset.value.dtype == np.dtype("int16")
    assert result.dataset.category.attrs["missingness"] == "USE_VALID_MASK"
    assert result.semantics == "CALLER_DECLARED_CATEGORIES_NOT_NATIVE_CLASSIFICATION"
    result.dataset.value.values[0, 0] = 7
    assert source.dataset.value.values[0, 0] == 0


@pytest.mark.parametrize("entries", [[], (), tuple((n, str(n)) for n in range(257)), ((True, "a"),),
    ((1.0, "a"),), ((2**63, "a"),), ((1, ""),), ((1, "x" * 257),), ((1, "a"), (1, "b")),
    ((1, "a"), (2, "a")), ([1, "a"],), ((1,),)])
def test_invalid_ambiguous_or_mutable_dictionaries_refuse(entries):
    with pytest.raises(InvalidRequestError): RasterCategories(entries, "caller")


@pytest.mark.parametrize("provenance", [None, "", " ", "x" * 4097])
def test_dictionary_requires_attribution(provenance):
    with pytest.raises(InvalidRequestError): RasterCategories(((1, "a"),), provenance)


def test_unknown_codes_float_codes_bad_masks_and_overwrite_are_not_coerced(tmp_path):
    source = raster(tmp_path)
    with pytest.raises(UnsupportedOperationError): categorize_raster(source, RasterCategories(((0, "bare"),), "caller"))
    dataset = source.dataset.copy(deep=True)
    dataset["value"] = dataset.value.astype("float64")
    with pytest.raises(UnsupportedOperationError): categorize_raster(replace(source, dataset=dataset), dictionary())
    dataset = source.dataset.copy(deep=True)
    dataset["valid"] = dataset.valid.astype("int8")
    with pytest.raises(UnsupportedOperationError): categorize_raster(replace(source, dataset=dataset), dictionary())
    dataset = source.dataset.copy(deep=True)
    dataset["valid"] = (("unbound_row", "unbound_column"), dataset.valid.values.copy())
    with pytest.raises(UnsupportedOperationError): categorize_raster(replace(source, dataset=dataset), dictionary())
    dataset = source.dataset.copy(deep=True)
    dataset["category"] = dataset.value
    with pytest.raises(UnsupportedOperationError): categorize_raster(replace(source, dataset=dataset), dictionary())


def test_explicit_input_and_allocation_budgets_before_label_buffers(tmp_path):
    source = raster(tmp_path)
    with pytest.raises(InvalidRequestError): categorize_raster(object(), dictionary())
    with pytest.raises(InvalidRequestError): categorize_raster(source, {})
    for budget in (True, 0, -1, 1):
        with pytest.raises(InvalidRequestError): categorize_raster(source, dictionary(), max_bytes=budget)


def test_exact_signed64_codes_do_not_round_and_unsigned_unknown_does_not_alias(tmp_path):
    source = raster(tmp_path)
    dataset = source.dataset.copy(deep=True)
    dataset["value"] = (dataset.value.dims, np.array([[-2**63, 2**63 - 1, 0], [0, -2**63, 2**63 - 1]], dtype="int64"))
    exact = RasterCategories(((-2**63, "low"), (2**63 - 1, "high"), (0, "zero")), "caller exact signed64 controls")
    result = categorize_raster(replace(source, dataset=dataset, descriptor=replace(source.descriptor, dtype="int64")), exact)
    assert result.dataset.category.values.tolist() == [["low", "high", ""], ["zero", "low", "high"]]
    np.testing.assert_array_equal(result.dataset.value.values, dataset.value.values)
    dataset["value"] = (dataset.value.dims, np.full((2, 3), 2**64 - 1, dtype="uint64"))
    with pytest.raises(UnsupportedOperationError): categorize_raster(replace(source, dataset=dataset), RasterCategories(((-1, "not-an-alias"),), "caller"))
