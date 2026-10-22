# Collective query completion

This increment is a foundation for recursive resolution, not an executable contextual
route. It repairs the collective catalog-query boundary using existing execution
Activities and transaction state. Containment still legalizes a request independently
of this query: Tier-0 precomputed fulfillment does not need member discovery.

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
