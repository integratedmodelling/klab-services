"""Bounded, explicitly partial graph pages; invalidation is not durable resync."""
from collections import OrderedDict
from dataclasses import dataclass
import json
from threading import RLock

from .evidence import _plain
from .errors import InvalidRequestError, ProtocolError
from .graph import GRAPH_RELATIONSHIPS, GraphBrowser, GraphPage
from .handles import to_handle


@dataclass(frozen=True)
class ProjectedGraphPage:
    page: GraphPage
    generation: int
    invalidated: bool
    retained_json_bytes: int


@dataclass(frozen=True)
class GraphProjectionView:
    pages: tuple[ProjectedGraphPage, ...]
    generation: int
    invalidation_reason: str | None
    retained_json_bytes: int
    partial: bool = True
    consistency: str = "UNKNOWN: no revision lease; uninvalidated does not mean current"
    current_authorization: str = "NOT_RECHECKED: local retained data, not permission"


def _page_bytes(page):
    # Budget the serialized retained semantic payload, not Python allocator/RSS.
    data = {"anchor": _plain(page.anchor.raw), "links": [
        {"source": _plain(link.source.raw), "target": _plain(link.target.raw),
         "relationship": link.relationship, "sequence": link.sequence,
         "properties": _plain(link.properties)} for link in page.links]}
    try:
        return len(json.dumps(data, ensure_ascii=False, allow_nan=False,
                              separators=(",", ":")).encode("utf-8"))
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise ProtocolError("Graph page cannot be retained as bounded JSON data") from None


class GraphProjection:
    """Explicit read-through refresh, never cache-hit authorization or graph replay.

    Every load uses GraphBrowser's membership and link requests, never an implicit
    cache hit. Stored pages are immutable partial evidence. Replacements are keyed
    by anchor/kind/URN, direction, relationship selection and offset. FIFO eviction
    bounds retention; pages are never merged into a supposed coherent snapshot.
    """
    def __init__(self, browser, *, max_pages=16, max_retained_json_bytes=4 * 1024 * 1024):
        if not isinstance(browser, GraphBrowser):
            raise InvalidRequestError("GraphProjection requires an explicit GraphBrowser")
        if type(max_pages) is not int or not 1 <= max_pages <= 32:
            raise InvalidRequestError("Graph projection retains 1..32 pages")
        if (type(max_retained_json_bytes) is not int
                or not 1 <= max_retained_json_bytes <= 16 * 1024 * 1024):
            raise InvalidRequestError("Graph retained JSON budget requires 1..16777216 bytes")
        self._browser, self._reference = browser, browser.reference
        self._max_pages, self._max_bytes = max_pages, max_retained_json_bytes
        self._pages = OrderedDict()
        self._generation = self._bytes = 0
        self._reason = None
        self._lock = RLock()
        self._binding()

    def _binding(self):
        self._browser.client._validate_context_binding(self._browser.context)
        if self._browser.reference != self._reference or to_handle(self._browser.context) != self._reference:
            raise ProtocolError("Graph projection context changed; cached data is not remote authority")

    @property
    def view(self):
        """A local snapshot only; does not revalidate current server-side ACLs."""
        with self._lock:
            self._binding()
            return GraphProjectionView(tuple(ProjectedGraphPage(page, generation,
                generation != self._generation, size)
                for page, generation, size in self._pages.values()),
                self._generation, self._reason, self._bytes)

    def invalidate(self, reason="EXPLICIT_REFRESH"):
        """Mark all retained pages without I/O. No event ordering/commit inference."""
        if not isinstance(reason, str) or reason not in (
                "EXPLICIT_REFRESH", "NOTIFICATION_HINT", "TRANSPORT_LOST", "READ_FAILED"):
            raise InvalidRequestError("Use a supported nonsecret graph invalidation reason")
        with self._lock:
            self._generation += 1
            self._reason = reason

    def load(self, anchor, *, direction="both", relationships=GRAPH_RELATIONSHIPS, offset=0):
        """Explicitly refetch exactly one page; never auto-page or return stale data.

        Read failure/cancellation invalidates retained evidence and propagates the
        error. Invalidation during an in-flight read marks its returned page too.
        Other pages remain invalidated until explicitly refetched or evicted.
        """
        try:
            with self._lock:
                self._binding()
                generation = self._generation
            page = self._browser.neighbors(anchor, direction=direction,
                                            relationships=relationships, offset=offset)
            size = _page_bytes(page)
            if size > self._max_bytes:
                raise ProtocolError("Graph page exceeds retained JSON budget; not cached")
            key = (page.anchor.kind, page.anchor.id, page.anchor.urn,
                   page.direction, page.relationships, page.offset)
            with self._lock:
                self._binding()
                newer = self._pages.get(key)
                if newer is not None and newer[1] > generation:
                    # A slower pre-invalidation read cannot replace a refreshed page.
                    return ProjectedGraphPage(page, generation, True, size)
                old = self._pages.pop(key, None)
                if old is not None:
                    self._bytes -= old[2]
                while self._pages and (len(self._pages) >= self._max_pages
                                       or self._bytes + size > self._max_bytes):
                    _, removed = self._pages.popitem(last=False)
                    self._bytes -= removed[2]
                self._pages[key] = (page, generation, size)
                self._bytes += size
                return ProjectedGraphPage(page, generation, generation != self._generation, size)
        except BaseException:
            self.invalidate("READ_FAILED")
            raise

    def clear(self):
        """Forget only this object's retained pages, never release remote resources."""
        with self._lock:
            self._pages.clear()
            self._bytes = 0
            self.invalidate()
