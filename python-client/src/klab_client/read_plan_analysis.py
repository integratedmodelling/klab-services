"""Local analysis of complete proposed snapshots, never native committed science.

JAVA-GAP-0006/0014. Revalidates detached content offline without acquiring a grant,
touching a Runtime or promoting a content hash to a scientific commit identity.
"""
from dataclasses import dataclass
import math
import hashlib
import json
from typing import Any

from .analysis import DEFAULT_ANALYSIS_BYTES
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from .handles import Handle
from .read_plans import (PROTOCOL, ReadPlanData, ReadPlan, ReadShardSpec, _plan, _shard,
    assemble_read_plan)


@dataclass(frozen=True)
class ReadPlanAnalysis:
    dataset: Any
    snapshot: ReadPlanData
    estimated_bytes: int
    manifest_sha256: str
    consistency: str = "PROPOSED_REFERENCE_SNAPSHOT_NOT_NATIVE_COMMITTED_SCIENCE"


def analyze_read_plan(snapshot, *, max_bytes=DEFAULT_ANALYSIS_BYTES):
    """Create detached float64values/exact boolmask and centre coordinates offline.

    Invalid reference cells become NaN in the array, remain None in the immutable
    source and retain false mask. No units/CRS conversion, resampling or inference.
    """
    if (not isinstance(snapshot, ReadPlanData) or not isinstance(snapshot.plan, ReadPlan)
            or type(snapshot.values) is not tuple or type(snapshot.valid) is not tuple
            or len(snapshot.values) > 4096 or len(snapshot.valid) > 4096
            or snapshot.consistency != "PROPOSED_REFERENCE_SNAPSHOT_NOT_NATIVE_COMMITTED_SCIENCE"):
        raise InvalidRequestError("Analysis requires a complete explicit immutable ReadPlanData")
    if type(max_bytes) is not int or max_bytes <= 0:
        raise InvalidRequestError("Snapshot analysis budget must be a positive integer")
    plan = snapshot.plan
    if (not isinstance(plan.source, Handle) or plan.source.kind != "observation"
            or type(plan.shape) is not tuple or len(plan.shape) != 2
            or type(plan.transform) is not tuple or len(plan.transform) != 6
            or type(plan.shards) is not tuple or not 1 <= len(plan.shards) <= 4096
            or any(not isinstance(spec, ReadShardSpec) for spec in plan.shards)
            or type(plan.shards[0].count) is not int or not 1 <= plan.shards[0].count <= 128):
        raise InvalidRequestError("Snapshot requires an exact originating observation and immutable descriptor/shards")
    manifest = {"protocol": PROTOCOL, "plan_id": plan.plan_id, "epoch": plan.epoch, "revision": plan.revision,
        "revision_kind": plan.revision_kind, "observation_id": plan.source.observation_id,
        "scope": plan.source.scope, "runtime_id": plan.source.runtime_id, "dtype": plan.dtype,
        "shape": list(plan.shape), "crs": plan.crs, "transform": list(plan.transform), "order": plan.order,
        "units": plan.units, "fixture_origin": plan.fixture_origin,
        "shards": [{"index": spec.index, "start": spec.start, "count": spec.count, "sha256": spec.sha256} for spec in plan.shards]}
    confirmed = _plan(manifest, plan.context, plan.source, plan.shards[0].count, 4096)
    manifest_sha256 = hashlib.sha256(json.dumps(manifest, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")).hexdigest()
    cells = math.prod(confirmed.shape)
    if len(snapshot.values) != cells or len(snapshot.valid) != cells:
        raise ProtocolError("Snapshot cells/mask do not match complete reference shape")
    height, width = confirmed.shape
    estimate = cells * 256 + len(confirmed.shards) * 2048 + (height + width) * 64 + 32768
    if estimate > max_bytes:
        raise InvalidRequestError("Retained snapshot, arrays and validation buffers exceed analysis budget")
    blocks = []
    for spec in confirmed.shards:
        stop = spec.start + spec.count
        blocks.append(_shard(confirmed, spec.index, {"protocol": PROTOCOL, "plan_id": plan.plan_id,
            "epoch": plan.epoch, "revision": plan.revision, "index": spec.index, "start": spec.start,
            "values": list(snapshot.values[spec.start:stop]), "valid": list(snapshot.valid[spec.start:stop]), "sha256": spec.sha256}))
    assemble_read_plan(confirmed, tuple(blocks))
    try:
        import numpy as np
        import xarray as xr
    except ImportError:
        raise UnsupportedOperationError("Install klab-python-client[analysis] for local snapshot arrays") from None
    a, _, west, _, e, north = confirmed.transform
    x = west + (np.arange(width, dtype="float64") + 0.5) * a
    y = north + (np.arange(height, dtype="float64") + 0.5) * e
    if not np.isfinite(x).all() or not np.isfinite(y).all() or not np.all(x[1:] > x[:-1]) or not np.all(y[1:] < y[:-1]):
        raise ProtocolError("Reference pixel centres are not finite/distinct float64 coordinates")
    values = np.array(snapshot.values, dtype="float64").reshape(confirmed.shape)
    valid = np.array(snapshot.valid, dtype=bool).reshape(confirmed.shape)
    attrs = {"protocol": PROTOCOL, "revision": confirmed.revision, "revision_kind": confirmed.revision_kind,
        "plan_id": confirmed.plan_id, "epoch": confirmed.epoch, "fixture_origin": confirmed.fixture_origin,
        "source_runtime_id": confirmed.source.runtime_id, "source_runtime_url": confirmed.source.runtime_url,
        "source_scope": confirmed.source.scope, "source_observation_id": confirmed.source.observation_id,
        "crs": confirmed.crs, "transform": confirmed.transform, "order": confirmed.order,
        "units": confirmed.units, "consistency": snapshot.consistency, "invalid_cell": "NaN_USE_VALID_MASK",
        "reference_manifest_sha256": manifest_sha256, "manifest_hash_kind": "LOCAL_REFERENCE_DESCRIPTOR_NOT_NATIVE_COMMIT"}
    dataset = xr.Dataset({"value": (("y", "x"), values), "valid": (("y", "x"), valid)},
        coords={"x": x, "y": y}, attrs=attrs)
    return ReadPlanAnalysis(dataset, snapshot, estimate, manifest_sha256)
