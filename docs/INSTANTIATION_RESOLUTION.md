# Resolution of instantiated individuals

Instantiation establishes which individuals exist, with their identities and geometries.
Explaining each individual is a separate operation. The `@resolution` annotation controls
that automatic secondary explanation. Producing and connecting the roads of a country,
for example, may be sufficient without explaining every road independently.

## Annotation and modes

Use `@resolution(none)`, `@resolution(collective)` or `@resolution(individual)` on an
instantiator model whose primary observable uses `each`. Omission means `individual`.
Positional arguments and named `value` are accepted; names and mode values are lowercase.
Missing, unknown, conflicting or extra arguments are errors, rather than default behavior.

| Mode | Secondary planning | Child execution |
| --- | --- | --- |
| `none` | No automatic explanatory lookup | Acknowledge each individual through its ordinary submission, preserving identity, geometry, membership, provenance and lifecycle |
| `collective` | Select one explanatory plan for the singular member meaning over the full contextualized support of the producing `each` model | Bind a separate copy to each child, with its own support, dependencies, executors and transaction |
| `individual` | Resolve each child in its native context and support | Execute each resulting plan independently |

`collective` is the modeler's assertion that the modeling scope and explanatory selection
have been validated over the full producer support. It skips repeated model selection
and model-scoping admission. Ordinary binding, coverage checks and contextualization still
occur for every child. It neither chooses the first child as representative nor turns the
individual outputs into a collective-valued result. Values can differ between children.

`none` does not prove qualities or dependencies that were never computed. It does not
prohibit later explicit explanation, suppress the consequence engine or bypass acceptance
of effects that actually occur.

## Originating observation and producer precedence

An annotation on the originating observation applies to all instantiations in its compiled
explanation, including contributions from different instantiator models. Supply it through
the originating k.IM `define observation` or through the observation builder:

```java
scope.observation(observable)
    .withAnnotation(Annotation.of("resolution", "value", "none"))
    .geometry(geometry)
    .submit();
```

Request annotations have the same precedence as explicit observation definitions. An
originating policy takes precedence over all producer policies and is harvested into the
produced children. This includes an explicit `individual` request. The effective annotation
and its priority survive interface transport and native persistence.

Without an originating override, each computation contributes its own producer policy.
The compiled Actuator retains that declaration alongside its per-computation lexical
constraints. Runtime binds it to that computation's actual outcomes; a later model's merged
annotation on the collective cannot change earlier outcomes. An explicit annotation on a
produced child overrides its producer under ordinary definition precedence.

The annotation does not become a general resolution constraint. Dependencies retain normal
resolution. A nested instantiator uses an explicitly propagated originating policy or its
own producer declaration; unrelated later submissions are unaffected.

## Shared planning and binding

Each compatible producer contribution owns one batch-local planning future. Its request
uses the singular member meaning, frozen observer, producer namespace/project and constraints,
and full contextualized producer support, intersected with any declared model coverage.
An absent namespace or project remains valid. An empty batch performs no secondary lookup.
There is no durable shared-plan or negative-result cache across batches.

The selected Dataflow is frozen before binding. Every child receives a separately decoded
plan with fresh provisional dependency identities and executor state. Binding crops the
applicable coverage fragments, remaps dependency parents and process bearers, preserves
frozen admission evidence, and restricts observer contribution support. References to existing
native observations must remain accessible, current and sufficient for the child's support.
The ordinary compiler, scheduler, input guards and owned transaction then execute that plan.
Sibling storage and numerical values are never shared by this policy.

A successful shared explanation must establish full producer coverage. A child outside
that support, partial resolution, stale native input or an incompatible template fails the
batch. Runtime does not silently fall back to per-child model selection. A completed
`NO_MODEL` decision may acknowledge all children; `DEFERRED` and `FAILED` are preserved as
failures, rather than interpreted as proof of absence.

### Collective template boundaries

The binder supports ordinary observation/dependency trees, coverage fragments, process bearer
remapping and guarded native references. Plans containing instance-bound semantic UPDATE
operations, classifier dependencies, dependency families, member-quality input plans, query
views or already initialized dependency data are rejected with a diagnostic directing the
author to `individual`. These structures contain additional native binding evidence that
cannot be copied to siblings as ordinary observation references. This restriction is checked
explicitly; the policy cannot authorize writing an existing native observation through a
copied template.

## Lifecycle and acceptance

All modes use ordinary registration and child submission within the producing transaction.
Identity reconciliation, collective membership, parent links, source attribution and provenance
remain required. A failed required child prevents publication of a partial successful batch.
Cancellation and root rollback retain the same ownership rules as individual resolution.

An acknowledgment has its own executable lifecycle plan even when it has no computation.
It records existence, rather than inventing numerical evidence. Portable plan snapshots allow
the scheduler to recover it. Event instantiation retains interval scheduling, bounded-period
validation, pending visibility and separate individual START/END transitions. `none` preserves
these intrinsic transitions without adding explanatory numerical effects. Behavior attachment
or other initialization performed by an explanatory model requires that explanation; the
annotation does not replace such model computations.

## Verification boundaries

Focused tests cover actual k.IM parsing and semantic-bean transport, per-computation policy
compilation, originating builder precedence, mixed producers, default individual behavior,
large `none` batches with zero Resolver calls, and one shared selection with separate child
bindings. Runtime pipeline tests check ordinary compilation/submission and failure propagation;
native transaction tests check atomic publication, provenance, membership and rollback.
Temporal storage tests exercise event START/END, failed commit, retry and scheduler restart.
These controlled fixtures do not replace a rebuilt-stack test with real model services and
adapters, or establish coverage of every possible agent initialization path.

See [Resolution](RESOLUTION.md), [Admission](ADMISSION.md),
[Observation activities](OBSERVATION.md), [Annotations](ANNOTATIONS.md) and
[Occurrence scheduling](OCCURRENCE.md).
