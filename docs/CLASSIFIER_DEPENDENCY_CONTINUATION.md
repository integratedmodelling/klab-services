# Request-local classifier dependency continuation

The sixth isolated candidate adds an explicit service-local Runtime entry,
`RuntimeService.resolveClassifiedDependencies(Prepared, prerequisites, support, event, scope)`.
It runs the actual member classifier, applies occurrence-keyed Cartesian expansion, redeclares
concrete optional dependencies, checks canonical executability, and invokes ordinary Resolver
planning. Returned plans retain their native context and independent branch identity.

This is a bounded prerequisite hop. The normal selected-parent-model dependency loop does not
yet call it, and the returned dependency plans are not executed or bound into a parent model's
inputs. It must be invoked only after selection of a route needing classification. Successful
precomputed outer Tier-0 routes continue to avoid it. The direct classification strategy itself
retains its documented Tier-0 status; prerequisite work does not globally change that ranking.

## Admitted inputs

- A live Runtime parent attempt and one already successful local instantiation producer, with
  affirmative typed Activity outcome and exact support, linked to its native context and owning
  persisted cohort. Missing completion is UNKNOWN; failed completion is FAILED. An empty cohort
  is successful only with that affirmative producer completion.
- Abstract trait/role occurrences directly on an explicitly named inherent native bearer.
  Each occurrence has its own frozen request identity and classifier source plan. The classifier
  family, original dependency semantics, units/currency and original optionality must match.
- One native cohort-plus-ancestor-attempt overlay domain for all occurrences. This certifies
  the current complete cohort view plus tentative member links, not a producer-only result set.
  Concrete bearer filters and other residual qualifiers are rejected rather than borrowing the
  base cohort domain. Other occurrence shapes, mixed domains, durable producer replay, discovery and member-set
  interpretations are not inferred.

Each member result includes valid reused predicates as well as fresh classifier results and
explicit permitted optional absence. Reuse creates no new attribution. Independent occurrence
closures form the documented positional Cartesian product. Explicit shared-binding syntax and
new correlation/reducer policies are not introduced. Generated branches have the expansion
contract's optionality; the original classifier dependency and unexpanded source keep theirs.
Optional NO_MODEL remains distinguishable from failed planning or transport failure.

## Snapshot and atomicity

A short native knowledge-graph transaction captures owning cohort meaning, exact sorted member
IDs, full observation properties, geometry, perceived geometry and per-member semantic revision.
Materialization uses these captured properties through the normal observation restoration path;
it does not fetch newer state. Local captured getter payloads include metadata and contextualization
state as well as semantics, support and identity. Durable transport-wrapper transient IDs do not
change durable member identity. Producer completion, contribution/context links, source plan,
syntax occurrence, semantic commitment and live root attempt are also bound.

The local mutation stamp is checked around native capture. All graph locks are released before
classifier or Resolver work. Each classifier's own attributions are staged atomically against the
expected projected view; unrelated member, semantic, payload, source or completion changes fail
the attempt. Characterization must not change that frozen member input beyond the staged
attribution in this capability. Support is detached from caller-owned geometry and pinned.

Root commit checks local guards before its own lifecycle metadata preparation, locks the sorted
union of read members and modified existing observations, and validates all native read sets on
that same native write transaction before writes. This also applies when every classifier result
was reused. Membership writers lock the owning context/cohort; new cohort ownership links precede
membership links. A stale read or failed/cancelled continuation rolls back the whole parent attempt.
The semantic cache generation is never used as a membership certificate.
As in the existing commit lifecycle, a later failed durable commit can leave prepared lifecycle
metadata in memory. Native effects roll back and attributions remain unpublished; lifecycle
metadata alone is not a durable success receipt.

This is request-local validation, not a persistent reuse or invalidation protocol. A classifier's
arbitrary external service/datastore reads are not certified by this observation snapshot.
Unsupported payload projections fail closed. No graph monitor is held across asynchronous work.

## Verification and next boundary

`ClassifierDependencyContinuationTest` enters the concrete Runtime method, uses actual member
classifier execution, staged transaction effects, prepared expansion, and actual Resolver/
ResolutionCompiler/DataflowCompiler reference planning. Component registry, typed declaration,
service boundaries and known cached observations are controlled test doubles. Native read/commit
behavior is independently exercised with real embedded Neo4j and the production transaction code.
This is not a live multi-service or model-computation integration claim.

Coverage includes fresh/reused outcomes, two abstract predicates, asynchronous branch ordering,
empty versus pending/failed/cancelled producers, optional absence versus failure, eligibility
before planning, member/payload/meaning/support/source drift, rollback, and successful root commit.
Existing Tier-0, native-bearer, dependency eligibility and recursive binding regression suites
remain part of the reactor selection.

The next engineering step is selected-parent-model suspension/resumption and generic concrete
input fanout/binding, with execution and parent atomicity tested together. UNKNOWN retry/lifetime,
explicit shared-binding syntax, persistent invalidation, outer coverage/composition and scientific
reducers remain unselected; this API makes none of those decisions.


## Typed redeclaration and native proof diagnostics (seventh work in progress)

The public typed Reasoner entry points now normalize a detached copy of a mapped tree whose
root concept URN was invalidated. `DependencyExpansion` deliberately invalidates cached syntax
URIs; passing that tree directly to canonical lookup previously failed on a null URN. This repair
preserves the frozen input and generated optionality and uses ordinary declaration/validation.
It is not generic repair of inconsistent ASTs with a non-null root and invalid children.

The real OWL/HermiT fixture `ClassifierNativeAdmissionTest` compares ordinary source construction,
incremental concept-building and classifier-generated typed redeclaration. Generated trait
restrictions remain necessary subclass restrictions; they do not establish a subclass relation
between two generated compound expressions merely because their predicates are ordered. An explicit
native subtype axiom can prove a fixture's binding; it is not added automatically by this repair.
`X of A` also does not inherit abstractness from its clause filler (OBSERVABLES section 7.2).
No provisional executable-admission mode is introduced.

The owner has selected one modeled execution with independent concrete prerequisite observations,
retrieved by its final contextualizer from the graph. Observation dependency/provenance is distinct
from native ownership: existing AFFECTS prerequisites and actuator HAS_CHILD links must not become
an observation HAS_CHILD that changes a quality's native bearer. No reducer is implied.

Ordinary Runtime.submit suspension, classifier planning/dispatch, compatible observed-member binding,
concrete dependency execution, graph-family consumption and numerical crop valuation remain outside
this verified diagnostic/repair boundary. The planned crop fixture uses explicit prices with units
of currency per harvest mass and changed crop membership; neither physical harvest quantities nor
the Cartesian product are automatically monetary values or sums. Test vocabulary is not a worldview
addition. No singleton numerical or multi-result runtime acceptance is claimed here.


## Approved monotone attribute refinement prerequisite

The later isolated refinement slice supersedes the earlier diagnostic's missing positive compound
link for the bounded language rule now approved in OBSERVABLES section 7.3. Ordinary construction,
incremental building and classifier typed redeclaration share provenance-annotated directed links
for proved attribute replacements in otherwise identical expressions, in either build order.
Native eligibility can use those links; no source model or runtime context is retyped. Other
operators, changed clauses, identity/role substitution and arbitrary data compatibility remain
outside the rule. See [construction, authority and cache boundaries](MONOTONE_ATTRIBUTE_REFINEMENT.md).
This is still a prerequisite rather than automatic parent-model execution or crop aggregation.
