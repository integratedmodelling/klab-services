"""Optional, bounded local RFC7946 analysis; no reprojection or geometry repair."""
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
from types import MappingProxyType

from .analysis import AnalysisArtifact
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


@dataclass(frozen=True)
class VectorAnalysis:
    """Detached mutable frame plus immutable original records (missing != null).

    IDs stay in records, not a lossy dataframe index. The geometry column name is
    selected without overwriting any source property. CRS84 is RFC7946 lon/lat,
    not a measured accuracy, unit/category declaration or server revision.
    """
    dataframe: object
    features: tuple
    geometry_column: str
    coordinate_count: int
    sha256: str
    artifact: AnalysisArtifact | None
    opened_at: datetime
    consistency: str = "unknown: no shared export/provenance revision contract"


def _freeze(value, depth=0):
    if depth > 32:
        raise ProtocolError("GeoJSON nesting exceeds 32 levels")
    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(item, depth + 1) for key, item in value.items()})
    if isinstance(value, list):
        return tuple(_freeze(item, depth + 1) for item in value)
    if type(value) is float and not math.isfinite(value):
        raise ProtocolError("GeoJSON requires finite JSON values")
    return value


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ProtocolError("Duplicate GeoJSON object key")
        result[key] = value
    return result


def _geometry(value, remaining, depth=0):
    if depth > 16 or not isinstance(value, dict) or "crs" in value:
        raise ProtocolError("Invalid or legacy-CRS GeoJSON geometry")
    kind = value.get("type")
    if kind == "GeometryCollection":
        children = value.get("geometries")
        if not isinstance(children, list):
            raise ProtocolError("GeometryCollection requires geometries")
        for child in children:
            _geometry(child, remaining, depth + 1)
        return
    levels = {"Point": 0, "MultiPoint": 1, "LineString": 1,
              "MultiLineString": 2, "Polygon": 2, "MultiPolygon": 3}
    if kind not in levels:
        raise UnsupportedOperationError("Unsupported GeoJSON geometry type")

    dimensions = set()

    def coordinates(items, level):
        if not isinstance(items, list):
            raise ProtocolError("GeoJSON coordinates must be arrays")
        if level:
            if not items:
                raise ProtocolError("Empty coordinate geometries are unsupported; use null")
            if level == 1 and kind in ("LineString", "MultiLineString") and len(items) < 2:
                raise ProtocolError("GeoJSON lines require at least two positions")
            if level == 1 and kind in ("Polygon", "MultiPolygon") and (len(items) < 4 or items[0] != items[-1]):
                raise ProtocolError("GeoJSON rings must already be closed with at least four positions")
            for item in items:
                coordinates(item, level - 1)
        else:
            if (len(items) not in (2, 3) or
                    any(type(v) not in (int, float) or type(v) is float and not math.isfinite(v) for v in items) or
                    not -180 <= items[0] <= 180 or not -90 <= items[1] <= 90):
                raise ProtocolError("GeoJSON positions require finite RFC7946 lon/lat coordinates")
            dimensions.add(len(items))
            if len(dimensions) != 1:
                raise ProtocolError("Mixed coordinate dimensions are unsupported")
            remaining[0] -= 1
            if remaining[0] < 0:
                raise InvalidRequestError("GeoJSON coordinate budget exceeded")

    coordinates(value.get("coordinates"), levels[kind])


def open_vector(source, *, max_bytes=16 * 1024 * 1024, max_features=10000, max_coordinates=100000,
                max_cells=1000000):
    """Read a local FeatureCollection into an optional GeoDataFrame without I/O plugins.

    Budgets bound input bytes/features/positions/dense table cells, not total RSS. No
    remote URI, driver discovery, area calculation, repair or reprojection occurs.
    Source feature records preserve IDs, null versus missing and all properties;
    dataframe columns are object-typed, with absent properties shown as None.
    """
    for value, maximum in ((max_bytes, 256 * 1024 * 1024), (max_features, 100000),
                           (max_coordinates, 1000000), (max_cells, 1000000)):
        if type(value) is not int or not 1 <= value <= maximum:
            raise InvalidRequestError("Vector budgets require positive bounded integers")
    artifact = source if isinstance(source, AnalysisArtifact) else None
    if artifact and artifact.export.media_type.split(";", 1)[0].strip() != "application/geo+json":
        raise UnsupportedOperationError("Vector analysis requires native application/geo+json")
    path = Path(artifact.export.path if artifact else source).resolve(strict=True)
    if not path.is_file() or path.stat().st_size > max_bytes:
        raise InvalidRequestError("Vector analysis requires a local file within the byte budget")
    with path.open("rb") as stream:
        raw = stream.read(max_bytes + 1)
    if len(raw) > max_bytes:
        raise InvalidRequestError("GeoJSON byte budget exceeded while reading")
    digest = hashlib.sha256(raw).hexdigest()
    if artifact and digest != artifact.sha256:
        raise ProtocolError("Analysis artifact bytes changed since export")
    try:
        payload = json.loads(raw.decode("utf-8"), object_pairs_hook=_pairs)
        records = _freeze(payload)  # Also bounds nesting and rejects nonfinite properties.
    except (UnicodeError, ValueError, RecursionError):
        raise ProtocolError("Vector analysis requires valid finite UTF-8 JSON") from None
    if not isinstance(payload, dict) or payload.get("type") != "FeatureCollection" or "crs" in payload:
        raise ProtocolError("Vector analysis requires an RFC7946 FeatureCollection without legacy CRS")
    features = payload.get("features")
    if not isinstance(features, list):
        raise ProtocolError("FeatureCollection requires a features array")
    if len(features) > max_features:
        raise InvalidRequestError("GeoJSON feature budget exceeded")
    remaining = [max_coordinates]
    names = {}
    for feature in features:
        if (not isinstance(feature, dict) or feature.get("type") != "Feature" or "crs" in feature or
                "geometry" not in feature or "properties" not in feature):
            raise ProtocolError("GeoJSON requires Feature records with geometry and properties")
        if "id" in feature and type(feature["id"]) not in (str, int, float):
            raise ProtocolError("GeoJSON feature IDs must be strings or numbers")
        properties = feature["properties"]
        if properties is not None and not isinstance(properties, dict):
            raise ProtocolError("GeoJSON properties must be an object or null")
        names.update(dict.fromkeys(properties or {}))
        if feature["geometry"] is not None:
            _geometry(feature["geometry"], remaining)
    if len(features) * (len(names) + 1) > max_cells:
        raise InvalidRequestError("GeoJSON dense dataframe cell budget exceeded")
    try:
        import geopandas as gpd
        import pandas as pd
        from shapely.geometry import shape
        from shapely.errors import GEOSException
    except ImportError:
        raise UnsupportedOperationError("Install klab-python-client[vector] for vector analysis") from None
    geometries = []
    for feature in features:
        try:
            geometry = shape(feature["geometry"]) if feature["geometry"] is not None else None
        except (ValueError, TypeError, IndexError, OverflowError, GEOSException):
            raise ProtocolError("Malformed GeoJSON geometry; no repair attempted") from None
        if geometry is not None and (geometry.is_empty or not geometry.is_valid):
            raise ProtocolError("Invalid GeoJSON topology; no repair attempted")
        geometries.append(geometry)
    column = "geometry"
    while column in names:
        column = "_" + column
    frame = pd.DataFrame({name: pd.Series([(f["properties"] or {}).get(name) for f in features], dtype=object)
                          for name in names}, index=range(len(features)))
    frame[column] = gpd.GeoSeries(geometries, index=frame.index, crs="OGC:CRS84")
    frame = gpd.GeoDataFrame(frame, geometry=column)
    return VectorAnalysis(frame, records["features"], column, max_coordinates - remaining[0], digest,
                          artifact, datetime.now(timezone.utc))
