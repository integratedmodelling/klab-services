"""JAVA-GAP-0006/0014: explicit proposed snapshot shards, NOT native READ_VALUE plans.

No inference, mutation replay, automatic replan or simulator dependency. Reference
content hashes are not native committed scientific revisions.
"""
from dataclasses import dataclass, field
import hashlib
import json
import math
import re

from .api.runtime import ObservationImpl
from .client import Client, Context, scope_token
from .errors import InvalidRequestError, ProtocolError
from .handles import Handle, to_handle

PROTOCOL = "klab.experimental.read-plans/1"
PREFIX = "/experimental/read-plans/v1/"
REVISION_KIND = "REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT"


@dataclass(frozen=True)
class ReadShardSpec:
    index: int
    start: int
    count: int
    sha256: str


@dataclass(frozen=True)
class ReadPlan:
    plan_id: str
    epoch: str
    revision: str
    source: Handle
    shape: tuple
    crs: str
    transform: tuple
    units: str | None
    shards: tuple
    context: Context = field(repr=False, compare=False)
    revision_kind: str = REVISION_KIND
    dtype: str = "float64"
    order: str = "ROW_MAJOR_NORTH_TO_SOUTH"
    fixture_origin: str = "SYNTHETIC_SIMULATOR"


@dataclass(frozen=True)
class ReadShard:
    plan_id: str
    epoch: str
    revision: str
    index: int
    start: int
    values: tuple
    valid: tuple
    sha256: str


@dataclass(frozen=True)
class ReadPlanData:
    plan: ReadPlan
    values: tuple
    valid: tuple
    consistency: str = "PROPOSED_REFERENCE_SNAPSHOT_NOT_NATIVE_COMMITTED_SCIENCE"


def _hash(value, length):
    return isinstance(value, str) and re.fullmatch("[0-9a-f]{" + str(length) + "}", value) is not None


def _cells_digest(values, valid):
    raw = json.dumps({"values": list(values), "valid": list(valid)}, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def _json(raw):
    def unique(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate read-plan field")
            result[key] = value
        return result
    def constant(_):
        raise ValueError("Nonfinite read-plan literal")
    try:
        if not isinstance(raw, bytes) or len(raw) > 65536:
            raise ValueError("Invalid read-plan bytes")
        return json.loads(raw.decode("utf-8"), object_pairs_hook=unique, parse_constant=constant)
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Malformed bounded UTF-8 read-plan reference") from None


def _binding(context, allow_experimental):
    if allow_experimental is not True or not isinstance(context, Context) or scope_token(context) != context.id:
        raise InvalidRequestError("Read plans require explicit experimental opt-in and bound root context")
    context.client._validate_context_binding(context)
    return to_handle(context)


def _open(observation, allow_experimental, shard_cells, lease_seconds, max_cells):
    if not isinstance(observation, ObservationImpl) or observation._context is None:
        raise InvalidRequestError("Read plan requires an explicitly bound observation")
    context = observation._context
    _binding(context, allow_experimental)
    if (type(shard_cells) is not int or not 1 <= shard_cells <= 128
            or type(lease_seconds) is not int or not 1 <= lease_seconds <= 60
            or type(max_cells) is not int or not 1 <= max_cells <= 4096):
        raise InvalidRequestError("Choose exact bounded shard/lease/cell budgets")
    source = to_handle(observation)
    return context, source, {"protocol": PROTOCOL, "observation_id": source.observation_id,
        "shard_cells": shard_cells, "lease_seconds": lease_seconds, "max_cells": max_cells}


def _plan(value, context, source, shard_cells, max_cells):
    fields = {"protocol", "plan_id", "epoch", "revision", "revision_kind", "observation_id", "scope", "runtime_id", "dtype", "shape", "crs", "transform", "order", "units", "shards", "fixture_origin"}
    if (not isinstance(value, dict) or set(value) != fields or value["protocol"] != PROTOCOL
            or not _hash(value["plan_id"], 32) or not _hash(value["epoch"], 32) or not _hash(value["revision"], 64)
            or value["revision_kind"] != REVISION_KIND or value["observation_id"] != source.observation_id
            or value["fixture_origin"] not in ("SYNTHETIC_SIMULATOR", "AUTHENTIC_FIXTURE_SIMULATED_DELIVERY")
            or type(value["observation_id"]) is not int or value["scope"] != source.scope or value["runtime_id"] != source.runtime_id
            or value["dtype"] != "float64" or not isinstance(value["shape"], list) or len(value["shape"]) != 2
            or any(type(n) is not int or not 1 <= n <= 4096 for n in value["shape"])
            or not isinstance(value["crs"], str) or not value["crs"] or len(value["crs"]) > 4096
            or value["order"] != "ROW_MAJOR_NORTH_TO_SOUTH" or not isinstance(value["transform"], list) or len(value["transform"]) != 6
            or any(type(v) not in (int, float) or abs(v) > 10**12 or not math.isfinite(v) for v in value["transform"])
            or value["transform"][0] <= 0 or value["transform"][4] >= 0 or value["transform"][1] != 0 or value["transform"][3] != 0
            or value["units"] is not None and (not isinstance(value["units"], str) or not value["units"] or len(value["units"]) > 256)
            or not isinstance(value["shards"], list)):
        raise ProtocolError("Read plan did not establish exact proposed origin/snapshot/descriptor")
    cells = math.prod(value["shape"])
    if cells > max_cells or not 1 <= len(value["shards"]) <= cells:
        raise ProtocolError("Read plan exceeds explicit cell/shard budget")
    specs, start = [], 0
    for index, spec in enumerate(value["shards"]):
        if (not isinstance(spec, dict) or set(spec) != {"index", "start", "count", "sha256"}
                or type(spec["index"]) is not int or spec["index"] != index
                or type(spec["start"]) is not int or spec["start"] != start
                or type(spec["count"]) is not int or not 1 <= spec["count"] <= shard_cells or spec["count"] != min(shard_cells, cells - start)
                or not _hash(spec["sha256"], 64)):
            raise ProtocolError("Read-plan shards overlap, omit cells or differ from bounded layout")
        specs.append(ReadShardSpec(index, start, spec["count"], spec["sha256"]))
        start += spec["count"]
    if start != cells:
        raise ProtocolError("Read-plan layout is incomplete")
    return ReadPlan(value["plan_id"], value["epoch"], value["revision"], source, tuple(value["shape"]),
        value["crs"], tuple(value["transform"]), value["units"], tuple(specs), context, fixture_origin=value["fixture_origin"])


def _options(plan, allow_experimental):
    if not isinstance(plan, ReadPlan):
        raise InvalidRequestError("Use an explicit bound ReadPlan")
    reference = _binding(plan.context, allow_experimental)
    if (reference.runtime_url, reference.runtime_id, reference.scope) != (plan.source.runtime_url, plan.source.runtime_id, plan.source.scope):
        raise InvalidRequestError("Read plan belongs to another originating Runtime/scope; no request sent")
    return {"scope": reference.scope, "service_id": reference.runtime_id, "max_response_bytes": 65536,
        "params": {"protocol": PROTOCOL, "epoch": plan.epoch}, "response": "bytes"}


def _shard(plan, index, value):
    spec = plan.shards[index]
    if (not isinstance(value, dict) or set(value) != {"protocol", "plan_id", "epoch", "revision", "index", "start", "values", "valid", "sha256"}
            or value["protocol"] != PROTOCOL or value["plan_id"] != plan.plan_id or value["epoch"] != plan.epoch
            or value["revision"] != plan.revision or type(value["index"]) is not int or value["index"] != index
            or type(value["start"]) is not int or value["start"] != spec.start
            or not isinstance(value["values"], list) or not isinstance(value["valid"], list)
            or len(value["values"]) != spec.count or len(value["valid"]) != spec.count):
        raise ProtocolError("Shard origin/revision/layout differs from the explicit plan")
    for number, valid in zip(value["values"], value["valid"]):
        if (type(valid) is not bool or (not valid and number is not None)
                or valid and (type(number) not in (int, float) or type(number) is int and abs(number) > 2**53 or not math.isfinite(number))):
            raise ProtocolError("Shard values/missingness are not exact bounded finite numerical cells")
    digest = _cells_digest(value["values"], value["valid"])
    if value["sha256"] != digest or digest != spec.sha256:
        raise ProtocolError("Shard digest differs; no partial snapshot or automatic replan")
    return ReadShard(plan.plan_id, plan.epoch, plan.revision, index, spec.start,
        tuple(value["values"]), tuple(value["valid"]), digest)


def open_read_plan(observation, *, allow_experimental=False, shard_cells=128, lease_seconds=30, max_cells=4096):
    context, source, body = _open(observation, allow_experimental, shard_cells, lease_seconds, max_cells)
    if not isinstance(context.client, Client):
        raise InvalidRequestError("Use matching synchronous read-plan client")
    value = context.client.transport.request("runtime", "POST", PREFIX + "open", scope=source.scope, service_id=source.runtime_id,
        json=body, ambiguous=True, max_response_bytes=65536, response="bytes")
    return _plan(_json(value), context, source, shard_cells, max_cells)


def read_plan_shard(plan, index, *, allow_experimental=False):
    options = _options(plan, allow_experimental)
    if type(index) is not int or not 0 <= index < len(plan.shards):
        raise InvalidRequestError("Choose an exact shard index from the bound plan")
    if not isinstance(plan.context.client, Client):
        raise InvalidRequestError("Use matching synchronous read-plan client")
    value = plan.context.client.transport.request("runtime", "GET", PREFIX + plan.plan_id + "/shards/" + str(index), **options)
    return _shard(plan, index, _json(value))


def release_read_plan(plan, *, allow_experimental=False):
    options = _options(plan, allow_experimental)
    if not isinstance(plan.context.client, Client):
        raise InvalidRequestError("Use matching synchronous read-plan client")
    result = plan.context.client.transport.request("runtime", "DELETE", PREFIX + plan.plan_id, ambiguous=True, **options)
    value = _json(result)
    if not isinstance(value, dict) or value.get("released") is not True or value != {"protocol": PROTOCOL, "released": True, "plan_id": plan.plan_id, "epoch": plan.epoch}:
        raise ProtocolError("Read-plan release not confirmed; no automatic replay")
    return True


def assemble_read_plan(plan, shards):
    if not isinstance(plan, ReadPlan) or type(shards) is not tuple or len(shards) != len(plan.shards):
        raise InvalidRequestError("Complete explicit immutable shard tuple is required")
    values, valid = [], []
    for spec, shard in zip(plan.shards, shards):
        if not isinstance(shard, ReadShard):
            raise ProtocolError("Unvalidated shard cannot establish a snapshot")
        confirmed = _shard(plan, spec.index, {"protocol": PROTOCOL, "plan_id": shard.plan_id, "epoch": shard.epoch,
            "revision": shard.revision, "index": shard.index, "start": shard.start,
            "values": list(shard.values), "valid": list(shard.valid), "sha256": shard.sha256})
        values.extend(confirmed.values); valid.extend(confirmed.valid)
    if _cells_digest(values, valid) != plan.revision:
        raise ProtocolError("Complete snapshot digest differs; no mixed/partial data returned")
    return ReadPlanData(plan, tuple(values), tuple(valid))


async def async_open_read_plan(observation, *, allow_experimental=False, shard_cells=128, lease_seconds=30, max_cells=4096):
    from .async_client import AsyncClient
    context, source, body = _open(observation, allow_experimental, shard_cells, lease_seconds, max_cells)
    if not isinstance(context.client, AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous read-plan client")
    value = await context.client.transport.request("runtime", "POST", PREFIX + "open", scope=source.scope,
        service_id=source.runtime_id, json=body, ambiguous=True, max_response_bytes=65536, response="bytes")
    return _plan(_json(value), context, source, shard_cells, max_cells)


async def async_read_plan_shard(plan, index, *, allow_experimental=False):
    from .async_client import AsyncClient
    options = _options(plan, allow_experimental)
    if type(index) is not int or not 0 <= index < len(plan.shards):
        raise InvalidRequestError("Choose an exact shard index from the bound plan")
    if not isinstance(plan.context.client, AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous read-plan client")
    value = await plan.context.client.transport.request("runtime", "GET", PREFIX + plan.plan_id + "/shards/" + str(index), **options)
    return _shard(plan, index, _json(value))


async def async_release_read_plan(plan, *, allow_experimental=False):
    from .async_client import AsyncClient
    options = _options(plan, allow_experimental)
    if not isinstance(plan.context.client, AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous read-plan client")
    result = await plan.context.client.transport.request("runtime", "DELETE", PREFIX + plan.plan_id, ambiguous=True, **options)
    value = _json(result)
    if not isinstance(value, dict) or value.get("released") is not True or value != {"protocol": PROTOCOL, "released": True, "plan_id": plan.plan_id, "epoch": plan.epoch}:
        raise ProtocolError("Read-plan release not confirmed; no automatic replay")
    return True
