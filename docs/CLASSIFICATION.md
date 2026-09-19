# Classification and characterization

Status: **CLASSIFICATION is complete and accepted**, confirmed by the maintainer on
2026-09-19. The implementation includes mandatory individual characterization after each new
attribution. The classification staging plan and continuation prompts have been retired.

This reference describes the implemented contract. See [observable semantics](OBSERVABLES.md),
[observation strategies](OBSERVATION.md), [resolution](RESOLUTION.md), and
[knowledge-graph persistence](KNOWLEDGE_GRAPH.md) for the surrounding architecture.

## Semantic contract

Classification resolves `P of each S`, for abstract or concrete predicate P and substantial S.
Distributed inherence alone selects CLASSIFICATION. Without `each`, `P of S` selects
CHARACTERIZATION regardless of abstraction. Quality inherence remains TRANSFORMATION.

Classification attributes a satisfiable concrete P or concrete specialization; it never
attributes an abstract predicate. Existing valid attributions satisfy the request without
reinvocation, another effect edge, or another characterization. Identity, URN, geometry, cohort
membership, and unrelated predicates are preserved. Invalid same-family attributions remain
errors; reuse does not authorize replacement or accumulation of classifications.

Characterization models may match the predicate exactly or subsume it. Discovery includes abstract
and concrete ancestor heads; semantic distance participates in the general Prioritizer criterion
order (lexical scope first by default). Inherency and other semantic constraints still apply.
Distinct non-predicate heads remain incompatible. See
[choosing a characterization model](OBSERVABLES.md#15-choosing-a-characterization-model).

## Strategy and resolution

Classification always consumes observed members. Resolving `each S` is a mandatory Tier-0
prerequisite. Complete cohort coverage is reused; partial coverage resolves the missing support.

```text
strategy 0 named classification.members
  for pattern {
    node {
      activity = CLASSIFICATION;
      inherent = capture members as node { collective = true; };
    }
  }
  ensure context.exists()
  resolve $members to cohort
  observe $this with inputs(members = cohort);

strategy 0 named characterization.direct
  for pattern { node { activity = CHARACTERIZATION; } }
  ensure context.exists()
  observe $this;
```

The classifier model explains the full predicate-of-each-substantial directive. Abstract predicate
requests are intentional and must not be rejected by a `request.fully_specified` guard.
The `members` port is a typed collective/member binding, not a scalar model argument.

The existing Resolver API returns a Dataflow. `ResolutionCompiler` represents semantic updates
with `OperationTarget`, and portable UPDATE actuators carry operation semantics, requested support,
and typed target bindings. No observation, storage, or scheduler entry is allocated for the
directive. UPDATE dependencies execute before their consumer, including consumers with empty
computation. All mutations result from resolved Dataflow execution; there is no separate mutation API.

## Member selection and classifier invocation

Member producers complete their required individual resolution before classification. Enumeration
combines durable `HAS_MEMBER` links with transaction-local links, including ancestor transactions,
and deduplicates member identities. Selection intersects member geometry with producer/request
support and checks decoded extent emptiness. Points and lines retain their spatial support;
disjoint members are excluded. A completed empty cohort succeeds without invoking a classifier.
A missing or incomplete producer is an error, not an empty cohort.

`MemberClassifierExecutor` selects one unambiguous local implementation through ComponentRegistry.
The method returns Concept and accepts exactly one Observable and one Scope/ContextScope. It may
also accept one each of Observation, ServiceCall, Geometry, and Scheduler.Event. Unknown, duplicate,
or ambiguous parameters and non-Concept return types are rejected; instance methods require a
local receiver.

The Observable argument is the full classification directive. Each invocation receives
`scope.within(member)` and the actual member when requested. Validation removes only INHERENT
from the directive to obtain the requested predicate, preserving its other restrictions.

| Classifier result | Required dependency or other trigger | Optional original model dependency |
|---|---|---|
| Concrete, satisfiable specialization, including the requested concrete predicate itself | Pending attribution | Pending attribution |
| Null | Failure | No attribution for that member |
| NOTHING / owl:Nothing | Inconsistency error | Inconsistency error |
| Abstract, unrelated, unsatisfiable, or non-predicate concept | Failure | Failure |
| Invocation exception | Failure | Failure |

Only the original model dependency's optional flag permits null. Root requests and strategy-only
requests do not acquire that permission from their own optional flag. Missing implementations,
service failures, and invalid results are never suppressed as optional outcomes.

Invocation success or failure is cached per compiled executor, root transaction, member, event,
and support. The full batch is validated before staging. `PendingAttribution` records contain the
member, original observable, requested and returned predicates, support, and event. The legacy
transport field `abstractPredicate` means the requested predicate, which may be concrete.

## Atomic attribution and provenance

`TransactionImpl.stageAttributions` uses the member's Reasoner builder to add the returned trait or
role and validates the resulting observable. Detached semantic replacements leave original live
objects unchanged until durable root commit. Transaction asset/link views expose the staged
replacement. This is a semantic overlay, not general isolation for arbitrary fields or Cypher queries.

New members are stored with final semantics. Existing members are updated through
`KnowledgeGraph.Transaction.updateSemantics`, with locks acquired in ID order and the persisted
observable compared against its recorded baseline. Stale or missing targets fail the transaction.
Indexed observable, semantics, and semantic-type properties change together; unrelated state is
preserved. Explicit replacement/combination and repeated concurrent staging are unsupported.

Successful root commit publishes semantics to live objects, reports existing IDs in
`Commit.modifiedAssets`, and invalidates graph, scope, and client caches. New members remain in
`addedObservations`. Invocation, characterization, staging, or storage failure rolls back the
batch. A successful child activity means staged work; only root commit establishes durability.

Classification provenance is:

```text
parent Activity -TRIGGERED-> CLASSIFICATION Activity -CLASSIFIED-> member
```

Each CLASSIFIED edge records before/after observable URNs, requested/returned predicates, encoded
support, event, and member identity. The portable audit list is also Activity metadata
`Metadata.IM_ATTRIBUTIONS`. Typed effects establish visibility but do not establish deletion
ownership. `CONTEXTUALIZED_BY` separately connects observations to executable actuators;
`HAS_PLAN` connects activities to plans. No generic CONTEXTUALIZED effect is used.

## Mandatory characterization

After staging a new attribution, `CharacterizationLifecycle` resolves the concrete returned
predicate of the singular substantial in the staged member's scope. It does not resubmit the
whole member or re-enter classification. Runtime awaits all required child work before completing
classification and committing the root transaction. Characterization is a runtime lifecycle
obligation, not an authored strategy continuation.

Dataflow outcomes distinguish `RESOLVED`, `NO_MODEL`, and `FAILED`. Successful discovery with no
characterization model is a terminal success and preserves the attribution. Its Resolution
Activity records `resolutionOutcome=NO_MODEL`; no CHARACTERIZED edge is created when no work ran.
Infrastructure errors, exceptional responses, and contextualizer failures remain failures.
Actual successful characterization creates a CHARACTERIZATION Activity with a CHARACTERIZED edge
to the existing member and a plan FlowChart; the directive never becomes an observation.

Local characterizers return `void` or primitive `boolean` (`false` fails). They require Observable
and exactly one Scope/ContextScope, and may accept Observation, ServiceCall, Geometry, and
Scheduler.Event without duplicate parameter types. A model with dependencies and no local
computation is executable. Observation/Concept-returning and remote/adapter characterizers are
outside this executor's supported contract and fail compilation explicitly.

## Diagnostics and verification

Accepted resolution graphs travel as FlowChart metadata on Dataflow and RESOLUTION Activity.
Operation nodes expose contextualization and optional-dependency status; links expose member
bindings and coverage. Execution records per-member attribution audit data and typed effects.
See [resolution diagnostics](FLOWCHARTS.md#resolution-diagnostics).

Regression coverage includes semantic dispatch and transport, model matching, member invocation
and reuse, support filtering, atomic persistence and rollback, cache invalidation, characterization
scope and completion ordering, no-model outcomes, duplicate visits, and provenance. The maintainer
has accepted the implementation following live testing. No classification staging acceptance work
remains. General strategy composition and provenance-derived replay remain separate work described
in [OBSERVATION.md](OBSERVATION.md) and [DATAFLOW.md](DATAFLOW.md).
