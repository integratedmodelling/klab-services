import json
from pathlib import Path

import httpx
import numpy as np
import pytest
import rasterio
from rasterio.transform import Affine

from klab_client import Client, Context
from klab_client.dto import observation_from_wire
from workflows import native_stream_workload as workflow


def native_fixture(path, *, value=234.5, unit="m", shift=0, dtype="float64", invalid=False):
    values = np.full((1024, 1024), value, dtype=dtype)
    if invalid:
        values[0, 0] = np.nan
    with rasterio.open(path, "w", driver="GTiff", height=1024, width=1024, count=1,
        dtype=dtype, crs="EPSG:3857", transform=Affine(100, 0, 200000 + shift, 0, -100, 6102400), nodata=np.nan) as target:
        target.write(values, 1)
        target.set_band_unit(1, unit)
    return path


def test_all_native_cells_validate_incrementally_against_literal_and_geometry(tmp_path):
    path = native_fixture(tmp_path / "known.tif")
    report = workflow.validate_native(path)
    assert report["cells"] == 1048576 and report["windows"] == 64
    assert report["largest_window_cells"] == 16384 and report["expected_value"] == 234.5
    assert len(workflow.digest(path)) == 64


@pytest.mark.parametrize("changes", [{"value": 234}, {"unit": "mm"}, {"shift": 1},
                                   {"dtype": "float32"}, {"invalid": True}])
def test_wrong_values_units_bounds_dtype_or_mask_are_not_scientific_passes(tmp_path, changes):
    path = native_fixture(tmp_path / "wrong.tif", **changes)
    with pytest.raises(AssertionError):
        workflow.validate_native(path)


class Stream(httpx.SyncByteStream):
    def __init__(self, *, error=False):
        self.closed = False
        self.error = error
    def __iter__(self):
        if self.error:
            raise httpx.ReadError("genuine failure before controlled injection")
        for _ in range(4):
            yield b"x" * 65536  # Transport-control fixture, not scientific raster bytes.
    def close(self):
        self.closed = True


def configured(stream):
    def handler(request):
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "home", "exportSchemata": {"geospatial.io": [{
                "schemaId": "geospatial.io.geotiff", "knowledgeClass": "OBSERVATION", "type": "STREAM",
                "mediaTypes": [workflow.MEDIA], "extensions": ["tif"]}]}})
        return httpx.Response(200, headers={"Content-Type": workflow.MEDIA, "Content-Length": str(4 * 65536)}, stream=stream)
    client = Client("https://runtime.example/runtime", "test-credential", runtime_service_id="home",
                    http_transport=httpx.MockTransport(handler))
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    observation = observation_from_wire(payload, Context(client, {"id": "s.c", "serviceId": "home"}, token="s.c"))
    return client, observation


def test_chunk_measurement_restores_transport_and_closes_response(tmp_path):
    stream = Stream()
    client, observation = configured(stream)
    with client:
        original = client.transport.stream
        with workflow.measured_stream(client) as stats:
            result = observation.export_to_file(tmp_path / "native.tif", media_type=workflow.MEDIA)
        assert client.transport.stream == original
    assert stats["response_closed"] and stream.closed and not stats["injected"]
    assert stats["bytes_emitted"] == result.bytes_written == 4 * 65536
    assert stats["chunks"] == 4 and stats["largest_chunk"] == 65536
    assert stats["sampled_rss_growth"] >= 0 and stats["elapsed_seconds"] >= 0


@pytest.mark.parametrize("existing", [False, True])
def test_injected_native_read_leaves_no_staging_or_replaced_destination(tmp_path, existing):
    path = tmp_path / "native.tif"
    if existing:
        path.write_bytes(b"original known-valid destination sentinel")
    stream = Stream()
    client, observation = configured(stream)
    with client:
        stats = workflow.interrupted_export(client, observation, path, tmp_path, overwrite=existing)
    assert stats["injected"] and stats["chunks"] == 1 and stream.closed
    assert stats["response_closed"] and not list(tmp_path.glob(".klab-export-*"))
    assert stats["existing_destination_preserved"] is existing


def test_real_transport_failure_is_not_a_controlled_interruption_pass(tmp_path):
    stream = Stream(error=True)
    client, observation = configured(stream)
    with client, pytest.raises(AssertionError, match="genuine transport failure"):
        workflow.interrupted_export(client, observation, tmp_path / "native.tif", tmp_path)
    assert stream.closed and not list(tmp_path.glob(".klab-export-*"))
