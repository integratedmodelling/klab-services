"""Independent pixel/coordinate expectations; real TIFF IO, no service acceptance claim."""
import builtins
from dataclasses import replace
from datetime import datetime, timezone
import hashlib
from pathlib import Path

import pytest

from klab_client import AnalysisArtifact, ExportResult, Handle, export_for_analysis, open_raster
from klab_client.analysis import _digest
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError

np = pytest.importorskip("numpy")
rio = pytest.importorskip("rasterio")
pytest.importorskip("xarray")
from rasterio.transform import Affine


def write_raster(tmp_path, *, data=None, transform=None, units="degree_angle", **changes):
    path = tmp_path / "known field.tif"
    data = np.array([[16777217.125, 0, 3], [7, np.nan, 11]], dtype="float64") if data is None else data
    options = dict(driver="GTiff", width=data.shape[-1], height=data.shape[-2], count=1,
        dtype=data.dtype, crs="EPSG:3857", transform=transform or Affine(20, 0, 100, 0, -30, 500), nodata=np.nan)
    options.update(changes)
    with rio.open(path, "w", **options) as target:
        target.write(data, 1)
        if units is not None:
            target.set_band_unit(1, units)
    return path


def association(path, units="degree_angle"):
    return AnalysisArtifact(ExportResult(path, path.stat().st_size, "image/tiff", "test-schema"),
        Handle("observation", "home", "https://runtime.invalid/runtime", "s.c", "s.c", observation_id=42),
        datetime.now(timezone.utc), units, _digest(path))


def test_known_field_preserves_precision_axes_masks_units_and_lifetime(tmp_path):
    path = write_raster(tmp_path)
    before = path.read_bytes()
    artifact = association(path)
    result = open_raster(artifact)
    ds = result.dataset
    assert ds.value.dtype == np.dtype("float64")
    assert ds.value.values[0, 0] == 16777217.125
    assert ds.value.values[0, 1] == 0 and ds.valid.values[0, 1]
    assert not ds.valid.values[1, 1]
    np.testing.assert_array_equal(ds.x, [110, 130, 150])
    np.testing.assert_array_equal(ds.y, [485, 455])
    np.testing.assert_array_equal(ds.valid, [[True, True, True], [True, False, True]])
    assert float(ds.value.where(ds.valid).sum()) == 16777238.125
    assert result.descriptor.shape == (2, 3)
    assert result.descriptor.transform == (20, 0, 100, 0, -30, 500)
    assert result.descriptor.units == "degree_angle"  # projected CRS uses metres, source field does not
    assert result.descriptor.sha256 == hashlib.sha256(before).hexdigest()
    assert result.artifact == artifact and result.opened_at.tzinfo is not None
    assert result.consistency.startswith("unknown")
    assert path.read_bytes() == before
    path.unlink()  # Windows permits this only after file handles are closed
    assert ds.value.values[1, 2] == 11


@pytest.mark.parametrize("dtype", ["int16", "uint64", "float32"])
def test_native_dtype_no_silent_coercion(tmp_path, dtype):
    data = np.array([[0, 2, 9], [5, 6, 7]], dtype=dtype)
    if dtype == "uint64":
        data[1, 2] = 2**63 + 1
    path = write_raster(tmp_path, data=data, nodata=None, units=None)
    result = open_raster(path)
    assert result.dataset.value.dtype == data.dtype
    np.testing.assert_array_equal(result.dataset.value.values, data)
    assert result.dataset.valid.values.all()
    assert result.descriptor.units is None and "units" not in result.dataset.value.attrs
    assert result.artifact is None


@pytest.mark.parametrize("transform", [Affine(20, 0, 100, 0, 30, 500), Affine(-20, 0, 100, 0, -30, 500)])
def test_native_axis_direction_is_not_silently_reordered(tmp_path, transform):
    result = open_raster(write_raster(tmp_path, transform=transform))
    np.testing.assert_array_equal(result.dataset.x, [100 + transform.a * v for v in (.5, 1.5, 2.5)])
    np.testing.assert_array_equal(result.dataset.y, [500 + transform.e * v for v in (.5, 1.5)])
    assert result.dataset.value.values[0, 0] == 16777217.125


@pytest.mark.parametrize("budget", [True, 0, -1, 1.2])
def test_invalid_budget(tmp_path, budget):
    with pytest.raises(InvalidRequestError):
        open_raster(tmp_path / "absent", max_bytes=budget)


def test_file_and_array_budget_preflight(tmp_path):
    path = write_raster(tmp_path)
    with pytest.raises(InvalidRequestError, match="local file"):
        open_raster(path, max_bytes=1)
    with pytest.raises(InvalidRequestError, match="local file"):
        open_raster(tmp_path)
    with pytest.raises(InvalidRequestError, match="arrays"):
        open_raster(path, max_bytes=path.stat().st_size)


@pytest.mark.parametrize("transform", [Affine(20, 1, 100, 0, -30, 500), Affine(20, 0, 100, 1, -30, 500), Affine.identity(),
                                       Affine(0, 1, 100, 0, -30, 500)])
def test_unsupported_georeferencing_is_rejected(tmp_path, transform):
    path = write_raster(tmp_path, transform=transform)
    with pytest.raises(UnsupportedOperationError):
        open_raster(path)
    path.unlink()


def test_missing_crs_and_multiband(tmp_path):
    with pytest.raises(UnsupportedOperationError):
        open_raster(write_raster(tmp_path, crs=None))
    with pytest.raises(UnsupportedOperationError):
        open_raster(write_raster(tmp_path, count=2))


@pytest.mark.parametrize("dtype", ["complex64", "complex128"])
def test_complex_values_unsupported(tmp_path, dtype):
    path = write_raster(tmp_path, data=np.ones((2, 3), dtype=dtype), nodata=None)
    with pytest.raises(UnsupportedOperationError):
        open_raster(path)


def test_palette_and_scaled_inputs_are_explicit(tmp_path):
    path = write_raster(tmp_path, data=np.ones((2, 3), dtype="uint8"), nodata=None)
    with rio.open(path, "r+") as writer:
        writer.write_colormap(1, {1: (255, 0, 0, 255)})
    with pytest.raises(UnsupportedOperationError, match="plain numerical"):
        open_raster(path)
    path = write_raster(tmp_path)
    with rio.open(path, "r+") as writer:
        writer.scales = (2,)
    with pytest.raises(UnsupportedOperationError, match="Scaled"):
        open_raster(path)


def test_association_rejects_modified_bytes_and_conflicting_or_missing_units(tmp_path):
    path = write_raster(tmp_path)
    with pytest.raises(ProtocolError, match="bytes differ"):
        open_raster(replace(association(path), sha256="bad"))
    with pytest.raises(ProtocolError, match="units"):
        open_raster(association(path, "m"))
    path = write_raster(tmp_path, units=None)
    with pytest.raises(ProtocolError, match="units"):
        open_raster(association(path, "m"))
    assert open_raster(association(path, None)).descriptor.units is None


def test_missing_extra_has_actionable_error(tmp_path, monkeypatch):
    path = write_raster(tmp_path)
    original = builtins.__import__
    def missing(name, *args, **kwargs):
        if name == "xarray":
            raise ImportError("not installed")
        return original(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", missing)
    with pytest.raises(UnsupportedOperationError, match=r"\[analysis\]"):
        open_raster(path)


def test_malformed_file_and_missing_path(tmp_path):
    path = tmp_path / "not.tif"
    path.write_text("not a TIFF")
    with pytest.raises(rio.errors.RasterioIOError):
        open_raster(path)
    with pytest.raises(FileNotFoundError):
        open_raster(tmp_path / "absent")


def test_export_association_uses_native_path_and_handle(tmp_path, monkeypatch):
    from klab_client import Client, Context
    from klab_client.dto import observation_from_wire
    import json
    fixture = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    fixture["urn"] = "s.c.42"
    path = write_raster(tmp_path, units="m")
    with Client("https://runtime.invalid/runtime", "secret", runtime_service_id="home") as client:
        observation = observation_from_wire(fixture, Context(client, {"id": "s.c", "serviceId": "home"}))
        calls = []
        def export(self, destination, **kwargs):
            calls.append((destination, kwargs))
            return ExportResult(path, path.stat().st_size, "image/tiff", "native")
        monkeypatch.setattr(type(observation), "export_to_file", export)
        artifact = export_for_analysis(observation, path, overwrite=True, max_bytes=10000)
        assert artifact.observation.observation_id == 42 and artifact.source_units == "m"
        assert artifact.sha256 == _digest(path)
        assert calls == [(path, {"media_type": "image/tiff;application=geotiff", "overwrite": True, "max_bytes": 10000})]
        assert open_raster(artifact).artifact == artifact
        with pytest.raises(InvalidRequestError):
            export_for_analysis(artifact.observation, path)


def test_changed_file_during_load_is_not_given_a_stale_digest(tmp_path, monkeypatch):
    import klab_client.analysis as module
    path = write_raster(tmp_path)
    original = module._digest
    calls = []
    def changing(path):
        calls.append(path)
        return original(path) if len(calls) == 1 else "changed"
    monkeypatch.setattr(module, "_digest", changing)
    with pytest.raises(ProtocolError, match="changed during"):
        open_raster(path)


def test_tracing_checks_exact_origin_identity_without_snapshot_claim(tmp_path):
    from klab_client import ProvenanceView, trace_analysis
    artifact = association(write_raster(tmp_path))
    result = open_raster(artifact)
    view = ProvenanceView(artifact.observation, (), datetime.now(timezone.utc), 0, None)
    traced = trace_analysis(result, view)
    assert traced.consistency.startswith("unknown") and traced.analysis is result and traced.provenance is view
    foreign = replace(view, observation=replace(artifact.observation, runtime_url="https://other.invalid/runtime"))
    for analysis, provenance in [(result, foreign), (open_raster(artifact.export.path), view), (None, view), (result, None)]:
        with pytest.raises(InvalidRequestError):
            trace_analysis(analysis, provenance)


@pytest.mark.parametrize("transform", [Affine(1e308, 0, 1e308, 0, -30, 500), Affine(1, 0, 1e20, 0, -30, 500),
    Affine(20, 0, 100, 0, -1e308, -1e308), Affine(20, 0, 100, 0, -1, 1e20)])
def test_unrepresentable_pixel_centers_reject_instead_of_inventing_coordinates(tmp_path, transform):
    path = write_raster(tmp_path, transform=transform)
    with pytest.raises(UnsupportedOperationError, match="pixel centers"):
        open_raster(path)
