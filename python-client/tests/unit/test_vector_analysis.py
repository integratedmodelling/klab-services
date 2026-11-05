import builtins
from dataclasses import replace
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

import pytest

from klab_client import AnalysisArtifact, ExportResult, open_vector
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


def feature(geometry=None, **extra):
    return {"type": "Feature", "properties": {}, "geometry": geometry, **extra}


def write(tmp_path, features, **extra):
    path = tmp_path / "native.geojson"
    path.write_text(json.dumps({"type": "FeatureCollection", "features": features, **extra}), encoding="utf-8")
    return path


def point():
    return {"type": "Point", "coordinates": [12, 45]}


def ring():
    return [[0, 0], [4, 0], [4, 4], [0, 4], [0, 0]]


def artifact(path):
    # Association identity is not inspected/reinterpreted by the local adapter.
    return AnalysisArtifact(ExportResult(path, "geojson", "application/geo+json", path.stat().st_size),
        None, datetime.now(timezone.utc), None, hashlib.sha256(path.read_bytes()).hexdigest())


def test_preserves_holes_multiparts_ids_properties_and_no_geometry_collision(tmp_path):
    hole = [[1, 1], [1, 2], [2, 2], [2, 1], [1, 1]]
    polygon = {"type": "MultiPolygon", "coordinates": [[ring(), hole],
        [[[10, 10], [11, 10], [11, 11], [10, 11], [10, 10]]]]}
    properties = {"geometry": "native", "_geometry": 0, "nullable": None,
        "false": False, "zero": 0, "big": 9007199254740993, "nested": {"token": "not-a-credential", "values": [0, False, None]}}
    path = write(tmp_path, [feature(polygon, id=9007199254740993, properties=properties),
                            feature(None, id="transient:original", properties=None), feature(point())])
    result = open_vector(path)
    assert result.geometry_column == "__geometry"
    assert result.dataframe.crs.to_string() == "OGC:CRS84"
    geometry = result.dataframe.geometry.iloc[0]
    assert len(geometry.geoms) == 2 and len(geometry.geoms[0].interiors) == 1
    assert geometry.area == 16  # Independent planar fixture test only; not an API area claim.
    assert result.dataframe.iloc[0]["false"] is False
    assert result.dataframe.iloc[0]["zero"] == 0
    assert result.dataframe.iloc[0]["big"] == 9007199254740993
    assert result.dataframe.iloc[0]["geometry"] == "native"
    assert result.features[0]["id"] == 9007199254740993
    assert result.features[0]["properties"]["nested"]["token"] == "not-a-credential"
    assert result.features[0]["properties"]["nested"]["values"] == (0, False, None)
    assert result.features[1]["geometry"] is None and result.features[1]["properties"] is None
    assert "id" not in result.features[2] and "nullable" not in result.features[2]["properties"]
    with pytest.raises(TypeError):
        result.features[0]["properties"]["zero"] = 1
    assert result.sha256 == hashlib.sha256(path.read_bytes()).hexdigest()
    assert result.coordinate_count == 16 and result.artifact is None and "unknown" in result.consistency


@pytest.mark.parametrize("geometry", [point(), {"type": "Point", "coordinates": [1, 2, 3]},
    {"type": "MultiPoint", "coordinates": [[0, 0], [1, 1]]},
    {"type": "LineString", "coordinates": [[0, 0], [1, 1]]},
    {"type": "MultiLineString", "coordinates": [[[0, 0], [1, 1]]]},
    {"type": "Polygon", "coordinates": [ring()]},
    {"type": "GeometryCollection", "geometries": [point()]}])
def test_supported_geometry_shapes_are_not_repaired(tmp_path, geometry):
    result = open_vector(write(tmp_path, [feature(geometry)]))
    assert result.dataframe.geometry.iloc[0].geom_type == geometry["type"]


def test_empty_collection_null_geometry_duplicate_ids_and_artifact(tmp_path):
    result = open_vector(write(tmp_path, []))
    assert result.dataframe.empty and result.features == () and result.coordinate_count == 0
    path = write(tmp_path, [feature(None, id=0), feature(None, id=0)])
    source = artifact(path)
    result = open_vector(source)
    assert result.artifact is source and result.dataframe.geometry.isna().all()
    assert [f["id"] for f in result.features] == [0, 0]  # No inferred deduplication.
    with pytest.raises(UnsupportedOperationError):
        open_vector(replace(source, export=replace(source.export, media_type="image/tiff")))
    with pytest.raises(ProtocolError):
        open_vector(replace(source, sha256="0" * 64))


@pytest.mark.parametrize("name,value", [("max_bytes", 0), ("max_bytes", True), ("max_bytes", 268435457),
    ("max_features", -1), ("max_features", 100001), ("max_coordinates", 0), ("max_coordinates", 1000001),
    ("max_cells", True), ("max_cells", 1000001)])
def test_invalid_budgets(tmp_path, name, value):
    with pytest.raises(InvalidRequestError):
        open_vector(write(tmp_path, []), **{name: value})


def test_input_budgets(tmp_path):
    with pytest.raises(InvalidRequestError):
        open_vector(write(tmp_path, []), max_bytes=1)
    with pytest.raises(InvalidRequestError):
        open_vector(tmp_path)
    with pytest.raises(InvalidRequestError):
        open_vector(write(tmp_path, [feature(), feature()]), max_features=1)
    with pytest.raises(InvalidRequestError):
        open_vector(write(tmp_path, [feature({"type": "MultiPoint", "coordinates": [[0, 0], [1, 1]]})]), max_coordinates=1)
    with pytest.raises(InvalidRequestError, match="cell budget"):
        open_vector(write(tmp_path, [feature(properties={"a": 0}), feature(properties={"b": False})]), max_cells=5)


@pytest.mark.parametrize("raw", [b"not json", b"\xff", b'{"type":"FeatureCollection","type":"other","features":[]}',
    b'{"type":"FeatureCollection","features":[],"foreign":NaN}',
    b'{"type":"FeatureCollection","features":[],"foreign":1e400}',
    b"[" * 1100 + b"]" * 1100,
    json.dumps({"type": "FeatureCollection", "features": [], "foreign": [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]] }).encode()])
def test_invalid_json_or_nesting(tmp_path, raw):
    path = tmp_path / "invalid.json"
    path.write_bytes(raw)
    with pytest.raises(ProtocolError):
        open_vector(path)


@pytest.mark.parametrize("payload", [[], {}, {"type": "Feature"},
    {"type": "FeatureCollection", "crs": None, "features": []},
    {"type": "FeatureCollection", "features": {}},
    {"type": "FeatureCollection", "features": [None]},
    {"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {}}]},
    {"type": "FeatureCollection", "features": [feature(crs={})]},
    {"type": "FeatureCollection", "features": [feature(properties=[])]},
    {"type": "FeatureCollection", "features": [feature(id=True)]}])
def test_bad_collection_feature_contracts(tmp_path, payload):
    path = tmp_path / "invalid.json"
    path.write_text(json.dumps(payload))
    with pytest.raises(ProtocolError):
        open_vector(path)


@pytest.mark.parametrize("geometry", [[], {"type": "Point", "crs": None, "coordinates": [1, 2]},
    {"type": "GeometryCollection", "geometries": {}},
    {"type": "Point", "coordinates": "1,2"}, {"type": "Point", "coordinates": [1]},
    {"type": "Point", "coordinates": [True, 2]}, {"type": "Point", "coordinates": [181, 2]},
    {"type": "Point", "coordinates": [1, 91]}, {"type": "Point", "coordinates": ["1", 2]},
    {"type": "LineString", "coordinates": []}, {"type": "LineString", "coordinates": [[0, 0]]},
    {"type": "LineString", "coordinates": [[0, 0], [1, 1, 2]]},
    {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 1]]]},
    {"type": "Polygon", "coordinates": [[[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]]]},
    {"type": "GeometryCollection", "geometries": []}])
def test_bad_geometry_contracts_or_topology(tmp_path, geometry):
    with pytest.raises(ProtocolError):
        open_vector(write(tmp_path, [feature(geometry)]))


def test_unknown_type_and_deep_geometry_collection(tmp_path):
    with pytest.raises(UnsupportedOperationError):
        open_vector(write(tmp_path, [feature({"type": "Triangle", "coordinates": []})]))
    geometry = point()
    for _ in range(18):
        geometry = {"type": "GeometryCollection", "geometries": [geometry]}
    with pytest.raises(ProtocolError):
        open_vector(write(tmp_path, [feature(geometry)]))


def test_coordinate_double_precision_is_explicit(tmp_path):
    with pytest.raises(UnsupportedOperationError, match="exact double"):
        open_vector(write(tmp_path, [feature({"type": "Point", "coordinates": [1, 2, 9007199254740993]})]))
    result = open_vector(write(tmp_path, [feature({"type": "Point", "coordinates": [1.25, -2.5, 9007199254740992]})]))
    assert tuple(result.dataframe.geometry.iloc[0].coords[0]) == (1.25, -2.5, 9007199254740992)


def test_missing_optional_dependency_is_explicit(tmp_path, monkeypatch):
    original = builtins.__import__
    def blocked(name, *args, **kwargs):
        if name == "geopandas":
            raise ImportError()
        return original(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", blocked)
    with pytest.raises(UnsupportedOperationError, match=r"\[vector\]"):
        open_vector(write(tmp_path, [feature(point())]))


def test_growing_file_is_bounded(tmp_path, monkeypatch):
    path = write(tmp_path, [])
    original = Path.open
    def grow(self, *args, **kwargs):
        if self == path and args == ("rb",):
            with original(self, "ab") as stream:
                stream.write(b" " * 100)
        return original(self, *args, **kwargs)
    monkeypatch.setattr(Path, "open", grow)
    with pytest.raises(InvalidRequestError):
        open_vector(path, max_bytes=path.stat().st_size)


def test_geometry_backend_failure_is_not_repaired(tmp_path, monkeypatch):
    import shapely.geometry
    def fail(value):
        raise ValueError("backend details are not exposed")
    monkeypatch.setattr(shapely.geometry, "shape", fail)
    with pytest.raises(ProtocolError, match="no repair"):
        open_vector(write(tmp_path, [feature(point())]))
