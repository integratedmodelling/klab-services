"""Read-only protocol1 source catalog, not semantic admission or model selection.

JAVA-GAP-0003: source-defined Java route/DTO; deployed compatibility NOT_TESTED.
No legacy fallback or delegation of context/Runtime identity to a supplier.
"""
from dataclasses import dataclass, field
import hashlib
import json
import math
import time

from .client import Client
from ._inspection import _reject_credential_leaves
from .dto import check_notifications
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError

ROUTE = "/api/v1/models/candidates"
SOURCE = "klab.model.candidate.source.v1"
HASH = "klab.model.candidate.sourceHash.v1"
MAX_BYTES = 1024 * 1024


@dataclass(frozen=True)
class ModelCandidateQuery:
    namespaces: tuple[str, ...] = ()
    projects: tuple[str, ...] = ()
    scenarios: tuple[str, ...] = ()

    def __post_init__(self):
        for values in (self.namespaces, self.projects, self.scenarios):
            if (type(values) is not tuple or len(values) > 32 or len(set(v for v in values if isinstance(v, str))) != len(values)
                    or any(not isinstance(v, str) or not v or len(v) > 256
                        or any(ord(c) < 33 or ord(c) == 127 for c in v) for v in values)):
                raise InvalidRequestError("Candidate selectors require immutable unique bounded text tuples")

    def to_wire(self):
        return {"version": 1, "namespaces": list(self.namespaces), "projects": list(self.projects), "scenarios": list(self.scenarios)}


@dataclass(frozen=True)
class ModelCandidateSource:
    model_urn: str
    namespace_urn: str
    project_urn: str | None
    provider_id: str
    timestamp: int
    source_sha256: str
    source_json: str = field(repr=False)


@dataclass(frozen=True)
class ModelCandidateCatalog:
    provider_id: str
    provider_url: str
    query: ModelCandidateQuery
    candidates: tuple[ModelCandidateSource, ...]
    evidence: str = "SOURCE_SNAPSHOT_NOT_SEMANTIC_ADMISSION_OR_MODEL_SELECTION"


def _json(raw):
    def unique(items):
        value = {}
        for key, item in items:
            if key in value:
                raise ValueError("Duplicate candidate JSON field")
            value[key] = item
        return value
    def constant(_):
        raise ValueError("Nonfinite candidate JSON")
    def finite(value):
        parsed = float(value)
        if not math.isfinite(parsed):
            raise ValueError("Overflowed candidate JSON number")
        return parsed
    try:
        if not isinstance(raw, bytes) or len(raw) > MAX_BYTES:
            raise ValueError("Candidate response exceeds byte budget")
        return json.loads(raw.decode("utf-8"), object_pairs_hook=unique, parse_constant=constant, parse_float=finite)
    except (ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Invalid bounded candidate source JSON") from None


def _text(value):
    return isinstance(value, str) and 0 < len(value) <= 512 and not any(ord(c) < 33 or ord(c) == 127 for c in value)


def _capabilities(raw, redact):
    value = _json(raw)
    if (not isinstance(value, dict) or value.get("type") != "RESOURCES" or not _text(value.get("serviceId"))
            or redact(value["serviceId"]) != value["serviceId"]):
        raise ProtocolError("Candidate supplier identity/type was not verified")
    if type(value.get("modelCandidateProtocol")) is not int or value["modelCandidateProtocol"] != 1:
        raise UnsupportedOperationError("Resources must advertise modelCandidateProtocol1; no candidate fallback")
    check_notifications(value, redact)
    return value["serviceId"]


def _descriptor(row, kind, provider, redact):
    if (not isinstance(row, dict) or row.get("knowledgeClass") != kind or row.get("serviceId") != provider
            or not _text(row.get("resourceUrn")) or row.get("projectUrn") is not None and not _text(row["projectUrn"])
            or type(row.get("timestamp")) is not int or not 0 <= row["timestamp"] <= 2**53 - 1
            or not isinstance(row.get("metadata"), dict)):
        raise ProtocolError("Candidate descriptor identity/class/timestamp is invalid")
    check_notifications(row, redact)
    metadata = row["metadata"]
    source, digest = metadata.get(SOURCE), metadata.get(HASH)
    if not isinstance(source, str) or redact(source) != source or not isinstance(digest, str):
        raise ProtocolError("Candidate source and hash must be exact credential-free text")
    try:
        encoded = source.encode("utf-8")
    except UnicodeError:
        raise ProtocolError("Candidate source is not exact valid UTF8") from None
    if hashlib.sha256(encoded).hexdigest() != digest:
        raise ProtocolError("Candidate source changed after capture")
    namespace = _json(encoded)
    try:
        _reject_credential_leaves(namespace, redact, "Candidate source contains credential material")
    except InvalidRequestError:
        raise ProtocolError("Candidate source contains credential material") from None
    if (not isinstance(namespace, dict)
            or namespace.get("@CLASS") != "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl"
            or not _text(namespace.get("urn"))
            or namespace.get("projectName") != row.get("projectUrn")
            or type(namespace.get("lastUpdateTimestamp")) is not int
            or namespace["lastUpdateTimestamp"] != row["timestamp"]
            or type(namespace.get("inactive", False)) is not bool or namespace.get("inactive", False)
            or not isinstance(namespace.get("statements"), list) or len(namespace["statements"]) > 128):
        raise ProtocolError("Candidate namespace differs from its descriptor or is inactive")
    if (kind == "NAMESPACE" and row["resourceUrn"] != namespace["urn"]
            or kind == "MODEL" and not any(isinstance(s, dict) and s.get("urn") == row["resourceUrn"]
                and s.get("@CLASS") == "org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl" for s in namespace["statements"])):
        raise ProtocolError("Candidate member is absent from the captured namespace")
    return namespace["urn"], source, digest


def _catalog(raw, provider, url, query, redact):
    value = _json(raw)
    if (not isinstance(value, dict) or not isinstance(value.get("results"), list)
            or not isinstance(value.get("namespaces"), list) or len(value["results"]) > 128
            or len(value["namespaces"]) > 128 or type(value.get("empty")) is not bool
            or value["empty"] != (not value["results"])):
        raise ProtocolError("Candidate ResourceSet must declare a bounded complete result list")
    check_notifications(value, redact)
    namespaces = {}
    for row in value["namespaces"]:
        namespace, source, digest = _descriptor(row, "NAMESPACE", provider, redact)
        if namespace in namespaces:
            raise ProtocolError("Duplicate candidate namespace")
        namespaces[namespace] = (source, digest)
    candidates = []; seen = set()
    for row in value["results"]:
        namespace, source, digest = _descriptor(row, "MODEL", provider, redact)
        if namespaces.get(namespace) != (source, digest) or row["resourceUrn"] in seen:
            raise ProtocolError("Candidate has no exact namespace snapshot or duplicates another member")
        seen.add(row["resourceUrn"])
        candidates.append(ModelCandidateSource(row["resourceUrn"], namespace, row.get("projectUrn"),
            provider, row["timestamp"], digest, source))
    return ModelCandidateCatalog(provider, url, query, tuple(candidates))


def _prepare(query, timeout):
    if not isinstance(query, ModelCandidateQuery) or type(timeout) not in (int, float) or not math.isfinite(timeout) or not 0 < timeout <= 300:
        raise InvalidRequestError("Candidate query requires explicit selectors and total timeout in (0,300]")
    return time.monotonic() + timeout


def inspect_model_candidates(client, query, *, timeout=10):
    """Read supplier-owned source only; no workflow/Runtime headers or legacy fallback."""
    deadline = _prepare(query, timeout)
    if not isinstance(client, Client):
        raise InvalidRequestError("Use matching synchronous candidate inspection client")
    options = {"deadline": deadline, "response": "bytes", "max_response_bytes": MAX_BYTES}
    capabilities = client.transport.request("resources", "GET", "/public/capabilities", **options)
    provider = _capabilities(capabilities, client.transport.redact)
    raw = client.transport.request("resources", "POST", ROUTE, json=query.to_wire(), **options)
    return _catalog(raw, provider, client.transport.endpoint("resources").url, query, client.transport.redact)


async def async_inspect_model_candidates(client, query, *, timeout=10):
    from .async_client import AsyncClient
    deadline = _prepare(query, timeout)
    if not isinstance(client, AsyncClient):
        raise InvalidRequestError("Use matching asynchronous candidate inspection client")
    options = {"deadline": deadline, "response": "bytes", "max_response_bytes": MAX_BYTES}
    capabilities = await client.transport.request("resources", "GET", "/public/capabilities", **options)
    provider = _capabilities(capabilities, client.transport.redact)
    raw = await client.transport.request("resources", "POST", ROUTE, json=query.to_wire(), **options)
    return _catalog(raw, provider, client.transport.endpoint("resources").url, query, client.transport.redact)
