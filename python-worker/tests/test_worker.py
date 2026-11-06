"""SIMULATED_PROVISIONAL (JAVA-GAP-0017). No actual Java execution or science claim."""
import json
from pathlib import Path
import sys
import threading

import pytest

from klab_worker import Field, Limits, WorkerClient, WorkerError, execute
from klab_worker.protocol import decoded


def sample():
    return Field((2, 3), ("y", "x"), ((50, -50), (10, 20, 30)), "EPSG:3857", "m",
                 (0, -3.5, None, 7.25, 1.5, 8), (True, True, False, True, True, True))


def script(tmp_path, source):
    file = tmp_path / "trusted.py"
    file.write_text(source)
    return file


def test_exact_envelope_roundtrip_keeps_zero_negative_mask_axes_and_unknown_metadata():
    source = sample()
    assert Field.from_dict(source.to_dict()) == source
    assert source.values[:3] == (0, -3.5, None)
    raw = source.to_dict(); raw.update(crs=None, unit=None)
    assert Field.from_dict(raw).unit is None


@pytest.mark.parametrize("change", ["mask", "shape", "axes", "coordinates", "nan", "bool", "invalid-value", "unknown", "huge-int"])
def test_bad_input_never_becomes_scientific_success(change):
    raw = sample().to_dict()
    if change == "mask": raw["valid"][0] = 1
    elif change == "shape": raw["shape"] = [True, 6]
    elif change == "axes": raw["axes"] = ["x", "x"]
    elif change == "coordinates": raw["coordinates"] = [[1], [2]]
    elif change == "nan": raw["values"][0] = float("nan")
    elif change == "bool": raw["values"][0] = True
    elif change == "invalid-value": raw["values"][2] = 1
    elif change == "huge-int": raw["values"][0] = 10 ** 1000
    else: raw["guessed_revision"] = 1
    with pytest.raises(WorkerError): Field.from_dict(raw)


@pytest.mark.parametrize("payload", [b'{"x":1,"x":2}', b'{"x":NaN}', b'{' , b'"\xff"'])
def test_invalid_wire_json_is_not_silently_coerced(payload):
    with pytest.raises(WorkerError): decoded(payload)


def test_isolated_trusted_computation_has_no_ambient_credentials_or_sdk_import(tmp_path, monkeypatch):
    monkeypatch.setenv("KLAB_TOKEN", "synthetic-do-not-inherit")
    file = script(tmp_path, 'import json,os,sys\nassert "KLAB_TOKEN" not in os.environ\nassert sys.flags.isolated\nx=json.load(sys.stdin)\nx["values"]=[v*2 if ok else None for v,ok in zip(x["values"],x["valid"])]\njson.dump(x,sys.stdout)\n')
    result, digest = execute(file, sample())
    assert result.values == (0, -7, None, 14.5, 3, 16)
    assert result.same_layout(sample()) and len(digest) == 64
    assert "klab_simulator" not in sys.modules


@pytest.mark.parametrize("failure", ["deadline", "cancel", "crash", "layout", "output-budget", "cell-budget"])
def test_failed_computation_never_returns_publishable_output_and_reaps_child(tmp_path, failure):
    source = 'import json,sys\nx=json.load(sys.stdin)\njson.dump(x,sys.stdout)\n'
    limits = Limits()
    cancellation = threading.Event()
    if failure == "deadline":
        source = 'while True: pass\n'; limits = Limits(seconds=.1)
    elif failure == "cancel": cancellation.set()
    elif failure == "crash": source = 'raise RuntimeError("do not disclose child secrets")\n'
    elif failure == "layout": source = 'import json,sys\nx=json.load(sys.stdin);x["unit"]="mm"\njson.dump(x,sys.stdout)\n'
    elif failure == "output-budget": limits = Limits(output_bytes=10)
    elif failure == "cell-budget": limits = Limits(cells=1)
    with pytest.raises(WorkerError): execute(script(tmp_path, source), sample(), limits=limits, cancel=cancellation)


@pytest.mark.parametrize("endpoint", ["http://untrusted.example", "https://u:secret@example.org", "https://example.org?token=secret"])
def test_worker_endpoint_cannot_forward_credentials_to_unsafe_target(endpoint):
    with pytest.raises(WorkerError): WorkerClient(endpoint, "synthetic", scope="scope", allow_experimental=True)


def test_native_unknown_protocol_requires_opt_in_before_network():
    with pytest.raises(WorkerError, match="opt-in"):
        WorkerClient("http://127.0.0.1:1/runtime", "synthetic", scope="scope")


@pytest.mark.parametrize("options", [{"seconds": 0}, {"seconds": True}, {"output_bytes": 0}, {"cells": 4097}])
def test_invalid_resource_limits_cannot_disable_guards(options):
    with pytest.raises(WorkerError): Limits(**options)


def test_mutable_direct_field_and_missing_or_invalid_metadata_fail_closed():
    source = sample()
    with pytest.raises(WorkerError):
        Field(list(source.shape), source.axes, source.coordinates, source.crs, source.unit, source.values, source.valid)
    for key, value in (("shape", []), ("unit", ""), ("coordinates", []), ("values", [])):
        raw = source.to_dict(); raw[key] = value
        with pytest.raises(WorkerError): Field.from_dict(raw)
