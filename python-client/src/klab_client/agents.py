"""Server-parsed source-only agent translation; never a running agent handle."""
from dataclasses import dataclass, field
import hashlib
import json
from typing import Mapping

from .client import Client
from .dto import check_notifications, object_payload, required_text
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from ._inspection import freeze, _reject_credential_leaves

BEHAVIOR_CLASS = "org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl"
AGENT_CLASS = "org.integratedmodelling.common.runtime.actors.AgentImpl"


@dataclass(frozen=True)
class AgentSourcePreview:
    """Generated source is not compiled Java, an executing agent or scientific evidence."""
    behavior_urn: str
    source_sha256: str
    parser_source_sha256: str
    parser_edge_whitespace_trimmed: bool
    java_code: str = field(repr=False)
    resources_url: str
    resources_id: str
    runtime_url: str
    runtime_id: str
    raw: Mapping = field(repr=False)
    execution: str = "NOT_STARTED_NOT_REGISTERED_NOT_JAVA_COMPILED"


class AgentPreviews:
    """Translate server-parsed BEHAVIOR source with fixed nonexecution options.

    No DTO intake, execution flags, custom messages, lifecycle controls, background
    workers, broker credentials or automatic request replay are exposed.
    """
    def __init__(self, client, *, max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        if not isinstance(client, Client):
            raise InvalidRequestError("Agent previews require a synchronous configured Client")
        self._configure(client, max_source_bytes, max_response_bytes)

    def _configure(self, client, max_source_bytes, max_response_bytes):
        if (type(max_source_bytes) is not int or not 1 <= max_source_bytes <= 1024 * 1024
                or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
            raise InvalidRequestError("Agent previews require bounded source/response byte budgets")
        self.client = client
        self.max_source_bytes, self.max_response_bytes = max_source_bytes, max_response_bytes
        self._binding = None

    def _safe(self, value):
        try:
            encoded = json.dumps(value, allow_nan=False, ensure_ascii=False)
            size = len(encoded.encode("utf-8"))
        except (TypeError, ValueError, RecursionError, UnicodeError):
            raise InvalidRequestError("Agent preview values must be finite UTF-8 JSON data") from None
        if size > self.max_response_bytes:
            raise InvalidRequestError("Agent preview payload exceeded the response byte budget")
        if self.client.transport.redact(encoded) != encoded:
            raise InvalidRequestError("Agent preview values must not contain configured credential material")
        _reject_credential_leaves(value, self.client.transport.redact,
            "Agent preview values must not contain configured credential material")

    def _identity(self):
        resources = self.client.resources.capabilities(max_response_bytes=self.max_response_bytes)
        runtime = self.client.runtime.capabilities(max_response_bytes=self.max_response_bytes)
        return self._accept_identity(resources, runtime)

    def _accept_identity(self, resources, runtime):
        binding = (self.client.transport.endpoint("resources").url, resources.service_id,
                   self.client.transport.endpoint("runtime").url, runtime.service_id)
        self._safe(binding)
        if self._binding is not None and self._binding != binding:
            raise ProtocolError("Agent preview service identity changed; create a new preview client explicitly")
        self._binding = binding
        return binding

    def _source(self, source):
        try:
            encoded = source.encode("utf-8") if isinstance(source, str) else b""
        except UnicodeError:
            raise InvalidRequestError("Agent source must be valid UTF-8 text") from None
        if not encoded or not source.strip() or len(encoded) > self.max_source_bytes:
            raise InvalidRequestError("Agent source must be nonblank text within its UTF-8 byte budget")
        self._safe(source)

    def _parsed(self, source, parsed, binding):
        parsed = object_payload(parsed, "parsed behavior")
        check_notifications(parsed, self.client.transport.redact)
        parser_source = parsed.get("sourceCode")
        if (parsed.get("@CLASS") != BEHAVIOR_CLASS or not isinstance(parser_source, str)
                or parser_source not in (source, source.strip(" \t\r\n"))):
            raise ProtocolError("Behavior parser must preserve source except explicitly reported outer space/tab/newline trimming")
        if parsed.get("behaviorType") != "BEHAVIOR":
            raise UnsupportedOperationError("Source preview currently admits only BEHAVIOR, not executable scripts/apps/tasks or components")
        urn = required_text(parsed, "urn")
        if parsed.get("serviceId") != binding[1]:
            raise ProtocolError("Parsed behavior belongs to a different Resources identity")
        self._safe(parsed)
        return parsed, parser_source, urn

    def _request(self, parsed):
        return {"behavior": parsed, "compileOnly": True, "reportJavaCode": True,
                "doNotStart": True, "doNotBindSession": True, "doNotBindObservation": True,
                "observationId": -1}

    def _result(self, result, urn):
        result = object_payload(result, "source-only agent preview")
        check_notifications(result, self.client.transport.redact)
        if result.get("@CLASS") != AGENT_CLASS or result.get("behaviorUrn") != urn:
            raise ProtocolError("Agent preview response has an unexpected class or behavior identity")
        if (result.get("viable") is not True or result.get("alive") is not False
                or result.get("messagingConnected") is not False or result.get("startDeferred") is not False
                or result.get("urn") is not None or result.get("scopeId") is not None
                or result.get("startedAt") != -1 or result.get("observationId") != -1):
            raise ProtocolError("Runtime did not confirm a viable unregistered unstarted source-only preview")
        code = required_text(result, "javaCode")
        self._safe(result)
        return result, code

    def _receipt(self, source, parser_source, urn, result, code, binding):
        return AgentSourcePreview(urn, hashlib.sha256(source.encode("utf-8")).hexdigest(),
                                  hashlib.sha256(parser_source.encode("utf-8")).hexdigest(),
                                  parser_source != source, code,
                                   binding[0], binding[1], binding[2], binding[3],
                                   freeze(result, self.client.transport.redact))

    def preview(self, source):
        """Translate once with fixed compileOnly (DO_NOT_COMPILE_JAVA) flags.

        No running/registered agent or automatic retry is created by this client.
        """
        self._source(source)
        binding = self._identity()
        parsed = self.client.transport.request("resources", "POST", "/parseAsset/BEHAVIOR", text=source,
                                               max_response_bytes=self.max_response_bytes)
        parsed, parser_source, urn = self._parsed(source, parsed, binding)
        self._identity()
        result = self.client.transport.request("runtime", "POST", "/api/v1/instantiate/agent",
            json=self._request(parsed), max_response_bytes=self.max_response_bytes)
        result, code = self._result(result, urn)
        self._identity()
        return self._receipt(source, parser_source, urn, result, code, binding)


class AsyncAgentPreviews(AgentPreviews):
    """Native awaited nonexecuting preview; no thread facade, broker or hidden loop."""
    def __init__(self, client, *, max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        from .async_client import AsyncClient
        if not isinstance(client, AsyncClient):
            raise InvalidRequestError("Async agent previews require a configured AsyncClient")
        self._configure(client, max_source_bytes, max_response_bytes)

    async def _identity(self):
        resources = await self.client.resources.capabilities(max_response_bytes=self.max_response_bytes)
        runtime = await self.client.runtime.capabilities(max_response_bytes=self.max_response_bytes)
        return self._accept_identity(resources, runtime)

    async def preview(self, source):
        self._source(source)
        binding = await self._identity()
        parsed = await self.client.transport.request("resources", "POST", "/parseAsset/BEHAVIOR", text=source,
                                                     max_response_bytes=self.max_response_bytes)
        parsed, parser_source, urn = self._parsed(source, parsed, binding)
        await self._identity()
        result = await self.client.transport.request("runtime", "POST", "/api/v1/instantiate/agent",
            json=self._request(parsed), max_response_bytes=self.max_response_bytes)
        result, code = self._result(result, urn)
        await self._identity()
        return self._receipt(source, parser_source, urn, result, code, binding)
