"""Finite copied-snapshot composition, not native live federation or source grants.

No remote fetching or credential forwarding. The submitting client independently
reads authorized A/B snapshots; origin claims are not verified by this coordinator.
Only compatible finite reference fields can be combined into a C-owned result.
"""
from copy import deepcopy
import hashlib
import json
import math
import re
from urllib.parse import urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.copied-composition/1"
PREFIX = "/experimental/copied-composition/v1/"
REVISION_KIND = "REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT"


def _digest(values, valid):
    raw = json.dumps({"values": values, "valid": valid}, sort_keys=True, separators=(",", ":"), allow_nan=False)
    return hashlib.sha256(raw.encode()).hexdigest()


def _source(value):
    if (not isinstance(value, dict) or set(value) != {"origin", "epoch", "plan_id", "revision", "revision_kind", "descriptor", "values", "valid"}
            or value["revision_kind"] != REVISION_KIND):
        raise ContractError(422, "Exact copied reference snapshot required")
    origin = value["origin"]
    if (not isinstance(origin, dict) or set(origin) != {"runtime_url", "runtime_id", "context_id", "observation_id"}
            or any(not isinstance(origin[key], str) or not 1 <= len(origin[key]) <= 4096
                for key in ("runtime_url", "runtime_id", "context_id"))
            or type(origin["observation_id"]) is not int or not 1 <= origin["observation_id"] < 2**63):
        raise ContractError(422, "Origin-qualified source identity required")
    try:
        url = urlsplit(origin["runtime_url"])
        if (url.scheme not in ("http", "https") or not url.hostname or url.username or url.password
                or url.query or url.fragment or re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", origin["context_id"]) is None):
            raise ValueError("Invalid reference origin")
    except ValueError:
        raise ContractError(422, "Reference origin is invalid; coordinator never fetches it") from None
    for key, length in (("epoch", 32), ("plan_id", 32), ("revision", 64)):
        if not isinstance(value[key], str) or re.fullmatch("[0-9a-f]{" + str(length) + "}", value[key]) is None:
            raise ContractError(422, "Explicit reference epoch/plan/revision required")
    descriptor = value["descriptor"]
    if (not isinstance(descriptor, dict) or set(descriptor) != {"shape", "crs", "transform", "units", "dtype", "order"}
            or descriptor["dtype"] != "float64" or descriptor["order"] != "ROW_MAJOR_NORTH_TO_SOUTH"
            or not isinstance(descriptor["shape"], list) or len(descriptor["shape"]) != 2
            or any(type(n) is not int or not 1 <= n <= 4096 for n in descriptor["shape"])
            or not isinstance(descriptor["crs"], str) or not 1 <= len(descriptor["crs"]) <= 4096
            or not isinstance(descriptor["units"], str) or not 1 <= len(descriptor["units"]) <= 256
            or not isinstance(descriptor["transform"], list) or len(descriptor["transform"]) != 6
            or any(type(n) not in (int, float) or abs(n) > 10**12 or not math.isfinite(n) for n in descriptor["transform"])):
        raise ContractError(422, "Explicit finite reference descriptor required; no inferred mediation")
    cells = math.prod(descriptor["shape"])
    values, valid = value["values"], value["valid"]
    if (cells > 4096 or not isinstance(values, list) or not isinstance(valid, list)
            or len(values) != cells or len(valid) != cells or any(type(v) is not bool for v in valid)
            or any((type(v) not in (int, float) or abs(v) > 1e308 or not math.isfinite(v)) if ok else v is not None
                for v, ok in zip(values, valid))):
        raise ContractError(422, "Invalid bounded reference values/masks")
    if _digest(values, valid) != value["revision"]:
        raise ContractError(422, "Copied reference content differs from its pinned digest")
    return {"origin": origin, "epoch": value["epoch"], "plan_id": value["plan_id"],
        "revision": value["revision"], "revision_kind": REVISION_KIND}


class Compositions:
    def __init__(self, state):
        self.state = state
        self.rows = {}
        self.next_id = 1

    def request(self, method, path, scope, owner, headers, data):
        if self.state.scenario != "copied-composition" or self.state.store is not None:
            raise ContractError(409, "Explicit ephemeral copied-composition scenario required")
        root, _ = self.state._context(scope, owner)
        if scope != root or headers.get("klab-service") != self.state.service_ids["runtime"]:
            raise ContractError(403, "Composition requires exact owned C root and Runtime")
        if path == PREFIX + "create" and method == "POST":
            if (not isinstance(data, dict) or set(data) != {"protocol", "operation", "sources", "expected_vector"}
                    or data["protocol"] != PROTOCOL or data["operation"] != "PAIRWISE_SUM_REFERENCE"
                    or not isinstance(data["sources"], list) or len(data["sources"]) != 2):
                raise ContractError(422, "Exact two-source copied composition required")
            sources = data["sources"]
            vector = [_source(source) for source in sources]
            if data["expected_vector"] != vector:
                raise ContractError(409, "Pinned source vector differs; no automatic refresh or composition")
            origins = [source["origin"]["runtime_id"] for source in sources]
            if origins[0] == origins[1] or self.state.service_ids["runtime"] in origins:
                raise ContractError(422, "A and B must be distinct source Runtimes, not C or duplicate aliases")
            if sources[0]["descriptor"] != sources[1]["descriptor"]:
                raise ContractError(422, "Source descriptors differ; no implicit resampling or unit conversion")
            if sum(key[0] == root for key in self.rows) >= 8:
                raise ContractError(429, "C-owned composition capacity exceeded")
            valid = [a and b for a, b in zip(sources[0]["valid"], sources[1]["valid"])]
            values = [a + b if ok else None for a, b, ok in zip(sources[0]["values"], sources[1]["values"], valid)]
            if any(v is not None and not math.isfinite(v) for v in values):
                raise ContractError(422, "Reference arithmetic overflow; no output published")
            identity = str(self.next_id)
            result = {"protocol": PROTOCOL, "result_id": identity,
                "origin": {"runtime_id": self.state.service_ids["runtime"], "context_id": root},
                "source_vector": vector, "descriptor": sources[0]["descriptor"], "values": values,
                "valid": valid, "revision": _digest(values, valid), "revision_kind": REVISION_KIND,
                "source_authority": "CALLER_ASSERTED_COPIED_REFERENCE_NOT_REMOTE_GRANT",
                "operation": "PAIRWISE_SUM_REFERENCE", "live_federation": False, "durable": False}
            self.rows[(root, identity)] = deepcopy(result)
            self.next_id += 1
            return deepcopy(result), "application/json"
        identity = path[len(PREFIX):]
        if method not in ("GET", "DELETE") or not identity.isascii() or not identity.isdecimal():
            raise ContractError(404, "Unknown copied composition operation")
        key = (root, identity)
        if key not in self.rows:
            raise ContractError(410, "Copied C result absent or released")
        if method == "GET": return deepcopy(self.rows[key]), "application/json"
        del self.rows[key]
        return {"released": True, "source_mutations": 0}, "application/json"
