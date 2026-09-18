# Reasoning service

This document describes the semantic API implemented by `ReasonerService`, its remote facade in
`ReasonerClient`, and the administrative lifecycle of the reasoner. The API contract is defined by
`org.integratedmodelling.klab.api.services.Reasoner` and `Reasoner.Admin`.

This is the consolidated reasoner guide, covering semantic APIs, assisted observable composition,
and document validation. For language syntax see [observable expressions](OBSERVABLES.md) and
[worldview ontologies](ONTOLOGY_LANGUAGE.md); for strategy execution see
[observation strategies](OBSERVATION.md) and [resolution](RESOLUTION.md). The
[OWL translation audit](WORLDVIEW_OWL_TRANSLATION_AUDIT.md) records clause coverage and unresolved contracts.

## Contents

- [Semantic API and conventions](#implementation-surfaces)
- [Assisted observable composition](#assisted-observable-composition)
- [Semantic document validation](#semantic-document-validation)
- [Administrative operations](#administrative-operations)
- [Caching](#caching)
- [Remaining implementation gaps](#remaining-implementation-gaps)
- [Source map](#source-map)

The implementation uses OWLAPI 5, HermiT, the k.LAB semantic builder, and the worldview's asserted
axioms. Callers should use the `Reasoner` interface rather than the OWL layer directly.

## Implementation surfaces

- **Local service:** `ReasonerService` contains the authoritative implementations.
- **Remote client:** `ReasonerClient` invokes endpoints exposed by `ReasonerController` and caches
  frequent read operations locally.
- **OWL layer:** `OWL` and `Ontology` manage ontologies, restrictions, inferred queries, consistency,
  serialization, and the HermiT lifecycle.
- **Observation reasoner:** `ObservationReasoner` computes observation and identification strategies.

In the tables below, **yes** means that the operation is implemented on that surface. **Local only**
means that the service implementation exists but the remote protocol cannot currently express or
transport it. **Partial** identifies an implementation whose complete interface contract is not yet
fulfilled. **TODO** identifies an unsupported operation that throws `KlabUnimplementedException`.

## Fundamental conventions

### Direction of subsumption

`is(concept, other)` asks whether `concept` is a specialization of, or is subsumed by, `other`.
In conventional notation it tests `concept ⊑ other`.

For example, if `Oak ⊑ Tree`:

- `is(Oak, Tree)` is `true`;
- `is(Tree, Oak)` is `false`.

The same direction is used by hierarchy distance and semantic distance. Reversing the arguments is
not a harmless transformation.

### Distance contract

`semanticDistance(target, candidate[, context])` is directional. `target` is the requested, least
specific semantics; `candidate` is the potentially more specific semantics offered to resolve it.

- `0` means an exact semantic match;
- a positive value means a compatible, mediatable match;
- a smaller positive value is preferred over a larger one;
- a negative value means incompatibility.

`resolves(target, candidate, context)` is exactly the predicate
`semanticDistance(target, candidate, context) >= 0`.

`assertedDistance(from, to)` is different: it is the length of the shortest parent path from
`from` to `to` using asserted parents only. It returns `0` for equality and `-1` when `to` is not an
asserted ancestor. The implementation uses a cycle-safe breadth-first search.

### Null and empty results

Restriction accessors such as `goal`, `inherent`, or `relationshipSource` return `null` when the
restriction is absent. Plural accessors return an empty collection. Resolution failures normally
return `owl:Nothing` semantics rather than Java `null`.

### Asserted and inferred operations

- `parents`, `children`, `allParents`, `allChildren`, and `assertedDistance` use asserted hierarchy
  links.
- `closure`, `is`, `satisfiable`, and OWL restriction queries can use the configured DL reasoner.
- Union subsumption requires every union operand to be subsumed. An intersection is known to be
  subsumed when any operand is subsumed; otherwise the OWL reasoner is allowed to decide.

## Capabilities and service state

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `capabilities(scope)` | yes | yes | Returns identity, URL, worldview ID, consistency, components, schemas, and knowledge revision. |
| `Capabilities.isConsistent()` | yes | yes | Reports the result of the latest load/update consistency check. |
| `Capabilities.getWorldviewId()` | yes | yes | Returns the loaded worldview ID, or `null` before knowledge is loaded. |
| `Capabilities.getKnowledgeRevision()` | yes | yes | Returns a monotonically increasing revision used to invalidate semantic caches. Older implementations default to revision `0`. |

The reasoner should not be used for semantic decisions when no worldview is loaded or when
`isConsistent()` is false.

## Resolution and semantic construction

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `resolveConcept(definition)` | yes | yes | Resolves an atomic URN directly or parses and declares a compound k.IM concept. Results are cached. |
| `resolveObservable(definition)` | yes | yes | Parses and declares a k.IM observable, including mediation and value operators. Results are cached. |
| `compose(concepts, connector)` | yes | yes | Builds an OWL union or intersection. Empty input yields `owl:Nothing`; exclusion and disjoint union are rejected. The client composes a definition and resolves it. |
| `coreObservable(semantics)` | yes | yes | Follows `CORE_OBSERVABLE_PROPERTY` metadata transitively, with cycle protection, to the worldview core observable. |
| `baseSubstantialType(semantics, scope)` | partial | yes | Removes traits, roles, and semantic modifiers from a substantial. Individual-identity lexical roots are not yet restored. |
| `baseObservable(semantics)` | yes | yes | Returns a concept unchanged; for an observable, walks toward the declared base observable. |
| `rawObservable(semantics)` | yes | yes | Removes explicit restrictions by resolving the stored core-observable definition without descending to the worldview core. |
| `describedType(semantics)` | yes | yes | Returns the observable referenced by `DESCRIBES_OBSERVABLE_PROPERTY`, used by unary operators. |
| `splitOperators(semantics)` | yes | local only | Returns the underlying described concept and the ordered list of unary operator semantic types. |
| `negated(concept)` | yes | yes | Constructs or retrieves the OWL negation of a deniable concept. |
| `observableType(semantics, acceptTraits)` | partial | yes | Extracts the unique base modelable semantic type. `acceptTraits` is currently not distinguished. |

The client normalizes definitions by removing redundant outer parentheses and caches successful
resolutions. Null and `Nothing` results are not cached by the client, so subsequent attempts can
retry; returned failure diagnostics are preserved. Service resolution caches may retain `Nothing`
until knowledge invalidation. Client configuration constructs `Concept.nothing()` and
`Observable.nothing()` locally; observation builders mark `Nothing` semantics as unusable.
For source-bound explanations, use [document validation](#semantic-document-validation).

## Hierarchy and logical operations

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `is(concept, other)` | yes | yes | Directional subsumption (`concept ⊑ other`), using fast type checks, logical-expression handling, and OWL inference. |
| `operands(target)` | yes | yes | Returns union/intersection operands, or a singleton containing a non-logical target. |
| `children(target)` | yes | yes | Returns direct asserted children. |
| `parents(target)` | yes | yes | Returns direct asserted parents. |
| `parent(target)` | yes | yes | Returns one direct parent or `null`. The current implementation does not enforce the interface's single-parent precondition. |
| `allChildren(target)` | yes | yes | Returns all asserted descendants without including the target; cycle-safe. |
| `allParents(target)` | yes | yes | Returns the transitive asserted ancestor set; cycle-safe. |
| `closure(target)` | yes | yes | Returns inferred descendants from the OWL reasoner. |
| `assertedDistance(from, to)` | yes | yes | Returns shortest asserted parent distance or `-1`. |
| `satisfiable(semantics)` | yes | yes | Asks the DL reasoner whether the concept is satisfiable. |
| `domain(semantics)` | yes | yes | Breadth-first search for the nearest ancestor carrying `SemanticType.DOMAIN`. |
| `applicableObservables(concept)` | yes | yes | Returns restrictions through `APPLIES_TO_PROPERTY`. |
| `leastGeneralCommon(concepts)` | yes | yes | Repeatedly computes the most specific common ancestor; returns `null` if none exists. |

Subsumption is cached on both the service and client. Cache keys include the ordered concept pair
and knowledge revision.

## Semantic distance

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `semanticDistance(target, candidate)` | yes | yes | Computes directional semantic compatibility without an explicit contextual concept. |
| `semanticDistance(target, candidate, context)` | yes | yes | Includes the supplied contextual concept when inherency needs contextual resolution. |
| `resolves(target, candidate, context)` | yes | yes | Returns true for a non-negative semantic distance. |

Distance is computed in these stages:

1. Unary operator stacks must match. If operators exist, their described observables are compared
   recursively.
2. Non-predicate observations must have the same modifier-free core observable. Predicates may be
   resolved by a more specific predicate.
3. The candidate-to-target asserted hierarchy distance is multiplied by 50, making core distance
   dominant in prioritization.
4. Required traits and roles are checked. Contextualized abstract predicates may be replaced through
   the explicit abstract-to-concrete mapping used by the specialized matcher entry point.
5. Direct and inherited inherency are checked directionally. A candidate's inherent type may
   specialize the target's inherent type, but not vice versa.
6. Goal, co-occurrence, causant, caused, adjacency, compresence, and comparison restrictions are
   checked and contribute to distance.
7. When actual `Observable` objects are supplied, observer semantics, contextualization, and
   mediators (unit, currency, or range) are checked. Compatible but distinct mediation contributes
   a small positive cost.

Any incompatible component returns a negative result immediately. Context is part of the cache key,
so a contextual match cannot reuse a result computed for another context.

## Traits, identities, attributes, realms, and roles

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `traits(semantics)` | yes | yes | Union of inherited identities, attributes, and realms. |
| `identities(semantics)` | yes | yes | Inherited identity restrictions. |
| `attributes(semantics)` | yes | yes | Inherited attribute restrictions. |
| `realms(semantics)` | yes | yes | Inherited realm restrictions. |
| `directTraits(semantics)` | yes | yes | Union of directly asserted identity, attribute, and realm restrictions. |
| `directIdentities(semantics)` | yes | yes | Direct identity restrictions. |
| `directAttributes(semantics)` | yes | yes | Direct attribute restrictions. |
| `directRealms(semantics)` | yes | yes | Direct realm restrictions. |
| `hasTrait(semantics, trait)` | yes | yes | True when an inherited trait is equal to or specializes the requested trait. |
| `hasDirectTrait(semantics, trait)` | yes | yes | Tests directly asserted traits using subsumption. |
| `lexicalRoot(trait)` | yes | yes | Walks parents to the base declaration, respecting original-trait metadata. |
| `roles(semantics)` | yes | yes | Inherited role restrictions. |
| `directRoles(semantics)` | yes | yes | Direct role restrictions. |
| `hasRole(semantics, role)` | yes | yes | True when an inherited role is equal to or specializes the requested role. |
| `hasDirectRole(semantics, role)` | yes | yes | Tests directly asserted roles using subsumption. |
| `hasParentRole(semantics, role)` | yes | yes | True when the requested role specializes a role carried by the semantics. |

## Semantic clauses

Each singular clause accessor returns one matching restriction or `null`. If several restrictions
exist, the selected result is arbitrary; callers requiring all values should use a lower-level
plural query.

| Operation | Direct form | Inherited form | OWL property |
| --- | --- | --- | --- |
| inherency | `directInherent` | `inherent` | `IS_INHERENT_TO_PROPERTY` |
| purpose | `directGoal` | `goal` | `HAS_PURPOSE_PROPERTY` |
| co-occurrence | `directCooccurrent` | `cooccurrent` | `OCCURS_DURING_PROPERTY` |
| causant | `directCausant` | `causant` | `HAS_CAUSANT_PROPERTY` |
| caused | `directCaused` | `caused` | `HAS_CAUSED_PROPERTY` |
| adjacency | `directAdjacent` | `adjacent` | `IS_ADJACENT_TO_PROPERTY` |
| compresence | `directCompresent` | `compresent` | `HAS_COMPRESENT_PROPERTY` |
| comparison target | `directRelativeTo` | `relativeTo` | `IS_COMPARED_TO_PROPERTY` |

All listed direct and inherited operations are implemented locally and remotely.

## Relationships, occurrence, and causal semantics

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `relationshipSources(relationship)` | yes | yes | Returns direct and inherited implied-source restrictions. |
| `relationshipSource(relationship)` | yes | yes | Returns one source or `null`; deprecated when multiplicity matters. |
| `relationshipTargets(relationship)` | yes | yes | Returns direct and inherited implied-destination restrictions. |
| `relationshipTarget(relationship)` | yes | yes | Returns one target or `null`; deprecated when multiplicity matters. |
| `occurrent(semantics)` | yes | yes | True for processes and events. |
| `affected(semantics)` | yes | yes | Returns non-internal concepts restricted by `AFFECTS_PROPERTY`. |
| `created(semantics)` | yes | yes | Returns non-internal concepts restricted by `CREATES_PROPERTY`. |
| `affectedOrCreated(semantics)` | yes | yes | Union of affected and created concepts. |
| `affectedBy(affected, affecting)` | yes | yes | Tests affected restrictions, including the described type of an operator-derived quality. |
| `createdBy(created, creating)` | yes | yes | Tests created restrictions and operator-described types; a described type may also match the creating concept itself. |
| `compatible(first, second)` | yes | yes | Checks observational compatibility through core type, inherency, traits, and roles. |
| `contextuallyCompatible(focus, context1, context2)` | yes | yes | Extends compatibility for occurrents that affect the focus or share a compatible inherent context. |

`compatible` is an older boolean compatibility operation. Resolution and prioritization should use
`semanticDistance` when a score is needed.

## Resolving candidates

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `resolving(semantics)` | partial | yes | Produces known generalizations that may resolve the input by varying inherency and semantic modifiers. The implementation retains legacy limitations noted in source. |

## Pattern matching and concretization

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `match(candidate, pattern)` | yes | yes | Syntactic semantic-pattern matching through `SyntacticMatcher`; results and parsed declarations are cached. |
| `match(candidate, pattern, matches)` | partial | partial | Works for successful non-abstract patterns but does not capture abstract generic substitutions. |
| `concretize(pattern, replacements)` | yes | yes | Performs explicit generic-URN substitution from a concept map and resolves the resulting concept or observable. |
| `concretize(pattern, concreteConcepts)` | TODO | TODO | Inference-based substitution is not implemented. |

## Observation reasoning and semantic search

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `computeObservationStrategies(observation, scope)` | yes | yes | Returns matching observation strategies ordered by the observation reasoner. |
| `computeIdentificationStrategies(observable, scope)` | yes | yes | Returns the most specific identification strategy for the substantial observable in scope. |
| `semanticSearch(request)` | partial | partial | JSON endpoint with server-owned sessions, contextual proposals, selection, groups, values and undo; see [rules and extension guide](#assisted-observable-composition). |
| `buildConcept(strategy, scope)` | yes | yes | Replays an `ObservableBuildStrategy` through `SemanticsBuilder` and returns a concept. |
| `buildObservable(strategy, scope)` | yes | yes | Replays an `ObservableBuildStrategy` through `SemanticsBuilder` and returns an observable. |
| `validateDocument(request, scope)` | yes | yes | Validates a parsed namespace or synchronized ontology and returns occurrence-specific diagnostics and revision metadata; see [document validation](#semantic-document-validation). |

Interactive semantic expressions expire ten minutes after their last access.

## Display helpers

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `displayName(semantics)` | yes | yes | Deprecated semantic display name helper. The remote client uses the transported semantics directly. |
| `displayLabel(semantics)` | yes | yes | Deprecated human-readable label helper. |
| `style(concept)` | TODO | TODO | Semantic styling is not implemented. |

## Assisted observable composition

The first consumer is the concept modal in the sibling `klab-ide` project. Its `SemanticComposer`
contains the query field, proposal table, accepted expression and `ObservableCard`. A host supplies
an asynchronous Continue action receiving a validated `Observable`; the component has no knowledge
of digital-twin submission. This lets a forthcoming Worldview view reuse it with a different action.
The Eclipse `klab/.../SearchView.java` informed the interaction, not the implementation or API.

### Request/response contract

`POST /api/v1/semanticSearch` is represented by
`ServicesAPI.REASONER.SEMANTIC_SEARCH` (use that constant, not a hard-coded path).
`ReasonerClient.semanticSearch` posts a JSON `SemanticSearchRequest`; `AssistController` binds its body.

1. Initialize with `searchId = 0`, increasing `requestId`, and mode `TOKEN`. Empty text returns initial
   proposals. Retain the assigned nonzero search ID even if the query text changed while initializing.
2. `TOKEN` searches without modifying accepted components. Results contain identifiers, names,
   descriptions and typed operators; they are filtered against the current expression on the server.
3. `SELECT` sends `selectedMatchId` and the `matchesRequestId` of the response being selected.
   Stale or invented choices are rejected. Literal operands use `VALUE` with `queryString`.
4. `UNDO`, `OPEN_SCOPE` and `CLOSE_SCOPE` edit accepted components. Rejected edits preserve the
   previous expression. Undo replays accepted tokens, including reopening a closed group.
5. Every response reports the declaration, styled tokens, parenthesis depth and permitted UI actions.
   `observable` is non-null only for a complete, resolvable, satisfiable expression matching the
   initial result-type constraint. A UI must also check response errors before enabling Continue.
6. Cancel with `cancelSearch = true` and the session ID. Idle sessions expire after ten minutes;
   the cache is bounded to 1,000 sessions and invalidated when Reasoner knowledge caches reset.
   Unknown nonzero IDs return an expiration error and ID zero, never a silently replaced expression.

Each session serializes server edits and rejects non-increasing request IDs. The IDE serializes calls
on a background executor, debounces input, suppresses obsolete query responses and cancels its session
on disposal. No HTTP request or semantic construction runs on the JavaFX application thread.

### Validation and current coverage

`SemanticSearchSession` is a separate replayable state machine. The earlier graph-based
`SemanticExpression` contains incomplete role collection, scope and group handling and is not used
by the new endpoint. It remains available to existing callers.

The composer reads unary and clause operand restrictions from the existing `ObservableValidator`
rule tables, which also feed `KimValidator` and `KimOntologyVisitor`. Language-enum restrictions
supply cases without a validator rule. For example, `during` proposes events, using the shared
validator's narrower rule instead of the older lexical enum's event/process union. Binary operands
must have the same fundamental category (`follows` requires events). Prefix predicates remain
incomplete until an observable can be built. Completed candidates go through the existing Resources
parser and Reasoner declaration path, then OWL satisfiability. This is bounded, conservative proposal
validation, not a claim that every worldview-dependent compatibility rule has been implemented.

Implemented paths include named concepts and predicate prefixes, explicit `each` qualifiers, single-operand unary operators,
semantic clauses, binary operators, explicit groups, undo, and value operators with numeric, boolean,
or quoted-text operands. All completed forms still depend on the current parser and lowering path:
a form that does not resolve is rejected instead of being offered as a valid completion. Search text
is treated as plain prefix text, not Lucene query syntax. Shared parenthesis normalization removes
only a group enclosing the whole expression, preserving `(A) or (B)` and quoted parentheses.

Still to implement:

- Multi-operand unary operators (`ratio of ... to ...`, proportion, percentage, value-over).
- Units/currencies and distributed units; `where` conditions and richer literal editors.
- Full trait-base conflicts and remaining worldview-sensitive rules as shared validator contracts,
  including better explanations of why a proposal was excluded. Predicate applicability and
  disjointness are already checked through the reasoner-backed support.
- Context-aware proposal ranking, personal defaults/history and search-result pagination. Initial
  searches currently examine a bounded Lucene candidate window, so this is not an exhaustive browser.
- An ontology-independent completion policy for concept-only editing in the Worldview view. This
  component currently returns observables; bare predicates are intermediate components.

Unsupported operations are withheld rather than represented as successfully implemented paths.

### Rule catalog and extension points

There are three distinct decisions: whether a token may be entered, whether the current expression
is complete, and whether its resulting observable can be submitted in a particular context. Keep
these decisions separate when adding rules. An incomplete prefix is not a validation error, and a
satisfiable expression is not necessarily submit-ready.

| Rule / current behavior | Source of truth / implementation | What remains to extend |
| --- | --- | --- |
| Root accepts observable heads, predicate prefixes, unary operators and groups | `SemanticScope.root()`; `SemanticSearchSession.replay()` | Concept-only completion for ontology editing |
| `each` starts an expression or follows an applicable semantic clause, including `of`; it is never repeated in the same operand position | `ObservableValidator.conceptAttributeRules()` (`EACH` requires `COUNTABLE`); session qualifier replay | Additional concept attributes; explicit grammar-position rules for future qualifiers |
| Predicate prefixes preserve the enclosing operand category; disjoint predicates are excluded and completed applications must satisfy `applies to` | `replay()`, `complete()`, `OWLSemanticClauseSupport` | Full base-trait uniqueness and remaining incompatible-role rules |
| Unary operand categories and result types (dependent results are excluded in substantial clauses) | `ObservableValidator.unaryOperatorRules()`, with `UnarySemanticOperator.allowedOperandTypes` as fallback | Second operands, comparison clauses and operator-specific worldview constraints |
| Clause argument categories | `ObservableValidator.clauseRules()`, with `SemanticLexicalElement.argument` as fallback | Shared rules for every clause; compatibility with the head and already-bound roles |
| Direct/inherited clause fillers constrain complete operands by OWL subsumption | `OWLSemanticClauseSupport`; session trial replay | Broader diagnostics and isolated validation of unsynchronized ontology edits |
| Clause applicability and duplicate roles in a lexical frame | `SemanticLexicalElement.applicable`; `Frame.used` | Additional application-language constraints beyond ontology restriction bounds |
| `during` requires an event | Shared `ObservableValidator` clause rule | Any broader event/process policy must first be decided in that shared contract |
| `and` / `or` require the same fundamental operand category; `follows` requires events | `replay()` binary branch, consistent with `ObservableValidator.logicalOperatorRules()` for logical operators | Logical normalization, subsumption-aware compatibility, event ordering details |
| A group opens only in an operand position (never directly after another opening group), closes only when complete, and must fit its enclosing operand scope | `replay()` group branches; `describe()` depth/action flags | Predicate groups and richer nested grammar contexts |
| Value operators require a quality; `by` / `down to` currently allow class or countable operands | `replay()` value-operator branch; `ValueOperator.nArguments` | Abstract-class distinctions, unit/range compatibility, categorical operands, aggregation rules and `where` |
| Numeric, boolean and quoted text literals cannot inject additional expression tokens | `literal()`; 256 input characters and bounded decimal expansion | Typed literal editors and detailed type/operator compatibility beyond parser rejection |
| No completed observable until all groups/operands are complete, parsing/declaration succeeds and OWL reports satisfiable | `complete()`, `resolve()`, final `replay()` result | Full shared visitor validation and worldview-sensitive validation of the whole expression |
| Initial result-type restrictions apply to completed root expressions; initial match-type restrictions filter proposals | Session constructor, `replay()`, `handle()` | Distinguishing requested output category from operand search filters in a richer API |
| Raw qualities/processes need a context observation; collectives need an observer's perceived extent | `ObservableSubmission.builder()` | Additional submission policies belong here or in the host, not in concept-selection rules |
| Raw substantials become collectives before creating an unresolved observation | `ObservableSubmission.builder()` and `Observable.Builder.collective(true)` | Other host actions may retain individual semantics; they must not inherit this policy implicitly |

These are the implemented rules, not an exhaustive semantic contract. In particular, the old
`SemanticScope.Constraint.compatibleWith()` and `withDifferentBaseTraitOf()` are still stubs; the
new composer does not rely on those methods as evidence of compatibility. It relies on the
completed declaration/Reasoner check and may still admit incomplete prefixes that cannot be
completed. OWL satisfiability alone does not prove all application-language constraints.

#### Explicit collectives and keyboard scopes

`each` is a qualifier, not automatic observation-submission promotion. These distinct expressions
are supported by the current grammar and retained in the accepted declaration:

```text
each example:Tree
example:Height of each example:Tree
example:Height of (each example:Tree)
```

The names above are illustrative and require corresponding worldview concepts. At an expression
start, the completed expression must resolve to a collective countable observable. Following `of`,
`each` narrows the inherent operand to a countable concept; it does not make the outer quality
collective. Predicate prefixes remain possible before that head. The existing Resources adapter
carries the clause's collective flag onto its operand, and the Reasoner semantic builder preserves
that flag. Undo removes the head and qualifier independently. The grammar requires grouping when
`each` is used inside a unary or logical operand rather than at a concept-expression start.

The IDE renders accepted tokens with `Theme.semanticExpression`, using the k.IM colors and font
styles even before the expression is complete. Typing `(` or `)` in the query performs the same
operation as the buttons when the Reasoner permits it. Structural characters are consumed before
text editing, so they never enter the search query or trigger an extra search. They remain ordinary
text when a literal operand is expected. Backspace edits nonempty query text normally; on an empty
query it undoes one accepted component per physical key press. Held keys cannot queue repeated
scope edits. Consecutive opening groups are also rejected on the server, including selections from
the proposal table. Separate closing-key presses can close legitimately nested groups.

#### Ontology clause bounds and card provenance

`SemanticSearchSession` uses one `SemanticClauseSupport` source for both candidate validation and
card explanations. The deployed Reasoner supplies `OWLSemanticClauseSupport`: its role-to-property
map covers `of`, `for`, `with`, `caused by`, `causing`, `adjacent to`, `during`, and relationship
source/target clauses. Extend this map when introducing another ontology-backed clause.

For each owner concept, the support visits direct superclass/equivalent restrictions, asserted
ancestors and inferred superclasses. Every existing filler is a bound: the completed clause operand
must be an inferred subclass of that filler (equality is accepted). All bounds must hold. Union
fillers remain alternatives inside one bound; intersections and multiple restrictions are never
flattened into an arbitrary first concept. Superclass unions are not traversed as though every
branch were inherited. Collective flags are stripped for subsumption only.

The entire operand is checked, including unary expressions and closed groups. Proposal trial replay
and accepted edits use this same check; undo restores the previous scope. Incomplete predicate
prefixes may remain visible until a head completes them. Satisfiability of the resulting expression
is still required, but is not a substitute for checking specialization of the existing fillers.
The [document validation API](#semantic-document-validation) also uses these bounds to report
source-bound clause errors against loaded knowledge. Logical `and`/`or`/`follows` are expression connectors rather
than object-property clauses and retain their separate category and satisfiability checks.

The response includes `currentConcept` even when a clause is pending, plus `clauses` carrying role,
origin (`inherited`), optional named filler and styled code (also for anonymous union/intersection
fillers). The IDE card renders this snapshot using `Theme.semanticExpression` without synchronous
remote calls. A label icon denotes a direct restriction; a merge icon denotes an inherited one,
with accessible text and tooltips. Both are relative to the current composed concept. A bare
predicate can be shown and qualified but is not returned as a ready observable without inherency.

The generic support implementation uses the existing single-filler Reasoner getters for controlled
clients/tests; production sessions use the OWL implementation to preserve all bounds. Session-local
restriction snapshots are discarded with the search session on knowledge invalidation.

#### Relationship endpoints, predicate domains and substantial operands

`linking` and `to` form one indivisible clause. After a source has been entered, the
expression remains incomplete until its target is supplied. Both endpoints must be
substantials and specialize every inherited `links` bound. For example, a declaration
`StreamConnection links StreamJunction to StreamJunction` does not admit a generic
`Freshwater Region` endpoint unless that expression specializes `StreamJunction`.

Predicates may qualify a substantial operand, but entering one must not remove the
substantial from completion proposals. Predicates disjoint with those already entered
are excluded. Unary operators are filtered by their resulting observation type as well
as their operand type: operators producing a quality or process are not proposed in a
clause requiring a substantial.

`applies to` constrains the target of predicate application and adds a domain constraint
to dependent inherency. Alternatives inside one union are accepted as alternatives;
independent inherited bounds must all hold. A free dependent may remain without an
explicit inherent; an inherent supplied later must satisfy its applicable domain.

#### Adding or strengthening a rule

1. For a rule that applies to both authored k.IM and UI composition, add or refine the declarative
   rule in [`ObservableValidator`](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/ObservableValidator.java).
   Its rule tables are consumed by the document validators. The composer already reads unary and
   clause operand categories from them; other new rule families need an explicit composer consumer.
2. For an incremental-only rule (for example, which second operand is pending), implement it in
   [`SemanticSearchSession`](../klab.core.services/src/main/java/org/integratedmodelling/klab/indexing/SemanticSearchSession.java).
   Update both proposal trial-validation and selected-token replay through the same path. Do not
   add an independent JavaFX semantic rule or trust the client to send a correctly typed token.
3. Define the incomplete state and its exit conditions explicitly. Decide whether the new token is
   an operand, prefix, suffix, clause, group, or literal, and how its scope is restored by undo. For
   multi-operand unary operators, represent the pending second operand before exposing the operator.
4. Add positive and negative cases to
   [`ObservableValidatorTest`](../klab.core.api/src/test/java/org/integratedmodelling/klab/api/lang/kim/ObservableValidatorTest.java)
   for shared rules, and to
   [`SemanticSearchSessionTest`](../klab.core.services/src/test/java/org/integratedmodelling/klab/indexing/SemanticSearchSessionTest.java)
   for proposals, selection, rejection without mutation, nested scopes and undo. Mocked Reasoner
   acceptance is a state-machine test, not proof of real ontology semantics.
5. Verify the declaration against the actual grammar, Resources parser, Reasoner lowering and a
   loaded worldview. Add a live integration case before claiming support for a previously unfinished
   language path. A declaration appearing in an enum is insufficient evidence that lowering works.
6. If wire fields change, extend
   [`SemanticSearchTransportTest`](../klab.core.common/src/test/java/org/integratedmodelling/common/services/client/SemanticSearchTransportTest.java).
   Update the response action flags and generic UI only as needed to represent the new state.

For rule families not yet represented by the shared validator, prefer adding an immutable rule
record there over another switch in the UI. Rules requiring ontology access should accept an
explicit Reasoner/worldview context and return diagnostics; do not turn unfinished predicates into
permissive `true` defaults. Keep parser diagnostics, logical inconsistency and missing observation
context distinguishable so users can understand how to continue.

### Digital-twin action

The modal captures the selected digital twin and rejects continuation if it changed. If there is
no twin, it asks the existing controller to obtain a default context. `ModelerImpl.observe` accepts
a raw `Observable` and uses `ObservableSubmission` to construct an unresolved observation:

- Qualities (and other dependent observations, currently processes) require a context observation
  and use its geometry.
- Substantials, including enumerable relationships, are converted through the semantic builder to
  collective observables without mutating the selected observable.
- Predicates require a substantial or quality inherent. A quality inherent remains singular and uses mandatory context-observation geometry. For a substantial inherent, submission replaces a singular inherent with its collective form through `Observable.Builder.of(inherent.collective())`, preserving the predicate and the composed expression. Predicates already qualified with a collective are retained. Predicates over substantials use the observer's perceived extent.
- Collectives use the selected observer's `PERCEIVES` geometry. Missing observer/geometry is an
  error; the observer's own `OCCUPIES` geometry is never used as a fallback.

The resulting observation follows the existing Runtime submission, progress, cancellation identity,
notification and graph-update route. No identity is fabricated for an individual substantial.

### Verification

Focused tests cover state transitions and invalid/stale edits with a controlled Reasoner, shared
operand rules, literal bounds, balanced normalization, a real loopback HTTP JSON round trip through
`ReasonerClient`, and unresolved-observation geometry/collective adaptation. These do not establish
live worldview semantic behavior or a manually rendered JavaFX workflow. A small real HermiT ontology additionally tests direct/equivalent and inherited bounds, including unions; JavaFX tests check card structure and origin tooltips. A live acceptance pass should compose
a worldview quality, add a context clause, exercise nested groups/undo, then submit it in a twin with
a context observation; repeat with a substantial and an observer with a perceived extent.

## Semantic document validation

`Reasoner.validateDocument(SemanticValidationRequest, Scope)` and
`POST /api/v1/validate/document` validate parsed namespace and ontology snapshots.
`ReasonerClient` supplies the HTTP transport. The controller requires an authorized
scope. This API does not publish ontology declarations or change document source.

The request contains exactly one `KimNamespace` or `KimOntology`, an opaque editor
document version, and an optional expected reasoner knowledge revision (`-1` accepts
the current revision). Sending parsed beans preserves occurrence offsets; sending
only canonical expression strings would lose them.

The response contains semantic notifications, document URN/version, the SHA-256
hash of the exact source text, the evaluated knowledge revision, and a status:

| Status | Meaning |
| --- | --- |
| `COMPLETE` | The semantic pass completed; inspect notifications for errors. |
| `SYNTAX_ERRORS` | Fix parsing errors before requesting semantic validation. |
| `UNAVAILABLE` | No usable reasoner/Resources service, or unsupported endpoint. |
| `STALE_KNOWLEDGE` | Synchronize the ontology or refresh the expected knowledge revision, then retry. |
| `FAILED` | The semantic pass could not finish; this is not a successful validation. |

`response.valid()` requires `COMPLETE` and no error notifications. Semantic
notifications are copied with `Notification.LexicalContext` referring to each
source occurrence. Cached `Concept` instances do not receive editor locations.
Relationship endpoint violations identify the offending endpoint range and the
declared bound. The pass also checks clause bounds, applicability, predicate
disjointness, resolution failures and satisfiability.

### Client and Resources orchestration

`DocumentSemanticValidation.validate(request, scope)` in `klab.core.common` uses
the scope's selected reasoner and returns `UNAVAILABLE` when none is accessible.
It can be called by an editor or a Resources caller without making saving depend
on reasoner availability. It is synchronous: schedule it off the UI/save thread.

```java
var request = SemanticValidationRequest.of(parsedDocument, editorVersion);
request.setKnowledgeRevision(reasonerCapabilities.getKnowledgeRevision());
var response = DocumentSemanticValidation.validate(request, userScope);
// Back on the UI thread, compare with the CURRENT document and knowledge revision.
if (response.matches(currentRequest, currentKnowledgeRevision)) {
  // COMPLETE: replace only markers owned by semantic validation.
  // Other statuses: show pending/unavailable/failed validation, not a clean bill of health.
}
```

Trigger after opening a parsed document and after receiving the parsed result of
a save. Maintain semantic markers separately from parser markers. Ignore obsolete
responses; retry pending ontology checks after the reasoner reports a new knowledge
revision. In `klab-ide`, `WorkspaceEditor` now installs these hooks through
`WorkspaceSemanticValidation`. It validates loaded workspace documents, opened editors and parsed
save results on a background worker. Semantic tree badges aggregate document errors up to projects;
editor status labels distinguish pending, unavailable and synchronization states. A separate
`klab-semantics` Monaco marker owner preserves parser and LSP diagnostics. Editing clears semantic
markers until saved text is validated. Revision/availability checks run every 30 seconds while the
workspace is attached, and superseded or detached-workspace callbacks are discarded.

### Ontology synchronization boundary

The reasoner records source hashes during full knowledge loading and ontology
updates, and retains their source-bound compilation diagnostics for retrieval.
An ontology validation request must match its loaded source exactly.
Knowledge updates and validation are serialized to avoid observing a partly
reloaded ontology. A newer edit is never evaluated against the previous declaration
and never installed merely to validate it. Unsaved ontology validation before
knowledge synchronization requires a future isolated ontology staging mechanism.
Namespace expressions can be validated against the currently loaded worldview;
normal expression resolution may materialize derived semantic concepts.

### Validation verification

Regression tests cover client HTTP transport and lexical ranges, missing-reasoner handling,
document/knowledge revision matching, endpoint and inherency violations, valid endpoint correction,
retained ontology compilation diagnostics, and authorized/malformed HTTP requests. These tests do
not constitute a live connected-worldview UI acceptance test. The sibling IDE tests additionally
cover asynchronous save supersession, missing-service recovery, knowledge revisions and disposal;
Monaco bridge tests cover marker ownership and rejection of late markers for changed source.

## Administrative operations

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `loadKnowledge(worldview, scope)` | yes | yes | Replaces the loaded worldview, rebuilds the OWL manager and HermiT reasoner, loads ordered ontologies and strategies, flushes inference, and checks consistency. |
| `updateKnowledge(changes, scope)` | yes | yes | Applies ordered resource changes, refreshes affected knowledge, flushes inference, and checks consistency. |
| `defineConcept(statement, scope)` | yes | yes | Declares one concept using already known concepts; declaration order remains significant. |
| `exportNamespace(namespace, directory)` | yes | local only | Serializes an ontology into a service-local directory. A remote service cannot write to a client-local path. |
| `shutdown()` | yes | service lifecycle | Invalidates caches, disposes HermiT, and clears OWL managers, ontologies, mappings, and singleton concepts. |

Both load and update increment the knowledge revision and invalidate semantic caches. Update enters
maintenance mode while changes are applied. A consistency failure is reflected in capabilities and
recorded as a service advisory.

## Caching

All reasoner caches use Caffeine and are safe for concurrent reads.

### Service caches

| Cache | Maximum size / expiry | Key information |
| --- | --- | --- |
| concept definitions | 5,000 | normalized definition |
| observable definitions | 5,000 | normalized definition |
| subsumption | 20,000 | knowledge revision and ordered concept pair |
| semantic distance | 10,000 | knowledge revision, target, candidate, and optional context |
| asserted distance | 20,000 | knowledge revision and ordered hierarchy pair |
| parsed syntactic declarations | 5,000 | semantic URN |
| syntactic matches | 10,000 | ordered candidate/pattern pair |
| semantic search sessions | ten minutes after access | search ID |

### Client caches

| Cache | Maximum size | Key information |
| --- | --- | --- |
| concept definitions | 2,000 | normalized definition |
| observable definitions | 2,000 | normalized definition |
| subsumption | 20,000 | server knowledge revision and ordered concept pair |
| semantic distance | 10,000 | server knowledge revision, target, candidate, and optional context |

The client clears caches after administrative load/update calls and whenever refreshed capabilities
report a different knowledge revision. Observable-specific observer, contextualization, and mediator
checks are performed after retrieving the cached concept-level distance.

## Error and transport behavior

Controller endpoints that accept concept arrays validate arity and reject null concepts before
dispatch. Operations with one semantic result may legitimately return `null` only when the
corresponding restriction or ancestor does not exist. Unsupported operations throw explicitly;
they must not silently return `false`, `0`, an empty collection, or `null`.

## Remaining implementation gaps

- Generic substitution capture for abstract semantic patterns.
- Inference-based concretization from a collection of concrete concepts.
- Contextual role inference (`rolesFor`), implied role selection (`impliedRole`) and
  implied role closure (`impliedRoles`).
- Semantic styling through the general `style` API (the composer's styled tokens are separate).
- Remote transport of split-operator results and namespace exports.
- Reasoner asset retrieval.

Composer-specific gaps are listed under [current coverage](#validation-and-current-coverage).
Ontology validation before synchronization requires isolated staging; see the
[ontology synchronization boundary](#ontology-synchronization-boundary).

## Source map

- API: [Reasoner.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/Reasoner.java)
- Local implementation: [ReasonerService.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java)
- Semantic distance: [SemanticMatcher.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/SemanticMatcher.java)
- Syntactic matching: [SyntacticMatcher.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/SyntacticMatcher.java)
- OWL integration: [OWL.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWL.java)
- Remote client: [ReasonerClient.java](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ReasonerClient.java)
- HTTP controller: [ReasonerController.java](../klab.services.reasoner.server/src/main/java/org/integratedmodelling/klab/services/reasoner/controllers/ReasonerController.java)
- Composer session: [SemanticSearchSession.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/indexing/SemanticSearchSession.java)
- Clause bounds: [OWLSemanticClauseSupport.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWLSemanticClauseSupport.java)
- Document validator: [DocumentSemanticValidator.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/DocumentSemanticValidator.java)
- Validation request: [SemanticValidationRequest.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/reasoner/objects/SemanticValidationRequest.java)
- Validation response: [SemanticValidationResponse.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/reasoner/objects/SemanticValidationResponse.java)
- Client orchestration: [DocumentSemanticValidation.java](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/DocumentSemanticValidation.java)
