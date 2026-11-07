"""SIMULATED_PROVISIONAL synthetic bands, not native temporal/scientific acceptance."""
import hashlib

import httpx
import pytest

np = pytest.importorskip("numpy", reason="Dedicated scientific-adapters CI requires this extra")
pytest.importorskip("rasterio", reason="Dedicated scientific-adapters CI requires this extra")
pytest.importorskip("xarray", reason="Dedicated scientific-adapters CI requires this extra")

from klab_client import Client, ObservationRequest, RasterBandAxis, RectangularGeometry, export_for_analysis, open_raster
from klab_client.errors import UnsupportedOperationError
from klab_simulator.fixtures import declared_band_fixture
from conftest import launch
from test_vertical import client_args, control


def test_owned_sdk_export_retains_all_bands_with_explicit_caller_axis(tmp_path):
    manifest, pinned = declared_band_fixture()
    with launch("declared-band-export") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            client.initialize_user_scope()
            session = client.create_session()
            context = session.create_context()
            try:
                observation = context.observe(ObservationRequest("geography:Elevation in m",
                    geometry=RectangularGeometry(0, 300, 0, 200, 3, 2))).result(timeout=2)
                artifact = export_for_analysis(observation, tmp_path / "bands.tif")
                assert artifact.export.path.read_bytes() == pinned
                assert artifact.sha256 == manifest["sha256"] == hashlib.sha256(pinned).hexdigest()
                with pytest.raises(UnsupportedOperationError): open_raster(artifact)
                axis = RasterBandAxis(manifest["axis_name"], tuple(manifest["axis_coordinates"]), manifest["axis_source"])
                result = open_raster(artifact, band_axis=axis)
                np.testing.assert_array_equal(result.dataset.value.values, np.array(manifest["values"], dtype="float32"))
                np.testing.assert_array_equal(result.dataset.valid.values, np.array(manifest["values"]) != -9999)
                assert result.descriptor.shape == (3, 2, 3) and result.descriptor.units == "m"
                assert result.dataset.value.attrs["band_axis_source"] == "CALLER_DECLARED"
                assert result.consistency.startswith("unknown:")
                denied = httpx.get(ready["endpoints"]["runtime"] + "/export/OBSERVATION/" + observation.urn,
                    headers={"Authorization": "sim-token-bob", "klab-scope": context.id,
                        "klab-service": ready["service_ids"]["runtime"], "Accept": "image/tiff;application=geotiff",
                        "Accept-Encoding": "identity"}, trust_env=False, timeout=3)
                assert denied.status_code == 403
                refused = httpx.post(ready["endpoints"]["runtime"] + "/api/v1/observation/value",
                    headers={"Authorization": "sim-token-alice", "klab-scope": context.id},
                    json={"sourceId": observation.id, "offset": 0}, trust_env=False, timeout=3)
                assert refused.status_code == 422  # This fixture proves export, not a guessed band/offset point map.
            finally:
                context.release(); session.release()
        assert not any(control(ready)["counts"].values())
