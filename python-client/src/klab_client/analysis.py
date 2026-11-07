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
class RasterBandAxis:
    """Caller-declared band meaning, never inferred native temporal/category history."""
    name: str
    coordinates: tuple
    provenance: str

    def __post_init__(self):
        if (not isinstance(self.name, str) or not self.name.isidentifier() or self.name in ("x", "y", "value", "valid")
                or len(self.name) > 64 or type(self.coordinates) is not tuple or not 1 <= len(self.coordinates) <= 256
                or not isinstance(self.provenance, str) or not self.provenance.strip() or len(self.provenance) > 4096):
            raise InvalidRequestError("Band axis requires explicit immutable coordinates and attributable meaning")
        for coordinate in self.coordinates:
            if (type(coordinate) not in (str, int, float) or isinstance(coordinate, str) and (not coordinate or len(coordinate) > 256)
                    or type(coordinate) in (int, float) and (abs(coordinate) > 2**53 or not math.isfinite(coordinate))):
                raise InvalidRequestError("Band coordinates must be bounded explicit labels or exact finite numbers")
        if len({type(value) for value in self.coordinates}) != 1 or len(set(self.coordinates)) != len(self.coordinates):
            raise InvalidRequestError("Band coordinates must be homogeneous and unique")


@dataclass(frozen=True)
class RasterDescriptor:
    shape: tuple[int, ...]
    dtype: str
    crs: str
    transform: tuple[float, float, float, float, float, float]
    units: str | None
    nodata: float | int | None
    sha256: str
    estimated_bytes: int
    band_axis: RasterBandAxis | None = None


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


def export_for_analysis(observation, path, *, media_type="image/tiff;application=geotiff", overwrite=False, max_bytes=None, slice=None):
    """Reuse native export, preserving exact bytes and existing saved-reference semantics."""
    from .client import Client
    reference, units = _analysis_reference(observation, Client)
    result = observation.export_to_file(path, media_type=media_type, overwrite=overwrite, max_bytes=max_bytes,
                                       **({"slice": slice} if slice is not None else {}))
    return _artifact(result, reference, units)


def _analysis_reference(observation, client_type):
    if (not isinstance(observation, ObservationImpl)
            or not isinstance(getattr(observation._context, "client", None), client_type)):
        raise InvalidRequestError("Analysis export requires an observation bound to the matching sync/async client")
    return to_handle(observation), observation.units


def _artifact(result, reference, units):
    return AnalysisArtifact(result, reference, datetime.now(timezone.utc), units, _digest(result.path))


async def async_export_for_analysis(observation, path, *, media_type="image/tiff;application=geotiff", overwrite=False, max_bytes=None, slice=None):
    """Await native export with the same exact-byte association, never a revision lease.

    Remote streaming is native async. Local hashing uses bounded synchronous64KiB
    reads, like the synchronous helper; no scientific extras or thread facade.
    """
    from .async_client import AsyncClient
    reference, units = _analysis_reference(observation, AsyncClient)
    result = await observation.export_to_file(path, media_type=media_type, overwrite=overwrite, max_bytes=max_bytes,
                                             **({"slice": slice} if slice is not None else {}))
    return _artifact(result, reference, units)


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


def open_raster(source, *, max_bytes=DEFAULT_ANALYSIS_BYTES, band_axis=None):
    """Load numerical rectilinear GeoTIFF with explicit optional band-axis meaning.

    Dataset variables are `value` (native dtype) and `valid` (boolean). No implicit
    masking/coercion, reprojection, unit conversion or network access is performed.
    Budget covers estimated owned buffers and file size, not total GDAL/process RSS.
    """
    if type(max_bytes) is not int or max_bytes <= 0:
        raise InvalidRequestError("Analysis byte budget must be a positive integer")
    if band_axis is not None and not isinstance(band_axis, RasterBandAxis):
        raise InvalidRequestError("Multiband meaning requires an explicit RasterBandAxis")
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
            if reader.subdatasets or not 1 <= reader.count <= 256 or reader.count != 1 and band_axis is None:
                raise UnsupportedOperationError("Multiband rasters require explicit declared band-axis coordinates")
            if band_axis is not None and len(band_axis.coordinates) != reader.count:
                raise InvalidRequestError("Declared band axis differs from the exact TIFF band count")
            dtype = np.dtype(reader.dtypes[0])
            if dtype.kind not in "iuf" or len(set(reader.dtypes)) != 1 or any(value.name == "palette" for value in reader.colorinterp):
                raise UnsupportedOperationError("Only plain numerical rasters are supported")
            if reader.scales != (1.0,) * reader.count or reader.offsets != (0.0,) * reader.count:
                raise UnsupportedOperationError("Scaled/offset raster values require an explicit mediation contract")
            transform = reader.transform
            coefficients = tuple(transform)[:6]
            if (not all(math.isfinite(v) for v in coefficients) or transform.b != 0 or transform.d != 0
                    or transform.a == 0 or transform.e == 0 or transform.is_identity or reader.crs is None):
                raise UnsupportedOperationError("Raster requires supplied CRS and nondegenerate rectilinear georeferencing")
            cells = reader.width * reader.height * reader.count
            estimate = size + cells * (2 * dtype.itemsize + 4) + 32 * (reader.width + reader.height)
            if band_axis is not None:
                estimate += reader.count * (512 + 8 * max(len(str(value)) for value in band_axis.coordinates)) + 8 * len(band_axis.provenance)
            if estimate > max_bytes:
                raise InvalidRequestError("Raster arrays and validity exceed the analysis byte budget")
            units = reader.units[0] or None
            if any((unit or None) != units for unit in reader.units):
                raise UnsupportedOperationError("Bands with different units require an explicit mediation contract")
            if artifact and artifact.source_units is not None and artifact.source_units != units:
                raise ProtocolError("Observation and exported raster source units disagree or are missing")
            with np.errstate(over="ignore", invalid="ignore"):
                x = transform.c + (np.arange(reader.width, dtype="float64") + 0.5) * transform.a
                y = transform.f + (np.arange(reader.height, dtype="float64") + 0.5) * transform.e
            x_ordered = np.all(x[1:] > x[:-1]) if transform.a > 0 else np.all(x[1:] < x[:-1])
            y_ordered = np.all(y[1:] > y[:-1]) if transform.e > 0 else np.all(y[1:] < y[:-1])
            if not np.isfinite(x).all() or not np.isfinite(y).all() or not x_ordered or not y_ordered:
                raise UnsupportedOperationError("Raster pixel centers must be finite and distinct in float64 coordinates")
            values = reader.read() if band_axis is not None else reader.read(1)
            valid = (reader.read_masks() if band_axis is not None else reader.read_masks(1)) != 0
            shape = (reader.count, reader.height, reader.width) if band_axis is not None else (reader.height, reader.width)
            descriptor = RasterDescriptor(shape, dtype.name, reader.crs.to_wkt(),
                coefficients, units, reader.nodata, digest, estimate, band_axis)
    if _digest(path) != digest:
        raise ProtocolError("Raster file changed during analysis loading")
    attrs = {"crs_wkt": descriptor.crs, "transform": descriptor.transform,
             "artifact_sha256": descriptor.sha256, "validity": "valid variable; zero is not missing"}
    if units is not None:
        attrs["units"] = units
    dimensions = (band_axis.name, "y", "x") if band_axis is not None else ("y", "x")
    coordinates = {"x": x, "y": y}
    if band_axis is not None:
        coordinates[band_axis.name] = list(band_axis.coordinates)
        attrs.update(band_axis_source="CALLER_DECLARED", band_axis_provenance=band_axis.provenance)
    dataset = xr.Dataset({"value": (dimensions, values, attrs),
                          "valid": (dimensions, valid)}, coords=coordinates)
    return RasterAnalysis(descriptor, dataset, artifact, datetime.now(timezone.utc))
