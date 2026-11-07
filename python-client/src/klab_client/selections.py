"""Persisted relationship endpoints and bounded existing-observation collections.

Native source-defined headers/graph query shapes; no local semantic admission,
unresolved transaction snapshots, implicit endpoint creation or permission grants.
"""
from dataclasses import dataclass
from typing import cast

from .api.runtime import ObservationImpl
from .errors import ConfigurationError, InvalidRequestError, ProtocolError
from .requests import _positive_id
from .transport import _normalize_service_url


def _identity(context):
    return (_normalize_service_url(context.client.transport.endpoint("runtime").url),
        context.id, context.client._known_runtime_identity(context.configuration))


def relationship_options(context):
    selection = getattr(context, "_relationship_selection", None)
    if selection is None: return {}
    if not isinstance(selection, RelationshipSelection) or selection.origin != _identity(context):
        raise InvalidRequestError("Relationship selection origin changed; explicitly select again")
    return {"relationship": (selection.source_id, selection.target_id)}


@dataclass(frozen=True)
class RelationshipSelection:
    """Persisted endpoint identities, not a new relationship or authorization."""
    source_id: int
    target_id: int
    origin: tuple[str, str, str | None]

    def __post_init__(self):
        _positive_id(self.source_id); _positive_id(self.target_id)
        if self.source_id == self.target_id:
            raise InvalidRequestError("Relationship endpoints must be distinct persisted observations")
        if (not isinstance(self.origin, tuple) or len(self.origin) != 3
                or not isinstance(self.origin[0], str) or not isinstance(self.origin[1], str)
                or self.origin[2] is not None and not isinstance(self.origin[2], str)):
            raise InvalidRequestError("Relationship origin requires Runtime URL, root and known identity")


def between(context, source, target):
    from .client import scope_token
    relationship_options(context)
    if not context.client.runtime_service_id:
        raise ConfigurationError("Persisted relationship selection requires the configured home Runtime identity")
    def endpoint(value):
        if isinstance(value, ObservationImpl):
            bound = value._context
            if bound is None or bound.id != context.id:
                raise InvalidRequestError("Relationship endpoint must be bound to the selected root")
            context.client._validate_context_binding(bound, configuration=context.configuration)
            if value.metadata.get("im:storage-binding-source"):
                raise InvalidRequestError("Detached mediated views are not persisted relationship endpoints")
            value = value.id
        return _positive_id(value)
    selection = RelationshipSelection(endpoint(source), endpoint(target), _identity(context))
    result = type(context)(context.client, context.configuration, owned=context.owned, token=scope_token(context))
    result._relationship_selection = selection
    return result


@dataclass(frozen=True)
class ObservationPage:
    """One explicit page, not a complete, revision-leased or semantically grouped set."""
    items: tuple[ObservationImpl, ...]
    offset: int
    next_offset: int | None
    scope: str
    partial: bool = True
    consistency: str = "No revision lease; collection may change between explicit requests"


def _query(context, limit, offset, max_response_bytes):
    from .client import scope_token
    if (type(limit) is not int or not 1 <= limit <= 128
            or type(offset) is not int or not 0 <= offset <= 2**31 - 1
            or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
        raise InvalidRequestError("Observation page requires bounded limit, offset and response bytes")
    context.client._validate_context_binding(context)
    token = cast(str, scope_token(context))  # Bound Context grammar cannot produce None.
    relationship_options(context)
    path = token.split("#")[0].split(".")[2:]
    source = {"type": "OBSERVATION", "id": int(path[-1])} if path else {"type": "SCOPE"}
    return {"type": "QUERY", "resultType": "OBSERVATION", "source": source,
        "relationship": "HAS_CHILD", "depth": 1, "minimumDepth": 1, "limit": limit + 1, "offset": offset}, token


def _page(context, rows, limit, offset, token):
    from .client import scope_token
    from .handles import _decode_observation
    if scope_token(context) != token:
        raise ProtocolError("Observation collection scope changed during request")
    context.client._validate_context_binding(context)
    relationship_options(context)
    if not isinstance(rows, list) or len(rows) > limit + 1:
        raise ProtocolError("Observation collection response exceeds the requested page")
    decoded = tuple(_decode_observation(context, row) for row in rows)
    identities = [item.id for item in decoded]
    if (any(type(identity) is not int or not 1 <= identity <= 2**63 - 1 for identity in identities)
            or len(set(identities)) != len(identities)):
        raise ProtocolError("Observation collection requires distinct persisted identities")
    return ObservationPage(decoded[:limit], offset, offset + limit if len(decoded) > limit else None, token)


def observations(context, *, limit=64, offset=0, max_response_bytes=1024 * 1024):
    query, token = _query(context, limit, offset, max_response_bytes)
    selection = relationship_options(context)
    rows = context.client.transport.request("runtime", "POST", "/api/v1/query", json=query,
        scope=token, service_id=context.client.runtime_service_id,
        max_response_bytes=max_response_bytes, **selection)
    if relationship_options(context) != selection:
        raise ProtocolError("Relationship collection selection changed during request")
    return _page(context, rows, limit, offset, token)


async def async_observations(context, *, limit=64, offset=0, max_response_bytes=1024 * 1024):
    query, token = _query(context, limit, offset, max_response_bytes)
    selection = relationship_options(context)
    rows = await context.client.transport.request("runtime", "POST", "/api/v1/query", json=query,
        scope=token, service_id=context.client.runtime_service_id,
        max_response_bytes=max_response_bytes, **selection)
    if relationship_options(context) != selection:
        raise ProtocolError("Relationship collection selection changed during request")
    return _page(context, rows, limit, offset, token)
