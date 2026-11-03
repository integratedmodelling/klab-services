"""Offline decoding of Java RuntimeAgent.Status snapshots, not agent control."""
from dataclasses import dataclass, field
import json

from .errors import InvalidRequestError, ProtocolError

_LONG_MAX = 2**63 - 1
_STATUS_CLASS = "org.integratedmodelling.klab.api.actors.RuntimeAgent$Status"
_FIELDS = {"@CLASS", "agentUrn", "state", "viable", "timestamp", "observationId",
           "startedAt", "lastActivityAt"}


@dataclass(frozen=True)
class AgentStatusSnapshot:
    """Untrusted sender snapshot. No authentication, ordering or cleanup proof.

    Timestamps are sender-side epoch milliseconds, not a recovery cursor. READY
    does not prove a start request was never sent. STOPPED/FAILED report only the
    sender's state, not independently confirmed removal of owned resources.
    """
    agent_urn: str
    state: str
    viable: bool
    detail: str | None = field(repr=False)
    timestamp: int
    observation_id: int
    started_at: int
    last_activity_at: int


def _text(value, limit):
    return (isinstance(value, str) and 0 < len(value) <= limit
            and not any(ord(char) < 33 or char.isspace() or 0xD800 <= ord(char) <= 0xDFFF
                        for char in value))


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate field")
        result[key] = value
    return result


def decode_agent_status(body, *, expected_agent_urn, max_bytes=65536):
    """Decode one bounded UTF-8 Status JSON object for an explicitly expected URN.

    Accepts bytes only; duplicate/unknown fields and non-Java integer ranges fail
    closed. This is a payload decoder, NOT an AMQP envelope verifier. The caller
    must separately authenticate and bind broker/principal/Runtime/incarnation
    before trusting a snapshot. A matching URN alone proves none of those.
    No I/O, mutation, scheduling, replay, transport or running-agent handle.
    """
    if not _text(expected_agent_urn, 4096):
        raise InvalidRequestError("An explicit bounded agent URN is required")
    if type(max_bytes) is not int or not 1 <= max_bytes <= 65536:
        raise InvalidRequestError("Status byte limit must be an integer in [1,65536]")
    if not isinstance(body, bytes) or len(body) > max_bytes:
        raise ProtocolError("Status must be bytes within the admitted byte limit")
    try:
        data = json.loads(body.decode("utf-8"), object_pairs_hook=_pairs)
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Malformed status JSON") from None
    if not isinstance(data, dict) or set(data) not in (_FIELDS, _FIELDS | {"detail"}):
        raise ProtocolError("Unsupported status fields; expected a Status payload, not an envelope")
    if data["@CLASS"] != _STATUS_CLASS:
        raise ProtocolError("Unexpected status payload class")
    if not _text(data["agentUrn"], 4096) or data["agentUrn"] != expected_agent_urn:
        raise ProtocolError("Status belongs to a different or invalid agent URN")
    if not isinstance(data["state"], str) or data["state"] not in ("READY", "RUNNING", "STOPPED", "FAILED"):
        raise ProtocolError("Unknown agent state")
    if type(data["viable"]) is not bool:
        raise ProtocolError("Status viable must be a boolean")
    detail = data.get("detail")
    if detail is not None and (not isinstance(detail, str) or len(detail) > 16384
                               or any(0xD800 <= ord(char) <= 0xDFFF for char in detail)):
        raise ProtocolError("Invalid status detail")
    for key in ("timestamp", "observationId", "startedAt", "lastActivityAt"):
        value = data[key]
        if type(value) is not int or not (0 if key == "timestamp" else -1) <= value <= _LONG_MAX:
            raise ProtocolError("Status numeric field is outside the Java long contract")
    return AgentStatusSnapshot(data["agentUrn"], data["state"], data["viable"], detail,
                               data["timestamp"], data["observationId"],
                               data["startedAt"], data["lastActivityAt"])
