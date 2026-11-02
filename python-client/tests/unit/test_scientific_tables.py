"""Exact local view semantics; no provider or ecological validation is implied."""
import asyncio
import builtins
from dataclasses import FrozenInstanceError
from decimal import Decimal
import json
from pathlib import Path

import httpx
import pytest

from klab_client import (AsyncClient, AsyncContext, Client, Context, ScientificData,
                         ScientificDataOrigin, to_dataframe)
from klab_client.dto import observation_from_wire
from klab_client.errors import (ConfigurationError, InvalidRequestError, ProtocolError,
                               UnsupportedOperationError)
from klab_client.experiment import rectangle_geometry


def sample(**kwargs):
    values = dict(observation_id=42, observable="geography:Elevation in m", units="m",
        geometry=rectangle_geometry(), offsets=(0, 2, 2), curve="D2_YX",
        slice={"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0},
        values=(0, None, Decimal("123.25")), text=("0", "null", "123.25"),
        source_observable="geography:Elevation in m", source_units="m", artifact_type="NUMBER", contextualization="MEASURE",
        origin=ScientificDataOrigin("https://runtime.invalid/runtime", "s.c", "s.c.8#9", "home"))
    values.update(kwargs)
    return ScientificData(**values)


def test_indexed_frame_preserves_order_duplicates_zero_missing_and_metadata():
    source = sample()
    frame = source.to_dataframe()
    assert list(frame.columns) == ["offset", "value", "wire_missing", "wire_text"]
    assert frame["offset"].tolist() == [0, 2, 2]
    assert frame["value"].tolist() == [0, None, Decimal("123.25")]
    assert type(frame.at[0, "value"]) is int and frame.at[1, "value"] is None
    assert str(frame["value"].dtype) == str(frame["offset"].dtype) == "object"
    assert frame["wire_missing"].tolist() == [False, True, False]
    metadata = frame.attrs["klab"]
    assert metadata["origin"] == {"runtime_url": "https://runtime.invalid/runtime", "context_id": "s.c",
                                   "scope": "s.c.8#9", "runtime_id": "home"}
    assert metadata["units"] == metadata["source_units"] == "m"
    assert metadata["curve"] == "D2_YX" and metadata["slice"] == source.slice
    assert "no committed-revision" in metadata["consistency"]
    assert "no inferred world coordinates" in metadata["coordinates"]
    metadata["slice"]["key"] = "changed only in the view"
    metadata["geometry"]["dimensions"][0]["shape"][0] = 999
    assert source.slice["key"] == "0-0"
    assert source.geometry.to_wire()["dimensions"][0]["shape"][0] == 5


def test_large_integer_decimal_nonfinite_and_unknown_metadata_are_not_coerced():
    huge = 2**80 + 1
    source = sample(offsets=(0, 1, 2), values=(huge, Decimal("1.2300"), Decimal("NaN")),
        text=(str(huge), "1.2300", "NaN"), origin=None, geometry=None, units=None,
        source_units=None, source_observable=None, contextualization=None)
    frame = to_dataframe(source)
    assert frame.at[0, "value"] == huge and type(frame.at[0, "value"]) is int
    assert frame.at[1, "value"].as_tuple() == Decimal("1.2300").as_tuple()
    assert frame.at[2, "value"].is_nan()
    assert frame["wire_missing"].tolist() == [False, False, False]
    metadata = frame.attrs["klab"]
    for key in ("origin", "geometry", "units", "source_units", "source_observable", "contextualization"):
        assert metadata[key] is None


@pytest.mark.parametrize("kind,values,text", [
    ("BOOLEAN", (False, None, True), ("false", "null", "true")),
    ("TEXT", ("zero", None, "NULL"), ("zero", "null", "NULL")),
])
def test_boolean_false_and_literal_text_are_not_missing(kind, values, text):
    frame = to_dataframe(sample(artifact_type=kind, values=values, text=text, units=None))
    assert frame["value"].tolist() == list(values)
    assert frame["wire_missing"].tolist() == [False, True, False]
    assert type(frame.at[0, "value"]) is type(values[0])


@pytest.mark.parametrize("change", [
    {"offsets": []}, {"values": []}, {"text": []}, {"offsets": ()}, {"values": (1,)},
    {"text": ("0",)}, {"observation_id": 0}, {"observation_id": True},
    {"offsets": (-1, 0, 1)}, {"offsets": (True, 0, 1)},
    {"observable": ""}, {"observable": None}, {"curve": ""}, {"curve": None},
    {"slice": None}, {"units": 5}, {"source_units": 5}, {"source_observable": 5},
    {"origin": {}}, {"geometry": {}}, {"contextualization": 5},
])
def test_invalid_view_contract_fails_before_constructing_a_frame(change):
    with pytest.raises(InvalidRequestError):
        to_dataframe(sample(**change))


@pytest.mark.parametrize("budget", [0, True, 257, 1.5])
def test_row_budget_is_explicit(budget):
    with pytest.raises(InvalidRequestError):
        to_dataframe(sample(), max_rows=budget)


def test_no_silent_partial_table_or_unknown_input_type():
    with pytest.raises(InvalidRequestError):
        to_dataframe(sample(), max_rows=2)
    with pytest.raises(InvalidRequestError):
        to_dataframe({"values": [1]})


@pytest.mark.parametrize("budget", [0, True, 16 * 1024 * 1024 + 1, .5])
def test_metadata_budget_is_explicit(budget):
    with pytest.raises(InvalidRequestError):
        to_dataframe(sample(), max_metadata_bytes=budget)


def test_metadata_budget_overflow_and_non_json_data_fail_closed():
    with pytest.raises(InvalidRequestError, match="byte budget"):
        to_dataframe(sample(), max_metadata_bytes=1)
    for value in (float("inf"), object()):
        with pytest.raises(InvalidRequestError, match="finite JSON"):
            to_dataframe(sample(slice={"bad": value}))
    recursive = {}
    recursive["cycle"] = recursive
    with pytest.raises(InvalidRequestError, match="finite JSON"):
        to_dataframe(sample(slice=recursive))


@pytest.mark.parametrize("kind", [None, "CONCEPT", "CATEGORY", []])
def test_unsupported_categories_never_become_numeric_measurements(kind):
    with pytest.raises(UnsupportedOperationError, match="dictionary/worldview"):
        to_dataframe(sample(artifact_type=kind))


def test_known_categorization_is_rejected_even_with_numeric_storage():
    with pytest.raises(UnsupportedOperationError, match="dictionary/worldview"):
        to_dataframe(sample(contextualization="CATEGORIZATION"))


@pytest.mark.parametrize("changes", [
    {"text": (1, "null", "123.25")}, {"text": ("null", "null", "123.25")},
    {"values": (0, 5, Decimal("123.25"))}, {"values": (False, None, Decimal("123.25"))},
    {"values": (0., None, Decimal("123.25"))}, {"values": (0, None, Decimal("3.25"))},
    {"text": ("1", "null", "123.25")}, {"text": ("0.25", "null", "123.25")},
    {"values": (0, None, Decimal("3")), "text": ("0", "null", "3")},
    {"text": ("0", "null", "not-a-number")},
    {"artifact_type": "BOOLEAN", "values": (0, None, True), "text": ("false", "null", "true")},
    {"artifact_type": "BOOLEAN", "values": (False, None, True), "text": ("true", "null", "true")},
    {"artifact_type": "TEXT", "values": (0, None, "x"), "text": ("0", "null", "x")},
])
def test_values_must_match_declared_type_and_original_wire_text(changes):
    with pytest.raises(ProtocolError):
        to_dataframe(sample(**changes))


def test_optional_pandas_dependency_is_not_silently_replaced(monkeypatch):
    original = builtins.__import__
    def without_pandas(name, *args, **kwargs):
        if name == "pandas":
            raise ImportError("not installed")
        return original(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", without_pandas)
    with pytest.raises(UnsupportedOperationError, match=r"\[tabular\]"):
        to_dataframe(sample())


@pytest.mark.parametrize("url,context,scope,runtime", [
    (None, "s.c", "s.c", None), ("https://runtime.invalid", "s.c", None, None),
    ("https://runtime.invalid", "s.c", "", None),
    ("https://runtime.invalid", "s.c", "other.context", None),
    ("https://runtime.invalid", "s.c.8", "s.c.8", None),
    ("https://runtime.invalid", None, "s.c", None),
    ("https://runtime.invalid", "s.c", "s.c", ""),
    ("https://runtime.invalid", "s.c", "s.c", 1),
])
def test_read_origin_never_invents_a_missing_or_conflicting_identity(url, context, scope, runtime):
    with pytest.raises(InvalidRequestError):
        ScientificDataOrigin(url, context, scope, runtime)


def test_read_origin_normalizes_endpoint_and_can_preserve_unknown_runtime_id():
    origin = ScientificDataOrigin("https://runtime.invalid:443/runtime/", "s.c", "s.c")
    assert origin.runtime_url == "https://runtime.invalid/runtime" and origin.runtime_id is None
    with pytest.raises(FrozenInstanceError):
        origin.scope = "other.context"
    with pytest.raises(ConfigurationError):
        ScientificDataOrigin("https://runtime.invalid?secret=bad", "s.c", "s.c")


def test_explicit_event_support_is_recorded_without_generating_a_time_axis():
    event = {"type": "EVENT", "key": "event-17", "start": 1700, "end": 1900}
    frame = to_dataframe(sample(slice=event, curve="D3_XYZ"))
    assert frame.attrs["klab"]["slice"] == event
    assert frame.attrs["klab"]["curve"] == "D3_XYZ"
    assert list(frame.index) == [0, 1, 2]
    assert not any(column in frame.columns for column in ("time", "x", "y", "latitude", "longitude"))


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("case", ["normal", "unknown_runtime", "identity_drift", "credential_echo"])
def test_read_origin_is_captured_from_real_request_context_not_guessed_from_ids(mode, case):
    wire = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    calls = []
    holder = {}
    def handler(request):
        calls.append(request)
        if case == "identity_drift":
            holder["client"]._service_id_cache["runtime"] = "changed"
        return httpx.Response(200, text="0")
    cls = Client if mode == "sync" else AsyncClient
    api = cls("https://runtime.invalid/runtime", "secret", runtime_service_id="home" if case == "normal" else None,
              http_transport=httpx.MockTransport(handler))
    holder["client"] = api
    context = (Context if mode == "sync" else AsyncContext)(api, {"id": "secret.c" if case == "credential_echo" else "s.c"})
    obs = observation_from_wire(wire, context)
    def check(data):
        assert data.artifact_type == "NUMBER"
        assert data.origin == ScientificDataOrigin("https://runtime.invalid/runtime", "s.c", "s.c",
                                                   "home" if case == "normal" else None)
        assert data.to_dataframe().at[0, "value"] == 0
    async def asynchronous():
        async with api:
            if case in {"identity_drift", "credential_echo"}:
                with pytest.raises(ProtocolError):
                    await obs.fetch_data([0])
            else:
                check(await obs.fetch_data([0]))
    if mode == "async":
        asyncio.run(asynchronous())
    else:
        with api:
            if case in {"identity_drift", "credential_echo"}:
                with pytest.raises(ProtocolError):
                    obs.fetch_data([0])
            else:
                check(obs.fetch_data([0]))
    assert len(calls) == (0 if case == "credential_echo" else 1)
