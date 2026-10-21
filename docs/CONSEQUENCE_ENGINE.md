# Consequence engine — design draft

This document collects the current design for collaborator feedback. The consequence
engine is **future runtime work**. Declaration parsing, OWL restrictions, activity
names and source validation do not establish that these consequences execute today.
This is the authoritative home for the evolving design; reference guides and the
agent context pack should summarize it and link here.

Here, a **commit** means an observation/knowledge transaction commit in a digital
twin, not a Git commit. Consequences operate on observations and their scientific
meaning, not source-control changes.

## Agreed semantic contract

The engine runs after each observation/knowledge commit. Its responsibilities are:

| Consequence | Intended behavior |
| --- | --- |
| Configuration DETECTION | Recognize an emergent configuration observation from detected ingredients |
| Change processes | Resolve change processes for qualities when their context becomes occurrent |
| Conferred roles | Apply `confer` axioms for roles conferred by processes or relationships |
| Role implications | Follow implications of roles |
| Optional uncertainty | Optionally resolve `uncertainty of` qualities |

Consequences may cause further consequences through the models selected to resolve
them. The design is not a single pass. Existing resolution idempotency and reuse
are the primary mechanisms for avoiding duplicate work; the engine should not
introduce a separate competing resolution state machine.

### Agreed effective-head interpretation of `of`

Interpret binary `of` by the effective head kind after alias resolution and unary
derivation:

| Effective head | Language meaning and normalized relation |
| --- | --- |
| Quality or process | Native inherency, using `odo:isInherentTo` |
| Predicate | Applicability, retaining shared `odo:isInherentTo` contextualization mechanics |
| Configuration | Emergence shorthand, using `odo:emergesFrom`, not native inherency |

A configuration's application target is stated explicitly with `applies to`
(`odo:appliesTo`). `Network of Relationship` therefore identifies emergence
ingredients; it does not name the Network's application target. The configuration
rule is agreed language semantics, while the runtime that detects the resulting
observation remains future work. Independent subject/agent/event heads cannot take
binary `of`; unary forms containing that token retain their own derivation rules.

### Configuration lifecycle

DETECTION produces the emergent configuration observation. Models never instantiate
configurations: `model each Configuration` is illegal. `model Configuration` is a
legitimate request to resolve an observation **after emergence**. That model does
not create the configuration observation.

Placement depends on the kind of detected ingredients:

- Configuration detected from the (com)presence of dependents is placed under those
  dependents' bearer.
- Configuration detected from substantials belongs to root-level cohorts, like
  substantials.
- Relationship emergence yields Network configurations. Resolving an emerged Network
  creates the scientific network model, a distinct step from detecting its existence.

Different-bearer dependent ingredients need an explicit design decision. This draft
does not merge bearers, pick one arbitrarily, discard ingredients or infer a new
inherency relationship to make detection possible.

`model Quality of each Bearer` distributes the bearer and is distinct from attempting
to instantiate a configuration. Aliases and unary-derived expressions must be checked
by their effective head kind, rather than by surface keywords or stale type flags.

### Containment remains a separate observational contract

Observational containment is neither spatial enclosure nor physical parthood.
`A contains B` and `B contained into A` normalize to the same A-to-B authored contract.
Worldview declaration `within` specializes that contract without changing native
inherency. A successfully empty collective is legal; unresolved or failed discovery
is not successful emptiness. No member, geometry, coverage or completed detection is
entailed merely by declaring containment.

The marked minimum-zero OWL restriction is an asserted declaration carrier, not an
entailment-based permission test. See the
[containment contract](OBSERVABLES.md#observational-containment-declarations) and
[declaration/execution boundary](OBSERVATION.md#observational-containment-declaration-support-versus-contextual-execution).
Neither containment nor emergence is a shortcut for waiving a dependent's native
bearer, identity obligations or explicit abstractness.

### Execution choice and recursive consequences

When a consequence requests resolution, normal strategy precedence still applies.
A successful independent Tier-0 precomputed strategy must win before expensive
Tier-1+ recursive discovery/computation. Preserve coverage-aware partial fulfillment;
only outstanding support may require another route. Declaration legality and the
chosen execution route remain separate.

Idempotency prevents repeated execution of an identical observation/request. Global
termination additionally requires bounded generation of distinct observations and
requests. Verify that invariant against k.LAB's identity, scope and revision rules;
it is not a claim that the current resolution contract fails. No scheduling,
fixed-point or work-queue algorithm is mandated by this document.

## Current support and implementation boundary

| Component | Observed source support | What this does not establish |
| --- | --- | --- |
| Activity API | [Contextualization.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/Contextualization.java) defines DETECTION and maps configuration semantics to it | A working post-commit detector, placement engine or separate detection/resolution scheduler |
| Worldview compilation | [WorldviewDeclarationSupport.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDeclarationSupport.java) compiles explicit emergence triggers to `odo:emergesFrom` | Automatic observation creation or completed runtime trigger evaluation |
| Trigger registration | [ReasonerService.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java) registers emergence declarations through `registerEmergent` | A successful detection consequence |
| Emergence runtime | The same service's `emergentResolvables` currently returns an empty map; `Emergence.makeObservationCatalog` has no active catalog population | End-to-end detection, placement, updates or network construction |
| Model validation candidate | [KimNamespaceVisitor.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/language/KimNamespaceVisitor.java) and [DocumentSemanticValidator.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/DocumentSemanticValidator.java) contain the focused `model each Configuration` rejection | A runtime detector; passing-test status must come from the implementation report |
| Transactions and reuse | [Knowledge graph transactions](KNOWLEDGE_GRAPH.md#semantic-attribution-transactions) and [resolution state](RESOLUTION.md#11-reentrancy-and-concurrency-risks) describe existing identity, isolation and commit behavior | A complete consequence-engine integration or general committed semantic-plan catalog |

The existing resolution guide documents attempt isolation and process-local
coalescing of concurrent singular-substantial submissions. It also identifies the
missing atomic publication of reusable successful semantic plans. Reuse and
idempotency integration must therefore be verified for each consequence category;
the design must not equate one existing coalescing path with complete engine support.

No implementation completeness claim is made here for change-process triggering,
role conferral, role implications or optional uncertainty resolution. Their exact
runtime entry points and dependencies still require a targeted audit.

## Worked examples and counterexamples

**Terrain-like pattern.** After committed Elevation, Slope and Aspect observations
provide the required (com)presence under a common bearer, DETECTION may recognize the
configuration there according to its authored emergence rule. A later configuration
model resolves that observation. Merely parsing an `emerges from` clause does neither.
This example does not decide whether a particular rule requires all ingredients or
one boolean alternative; preserve the authored boolean contract.

**Stream network.** Detected StreamConnection relationships may support a Network
configuration. Its observation belongs in a root-level cohort; a network model is
constructed when that observation is resolved. Declaring Region containment alone
does not discover StreamConnections, prove their geometry or create the network.

**Occurrent transition.** A quality's context becoming occurrent can request its
change process. That resolution may commit observations that cause another
consequence. Repeated notification of the same completed request should reuse its
result under the applicable identity/scope/revision rules, rather than create another
observation simply because the callback ran again.

**Role and uncertainty.** A committed process or relationship may confer a role;
that role may imply another consequence. Optional uncertainty resolution for a
quality is another ordinary resolution request. Failure, absence of a suitable
model and successful empty membership must retain their distinct outcomes.

## Integration proposals and prerequisites to verify

These are design considerations, not a promised implementation schedule:

1. Identify the authoritative successful observation/knowledge commit boundary and
   its durable notification/replay semantics. Consequences must not inspect a
   speculative prefix as if it were committed.
2. Bind each request to observation identity, scope, observer, semantic/worldview
   revision and relevant support. Determine which revisions invalidate reuse.
3. Reuse normal resolver admission, strategy ordering and result reuse. Audit where
   existing identity/coalescing guarantees apply and which categories need support.
4. Preserve provenance from triggering commit, ingredients, rule and selected model
   through the consequence's resolution and subsequent commits.
5. Verify incremental processing, duplicate notification, cycles, concurrent commits,
   failure and restart/replay behavior without duplicating the resolver state model.

The commit hook, durable work ownership, optional-uncertainty policy and exact
invalidation rules are not selected here. Nor is a common-bearer algorithm for
mixed dependent emergence. Establish these dependencies before claiming an engine
or assigning delivery commitments.

## Questions for collaborators

- What defines the identity of an emerged configuration, and when does a changed
  ingredient update it, invalidate it or produce a different observation?
- How do boolean emergence clauses, partial ingredient availability, absence and
  removal affect detection and retraction?
- What common-bearer rule is justified when dependent ingredients have different
  bearers, including inherited or specialized bearer types?
- Which identity/scope/revision guarantees bound distinct consequence work, and
  which tests demonstrate finiteness for recursive model-generated consequences?
- What is atomic with the triggering commit, and what is a separately retryable
  consequence? How are failure, cancellation and no-model outcomes exposed?
- Who selects optional uncertainty resolution, and what prevents uncertainty or
  change-process requests from generating repeated equivalent work?
- How should provenance and explanations present conferral, implication and
  detection decisions to users?

## Candidate worldview migration and verification

Earlier source used configuration `of` both for emergence ingredients and for
application targets. Applying the agreed rule requires explicit migration of the
latter, rather than silently changing their meaning. Network ingredients normalize
to `emergesFrom`; profile and Terrain application targets use `applies to`.

The private candidate introduces two named applicability specializations for nested
description targets: `geography:TerrestrialSurfaceArrangement` applies to terrestrial
Region without a Crust restriction, and `geography:LocatedBathymetry` applies to
Location. Elevation's nested spatial-arrangement target uses the former. The nested
`Bathymetry of Location` occurrence belongs to **BathymetricSlope**, whose target uses
the latter; BathymetricDepth's native `Height of Location` remains unchanged.

These are private candidate migration choices; their exact compiler/reasoner evidence
belongs to the implementation report. This design draft does not establish live
detection, contextualization or deployment success. Explicit `emerges from` and `applies to` retain distinct
semantic roles despite the readable configuration `of` shorthand.

Related references: [observables](OBSERVABLES.md), [observation strategies](OBSERVATION.md),
[worldview clauses](ONTOLOGY_LANGUAGE.md#77-emerges-from-and-implies),
[resolution](RESOLUTION.md), [knowledge graph](KNOWLEDGE_GRAPH.md) and the
[agent context pack](../llm/DOMAIN_CONTEXT_PACK.md).
