"""Optional, eager, scientifically explicit GeoTIFF analysis; no scientific imports at import time."""
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import math
from pathlib import Path

from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from .api.runtime import ObservationImpl
from .exports import ExportResult
from .handles import Handle, to_handle

DEFAULT_ANALYSIS_BYTES = 256 * 1024 * 1024


@dataclass(frozen=True)
class AnalysisArtifact:
    """Association captured during native export, not a revision-pinned server snapshot."""
    export: ExportResult
    observation: Handle
    exported_at: datetime
    source_units: str | None
    sha256: str


@dataclass(frozen=True)
class RasterDescriptor:
    shape: tuple[int, int]
    dtype: str
    crs: str
    transform: tuple[float, float, float, float, float, float]
    units: str | None
    nodata: float | int | None
    sha256: str
    estimated_bytes: int


@dataclass(frozen=True)
class RasterAnalysis:
    """Detached in-memory arrays. Caller owns the mutable Dataset; no file remains open."""
    descriptor: RasterDescriptor
    dataset: object
    artifact: AnalysisArtifact | None
    opened_at: datetime
    consistency: str = "unknown: no shared export/provenance revision contract"


def _digest(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def export_for_analysis(observation, path, *, media_type="image/tiff;application=geotiff", overwrite=False, max_bytes=None):
    """Reuse native export, preserving exact bytes and existing saved-reference semantics."""
    if not isinstance(observation, ObservationImpl) or observation._context is None:
        raise InvalidRequestError("Analysis export requires a bound observation")
    reference = to_handle(observation)
    units = observation.units
    result = observation.export_to_file(path, media_type=media_type, overwrite=overwrite, max_bytes=max_bytes)
    return AnalysisArtifact(result, reference, datetime.now(timezone.utc), units, _digest(result.path))


@dataclass(frozen=True)
class TracedAnalysis:
    analysis: RasterAnalysis
    provenance: object
    consistency: str = "unknown: association is not a revision-pinned snapshot"


def trace_analysis(analysis, provenance):
    """Associate exact saved identities; never promote association to snapshot consistency."""
    from .provenance import ProvenanceView
    if (not isinstance(analysis, RasterAnalysis) or not isinstance(provenance, ProvenanceView)
            or analysis.artifact is None or analysis.artifact.observation != provenance.observation):
        raise InvalidRequestError("Tracing requires analysis and provenance for the same captured observation handle")
    return TracedAnalysis(analysis, provenance)


def open_raster(source, *, max_bytes=DEFAULT_ANALYSIS_BYTES):
    """Load one local numerical rectilinear GeoTIFF within an eager-allocation budget.

    Dataset variables are `value` (native dtype) and `valid` (boolean). No implicit
    masking/coercion, reprojection, unit conversion or network access is performed.
    Budget covers estimated owned buffers and file size, not total GDAL/process RSS.
    """
    if type(max_bytes) is not int or max_bytes <= 0:
        raise InvalidRequestError("Analysis byte budget must be a positive integer")
    artifact = source if isinstance(source, AnalysisArtifact) else None
    path = Path(artifact.export.path if artifact else source).resolve(strict=True)
    size = path.stat().st_size
    if not path.is_file() or size > max_bytes:
        raise InvalidRequestError("Analysis requires a local file within the byte budget")
    try:
        import numpy as np
        import rasterio
        import xarray as xr
    except ImportError:
        raise UnsupportedOperationError("Install klab-python-client[analysis] to open scientific rasters") from None
    digest = _digest(path)
    if artifact and artifact.sha256 != digest:
        raise ProtocolError("Local artifact bytes differ from the captured export")
    # Internal georeferencing only: no sidecars, world files or auxiliary remote references.
    with rasterio.Env(GDAL_PAM_ENABLED="NO", GDAL_DISABLE_READDIR_ON_OPEN="EMPTY_DIR",
                      GDAL_GEOREF_SOURCES="INTERNAL"):
        with rasterio.open(path, driver="GTiff") as reader:
            if reader.count != 1 or reader.subdatasets:
                raise UnsupportedOperationError("Only single-band two-dimensional rasters are supported")
            dtype = np.dtype(reader.dtypes[0])
            if dtype.kind not in "iuf" or reader.colorinterp[0].name == "palette":
                raise UnsupportedOperationError("Only plain numerical rasters are supported")
            if reader.scales != (1.0,) or reader.offsets != (0.0,):
                raise UnsupportedOperationError("Scaled/offset raster values require an explicit mediation contract")
            transform = reader.transform
            coefficients = tuple(transform)[:6]
            if (not all(math.isfinite(v) for v in coefficients) or transform.b != 0 or transform.d != 0
                    or transform.a == 0 or transform.e == 0 or transform.is_identity or reader.crs is None):
                raise UnsupportedOperationError("Raster requires supplied CRS and nondegenerate rectilinear georeferencing")
            cells = reader.width * reader.height
            estimate = size + cells * (2 * dtype.itemsize + 4) + 32 * (reader.width + reader.height)
            if estimate > max_bytes:
                raise InvalidRequestError("Raster arrays and validity exceed the analysis byte budget")
            units = reader.units[0] or None
            if artifact and artifact.source_units is not None and artifact.source_units != units:
                raise ProtocolError("Observation and exported raster source units disagree or are missing")
            with np.errstate(over="ignore", invalid="ignore"):
                x = transform.c + (np.arange(reader.width, dtype="float64") + 0.5) * transform.a
                y = transform.f + (np.arange(reader.height, dtype="float64") + 0.5) * transform.e
            x_ordered = np.all(x[1:] > x[:-1]) if transform.a > 0 else np.all(x[1:] < x[:-1])
            y_ordered = np.all(y[1:] > y[:-1]) if transform.e > 0 else np.all(y[1:] < y[:-1])
            if not np.isfinite(x).all() or not np.isfinite(y).all() or not x_ordered or not y_ordered:
                raise UnsupportedOperationError("Raster pixel centers must be finite and distinct in float64 coordinates")
            values = reader.read(1)
            valid = reader.read_masks(1) != 0
            descriptor = RasterDescriptor((reader.height, reader.width), dtype.name, reader.crs.to_wkt(),
                coefficients, units, reader.nodata, digest, estimate)
    if _digest(path) != digest:
        raise ProtocolError("Raster file changed during analysis loading")
    attrs = {"crs_wkt": descriptor.crs, "transform": descriptor.transform,
             "artifact_sha256": descriptor.sha256, "validity": "valid variable; zero is not missing"}
    if units is not None:
        attrs["units"] = units
    dataset = xr.Dataset({"value": (("y", "x"), values, attrs),
                          "valid": (("y", "x"), valid)}, coords={"x": x, "y": y})
    return RasterAnalysis(descriptor, dataset, artifact, datetime.now(timezone.utc))
