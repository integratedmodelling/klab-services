# Partial graph page projection

Native awaited refresh:

```python
from klab_client import AsyncGraphBrowser, AsyncGraphProjection

projection = AsyncGraphProjection(AsyncGraphBrowser(async_context), max_pages=8)
entry = await projection.load(observation)
projection.invalidate("NOTIFICATION_HINT")  # local operation, no await/I/O
refreshed = await projection.load(observation)  # exactly one explicit refetch
```

`view`, `invalidate` and `clear` remain synchronous pure local operations. Local
locks protect retained data only and are never held across network awaits. Async
refresh shares the same bounded retention/eviction and generation checks: a slower
pre-invalidation read cannot replace a newer refreshed page. Failure/cancellation
invalidates every retained page; a subsequent explicit load refreshes only its own
page, not global state. Retained data is never proof of current authorization,
committed revision, durable event replay or snapshot lineage. Mixing a synchronous
GraphProjection with AsyncGraphBrowser is rejected before producing a coroutine.

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

### Reproducible owned Runtime disconnect

The development runner supports `--scenario partial-refresh` with the same explicit
compiled checkout, Java21 and component pins as nonuniform-export acceptance:

```console
python tools/run_reliability_acceptance.py --server-repo <compiled-checkout> --java <jdk21-java.exe> --components <pins.json> --state-dir <fresh-private-state> --scenario partial-refresh
```

The functional child retains two actual Java graph query selections. Only its
parent may stop/restart the captured owned Runtime process; control files live
in the fresh private state and are not product endpoints. Restarted processes are
added to the existing ownership journal/final cleanup. There is no scientific
resubmission, old-Job replay, event replay cursor or automatic full refresh.
Failed reads must retain prior page identity, timestamps and query metadata but
invalidate all pages. An explicit one-page refetch must leave the other page
invalidated; even refetched content remains partial with UNKNOWN consistency.
Owner read/reattach and separate signed-identity private-context attach/release
denials are tested at the actual Java service, never inferred from a local role.

Transport failure, HTTP authentication/authorization, missing state and service
errors stay distinct exceptions. An HTTP404 alone does not distinguish expired
scope from deleted/missing state; no expiry claim is inferred. Controlled contract
tests cover these status distinctions while the owned stack supplies genuine
disconnect/reconnect evidence. Revisioned two-client convergence and durable
restart/replay acceptance remain outside this recipe (#126).

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
