# k.LAB domain ontology articulation context pack

## 1. Mission

Use this pack to analyze authoritative literature for a domain of knowledge and propose a
community-reviewable articulation of that domain in k.LAB terms. The result is a **semantic
proposal**, not an automatic assertion of truth and not necessarily executable `.kwv` source.
It must be precise enough that a worldview author can review it, reconcile it with existing
ontologies, and eventually encode it.

The agent receives:

1. authoritative domain literature;
2. existing ontologies that provide relevant upper and neighboring concepts;
3. this context pack; and
4. a required target `tier` specifying the intended level of domain generality; and
5. optionally, prior proposals and community feedback.

The agent can consult the attached documentation and prior proposals to inform its analysis. It can browse the Web for additional information and inspiration. It can refer to the ODO-IM ontology in its OWL2 source format at https://raw.githubusercontent.com/integratedmodelling/odo-im/refs/heads/master/releases/0.1.0/odo.owl

The agent must return a self-consistent, provenance-rich corpus that:

- identifies all domain-relevant observables and predicates supported by the literature;
- assigns each proposal an ODO-IM category, dependence arity, and structural or functional
  perspective;
- keeps every target concept at the requested tier and exposes its valid ancestry to the required
  upper or Tier-1 ontology context;
- relates it to the most defensible existing upper concept without inventing equivalence;
- explains both the concept and why it was extracted;
- distinguishes explicit source claims from interpretation and modeling choice;
- maximizes orthogonality, so each atomic concept expresses one clear conceptual dimension that
  can vary as independently as the domain permits;
- admits no internally ambiguous concept as review-ready: ambiguous candidates must be split,
  explicitly qualified, or retained as unresolved alternatives;
- orders concepts by semantic dependency; and
- preserves stable identifiers and feedback records across successive review rounds.

Do not confuse the three layers:

- a **worldview concept** defines shared meaning;
- a **Description/model** supplies a strategy for observing that meaning in context; and
- an **observation/Resource** is contextualized information produced by such a strategy.

This pack concerns the first layer. Dataset fields, methods, equations, algorithms, measurement
protocols, and software are evidence about concepts or candidate Descriptions; they are not
automatically worldview concepts.

## 2. Governing ontological stance

ODO-IM is an ontology of scientific observations and their descriptions. It is
phenomenological, descriptive, and linguistic: scientific products result from observations
made from perspectives, at scales, and in contexts. It does not claim that its categories are
the final metaphysical inventory of the world.

Apply these commitments throughout the analysis:

1. **Worldview remains coherent.** The worldview is a distributed but coherent corpus of ontologies that import each other and include a tier system, documented later. You are contributing to one ontology in the context of others; domain conceptual boundaries matter deeply when structuring the worldview. Your domain will import those most generic ones that are relevant to it, and these should be supplied as additional context to the request, but may themselves be in flux or incomplete. According to availability, the .kvw source, the YAML community process output, or both will be supplied for the imported domains. Only the root domain of the worldview can access ODO-IM concepts directly.
2. **Observable is not observation.** An Observable is an abstract, reusable meaning that may
   be observed. An observation is contextualized semantic content produced for an observer in a
   spatial, temporal, and semantic frame.
2. **Observability is mediated.** Direct perception is not privileged over instruments, data,
   models, or other scientific artifacts. What matters is an explicit, defensible description
   of what is being observed.
3. **Perspective has priority.** The same subject matter may be viewed structurally, with a
   continuant-like substantial as focus, or functionally, with an occurrent-like event as focus.
   Category attribution is therefore about the entity *qua observable in the proposed context*,
   not an unconditional metaphysical verdict.
4. **Context is indispensable.** Observables live in relation to other observables. Dependence,
   bearer, participants, endpoints, realm, scale, and observation context must be made explicit
   whenever the source supports them.
5. **Meaning is community-governed.** Domain distinctions are negotiated scientific concepts.
   Record ambiguity and alternatives instead of silently resolving genuine disagreement.
6. **Concepts are operational commitments.** Type, parentage, predicate applicability, inherency,
   endpoints, and clauses affect reasoning and model matching; they are not decorative labels.
7. **Parser acceptance is not semantic validity.** Candidate syntax remains provisional until
   references resolve and the active worldview, validators, Reasoner, adapters, and representative
   workflows accept it.
8. **Internal unambiguity is a hard gate.** A proposed concept must have one stable interpretation
   in the corpus. Polysemous source terms may motivate several separately named concepts, but no
   accepted concept may silently combine their senses. If ambiguity cannot be resolved from
   evidence and scope, keep alternatives open instead of selecting a vague compromise.
9. **Maximize conceptual orthogonality.** Subject to source faithfulness, concepts should encode
   independent semantic axes with the least possible overlap. Prefer composition of an observable,
   predicates, clauses, and operators over atomic concepts that bundle several dimensions. The
   ontology's value increases when each dimension is clear, discriminable, and independently
   reusable.
10. **Use the keyword `abstract` according to k.LAB conventions.** In k.LAB, an abstract concept cannot be directly observed: only its concrete subclasses can. A concept's abstract status matter in resolution and propagates through observables: tagging a concrete observable with an abstract predicate makes it abstract. In a dependency, the abstract status of a predicate needs to be resolved to its contextually concrete states before the observation can take place, creating independently resolved concrete dependencies for each state.

## 3. The semantic coordinate system

Classify every candidate along separate coordinates. Never substitute one coordinate for another.

### 3.1 Structural versus functional perspective

The structural/functional distinction identifies the observation's guiding focus:

| Perspective | Guiding independent observable | Dependent observable | Relational observable |
| --- | --- | --- | --- |
| Structural / continuant-like | substantial (`thing` or `agent`) | quality | structural relationship or bond |
| Functional / occurrent-like | event | process | functional relationship or bond |

Structural means the observation is organized around something enduring that bears qualities.
Functional means it is organized around a bounded occurrence and the ongoing processes composing
or producing change within it. This distinction is perspectival. An event can be dependent on
participants in a structurally framed analysis, while participants can be analyzed through their
involvement in an event under a functional framing.

### 3.2 Dependence and arity

Arity counts how many other observables are constitutively required for the candidate to be
observable in the chosen perspective. It is not grammatical argument count, database cardinality,
or the number of entities mentioned in a sentence.

| Dependence class | Arity | Structural expression | Functional expression | Required question |
| --- |------:| --- | --- | --- |
| Independent / bounded |   `0` | substantial: subject or agent | event | Can it establish the guiding identity and boundary of an observation? |
| Dependent |   `1` | quality inhering in a bearer | process composing an event or involving participants | What must bear, undergo, or frame it? |
| Relational |   `2` | structural relationship | functional relationship | What are the typed source and target? |

Use the labels `independent`, `dependent`, and `relational` for these arity classes in the
intermediate representation. The structural member of the independent class is a substantial;
its functional counterpart is an event, which is not a substantial. A quality can require more
than one reference—for example, a ratio
requires compatible quantities—but remains a dependent observable rather than a relationship.
A process may also have several participants while remaining a process.

### 3.3 Boundedness and countability

A countable is a whole with an identity principle and spatial, temporal, or conceptual boundaries;
it supports the question “how many?”. Substantials, events, and relationships are countables.
Processes are open-ended courses of change, not bounded event tokens. Qualities characterize or
measure something else. Configurations are recognized emergent patterns.

Do not infer `thing` merely because a noun is grammatically countable, or `process` merely because
a word is a gerund. Determine the observational commitment described by the source.

### 3.4 Maximum orthogonality and internal unambiguity

Treat **internal unambiguity** as non-negotiable and **maximum orthogonality** as the principal
optimization criterion among otherwise source-faithful articulations.

Orthogonality does not mean that concepts have no logical relationships. A child necessarily
overlaps its parent, a quality depends on its bearer, and a derived observable depends on its
operand. The criterion applies to the *dimensions encoded as distinct concepts*: siblings and
cross-cutting predicates should not duplicate, partially conceal, or obligatorily bundle each
other's meaning.

Apply these tests to every candidate and to every potentially overlapping pair:

1. **Single-meaning test:** Can the concept be defined with one interpretation, stable scope, and
   one primary observational category? If not, split or defer it.
2. **Discrimination test:** Can a reviewer decide whether an instance falls inside or outside the
   concept using its definition, examples, and counterexamples? If not, sharpen its boundaries.
3. **Independent-variation test:** For two proposed dimensions A and B, can A vary while B remains
   fixed, and can B vary while A remains fixed? If both are possible, they are plausibly
   orthogonal. If neither is possible, investigate identity, derivation, or redundancy. If only
   one is possible, investigate specialization or dependence.
4. **Compositionality test:** Is the candidate merely an existing observable plus an identity,
   realm, attribute, role, clause, context, or unary derivation? If so, represent the composition
   rather than minting an opaque atomic concept—unless the articulated meaning is sufficiently
   central and recurrent in the domain to deserve a stable jargon term. In that case, keep the
   full expression visible and require `equals`-versus-`is` review as described in section 7.5.
5. **Non-redundancy test:** Does another concept already have the same intension or extension at
   the intended scale? Reuse or align it; naming differences alone do not create independence.
6. **Category-stability test:** Does one reading require different observable categories, arities,
   bearers, or relationship direction than another? If so, the candidate is internally ambiguous
   and must not pass review as one concept.

The acceptable relations between a pair are `orthogonal`, `taxonomic`, `dependent`, `derived`, or
`compositional`. `partially_overlapping`, `redundant`, and `conflicting` require a recorded action
before the corpus can be accepted. That action may split, merge, specialize, qualify, replace with
composition, or defer the affected candidates.

Do not optimize apparent orthogonality by inventing distinctions absent from the domain evidence.
The priority order is: reject internal ambiguity; preserve evidence and domain meaning; maximize
orthogonality; then maximize coverage. A smaller set of clear independent dimensions is more
valuable than a comprehensive but entangled vocabulary.

### 3.5 Generality tiers and mandatory ontology context

Every request specifies one positive integer `tier`. The tier constrains how far toward general
domain meaning the proposed conceptualization should reach. It is not a confidence score, review
status, implementation stage, source rank, or measure of concept importance.

| Requested tier | Intended scope | Required ancestry | Intended community |
| ---: | --- | --- | --- |
| `1` | The first ontology layer that describes the domain itself | Direct specialization of upper-level concepts from ontologies that do **not** describe the same domain | The broad domain community and users of more specialized tiers |
| `2` | A specialist articulation within the Tier-1 domain | Every new domain concept must specialize, directly or transitively, a Tier-1 domain concept | A specialist community within the domain |
| `N > 2` | A progressively narrower specialist articulation | Every new domain concept must specialize a Tier-1 concept through the available intervening tier structure | A correspondingly narrower specialist community |

Apply these rules:

1. **Tier 1 is the domain gateway.** Its concepts descend directly from imported upper concepts
   whose ontologies are more general or describe another domain. Tier 1 should articulate the
   clearest, most reusable conceptual dimensions shared across the domain, leaving specialist
   distinctions to lower tiers.
2. **Tier 2 and below are domain specializations.** Every proposed specialist concept must have a
   traceable `is` path to at least one Tier-1 domain concept. It may specialize Tier 1 directly or
   through Tier 2, Tier 3, and so on when those intermediate ontologies exist.
3. **Tier-1 context is mandatory for every request with `tier >= 2`.** The request must include the
   authoritative Tier-1 ontology or ontologies for the domain, with the dependencies needed to
   interpret them. Relevant intervening tiers must also be supplied whenever the proposal is
   expected to specialize them.
4. **Verify the context before extraction.** If a Tier-2+ request lacks Tier-1 context, do not
   invent the missing foundation or present the corpus as valid. Report `missing_tier_1_context`
   and stop ontology articulation until the required context is supplied. Literature extraction
   may be retained as provisional evidence, but category alignment and final concepts remain
   blocked.
5. **Stay at the requested generality.** A Tier-1 proposal must not absorb terminology useful only
   to a specialist community. A Tier-2+ proposal must not redescribe or duplicate Tier-1 concepts;
   it should reuse them and articulate only the requested specialization layer.
6. **Do not confuse reuse with output.** Upper and Tier-1 concepts included as context belong in
   dependencies and alignments, not in the target concept list, unless the request explicitly asks
   for a revision of those concepts.
7. **Record upstream gaps without silently repairing them.** If specialist evidence requires a
   missing or ambiguous Tier-1 concept, emit an `upstream_gap` with the evidence and required
   community decision. Do not create a Tier-1 concept inside a Tier-2+ proposal.
8. **Validate every ancestry claim.** Merely referencing a Tier-1 ontology is insufficient; every
   proposed lower-tier concept must expose its direct parent and complete path to a Tier-1 root.

Tier constrains generality, while orthogonality constrains conceptual quality. A lower-tier concept
may be highly specialized and still must encode one unambiguous, maximally independent dimension.

## 4. Observable taxonomy and decision rules

### 4.1 `thing`: non-agentive substantial

A `thing` is a non-agentive, continuant-like substantial that endures through time, has unity and
boundaries, and can bear qualities. It can establish the structural context for observations.
Examples include an organism, mountain, lake, machine, organization, or region when treated as a
bounded subject.

Propose `thing` when the literature treats instances as identifiable wholes and does not require
agency for their identity. State the proposed identity and unity criterion: what makes one
instance the same instance, and what separates one instance from another? If these are unclear,
flag the proposal rather than substituting a vague container noun.

In older ODO-IM prose this category may be called a **subject**. In current worldview syntax,
`thing` is the declaration keyword for a non-agentive substantial.

### 4.2 `agent`: agentive substantial

An `agent` is a substantial characterized by agency. Yet does not necessarily need the ontologicaly identity of a living being: for example, it could represent groups such as institutions. Agency is stronger than being grammatically
the actor in a sentence and should be supported by capacities such as intention, response,
interaction, decision, or autonomous action.

The language can qualify agents as:

- `deliberative agent`: capable of goal-oriented deliberation;
- `interactive agent`: capable of interaction with other agents or observations; or
- `reactive agent`: responds to conditions or events.

The grammar also accepts unqualified `agent`, but a concrete proposal should select a qualifier
when the evidence supports it. Do not classify a force, mechanism, algorithm, institution, or
instrument as an agent solely because prose says it “acts”; distinguish metaphor, causal agency,
legal/institutional agency, and modeled autonomous behavior in the rationale.

### 4.3 `event`: bounded functional independent

An `event` is a bounded occurrence with temporal identity. It has a cognitively relevant beginning
and end, involves participating substantials, and is made of or realized through processes. Event
concepts are reusable scripts or patterns; contextualization produces event instances. Processes can be inherent to events the same way that qualities are inherent to subjects.

Propose `event` when the source treats an occurrence as a recognizable whole that can happen,
recur, be counted, and be delimited. Record boundary criteria, participants, and composing
processes where available. Wildfire, birth, conference, and storm episodes illustrate this form.

### 4.4 `process`: open-ended dependent occurrent

A `process` is an ongoing, temporally extended course of interaction or change. It is homogeneous
or open-ended at the chosen description level and is understood dynamically while it occurs.
Processes compose or make events and may create countables or qualities, affect qualities, or
confer attributes and roles.

Propose `process` when the scientific interest is the ongoing mechanism, flow, transformation, or
state of change rather than a bounded episode. Record the event, context, or participants on which
it depends. Use an event instead if start/end boundaries and identity as a whole are essential.

### 4.5 `relationship` and `bond`: binary dependent countables

A relationship directionally connects a source substantial to a target substantial and has
dependence arity `2`. Its endpoints are part of its meaning and must be proposed explicitly.

- A **structural relationship** presents a stable/static connection. It may support the emergence
  of a relator substantial or configuration, such as a road connecting cities or parenthood
  contributing to a family.
- A **functional relationship** presents interaction or dynamic involvement. It may engender an
  event composed of processes, such as use or service interactions.

Current syntax supports `functional relationship`, `structural relationship`, and corresponding
`bond` declarations. Use `bond` only when the relation has no direction and a specific source and target are not discernible; do not invent a distinction
from wording alone. Although the grammar accepts an unqualified relationship or bond, concrete
proposals should choose structural or functional whenever defensible.

In `relationship`, direction matters. “A supplies B” is not interchangeable with “B is supplied by A” in a source/
target declaration. If the literature is unambiguously non-directional, use a `bond`; if contradictory or confused, say so in the rationale and verify how the
active worldview represents symmetry; do not fabricate two directed concepts without need.

### 4.6 Quality: dependent state or characteristic

A quality inheres in or characterizes another observable. It cannot be observed without a bearer,
reference, or other dependency. Always identify the candidate bearer types and what kind of value
an observation would produce. The bearer does not need to be in the proposed structure; if not, though, it should be part of an upper-level, less specific ontology.

#### Contextual quality and extent

Spatial, temporal, and other contextual dimensions provide an observation's scale, granularity,
and extent. Worldview syntax includes `extent` for these concepts. Do not mistake a spatial realm
predicate such as Atmospheric for spatial extent, or a bounded geographical subject such as a
watershed for the abstract spatial dimension used to locate it.

#### Presence

Presence is a boolean-like quality expressing whether or where a countable is present. Prefer a
derived `presence of X` observable over inventing a new domain quality when the intended meaning
is exactly existence/presence of an existing countable.

#### Enumerable quality and `class`

An enumerable quality takes conceptual values from a declared classification space. Worldview
syntax uses `class` for this kind of quality. Examples include land-cover class or soil type. Its
values are concepts, not arbitrary strings.

An **identity predicate** such as a species or material identity is not itself an observable
quality. Apply `type of` to reify an identity/trait taxonomy as an observable enumerable quality:

```observable
type of biology:Tree;
```

The derived observable asks for the classifying identity of the subject in context. In a proposal,
keep the identity taxonomy and the derived `type of` quality distinct and link them explicitly.
Do not declare every “type of X” phrase in literature as an identity; sometimes the text names an
enumerable quality, and sometimes it merely introduces a subclass taxonomy.

#### Quantifiable quality

A quantifiable quality receives a numeric value under a quantification Description. The generic
keywords are `quantity` and physical-property keywords such as `volume`, `mass`, `temperature`,
and `velocity`.

Physical properties will be attributed a unit of measurement. In doubt, a quality that does not admit a unit cannot be a physical property. Classify physical properties as:

- **extensive** when aggregation over support is meaningful and value depends on the extent or
  amount of the bearer; or
- **intensive** when simple summation across support is not meaningful.

The active ontology-language contract is operationally authoritative for keyword classification.
It classifies `amount`, `area`, `duration`, `length`, `mass`, `money`, `volume`, and `weight` as
extensive; and `acceleration`, `angle`, `charge`, `electric-potential`, `energy`, `entropy`,
`pressure`, `priority`, `resistance`, `resistivity`, `temperature`, `velocity`, and `viscosity` as
intensive. Domain science may require more specialized quality and mediation rules; explain any
apparent mismatch rather than changing the base category silently.

Other important quantifiable qualities are:

- probability, including presence probability;
- relative quantities, including ratios and proportions that depend on compatible qualities;
- uncertainty, which qualifies partial information about a state or result;
- numerosity, derived by counting countables;
- priority, a numeric monotonic ranking; and
- value, assigned by a subject or agent according to criteria, including monetary value and
  non-monetary preference.

Use `ordering` for a conceptual ordering or discretization of a quality, and `priority` for the
specific quantifiable ranking property. Use `subjective` only when interpretation genuinely
depends on an observer or perspective, and state whose perspective and by which criteria.

### 4.7 `configuration`: recognized emergent pattern

A configuration is an observer-dependent arrangement or system recognized from other
observations, commonly qualities and relationships. It is not merely a collection, a context
subject, or a relationship. In ODO-IM it is contextualized through detection.

Propose a configuration only when the literature identifies a pattern whose organization is the
object of interest—for example, a social network emerging from social connections. List the
observables from which it emerges and the detection or recognition criteria if the literature
provides them. Current worldview syntax permits `configuration` declarations and `emerges from`
clauses, but operational support must still be validated.

## 5. The four observable-refining predicate types

Predicates specialize an observable's meaning but normally cannot be directly supported as
stand-alone observations. Keep four predicate dimensions distinct: **attribute, identity, role,
and realm**. ODO-IM additionally calls `domain` an epistemic predicate; in the worldview language
it is primarily an organizing concept for locating ontologies and assigning disciplinary scope,
not one of the four ordinary dimensions attached to an individual observable in this pack.

### 5.1 `attribute`

An attribute is an accidental descriptive predicate: the bearer can gain or lose it without
becoming a different kind of thing. Attributes cover:

- capabilities or dispositions, such as reproductive or pervious;
- states or phases, such as pollinated, irrigated, or adult; and
- non-quantitative subjective orderings, such as mild, severe, or damaged.

Use an attribute only when the term refines a bearer rather than naming an independently
observable quality. “Hot” may be an attributed ordering, while temperature is a quantifiable
quality. State applicability. Mark an attribute `deniable` only when meaningful explicit
negation exists; use `deniable as` when the worldview defines a named opposite. `rescaling`
attributes are language-supported but should not be proposed without an established semantic
need and validation.

### 5.2 `identity`

An identity is a classificatory predicate whose instances share a principle of identity or an
epistemically important kind. Identity taxonomies may summarize many morphological, functional,
or behavioral characteristics: species, substances, rock kinds, and artifact types are common
examples.

Identity is not the same as:

- the individuality of a contextualized token;
- a parent observable declared with `is`;
- a temporary role;
- a measured class quality; or
- a loose topical label.

Use `individual identity` only for a predicate intended to identify a unique individual entity.
Use an authority such as `IUPAC:...` or `GBIF:...` when an external terminology owns a large
identifier space. Preserve the external identifier and proposed alignment instead of copying the
whole authority taxonomy into the worldview.

When an identity must become an observation result, derive the enumerable quality with `type of`.
The base identity remains a predicate; the derived class is the observable.

### 5.3 `role`

A role is an externally grounded, contingent characterization played in a specific context. A
bearer can play several roles simultaneously and can cease playing one without changing its
identity. Pollinator, supplier, beneficiary, patient, or regulator may be roles depending on the
domain analysis.

In general, roles are contextual and therefore should not be attributed to the observables as part of a semantic analysis of the domain. Rather, the possible roles that pertain to the domain should be articulated along with their applicable observables and implications.

State:

- which observable types can play the role (`applies to`);
- the context that makes the role hold;
- any related process, event, relationship, or counterpart role;
- remember that a process or a continuant-bound configuration may imply (`implies`) a role for the continuant that the process inheres to; and
- remember that a process or a continuant-bound configuration may imply (`implies`) a role for the continuant that the process inheres to; and
- whether the literature treats the term as a role or as an identity across all contexts.

Do not turn every participant label into a role. If it is only a local argument name in one method,
retain it in evidence or Description analysis rather than promoting it to the worldview.

### 5.4 `realm`

A realm restricts the broad physical/environmental location or stratum of an observable, such as
terrestrial, atmospheric, marine, freshwater, subterranean, or soil-stratum. It is a contextual
predicate, not a coordinate, extent, bounded region subject, or scientific discipline.

State applicability and distinguish:

- `AtmosphericTemperature`: temperature refined by an atmospheric realm;
- `temperature of earth:Atmosphere`: a quality inhering in an atmosphere subject; and
- an atmospheric spatial extent.

These may be related but they are not automatically equivalent.

### 5.5 `domain`: organizing epistemic concept

A domain assigns concepts or an ontology to a disciplinary perspective, such as geography or
ecology. Domains and identities are recursively taxonomic and carry explanatory/inferential value,
but `domain` should normally organize the corpus and ontology preamble rather than modify each
candidate observable. Record cross-domain concepts and competing domain placements explicitly.

## 6. Unary semantic operators

Unary operators derive a new observable meaning from an existing operand. They are not string
macros, synonyms, or arbitrary mathematical functions. Before proposing one, verify the operand's
semantic category and explain the new observable's dependence and bearer/context.

| Form | Required operand | Resulting intent | Engagement rule |
| --- | --- | --- | --- |
| `presence of X` | countable | presence quality | Use for whether/where X exists; do not duplicate it as a named boolean quality without domain reason. |
| `count of X` | countable | numerosity quality | Counts instances in context; do not use for amount, mass, or other continuous quantity. |
| `occurrence of X` | event or process expression accepted by the active worldview | occurrence quality | Distinguish occurrence evidence from the occurrence itself. Current API typing is narrower than the user-facing description, so validate this derivation before relying on it. |
| `distance to X` / `distance from X` | geolocatable countable | distance quality | Direction and reference must be meaningful in context. |
| `probability of X` | event | probability quality | Probability concerns occurrence of a possible event; avoid treating confidence or uncertainty as probability. |
| `uncertainty of X` | quality | uncertainty quality | Identifies uncertainty attached to another observed quality or result. |
| `magnitude of X` | quantifiable | magnitude quality | Use when magnitude is the intended representation, not as a generic numeric conversion. |
| `level of X` | quantifiable | ordered class quality | Produces an ordering/discretization; define level categories or thresholds separately. |
| `type of X` | identity/trait predicate | enumerable class quality | Reifies a classification space as an observable result; retain the source identity taxonomy. |
| `change in X` | quality | process | Open-ended change in a quality. Identify bearer and temporal context. |
| `changed X [from A] [to B]` | quality | event | Bounded transition, optionally constrained by endpoints; distinct from ongoing change. |
| `change rate of X` | quality | rate quality | Requires a temporal reference and a quality for which rate is meaningful. |
| `proportion X in Y` | trait or quantifiable | relative quantity | `Y` is the whole/reference; current syntax has no `of` after `proportion`. |
| `percentage X in Y` | trait or quantifiable | percentage quality | Same semantics as proportion with percentage representation. |
| `ratio of X to Y` | compatible quantifiables | ratio quality | Both operands are required and dimensional compatibility must be justified. |
| `value of X [over Y]` | observable/configuration | value quality | State valuing subject/agent, criteria, and optional comparison/beneficiary. |
| `monetary value of X [over Y]` | observable/configuration | monetary value quality | State currency/trading system, reference year where represented, agent, and criteria. |
| `not X` | deniable attribute | negated predicate | Valid only when the worldview declares the attribute deniable; absence of evidence is not negation. |

Additional rules:

1. Reuse a canonical derived expression instead of declaring an equivalent ad hoc concept.
2. Apply operators after resolving predicate scope and grouping. Use parentheses when an operator
   should transform a composite expression.
3. Do not assume that an operator preserves type: `change in` returns a process, `changed` returns
   an event, `type of` returns a class quality, and most others return qualities.
4. Do not stack operators until each intermediate expression is meaningful and supported.
5. Record units, currency, ranges, classification spaces, and mediation separately from meaning.
6. Treat operator applicability in this pack as guidance; final validity belongs to the active
   worldview and Reasoner.

## 7. Combining an observable, predicates, and specialization clauses

### 7.1 Build one semantic head

Every composite expression has one main observable. Predicates narrow that head; dependent clauses
connect it to other observables. First write the intended reading in plain language:

> atmospheric, managed water reservoir playing the role of water supplier

Then assign each modifier separately:

- head: `infrastructure:Reservoir` (`thing`);
- realm: `earth:Atmospheric` only if that physical placement actually applies;
- attribute: `management:Managed`;
- role: `water:WaterSupplier` in a specified supply context; and
- identity: only if a classificatory kind is intended.

Do not create one opaque concept name that bundles all dimensions unless the conjunction has a
stable, community-recognized meaning and needs its own parentage or axioms.

### 7.2 Predicate composition

In observable syntax, predicates and the head may appear as a concept sequence:

```observable
biology:Eucalyptus biology:Tree;
(ecology:AboveGround ecology:Biomass) of biology:Tree;
```

The active worldview determines admissible predicate order and compatibility. In the intermediate
representation, always preserve explicit predicate categories and scope even if a compact surface
expression is supplied. Use parentheses to show which composite a later modifier constrains.

Use `inherits` in a concept declaration to attach predicate-like concepts to a declared concept:

```kwv
identity Snow
    inherits physical:Solid, earth:Frozen
;
```

`inherits` is not taxonomic specialization. Use `is` for a narrower kind.

### 7.3 Inherency and other semantic modifiers

Use semantic modifiers to expose dependency rather than embedding it in names:

| Syntax | Meaning |
| --- | --- |
| `X of Y` | X inheres in or is attributed to Y |
| `X caused by Y` / `X causing Y` | causal direction |
| `X for Y` | goal, beneficiary, or intended context |
| `X adjacent to Y` | adjacency |
| `X contained in Y` / `X containing Y` | containment |
| `X with Y` | compresence |
| `X during Y` | temporal co-occurrence |
| `R linking S to T` | relationship R with source S and target T |

Examples:

```observable
climate:AirTemperature of earth:Atmosphere;
hydrology:WaterFlow caused by climate:Precipitation;
ecology:HabitatSuitability for biology:Species;
infrastructure:Road linking geography:City to geography:City;
```

Do not infer causation from correlation, adjacency from co-occurrence, containment from topical
association, or inherency from a convenient dataset grouping. Cite the textual evidence and mark
the relation as a modeling hypothesis when it is inferred.

### 7.4 Taxonomic specialization and equivalence

In worldview declarations:

- `is Parent` means specialization/subsumption;
- `is Parent within Context` means contextualized specialization;
- `is core ExternalConcept` anchors a worldview concept to an imported foundational concept; and
- `equals Other` asserts genuine semantic equivalence.

Use the narrowest defensible existing parent that preserves the proposed type. Multiple topical
similarities do not justify multiple parents. Never use `equals` for synonyms that differ in scope,
measurement convention, perspective, or community usage.

### 7.5 Naming a compositional meaning as domain jargon

A meaning that can be expressed compositionally may legitimately remain a longer articulated
expression **or** receive a domain-specific concept name. Orthogonality alone does not decide
between them: a named alias need not introduce a new conceptual dimension.

Base the choice on the expression's importance to the domain:

- prefer the articulated expression when the meaning is peripheral, rarely used, specific to one
  source, or unlikely to be queried and discussed as a unit;
- consider a stable domain term when the meaning is central to domain explanations, repeatedly
  cited or defined, used across independent authoritative sources, or needed frequently as a unit
  in discourse, annotation, queries, or downstream definitions; and
- do not treat raw word frequency as sufficient evidence. Record citation locations, source
  diversity, canonical definitions, and the concept's functional centrality in the domain.

When the proposed term means **exactly** the same thing as the articulated expression, flag it for
review as a possible `equals` declaration. This captures accepted jargon as an alias without
asserting a false specialization:

```kwv
<type> DomainJargon
    "The established domain term for the articulated meaning."
    equals <articulated-concept-expression>
;
```

Use `is` only when the named concept is genuinely narrower than the expression. If equivalence is
uncertain, retain both options for review; domain salience licenses consideration of a name, not an
unsupported equivalence claim. A reviewer must compare intension, extension, perspective,
dependence, context, units/value space, and applicable predicates. Any mismatch rules out
`equals` unless the expression or definition is corrected.

Every such proposal must preserve the full expression, evidence of domain salience, the benefit of
naming it, and an explicit `equals`-versus-`is` review flag. The outcome must be one of:

- `keep_expression`: no new name;
- `alias_with_equals`: name and expression are genuinely equivalent;
- `specialize_with_is`: the name denotes a narrower domain concept; or
- `unresolved`: reviewers must decide or request further evidence.

### 7.6 Definition clauses

Propose clauses only when evidence or a transparent modeling rationale supports them:

| Clause | Use |
| --- | --- |
| `applies to X, Y` | restrict the valid bearers of a predicate or relevant concept |
| `links S to T` | type the source and target of a relationship |
| `creates X, Y` | declare what a process may produce |
| `affects X, Y` | declare qualities or observables whose state a process may change |
| `emerges from E` | state observations from which a configuration/pattern is recognized |
| `implies E` | state an entailment justified by observing the concept |
| `describes X ...` | define a quality through another observable or constraint |
| `increases with X` / `decreases with X` | qualitative direct/inverse proportionality |
| `marks X` | connect a deniable marker to a quality condition |
| `classifies X` | connect an enumerable quality to what it classifies |
| `discretizes X` | connect an ordering to the quantitative quality it partitions |
| `requires identity/realm/extent/attribute X` | state a compulsory semantic dimension |
| `requires authority A {...}` | delegate identity validation to an external authority |
| `has [disjoint] children ...` | define a compact child taxonomy |

`creates` and `affects` express semantic potential, not equations or executable computation.
`emerges from` and `implies` accept contextualized expressions, not merely labels. Use disjoint
children only if children cannot overlap; use a sealed partition only when the children completely
cover the parent. Treat completeness, exclusivity, and clause compatibility as claims requiring
review.

## 8. Extraction and articulation procedure

Follow these phases in order. Do not draft the final taxonomy directly from keyword matches.

### Phase A — establish scope and evidence

1. Record and validate the requested tier. For `tier >= 2`, inventory the mandatory Tier-1
   ontology context and any relevant intervening tiers before extracting concepts. If Tier 1 is
   absent, report `missing_tier_1_context` and block articulation.
2. Record the domain question, intended users, jurisdiction, time period, spatial extent, scales,
   and authoritative-source policy.
3. Inventory every input source and ontology with stable identifiers, versions, tiers, domain
   scopes, and access dates.
4. Extract source passages into an evidence ledger. Each evidence item should capture the passage
   location, a concise paraphrase, the source's own term, and whether it states a definition,
   distinction, mechanism, classification, measurement, example, or contested claim.
5. Separate domain claims from document structure, dataset schema, and method-specific artifacts.
6. Record terminological conflicts, source-specific senses, and translation decisions.

### Phase B — discover candidate dimensions

For every domain passage ask:

1. What identifiable subjects or agents establish context?
2. What bounded events occur?
3. What ongoing processes compose those events or change state?
4. What qualities inhere in which bearers, and what are their value kinds?
5. What directed relationships or bonds connect which endpoints?
6. What configurations emerge from qualities and connections?
7. Which identities classify kinds, which attributes characterize them, which roles are played in
   context, and which realms locate them?
8. Which distinctions are already expressible by upper concepts, predicates, operators, or
   composition and therefore do **not** need new atomic concepts?
9. Which source terms conflate senses or conceptual dimensions that must be separated?
10. Which candidate dimensions can vary independently, and which overlap, imply, derive from, or
    duplicate each other?
11. Which compositional meanings are so central, repeatedly cited, or widely established in the
    domain that a stable jargon alias may be justified?
12. Does each candidate belong at the requested tier, or is it an upper/Tier-1 dependency, an
    intervening-tier concept, a more specialized future concept, or an upstream gap?

Extract a concept when it has a stable meaning needed to express domain observations, inference,
or integration—not simply because it is frequent, capitalized, or present in a glossary.

### Phase C — classify and align

For each candidate:

1. resolve the candidate to one stable sense, or split it into explicitly distinguished candidates;
2. assign the candidate to the requested tier or exclude it from the target corpus with a recorded
   tier rationale;
3. write a non-circular definition with boundaries, examples, and counterexamples;
4. select exactly one primary category and record any semantic flags;
5. assign perspective, dependence class, and arity;
6. identify bearer, participants, or endpoints as required by category;
7. search the supplied ontologies for exact concepts, parents, predicates, and reusable expressions;
8. rank alignment candidates and explain the selected parent;
9. for Tier 1, confirm that the direct parent belongs to an upper ontology outside the same domain;
   for Tier 2+, construct and verify the complete ancestry path to a Tier-1 domain concept;
10. record rejected alternatives and why they fail;
11. decide whether the result should be an atomic declaration, a specialization, a predicate
   composition, a unary derivation, a contextualized expression, an authority identity, or a
   Description/model concern;
12. apply the single-meaning, discrimination, compositionality, and category-stability tests;
13. compare it with related candidates using the independent-variation and non-redundancy tests;
14. when a domain term names an otherwise compositional meaning, assess its citation support and
    domain centrality, preserve the full expression, and require `equals`-versus-`is` review; and
15. assign confidence separately to extraction, category attribution, upper alignment,
    unambiguity, and orthogonality.

Do not force uncertain candidates into the ontology. Use `needs_review`, `defer`, or
`not_a_worldview_concept` dispositions with reasons.

### Phase D — construct and order the corpus

Build an explicit dependency graph. A concept depends on every parent, predicate, bearer type,
endpoint type, clause target, operator operand, and referenced classification value that it uses.
Reject missing references and cycles unless a reviewer confirms a legitimate mutual definition.
For Tier-2+ corpora, include context-only ancestry nodes in the graph so each target concept's path
to Tier 1 can be validated without re-emitting those nodes as proposals.

Also build a pairwise orthogonality matrix for siblings, near-synonyms, candidates sharing a
bearer or value space, and any pair flagged by reviewers or lexical similarity. A full quadratic
comparison of obviously unrelated concepts is unnecessary, but every plausible overlap must be
classified. Resolve all `partially_overlapping`, `redundant`, and `conflicting` pairs or keep the
corpus in `draft`/`needs_review` status.

Emit a stable topological order with these tie-breakers:

1. domains and external authority declarations needed by the corpus;
2. independent structural substantials: `thing`, then `agent`;
3. identity, realm, attribute, and role taxonomies needed to refine them;
4. structural qualities and derived qualities of those bearers;
5. bounded events;
6. processes and their affected/created observables;
7. functional qualities and derived qualities;
8. structural relationships and bonds;
9. functional relationships and bonds; and
10. configurations, orderings, partitions, and other concepts that depend on the preceding graph.

The requested human reading order begins with subjects and agents, then their qualities and
processes, then relationships and bonds. When strict dependency requires a predicate or parent to
appear first, preserve the topological order and use `section` plus `depends_on` to make the reason
visible rather than duplicating the concept.

### Phase E — validate and report

Perform the checks in section 11. Summarize coverage, unresolved questions, source conflicts,
structural risks, and concepts requiring domain-expert decisions. If candidate `.kwv` syntax was
not parsed and Reasoner-validated, label it `illustrative` or `unverified`; never call it
implementable merely because it looks plausible.

## 9. Required proposal format

YAML is the recommended intermediate representation because it is reviewable, diffable, and
re-ingestable. JSON with the same information model is acceptable. Preserve field names and
stable IDs between iterations. A field may be `null`; do not omit required fields to hide missing
analysis.

```yaml
context_pack_version: "1.1"
proposal:
  id: "domain-slug-v1"
  title: "Domain ontology articulation proposal"
  iteration: 1
  supersedes: null
  status: "draft" # blocked | draft | community_review | revised | accepted | rejected
  generated_at: "YYYY-MM-DD"
  scope:
    domain: "plain-language domain"
    purpose: "questions and integrations this corpus must support"
    requested_tier: 2
    target_community: "the specialist community served by this tier"
    tier_1_context_required: true
    tier_1_context_status: "present" # not_required | present | missing | incomplete
    included: []
    excluded: []
    spatial_context: null
    temporal_context: null
    intended_scales: []
  source_policy:
    authority_criteria: []
    inference_policy: "state interpretations and modeling choices explicitly"
  sources:
    - source_id: "src-001"
      citation: "full bibliographic or ontology citation"
      version: null
      locator: "DOI, URL, ontology IRI, or supplied-file identifier"
      authority_note: "why this source is authoritative for this claim"
  existing_ontologies:
    - ontology_id: "upper"
      version: null
      role: "upper" # upper | tier_1_mandatory | intervening_tier | neighboring | authority | prior_domain
      tier: null
      domain_scope: "a more general or different domain"
      mandatory_context: true
    - ontology_id: "tier1-domain"
      version: "1.0"
      role: "tier_1_mandatory"
      tier: 1
      domain_scope: "the broad domain ontology specialized by this proposal"
      mandatory_context: true
  evidence:
    - evidence_id: "ev-001"
      source_id: "src-001"
      locator: "page, section, paragraph, figure, or axiom"
      source_term: "term used by the source"
      paraphrase: "short source-faithful claim"
      evidence_kind: "definition | distinction | classification | mechanism | measurement | example | contested_claim"
      interpretation: "none, or the agent's explicit interpretation"

  concepts:
    - concept_id: "domain:StableConceptName"
      label: "preferred human label"
      abstract: false
      aliases: []
      section: "subjects"
      tier: 2
      tier_rationale: "why this concept belongs at the requested generality"
      disposition: "propose" # propose | reuse | align | needs_review | defer | not_a_worldview_concept
      definition: "necessary and distinguishing meaning, without circularity"
      conceptual_dimension: "the single semantic axis encoded by this concept"
      boundaries:
        includes: []
        excludes: []
        examples: []
        counterexamples: []
      extraction_rationale: "why the domain needs this concept"
      evidence_refs: ["ev-001"]
      epistemic_status: "explicit | inferred | modeling_choice | contested"

      semantic_coordinates:
        kind: "thing" # allowed kinds are listed below
        flags: [] # abstract, subjective, individual, deliberative, interactive, reactive, functional, structural, deniable, rescaling
        perspective: "structural" # structural | functional | cross_perspective | not_applicable
        dependence: "independent" # independent | dependent | relational | not_applicable
        arity: 0 # integer, ">=1", or null for predicates/domains
        countable: true
        bearer_types: []
        participant_types: []
        relationship_source: null
        relationship_target: null
        value_kind: null # boolean | concept | number | rank | geometry | duration | other
        aggregation_behavior: null # extensive | intensive | not_applicable | unresolved
      category_rationale: "why these coordinates fit better than alternatives"

      ambiguity:
        status: "unambiguous" # unambiguous | qualified | unresolved
        source_senses: []
        selected_sense: "the one sense represented by this concept"
        excluded_senses: []
        resolution_rationale: "how wording and scope prevent internal ambiguity"

      orthogonality:
        atomic_dimension: true
        composite_of: []
        independently_variable_from: []
        relations:
          - other_concept: "domain:RelatedConcept"
            relation: "orthogonal" # orthogonal | taxonomic | dependent | derived | compositional | partially_overlapping | redundant | conflicting
            a_varies_with_b_fixed: true
            b_varies_with_a_fixed: true
            rationale: "evidence for independence or the specified non-orthogonal relation"
            required_action: null
        assessment: "why this is an independent reusable dimension rather than a bundle"

      named_composition:
        applicable: true
        domain_term: "established domain jargon"
        articulated_expression: "upper:Predicate upper:Observable of upper:Bearer"
        naming_rationale: "why this meaning is crucial enough to address as a unit"
        salience:
          level: "core" # peripheral | established | core
          citation_evidence_refs: ["ev-001"]
          occurrence_count: null
          independent_source_count: null
          canonical_definition_refs: []
          discourse_uses: [] # explanation | annotation | query | model_definition | community_governance
        equals_vs_is_review_required: true
        proposed_outcome: "unresolved" # keep_expression | alias_with_equals | specialize_with_is | unresolved
        equivalence_assessment:
          same_intension: null
          same_extension: null
          same_perspective_and_category: null
          same_context_and_dependence: null
          same_value_space_and_predicates: null
          rationale: null
        reviewer_decision: null

      alignment:
        action: "specialize" # reuse_exact | specialize | core_anchor | authority_reference | new_root_candidate | unresolved
        selected_parent: "tier1:DomainConcept"
        direct_parent_tier: 1
        tier_1_ancestor: "tier1:DomainConcept"
        ancestry_to_tier_1:
          - "domain:StableConceptName"
          - "tier1:DomainConcept"
        ancestry_status: "verified" # verified | missing_parent | missing_tier_1_ancestor | unresolved
        contextualized_within: null
        candidates:
          - concept: "tier1:DomainConcept"
            relation: "is"
            score: 0.90
            rationale: "shared intension and compatible observational type"
        rejected_candidates: []
      predicates:
        identities: []
        realms: []
        attributes: []
        roles: []
      derivation:
        operator: null
        operand: null
        secondary_operand: null
        rationale: null
      clauses:
        inherits: []
        applies_to: []
        links: null
        creates: []
        affects: []
        emerges_from: []
        implies: []
        describes: null
        requires: []
        children: []
      dependencies: ["tier1:DomainConcept"]
      candidate_expression: "domain:StableConceptName"
      candidate_kwv: null
      syntax_status: "not_attempted" # not_attempted | illustrative | parsed | adapted | reasoner_validated
      confidence:
        extraction: 0.90
        category: 0.85
        alignment: 0.75
        unambiguity: 0.95
        orthogonality: 0.85
      open_questions: []
      alternatives: []
      feedback:
        state: "open" # open | changes_requested | accepted | rejected | deferred
        comments: []
        decisions: []
        last_reviewed_iteration: null

  dependency_order: ["domain:StableConceptName"]
  upstream_gaps: []
  orthogonality_review:
    hard_unambiguity_gate_passed: false
    pairwise_review_complete: false
    matrix:
      - concept_a: "domain:StableConceptName"
        concept_b: "domain:RelatedConcept"
        relation: "orthogonal"
        a_varies_with_b_fixed: true
        b_varies_with_a_fixed: true
        overlap_description: null
        resolution: null
    unresolved_ambiguities: []
    unresolved_overlaps: []
  validation:
    schema_valid: false
    references_resolve: false
    acyclic: false
    requested_tier_valid: false
    mandatory_tier_1_context_present: false
    tier_ancestry_valid: false
    orthogonality_reviewed: false
    internally_unambiguous: false
    named_composition_reviews_complete: false
    semantic_review: "pending"
    syntax_validation: "not_run"
    warnings: []
  corpus_feedback:
    state: "open"
    coverage_comments: []
    structural_comments: []
    cross_cutting_issues: []
    decisions: []
    requested_changes: []
  change_log:
    - iteration: 1
      changes: ["initial proposal"]
      feedback_resolved: []
```

Allowed primary `kind` values are:

```text
thing, agent, event, process, relationship, bond, configuration,
quality, class, quantity, ordering, extent,
amount, area, duration, length, mass, money, volume, weight,
acceleration, angle, charge, electric-potential, energy, entropy,
pressure, priority, resistance, resistivity, temperature, velocity, viscosity,
attribute, identity, role, realm, domain
```

Prefer the most specific justified quality keyword. If the ODO-IM conceptual kind is known but no
current declaration keyword is confidently mapped, use the nearest generic current kind and record
the intended specialization in `category_rationale` and `open_questions`.

### 9.1 Tier request and blocked-response contract

The request must state `requested_tier` and provide an ontology manifest that identifies each
ontology's tier, domain scope, version, and role. For `requested_tier >= 2`, at least one supplied
ontology must be explicitly identified as `tier_1_mandatory` for the same domain.

If that requirement is not met, return a compact structured response instead of a concept corpus:

```yaml
proposal:
  id: "domain-slug-tier2-blocked"
  status: "blocked"
  scope:
    domain: "plain-language domain"
    requested_tier: 2
    tier_1_context_required: true
    tier_1_context_status: "missing"
  concepts: []
  upstream_gaps:
    - gap_id: "gap-tier1-context"
      required_tier: 1
      required_concept: "authoritative Tier-1 ontology context for this domain"
      evidence_refs: []
      reason: "Tier-2 articulation cannot establish valid domain ancestry without Tier 1."
      status: "open"
  validation:
    requested_tier_valid: true
    mandatory_tier_1_context_present: false
    tier_ancestry_valid: false
    warnings: ["missing_tier_1_context"]
```

An `upstream_gap` discovered after valid context has been supplied uses the same record structure,
but it identifies the specific missing or ambiguous Tier-1 concept and cites the specialist
evidence that requires it. Such a gap belongs to Tier-1 governance and is not silently added to the
lower-tier output.

### 9.2 Per-concept feedback records

Each comment must remain addressable and auditable:

```yaml
- comment_id: "fb-concept-004"
  author: "reviewer or community identifier"
  iteration: 1
  target: "domain:StableConceptName"
  field: "semantic_coordinates.kind"
  stance: "question | support | object | request_change"
  comment: "Why is this a process rather than an event?"
  evidence_refs: ["ev-014"]
  proposed_change: "Reclassify as event and add temporal boundary criteria."
  status: "open" # open | accepted | rejected | superseded | resolved
  resolution: null
  resolved_in_iteration: null
```

A resolution must explain the decision, cite supporting evidence, and name the iteration that
implemented it. Never erase rejected comments; retain them in the history.

### 9.3 Corpus-level feedback records

Use corpus feedback for missing dimensions, wrong boundaries, systemic categorization problems,
ordering, naming policy, duplicated concepts, domain placement, and source-policy concerns:

```yaml
- comment_id: "fb-corpus-002"
  author: "community working group"
  iteration: 1
  target: "corpus"
  area: "coverage | structure | dependency_order | naming | source_policy | alignment"
  stance: "request_change"
  comment: "The proposal models outcomes but omits the agents and processes that produce them."
  affected_concepts: ["domain:Outcome"]
  proposed_change: "Repeat extraction phases B and C for agency and process dimensions."
  status: "open"
  resolution: null
  resolved_in_iteration: null
```

### 9.4 Successive-iteration protocol

When community feedback is returned with a previous corpus:

1. ingest the previous YAML as the baseline rather than extracting the whole domain anew;
2. preserve `concept_id`, `evidence_id`, and `comment_id` values for unchanged records;
3. preserve the requested tier across an iteration; changing tier starts a separately scoped
   proposal and requires a new context and ancestry audit;
4. append comments and decisions; never rewrite review history or delete rejected alternatives;
5. resolve each accepted comment through a named field change and cite the resolution in
   `change_log.feedback_resolved`;
6. retain removed proposals as records with `disposition: defer`,
   `not_a_worldview_concept`, or an explicit rejection decision instead of silently dropping them;
7. rerun tier ancestry, reference, category, ambiguity, pairwise orthogonality, dependency, and
   ordering validation
   for every changed concept, its close conceptual neighbors, and all of its downstream dependents;
8. require a recorded reviewer decision for every applicable `equals_vs_is_review_required` flag,
   preserving the articulated expression even when a jargon name is accepted;
9. increment `iteration`, set `supersedes` to the previous proposal ID/version, and summarize both
   semantic and editorial changes; and
10. reopen corpus-level review when a local change alters category boundaries, naming policy,
   partitions, imports, or dependency order elsewhere in the proposal.

If two review comments conflict, preserve both, state the conflict, identify the decision authority
or evidence needed, and leave the affected field `needs_review` until a recorded resolution exists.

## 10. Minimal syntax guidance

The active grammar is the source of truth. These patterns are sufficient to make proposals
translatable while keeping YAML as the primary artifact.

### 10.1 Names and ontology preamble

- Ontology namespaces are lower-case identifiers or lower-case dotted paths.
- Concept names begin with an uppercase letter.
- Qualified references use `namespace:ConceptName`.
- Every `.kwv` concept declaration ends with `;`.
- `in domain` and `version` are mandatory in an ontology preamble; `using` imports complete
  namespaces.

```kwv
ontology freshwater.ecology
    "Concepts proposed for community review."
    using imod, earth
    in domain imod:Knowledge of imod:Biosphere
    version 0.1
;
```

### 10.2 Declaration skeleton

```kwv
<annotation>*
[abstract] [subjective] <type> <ConceptName>
    "Non-circular definition."
    [is <concept-ref> [within <concept-ref>]]
    [inherits <predicate-ref>, ...]
    [other compatible clauses]
    [metadata {...}]
;
```

Clause order is flexible in the grammar, but proposals should use parentage first, then
restrictions, effects and emergence, metadata, and child taxonomies. Each clause family occurs at
most once per concept definition.

### 10.3 Representative declarations

```kwv
thing Reservoir
    "A bounded constructed substantial that stores a material in context."
    is infrastructure:StorageFacility
;

reactive agent ReservoirOperator
    "An agent that responds to reservoir conditions through operational actions."
    is management:Operator
;

temperature WaterTemperature
    "Temperature inhering in a water body or water material."
    is imod:Temperature
    within earth:Water
;

process Infiltration
    "Ongoing movement of water into a permeable material."
    is hydrology:WaterMovement
    affects hydrology:WaterContent
;

event Flood
    "A bounded episode in which water inundates normally non-inundated subjects."
    is hydrology:HydrologicalEvent
;

structural relationship DrainsTo
    "A directed structural drainage connection."
    links earth:Region to earth:WaterBody
;

role WaterSupplier
    "A contingent role played by an entity that supplies water in a supply context."
    applies to infrastructure:Reservoir, infrastructure:Utility
;
```

Examples are illustrative. References, type compatibility, local concepts, and clauses must be
checked against the supplied ontologies and active services before claiming validity.

### 10.4 Observable expression patterns

```observable
domain:Predicate domain:HeadObservable;
domain:Quality of domain:Bearer;
type of domain:Identity;
change in domain:Quality;
domain:Relationship linking domain:Source to domain:Target;
```

Units, currencies, and ranges constrain representation rather than replace meaning:

```observable
climate:AirTemperature in degC;
economy:Revenue in EUR@2025;
ecology:HabitatSuitability 0 to 1;
```

Use explicit unit products such as `m*m` or `kg/(m*m*m)`. In the current grammar, `^` is followed
by another unit element, not a numeric exponent, so do not write `m^2` as if it were conventional
exponent syntax.

## 11. Validation gates

### 11.1 Evidence and scope

- Every proposed or reused concept has at least one evidence reference or is explicitly labeled a
  modeling choice.
- Definitions paraphrase sources without importing undocumented claims.
- Contested meanings, jurisdictional variation, and source disagreement remain visible.
- Dataset columns and method steps are not promoted without a domain-semantic rationale.

### 11.2 Tier integrity

- `requested_tier` is a positive integer and is not interpreted as confidence, maturity, or source
  quality.
- Every supplied ontology records its role, tier where applicable, version, and domain scope.
- A Tier-1 target specializes upper concepts from ontologies that do not describe the same domain.
- A Tier-2+ request includes the mandatory authoritative Tier-1 ontology context for the same
  domain and all dependencies needed to interpret it.
- Every target concept is assigned to the requested tier and has a tier rationale.
- Every Tier-2+ target concept has a verified direct parent and a complete specialization path to a
  Tier-1 domain ancestor; relevant intervening tiers are present when that path uses them.
- Upper, Tier-1, and intervening-tier context concepts are reused as dependencies rather than
  duplicated in the target corpus.
- Missing Tier-1 context yields a blocked response with `missing_tier_1_context`, not speculative
  concepts.
- Gaps discovered in Tier-1 ancestry are reported as `upstream_gap` records for upstream governance.

### 11.3 Category integrity

- Every observable has one primary kind, perspective, dependence class, and arity.
- Every quality names plausible bearer types and value kind.
- Every process names its dependent context, participants, affected states, or event when known.
- Every event states temporal boundary and participation criteria when known.
- Every relationship has exactly one typed source and target in the proposal.
- Every bond identifies the participant types it binds while preserving its non-directional
  semantics; it is not assigned an artificial source or target.
- Every configuration states what it emerges from.
- Agents are supported by actual agentive characteristics.
- Roles are contingent; identities are classificatory; attributes are accidental; realms are
  physical/contextual.
- `type of` reifications remain separate from their identity taxonomies.

### 11.4 Orthogonality and ambiguity

- Every concept has exactly one selected sense, primary category, and conceptual dimension.
- Every concept passes the single-meaning, discrimination, compositionality, non-redundancy, and
  category-stability tests, or remains explicitly unresolved and cannot be accepted.
- Polysemous source terms are represented by qualified, separately identified concepts rather than
  one definition that alternates between senses.
- Plausibly overlapping pairs are recorded in the orthogonality matrix and tested for independent
  variation in both directions.
- Taxonomic, dependent, derived, and compositional relations are explicit and are not mislabeled as
  failures of orthogonality.
- No unresolved pair remains `partially_overlapping`, `redundant`, or `conflicting` in an accepted
  corpus.
- Composite meanings use predicates, clauses, context, and unary operators unless the conjunction
  has stable, evidenced, and sufficiently central domain meaning to justify an addressable jargon
  term. Naming such a meaning is not misreported as a new orthogonal dimension.
- No concept is retained only to improve apparent coverage when it reduces clarity or independence.

### 11.5 Composition and alignment

- Parent and clause-target types are compatible.
- `is`, `equals`, `inherits`, predicate composition, and `of` are not conflated.
- Equivalent meanings are reused; composite meanings are not duplicated as opaque atomic concepts.
- Every named compositional meaning preserves its full articulated expression and evidence of
  domain centrality, citation support, and expected use as a unit.
- Every applicable `equals_vs_is_review_required` flag has an explicit reviewer disposition:
  `keep_expression`, `alias_with_equals`, `specialize_with_is`, or `unresolved`.
- `equals` is selected only after equivalence of intension, extension, perspective, dependence,
  context, value space, and predicates has been considered; salience alone never proves equality.
- Causation, emergence, implication, disjointness, and partition completeness have explicit support.
- Authorities preserve externally governed identifiers.
- Every dependency exists, and the concept graph is acyclic and topologically ordered.

### 11.6 Syntax and operational honesty

- Namespace and concept names follow the current lexical rules.
- Candidate expressions have explicit grouping and modifier scope.
- Unary operator operands satisfy the intended semantic constraints.
- Each `.kwv` clause family appears at most once in a declaration.
- Imports and references are available.
- `syntax_status` accurately distinguishes not attempted, illustrative, parsed, adapted, and
  Reasoner-validated states.
- Parser success is never reported as proof of semantic or runtime support.

### 11.7 Review readiness

- Definitions include boundaries, examples, and counterexamples where useful.
- Extraction, category, alignment, unambiguity, and orthogonality confidence are separate.
- Alternatives and open questions are preserved.
- Named-composition decisions and their `equals`-versus-`is` rationale are preserved.
- Each concept and the corpus have feedback containers.
- Stable concept and feedback IDs survive iteration.
- The change log connects accepted feedback to actual changes.

## 12. Required final narrative

Accompany the structured corpus with a concise report containing:

1. scope, requested tier, target community, source policy, and ontology-context manifest;
2. tier validation, including mandatory Tier-1 context and representative ancestry paths;
3. the dominant structural and functional perspectives in the domain;
4. coverage by observable and predicate category;
5. a dependency-ordered walkthrough of proposed concepts;
6. concepts reused from supplied upper/Tier-1/intervening ontologies and newly proposed concepts;
7. upstream gaps that require a broader-tier governance decision;
8. key derivations using unary operators or composition;
9. compositional meanings proposed as named domain jargon, their salience evidence, and pending or
   completed `equals`-versus-`is` decisions;
10. the orthogonality audit, including splits, merges, compositions, and unresolved overlaps;
11. ambiguities, competing articulations, and evidence gaps;
12. validation performed and validation not performed; and
13. specific questions for community review at both concept and corpus level.

The report must not hide uncertainty behind polished prose. A smaller, coherent proposal with
traceable gaps is preferable to a large taxonomy built from weak lexical associations.

## 13. Compact instruction to the analyzing agent

> Analyze the supplied literature as evidence about observable meaning, not as a bag of terms.
> First validate the requested tier. Tier 1 must descend directly from upper ontologies outside the
> domain. Every Tier-2+ request must include the authoritative Tier-1 domain ontology context, and
> every proposed lower-tier concept must expose a complete specialization path to Tier 1. If that
> context is missing, return `missing_tier_1_context` and no concept corpus; never invent the
> foundation. Keep context concepts out of the target list unless their revision is requested.
> Extract independent structural subjects and agents, bounded functional events, dependent
> qualities and processes, binary structural and functional relationships/bonds, emergent
> configurations, and the four refining predicate dimensions: identity, realm, attribute, and
> role. For every candidate, assign perspective, dependence arity, bearer/participants/endpoints,
> value kind, upper alignment, predicates, clauses, evidence, rationale, alternatives, and separate
> confidence scores. Internal unambiguity is a hard gate: split, qualify, or defer every candidate
> with more than one interpretation. Subject to source faithfulness, maximize orthogonality by
> testing plausible concept pairs for independent variation and recording every taxonomic,
> dependent, derived, compositional, overlapping, redundant, or conflicting relation. Prefer reuse,
> composition, and valid unary derivation—especially `type of` for observable reification of
> identities—over redundant or bundled atomic concepts. A crucial, repeatedly cited compositional
> meaning may nevertheless receive a stable domain term: preserve its full expression, document
> source diversity and domain centrality, and flag it for mandatory `equals`-versus-`is` review so
> reviewers can choose a genuine alias, a specialization, or the expression alone. Produce the
> versioned YAML corpus in dependency order, retain unresolved issues, and support auditable
> feedback on each concept and on the overall structure. Treat any proposed k.LAB syntax as
> unverified until it has passed the corresponding parser, adaptation, and Reasoner checks.
