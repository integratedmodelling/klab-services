"""Local snapshot contracts, not a new Java execution or scientific provider oracle."""
from dataclasses import replace
from decimal import Decimal
import json
import re
from pathlib import Path

import pytest

from klab_client import ScientificData, ScientificDataOrigin
from klab_client.api.runtime import GeometryImpl
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


def sample(**changes):
    data = ScientificData(42, "geography:Elevation in m", "m", GeometryImpl(),
        (4, 0, 4, 9), "D2_YX", {"type": "INITIALIZATION", "start": 0},
        (9007199254740993, Decimal("1.2300"), 0, None),
        ("9007199254740993", "1.2300", "0", "null"),
        source_observable="geography:Elevation in m", source_units="m",
        origin=ScientificDataOrigin("https://runtime.invalid/runtime", "s.c", "s.c.8#9", "home"),
        artifact_type="NUMBER", contextualization="MEASURE")
    return replace(data, **changes)


def test_independent_known_values_metadata_and_detached_roundtrip():
    source = sample()
    encoded = source.dumps()
    restored = ScientificData.loads(encoded)
    assert restored.offsets == (4, 0, 4, 9)
    assert restored.values[0] == 2**53 + 1 and type(restored.values[0]) is int
    assert restored.values[1].as_tuple() == Decimal("1.2300").as_tuple()
    assert restored.values[2:] == (0, None)
    assert restored.text == source.text
    assert restored.units == restored.source_units == "m"
    assert restored.observable == restored.source_observable == source.observable
    assert restored.origin == source.origin
    assert restored.geometry.to_wire() == source.geometry.to_wire()
    restored.slice["start"] = 99
    restored.geometry.raw["scalar"] = False
    assert source.slice["start"] == 0 and source.geometry.is_scalar()
    assert "values" not in json.loads(encoded)


def test_independently_authored_v1_fixture_without_client_serializer():
    path = Path(__file__).parents[1] / "fixtures/scientific-snapshot-v1.json"
    data = ScientificData.loads(path.read_text(encoding="utf-8"))
    assert data.offsets == (3, 0, 3, 1)
    assert data.values == (9007199254740993, 0, Decimal("1.2300"), None)
    assert data.geometry.shape == (2, 2)
    assert data.geometry.raw["dimensions"][0]["parameters"]["projection"] == "EPSG:3857"
    assert data.geometry.raw["futureDescription"] == "retained, not interpreted"
    assert data.slice == {"type": "INITIALIZATION", "start": 0, "end": 0}
    assert data.origin.runtime_id is None


@pytest.mark.parametrize("kind,text,expected", [
    ("BOOLEAN", ("false", "null", "true"), (False, None, True)),
    ("TEXT", ("", "null", "café雪"), ("", None, "café雪")),
    ("NUMBER", ("-0.0", "NaN", "Infinity"), (0, Decimal("NaN"), Decimal("Infinity"))),
])
def test_primitive_wire_semantics_and_unknown_metadata(kind, text, expected):
    source = sample(artifact_type=kind, text=text, values=expected, offsets=(0, 1, 1),
        units=None, source_units=None, source_observable=None, origin=None,
        geometry=None, contextualization=None)
    restored = ScientificData.loads(source.dumps())
    for actual, value in zip(restored.values, expected):
        assert (actual.as_tuple() == value.as_tuple() if isinstance(value, Decimal)
                else actual == value and type(actual) is type(value))
    assert restored.text == text
    assert restored.origin is restored.geometry is restored.units is None
    assert restored.source_units is restored.source_observable is restored.contextualization is None


@pytest.mark.parametrize("budget", [0, True, 1.5, 16 * 1024**2 + 1])
def test_bad_budget(budget):
    with pytest.raises(InvalidRequestError):
        sample().dumps(max_bytes=budget)
    with pytest.raises(InvalidRequestError):
        ScientificData.loads("{}", max_bytes=budget)


def test_exact_byte_budget_and_unicode_input():
    encoded = sample().dumps()
    assert sample().dumps(max_bytes=len(encoded)) == encoded
    assert ScientificData.loads(encoded, max_bytes=len(encoded)).offsets == (4, 0, 4, 9)
    with pytest.raises(InvalidRequestError):
        sample().dumps(max_bytes=len(encoded) - 1)
    with pytest.raises(InvalidRequestError):
        sample().dumps(max_bytes=1)
    with pytest.raises(InvalidRequestError):
        ScientificData.loads(encoded, max_bytes=len(encoded) - 1)
    text = json.dumps(json.loads(encoded), ensure_ascii=False).replace('"m"', '"雪"')
    with pytest.raises(InvalidRequestError):
        ScientificData.loads(text, max_bytes=len(text))


@pytest.mark.parametrize("text", [None, b"{}", "[]", "{", '"secret"', "\ud800", "[" * 2000,
    '{"version":1,"version":1}', '{"metadata":{"units":1,"units":2}}'])
def test_malformed_json_is_rejected_without_echo(text):
    with pytest.raises(InvalidRequestError) as error:
        ScientificData.loads(text)
    assert "secret" not in str(error.value)


@pytest.mark.parametrize("change", [
    {"version": True}, {"version": 2}, {"kind": "remote-handle"}, {"extra": "ignored?"},
    {"metadata": []}, {"metadata": {}}, {"offsets": []}, {"offsets": [0] * 257},
    {"offsets": "0"}, {"text": "0"}, {"text": ["0"]}, {"text": [1, 2, 3, 4]},
])
def test_strict_schema_and_column_validation(change):
    payload = json.loads(sample().dumps())
    payload.update(change)
    with pytest.raises(InvalidRequestError):
        ScientificData.loads(json.dumps(payload))


@pytest.mark.parametrize("field,value", [
    ("origin", {"runtime_url": "https://user:secret@runtime.invalid", "context_id": "s.c", "scope": "s.c"}),
    ("origin", {"credential": "secret"}), ("origin", []),
    ("geometry", {"dimensions": [1]}), ("geometry", {"dimensions": [{"shape": [-1]}]}),
    ("slice", {"x": float("nan")}), ("curve", ""), ("observation_id", True),
])
def test_invalid_metadata_is_not_blessed_as_remote_authority(field, value):
    payload = json.loads(sample().dumps())
    payload["metadata"][field] = value
    with pytest.raises((InvalidRequestError, ProtocolError)):
        ScientificData.loads(json.dumps(payload))


def test_invalid_scientific_meaning_reuses_existing_fail_closed_validation():
    with pytest.raises(ProtocolError):
        sample(values=(1, Decimal("1.2300"), 0, None)).dumps()
    with pytest.raises(ProtocolError):
        sample(values=(Decimal("1"), Decimal("1.2300"), 0, None)).dumps()
    with pytest.raises(UnsupportedOperationError):
        sample(contextualization="CATEGORIZATION").dumps()
    payload = json.loads(sample().dumps())
    payload["metadata"]["artifact_type"] = "FUTURE"
    with pytest.raises(UnsupportedOperationError):
        ScientificData.loads(json.dumps(payload))
    payload["metadata"]["artifact_type"] = "BOOLEAN"
    with pytest.raises(ProtocolError):
        ScientificData.loads(json.dumps(payload))


@pytest.mark.parametrize("metadata", [{1: "a", "1": "b"}, {1: "a"}, {"nested": [{False: "b"}]}])
def test_metadata_keys_are_not_silently_coerced_or_collapsed(metadata):
    with pytest.raises(InvalidRequestError, match="keys must be strings"):
        sample(slice=metadata).dumps()


def test_reconciliation_has_unique_requirements_and_one_primary_status():
    path = Path(__file__).resolve().parents[3] / "docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md"
    rows = re.findall(r"^\| ((?:RC-|CAP-)[\w.-]+) \|.*$", path.read_text(encoding="utf-8"), re.M)
    assert len(rows) == len(set(rows)) == 42
    statuses = {"ACCEPTED", "IMPLEMENTED_UNVERIFIED", "PARTIAL", "NOT_IMPLEMENTED",
                "BLOCKED_EXTERNAL", "DEFERRED_APPROVED"}
    lines = [line for line in path.read_text(encoding="utf-8").splitlines() if re.match(r"\| (RC-|CAP-)", line)]
    for line in lines:
        assert len(statuses.intersection(line.split(" | "))) == 1
