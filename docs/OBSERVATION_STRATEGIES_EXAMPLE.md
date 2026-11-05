# Observation strategy examples

These examples illustrate the executable forms in the [language guide](OBSERVATION.md#language-guide).
The active generated Observation grammar defines parsing, while matching/lowering and Runtime
contracts define which parsed plans execute. Strategy rank, model ranking and execution order
are separate decisions.

## Direct dependent observation

```text
strategies observations version 2.0;

strategy 0 named dependent.direct
  "Find a model for a concrete quality or process in its context."
  for pattern { node { kind = one_of(quality, process); abstract = false; } }
  ensure context.exists(), request.fully_specified($this)
  observe $this
;
```

An absent context rejects this strategy. A containing model supplies the native context for
its dependencies; admission must still validate their required-context obligations. Unnamed
terminal producers return their graph. Use `to name` only when another step needs the graph.

## Collective discovery and individual acknowledgment

An instantiation strategy observes the collective substantial. Runtime acknowledges/resolves
the produced individuals under their own lifecycle and producing-model lexical constraints.
A successful collective can have zero members. No model for an individual acknowledgment can
be an explicit NO_MODEL decision; a failed generator or dependency is not an empty success.

Use stable model/strategy identity and the actual invocation support when recording discovery.
Full-observable precomputed models have priority over automatic decomposition and selection.

## Classification and characterization

```text
strategy 0 named classification.members
  for pattern {
    node {
      activity = CLASSIFICATION;
      inherent = capture members as node { collective = true; };
    }
  }
  resolve $members to cohort
  observe $this with inputs(members = cohort)
;

strategy 0 named characterization.direct
  for pattern { node { activity = CHARACTERIZATION; } }
  ensure context.exists()
  observe $this
;
```

An abstract classifier request is legal: the implementation must return a satisfiable concrete
predicate equal to or specializing the requested predicate. The members port binds observed
members, not a directive observation. New attributions require individual characterization.
See [Classification](CLASSIFICATION.md).

## Relationship endpoints

```text
let source = relationship.source($this), target = relationship.target($this)
resolve collective($source) to sources
resolve collective($target) to targets
observe $this with inputs(source = sources, target = targets)
```

This plan body resolves endpoints before looking for the relationship model. Endpoint graphs
alone do not establish relationships. Runtime validates actual participants against declared
endpoint semantics, including compatible specializations, while respecting directed order or
unordered bond identity. See [Connection](CONNECTION.md).

## Predicate transformation

Decompose a concrete quality's direct trait with `predicates.split_first`, resolve its base,
observe the trait inhering in that base and finish with a terminal two-input transform merge.
The selected transformer must declare one unambiguous base port. Existing base observations
can be references. Both inputs must fulfil their own obligations; neither base coverage nor a
matching trait substitutes for the transformer. General merges and chained results remain
outside this lowering. See [Predicate transformation](OBSERVATION.md#predicate-transformation).

## Presence, count and concrete collection selection

Presence/count first permit direct models of the full quality. The bounded automatic reducer
then requires complete native discovery and actual current membership under its scalar,
initialization-only contract. A completed empty collection produces false/zero; missing evidence
does not. See [Collective reductions](TIER1_COLLECTIVE_REDUCTIONS.md).

Concrete collection selection completes native discovery and family classifications before
sealing a source-backed view. Ordinary models can consume that view through a named input;
their alias receipt, numerical execution and atomic rollback are separate acceptance checks.
See [Collective completion](COLLECTIVE_COMPLETION.md).

## Matching and unsupported composition

Commas between `for` alternatives mean OR; commas between ensure calls mean AND. Pattern
captures remain candidate-local. Semantic tests inside a structural pattern do not replace
structural matching of the whole expression. Lowering diagnostics identify selection, setup
and unsupported plan constructs.

General logical graph composition, arbitrary context/member iteration, fallback blocks,
operator projection and graph-reproduction execution must not be inferred from grammar support.
Consult the [composition limits](OBSERVATION.md#composition-and-execution-limits). When extending
a construct, check real parsing/adaptation, interface transport, matching, lowering, planning,
Runtime binding, scientific effects and negative controls together.
