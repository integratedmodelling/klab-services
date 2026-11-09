# Consequence engine capabilities and limits

This is a current capability reference. [Consequence contracts](CONSEQUENCE_ENGINE.md) describe
the implementation; [admission and acceptance checks](CONSEQUENCE_ENGINE_ADMISSION_AUDIT.md)
define the evidence required at its boundaries.

## Supported workflow

Successful root commits enter a per-twin asynchronous queue. Native relevance checks precede
semantic assessment. Handlers retain the cycle's observer, semantic actor, enablement and causal
chain, use normal admitted optional resolution and publish effects through new guarded commits.
Causal commits can enqueue more work. A later explicit request waits for the cycle to drain.

Process/relationship conferral, implication, positive configuration detection and guarded
single-root explanatory recomputation have Runtime paths. CHANGE checks current semantic process
writers before submitting redundant work. Local modeled cessation is execution-bound and retains
native history; it is not arbitrary asset deletion. Currentness, source/admission, support and
original native capture checks remain necessary in every path.

## Capability boundaries

| Area | Supported contract | Limit |
| --- | --- | --- |
| Cycles | Per-twin frozen logical view and observer through recursive drain | No immutable external mutable-authority dataset snapshot |
| Queue | In-memory work ownership, local duplicate suppression and cooperative shutdown | No durable outbox, crash replay, exactly-once or general backpressure/retry guarantee |
| Intrinsic roles | Guarded support, attribution and optional characterization; normal resolution for implied targets | Independent-support withdrawal/reacquisition requires complete scenario-specific verification |
| Configuration detection | Supported positive matching, guarded ingredients/placement and same-identity recomputation | Arbitrary mixed-bearer/negative emergence, multi-root explanation and termination are unsupported |
| Relationships/networks | Explicit endpoint semantics and supported structural/component handling | Full scenarios must verify recipient rules, disconnected components, identity/history and observer filtering |
| Cessation | Local Data.Builder intent bound to actual execution and owned inputs | Remote/Avro cease output has no accepted authority envelope |
| Scientific completion | Typed resolution/reuse evidence plus current native support and effects | Positive IDs and duplicate metadata do not certify APPLIED |
| Uncertainty | Quality-specific, lowest-priority optional assessment category | No default execution handler; reports DEFERRED without one |
| Client integration | Typed decision/commit messages and graph invalidation | Reconnect/history and viewer navigation require their own integration checks |

## Verification requirements

Acceptance follows the claim being made: parsing and source validation, portable transport,
admitted planning, actual native execution, atomic publication, committed inspection, recovery
and full service deployment are separate boundaries. A test suite covering selected models does
not imply coverage of every network, cessation or external authority configuration.

Use ordinary public APIs with matching deployed contracts. For scientific scenarios verify actual
method invocation, correct values/effects, current support and observer, and immutable provenance.
For rollback, fail after work has actually begun. For independent role supports, remove one real
support and prove that another retains the role before testing final-support withdrawal.

Relationship/network checks include omitted-versus-explicit recipients, native bearer placement,
cross-cohort components, stable identities, split/merge continuity, empty persistence and supported
current-time execution. Cessation checks include affected dependents/relationships, current lookup
and scheduler exclusion, surviving assets and current COUNT with preserved initialization history.

Native scalar convenience reads and explicit requested-support reads must preserve the same
scalar/distribution guard. Client checks verify accessible new observations and quiet background
refresh without changing focus, observer, expansion or scroll position.

## Operating and extending the implementation

Deploy compatible API, language, Runtime, Resolver and Reasoner contracts. Suppliers can negotiate
their supported candidate protocol independently. Opt-in fixture packs must remain isolated from
the working worldview. Evidence from controlled components must be identified as such.

Extensions must preserve native ownership/read sets, semantic/source admission, causal cycle
lifetime, actual scientific completion and immutable history. Unsupported semantics require a
defined contract before implementation. Documentation and source-control history serve different
purposes: this reference describes behavior and limits, not completed implementation stages.
