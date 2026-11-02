# Typed recorded graph browsing

`GraphBrowser` provides explicit bounded read-only pages of links incident to a
known observation, activity, actuator or cohort in one configured context. It
reuses existing `ProvenanceNode`/`ProvenanceLink` decoding and the Runtime
`/api/v1/query` contract. No Cypher/GraphQL text, recursive crawl, graph mutation or
scientific calculation is introduced.

```python
from klab_client import GraphBrowser

browser = GraphBrowser(context, max_links=16)  # The Context used to attach/compute observation.
page = browser.neighbors(observation, direction="incoming", relationships=["MEASURED"])
for link in page.links:
    print(link.source.kind, link.source.activity_type, link.relationship)
    activity_page = browser.neighbors(link.source, direction="outgoing", relationships=["MEASURED"])
```

Each `neighbors` call performs two requests: fresh membership lookup and one
single-hop link query. To continue, explicitly pass `page.next_offset` with the
same anchor/filters. None means no extra row was observed **on that request**, not
that lineage is complete. No automatic retry, paging or recursive traversal occurs.

Supported filters include the recorded-effect relationships in the existing
provenance API and HAS_CHILD, HAS_MEMBER, HAS_ACTIVITY and TRIGGERED. Unsupported
node classes fail explicitly; this is not all RuntimeAsset classes. A direction
is incoming, outgoing or both. Page defaults/maxima: 64/128 links, 1/16 MiB response
bytes. Each query requests one extra row for the continuation hint. Budgets apply
per response/page, not an unbounded browsing session; callers control total calls.

## Contracts and limitations

- Runtime query compiler supports exactly one hop for LINK results. Endpoint and
  relationship constraints are typed DTOs, never user-provided query fragments.
- The compiler adds deterministic `elementId(n)` ordering. **No shared revision,
  cursor or snapshot lease exists**: insertions/deletions can skip/repeat links
  across offset pages. Every GraphPage is permanently marked partial.
- Node IDs/URNs and cached descriptors are not authorization. The current server
  scope rechecks membership; absent/inaccessible anchors fail rather than return
  an empty success. Browser context identity is compared before/after each query.
- Descriptor refresh may change activity status. A SUCCESS activity is its recorded
  outcome, not proof of published root-transaction success or scientific validity.
- Planned RESOLVED/CONTEXTUALIZED_BY edges remain planned evidence, not execution.
  Empty pages are not proof of absent provenance.
- This API accepts synchronous Client/Context only. Passing AsyncClient scopes
  fails before I/O; no hidden event loop or worker-thread emulation is used.
- Returned objects deeply freeze/redact raw properties through existing provenance
  decoders. Data/context identities may still be private; inspect before publishing.

Source authority: `KnowledgeGraphQuery` typed wire DTO and
`Neo4jQueryCompiler` LINK/visibility/pagination rules, plus GraphModel relationship
enums. Tests of DTOs and mocked responses establish client contracts, not deployed
server compatibility. Any genuine Java acceptance must be reported separately with
its pinned build, owned lifecycle and outcome. No graph page is PaL reproduction.

The controlled Java run now passes observation → MEASURE activity → observation
navigation with fresh membership checks, both scope releases and owned JVM cleanup.
See [sanitized acceptance facts](results/graph-controlled-20261102.json) and the
[author review](reviews/graph-browsing.md). Earlier failed attempts are preserved.
This is not live acceptance of every supported node/relationship or a claim of
complete lineage, root-commit proof, privacy testing or snapshot pagination.
