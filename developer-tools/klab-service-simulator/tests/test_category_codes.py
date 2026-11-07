"""SIMULATED_PROVISIONAL/JAVA-GAP-0014, declared labels over raw synthetic code TIFF."""
import pytest

np = pytest.importorskip("numpy", reason="Dedicated scientific CI installs analysis extras")
pytest.importorskip("rasterio", reason="Dedicated scientific CI installs analysis extras")
pytest.importorskip("xarray", reason="Dedicated scientific CI installs analysis extras")

from klab_client import (Client, ObservationRequest, RectangularGeometry, RasterCategories,
    categorize_raster, open_raster, export_for_analysis)
from klab_simulator.fixtures import category_code_fixture
from conftest import launch
from test_vertical import client_args, control


def test_owned_synthetic_code_export_retains_codes_mask_and_explicit_dictionary(tmp_path):
    manifest, pinned = category_code_fixture()
    with launch("category-code-export") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            try:
                observation = context.observe(ObservationRequest(manifest["observable"],
                    geometry=RectangularGeometry(0, 300, 0, 200, 3, 2))).result(timeout=2)
                artifact = export_for_analysis(observation, tmp_path / "codes.tif")
                assert artifact.export.path.read_bytes() == pinned
                source = open_raster(artifact)
                dictionary = RasterCategories(tuple(tuple(entry) for entry in manifest["entries"]), manifest["meaning"])
                result = categorize_raster(source, dictionary)
                np.testing.assert_array_equal(result.dataset.value.values, np.array(manifest["values"], dtype="int16"))
                np.testing.assert_array_equal(result.dataset.valid.values, np.array(manifest["values"]) != manifest["nodata"])
                assert result.dataset.category.values.tolist() == [["bare", "forest", ""], ["water", "bare", "forest"]]
                assert result.dataset.category.attrs["category_source"] == "CALLER_DECLARED"
                assert source.descriptor.units is None and source.descriptor.dtype == "int16"
            finally:
                context.release(); session.release()
        assert not any(control(ready)["counts"].values())
