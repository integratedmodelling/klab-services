# Abstract-predicate dependency expansion: prepared syntax boundary

P0 provides request-local frozen preparation and keyed outcome application. Production
Resolver/classifier orchestration remains absent. This is not a completed-classification
adapter, member snapshot, deferred runtime, semantic cache or end-to-end recursive resolver.

## Supported API

`DependencyExpansion.prepare(originalSyntax, declarations)` freezes one detached observable,
canonical declaration snapshots, a fresh syntax-attempt UUID and occurrence paths. It invokes
no classifiers. `Prepared.requests()` returns fresh detached payloads with the same identities
on every call. Equal predicate names/text or shared AST objects do not couple positions.

`Prepared.apply(completed)` requires exactly those RequestIdentity keys, including UUIDs.
Missing, foreign, extra and wrong-path outcomes are rejected. Each completed classification
owns detached canonical leaf syntax; callers cannot mutate its private candidates through
input objects or accessors. UNKNOWN and FAILED throw a typed ClassificationException carrying
the exact occurrence and status. No failed/unknown factor is hidden by another empty factor.

`Prepared.applyAsync(stages)` accepts caller-owned CompletionStages keyed to the same plan.
It registers nonblocking captures, freezes each completed outcome and applies the existing
positional product after all captures complete. It invokes no classifier and creates no
executor, timeout or retry. Cancellation propagates promptly as cancelled completion and
never cancels borrowed stages. Ordinary exceptional completion uses allOf and waits for the
remaining captures; this does not implement prompt Runtime rollback or parent-attempt policy.
Consumer cancellation also leaves borrowed prerequisite stages alone.

The original synchronous `expand` is an adapter over prepare/apply. Its no-factor case
continues to return the original object. Prepared no-factor application returns a detached
unchanged observable, retaining its required/optional flag and cached authored URN.

## Product and eligibility contract

The approved contract is `odo-im/docs/decisions/0001-worldview-semantics.md`, Abstract and any
resolution. Separate bearer/endpoint/role/position occurrences have independent choices;
sharing substitution requires explicit semantic binding, whose syntax is not selected.
One shared completion stage at two independent positions still gives the complete positional
Cartesian product. Concrete `any` self is allowed; ANY does not alter canonical abstractness.
No generic global URN replacement or descendants-of-whole-expression algorithm is used.

All factors must affirm SUCCESS before an empty factor can yield successful empty expansion.
Successful nonempty products preserve native clauses, units, authored source provenance and
subjectivity, invalidate cached observable URNs, and mark generated dependencies optional.
Classification prerequisite optionality is governed by the original dependency; generated
optionality does not excuse failed, unknown or invalid classifiers.

The helper checks syntax shape and concrete named predicate flags. Authoritative predicate
family/satisfiability proof remains the classification provider's responsibility. Every
result must be redeclared and pass existing model/native-bearer/identity/subjectivity and
accepted-revision validation before semantic distance or execution. The production public
Reasoner gate tests prove ineligible canonical compound requests do not invoke the matcher;
intentional abstract classifier categories retain their existing exception.

## Production integration seam (updated after P1 and the sixth candidate)

The stable prepared attempt now bridges asynchronous syntax requests without rerunning
preparation and changing their UUIDs. It must be associated with the enclosing Runtime
attempt, not used as a Runtime transaction ID. The documented next API remains:

`classifyPrerequisite(parentAttempt, occurrenceRequest, runtimeContext) -> CompletionStage<ClassificationOutcome>`

The sixth candidate implements a bounded explicit Runtime prerequisite hop with coherent
cohort-plus-attempt capture, complete keyed outcomes, redeclaration, eligibility validation and
ordinary concrete dependency planning. See [its admitted inputs, atomicity and exact limits](CLASSIFIER_DEPENDENCY_CONTINUATION.md).
The selected-parent-model dependency loop still needs suspension/resumption and generic input
fanout/binding. The explicit hop must not run while ranking speculative model candidates.

The independent-occurrence product and outcome distinctions are settled. Explicit shared
binding syntax is undecided and unsupported. UNKNOWN resume/parent-lifetime and retry/cycle/
retention policies remain future lifecycle design; no such choices are made by this boundary.
Current classification's direct strategy is documented Tier-0 despite required member
prerequisites. Successful precomputed outer Tier-0 strategies continue to win without entering
this future route; do not relabel every classifier as Tier-1 or eagerly prepare candidates.

## Diagnostics

ClassificationExecutionTest installs the standard scale factory independently of suite order.
The optional actual generator-source fixture now supplies its companion sourcepath and runs
when configured. The sixth candidate's ordinary Maven runner enables both that fixture and the
local LAND archive probe; optional skips must be reported separately when either is unavailable.
No generator artifact, shared cache, live service or deployed runtime is changed.
