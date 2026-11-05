"""Remote semantic exploration; no cache, local inference or automatic candidate choice."""
from dataclasses import dataclass, field
import math
import re
from typing import Mapping
from urllib.parse import urlsplit

from ._inspection import bounds, freeze, html_summary, text
from .client import scope_token
from .api.primitives import SemanticType
from .dto import check_notifications, concept_from_wire, object_payload, observable_from_wire, required_text
from .errors import InvalidRequestError, MissingAssetError, ProtocolError, UnsupportedOperationError
from .transport import _normalize_service_url


class StaleKnowledgeError(InvalidRequestError):
    """The selected semantic evidence no longer matches observed knowledge."""


@dataclass(frozen=True)
class KnowledgeState:
    service_url: str
    service_id: str
    scope: str | None
    worldview_id: str
    revision: int | None

    @property
    def freshness(self):
        return ("observed revision; not an atomic snapshot" if self.revision is not None
                else "unknown: service supplies no reliable knowledge revision; refresh explicitly")


@dataclass(frozen=True)
class SemanticResult:
    kind: str
    urn: str
    label: str | None
    knowledge: KnowledgeState
    raw: Mapping = field(repr=False)

    def summary(self):
        return f"{self.kind}: {self.urn}" + (f" — {self.label}" if self.label else "") + f"\n{self.knowledge.freshness}"

    def _repr_html_(self):
        return html_summary(self.summary())


@dataclass(frozen=True)
class AuthorityCandidate:
    authority: str
    code: str
    locator: str
    label: str | None
    description: str | None
    score: float | None
    knowledge: KnowledgeState
    raw: Mapping = field(repr=False)
    _owner: object = field(repr=False, compare=False)

    def summary(self):
        return f"{self.locator}" + (f" — {self.label}" if self.label else "")

    def _repr_html_(self):
        return html_summary(self.summary())


@dataclass(frozen=True)
class AuthoritySearch:
    status: str
    matches: tuple[AuthorityCandidate, ...]
    total: int
    next_offset: int | None
    knowledge: KnowledgeState
    raw: Mapping = field(repr=False)

    def select(self, code):
        """Select an exact canonical code, never a display label or implicit first hit."""
        selected = [candidate for candidate in self.matches if candidate.code == code]
        if self.status != "OK" or len(selected) != 1:
            raise InvalidRequestError("Select one exact canonical code from a successful search page")
        return selected[0]

    def summary(self):
        return (f"Authority search: {self.status}; {len(self.matches)} returned; provider list total {self.total}\n"
                + "\n".join(candidate.summary() for candidate in self.matches))

    def _repr_html_(self):
        return html_summary(self.summary())


def _authority(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Z][A-Z0-9_]+(?:\.[A-Z][A-Z0-9_]+)*", value):
        raise InvalidRequestError("Use the configured worldview-local authority name, not a provider URN")
    return value


def _locator(authority, code):
    # Exact AuthorityIdentitySyntax.encode spelling. This serializes a code, not its meaning.
    text(code, "Authority code", 2048)
    if re.fullmatch(r"[a-z][a-z0-9_]*|[A-Z][A-Z0-9_]+|[0-9]+", code):
        return authority + ":" + code
    return authority + ":[" + code.replace("\\", "\\\\").replace("]", "\\]") + "]"


def _optional_text(payload, key):
    value = payload.get(key)
    if value is not None and not isinstance(value, str):
        raise ProtocolError(f"{key} must be text or null")
    return value


class SemanticExplorer:
    """Use a configured Client/Connection.client and its current authorization.

    Every operation reads capabilities afresh. Stored views are inspection snapshots,
    not authorization or permanent semantic validity. Documentation URLs are never fetched.
    """

    def __init__(self, client, scope=None, *, max_items=100, max_response_bytes=1024 * 1024):
        from ._inspection import require_sync
        require_sync(client)
        self._configure(client, scope, max_items, max_response_bytes)

    def _configure(self, client, scope, max_items, max_response_bytes):
        self.client, self.scope = client, scope
        self.max_items = bounds(max_items, 1000, "Semantic item budget")
        self.max_response_bytes = bounds(max_response_bytes, 16 * 1024 * 1024, "Response byte budget")
        self._owner = object()

    def _request(self, method, route, **kwargs):
        return self.client.transport.request("reasoner", method, route, scope=scope_token(self.scope),
            service_id=self.client.runtime_service_id, max_response_bytes=self.max_response_bytes, **kwargs)

    def _identity(self, payload, key):
        value = required_text(payload, key)
        # These identities may be reused in requests: never silently rewrite them.
        if self.client.transport.redact(value) != value:
            raise ProtocolError("Semantic identity contains configured credential material")
        return value

    def _state(self):
        payload = object_payload(self._request("GET", "/public/capabilities"), "Reasoner capabilities")
        return self._knowledge_state(payload)

    def _knowledge_state(self, payload):
        payload = object_payload(payload, "Reasoner capabilities")
        check_notifications(payload, self.client.transport.redact)
        if payload.get("consistent") is not True:
            raise ProtocolError("Reasoner knowledge consistency is false or unknown")
        revision = payload.get("knowledgeRevision")
        if revision is not None and (type(revision) is not int or revision < 0):
            raise ProtocolError("Invalid knowledge revision")
        return KnowledgeState(
            service_url=_normalize_service_url(self.client.transport.endpoint("reasoner").url),
            service_id=self._identity(payload, "serviceId"),
            scope=scope_token(self.scope),
            worldview_id=self._identity(payload, "worldviewId"),
            revision=revision if revision else None,
        )

    def _unchanged(self, expected):
        if self._state() != expected:
            raise StaleKnowledgeError("Reasoner service, scope, worldview or knowledge revision changed; search/resolve afresh")

    def _semantic(self, payload, kind, state):
        payload = object_payload(payload, kind)
        check_notifications(payload, self.client.transport.redact)
        if kind == "concept":
            concept_from_wire(payload)
        else:
            observable_from_wire(payload)
        semantics = payload if kind == "concept" else object_payload(payload.get("semantics"), "semantics")
        check_notifications(semantics, self.client.transport.redact)
        if semantics.get("urn") == "owl:Nothing" or "NOTHING" in semantics.get("type", ()) or payload.get("artifactType") == "VOID":
            raise MissingAssetError("Semantic definition did not resolve in the configured worldview")
        if any(name not in SemanticType.__members__ for name in semantics.get("type", ())):
            raise UnsupportedOperationError("Unknown semantic classification; inspect the service contract before interpreting it")
        raw = freeze(payload, self.client.transport.redact)
        label = _optional_text(semantics, "displayLabel")
        return SemanticResult(
            kind=kind,
            urn=self._identity(payload, "urn"),
            label=self.client.transport.redact(label) if label else label,
            knowledge=state,
            raw=raw,
        )

    def _resolve(self, definition, kind, state):
        return self._semantic(self._request("POST", "/api/v1/resolve/" + kind, text=text(definition, "Definition")), kind, state)

    def resolve_concept(self, definition):
        state = self._state()
        result = self._resolve(definition, "concept", state)
        self._unchanged(state)
        return result

    def resolve_observable(self, definition):
        state = self._state()
        result = self._resolve(definition, "observable", state)
        self._unchanged(state)
        return result

    def search_authority(self, authority, query, *, filter=None, offset=0, limit=20):
        self._search_spec(authority, query, filter, offset, limit)
        state = self._state()
        payload = self._request("POST", "/api/v1/authority/search",
            json={"authority": authority, "query": query, "filter": filter, "offset": offset, "limit": limit})
        result = self._search_result(payload, authority, offset, limit, state)
        self._unchanged(state)
        return result

    def _search_spec(self, authority, query, filter, offset, limit):
        _authority(authority)
        text(query, "Authority query", 256)
        if filter is not None:
            text(filter, "Search filter", 128)
        if type(offset) is not int or not 0 <= offset <= 10000:
            raise InvalidRequestError("Authority offset must be 0..10000")
        bounds(limit, min(100, self.max_items), "Search limit")
    def _search_result(self, payload, authority, offset, limit, state):
        payload = object_payload(payload, "authority search")
        status = payload.get("status")
        if status not in ("OK", "UNSUPPORTED", "UNAVAILABLE", "FAILED"):
            raise ProtocolError("Unknown authority search status")
        rows, total, following = payload.get("matches"), payload.get("total"), payload.get("nextOffset")
        if (not isinstance(rows, list) or len(rows) > limit or type(total) is not int or total < 0
                or type(following) is not int or following != -1 and not offset < following <= 10000
                or total < offset + len(rows) and rows
                or status != "OK" and (rows or total != 0 or following != -1)):
            raise ProtocolError("Malformed authority search page")
        if status == "OK":
            check_notifications(payload, self.client.transport.redact)
        # Parse notifications on non-OK responses without turning them into an empty success.
        from .dto import notifications
        notifications(payload)
        matches, seen = [], set()
        for row in rows:
            row = object_payload(row, "authority candidate")
            code, name = self._identity(row, "id"), self._identity(row, "authorityName")
            if name != authority or code in seen or row.get("locator") != _locator(authority, code):
                raise ProtocolError("Authority candidate has a duplicate or mismatching canonical identity")
            seen.add(code)
            score = row.get("score")
            if score is not None:
                try:
                    finite_score = type(score) in (int, float) and math.isfinite(score)
                except OverflowError:
                    finite_score = False
                if not finite_score:
                    raise ProtocolError("Authority score must be finite or null")
            raw = freeze(row, self.client.transport.redact)
            matches.append(AuthorityCandidate(
                authority=name,
                code=code,
                locator=row["locator"],
                label=_optional_text(raw, "label"),
                description=_optional_text(raw, "description"),
                score=score,
                knowledge=state,
                raw=raw,
                _owner=self._owner,
            ))
        return AuthoritySearch(
            status=status,
            matches=tuple(matches),
            total=total,
            next_offset=None if following == -1 else following,
            knowledge=state,
            raw=freeze(payload, self.client.transport.redact),
        )

    def _candidate(self, candidate):
        self._owned_candidate(candidate)
        self._unchanged(candidate.knowledge)
        self._candidate_notifications(candidate)

    def _owned_candidate(self, candidate):
        if not isinstance(candidate, AuthorityCandidate) or candidate._owner is not self._owner:
            raise InvalidRequestError("Select a candidate returned by this explorer")

    def _candidate_notifications(self, candidate):
        check_notifications({"notifications": [dict(n) for n in candidate.raw.get("notifications", ())]}, self.client.transport.redact)

    def documentation(self, candidate):
        """Inspect MIME-to-URL links only. No redirect, credential forwarding or HTML execution."""
        self._candidate(candidate)
        payload = object_payload(self._request("GET", "/api/v1/authority/documentation",
            params={"authority": candidate.authority, "identity": candidate.code}), "documentation")
        result = self._documentation(payload)
        self._unchanged(candidate.knowledge)
        return result

    def _documentation(self, payload):
        payload = object_payload(payload, "documentation")
        for media_type, url in payload.items():
            if not isinstance(media_type, str) or "/" not in media_type or not isinstance(url, str):
                raise ProtocolError("Invalid documentation descriptor")
            parsed = urlsplit(url)
            if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
                raise ProtocolError("Documentation links must be HTTP(S) URLs without credentials")
        return freeze(payload, self.client.transport.redact)

    def resolve_candidate(self, candidate):
        self._candidate(candidate)
        result = self._resolve(candidate.locator, "concept", candidate.knowledge)
        self._unchanged(candidate.knowledge)
        return result

    def _concept_wire(self, definition, state):
        # Fresh resolution: never send a saved view as current authoritative semantics.
        result = self._request("POST", "/api/v1/resolve/concept", text=text(definition, "Concept definition"))
        self._semantic(result, "concept", state)
        return result

    def _neighbors(self, definition, direction):
        state = self._state()
        concept = self._concept_wire(definition, state)
        payload = self._request("POST", "/api/v1/" + direction, json=concept)
        if not isinstance(payload, list) or len(payload) > self.max_items:
            raise ProtocolError("Hierarchy response exceeds the item budget or is not an array")
        result = tuple(self._semantic(row, "concept", state) for row in payload)
        self._unchanged(state)
        return result

    def parents(self, definition):
        return self._neighbors(definition, "parents")

    def children(self, definition):
        return self._neighbors(definition, "children")

    def _check(self, route, definitions):
        state = self._state()
        concepts = [self._concept_wire(definition, state) for definition in definitions]
        result = self._request("POST", "/api/v1/" + route, json=concepts if len(concepts) == 2 else concepts[0])
        if type(result) is not bool:
            raise ProtocolError("Semantic predicate must return a boolean")
        self._unchanged(state)
        return result

    def is_a(self, concept, other):
        """Whether concept specializes other (concept ⊑ other); argument order matters."""
        return self._check("subsumes", (concept, other))

    def satisfiable(self, definition):
        return self._check("satisfiable", (definition,))


class AsyncSemanticExplorer(SemanticExplorer):
    """Native awaited remote semantics; no local inference or authority auto-selection."""

    def __init__(self, client, scope=None, *, max_items=100, max_response_bytes=1024 * 1024):
        from .async_client import AsyncClient
        if not isinstance(client, AsyncClient):
            raise InvalidRequestError("Async semantic exploration requires a native AsyncClient")
        self._configure(client, scope, max_items, max_response_bytes)

    async def _request(self, method, route, **kwargs):
        return await self.client.transport.request("reasoner", method, route, scope=scope_token(self.scope),
            service_id=self.client.runtime_service_id, max_response_bytes=self.max_response_bytes, **kwargs)

    async def _state(self):
        return self._knowledge_state(await self._request("GET", "/public/capabilities"))

    async def _unchanged(self, expected):
        if await self._state() != expected:
            raise StaleKnowledgeError("Reasoner service, scope, worldview or knowledge revision changed; search/resolve afresh")

    async def _resolve(self, definition, kind, state):
        payload = await self._request("POST", "/api/v1/resolve/" + kind, text=text(definition, "Definition"))
        return self._semantic(payload, kind, state)

    async def resolve_concept(self, definition):
        state = await self._state()
        result = await self._resolve(definition, "concept", state)
        await self._unchanged(state)
        return result

    async def resolve_observable(self, definition):
        state = await self._state()
        result = await self._resolve(definition, "observable", state)
        await self._unchanged(state)
        return result

    async def search_authority(self, authority, query, *, filter=None, offset=0, limit=20):
        self._search_spec(authority, query, filter, offset, limit)
        state = await self._state()
        payload = await self._request("POST", "/api/v1/authority/search",
            json={"authority": authority, "query": query, "filter": filter, "offset": offset, "limit": limit})
        result = self._search_result(payload, authority, offset, limit, state)
        await self._unchanged(state)
        return result

    async def _candidate(self, candidate):
        self._owned_candidate(candidate)
        await self._unchanged(candidate.knowledge)
        self._candidate_notifications(candidate)

    async def documentation(self, candidate):
        await self._candidate(candidate)
        payload = await self._request("GET", "/api/v1/authority/documentation",
            params={"authority": candidate.authority, "identity": candidate.code})
        result = self._documentation(payload)
        await self._unchanged(candidate.knowledge)
        return result

    async def resolve_candidate(self, candidate):
        await self._candidate(candidate)
        result = await self._resolve(candidate.locator, "concept", candidate.knowledge)
        await self._unchanged(candidate.knowledge)
        return result

    async def _concept_wire(self, definition, state):
        result = await self._request("POST", "/api/v1/resolve/concept", text=text(definition, "Concept definition"))
        self._semantic(result, "concept", state)
        return result

    async def _neighbors(self, definition, direction):
        state = await self._state()
        concept = await self._concept_wire(definition, state)
        payload = await self._request("POST", "/api/v1/" + direction, json=concept)
        if not isinstance(payload, list) or len(payload) > self.max_items:
            raise ProtocolError("Hierarchy response exceeds the item budget or is not an array")
        result = tuple(self._semantic(row, "concept", state) for row in payload)
        await self._unchanged(state)
        return result

    async def parents(self, definition):
        return await self._neighbors(definition, "parents")

    async def children(self, definition):
        return await self._neighbors(definition, "children")

    async def _check(self, route, definitions):
        state = await self._state()
        concepts = [await self._concept_wire(definition, state) for definition in definitions]
        result = await self._request("POST", "/api/v1/" + route, json=concepts if len(concepts) == 2 else concepts[0])
        if type(result) is not bool:
            raise ProtocolError("Semantic predicate must return a boolean")
        await self._unchanged(state)
        return result

    async def is_a(self, concept, other):
        """Whether concept specializes other (concept ⊑ other); argument order matters."""
        return await self._check("subsumes", (concept, other))

    async def satisfiable(self, definition):
        return await self._check("satisfiable", (definition,))
