"""SIMULATED_PROVISIONAL/JAVA-GAP-0006/0014 owned finite snapshot-shard proposal.

Grants are process-local, current-owner bound and explicitly expired after restart.
Content digests are NOT native scientific commit IDs or Java read-plan authority.
"""
from copy import deepcopy
import hashlib
import json
import math
import re
import secrets
import time
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.read-plans/1"
PREFIX = "/experimental/read-plans/v1/"
REVISION_KIND = "REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT"


def digest(values, valid):
    raw = json.dumps({"values": values, "valid": valid}, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


class ReadPlans:
    def __init__(self, state):
        self.state = state
        self.epoch = secrets.token_hex(16)
        self.rows = {}

    def cleanup(self):
        now = time.monotonic()
        self.rows = {key: row for key, row in self.rows.items() if row["scope"] in self.state.contexts and row["expires"] > now}

    def request(self, method, route, scope, owner, headers, body):
        root, _ = self.state._context(scope, owner)
        if root != scope or headers.get("klab-service") != self.state.service_ids["runtime"]:
            raise ContractError(403, "Read-plan reference requires originating root Runtime/owner")
        self.cleanup()
        url = urlsplit(route)
        suffix = url.path[len(PREFIX):]
        if method == "POST" and suffix == "open" and not url.query:
            return self.open(root, body), "application/json"
        params = parse_qs(url.query, keep_blank_values=True)
        if body or params.get("protocol") != [PROTOCOL] or set(params) != {"protocol", "epoch"} or len(params["epoch"]) != 1:
            raise ContractError(422, "Explicit reference protocol/epoch query required")
        pieces = suffix.split("/")
        if re.fullmatch(r"[0-9a-f]{32}", pieces[0]) is None:
            raise ContractError(422, "Invalid read-plan identity")
        row = self.rows.get(pieces[0])
        if row is None or params["epoch"] != [self.epoch]:
            raise ContractError(410, "Read-plan reference expired, released or lost; no automatic replan")
        if row["scope"] != root:
            raise ContractError(403, "Read plan belongs to another root context")
        if method == "DELETE" and len(pieces) == 1:
            del self.rows[pieces[0]]
            return {"protocol": PROTOCOL, "released": True, "plan_id": pieces[0], "epoch": self.epoch}, "application/json"
        if method != "GET" or len(pieces) != 3 or pieces[1] != "shards" or re.fullmatch(r"0|[1-9][0-9]{0,3}", pieces[2]) is None:
            raise ContractError(404, "Unsupported read-plan operation")
        index = int(pieces[2])
        if index >= len(row["blocks"]):
            raise ContractError(422, "Shard index is outside this exact plan")
        return deepcopy(row["blocks"][index]), "application/json"

    def open(self, root, body):
        if len(body) > 4096:
            raise ContractError(413, "Read-plan request byte budget exceeded")
        try:
            def unique(items):
                result = {}
                for key, value in items:
                    if key in result:
                        raise ValueError("Duplicate plan field")
                    result[key] = value
                return result
            data = json.loads(body, object_pairs_hook=unique)
        except (ValueError, UnicodeError, RecursionError):
            raise ContractError(422, "Invalid read-plan JSON") from None
        if (not isinstance(data, dict) or set(data) != {"protocol", "observation_id", "shard_cells", "lease_seconds", "max_cells"}
                or data["protocol"] != PROTOCOL or type(data["observation_id"]) is not int or not 1 <= data["observation_id"] < 2**53
                or type(data["shard_cells"]) is not int or not 1 <= data["shard_cells"] <= 128
                or type(data["lease_seconds"]) is not int or not 1 <= data["lease_seconds"] <= 60
                or type(data["max_cells"]) is not int or not 1 <= data["max_cells"] <= 4096):
            raise ContractError(422, "Choose explicit bounded read-plan protocol/budgets")
        if (root, data["observation_id"]) not in self.state.observations:
            raise ContractError(404, "Completed owned observation required")
        if self.state.scenario not in ("elevation", "zero-negative-nodata", "usgs-recorded", "selected-read-plans"):
            raise ContractError(422, "This fixture has no established numerical snapshot mapping")
        if len(self.rows) >= 16:
            raise ContractError(429, "Read-plan reference grant capacity exceeded")
        height, width = self.state.grid["shape"]
        if height * width != len(self.state.values) or len(self.state.values) > data["max_cells"]:
            raise ContractError(422, "Read-plan source exceeds exact requested cell budget")
        values, valid = [], []
        for number in self.state.values:
            if number is None or number == "NaN":
                values.append(None); valid.append(False)
            elif (type(number) in (int, float) and not (type(number) is int and abs(number) > 2**53) and math.isfinite(number)):
                values.append(float(number)); valid.append(True)
            else:
                raise ContractError(422, "Read-plan fixture contains unsupported numerical cells")
        identity = secrets.token_hex(16)
        revision = digest(values, valid)
        blocks, specs = [], []
        for index, start in enumerate(range(0, len(values), data["shard_cells"])):
            numbers = values[start:start + data["shard_cells"]]
            masks = valid[start:start + data["shard_cells"]]
            checksum = digest(numbers, masks)
            specs.append({"index": index, "start": start, "count": len(numbers), "sha256": checksum})
            blocks.append({"protocol": PROTOCOL, "plan_id": identity, "epoch": self.epoch, "revision": revision,
                "index": index, "start": start, "values": numbers, "valid": masks, "sha256": checksum})
        west, south, east, north = self.state.grid["bounds"]
        result = {"protocol": PROTOCOL, "plan_id": identity, "epoch": self.epoch, "revision": revision,
            "revision_kind": REVISION_KIND, "observation_id": data["observation_id"], "scope": root,
            "runtime_id": self.state.service_ids["runtime"], "dtype": "float64", "shape": [height, width],
            "crs": self.state.grid["crs"], "transform": [(east - west) / width, 0, west, 0, -(north - south) / height, north],
            "order": "ROW_MAJOR_NORTH_TO_SOUTH", "units": self.state.grid["unit"], "shards": specs,
            "fixture_origin": self.state.origin}
        if len(json.dumps(result, allow_nan=False).encode("utf-8")) > 65536:
            raise ContractError(413, "Read-plan manifest exceeds response byte budget; no grant allocated")
        self.rows[identity] = {"scope": root, "expires": time.monotonic() + data["lease_seconds"], "blocks": blocks}
        return result
