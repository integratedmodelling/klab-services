# Source-bound finite dependency graph reference

`DependencyGraphs` / `AsyncDependencyGraphs` is a **separate proposed reference**,
not a reinterpretation of native `ResourceSet` groups as a graph. Construction
requires an explicit ordinary Runtime root and `allow_experimental=True`. Before
each graph page, the selected Resources service must advertise
`dependencyGraphProtocol="klab.experimental.dependency-graphs/1"`. No advertisement
or a failed service means refusal, not simulation fallback or legacy native routing.

```python
graphs = DependencyGraphs(context, allow_experimental=True,
                          max_nodes=64, max_edges=256, max_pages=16)
snapshot = graphs.inspect("synthetic.model", knowledge_class="MODEL", page_size=1)
```

Await async `.inspect` directly. The returned immutable snapshot is emitted only
after **all pages** have passed the same root/Resources/request/snapshot/revision/
root-node/total/provider checks. A source-generation change between pages rejects
with409; the facade does not retry behind the caller's back or return a partial
graph. Explicitly call `.inspect` again to choose a new current snapshot. Old
receipts remain historical reports, not a currentness lease.

## Source, identity and access evidence

Each node carries an opaque node ID, URN/class/provider, an exact three-component
nonnegative signed-Java-int version string (no `latest`, wildcard or range), explicit
source revision, bounded nonblank UTF-8 source and SHA256 of those exact bytes.
The reference's access stamp binds a public subject, root, READ claim and graph
revision. It is **a server report, not permission authority or cryptographic proof**.
No token or credential is stored in that stamp. Current credentials and server-side
root ownership, not a Python DTO or `context.owned`, determine actual access.

The client validates unique IDs and provider/class/URN identities, exact totals,
consistent access subjects/providers, existing typed edge endpoints, root identity,
all-node reachability and absence of cycles. Shared dependencies in an acyclic DAG
are allowed. Duplicate pages/nodes/edges, missing sources/providers/access, invented
edge kinds, orphan nodes or contradictory totals refuse the entire snapshot.
It never infers edges from array order or performs semantic/model selection.

Provider URLs are inert HTTP(S) metadata without embedded credentials, query strings
or fragments. They are never followed with the caller's credentials. Endpoint,
credential, transport, client and Runtime-root binding checks bracket page reads;
credential reflection in route/source/receipts refuses. Inspecting source does not
authorize loading it, publishing it, compiling it or executing a scientific model.

## Finite completeness, not native completeness

`DependencyGraphSnapshot.evidence` says `REFERENCE_DECLARED_COMPLETE`, explicitly
**not native closure/scientific/access authority**. The independent fixture declares
one literal model -> namespace/input -> base-namespace/base-input graph, plus a
resource-root view of the same transitive input -> base-input dependency. Sources are finite
literal receipts, not a parser/compiler or independent scientific engine. Test-only
source-generation advance, cycle and missing-endpoint modes prove refusal and fresh
explicit inspection. These controls do not mutate native Java knowledge.

Native `DependencyInspector` remains the source-defined membership view with
closureUNKNOWN/edgesUNAVAILABLE. The inspected native code has unfinished dependency
categories/remote paths and transmits no edges/source/access snapshot witness. This
reference does not change that fact or clear JAVA-GAP-0013. No fake native functional
adapter is supplied for an unestablished experimental graph contract: maintainer
confirmation and an approved disposable compatibility fixture are still required.

## Budgets and cleanup

- Client defaults64nodes/256edges/16pages, configurable within1..256nodes,
  1..1024edges,1..64pages; page size1..32. At most1MiB/response and16KiB/source.
  Total budgets apply across pages, not just per-response. Budget exhaustion never
  emits an incomplete snapshot or invents server pagination.
- Snapshot/cursor values are opaque32hex strings, never continuation URLs. Repeated
  cursors refuse. The finite service binds each cursor to root/snapshot/page size;
  stale generation409, unknown snapshot410 and invalid cursor422 remain distinct.
- Reference storage is ephemeral only: at most8snapshots/root,128globally and
  64cursors/snapshot. Capacity429 refuses without silently evicting active receipts.
  Releasing the owned root removes its reference snapshots; unrelated roots survive.
  Durable-storage mode refuses rather than claiming unreviewed restart authority.

Public sync/nativeasync HTTP acceptance covers exact source/version/access graph,
multiple pages, changed source generation/no mixed result, explicit fresh inspection,
current-principal denial, cycle/gap/budget refusal and owned cleanup. Supplementary
fixture cases remain separate from that public acceptance. Native compatibility,
real dependency completeness, scientific lineage and release approval remain distinct.
