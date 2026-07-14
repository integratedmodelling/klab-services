# k.LAB reasoning service

This document describes the semantic API implemented by `ReasonerService`, its remote facade in
`ReasonerClient`, and the administrative lifecycle of the reasoner. The API contract is defined by
`org.integratedmodelling.klab.api.services.Reasoner` and `Reasoner.Admin`.

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

Resolution caches store normalized definitions after removing redundant outer parentheses. A failed
resolution is cached as `Nothing` so malformed definitions do not repeatedly trigger parsing and
network traffic.

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
| `semanticSearch(request)` | yes | local only | Maintains an expiring interactive `SemanticExpression` supporting token search, scopes, and undo. |
| `buildConcept(strategy, scope)` | yes | local only | Replays an `ObservableBuildStrategy` through `SemanticsBuilder` and returns a concept. |
| `buildObservable(strategy, scope)` | yes | local only | Replays an `ObservableBuildStrategy` through `SemanticsBuilder` and returns an observable. |

Interactive semantic expressions expire ten minutes after their last access.

## Display helpers

| Operation | Local | Remote | Behavior |
| --- | --- | --- | --- |
| `displayName(semantics)` | yes | yes | Deprecated semantic display name helper. The remote client uses the transported semantics directly. |
| `displayLabel(semantics)` | yes | yes | Deprecated human-readable label helper. |
| `style(concept)` | TODO | TODO | Semantic styling is not implemented. |

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

## TODO: generic substitution capture

TODO.

## TODO: inference-based concretization

TODO.

## TODO: contextual role inference (`rolesFor`)

TODO.

## TODO: implied role selection (`impliedRole`)

TODO.

## TODO: implied role closure (`impliedRoles`)

TODO.

## TODO: semantic styling (`style`)

TODO.

## TODO: remote split-operator transport

TODO.

## TODO: remote semantic search

TODO.

## TODO: remote build strategies

TODO.

## TODO: remote namespace export

TODO.

## TODO: reasoner asset retrieval

TODO.

## Source map

- API: `klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/Reasoner.java`
- Local implementation: `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java`
- Semantic distance: `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/SemanticMatcher.java`
- Syntactic matching: `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/SyntacticMatcher.java`
- OWL integration: `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWL.java`
- Remote client: `klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ReasonerClient.java`
- HTTP controller: `klab.services.reasoner.server/src/main/java/org/integratedmodelling/klab/services/reasoner/controllers/ReasonerController.java`
