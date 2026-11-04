# Partial graph page projection

`GraphProjection(GraphBrowser(context))` retains bounded typed pages from the
existing graph-query interface. Every `load(anchor, ...)` explicitly refetches one
page through `GraphBrowser`: membership and links are checked with current HTTP
credentials. It never serves a silent cache hit, recursively crawls or follows the
next offset automatically. It introduces no endpoint, query language or mutation.

```python
from klab_client import GraphBrowser, GraphProjection

projection = GraphProjection(GraphBrowser(context), max_pages=8)
entry = projection.load(observation)
projection.invalidate("NOTIFICATION_HINT")  # No I/O or inference that a commit exists.
assert projection.view.pages[0].invalidated
refreshed = projection.load(observation)  # Explicitly refetch just this page.
```

## Guarantees and non-guarantees

- Immutable pages retain their original identity, query selection, offset and
  fetched timestamp. `view` is an immutable point-in-time local snapshot, not a
  live object and not a current server authorization check.
- Retention is bounded by page count (default16, maximum32) and serialized retained
  semantic JSON bytes (default4MiB, maximum16MiB). The byte budget counts anchor,
  link endpoint descriptors and properties, **not** Python allocator/RSS overhead
  or the entire HTTP response. Configure the browser's response budget separately.
  Size is checked after bounded browser decoding. A single oversized page fails
  explicitly rather than becoming a successful partial cache insertion.
- Replacement is keyed by anchor kind/ID/URN, direction, relationship selection
  and offset. FIFO eviction bounds count/bytes; evicted data is not deleted remotely.
- Invalidation marks all pages. Explicit refresh replaces only the selected page;
  other pages remain invalidated. A slow pre-invalidation response cannot replace
  a newer post-invalidation refresh of the same key. A response fetched during
  invalidation is still marked invalidated when returned.
- Read failures/cancellation propagate and invalidate retained evidence. A denial
  never falls back to a retained page. Context/reference drift fails closed.
- `clear()` forgets this object's pages and invalidates its local generation; it
  does not dispose a context, twin, job or session. Pages previously held by the
  caller remain unchanged.

**All views are partial and consistency remains UNKNOWN.** A page whose local
invalidation marker is false is not proven current. There is no server revision
lease, transactionally coherent multi-page snapshot, continuous subscription,
ordered commit application, deduplication across graph queries or durable cursor.
Local generation is only an invalidation epoch, never a server commit/revision.
Cached bytes are previously retrieved local evidence, not authority to access the
remote twin. Reconnect requires explicit invalidation/refetch, not mutation replay.
Do not use this slice to close durable-event/recovery issue #126 or full phase3.

## Safe reproduction

From this PR checkout with Python3.11+:

```sh
python -m pip install -e './python-client[dev]'
python -m pytest -q python-client/tests/unit/test_graph_projection.py python-client/tests/unit/test_graph.py
```

Tests exercise the existing HTTP wire contract using controlled HTTPX transport
doubles, including positive reads and explicit403 denial, not an executing Java
Runtime. No real credential, persistent service, scientific Job or remote mutation
is used. They check identity drift, byte/count budgets, replacement, eviction,
invalidation races, malformed retention, denial and cancellation. Genuine served
graph/restart/permissions acceptance at an exact source/dependency closure remains
separate work; this is useful client implementation, not a Java acceptance waiver.
