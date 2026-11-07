"""Revision-bound namespace diagnostics through the existing Reasoner contract.

No ontology synchronization, publication, executable-model admission or local
semantic checking. Parser receipts and semantic diagnostics remain distinct.
"""
from dataclasses import dataclass, field
import hashlib
import re
from typing import Mapping

from ._inspection import freeze
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from .evidence import _plain
from .semantics import StaleKnowledgeError
from .source_previews import SourcePreview, SourcePreviews
from .transport import _normalize_service_url

ROUTE = "/api/v1/validate/document"
STATUSES = ("COMPLETE", "SYNTAX_ERRORS", "UNAVAILABLE", "STALE_KNOWLEDGE", "FAILED")


@dataclass(frozen=True)
class ValidationKnowledge:
    service_url: str
    service_id: str
    worldview_id: str
    revision: int
    semantic_session_id: str
    content_fingerprint: str


@dataclass(frozen=True)
class SourceValidationReceipt:
    document_urn: str
    document_version: str
    source_sha256: str
    parser_source_sha256: str
    resources_url: str
    resources_id: str
    knowledge: ValidationKnowledge
    status: str
    raw: Mapping = field(repr=False)
    limit: str = "Server-reported namespace source diagnostics, not publication or executable/scientific admission"


class SourceValidation(SourcePreviews):
    """Validate an explicit namespace parser snapshot against pinned knowledge.

Create a new instance to change configured endpoints/credentials. Each explicit
validate call obtains current knowledge; it never silently retries a stale request.
"""
    def _prepare(self, preview, document_version, expected_revision):
        if not isinstance(preview, SourcePreview):
            raise InvalidRequestError("Validation requires an explicit SourcePreviews parser receipt")
        if preview.kind != "NAMESPACE":
            raise UnsupportedOperationError("Only namespace diagnostics supported; ontology validation may synchronize native knowledge")
        if (not isinstance(document_version, str) or not document_version.strip() or len(document_version) > 128):
            raise InvalidRequestError("Document version requires explicit nonblank text within128characters")
        if expected_revision is not None and (type(expected_revision) is not int or not 0 <= expected_revision <= 2**63 - 1):
            raise InvalidRequestError("Expected knowledge revision must be a nonnegative Java-long integer")
        payload = _plain(preview.raw)
        self._safe(payload); self._safe(document_version)
        source = payload.get("sourceCode") if isinstance(payload, dict) else None
        for digest in (preview.source_sha256, preview.parser_source_sha256):
            if not isinstance(digest, str) or re.fullmatch('[0-9a-f]{64}', digest) is None:
                raise InvalidRequestError("Parser receipt requires exact SHA256 source digests")
        if (type(preview.edge_whitespace_trimmed) is not bool
                or not preview.edge_whitespace_trimmed and preview.source_sha256 != preview.parser_source_sha256):
            raise InvalidRequestError("Parser receipt source-trimming policy and digests disagree")
        if (not isinstance(source, str) or not source.strip() or len(source.encode('utf-8')) > self._max_source
                or hashlib.sha256(source.encode('utf-8')).hexdigest() != preview.parser_source_sha256
                or payload.get("urn") != preview.urn or payload.get("serviceId") != preview.service_id
                or payload.get("@CLASS") != "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl"):
            raise InvalidRequestError("Parser snapshot source, hash, identity or namespace shape changed")
        return payload

    def _knowledge(self, payload):
        self._safe(payload)
        if not isinstance(payload, dict) or payload.get("consistent") is not True:
            raise ProtocolError("Namespace validation requires known consistent Reasoner knowledge")
        revision = payload.get("knowledgeRevision")
        if type(revision) is not int or not 0 <= revision <= 2**63 - 1:
            raise ProtocolError("Namespace validation requires a known bounded knowledge revision")
        keys = ("serviceId", "worldviewId", "semanticSessionId", "semanticContentFingerprint")
        if any(not isinstance(payload.get(key), str) or not payload[key].strip() or len(payload[key]) > 512 for key in keys):
            raise ProtocolError("Validation requires explicit Reasoner/worldview/session/content identities")
        return ValidationKnowledge(_normalize_service_url(self._transport.endpoint("reasoner").url),
            payload['serviceId'], payload['worldviewId'], revision, payload['semanticSessionId'], payload['semanticContentFingerprint'])

    def _body(self, preview, document_version, expected_revision, parsed, resources, knowledge):
        if resources != (preview.service_url, preview.service_id):
            raise ProtocolError("Parser receipt does not belong to the current Resources service")
        if expected_revision is not None and expected_revision != knowledge.revision:
            raise StaleKnowledgeError("Expected knowledge changed; explicitly revalidate the current source")
        return {"namespace": parsed, "documentVersion": document_version,
            "knowledgeRevision": knowledge.revision, "executableModels": False,
            "classifierPreparableDependencies": False, "executionContext": None,
            "preparableMemberQualityDependencies": []}

    def _receipt(self, payload, preview, version, knowledge):
        self._safe(payload)
        if not isinstance(payload, dict) or payload.get('status') not in STATUSES:
            raise ProtocolError("Unknown namespace validation response status")
        if (payload.get('documentUrn') != preview.urn or payload.get('documentVersion') != version
                or payload.get('sourceHash') != preview.parser_source_sha256
                or payload.get('executableModels') is not False
                or payload.get('classifierPreparableDependencies') is not False
                or payload.get('executionContext') is not None
                or payload.get('preparableMemberQualityDependencies') != []):
            raise ProtocolError("Validation response does not match exact source/version/non-executable request")
        if (payload.get('status') == 'STALE_KNOWLEDGE' or type(payload.get('knowledgeRevision')) is not int
                or payload.get('knowledgeRevision') != knowledge.revision):
            raise StaleKnowledgeError("Validation used different knowledge; explicitly revalidate, never publish this receipt")
        if payload.get('status') == 'COMPLETE' and (
                payload.get('semanticSessionId') != knowledge.semantic_session_id
                or payload.get('semanticContentFingerprint') != knowledge.content_fingerprint):
            raise StaleKnowledgeError("Validation semantic session or content changed")
        notifications = payload.get('notifications')
        if not isinstance(notifications, list) or any(not isinstance(item, dict) for item in notifications):
            raise ProtocolError("Validation diagnostics must be an explicit object array")
        return SourceValidationReceipt(preview.urn, version, preview.source_sha256, preview.parser_source_sha256,
            preview.service_url, preview.service_id, knowledge, payload['status'], freeze(payload, self._transport.redact))

    def _reasoner_binding(self, expected):
        if self._client.transport is not self._transport or self._transport.endpoint('reasoner') != expected:
            raise StaleKnowledgeError("Reasoner transport/endpoint/credential changed; no implicit rebind")

    def validate(self, preview, *, document_version, expected_revision=None):
        parsed = self._prepare(preview, document_version, expected_revision)
        resources = self._identity()
        before = self._transport.endpoint('reasoner')
        knowledge = self._knowledge(self._client.reasoner.capabilities(max_response_bytes=self._max_response).raw)
        self._reasoner_binding(before)
        body = self._body(preview, document_version, expected_revision, parsed, resources, knowledge)
        payload = self._transport.request('reasoner', 'POST', ROUTE, json=body, max_response_bytes=self._max_response)
        current = self._knowledge(self._client.reasoner.capabilities(max_response_bytes=self._max_response).raw)
        self._reasoner_binding(before)
        if current != knowledge:
            raise StaleKnowledgeError("Reasoner binding/knowledge changed during validation; explicitly revalidate")
        self._identity()  # Existing parser identity binding refuses any Resources drift.
        return self._receipt(payload, preview, document_version, knowledge)


class AsyncSourceValidation(SourceValidation):
    """Native awaited namespace diagnostics; no sync facade or automatic retry."""
    def __init__(self, client, *, max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        from .async_client import AsyncClient
        if not isinstance(client, AsyncClient):
            raise InvalidRequestError("Async source validation requires a native AsyncClient")
        self._configure(client, max_source_bytes, max_response_bytes)

    async def parse(self, source, *, kind="NAMESPACE", allow_edge_trimming=False):
        source_bytes = self._source(source, kind, allow_edge_trimming)
        url, identity = await self._identity()
        payload = await self._transport.request('resources', 'POST', f'/parseAsset/{kind}', text=source,
            max_response_bytes=self._max_response)
        result = self._preview(payload, source, source_bytes, kind, allow_edge_trimming, url, identity)
        await self._identity()
        return result

    async def _identity(self):
        before = self._before_identity()
        capability = await self._client.resources.capabilities(max_response_bytes=self._max_response)
        return self._after_identity(before, capability)

    async def validate(self, preview, *, document_version, expected_revision=None):
        parsed = self._prepare(preview, document_version, expected_revision)
        resources = await self._identity()
        before = self._transport.endpoint('reasoner')
        knowledge = self._knowledge((await self._client.reasoner.capabilities(max_response_bytes=self._max_response)).raw)
        self._reasoner_binding(before)
        body = self._body(preview, document_version, expected_revision, parsed, resources, knowledge)
        payload = await self._transport.request('reasoner', 'POST', ROUTE, json=body, max_response_bytes=self._max_response)
        current = self._knowledge((await self._client.reasoner.capabilities(max_response_bytes=self._max_response)).raw)
        self._reasoner_binding(before)
        if current != knowledge:
            raise StaleKnowledgeError("Reasoner binding/knowledge changed during validation; explicitly revalidate")
        await self._identity()
        return self._receipt(payload, preview, document_version, knowledge)
