# Collective query completion

This increment is a foundation for recursive resolution, not an executable contextual
route. It repairs the collective catalog-query boundary using existing execution
Activities and transaction state. Containment still legalizes a request independently
of this query: Tier-0 precomputed fulfillment does not need member discovery.

## Focus at the concrete-selection submission boundary

The bounded positive concrete-predicate Subject/Agent selection route preserves an explicit native
focus before planning, so its receipt and replay remain bound to that context. Missing support or
invalid focus fails before effects; clearing the focus is not a fallback. Ordinary independent
substantial reset and nested ownership rules remain in place. A successful full-observable Tier-0
model retains priority and produces a committed observation, while the automatic selection result
is a virtual view. See [the context submit contract](AGENTS_REFERENCE.md#corecontext).

## Evidence required by the collective query

`CONTRIBUTED_TO` is recorded during registration/compilation. Its presence, a positive
observation ID, planned resolved coverage, commit metadata or registered geometry is
not evidence that discovery executed. The query now requires:

- A correctly typed successful execution Activity targeting that producer:
  `INSTANTIATION` through `INSTANTIATED`, or `CONNECTION` through `CONNECTED`.
- The support of that invocation, encoded in the Activity's
  `Metadata.IM_EXECUTION_GEOMETRY` (`im:execution-geometry`). `CompiledDataflow` records
  the geometry passed to the invocation, rather than assuming the producer's entire
  registered extent was executed. The evidence is clipped to the producer's support.
- For transaction-local work, an attempt tree that has not failed. The new read-only
  `Transaction.isFailed()` exposes the existing failure state; it adds no lifecycle
  state machine. A later sibling/root failure invalidates reuse of earlier child
  success. Durable lookup reads the typed Activity from the committed knowledge graph.

Successful zero-member execution supplies discovery support without creating members,
native bearers or quality values. Pending, failed, cancelled or wrong-kind activities
do not supply that evidence. Failure during durable close cannot become successful
emptiness even if IDs or commit metadata were assigned before close.

Legacy contributions missing successful typed execution or invocation support remain
unproven. This increment does not infer completion from old metadata or migrate old
graphs. They may require a new resolution rather than catalog reuse.

## Conservative support boundary

One geometry cannot express every union of invocation supports. Current time union can
fill gaps between periods; merging dimensions independently can invent space-time
combinations. The collective query therefore returns **one actual successful support
fragment**, choosing the fragment with greatest coverage of the requested support.
Its metadata names only that producer. A fully covering single invocation can answer
completely; disjoint or complementary invocations are not promoted to complete coverage.
This may underuse valid existing knowledge until exact multi-support composition is
implemented. It is a catalog reuse limit, not a scientific aggregation or merge policy.
Invocation, producer and requested support must have the same dimension types; this
guard does not broadcast a missing spatial or temporal dimension through intersection.

`IM_QUERY_COVERAGE` describes discovery support, not environmental-field coverage or
member-footprint union. `childrenCount` retains the selected producer's reported count;
it is not a fresh enumeration/count of members clipped to the query fragment.

## Verification and remaining work

Canonical reuse fixtures must compare discovery and selection with identical context,
observer and support constraints. An unqualified substantial submitted as an independent
root retains the existing focus-reset contract; a qualified collective selection preserves
its explicit focus. Their completion receipts are deliberately incompatible when those
contexts differ. The canonical suite therefore tests bare-native reuse without focus and
focused qualified discovery/reuse separately. Partial-support completion also checks that
the original member IDs remain present in the expanded cohort.

Inspector mutation checks distinguish the actual requested predicate from its lexical
family through `classificationcheckfor(context, before, requested, family)`.
Selection itself executes family-root directives, so its classification audits name those
families; the final concrete selection filter is not the operation's audit request. Use
the family-root `classificationcheck` for those audits and `classificationcheckfor` for
a direct concrete classification operation.
Failure cases use `failed(response)` rather than the non-null-only `present` check, followed by execution
counters and `nativeunchanged`; an explicit failure response alone does not prove rollback.
These fixture/oracle corrections require a fresh live run. Their presence is not live or restart acceptance evidence.

`CollectiveCompletionQueryTest` exercises the production query and real transaction
state/commit logic, with an atomic test persistence boundary and recovered JSON objects.
It covers empty/nonempty success, pending/cancelled/wrong activity, later sibling failure,
durable-close failure, legacy evidence, single-period support, disjoint periods/producers
and space-time fragments. Production Activity property encoding/recovery and embedded
Neo4j typed-effect ownership queries have separate regression tests. The ordinary Maven
reactor compiles and runs these alongside the admission, resolver and runtime suites.

This is not full public-submit/live replay acceptance. Exact discovery-policy, observer,
source/strategy revision and invalidation keys are not added. `getMembers` still has its
existing enumeration contract; this increment does not turn an arbitrary direct call into
a verified discovery binding or define snapshot identity across replay. Recursive
context/member execution, cycles/progress limits, native domains extending outside query
support, sampling and typed composition remain future stages. No scientific gap/overlap,
identity coalescing or directional/volume reducer policy is selected here.

## Scoped semantic evidence

Version-3 selection receipts compose the exact actual execution signature with a
`semantic-dependency-v1` Reasoner witness under `semantic-content-v2`. Static identity covers
accepted sources and semantic configuration; process-local generation and owner session remain
active execution fences. Each authenticated remote request fetches the observations named in
its scope token from the owning Runtime before capturing its focus; asynchronous commit delivery
is not a freshness proof. This targeted fetch does not rewrite earlier planning snapshots or
clear unrelated caches. Cold revalidation reconstructs canonical Concept expressions through
the accepted compiler before checking their dependency closure. During reconstruction, authority
access is restricted to captured responses or configured snapshots; a missing mutable answer
cannot be fetched or treated as independence. Durable reuse recomputes the scoped proof and checks the existing
source/member, observer, scope, geometry and support constraints. Equal numeric generations
alone are never proof; unchanged semantic content and relevant authority proofs may survive a
restart. The stable worldview namespace (root ontology URN) remains part of content identity;
the generated Resources worldview instance ID does not. That instance ID still participates in
its separate commitment/admission checks. Reconstructing an identical namespace and typed
semantic inputs therefore preserves content identity, while a namespace, source, strategy,
compiler, authority recipe or published codelist change requires revalidation. Receipts made by
a different fingerprint calculation are not relabeled. Legacy receipts retain their observations
but are not stamped or certified by inspection.

An authority-independent complete signature can be certified despite unrelated mutable providers.
A relevant deterministic configured snapshot contributes its immutable digest. Relevant legacy
unversioned answers require revalidation and cannot certify an exhaustive selection; unknown
execution footprints likewise cannot become successful empty results. The current protocol does
not implement provider refresh/retraction or claim that cached ancestry is a fresh response.
Opaque adapters/functions remain usable through their existing routes, but cannot provide a
complete scoped selection certificate without an audited semantic input/output contract.

Actual scalar computations are attested through their private compiled engine, processed closed
arithmetic grammar, builder revision and original bound observation identities. Source text alone
is insufficient. Transported query inputs re-enter the ordinary Runtime query and preserve all
payload guards; only proof-equivalent historical audit counters may differ after revalidation.

Classification captures its consumed input signature before applying attributions, then adds
post-effect results and characterization evidence through the same private, generation-pinned
collector. A successful already-known family still binds the completed native view before
skipping execution. Read-only QUERY_ID submissions retain the caller's context. Native replay
uses its declared support, preserving promoted temporal defaults without changing the original
view; final payload/source/member/observer checks still reject changed support or results.
Concept type-set order is normalized for transport comparisons; ordered arrays and all type
contents remain guarded.
