"""Optional scientific views of isolated HTTP results; independent frozen oracles.

Default core tests may skip this extra; dedicated scientific-adapters CI installs
and checks all dependencies before running these acceptance cases without skips.
"""
import asyncio
import json

import pytest

np = pytest.importorskip("numpy", reason="scientific adapter extra required")
pytest.importorskip("pandas", reason="scientific adapter extra required")
rio = pytest.importorskip("rasterio", reason="scientific adapter extra required")
pytest.importorskip("xarray", reason="scientific adapter extra required")

from klab_client import AsyncClient, Client, ObservationRequest, RectangularGeometry, fetch_provenance
from klab_client.analysis import export_for_analysis, open_raster, trace_analysis
from klab_client.errors import InvalidRequestError, UnsupportedOperationError
from klab_simulator.exports import MEDIA
from klab_simulator.fixtures import NONUNIFORM_TIFF_SHA
from conftest import REPO, ROOT, launch
from test_vertical import client_args, control, geometry


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_nonuniform_native_raster_xarray_precision_mask_coordinates_and_source_trace(mode, tmp_path):
    # The expected data predates this simulator and is not read from its implementation.
    oracle = json.loads((REPO / "python-client/tests/fixtures/nonuniform-export.json").read_text())
    expected = np.array([[np.nan if value is None else value for value in row] for row in oracle["north_first"]], dtype="float64")
    path = tmp_path / "nonuniform.tif"
    proposal = ObservationRequest("geography:Elevation in m",
        geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))
    with launch("nonuniform-export") as ready:
        if mode == "async":
            async def run():
                async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                    session = await client.create_session()
                    context = await session.create_context()
                    try:
                        observation = await (await context.observe(proposal)).result(timeout=2)
                        await observation.export_to_file(path, media_type=MEDIA)
                    finally:
                        await context.release()
                        await session.release()
            asyncio.run(run())
            analysis = open_raster(path)  # Explicit local inspection, not a fabricated async export association.
            assert analysis.artifact is None
        else:
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                session = client.create_session()
                context = session.create_context()
                try:
                    observation = context.observe(proposal).result(timeout=2)
                    artifact = export_for_analysis(observation, path)
                    analysis = open_raster(artifact)
                    assert analysis.artifact.observation == observation.to_handle()
                    traced = trace_analysis(analysis, fetch_provenance(observation))
                    assert traced.provenance.partial and "unknown" in traced.consistency
                finally:
                    context.release()
                    session.release()
        assert analysis.descriptor.sha256 == NONUNIFORM_TIFF_SHA
        assert analysis.descriptor.shape == (4, 5) and analysis.descriptor.dtype == "float64"
        assert analysis.descriptor.units == "m" and analysis.descriptor.transform == tuple(oracle["transform"])
        assert rio.crs.CRS.from_wkt(analysis.descriptor.crs).to_epsg() == 3857
        np.testing.assert_array_equal(analysis.dataset["value"].values, expected)
        np.testing.assert_array_equal(analysis.dataset["valid"].values, ~np.isnan(expected))
        np.testing.assert_array_equal(analysis.dataset.x.values, oracle["x"])
        np.testing.assert_array_equal(analysis.dataset.y.values, oracle["y"])
        assert int(analysis.dataset["valid"].values.sum()) == 18
        assert analysis.dataset["value"].values[0, 0] == 16777217.125  # Beyond exact float32 integer precision.
        assert analysis.dataset["value"].values[3, 0] == 0 and analysis.dataset["valid"].values[3, 0]
        with pytest.raises(InvalidRequestError):
            open_raster(path, max_bytes=100)
        path.unlink()  # Eager local arrays do not keep native files open.
        np.testing.assert_array_equal(analysis.dataset["value"].values, expected)
        assert not any(control(ready)["counts"].values())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_tabular_views_preserve_zero_negative_null_nan_duplicates_and_explicit_offsets(mode):
    with launch("zero-negative-nodata") as ready:
        if mode == "async":
            async def run():
                async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                    session = await client.create_session()
                    context = await session.create_context()
                    try:
                        observation = await (await context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))).result(timeout=2)
                        return await observation.fetch_data((0, 1, 2, 3, 0))
                    finally:
                        await context.release()
                        await session.release()
            data = asyncio.run(run())
        else:
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                session = client.create_session()
                context = session.create_context()
                try:
                    observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                    data = observation.fetch_data((0, 1, 2, 3, 0))
                finally:
                    context.release()
                    session.release()
        frame = data.to_dataframe()
        assert frame["offset"].tolist() == [0, 1, 2, 3, 0]
        assert frame["wire_missing"].tolist() == [False, False, True, False, False]
        values = frame["value"].tolist()
        assert values[:3] == [0, -7.5, None] and values[3].is_nan() and values[4] == 0
        assert frame.attrs["klab"]["units"] == "m"
        assert frame.attrs["klab"]["coordinates"].startswith("Offsets only")
        assert "no committed-revision lease" in frame.attrs["klab"]["consistency"]
        assert not any(control(ready)["counts"].values())


def test_frozen_authentic_tiff_without_supplied_internal_georeferencing_is_not_guessed():
    # Preserve its original bytes. Oracle geometry is not silently stamped into the TIFF.
    with pytest.raises(UnsupportedOperationError, match="supplied CRS"):
        open_raster(ROOT / "src/klab_simulator/fixtures/usgs-native-20x20.tif")
