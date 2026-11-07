"""Explicit experimental retained-event reference, NOT native AMQP replay.

JAVA-GAP-0005. No retries, background receiver, implicit cursor advancement or
simulator dependency. Persist a returned cursor only after caller processing.
"""
from dataclasses import dataclass
import json

from ._inspection import freeze
from .client import Client, Context, scope_token
from .errors import InvalidRequestError, ProtocolError
from .handles import to_handle

PROTOCOL = "klab.experimental.notifications/1"
ROUTE = "/experimental/notifications/v1/events"


@dataclass(frozen=True)
class NotificationCursor:
    runtime_url: str
    runtime_id: str
    scope: str
    epoch: str
    sequence: int

    def __post_init__(self):
        if (any(not isinstance(value, str) or not value or len(value) > 4096 for value in
                (self.runtime_url, self.runtime_id, self.scope, self.epoch))
                or type(self.sequence) is not int or not 0 <= self.sequence < 2**53):
            raise InvalidRequestError("Invalid explicit notification cursor")

    def to_dict(self):
        return {"runtime_url": self.runtime_url, "runtime_id": self.runtime_id, "scope": self.scope,
            "epoch": self.epoch, "sequence": self.sequence}

    @classmethod
    def from_dict(cls, value):
        if not isinstance(value, dict) or set(value) != {"runtime_url", "runtime_id", "scope", "epoch", "sequence"}:
            raise InvalidRequestError("Invalid notification cursor fields")
        return cls(**value)


@dataclass(frozen=True)
class NotificationBatch:
    events: tuple
    cursor: NotificationCursor
    coverage: str = "SINCE_EXPLICIT_REFERENCE_ACTIVATION_ONLY"


class NotificationResyncRequired(ProtocolError):
    """Retained history cannot establish continuity. Refetch state explicitly."""


def _prepare(context, cursor, allow_experimental, limit):
    if allow_experimental is not True:
        raise InvalidRequestError("Durable notification reference requires explicit experimental opt-in")
    if not isinstance(context, Context) or scope_token(context) != context.id:
        raise InvalidRequestError("Notification replay requires an explicitly bound root context")
    if type(limit) is not int or not 1 <= limit <= 64:
        raise InvalidRequestError("Notification batch limit must be1..64")
    context.client._validate_context_binding(context)
    reference = to_handle(context)
    if cursor is not None and (not isinstance(cursor, NotificationCursor) or
            (cursor.runtime_url, cursor.runtime_id, cursor.scope) != (reference.runtime_url, reference.runtime_id, reference.scope)):
        raise InvalidRequestError("Cursor belongs to another explicit Runtime/scope; no request sent")
    params = {"protocol": PROTOCOL, "limit": limit}
    if cursor is not None:
        params.update(epoch=cursor.epoch, after=cursor.sequence)
    return reference, params


def _decode(value, reference, cursor, limit):
    if (not isinstance(value, dict) or set(value) != {"protocol", "epoch", "first", "next", "state", "events", "coverage"}
            or value["protocol"] != PROTOCOL or value["coverage"] != "SINCE_EXPLICIT_REFERENCE_ACTIVATION_ONLY"
            or not isinstance(value["epoch"], str) or not value["epoch"] or len(value["epoch"]) > 128
            or any(type(value[key]) is not int or not 1 <= value[key] < 2**53 for key in ("first", "next"))
            or value["first"] > value["next"] or not isinstance(value["events"], list) or len(value["events"]) > limit):
        raise ProtocolError("Malformed retained-notification reference response")
    if value["state"] == "RESYNC_REQUIRED":
        if value["events"]:
            raise ProtocolError("Resync response cannot publish partial event history")
        raise NotificationResyncRequired("Reference epoch/retention changed; explicitly refetch authoritative state before a new cursor")
    if value["state"] != "OK" or cursor is not None and value["epoch"] != cursor.epoch:
        raise ProtocolError("Notification continuity not established")
    sequence = cursor.sequence if cursor is not None else value["next"] - 1
    if sequence < value["first"] - 1 or sequence >= value["next"]:
        raise ProtocolError("Notification cursor outside advertised retained history")
    events = []
    for event in value["events"]:
        if (cursor is None or not isinstance(event, dict) or set(event) != {"sequence", "kind", "job_id", "service"}
                or type(event["sequence"]) is not int or event["sequence"] != sequence + 1 or event["sequence"] >= value["next"]
                or event["kind"] not in ("JOB_WAITING", "JOB_STARTED", "JOB_FINISHED", "JOB_ABORTED", "JOB_INTERRUPTED")
                or type(event["job_id"]) is not int or not 1 <= event["job_id"] < 2**53
                or event["service"] not in ("resources", "resolver", "runtime")):
            raise ProtocolError("Notification event is invalid, unordered or incomplete")
        sequence = event["sequence"]
        events.append(freeze(event))
    if cursor is not None and len(events) != min(limit, value["next"] - 1 - cursor.sequence):
        raise ProtocolError("Notification batch omits advertised contiguous events")
    return NotificationBatch(tuple(events), NotificationCursor(reference.runtime_url, reference.runtime_id,
        reference.scope, value["epoch"], sequence))


def _wire(raw, reference, cursor, limit):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate notification field")
            result[key] = value
        return result
    def constant(_):
        raise ValueError("Nonfinite notification value")
    try:
        if not isinstance(raw, bytes) or len(raw) > 65536:
            raise ValueError("Invalid notification bytes")
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=pairs, parse_constant=constant)
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Malformed bounded UTF-8 notification reference") from None
    return _decode(value, reference, cursor, limit)


def read_notifications(context, cursor=None, *, allow_experimental=False, limit=64):
    """None establishes a tail cursor, not historical coverage before activation.

    Returned cursor never advances a caller-owned checkpoint implicitly. Reusing a
    cursor deliberately replays the same retained events; processing must tolerate
    duplicates. Retention/epoch gaps raise, never silently reset to synthetic state.
    """
    if not isinstance(getattr(context, "client", None), Client):
        raise InvalidRequestError("Use matching synchronous notification client")
    reference, params = _prepare(context, cursor, allow_experimental, limit)
    value = context.client.transport.request("runtime", "GET", ROUTE, scope=context.id,
        service_id=reference.runtime_id, params=params, max_response_bytes=65536, response="bytes")
    return _wire(value, reference, cursor, limit)


async def async_read_notifications(context, cursor=None, *, allow_experimental=False, limit=64):
    from .async_client import AsyncClient
    if not isinstance(getattr(context, "client", None), AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous notification client")
    reference, params = _prepare(context, cursor, allow_experimental, limit)
    value = await context.client.transport.request("runtime", "GET", ROUTE, scope=context.id,
        service_id=reference.runtime_id, params=params, max_response_bytes=65536, response="bytes")
    return _wire(value, reference, cursor, limit)
