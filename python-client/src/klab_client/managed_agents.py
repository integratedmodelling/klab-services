"""Explicit synthetic lifecycle proposal; NOT a k.Actors parser or native agent ABI.

JAVA-GAP-0008: maintained Runtime execution/independent cleanup remains unverified.
Existing native AgentExecutions/AgentChannel are separate and never fall back here.
"""
from dataclasses import dataclass, field
import re
import json

from .client import Client, Context, scope_token
from .handles import Handle, to_handle
from .errors import InvalidRequestError, ProtocolError

PROTOCOL = "klab.experimental.managed-agents/1"
PREFIX = "/experimental/managed-agents/v1/"
EXECUTION = "SYNTHETIC_STATE_MACHINE_NO_KACTORS_EXECUTION"


@dataclass(frozen=True)
class ManagedAgentReference:
    agent_id: str
    epoch: str
    fixture: str
    source: Handle
    context: Context = field(repr=False, compare=False)


@dataclass(frozen=True)
class ManagedAgentStatus:
    reference: ManagedAgentReference
    revision: int
    state: str
    cleanup_confirmed: bool
    fixture: str
    execution: str = EXECUTION


def _json(raw):
    def unique(items):
        value = {}
        for key, item in items:
            if key in value:
                raise ValueError("Duplicate lifecycle field")
            value[key] = item
        return value
    def constant(_):
        raise ValueError("Nonfinite lifecycle field")
    try:
        if not isinstance(raw, bytes) or len(raw) > 65536:
            raise ValueError("Lifecycle response byte budget exceeded")
        return json.loads(raw.decode("utf-8"), object_pairs_hook=unique, parse_constant=constant)
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Invalid bounded synthetic lifecycle JSON") from None


def _binding(context, allow_experimental):
    if allow_experimental is not True or not isinstance(context, Context) or scope_token(context) != context.id:
        raise InvalidRequestError("Managed reference requires opt-in and an originating root Context")
    context.client._validate_context_binding(context)
    return to_handle(context)


def _options(reference, allow_experimental):
    if not isinstance(reference, ManagedAgentReference):
        raise InvalidRequestError("Use an explicit managed reference")
    source = _binding(reference.context, allow_experimental)
    if (source.scope, source.runtime_id, source.runtime_url) != (reference.source.scope, reference.source.runtime_id, reference.source.runtime_url):
        raise InvalidRequestError("Managed reference belongs to another root/Runtime")
    if (reference.fixture not in ("idle", "finish", "fail")
            or any(not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{32}", value) is None for value in (reference.agent_id, reference.epoch))):
        raise InvalidRequestError("Invalid explicit managed reference identity")
    return {"scope": source.scope, "service_id": source.runtime_id, "max_response_bytes": 65536, "response": "bytes"}


def _status(raw, context, source, reference=None):
    value = _json(raw)
    fields = {"protocol", "agent_id", "epoch", "scope", "runtime_id", "revision", "state", "cleanup_confirmed", "fixture", "execution"}
    if (not isinstance(value, dict) or set(value) != fields or value["protocol"] != PROTOCOL
            or value["execution"] != EXECUTION or value["scope"] != source.scope or value["runtime_id"] != source.runtime_id
            or any(not isinstance(value[k], str) or re.fullmatch(r"[0-9a-f]{32}", value[k]) is None for k in ("agent_id", "epoch"))
            or type(value["revision"]) is not int or not 1 <= value["revision"] <= 16
            or value["state"] not in ("READY", "RUNNING", "STOPPING", "STOPPED", "FINISHED", "FAILED")
            or type(value["cleanup_confirmed"]) is not bool or value["fixture"] not in ("idle", "finish", "fail")
            or value["cleanup_confirmed"] and value["state"] not in ("STOPPED", "FINISHED", "FAILED")):
        raise ProtocolError("Managed reference response did not establish exact bounded synthetic lifecycle")
    if reference is not None and (value["agent_id"], value["epoch"], value["fixture"]) != (reference.agent_id, reference.epoch, reference.fixture):
        raise ProtocolError("Managed response belongs to another incarnation/agent")
    bound = reference or ManagedAgentReference(value["agent_id"], value["epoch"], value["fixture"], source, context)
    return ManagedAgentStatus(bound, value["revision"], value["state"], value["cleanup_confirmed"], value["fixture"])


def _create(context, fixture, allow_experimental):
    source = _binding(context, allow_experimental)
    if fixture not in ("idle", "finish", "fail"):
        raise InvalidRequestError("Select a finite synthetic fixture, not executable k.Actors source")
    return source, {"protocol": PROTOCOL, "fixture": fixture}


def _command(reference, command, revision, allow_experimental):
    options = _options(reference, allow_experimental)
    if command not in ("start", "stop", "purge") or type(revision) is not int or not 1 <= revision <= 16:
        raise InvalidRequestError("Choose an explicit bounded lifecycle command and observed revision")
    return options, {"protocol": PROTOCOL, "epoch": reference.epoch, "expected_revision": revision}


def _command_status(raw, reference, command, revision):
    result = _status(raw, reference.context, reference.source, reference)
    if result.state != {"start": "RUNNING", "stop": "STOPPING"}[command] or result.revision != revision + 1 or result.cleanup_confirmed:
        raise ProtocolError("Managed lifecycle command transition was not confirmed")
    return result


def create_managed_reference(context, *, fixture="idle", allow_experimental=False):
    source, body = _create(context, fixture, allow_experimental)
    if not isinstance(context.client, Client):
        raise InvalidRequestError("Use matching synchronous managed reference client")
    raw = context.client.transport.request("runtime", "POST", PREFIX + "create", scope=source.scope,
        service_id=source.runtime_id, json=body, ambiguous=True, response="bytes", max_response_bytes=65536)
    status = _status(raw, context, source)
    if status.state != "READY" or status.revision != 1 or status.cleanup_confirmed or status.fixture != fixture:
        raise ProtocolError("New managed reference was not confirmed deferred and unexecuted")
    return status


def read_managed_reference(reference, *, allow_experimental=False):
    options = _options(reference, allow_experimental)
    if not isinstance(reference.context.client, Client):
        raise InvalidRequestError("Use matching synchronous managed reference client")
    raw = reference.context.client.transport.request("runtime", "GET", PREFIX + reference.agent_id,
        params={"protocol": PROTOCOL, "epoch": reference.epoch}, **options)
    return _status(raw, reference.context, reference.source, reference)


def command_managed_reference(reference, command, *, expected_revision, allow_experimental=False):
    options, body = _command(reference, command, expected_revision, allow_experimental)
    if not isinstance(reference.context.client, Client):
        raise InvalidRequestError("Use matching synchronous managed reference client")
    raw = reference.context.client.transport.request("runtime", "POST", PREFIX + reference.agent_id + "/" + command,
        json=body, ambiguous=True, **options)
    if command == "purge":
        value = _json(raw)
        if not isinstance(value, dict) or value.get("purged") is not True or value != {"protocol": PROTOCOL, "agent_id": reference.agent_id, "epoch": reference.epoch, "purged": True}:
            raise ProtocolError("Managed reference purge not confirmed")
        return True
    return _command_status(raw, reference, command, expected_revision)


async def async_create_managed_reference(context, *, fixture="idle", allow_experimental=False):
    from .async_client import AsyncClient
    source, body = _create(context, fixture, allow_experimental)
    if not isinstance(context.client, AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous managed reference client")
    raw = await context.client.transport.request("runtime", "POST", PREFIX + "create", scope=source.scope,
        service_id=source.runtime_id, json=body, ambiguous=True, response="bytes", max_response_bytes=65536)
    status = _status(raw, context, source)
    if status.state != "READY" or status.revision != 1 or status.cleanup_confirmed or status.fixture != fixture:
        raise ProtocolError("New managed reference was not confirmed deferred and unexecuted")
    return status


async def async_read_managed_reference(reference, *, allow_experimental=False):
    from .async_client import AsyncClient
    options = _options(reference, allow_experimental)
    if not isinstance(reference.context.client, AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous managed reference client")
    raw = await reference.context.client.transport.request("runtime", "GET", PREFIX + reference.agent_id,
        params={"protocol": PROTOCOL, "epoch": reference.epoch}, **options)
    return _status(raw, reference.context, reference.source, reference)


async def async_command_managed_reference(reference, command, *, expected_revision, allow_experimental=False):
    from .async_client import AsyncClient
    options, body = _command(reference, command, expected_revision, allow_experimental)
    if not isinstance(reference.context.client, AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous managed reference client")
    raw = await reference.context.client.transport.request("runtime", "POST", PREFIX + reference.agent_id + "/" + command,
        json=body, ambiguous=True, **options)
    if command == "purge":
        value = _json(raw)
        if not isinstance(value, dict) or value.get("purged") is not True or value != {"protocol": PROTOCOL, "agent_id": reference.agent_id, "epoch": reference.epoch, "purged": True}:
            raise ProtocolError("Managed reference purge not confirmed")
        return True
    return _command_status(raw, reference, command, expected_revision)
