"""Bounded read-only recorded neighborhoods, never a complete or revision-pinned lineage graph."""
from dataclasses import dataclass, field
from datetime import datetime, timezone
from types import MappingProxyType
from typing import Mapping

from .client import scope_token
from .errors import InvalidRequestError, MissingAssetError, ProtocolError, UnsupportedOperationError
from .exports import _context
from .handles import Handle, _decode_observation, to_handle

# GraphModel.Relationship: preserve execution effects separately from planned resolution.
RELATIONSHIPS = (
    "CREATED", "INSTANTIATED", "ACKNOWLEDGED", "DETECTED", "SIMULATED", "MEASURED",
    "QUANTIFIED", "VALUED", "CATEGORIZED", "VERIFIED", "CLASSIFIED", "CHARACTERIZED",
    "TRANSFORMED", "CONNECTED", "CONTEXTUALIZED_BY", "AFFECTS", "EMERGED_FROM",
    "CONTRIBUTED_TO", "RESOLVED",
)
CLASSES = {
    "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl": "observation",
    "org.integratedmodelling.klab.api.provenance.impl.ActivityImpl": "activity",
    "org.integratedmodelling.common.runtime.ActuatorImpl": "actuator",
    "org.integratedmodelling.common.knowledge.CohortImpl": "cohort",
}
ACTIVITY_TYPES = {"CONTEXT_INITIALIZATION", "SUBMISSION", "INITIALIZATION", "RESOLUTION", "INSTANTIATION",
    "ACKNOWLEDGEMENT", "DETECTION", "SIMULATION", "MEASURE", "QUANTIFICATION", "VALUATION", "CATEGORIZATION",
    "VERIFICATION", "CLASSIFICATION", "CHARACTERIZATION", "TRANSFORMATION", "CONNECTION"}


@dataclass(frozen=True)
class ProvenanceNode:
    id: int
    kind: str
    urn: str | None
    activity_type: str | None
    outcome: str | None
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class ProvenanceLink:
    source: ProvenanceNode
    target: ProvenanceNode
    relationship: str
    sequence: int
    properties: Mapping = field(repr=False)

    @property
    def evidence(self):
        return "planned resolution" if self.relationship in {"RESOLVED", "CONTEXTUALIZED_BY"} else "recorded relationship"


@dataclass(frozen=True)
class ProvenanceView:
    observation: Handle
    links: tuple[ProvenanceLink, ...]
    fetched_at: datetime
    offset: int
    next_offset: int | None
    partial: bool = True
    consistency: str = "unknown: no shared export/provenance revision contract"


def _freeze(value, redact):
    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(item, redact) for key, item in value.items()
            if key.lower().replace("_", "-") not in {
                "authorization", "token", "access-token", "credential", "password", "server-key", "certificate"}})
    if isinstance(value, list):
        return tuple(_freeze(item, redact) for item in value)
    return redact(value) if isinstance(value, str) else value


def _node(payload, redact):
    if (not isinstance(payload, dict) or type(payload.get("id")) is not int
            or not 0 < payload["id"] <= 2**63 - 1 or not isinstance(payload.get("@CLASS"), str)
            or payload["@CLASS"] not in CLASSES):
        raise ProtocolError("Provenance endpoint has an unsupported class or invalid identity")
    raw = _freeze(payload, redact)
    for key in ("urn", "type", "outcome"):
        if raw.get(key) is not None and (not isinstance(raw[key], str) or not raw[key].strip()):
            raise ProtocolError("Malformed provenance endpoint fields")
    if CLASSES[raw["@CLASS"]] == "activity" and (
            raw.get("type") not in ACTIVITY_TYPES or raw.get("outcome") not in {None, "SUCCESS", "FAILURE", "INTERNAL_FAILURE"}):
        raise ProtocolError("Unknown recorded activity type or outcome")
    return ProvenanceNode(raw["id"], CLASSES[raw["@CLASS"]], raw.get("urn"),
                          raw.get("type"), raw.get("outcome"), raw)


def fetch_provenance(observation, *, limit=64, offset=0, max_response_bytes=1024 * 1024):
    """Explicit refresh of one page of incident recorded links; no recursive traversal.

    Pagination is stable only in an unchanged graph. Even an empty page is a
    partial neighborhood, not proof of absent provenance. Errors are not empty views.
    """
    if (type(limit) is not int or not 1 <= limit <= 128 or type(offset) is not int or not 0 <= offset <= 2**31 - 1
            or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
        raise InvalidRequestError("Provenance requires limit 1..128, nonnegative offset and byte budget 1..16777216")
    client = getattr(getattr(observation, "_context", None), "client", None)
    context = _context(client, observation)
    reference = to_handle(observation)
    # Recheck current context membership; never treat a cached DTO as access authority.
    membership = client.transport.request("runtime", "POST", "/api/v1/query",
        json={"type": "QUERY", "resultType": "OBSERVATION", "id": observation.id, "limit": 2},
        scope=scope_token(context), service_id=reference.runtime_id, max_response_bytes=max_response_bytes)
    if membership == []:
        raise MissingAssetError("Observation is absent or inaccessible in the current context")
    if not isinstance(membership, list) or len(membership) != 1:
        raise ProtocolError("Observation membership must return one asset")
    current = _decode_observation(context, membership[0], observation.id)
    if current.urn != observation.urn:
        raise ProtocolError("Observation membership returned a different URN")
    children = [{"type": "QUERY", "resultType": "LINK", direction: {"type": "OBSERVATION", "id": observation.id},
                 "relationship": relationship, "depth": 1, "minimumDepth": 1}
                for direction in ("source", "target") for relationship in RELATIONSHIPS]
    query = {"type": "OR", "resultType": "LINK", "children": children, "limit": limit + 1, "offset": offset}
    try:
        rows = client.transport.request("runtime", "POST", "/api/v1/query", json=query,
            scope=scope_token(context), service_id=reference.runtime_id, max_response_bytes=max_response_bytes)
    except InvalidRequestError as failure:
        if getattr(failure, "status_code", None) == 422:
            raise UnsupportedOperationError("This Runtime does not support recorded neighborhood queries") from None
        raise
    if not isinstance(rows, list) or len(rows) > limit + 1:
        raise ProtocolError("Provenance query returned an invalid or over-budget page")
    links = []
    for row in rows:
        if (not isinstance(row, dict) or row.get("relationship") not in RELATIONSHIPS
                or type(row.get("sequence")) is not int or not isinstance(row.get("properties"), dict)):
            raise ProtocolError("Malformed recorded provenance link")
        source, target = _node(row.get("source"), client.transport.redact), _node(row.get("target"), client.transport.redact)
        anchors = [node for node in (source, target) if node.id == observation.id and node.kind == "observation"]
        if not anchors or any(node.urn != observation.urn for node in anchors):
            raise ProtocolError("Provenance link does not belong to the requested observation")
        links.append(ProvenanceLink(source, target, row["relationship"], row["sequence"],
                                    _freeze(row["properties"], client.transport.redact)))
    return ProvenanceView(reference, tuple(links[:limit]), datetime.now(timezone.utc), offset,
                          offset + limit if len(rows) > limit else None)
