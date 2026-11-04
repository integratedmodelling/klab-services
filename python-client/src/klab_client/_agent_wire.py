"""Existing native agent message wire format; no dynamic Java class loading."""
from dataclasses import dataclass
import hashlib
import json
import uuid

from .agent_status import AgentStatusSnapshot, _pairs, _text, decode_agent_status
from .errors import InvalidRequestError, ProtocolError

MESSAGE_CLASS = "org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl"
STATUS_CLASS = "org.integratedmodelling.klab.api.actors.RuntimeAgent$Status"
COMMANDS = {"start": ("AgentStartRequested", "Events"), "stop": ("AgentStopRequested", "Events"),
            "status": ("AgentStatusRequested", "Status")}
STATUSES = {"AgentStarted": "Events", "AgentStopped": "Events", "AgentStatusChanged": "Status", "AgentFailed": "Errors"}
FIELDS = {"@CLASS", "messageType", "messageClass", "dispatchId", "id", "inResponseTo", "timestamp", "queue", "activityTransientId"}


@dataclass(frozen=True)
class AgentMessage:
    kind: str
    message_id: int
    timestamp: int
    status: AgentStatusSnapshot | None


def agent_exchange(federation_id, agent_urn):
    if not _text(federation_id, 4096) or not _text(agent_urn, 4096):
        raise InvalidRequestError("Explicit bounded federation and agent identities are required")
    # Java UUID.nameUUIDFromBytes: v3 MD5 of these bytes, NOT uuid3(namespace, text).
    digest = hashlib.md5((federation_id + "\0" + agent_urn).encode("utf-8"), usedforsecurity=False).digest()
    return "klab.agent." + str(uuid.UUID(bytes=digest, version=3))


def encode_command(action, urn, message_id, timestamp):
    if (not isinstance(action, str) or action not in COMMANDS or not _text(urn, 4096)
            or type(message_id) is not int or not 1 <= message_id <= 2**63 - 1
            or type(timestamp) is not int or not 0 <= timestamp <= 2**63 - 1):
        raise InvalidRequestError("Invalid bounded agent command")
    kind, queue = COMMANDS[action]
    return json.dumps({"@CLASS": MESSAGE_CLASS, "messageType": kind, "messageClass": "AgentCommunication",
        "dispatchId": urn, "id": message_id, "inResponseTo": 0, "timestamp": timestamp,
        "queue": queue, "activityTransientId": 0}, separators=(",", ":")).encode("utf-8")


def decode_message(body, urn):
    if not isinstance(body, bytes) or len(body) > 65536:
        raise ProtocolError("Agent message must be at most 65536 UTF-8 bytes")
    try:
        data = json.loads(body.decode("utf-8"), object_pairs_hook=_pairs)
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Malformed agent message JSON") from None
    if (not isinstance(data, dict) or not FIELDS <= set(data)
            or set(data) - FIELDS - {"payloadClass", "payload"}
            or data["@CLASS"] != MESSAGE_CLASS or data["messageClass"] != "AgentCommunication"
            or data["dispatchId"] != urn):
        raise ProtocolError("Invalid agent envelope or different agent identity")
    for key in ("id", "timestamp", "inResponseTo", "activityTransientId"):
        if type(data[key]) is not int or not (1 if key == "id" else 0) <= data[key] <= 2**63 - 1:
            raise ProtocolError("Invalid agent message numeric field")
    kind = data["messageType"]
    if not isinstance(kind, str):
        raise ProtocolError("Invalid agent message type")
    commands = dict(COMMANDS.values())
    if kind in commands:
        if data["queue"] != commands[kind] or "payload" in data or "payloadClass" in data:
            raise ProtocolError("Invalid agent command envelope")
        return AgentMessage(kind, data["id"], data["timestamp"], None)
    if kind not in STATUSES or data["queue"] != STATUSES[kind] or data.get("payloadClass") != STATUS_CLASS:
        raise ProtocolError("Unsupported agent lifecycle message")
    try:
        encoded = json.dumps(data.get("payload"), ensure_ascii=False, allow_nan=False).encode("utf-8")
    except (ValueError, TypeError, UnicodeError, RecursionError):
        raise ProtocolError("Malformed agent lifecycle payload") from None
    status = decode_agent_status(encoded, expected_agent_urn=urn)
    if (kind == "AgentStarted" and status.state != "RUNNING"
            or kind == "AgentFailed" and status.state != "FAILED"
            or kind == "AgentStopped" and status.state not in ("STOPPED", "FAILED")):
        raise ProtocolError("Lifecycle envelope disagrees with its status payload")
    return AgentMessage(kind, data["id"], data["timestamp"], status)
