# Connection instantiation and acknowledgement

Status: **CONNECTION is complete and accepted**, confirmed by the maintainer on
2026-09-19 following live testing. No connection staging acceptance work remains.

This reference complements [observable semantics](OBSERVABLES.md#16-connecting-substantials),
[observation strategies](OBSERVATION.md), and
[relationship persistence](KNOWLEDGE_GRAPH.md#relationship-observations).

## Semantic contract

CONNECTION instantiates collective RELATIONSHIP or BOND observables. Each resulting
individual is a contextualized substantial with arity two: it connects two distinct
individual substantials in the current context. It has its own identity, geometry,
provenance, and cohort membership. A relationship observation is not a knowledge-graph
edge. Bonds are represented semantically as `RELATIONSHIP + BIDIRECTIONAL`.

A free request uses the current observer's perceived geometry. Declared endpoint
restrictions constrain participant semantics; `linking ... to ...` specializes those
restrictions through semantic composition and validation. See [reasoning](REASONING.md).

## Endpoint resolution and model execution

The default Tier-0 `relationships.direct` strategy resolves the collective source and
target semantics, then discovers a connector model through `observe`, supplying the
resolved collections as named `source` and `target` inputs. Preparing mandatory
endpoints does not decompose the requested relationship's meaning.

Identical endpoint semantics share one collective resolution and instantiation within
the strategy. Both named ports remain available and bind to the same runtime producer
object. Fiat identities therefore do not cause duplicate populations simply because
the same substantial type occurs at both ends.

The endpoint producers finish their required individual resolution before the connector
runs. Member enumeration includes durable and transaction-local cohort members,
including members staged in parent transactions. It filters by requested support and
preserves point and line geometries during spatial intersection. Missing or incomplete
endpoint resolution is an error, not evidence of an empty collection.

The selected model chooses the pairs and number of relationships. Runtime does not
construct a Cartesian product or invent reverse connections. A model may legitimately
produce zero instances. Endpoint coverage alone does not satisfy the connection request.

## Creating and acknowledging instances

Contextualizers emit individuals through:

```java
builder.relationship(name, observable, geometry, identity, source, target);
```

Direct construction also supports `Observation.Builder.between(...)`.
`Observation.getParticipants()` carries creation-time references, including provisional
transaction-local endpoints. Runtime validates arity, participant existence, and
substantial semantics before acknowledgement.

Each new relationship is acknowledged individually. Its scope retains the producing
collective for registration and uses `ContextScope.between(source, target)` to communicate
the endpoints to resolution and contextualization. The producing model's lexical
constraints are preserved. Runtime waits for these lifecycle obligations before reporting
completion. No explanatory model is a valid acknowledgement outcome; execution and
infrastructure failures propagate and fail the enclosing transaction.

## Persistence and queries

Relationships are Observation nodes hosted by Cohorts through `HAS_MEMBER`.
Directed relationships have outgoing `HAS_RELATIONSHIP_SOURCE` and
`HAS_RELATIONSHIP_TARGET` edges to their respective participants. Bonds instead have
two unordered `HAS_RELATIONSHIP_PARTICIPANT` edges. Participant edges are persisted
with the relationship in the enclosing transaction and do not establish deletion ownership.
An existing relationship identity cannot be rebound to another pair.

`ContextScope.getRelationshipParticipants` reads transaction-local and durable participants;
directed results are ordered source then target, while bond results have no semantic order.
`getOutgoingRelationshipsOf` and `getIncomingRelationshipsOf` return relationship
observations and include bonds in both queries, with deduplication and visibility checks.
The CONNECTION activity uses the typed CONNECTED effect; individual acknowledgement
has its own lifecycle and provenance.

## Testing generator and verification

The `klab.component.generators` function `klab.generators.random.relationships` samples
a configurable percentage of incoming endpoints, chooses pairs, and creates randomized
geometry. For example:

```kim
model each earth:StreamConnection
    using klab.generators.random.relationships(fraction = 20, seed = 42);
```

`fraction` is a percentage from 0 to 100, defaulting to 20. `seed` controls reproducibility
for the same input population. `shape` accepts `lines` (default), `points`, or `polygons`;
`vertices` controls polygon generation. Sampling and pair selection can legitimately
yield no connections for small populations or when no distinct pair is available.

Regression coverage includes shared endpoint resolution and runtime bindings, parent-transaction
member visibility, point/line support, contextualizer invocation, participant transport,
directed and bond graph structure, and individual acknowledgement with producer constraints.
The maintainer confirmed the live connection test succeeds. General strategy composition
and persisted composite-plan replay remain separate work, not connection staging tasks.
