# Explicit annotated-input provenance join

`InputProvenance` / `AsyncInputProvenance` joins **server/reference claims**, not
native scientific lineage or cryptographic/permission authority. It never annotates
a resource implicitly. The semantic binding is the caller's explicit namespace
source admitted through existing ADD-only `Authoring`, not an invented metadata
UPDATE or generic native annotation endpoint.

```python
joined = InputProvenance(context, allow_experimental=True).inspect(
    upload_submission, namespace_create_receipt, exact_namespace_source,
    resolver_plan_job, observation, model_urn=selected_model_urn)
```

Await async `.inspect` directly. Both forms require explicit root and experimental
consent, read completed Resources import and Resolver plan jobs, inspect the advertised
source-bound MODEL graph, then **query current observation membership/input metadata**.
Cached observation metadata alone does not satisfy the join. No automatic upload,
source mutation, compilation, observation creation or retry occurs during inspection.

## Checked associations

- Upload and plan jobs use the exact current client/root/service and ordinary raw
  import mapping and Resolver dataflow result kinds. Failed, interrupted, expired or transport-uncertain jobs propagate
  their errors; they never become admission success or trigger an upload replay.
- Explicit namespace CREATE acknowledgment (from ADD), exact source, and upload
  receipt name the same selected Resources origin/provider. The graph must bind
  the requested selected MODEL to both that NAMESPACE and the exact RESOURCE with
  explicit direct dependency edges.
- The finite reference plan must be RESOLVED, nonempty, and name one OBSERVE actuator
  selecting exactly that model (no children). Model/resource requirements must pin
  the graph's exact provider/version/source hash. Candidate membership alone does
  not prove selection. Other computation shapes refuse; no engine is implemented.
- The namespace graph node retains the exact UTF-8 source and hash. The resource
  node's source is a JSON manifest with exactly protocol
  `klab.experimental.import-source/1`, `resourceUrn`, `schemaId`, `binarySha256` and
  positive bounded `binaryBytes`, matching the consumed upload bytes/receipt.
- Current observation input must agree on model/resource, binary hash/bytes,
  manifest hash, annotation hash and concrete model/resource versions. Missing,
  mismatched, inaccessible or foreign-root evidence refuses the whole join.
- Existing graph validation enforces complete finite source/access/provider evidence,
  exact snapshots, typed edges, reachability, acyclicity and bounded paging. Current
  credentials and root access remain service decisions, not receipt authority.

**Three different hashes:** `binary_sha256` hashes uploaded GeoTIFF bytes;
`manifest_sha256` hashes the resource's exact manifest source text; and
`annotation_sha256` hashes the explicit namespace source. They are never compared
as if interchangeable. An uploaded binary hash is not the graph manifest hash.

The optional `timeout` bounds each job wait separately to(0,60]seconds (default5);
`None`/infinite/nonpositive waits refuse. Graph paging and query transport retain
their own bounded budgets. This is not an atomic cross-service transaction deadline.

The immutable `InputProvenanceReceipt` is an association of historical and fresh
claims at inspection time. It is not an atomic distributed snapshot or a guarantee
that a source, grant or selection cannot later change. It does not fabricate native
version mappings, resource UPDATE, complete historical provenance or scientific
execution from a successful parser/import/plan response.

## Independent acceptance and cleanup boundary

The separate `annotated-input` scenario admits only the pinned crop and exact literal
namespace/model binding already used by the import workflow. Graph nodes derive
from current owned import/source state; selected plan and recorded observation carry
the corresponding manifests/versions/hashes. Data remains the recorded400-cell
authentic crop, **not newly executed native science** or a local scientific engine.
Existing `import-workflow` behavior is preserved separately.

Release owns Runtime/ephemeral reference state only. Source ADD assets persist;
tests separately use authenticated fixture-only reset for those assets, never claim
a production SDK native RESOURCE deletion. Native cleanup remains JAVA-GAP-0002.
The source/plan/input/graph claims are provisional (JAVA-GAP-0013); native safe semantic
annotation, source/selection lineage and scientific acceptance require maintained
contracts and approved disposable-fixture evidence. No IDE/worldview repair occurs.
