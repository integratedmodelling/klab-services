"""Explicit ADD-only source authoring against existing Resources contracts.

No parser port, unversioned overwrite, project-lock ownership or write replay.
"""
from dataclasses import dataclass, field
import json
import re
from types import MappingProxyType
from typing import Mapping

from .client import Client
from .dto import check_notifications, object_payload, required_text
from .errors import (InvalidRequestError, ProtocolError, ServerError,
                     SubmissionOutcomeUnknown, TransportError, UnsupportedOperationError)
from ._inspection import freeze, text
from .evidence import _plain

DOCUMENTS = {
    "NAMESPACE": "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl",
    "SCRIPT": "org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl",
}
COLLECTIONS = ("results", "namespaces", "ontologies", "behaviors", "resources", "observationStrategies", "projects")


@dataclass(frozen=True)
class ParsedSource:
    kind: str
    urn: str
    service_url: str
    service_id: str
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class AuthoringReceipt:
    kind: str
    urn: str
    operation: str
    service_url: str
    service_id: str
    changes: tuple[Mapping, ...] = field(repr=False)
    limit: str = "Recorded acknowledgement only; not version history, immutable publication or scientific acceptance"


class Authoring:
    """Synchronous parse + explicitly consented creation, not a full editing SDK."""
    def __init__(self, client, *, max_source_bytes=1024 * 1024, max_response_bytes=2 * 1024 * 1024):
        if not isinstance(client, Client):
            raise InvalidRequestError("Authoring requires a synchronous configured Client")
        if (type(max_source_bytes) is not int or not 1 <= max_source_bytes <= 1024 * 1024
                or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
            raise InvalidRequestError("Authoring requires explicit bounded source/response budgets")
        self.client = client
        self.max_source_bytes, self.max_response_bytes = max_source_bytes, max_response_bytes
        self._binding = None
        self._created = {}

    def _identity(self):
        capability = self.client.resources.capabilities(max_response_bytes=self.max_response_bytes)
        binding = (self.client.transport.endpoint("resources").url, capability.service_id)
        self._safe(binding)
        if self._binding is not None and self._binding != binding:
            raise ProtocolError("Resources identity changed; create a new authoring session explicitly")
        self._binding = binding
        return binding

    def _safe(self, value):
        try:
            encoded = json.dumps(value, allow_nan=False)
        except (ValueError, TypeError, RecursionError):
            raise InvalidRequestError("Authoring values must be finite JSON data") from None
        if len(encoded.encode("utf-8")) > self.max_response_bytes:
            raise InvalidRequestError("Authoring payload exceeded the configured byte budget")
        if self.client.transport.redact(encoded) != encoded:
            raise InvalidRequestError("Authoring identity/source must not contain configured credential material")

    def parse(self, source, *, kind="NAMESPACE"):
        if not isinstance(kind, str) or kind not in DOCUMENTS:
            raise UnsupportedOperationError("Source authoring admits only NAMESPACE and SCRIPT in this release")
        if not isinstance(source, str) or not source.strip() or len(source.encode("utf-8")) > self.max_source_bytes:
            raise InvalidRequestError("Source must be nonblank text within the configured UTF-8 byte budget")
        self._safe(source)
        url, identity = self._identity()
        payload = self.client.transport.request("resources", "POST", f"/parseAsset/{kind}", text=source,
                                                max_response_bytes=self.max_response_bytes)
        payload = object_payload(payload, "parsed source")
        check_notifications(payload, self.client.transport.redact)
        if payload.get("@CLASS") != DOCUMENTS[kind] or payload.get("sourceCode") != source:
            raise ProtocolError("Parser must return the admitted class and exact unchanged source")
        if kind == "SCRIPT" and payload.get("behaviorType") != "SCRIPT":
            raise ProtocolError("SCRIPT parsing returned a different behavior kind")
        urn = required_text(payload, "urn")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*", urn):
            raise ProtocolError("Parser returned an unsafe source URN")
        self._safe(payload)
        return ParsedSource(kind, urn, url, identity, freeze(payload, self.client.transport.redact))

    def _add(self, kind, urn, body, allow_write):
        if allow_write is not True:
            raise InvalidRequestError("Explicit allow_write=True is required to create remote assets")
        text(urn, "Asset URN", 512)
        self._safe(body)
        url, identity = self._identity()
        expected_urn = urn.split("/", 1)[1] if kind == "PROJECT" else urn
        try:
            rows = self.client.transport.request("resources", "PUT", f"/api/v1/submit/{kind}/ADD",
                params={"urn": urn}, text=json.dumps(body, allow_nan=False), ambiguous=True,
                max_response_bytes=self.max_response_bytes)
            if not isinstance(rows, list) or not 1 <= len(rows) <= 256:
                raise ProtocolError("Asset creation requires bounded nonempty ResourceSet acknowledgements")
            found = False
            for row in rows:
                row = object_payload(row, "authoring ResourceSet")
                check_notifications(row, self.client.transport.redact)
                if row.get("empty") is True:
                    raise ProtocolError("Asset creation returned an empty ResourceSet")
                for key in COLLECTIONS:
                    descriptors = row.get(key, [])
                    if not isinstance(descriptors, list):
                        raise ProtocolError("ResourceSet descriptors must be arrays")
                    for descriptor in descriptors:
                        descriptor = object_payload(descriptor, "authoring descriptor")
                        check_notifications(descriptor, self.client.transport.redact)
                        if (descriptor.get("resourceUrn") == expected_urn and descriptor.get("knowledgeClass") == kind
                                and descriptor.get("operation") == "CREATE" and descriptor.get("serviceId") == identity):
                            if kind == "PROJECT" and row.get("workspace") != urn.split("/", 1)[0]:
                                raise ProtocolError("Project acknowledgement belongs to a different workspace")
                            found = True
            if not found:
                raise ProtocolError("No matching CREATE acknowledgement from the selected Resources service")
            if self.client.transport.endpoint("resources").url != url or self.client._service_id_cache.get("resources") != identity:
                raise ProtocolError("Authoring service identity changed during asset creation")
            receipt = AuthoringReceipt(kind, urn, "CREATE", url, identity,
                tuple(freeze(row, self.client.transport.redact) for row in rows))
        except (ProtocolError, ServerError, TransportError):
            raise SubmissionOutcomeUnknown("Creation did not produce a confirmed acknowledgement; source persistence may have occurred. Inspect remote state; do not replay or assume rollback") from None
        self._created[(kind, urn)] = receipt
        return receipt

    def create_workspace(self, name, *, allow_write=False):
        text(name, "Workspace name", 128)
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", name):
            raise InvalidRequestError("Workspace name must be a simple local identifier")
        return self._add("WORKSPACE", name, {"urn": name}, allow_write)

    def create_project(self, workspace, name, *, allow_write=False):
        text(name, "Project name", 128)
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", name):
            raise InvalidRequestError("Project name must be a simple local identifier")
        if not isinstance(workspace, AuthoringReceipt) or self._created.get(("WORKSPACE", workspace.urn)) is not workspace:
            raise InvalidRequestError("Project creation requires this session's confirmed newly created workspace")
        urn = workspace.urn + "/" + name
        return self._add("PROJECT", urn, {"urn": urn}, allow_write)

    def create_document(self, parsed, project, *, allow_write=False):
        if (not isinstance(parsed, ParsedSource) or parsed.kind not in DOCUMENTS
                or not isinstance(project, AuthoringReceipt)
                or self._created.get(("PROJECT", project.urn)) is not project):
            raise InvalidRequestError("Document creation requires parsed source and this session's newly created project")
        if self._binding != (parsed.service_url, parsed.service_id):
            raise InvalidRequestError("Parsed source belongs to a different Resources identity")
        payload = object_payload(_plain(parsed.raw), "parsed authoring source")
        if (payload.get("urn") != parsed.urn or payload.get("@CLASS") != DOCUMENTS[parsed.kind]
                or not isinstance(payload.get("sourceCode"), str)
                or not isinstance(parsed.urn, str)
                or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*", parsed.urn)):
            raise InvalidRequestError("Parsed source identity is inconsistent")
        metadata = payload.get("metadata") or {}
        if not isinstance(metadata, dict) or any(not isinstance(key, str) for key in metadata):
            raise InvalidRequestError("Source metadata must be a checked text-key mapping")
        if any("authoring" in key.lower() for key in metadata):
            raise UnsupportedOperationError("ADD cannot select an existing authoring source")
        payload["projectName"] = project.urn.split("/", 1)[1]
        return self._add(parsed.kind, parsed.urn, payload, allow_write)

    @property
    def created(self):
        """Local confirmations only, not remote leases or automatic cleanup instructions."""
        return MappingProxyType(dict(self._created))
