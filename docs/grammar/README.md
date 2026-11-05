# Observation grammar reference

The active generated grammar is `Observation.xtext` in the sibling `klab-languages` project.
This directory retains a reviewable [Xtext reference](Observation.xtext) and syntax fixtures;
it does not replace the parser artifacts loaded by Services. Grammar acceptance, semantic
adaptation and executable lowering are separate contracts.

Read the [language guide](../OBSERVATION.md#language-guide),
[strategy examples](../OBSERVATION_STRATEGIES_EXAMPLE.md) and
[composition limits](../OBSERVATION.md#composition-and-execution-limits).

## Source and fixture boundaries

- [Observation.xtext](Observation.xtext) describes the strategy/document surface, including
  forms whose complete runtime behavior is not implemented.
- [Strategy syntax fixture](../examples/observation-strategies/observations-proposed.txt) supplies
  concrete examples for parsing/adaptation. Its `.txt` extension does not make it a deployable
  worldview document.
- [Context forms](contexts-proposed.txt) and [dataflow source](dataflow-proposed.txt) exercise
  additional syntax. A parsed context block or reproduction document is not an executable
  general composition/replay contract.

The source document version and the semantic transport bean's model version are distinct.
Changes to grammar/adapter/API shape require compatible generated language and Services artifacts.

## Strategy conventions

`for` alternatives are disjunctive. Ensure/guard lists are conjunctive. Setup and capture names
are local to their candidate; failed alternatives and negation cannot leak captures. Graph
names identify planning results, while `$this`, `$context` and captured variables denote their
documented semantic values.

An unnamed producer is terminal. A named terminal producer can return implicitly or through
explicit yield of the final producer. Prior graphs require explicit consumed bindings; merely
producing them does not combine their coverage. A merge's `yielding` clause asserts result
meaning rather than selecting an arbitrary earlier graph.

Structural patterns and ordinary observable patterns have distinct matching rules. A semantic
subsumption test inside a structural pattern does not turn the whole matcher into a single
ontology comparison. Unsupported projections must produce diagnostics rather than broaden the
request silently.

## Validation boundaries

The grammar determines accepted syntax. Semantic adaptation/validation additionally checks
names, arity, graph binding and operation contracts. Reasoner matching/setup/lowering checks
supported constructs; Resolver proves candidate/dependency coverage; Runtime validates actual
targets, installed implementations and execution evidence.

When extending a form, update grammar, syntax adapter, portable API, semantic validation,
matching/lowering and the relevant planning/execution tests together. Exercise populated
interface JSON and real-file parsing. Do not hand-edit generated lexer/parser code or infer
runtime support from a successful parse alone.
