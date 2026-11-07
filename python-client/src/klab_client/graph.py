"""Bounded typed pages of recorded context graph links, never snapshot lineage."""
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from .api.runtime import ObservationImpl
from .client import Client, Context, scope_token
from .errors import InvalidRequestError, MissingAssetError, ProtocolError, UnsupportedOperationError
from .handles import Handle, to_handle
from .provenance import CLASSES, RELATIONSHIPS, ProvenanceLink, ProvenanceNode, _node
from ._inspection import freeze
from .evidence import _plain

GRAPH_RELATIONSHIPS = RELATIONSHIPS + ("HAS_CHILD", "HAS_MEMBER", "HAS_ACTIVITY", "TRIGGERED")


@dataclass(frozen=True)
class GraphPage:
    context: Handle
    anchor: ProvenanceNode
    links: tuple[ProvenanceLink, ...]
    fetched_at: datetime
    direction: str
    relationships: tuple[str, ...]
    offset: int
    next_offset: int | None
    partial: bool = True
    consistency: str = "No revision lease; pagination is stable only in an unchanged graph"


class GraphBrowser:
    """Explicit single-page requests; no recursive crawl, mutation or Cypher API.

    Nodes are passive descriptors. Each call rechecks current context membership;
    an ID or a previously fetched page is never permission to bypass that check.
    Only synchronous Client is accepted; no hidden loop or async-thread facade.
    """
    def __init__(self, context, *, max_links=64, max_response_bytes=1024 * 1024):
        if not isinstance(context, Context) or not isinstance(context.client, Client):
            raise InvalidRequestError("Graph browsing requires a synchronous bound Context")
        self._configure(context, max_links, max_response_bytes)

    def _configure(self, context, max_links, max_response_bytes):
        if (type(max_links) is not int or not 1 <= max_links <= 128
                or type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024):
            raise InvalidRequestError("Graph budgets require 1..128 links and 1..16777216 response bytes")
        self.context, self.client = context, context.client
        self.max_links, self.max_response_bytes = max_links, max_response_bytes
        self.client._validate_context_binding(context)
        self.reference = to_handle(context)

    def _before_request(self):
        self.client._validate_context_binding(self.context)
        if to_handle(self.context) != self.reference:
            raise ProtocolError("Graph context identity changed; create a new browser explicitly")

    def _after_request(self):
        if to_handle(self.context) != self.reference:
            raise ProtocolError("Graph context identity changed during the request")

    def _request(self, query):
        self._before_request()
        try:
            result = self.client.transport.request("runtime", "POST", "/api/v1/query", json=query,
                scope=scope_token(self.context), service_id=self.reference.runtime_id,
                max_response_bytes=self.max_response_bytes)
        except InvalidRequestError as error:
            if getattr(error, "status_code", None) == 422:
                raise UnsupportedOperationError("Runtime does not support this typed graph query") from None
            raise
        self._after_request()
        return result

    def _selection(self, anchor, direction, relationships, offset):
        if (not isinstance(direction, str) or direction not in ("incoming", "outgoing", "both")
                or type(offset) is not int or not 0 <= offset <= 2**31 - 1):
            raise InvalidRequestError("Graph direction and nonnegative bounded offset must be explicit")
        if (not isinstance(relationships, (list, tuple)) or not relationships
                or any(not isinstance(r, str) or r not in GRAPH_RELATIONSHIPS for r in relationships)
                or len(set(relationships)) != len(relationships)):
            raise InvalidRequestError("Graph relationships must be a nonempty unique supported selection")
        if isinstance(anchor, ObservationImpl):
            if anchor.metadata.get("im:storage-binding-source"):
                raise UnsupportedOperationError("Detached mediated views require a native graph identity contract")
            if anchor._context is None or anchor._context.client is not self.client or anchor._context.id != self.context.id:
                raise InvalidRequestError("Graph observation belongs to a different client/context")
            descriptor = {"@CLASS": next(key for key, kind in CLASSES.items() if kind == "observation"),
                          "id": anchor.id, "urn": anchor.urn}
        elif isinstance(anchor, ProvenanceNode):
            descriptor = _plain(anchor.raw)
            checked = _node(descriptor, self.client.transport.redact)
            if (checked.id, checked.kind, checked.urn) != (anchor.id, anchor.kind, anchor.urn):
                raise InvalidRequestError("Graph node fields disagree with their source descriptor")
        else:
            raise InvalidRequestError("Graph anchor requires a bound observation or typed returned node")
        expected = _node(descriptor, self.client.transport.redact)
        return expected, tuple(relationships)

    def _membership(self, rows, expected):
        if rows == []:
            raise MissingAssetError("Graph anchor is absent or inaccessible in the current context")
        if not isinstance(rows, list) or len(rows) != 1:
            raise ProtocolError("Graph membership must return exactly one typed node")
        current = _node(rows[0], self.client.transport.redact)
        if (current.id, current.kind, current.urn) != (expected.id, expected.kind, expected.urn):
            raise ProtocolError("Graph membership returned a different anchor identity")
        return current

    def _link_query(self, current, direction, relationships, offset):
        directions = ("source", "target") if direction == "both" else (("source",) if direction == "outgoing" else ("target",))
        children: list[dict[str, Any]] = [{"type": "QUERY", "resultType": "LINK", side: {"type": current.kind.upper(), "id": current.id},
                     "relationship": rel, "depth": 1, "minimumDepth": 1}
                    for side in directions for rel in relationships]
        query: dict[str, Any] = children[0] if len(children) == 1 else {"type": "OR", "resultType": "LINK", "children": children}
        query.update(limit=self.max_links + 1, offset=offset)
        return query

    def _page(self, rows, current, direction, relationships, offset):
        if not isinstance(rows, list) or len(rows) > self.max_links + 1:
            raise ProtocolError("Graph returned an invalid or over-budget page")
        links = []
        for row in rows:
            if (not isinstance(row, dict) or row.get("relationship") not in relationships
                    or type(row.get("sequence")) is not int or not isinstance(row.get("properties"), dict)):
                raise ProtocolError("Malformed typed graph relationship")
            source, target = _node(row.get("source"), self.client.transport.redact), _node(row.get("target"), self.client.transport.redact)
            ends = (source, target) if direction == "both" else ((source,) if direction == "outgoing" else (target,))
            if not any((n.id, n.kind, n.urn) == (current.id, current.kind, current.urn) for n in ends):
                raise ProtocolError("Graph link does not match the requested anchor/direction")
            links.append(ProvenanceLink(source, target, row["relationship"], row["sequence"],
                                        freeze(row["properties"], self.client.transport.redact)))
        return GraphPage(self.reference, current, tuple(links[:self.max_links]), datetime.now(timezone.utc),
                          direction, tuple(relationships), offset, offset + self.max_links if len(rows) > self.max_links else None)

    def neighbors(self, anchor, *, direction="both", relationships=GRAPH_RELATIONSHIPS, offset=0):
        """One explicit page (membership + links); no recursive crawl or automatic paging."""
        expected, relationships = self._selection(anchor, direction, relationships, offset)
        rows = self._request({"type": "QUERY", "resultType": expected.kind.upper(), "id": expected.id, "limit": 2})
        current = self._membership(rows, expected)
        rows = self._request(self._link_query(current, direction, relationships, offset))
        return self._page(rows, current, direction, relationships, offset)


class AsyncGraphBrowser(GraphBrowser):
    """Native awaited bounded pages; no hidden thread, replay or revision authority."""
    def __init__(self, context, *, max_links=64, max_response_bytes=1024 * 1024):
        from .async_client import AsyncClient, AsyncContext
        if not isinstance(context, AsyncContext) or not isinstance(context.client, AsyncClient):
            raise InvalidRequestError("Async graph browsing requires a bound AsyncContext")
        self._configure(context, max_links, max_response_bytes)

    async def _request(self, query):
        self._before_request()
        try:
            result = await self.client.transport.request("runtime", "POST", "/api/v1/query", json=query,
                scope=scope_token(self.context), service_id=self.reference.runtime_id,
                max_response_bytes=self.max_response_bytes)
        except InvalidRequestError as error:
            if getattr(error, "status_code", None) == 422:
                raise UnsupportedOperationError("Runtime does not support this typed graph query") from None
            raise
        self._after_request()
        return result

    async def neighbors(self, anchor, *, direction="both", relationships=GRAPH_RELATIONSHIPS, offset=0):
        expected, relationships = self._selection(anchor, direction, relationships, offset)
        rows = await self._request({"type": "QUERY", "resultType": expected.kind.upper(), "id": expected.id, "limit": 2})
        current = self._membership(rows, expected)
        rows = await self._request(self._link_query(current, direction, relationships, offset))
        return self._page(rows, current, direction, relationships, offset)
