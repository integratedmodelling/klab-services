"""Explicit snapshot→array association, not native provenance/history acceptance."""
from dataclasses import replace
import builtins

import pytest

np = pytest.importorskip("numpy", reason="Optional analysis extra")
pytest.importorskip("xarray", reason="Optional analysis extra")

from klab_client import Client
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from klab_client.handles import to_handle
from klab_client.read_plans import _plan, _shard, assemble_read_plan
from klab_client.read_plan_analysis import analyze_read_plan
from test_read_plans import manifest, block, bound, client_options


def snapshot():
    with Client("https://example.test/runtime", "synthetic", **client_options()) as client:
        context, observation = bound(client)
        plan = _plan(manifest(), context, to_handle(observation), 2, 4)
        return assemble_read_plan(plan, tuple(_shard(plan, n, block(n)) for n in (0, 1)))


def test_detached_offline_values_mask_layout_identity_and_nonnative_revision_are_preserved():
    source = snapshot()  # HTTP client already closed; arrays require no live grant/service.
    result = analyze_read_plan(source)
    assert result.dataset.value.dtype == np.dtype("float64")
    assert result.dataset.valid.dtype == np.dtype("bool")
    np.testing.assert_equal(result.dataset.value.values, [[0, -2.5], [np.nan, 7]])
    assert result.dataset.valid.values.tolist() == [[True, True], [False, True]]
    assert result.dataset.x.values.tolist() == [50, 150]
    assert result.dataset.y.values.tolist() == [150, 50]
    attrs = result.dataset.attrs
    assert attrs["source_scope"] == "s.c" and attrs["source_observation_id"] == 1 and attrs["source_runtime_id"] == "runtime"
    assert attrs["revision"] == source.plan.revision and attrs["revision_kind"] == "REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT"
    assert attrs["fixture_origin"] == "SYNTHETIC_SIMULATOR" and attrs["units"] == "m"
    result.dataset.value.values[0, 0] = 99
    assert source.values == (0, -2.5, None, 7)
    assert analyze_read_plan(source).dataset.value.values[0, 0] == 0
    assert attrs["reference_manifest_sha256"] == result.manifest_sha256
    assert attrs["manifest_hash_kind"] == "LOCAL_REFERENCE_DESCRIPTOR_NOT_NATIVE_COMMIT"


@pytest.mark.parametrize("change", [{"values": [0, -2.5, None, 7]}, {"valid": [True] * 4},
    {"values": tuple(range(4097))}, {"consistency": "NATIVE_COMMITTED"}])
def test_incomplete_or_unattributed_input_refuses(change):
    with pytest.raises(InvalidRequestError): analyze_read_plan(replace(snapshot(), **change))


def test_shape_and_descriptor_integrity_are_revalidated_not_just_class_name():
    source = snapshot()
    with pytest.raises(InvalidRequestError): analyze_read_plan(None)
    for change in ({"source": None}, {"shape": [2, 2]}, {"transform": [100, 0, 0, 0, -100, 200]},
        {"shards": []}, {"shards": ()}, {"shards": (None,)}, {"shards": source.plan.shards * 2049}):
        with pytest.raises(InvalidRequestError): analyze_read_plan(replace(source, plan=replace(source.plan, **change)))
    with pytest.raises(ProtocolError): analyze_read_plan(replace(source, values=source.values[:3]))
    with pytest.raises(ProtocolError): analyze_read_plan(replace(source, plan=replace(source.plan, revision="c" * 64)))
    with pytest.raises(ProtocolError): analyze_read_plan(replace(source, values=(1, -2.5, None, 7)))
    with pytest.raises(ProtocolError): analyze_read_plan(replace(source, valid=(False, True, False, True)))
    with pytest.raises(ProtocolError): analyze_read_plan(replace(source, plan=replace(source.plan, order="UNKNOWN")))


@pytest.mark.parametrize("budget", [True, 0, -1, 1])
def test_budget_refuses_before_array_allocation(budget):
    with pytest.raises(InvalidRequestError): analyze_read_plan(snapshot(), max_bytes=budget)


def test_duplicate_float64_coordinates_are_not_silently_accepted():
    source = snapshot()
    plan = replace(source.plan, transform=(1e-20, 0, 1e12, 0, -100, 200))
    with pytest.raises(ProtocolError): analyze_read_plan(replace(source, plan=plan))


def test_missing_optional_analysis_dependencies_has_clear_failure(monkeypatch):
    source = snapshot()
    original = builtins.__import__
    def blocked(name, *args, **kwargs):
        if name == "numpy": raise ImportError("Synthetic missing analysis extra")
        return original(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", blocked)
    with pytest.raises(UnsupportedOperationError): analyze_read_plan(source)


def test_manifest_hash_records_local_descriptor_not_native_metadata_authentication():
    source = snapshot()
    original = analyze_read_plan(source)
    # Caller-forged local metadata must not gain native trust from identical cells.
    different = analyze_read_plan(replace(source, plan=replace(source.plan, units=None)))
    assert different.manifest_sha256 != original.manifest_sha256
    assert different.dataset.attrs["units"] is None
    assert different.dataset.attrs["revision"] == original.dataset.attrs["revision"]
    assert different.consistency == "PROPOSED_REFERENCE_SNAPSHOT_NOT_NATIVE_COMMITTED_SCIENCE"
