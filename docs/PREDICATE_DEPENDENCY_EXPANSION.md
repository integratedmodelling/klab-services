# Abstract-predicate dependency expansion: prepared syntax boundary

The prepared expansion API freezes request-local syntax and applies keyed outcomes. Runtime
and Resolver integrate it through the supported prerequisite/family routes described below.
The syntax object alone is not a classification certificate, native member snapshot, transaction
or general recursive resolver.

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

## Runtime integration

The stable prepared attempt bridges asynchronous syntax requests without rerunning preparation
or changing request identities. It belongs to an enclosing Runtime attempt and is not itself
a transaction ID. The bounded prerequisite route captures coherent cohort/attempt state,
returns complete keyed outcomes, redeclares generated syntax and checks eligibility before
ordinary concrete dependency planning. See [continuation](CLASSIFIER_DEPENDENCY_CONTINUATION.md),
[selected-model families](CLASSIFIER_DEPENDENCY_FAMILIES.md) and
[independent products](CLASSIFIER_CARTESIAN_RECOVERY.md).

Stateful classification is not speculative ranking work. Successful precomputed Tier-0
alternatives remain eligible before automatic expansion. Direct classification is a Tier-0
activity even though member resolution is its required prerequisite.

Independent positional products do not imply shared binding syntax. General unknown-outcome
resumption, cross-attempt retention/retry and arbitrary recursive lifecycle policies are not
supplied by this API.

## Diagnostics

Tests must establish their own scale factory and required fixture availability independently
of suite order. Report optional source/archive fixture skips separately. Source preparation,
keyed outcome composition, typed redeclaration and actual native execution are distinct checks.
