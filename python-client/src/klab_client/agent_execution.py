"""Explicit deferred BEHAVIOR registration and native broker control; no write replay."""
from dataclasses import dataclass, field
import hashlib
from threading import RLock, get_ident
from typing import Mapping

from ._inspection import freeze
from .agent_control import AgentControlBinding
from .agent_messaging import AgentChannel
from .agent_status import _text
from .agents import AGENT_CLASS, BEHAVIOR_CLASS, AgentPreviews, AsyncAgentPreviews
from .client import Context, scope_token
from .dto import check_notifications, object_payload, required_text
from .errors import (InvalidRequestError, ProtocolError, ServerError, SubmissionOutcomeUnknown,
                     TransportError, UnsupportedOperationError)
from .handles import to_handle


@dataclass(frozen=True)
class AgentRegistration:
    """Confirmed deferred registration, not execution, private-session or cleanup proof."""
    binding: AgentControlBinding
    behavior_urn: str
    source_sha256: str
    parser_source_sha256: str
    parser_edge_whitespace_trimmed: bool
    messaging_connected: bool
    raw: Mapping = field(repr=False)


class DeferredAgent:
    """Explicit one-time broker attachment. Close detaches locally, NEVER stops/releases."""
    def __init__(self, receipt, check_binding):
        if not isinstance(receipt, AgentRegistration) or not callable(check_binding):
            raise InvalidRequestError("Deferred agent requires a registration and binding validator")
        self._receipt = receipt
        self._check_binding = check_binding
        self._channel = None
        self._detached = False
        self._owner = get_ident()

    @property
    def receipt(self):
        return self._receipt

    def _thread(self):
        if get_ident() != self._owner:
            raise InvalidRequestError("DeferredAgent attachment and control are caller-driven and thread-affine")

    def attach(self, broker, *, federation_id, timeout=5, max_events=128, max_total_bytes=1024 * 1024,
               publication="required_confirms"):
        self._thread()
        self._check_binding()
        if self._channel is not None or self._detached:
            raise InvalidRequestError("Deferred agent cannot reconnect or replay commands through a fresh channel")
        if not self.receipt.messaging_connected:
            raise UnsupportedOperationError("Runtime did not report a connected agent transport; operator reconciliation required")
        self._channel = AgentChannel(self.receipt.binding, broker, federation_id=federation_id,
            timeout=timeout, max_events=max_events, max_total_bytes=max_total_bytes, publication=publication)
        self._channel.open()
        return self

    def _attached(self):
        self._thread()
        self._check_binding()
        if self._channel is None or self._detached:
            raise InvalidRequestError("Explicitly attach the registered agent before control or status reads")
        return self._channel

    @property
    def view(self):
        return self._channel.view if self._channel is not None else None

    def start(self):
        return self._attached().start(binding=self.receipt.binding)

    def stop(self):
        return self._attached().stop(binding=self.receipt.binding)

    def request_status(self):
        return self._attached().request_status(binding=self.receipt.binding)

    def read(self):
        return self._attached().read(binding=self.receipt.binding)

    def close(self):
        self._thread()
        self._detached = True
        if self._channel is not None:
            self._channel.close()


class AgentExecutions:
    """Register one source-parsed BEHAVIOR, compiled but NOT started or observation-bound.

    Existing root context only; SCRIPT/APP/UNITTEST are excluded because Runtime
    creates private sessions for them. No automatic start, retry, orphan rollback,
    private-session creation/disposal or scientific algorithm is implemented here.
    Principal/incarnation inputs are explicit operator assertions, not wire proof.
    """
    def __init__(self, context, *, principal_key, runtime_incarnation,
                  max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        self._configure(context, principal_key, runtime_incarnation, max_source_bytes,
                        max_response_bytes, AgentPreviews, RLock())

    def _configure(self, context, principal_key, runtime_incarnation, max_source_bytes,
                   max_response_bytes, preview_type, lock):
        if not isinstance(context, Context) or scope_token(context) != context.id:
            raise InvalidRequestError("Agent registration requires an explicitly bound root Context")
        if not _text(principal_key, 4096) or not _text(runtime_incarnation, 4096):
            raise InvalidRequestError("Explicit principal/incarnation assertions required; never infer them from uptime or IDs")
        self._context, self._client = context, context.client
        self._preview = preview_type(context.client, max_source_bytes=max_source_bytes,
                                      max_response_bytes=max_response_bytes)
        self._reference = to_handle(context)
        self._principal, self._incarnation = principal_key, runtime_incarnation
        self._attempted = False
        self._lock = lock
        self._check()

    def _check(self):
        self._client._validate_context_binding(self._context)
        if (to_handle(self._context) != self._reference
                or self._client.transport.endpoint("runtime").url != self._reference.runtime_url
                or self._client.runtime_service_id != self._reference.runtime_id):
            raise ProtocolError("Agent context/Runtime binding changed; no operation sent")

    def _intake(self, source, allow_execute):
        if allow_execute is not True:
            raise InvalidRequestError("Explicit allow_execute=True is required for remote compiled registration")
        if self._attempted:
            raise InvalidRequestError("Agent registration already attempted; do not replay an ambiguous creation")
        try:
            source_bytes = source.encode("utf-8") if isinstance(source, str) else b""
        except UnicodeError:
            raise InvalidRequestError("Agent source must be valid UTF-8") from None
        if not source_bytes or not source.strip() or len(source_bytes) > self._preview.max_source_bytes:
            raise InvalidRequestError("Agent source must be nonblank and within its UTF-8 budget")
        self._check()
        self._preview._safe(source)
        return source_bytes

    def _selected_identity(self, identity):
        if identity[2:] != (self._reference.runtime_url, self._reference.runtime_id):
            raise ProtocolError("Selected Runtime differs from the bound context")

    def _parsed(self, source, parsed, identity):
        parsed = object_payload(parsed, "parsed executable behavior")
        check_notifications(parsed, self._client.transport.redact)
        parser_source = parsed.get("sourceCode")
        if (parsed.get("@CLASS") != BEHAVIOR_CLASS or parsed.get("serviceId") != identity[1]
                or not isinstance(parser_source, str) or parser_source not in (source, source.strip(" \t\r\n"))):
            raise ProtocolError("Executable behavior parser changed source or Resources identity")
        if parsed.get("behaviorType") != "BEHAVIOR":
            raise UnsupportedOperationError("Only BEHAVIOR is admitted; scripts/apps/tests require a separate owned-session contract")
        urn = required_text(parsed, "urn")
        self._preview._safe(parsed)
        return parsed, parser_source, urn

    def _body(self, parsed):
        return {"behavior": parsed, "compileOnly": False, "reportJavaCode": False,
                "doNotStart": True, "doNotBindSession": True, "doNotBindObservation": True,
                "observationId": -1}

    def _registered(self, result, urn, source_bytes, parser_source, source):
        result = object_payload(result, "deferred agent registration")
        check_notifications(result, self._client.transport.redact)
        if (result.get("@CLASS") != AGENT_CLASS or result.get("behaviorUrn") != urn
                or result.get("viable") is not True or result.get("alive") is not False
                or result.get("startDeferred") is not True or type(result.get("messagingConnected")) is not bool
                or result.get("scopeId") != self._reference.scope
                or type(result.get("observationId")) is not int or result.get("observationId") != -1
                or type(result.get("startedAt")) is not int or result.get("startedAt") != -1):
            raise ProtocolError("Runtime did not confirm exact unstarted/unbound registered-agent identity")
        agent_urn = required_text(result, "urn")
        binding = AgentControlBinding(agent_urn, self._reference.runtime_url, self._reference.runtime_id,
            self._reference.scope, self._principal, self._incarnation)
        self._preview._safe(result)
        self._check()
        return AgentRegistration(binding, urn, hashlib.sha256(source_bytes).hexdigest(),
            hashlib.sha256(parser_source.encode("utf-8")).hexdigest(), parser_source != source,
            result["messagingConnected"], freeze(result, self._client.transport.redact))

    def create(self, source, *, allow_execute=False):
        """One explicitly consented compiled registration, deferred and unbound; no replay."""
        with self._lock:
            source_bytes = self._intake(source, allow_execute)
            identity = self._preview._identity()
            self._selected_identity(identity)
            parsed = self._client.transport.request("resources", "POST", "/parseAsset/BEHAVIOR", text=source,
                max_response_bytes=self._preview.max_response_bytes)
            parsed, parser_source, urn = self._parsed(source, parsed, identity)
            self._check()
            self._preview._identity()
            self._attempted = True
            try:
                result = self._client.transport.request("runtime", "POST", "/api/v1/instantiate/agent",
                    scope=self._reference.scope, service_id=self._reference.runtime_id,
                    json=self._body(parsed), ambiguous=True, max_response_bytes=self._preview.max_response_bytes)
                receipt = self._registered(result, urn, source_bytes, parser_source, source)
                self._preview._identity()
                return DeferredAgent(receipt, self._check)
            except (ProtocolError, InvalidRequestError, ServerError, TransportError):
                raise SubmissionOutcomeUnknown("Agent registration unconfirmed; inspect owned remote state, do not replay or assume rollback") from None


class AsyncAgentExecutions(AgentExecutions):
    """Native awaited one-shot deferred registration; broker attachment stays explicit sync.

    Cancelling a wait never authorizes recreation or implies remote rollback.
    Principal/incarnation remain explicit operator assertions, not service proof.
    """
    def __init__(self, context, *, principal_key, runtime_incarnation,
                 max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        import asyncio
        from .async_client import AsyncContext
        if not isinstance(context, AsyncContext):
            raise InvalidRequestError("Async agent registration requires an AsyncContext")
        self._configure(context, principal_key, runtime_incarnation, max_source_bytes,
                        max_response_bytes, AsyncAgentPreviews, asyncio.Lock())

    async def create(self, source, *, allow_execute=False):
        async with self._lock:
            source_bytes = self._intake(source, allow_execute)
            identity = await self._preview._identity()
            self._selected_identity(identity)
            parsed = await self._client.transport.request("resources", "POST", "/parseAsset/BEHAVIOR", text=source,
                max_response_bytes=self._preview.max_response_bytes)
            parsed, parser_source, urn = self._parsed(source, parsed, identity)
            self._check()
            await self._preview._identity()
            self._check()
            self._attempted = True
            try:
                result = await self._client.transport.request("runtime", "POST", "/api/v1/instantiate/agent",
                    scope=self._reference.scope, service_id=self._reference.runtime_id,
                    json=self._body(parsed), ambiguous=True, max_response_bytes=self._preview.max_response_bytes)
                receipt = self._registered(result, urn, source_bytes, parser_source, source)
                await self._preview._identity()
                self._check()
                return DeferredAgent(receipt, self._check)
            except (ProtocolError, InvalidRequestError, ServerError, TransportError):
                raise SubmissionOutcomeUnknown("Agent registration unconfirmed; inspect owned remote state, do not replay or assume rollback") from None
