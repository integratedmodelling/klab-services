"""Server-parsed source-only agent translation; never a running agent handle."""
from dataclasses import dataclass, field
import hashlib
import json
from typing import Mapping

from .client import Client
from .dto import check_notifications, object_payload, required_text
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from ._inspection import freeze

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
        if (type(max_source_bytes) is not int or not 1 <= max_source_bytes <= 1024 * 1024
                or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
            raise InvalidRequestError("Agent previews require bounded source/response byte budgets")
        self.client = client
        self.max_source_bytes, self.max_response_bytes = max_source_bytes, max_response_bytes
        self._binding = None

    def _safe(self, value):
        try:
            encoded = json.dumps(value, allow_nan=False, ensure_ascii=False)
        except (TypeError, ValueError, RecursionError):
            raise InvalidRequestError("Agent preview values must be finite JSON data") from None
        if len(encoded.encode("utf-8")) > self.max_response_bytes:
            raise InvalidRequestError("Agent preview payload exceeded the response byte budget")
        if self.client.transport.redact(encoded) != encoded:
            raise InvalidRequestError("Agent preview values must not contain configured credential material")

    def _identity(self):
        resources = self.client.resources.capabilities(max_response_bytes=self.max_response_bytes)
        runtime = self.client.runtime.capabilities(max_response_bytes=self.max_response_bytes)
        binding = (self.client.transport.endpoint("resources").url, resources.service_id,
                   self.client.transport.endpoint("runtime").url, runtime.service_id)
        self._safe(binding)
        if self._binding is not None and self._binding != binding:
            raise ProtocolError("Agent preview service identity changed; create a new preview client explicitly")
        self._binding = binding
        return binding

    def preview(self, source):
        """Parse with Resources, then translate once with Runtime's compileOnly option.

        compileOnly means DO_NOT_COMPILE_JAVA, not an executed/verified agent.
        A lost response is not retried and never establishes translation success.
        """
        if not isinstance(source, str) or not source.strip() or len(source.encode("utf-8")) > self.max_source_bytes:
            raise InvalidRequestError("Agent source must be nonblank text within its UTF-8 byte budget")
        self._safe(source)
        binding = self._identity()
        parsed = self.client.transport.request("resources", "POST", "/parseAsset/BEHAVIOR", text=source,
                                               max_response_bytes=self.max_response_bytes)
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
        self._identity()
        result = self.client.transport.request("runtime", "POST", "/api/v1/instantiate/agent",
            json={"behavior": parsed, "compileOnly": True, "reportJavaCode": True,
                  "doNotStart": True, "doNotBindSession": True, "doNotBindObservation": True,
                  "observationId": -1}, max_response_bytes=self.max_response_bytes)
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
        self._identity()
        return AgentSourcePreview(urn, hashlib.sha256(source.encode("utf-8")).hexdigest(),
                                  hashlib.sha256(parser_source.encode("utf-8")).hexdigest(),
                                  parser_source != source, code,
                                  *binding, freeze(result, self.client.transport.redact))
