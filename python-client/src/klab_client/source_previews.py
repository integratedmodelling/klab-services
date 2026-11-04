"""Read-only server parsing, deliberately separate from authoring write admission."""
from dataclasses import dataclass, field
import hashlib
import json
import re
from types import MappingProxyType
from typing import Mapping

from ._inspection import freeze
from .client import Client
from .dto import check_notifications, object_payload, required_text
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError

DOCUMENT_CLASSES = MappingProxyType({
    "NAMESPACE": "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl",
    "ONTOLOGY": "org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl",
    "OBSERVATION_STRATEGY_DOCUMENT": "org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl",
    "BEHAVIOR": "org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl",
})


@dataclass(frozen=True)
class SourcePreview:
    """Parser receipt, not authoring input, scientific acceptance or persistence."""
    kind: str
    urn: str
    service_url: str
    service_id: str
    source_sha256: str
    parser_source_sha256: str
    edge_whitespace_trimmed: bool
    raw: Mapping = field(repr=False)


class SourcePreviews:
    """Bounded source-only preview; never submit/import/compile/execute a document.

    Uses existing Resources /parseAsset/{knowledgeClass}. No parser is ported.
    Does not extend Authoring.DOCUMENTS or produce a ParsedSource accepted by it.
    """
    def __init__(self, client, *, max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        if not isinstance(client, Client):
            raise InvalidRequestError("Source previews require a synchronous configured Client")
        if (type(max_source_bytes) is not int or not 1 <= max_source_bytes <= 1024 * 1024
                or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
            raise InvalidRequestError("Source previews require bounded UTF-8 source/response budgets")
        self._client, self._transport = client, client.transport
        self._max_source, self._max_response = max_source_bytes, max_response_bytes
        self._binding = None

    def _safe(self, value):
        try:
            encoded = json.dumps(value, ensure_ascii=False, allow_nan=False).encode("utf-8")
        except (ValueError, TypeError, UnicodeError, RecursionError):
            raise ProtocolError("Parser preview must contain finite UTF-8 JSON data") from None
        if len(encoded) > self._max_response:
            raise ProtocolError("Parser preview exceeds the response budget")
        # Inspect decoded leaves too: JSON escaping must not hide a quoted or
        # non-ASCII credential from the check before source transmission.
        pending = [value]
        while pending:
            item = pending.pop()
            if isinstance(item, str):
                if self._transport.redact(item) != item:
                    raise InvalidRequestError("Preview identity/source must not contain configured credential material")
            elif isinstance(item, dict):
                pending.extend(item)
                pending.extend(item.values())
            elif isinstance(item, (list, tuple)):
                pending.extend(item)

    def _identity(self):
        if self._client.transport is not self._transport:
            raise ProtocolError("Preview transport changed; no implicit rebinding")
        endpoint = self._transport.endpoint("resources")
        before = (endpoint.url, endpoint.credential)
        if self._binding is not None and before != (self._binding[0], self._binding[2]):
            raise ProtocolError("Resources preview endpoint/credential changed; no request sent")
        capability = self._client.resources.capabilities(max_response_bytes=self._max_response)
        after = self._transport.endpoint("resources")
        binding = (before[0], capability.service_id, before[1])
        if ((after.url, after.credential) != before or self._client.transport is not self._transport
                or self._binding is not None and self._binding != binding):
            raise ProtocolError("Resources preview identity/configuration changed; no implicit rebinding")
        self._safe(binding[:2])
        self._binding = binding
        return binding[:2]

    def parse(self, source, *, kind="NAMESPACE", allow_edge_trimming=False):
        if not isinstance(kind, str) or kind not in DOCUMENT_CLASSES:
            raise UnsupportedOperationError("Preview admits only namespace, ontology, strategy document or behavior")
        if type(allow_edge_trimming) is not bool:
            raise InvalidRequestError("Edge-whitespace policy must be an explicit boolean")
        try:
            source_bytes = source.encode("utf-8") if isinstance(source, str) else b""
        except UnicodeError:
            raise InvalidRequestError("Preview source must be valid UTF-8 text") from None
        if not source_bytes or not source.strip() or len(source_bytes) > self._max_source:
            raise InvalidRequestError("Preview source must be nonblank text within its UTF-8 byte budget")
        self._safe(source)
        url, identity = self._identity()
        payload = self._transport.request("resources", "POST", f"/parseAsset/{kind}", text=source,
                                          max_response_bytes=self._max_response)
        payload = object_payload(payload, "source preview")
        check_notifications(payload, self._transport.redact)
        parser_source = payload.get("sourceCode")
        admitted = (source, source.strip(" \t\r\n")) if allow_edge_trimming else (source,)
        if (payload.get("@CLASS") != DOCUMENT_CLASSES[kind] or payload.get("serviceId") != identity
                or not isinstance(parser_source, str) or parser_source not in admitted):
            raise ProtocolError("Parser preview class/identity/source differs from the explicit input policy")
        urn = required_text(payload, "urn")
        if len(urn) > 512 or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*", urn):
            raise ProtocolError("Parser returned an unsafe source URN")
        self._safe(payload)
        self._identity()
        return SourcePreview(kind, urn, url, identity, hashlib.sha256(source_bytes).hexdigest(),
            hashlib.sha256(parser_source.encode("utf-8")).hexdigest(), parser_source != source,
            freeze(payload, self._transport.redact))
