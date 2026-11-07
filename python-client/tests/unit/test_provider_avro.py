"""Bounded raw-datum codec; synthetic controls, not native provider execution."""
from copy import deepcopy
import math

import pytest

from klab_client import _provider_avro as codec
from klab_client.errors import InvalidRequestError, ProtocolError


def instance():
    return dict(name="synthetic", geometry="1", observable="synthetic:Value", identity="fixture",
        fillingCurve="DN_LINEAR", empty=False, notifications=[], metadata={"unit": "m"},
        instances=None, doubleData=None, longData=[0, -1, 2**63 - 1], intData=None,
        floatData=None, dataKey=None)


@pytest.mark.parametrize("kind,values", [("long", [-(2**63), -129, -1, 0, 128, 2**63 - 1]),
    ("int", [-(2**31), 0, 2**31 - 1]), ("boolean", [True, False]), ("null", [None]),
    ("string", ["", "UTF8é🌍"]), ("float", [-1.5, 0., 1.25]), ("double", [-1e100, 0., 1.25]),
    (("enum", ("ONE", "TWO")), ["ONE", "TWO"]),
    (("union", ("string", "null")), [None, "value"]),
    (("array", "long"), [[], [1, -2]]), (("map", "string"), [{}, {"a": "b"}])])
def test_raw_datum_roundtrips_without_container_or_schema_loading(kind, values):
    for value in values:
        assert codec._decode(kind, codec._encode(kind, value)) == value


def test_instance_all_fields_and_nested_children_preserve_raw_numeric_channels():
    value = instance(); child = instance(); child["fillingCurve"] = None
    value.update(instances=[child], doubleData=[float("nan"), float("inf"), -2.5],
        intData=[-(2**31), 2**31 - 1], floatData=[1.25], dataKey={"1": "synthetic.category"},
        notifications=[dict(message="fixture", activityUrn="activity", metadata={"a": "b"}, code=7, level="INFO")])
    result = codec._decode(codec._INSTANCE, codec._encode(codec._INSTANCE, value))
    assert result["instances"] == [child] and result["longData"] == value["longData"]
    assert math.isnan(result["doubleData"][0]) and math.isinf(result["doubleData"][1])
    assert result["intData"] == value["intData"] and result["floatData"] == [1.25]
    assert result["dataKey"] == value["dataKey"] and result["notifications"] == value["notifications"]
    value["notifications"] = [dict(message="fixture", activityUrn=None, metadata=None, code=None, level="ERROR")]
    assert codec._decode(codec._INSTANCE, codec._encode(codec._INSTANCE, value))["notifications"] == value["notifications"]


@pytest.mark.parametrize("kind,value", [("boolean", 1), ("string", 1), ("string", "\ud800"),
    ("string", "x" * 65537), ("long", True), ("long", 2**63), ("int", 2**31), ("null", 1),
    ("double", "1"), ("float", 1e100), (("enum", ("ONE",)), "TWO"),
    (("array", "long"), {}), (("map", "long"), []), (codec._INSTANCE, {})])
def test_invalid_typed_requests_refuse_without_echoing_input(kind, value):
    with pytest.raises(InvalidRequestError): codec._encode(kind, value)


@pytest.mark.parametrize("kind,raw", [("long", b""), ("long", b"\x80" * 9 + b"\x02"),
    ("int", codec._long(2**31)), ("boolean", b"\x02"), ("string", b"\x01"),
    ("string", codec._long(65537)), ("string", b"\x02\xff"), ("double", b"x"),
    (("union", ("null", "string")), b"\x04"), (("union", ("null", "string")), b"\x01"),
    (("enum", ("ONE",)), b"\x02"), ("null", b"\x00"), ("long", None),
    (("map", "string"), b"\x04\x02a\x02b\x02a\x02c\x00"),
    (("array", "long"), codec._long(262145)),
    (("array", "long"), codec._long(-1) + codec._long(-1)),
    (("array", "long"), codec._long(-1) + codec._long(100)),
    (("array", "long"), codec._long(-1) + codec._long(0) + b"\x02\x00")])
def test_bad_binary_or_over_budget_counts_fail_before_unbounded_allocation(kind, raw):
    with pytest.raises(ProtocolError): codec._decode(kind, raw)


def test_negative_array_and_map_blocks_require_exact_byte_count():
    assert codec._decode(("array", "long"), b"\x03\x04\x02\x03\x00") == [1, -2]
    assert codec._decode(("map", "string"), b"\x01\x08\x02a\x02b\x00") == {"a": "b"}


def test_request_uses_source_field_order_and_null_union_not_a_container():
    value = dict(observable="synthetic:Value", geometry="1", startTime=0, endTime=0,
        resourceUrns=["urn:synthetic:resource"], inputData=None)
    expected = bytes.fromhex("1e73796e7468657469633a56616c756502310000022c75726e3a73796e7468657469633a7265736f757263650000")
    assert codec._encode(codec._REQUEST, value) == expected
    assert codec._decode(codec._REQUEST, expected) == value
    value["inputData"] = instance()
    assert codec._decode(codec._REQUEST, codec._encode(codec._REQUEST, value)) == value


def test_nesting_and_total_byte_and_item_budgets_are_enforced(monkeypatch):
    value = instance(); value["instances"] = [deepcopy(value)]
    raw = codec._encode(codec._INSTANCE, value)
    monkeypatch.setattr(codec, "MAX_DEPTH", 1)
    with pytest.raises(InvalidRequestError): codec._encode(codec._INSTANCE, value)
    with pytest.raises(ProtocolError): codec._decode(codec._INSTANCE, raw)
    monkeypatch.setattr(codec, "MAX_DEPTH", 16)
    monkeypatch.setattr(codec, "MAX_BYTES", 1)
    with pytest.raises(InvalidRequestError): codec._encode("string", "xx")
    with pytest.raises(ProtocolError): codec._decode("string", b"\x04xx")
    monkeypatch.setattr(codec, "MAX_BYTES", 100)
    monkeypatch.setattr(codec, "MAX_ITEMS", 1)
    with pytest.raises(InvalidRequestError): codec._encode(("array", "long"), [1, 2])
