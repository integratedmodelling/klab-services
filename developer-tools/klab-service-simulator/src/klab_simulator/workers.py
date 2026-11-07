"""SIMULATED_PROVISIONAL worker/1 proposal (JAVA-GAP-0017), not a Java route.

Independent service-side validation. Never executes Python scripts, controls a live
Runtime, imports the worker SDK or publishes real scientific storage.
"""
from copy import deepcopy
import hashlib
import json
import math
import re
import secrets

from .state import ContractError

PROTOCOL = "klab.experimental.worker/1"
PREFIX = "/experimental/worker/v1/"


def digest(field):
    return hashlib.sha256(json.dumps(field, allow_nan=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def finite(value):
    if type(value) not in (int, float):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:
        return False


def field(value):
    if not isinstance(value, dict) or set(value) != {"shape", "axes", "coordinates", "crs", "unit", "values", "valid"}:
        raise ContractError(422, "Explicit scientific envelope required")
    shape, axes, coords = value["shape"], value["axes"], value["coordinates"]
    if not isinstance(shape, list) or not 1 <= len(shape) <= 4 or any(type(n) is not int or not 1 <= n <= 4096 for n in shape):
        raise ContractError(422, "Invalid worker shape")
    count = math.prod(shape)
    if count > 4096 or not isinstance(axes, list) or len(axes) != len(shape) or any(not isinstance(a, str) or not a or len(a) > 64 for a in axes) or len(set(axes)) != len(axes):
        raise ContractError(422, "Invalid worker axes or cell budget")
    if not isinstance(coords, list) or len(coords) != len(shape):
        raise ContractError(422, "Explicit coordinates required")
    for size, coordinates in zip(shape, coords):
        if not isinstance(coordinates, list) or len(coordinates) != size or any(not finite(c) for c in coordinates):
            raise ContractError(422, "Invalid worker coordinates")
    for text in (value["crs"], value["unit"]):
        if text is not None and (not isinstance(text, str) or not text or len(text) > 256):
            raise ContractError(422, "Invalid worker scientific metadata")
    if not isinstance(value["values"], list) or not isinstance(value["valid"], list) or len(value["values"]) != count or len(value["valid"]) != count:
        raise ContractError(422, "Invalid worker values/mask size")
    for number, valid in zip(value["values"], value["valid"]):
        if type(valid) is not bool or (not valid and number is not None) or (valid and not finite(number)):
            raise ContractError(422, "Invalid worker value/mask")
    return value


class WorkerState:
    def __init__(self, parent):
        self.parent = parent
        self.workers, self.tasks = {}, {}
        self.next_task = 1

    def request(self, method, route, headers, body):
        if headers.get("X-Klab-Worker-Protocol") != PROTOCOL:
            raise ContractError(409, "Worker protocol is provisional and must be explicitly negotiated")
        token = headers.get("Authorization")
        identities = {"sim-token-alice": ("alice", "user"), "sim-token-bob": ("bob", "user"),
            "sim-token-worker-alice": ("alice", "worker"), "sim-token-worker-bob": ("bob", "worker")}
        if token not in identities:
            raise ContractError(401, "Worker identity not authorized")
        owner, role = identities[token]
        scope, _ = self.parent._context(headers.get("klab-scope", ""), owner)
        if len(body) > 256 * 1024:
            raise ContractError(413, "Worker message budget exceeded")
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise ContractError(400, "Duplicate worker message key")
                result[key] = value
            return result
        data = json.loads(body, object_pairs_hook=unique) if body else {}
        if not isinstance(data, dict):
            raise ContractError(400, "Worker message must be an object")
        action = route[len(PREFIX):]
        def reply(**values):
            return {"protocol": PROTOCOL, **deepcopy(values)}, "application/json"
        def require_role(expected):
            if role != expected:
                raise ContractError(403, "Worker role cannot invoke this operation")
        def name(key):
            value = data.get(key)
            if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", value):
                raise ContractError(422, "Invalid worker/operation name")
            return value
        def worker():
            row = self.workers.get((scope, name("worker_id")))
            if row is None or row["owner"] != owner:
                raise ContractError(404, "Worker not registered in this scope")
            return row
        if method == "POST" and action == "register":
            require_role("worker")
            key = scope, name("worker_id")
            operation = name("operation")
            if key in self.workers:
                if self.workers[key]["operation"] != operation:
                    raise ContractError(409, "Worker registration cannot change operation")
                return reply(worker_id=key[1], state="REGISTERED")
            if len(self.workers) >= 32:
                raise ContractError(429, "Worker capacity exceeded")
            self.workers[key] = {"owner": owner, "operation": operation, "worker_id": key[1]}
            return reply(worker_id=key[1], state="REGISTERED")
        if method == "POST" and action == "tasks":
            require_role("user")
            if len(self.tasks) >= 128:
                raise ContractError(429, "Worker task capacity exceeded")
            operation = name("operation")
            source = deepcopy(field(data.get("input")))
            task_id = f"worker-task-{self.next_task}"
            self.next_task += 1
            self.tasks[task_id] = {"owner": owner, "scope": scope, "operation": operation, "input": source,
                "state": "QUEUED", "lease": None, "worker_id": None}
            return reply(task_id=task_id, state="QUEUED")
        if method == "POST" and action == "claim":
            require_role("worker")
            row = worker()
            for task_id, task in self.tasks.items():
                if task["scope"] == scope and task["operation"] == row["operation"] and task["state"] == "QUEUED":
                    task.update(state="LEASED", lease=secrets.token_urlsafe(32), worker_id=row["worker_id"])
                    return reply(task_id=task_id, lease=task["lease"], input=task["input"], state="LEASED")
            return reply(state="IDLE")
        if method == "POST" and action == "release":
            require_role("worker")
            row = worker()
            for task in self.tasks.values():
                if task["scope"] == scope and task["worker_id"] == row["worker_id"] and task["state"] == "LEASED":
                    task.update(state="ABORTED", lease=None)
            del self.workers[(scope, row["worker_id"])]
            return reply(state="RELEASED")
        parts = action.split("/")
        if len(parts) not in (2, 3) or parts[0] != "tasks":
            raise ContractError(404, "Unknown worker operation")
        task = self.tasks.get(parts[1])
        if task is None or task["scope"] != scope or task["owner"] != owner:
            raise ContractError(404, "Task not found in this scope")
        if method == "GET" and len(parts) == 2:
            return reply(task_id=parts[1], state=task["state"], output=task.get("output"), provenance=task.get("provenance"))
        if method == "POST" and len(parts) == 3 and parts[2] == "cancel":
            require_role("user")
            if task["state"] == "PUBLISHED":
                raise ContractError(409, "Published output cannot be cancelled")
            task.update(state="CANCELLED", lease=None)
            return reply(state="CANCELLED")
        if method == "POST" and len(parts) == 3 and parts[2] == "recover":
            require_role("user")
            if task["state"] != "RECOVERY_REQUIRED" or data.get("confirm_recompute") is not True:
                raise ContractError(409, "Only uncertain recovered work can be explicitly requeued")
            task.update(state="QUEUED", lease=None, worker_id=None)
            return reply(task_id=parts[1], state="QUEUED")
        if method == "POST" and len(parts) == 3 and parts[2] == "fail":
            require_role("worker")
            if task["state"] != "LEASED" or not isinstance(data.get("lease"), str) or not secrets.compare_digest(task["lease"], data["lease"]):
                raise ContractError(409, "Failure lease is no longer current")
            task.update(state="FAILED", lease=None)
            return reply(state="FAILED")
        if method == "POST" and len(parts) == 3 and parts[2] == "publish":
            require_role("worker")
            if task["state"] != "LEASED" or not isinstance(data.get("lease"), str) or not secrets.compare_digest(task["lease"], data["lease"]):
                raise ContractError(409, "Task lease stale, cancelled or already published")
            result = deepcopy(field(data.get("output")))
            if any(result[key] != task["input"][key] for key in ("shape", "axes", "coordinates", "crs", "unit", "valid")):
                raise ContractError(422, "Worker result altered declared scientific layout")
            if data.get("input_sha256") != digest(task["input"]) or not isinstance(data.get("implementation_sha256"), str) or not re.fullmatch(r"[0-9a-f]{64}", data["implementation_sha256"]):
                raise ContractError(422, "Worker provenance mismatch")
            # Single assignment under the parent's lock: failed validation publishes nothing.
            task.update(state="PUBLISHED", output=result, lease=None,
                provenance={"worker_id": task["worker_id"], "scope": scope, "protocol": PROTOCOL,
                    "input_sha256": data["input_sha256"], "implementation_sha256": data["implementation_sha256"],
                    "output_sha256": digest(result), "origin": "SYNTHETIC_SIMULATOR"})
            return reply(state="PUBLISHED", output_sha256=task["provenance"]["output_sha256"])
        raise ContractError(404, "Unknown worker operation")
