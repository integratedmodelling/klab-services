# Dependency membership inspection

`DependencyInspector` and `AsyncDependencyInspector` expose the **native-shaped
ResourceSet membership groups** returned by the selected Resources service:

```python
snapshot = DependencyInspector(client, context, max_items=100).inspect(
    "example.namespace", knowledge_class="NAMESPACE")
for member in snapshot.groups["namespaces"] or ():
    print(member.urn, member.service_id, member.version)
```

Await the async inspector's `.inspect` directly. This calls existing Resources
capabilities and one `resolve` request, then checks current service/credential/scope
binding. It never follows returned URLs, loads knowledge, selects a model, compiles
source or creates observations. No new native endpoint or experimental graph
protocol is invented. Existing `ResourceInspector.resolve` remains its original
partial result view.

## What the receipt means

- `results` identifies the exact requested root; one explicit root per request.
- `groups` preserves each supplied array's order for namespaces, ontologies,
  behaviors, resources, observation strategies and projects. A missing/null group
  is **unknown (`None`)**, not an empty dependency set.
- Each immutable member retains the reported URN/class/provider, version,
  timestamp, optional flag, project identity and credential-filtered metadata.
  Missing version/timestamp/optional fields remain unknown rather than granting
  default permission or fabricated freshness. A native timestamp0 is a reported
  value, not proof that source has not changed.
- Versions are server claims, not source hashes or a distributed snapshot lease.
  Duplicate identities within one group and conflicting known versions across
  groups refuse. Root/group auxiliary metadata and timestamps can legitimately
  differ in the inspected native implementation, so both are retained.
- `services` contains inert validated HTTP(S) URLs without embedded credentials,
  query strings or fragments. A provider ID need not have a URL. Neither a URL nor
  an `accessKey` authorizes routing with the caller's credentials. `accessKey` and
  credential-named metadata are not exported as public permission evidence.
- Budgets count **all entries across all arrays including root duplicates**, not
  just unique nodes. Oversize refuses rather than truncating or inventing pages.

## Important native limit

The API documentation says `resolve` supplies full dependency closure. The inspected
implementation does not establish that guarantee for every category: namespace
imports are followed, while other category dependencies and remote namespaces have
unfinished paths. The wire DTO carries ordered members, **not dependency edges**.
Accordingly every snapshot reports `closure_completeness="UNKNOWN..."` and
`edge_completeness="UNAVAILABLE..."`. It does not fabricate a graph from array order
or silently turn an absence into a leaf. Root membership is not evidence of Resolver
selection, executable completeness, publication or scientific lineage.

The independent `dependency-inspection` fixture supplies a declared native-shaped
grouped response and a test-only version advance.
The fixture refuses durable-storage mode rather than resetting version authority
silently across restart. It creates no dependency resources or knowledge state.
Public sync/async tests verify membership, changed version snapshots,
credential/owner denial and cleanup. It is
not a native complete-closure or edge-graph acceptance substitute. Native tests remain
NOT_RUN; maintained missingness/edges/snapshot/access contracts are still needed
for deeper guarantees (JAVA-GAP-0013). No live IDE or worldview repair was performed.

Prepared opt-in `tests/functional/test_dependencies.py` requires an approved
`dependency-inspection.json` in the functional fixture-state directory, with
`allow_read_only_dependency_inspection=true`, `native_build`, Resources service ID,
root URN/class and optional current scope, plus expected result/group tuples
`[provider, knowledge_class, urn, version]`. It performs only capability/resolve reads;
it does not synthesize source hashes or validate native closure completeness.

This native-shaped membership slice does **not** finish the original roadmap's
deeper source-bound graph/pagination/cycle/access requirements. Those remain explicit
Python/reference work; honest missingness here is not an excuse to mark them done.
