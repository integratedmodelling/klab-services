"""Strict provisional worker receipts; server claims never native scientific authority."""
import hashlib
import re

from .protocol import Field, PROTOCOL, WorkerError, encoded


def _identity(value):
    return isinstance(value, str) and value not in (".", "..") and re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", value) is not None


def _hash(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def ack(value, state, **pins):
    if (not isinstance(value, dict) or set(value) != {"protocol", "state", *pins}
            or value["protocol"] != PROTOCOL or value["state"] != state
            or any(value[key] != expected for key, expected in pins.items())):
        raise WorkerError("Worker acknowledgement outcome unknown; no automatic replay or assumed rollback")
    return value


def assigned(value, task_id=None):
    if not isinstance(value, dict) or not _identity(value.get("task_id")):
        raise WorkerError("Worker assignment acknowledgement incomplete; no automatic replay")
    return ack(value, "QUEUED", task_id=value["task_id"] if task_id is None else task_id)


def claimed(value):
    if isinstance(value, dict) and value.get("state") == "IDLE":
        return ack(value, "IDLE")
    if (not isinstance(value, dict) or set(value) != {"protocol", "state", "task_id", "lease", "input"}
            or value["protocol"] != PROTOCOL or value["state"] != "LEASED" or not _identity(value["task_id"])
            or not isinstance(value["lease"], str) or not 1 <= len(value["lease"]) <= 128
            or any(ord(char) < 33 or ord(char) == 127 for char in value["lease"])):
        raise WorkerError("Worker claim did not establish an exact bounded task/input/lease; no execution or replay")
    Field.from_dict(value["input"])
    return value


def task_input(task):
    task = claimed(task)
    if task["state"] != "LEASED":
        raise WorkerError("Explicit leased task required; idle is not execution authority")
    return Field.from_dict(task["input"])


def status(value, task_id, scope):
    states = ("QUEUED", "LEASED", "PUBLISHED", "FAILED", "CANCELLED", "ABORTED", "RECOVERY_REQUIRED")
    if (not isinstance(value, dict) or set(value) != {"protocol", "task_id", "state", "output", "provenance"}
            or value["protocol"] != PROTOCOL or value["task_id"] != task_id or value["state"] not in states):
        raise WorkerError("Worker status did not confirm exact task identity/state")
    if value["state"] != "PUBLISHED":
        if value["output"] is not None or value["provenance"] is not None:
            raise WorkerError("Unpublished worker task cannot claim output/provenance")
        return value
    output = Field.from_dict(value["output"])
    provenance = value["provenance"]
    if (not isinstance(provenance, dict) or set(provenance) != {"worker_id", "scope", "protocol", "input_sha256",
            "implementation_sha256", "output_sha256", "origin"}
            or provenance["protocol"] != PROTOCOL or provenance["scope"] != scope or not _identity(provenance["worker_id"])
            or any(not _hash(provenance[key]) for key in ("input_sha256", "implementation_sha256", "output_sha256"))
            or not isinstance(provenance["origin"], str) or not 1 <= len(provenance["origin"]) <= 128
            or provenance["output_sha256"] != hashlib.sha256(encoded(output.to_dict())).hexdigest()):
        raise WorkerError("Published worker output does not match bounded scoped provenance/content digest")
    return value
