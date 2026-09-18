# Observable expression language guide

For inspection of syntactic and semantic forms, use `reason info <URN>` or
`reason info --syntax <URN>`. See [semantic documentation](SEMANTIC_DOCUMENTATION.md) for
the Markdown endpoint, report contents and asserted/inherited restriction provenance.

An **observable expression** is k.LAB's query language for meaning. It states
what should be observed independently of any dataset, model, service, or
digital twin. The same expression is the semantic key used to annotate and
catalogue assets, declare model outputs and dependencies, select observations,
and route semantic values through behaviors.

This guide follows the active
`org.integratedmodelling.languages.Observable` Xtext grammar. Its parsed
service contracts are `KimObservable` and `KimConcept` in `klab-api`. The
grammar is shared by all three user-facing languages:

| Host language | Use of observable syntax |
|---|---|
| [Worldview ontology language](ONTOLOGY_LANGUAGE.md) (`.kwv`) | Directly in concept definitions and relationships; as `{{ ... }}` literals where a value is expected |
| [k.IM](KIM.md) (`.kim`) | Directly for model outputs and dependencies; as `{{ ... }}` literals in parameters and other value positions |
| [k.Actors](AGENTS.md) (`.kactor`) | Only as `{{ ... }}` semantic literals in values and semantic match patterns |

Observable syntax is not a fourth workflow language. It is the common semantic
sublanguage through which the other three refer to the same meaning.

For semantic resolution, assisted composition and source-bound validation diagnostics, see
the [reasoner documentation](REASONING.md).

## 1. Observable, observation, and model

- An **observable** is a logical specification of something that could be
  observed.
- An **observation** is its contextualized realization in a digital twin.
- A **model** provides a method for a contextualization, including operations on existing observations.

```observable
probability of hydrology:FloodEvent during calendar:Year;
```

This expression does not identify a raster or endpoint. It asks for a meaning.
In a context scope, the Reasoner interprets it, the Resolver finds compatible
strategies, Resources supplies applicable assets, and Runtime produces, retrieves or updates observations. Different contexts may lead to different dataflows
without changing the query.

Observable expressions are consequently the primary semantic catalogue key. A
resource still has a physical URN, but its k.IM models state which observables
it can contribute to. Discovery by meaning can then find resources the
requester did not know by name.

### 1.1 Observing means contextualizing

**Contextualization** is k.LAB's observation process: realizing an observable's
meaning for an observer in a particular spatial, temporal, and semantic context.
It can identify entities, establish connections, assign values, explain a
predicate, or simulate change. It need not be a direct instrumental measurement.
Data retrieval and computation are possible means of observation; the observable
specifies what their result must mean.

The context supplies the frame in which the request makes sense: which entities
are present, which bearer a quality describes, which endpoints a relationship
connects, and which scale and time apply. A model supplies a method; the
observable determines the **kind of observation activity** that method must
fulfil. In ODO-IM this activity is a Description. Different methods can fulfil
the same activity, and one request can require several dependent activities.

### 1.2 From observable meaning to contextualization type

The following table follows the definitions in
[`Contextualization.java`](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/Contextualization.java).
Examples are illustrative expressions or descriptions, assuming compatible
worldview declarations; they are not a tested model catalogue.

| Observable or request | Contextualization | What the activity does | Example |
|---|---|---|---|
| Collective countable substantials, including subjects, agents, and events | `INSTANTIATION` | Creates zero or more instances and triggers acknowledgement of each. | `each earth:Terrestrial earth:Region`; identifying individual flood episodes in a time interval. |
| An individual substantial, including an individual relationship | `ACKNOWLEDGEMENT` | Explains the individual so that its observation fulfils its stated semantics. | Contextualizing one identified region or flood episode. |
| Collective relationships or bonds | `CONNECTION` | Observes the connected substantials and creates connections; each connection requires acknowledgement. | Establishing road connections between cities. |
| A configuration whose emergence has been detected | `DETECTION` | Contextualizes the configuration submitted to the digital twin by the semantic engine. | `infrastructure:RoadNetwork`. |
| A process | `SIMULATION` | Resolves the process, merges its schedule with the digital twin's, and contextualizes it as relevant time transitions occur. | Simulating evapotranspiration through time. |
| A measurable physical quality | `MEASURE` | Assigns a physical quantity with a unit providing its scale. | `climate:AirTemperature in degC`. |
| A numeric quality that is not a measurement or value | `QUANTIFICATION` | Assigns a number without a Unit; a range or proxy scale may apply. | `count of biology:Tree`; `probability of hydrology:FloodEvent`. |
| A monetary or non-monetary value quality | `VALUATION` | Assigns value, absolute or relative to another concept, with a Currency: a monetary unit or bounded rank. | `economy:PropertyValue in EUR@2025/m/m`; a non-monetary preference valuation. |
| A categorical quality, including a `type of` reification | `CATEGORIZATION` | Assigns a concept consistent with the declared value space. | `type of biology:Tree`. |
| A boolean presence quality | `VERIFICATION` | Establishes presence or absence of a substantial. | `presence of biology:Tree`. |
| A predicate with distributed substantial inherence: `P of each S` | `CLASSIFICATION` | Obtains S members and attributes P itself if concrete, or a concrete specialization; then characterizes each new attribution. | `Species of each Tree` identifies species; concrete `Deciduous of each Tree` can attribute Deciduous. |
| A predicate with individual substantial inherence: `P of S` | `CHARACTERIZATION` | Explains the predicate within S, using an exact or subsuming predicate model. P may be abstract or concrete. | `Deciduous of Tree`: explain that trait for the tree; a broader vegetation-trait model may supply the explanation. |
| A concrete predicate attributed to a quality | `TRANSFORMATION` | Transforms the quality so that it expresses the trait or role. | A normalization trait applied to a numeric quality, where the worldview defines that trait and its transformation. |
| A non-functional, abstract, or inconsistent request that cannot produce an observation | `VOID` | Produces nothing. | A bare predicate without an inherent observable. |

The predicate examples use schematic names to make the semantic distinctions
visible. `Predicate of Bearer` requests an activity about that predicate;
`Predicate Bearer` qualifies the bearer being requested. They are not
interchangeable. Likewise, a `type of` quality has a concept as its **value**;
classification instead adds a concrete predicate to the substantial's
**semantics**.

### 1.3 Creation, explanation, and time

`INSTANTIATION`, `CONNECTION`, and `CLASSIFICATION` are collective activities in
the enum's sense: they create zero or more target observations or predicate
attributions. Completion includes resolving their results. The contract requires
`INSTANTIATION` and `CONNECTION` to trigger `ACKNOWLEDGEMENT`, and
`CLASSIFICATION` to trigger `CHARACTERIZATION`; these follow-ups belong to the
implementation, not to user-authored observation strategies. The other types
are resolution activities: they explain an observation or characteristic.
Neither acknowledgement nor classification is defined by producing a numeric
data array.

For classification, `PREDICATE of each SUBSTANTIAL` first resolves the
collective substantials. Without `each`, the request is CHARACTERIZATION within
the inherent substantial, independently of predicate abstraction. Attributed predicates do not move them to
different cohorts, although an individual identity can support new cohorts
collecting its bearers. Connection follows the same distinction for its
endpoints: resolve a requested collective or use existing contextual instances.

A bounded event and a process are different requests. Identifying flood episodes
instantiates countable events; simulating water movement contextualizes a
process. The process contract says it is resolved but not initialized before
computation starts; its execution follows the merged digital-twin schedule.
Requesting the probability or presence of a flood changes the semantic head to
a quality, hence `QUANTIFICATION` or `VERIFICATION`. Presence of a process is
accepted as shorthand for presence of any event subsuming that process.

A configuration is also distinct from its constituents. A configuration built
from detected qualities is local to the observation holding those qualities.
One built from relationships is global to the digital twin and can change as
new observations are made under the same observer.

**Dispatch rule:** a predicate without a bearer gives `VOID`; a quality bearer gives
`TRANSFORMATION`. For a substantial bearer, `of each` selects `CLASSIFICATION` and `of` selects
`CHARACTERIZATION`. Abstractness does not select the activity. It constrains attribution: only a
concrete, satisfiable predicate can be added to a substantial's semantics. Classification preserves
identity and cohort membership, then Runtime schedules characterization inside each newly
classified member. A member already bearing a valid matching predicate needs no new classification.

The member-resolving classifier strategy is Tier 0. Classification and individual characterization
execute through semantic-update Dataflow nodes. An enum value alone does not establish support for
other workflows such as arbitrary root directive submission.

### 1.4 Existence, attribution and explanation

A substantial can exist without an explanatory model. Instantiation resolves each new member,
but an error-free absence of an explanation is successful acknowledgement, represented by a
non-empty, computation-free Dataflow with outcome `NO_MODEL`. It is not failed observation.
Likewise, classification remains valid when discovery finds no model explaining the newly
attributed concrete predicate. Errors in resolution or in an explanation that actually runs
remain failures.

For example, abstract `earth:PhysicalEnvironment of each earth:Region` first obtains Region
members and determines a concrete environment predicate for each. A result such as
`earth:Freshwater` enriches the same Region's semantics; it preserves identity, geometry and cohort
membership. Runtime then requests `earth:Freshwater of earth:Region` within that member. A
`type of` quality instead stores a concept as a value: it does not make this semantic attribution.

A classifier result must be a consistent concrete predicate equal to or specializing the requested
predicate. A concrete predicate with no children is a valid result for itself. An abstract predicate
may be requested, but neither it nor any abstract descendant can be attributed; unsatisfiable
concepts are always invalid.
The classifier receives the full observable and chooses the predicate projection needed to query
its closure. A null result may leave a member unchanged only for an optional original model
dependency with a resolved classifier; inconsistency never means optional absence.

Attributions and their explanations share the enclosing transaction. Semantics and typed
provenance become durable together; a later failure cannot leave only part of the classification
batch applied. See [resolution workflows](RESOLUTION.md#17-semantic-update-contextualizations) and
[semantic attribution transactions](KNOWLEDGE_GRAPH.md#semantic-attribution-transactions).

### 1.5 Choosing a characterization model

A model of `P of S` can characterize that predicate or a more specific predicate on a compatible
bearer. This applies whether P is abstract or concrete. Model discovery includes subsuming
predicate heads; semantic distance favors the exact match, then progressively broader applicable explanations.
This is a general model-ranking criterion: inherency and other clauses also contribute for
non-predicate observables. The configured criterion order applies; by default lexical scope precedes
semantic distance. The examples below assume equal higher-priority ranking criteria. Inherency, scope and other
semantic restrictions must still match. Non-predicate observables retain the exact-head rule:
a model of a different quality subclass does not become an interchangeable measurement.

Suppose `P2 is P1` and `P3 is P1`, with models for `P1 of S` and `P2 of S`:

| Member attribution | Characterization request | Preferred model |
|---|---|---|
| `P1 S` (if P1 is concrete) | `P1 of S` | `P1 of S` |
| `P2 S` | `P2 of S` | `P2 of S` (exact match) |
| `P3 S` | `P3 of S` | `P1 of S` (subsuming match) |

If P1 is abstract, `P1 S` cannot be created by classification, but the `P1 of S` model remains a
valid general explanation for concrete P2 and P3. Models of narrower or unrelated predicates do
not explain a broader requested predicate. `P of each S` classifier models and `P of S`
characterizer models are different operations and are not interchangeable.

### 1.6 Connecting substantials

The Tier-0 `relationships.direct` strategy handles `CONNECTION`: it resolves the
collective source and target types, then uses `observe` with named `source` and
`target` inputs to discover a connector model. Endpoint coverage alone is not a
connection result. The model chooses which pairs to connect and may emit zero
instances; Runtime does not construct a Cartesian product.

Implementations emit individual observations through
`Data.Builder.relationship(name, observable, geometry, identity, source, target)`.
Direct observation construction also supports `Observation.Builder.between(...)`.
Both participants must be distinct individual substantials in the current context.
The relationship has its own identity, geometry, provenance and cohort membership.
A free request uses the current observer's perceived geometry.

Runtime acknowledges every new result individually in a scope focused on the
producing collective for registration and contextualized through
`ContextScope.between(source, target)` for resolution and execution. It preserves
the producing model's lexical constraints. A missing explanatory model is valid
acknowledgement; an execution failure fails the enclosing transaction.

Directed relationships persist separate source and target edges. Bonds, represented
semantically as `RELATIONSHIP + BIDIRECTIONAL`, persist two unordered participant
edges. See [relationship observations in the knowledge graph](KNOWLEDGE_GRAPH.md#relationship-observations).
The `klab.component.generators` function `klab.generators.random.relationships`
provides percentage-based endpoint sampling and seeded random geometry for tests.

## 2. Concepts and predicates

A worldview concept is written as `namespace.path:ConceptName`:

References in Worldview declarations must also use this qualified form, even
when the referenced concept belongs to the same ontology namespace.

```observable
earth:Region;
hydrology.physical:WaterFlow;
```

The namespace is a lower-case dotted path and the concept name begins with an
upper-case letter. A concept may be a complete query or be composed with other
concepts and semantic operators. Every expression has one main observable;
other concepts constrain or qualify it.

k.LAB describes observable concepts by dependence and perspective:

| Dependence | Structural perspective | Functional perspective |
|---|---|---|
| Independent | subject or other substantial entity | event |
| Dependent | quality | process |
| Relational | structural relationship or bond | functional relationship |

Configurations describe recognized, observer-dependent patterns emerging from
other observations. Worldviews also define agents, extents, identities, realms,
roles, attributes, domains, quantities, and classes. See the
[worldview guide](ONTOLOGY_LANGUAGE.md) and [ODO-IM](ODO_IM.md).

Predicates refine an observable but do not normally stand alone as an
observation request:

```observable
biology:Eucalyptus biology:Tree;
(ecology:AboveGround ecology:Biomass) of biology:Tree;
```

The active worldview determines whether such sequences are logically
admissible. Parsing alone cannot establish that.

## 3. Observable statements

The base grammar can parse a stand-alone sequence of semicolon-terminated
statements. Observable expressions are not normally published as a separate
k.LAB asset type; deployed declarations are embedded in `.kwv`, `.kim`, or
`.kactor` host documents.

```ebnf
concept-expression
  [observed as concept-expression]
  [in unit | in currency | range-min to range-max]
  [named local-name]
  [optional | required]
  [inline-metadata ...]
;
```

These clauses are unordered in the grammar. The order above is recommended:

```observable
climate:AirTemperature
  observed as earth:AtmosphericCondition
  in degC
  named air_temperature
  required
  :source-kind "station"
;
```

### 3.1 Units, currencies, and ranges

```observable
geography:Elevation in m;
climate:PrecipitationVolume in mm;
economy:Revenue in EUR@2025;
economy:PropertyValue in EUR@2025/m/m;
ecology:HabitatSuitability 0 to 1;
```

A currency requires a reference year. Units may contain multiplication,
division, `^` connectors, and parenthesized parts, such as `kg/(m*m*m)` and
`(J/s)/(m*m)`. In the current grammar, the operand after `^` is a unit element,
not a numeric exponent, so use explicit products rather than forms such as
`m^2`. A numeric range is an alternative to a unit or currency.

Mediators constrain representation; they do not replace meaning. Compatible
unit conversion must remain visible in dataflow provenance.

### 3.2 Observation semantics

`observed as` states an observation lens:

```observable
ecology:LandCoverClass
  observed as remote_sensing:SatelliteObservation;
```

This is distinct from inherency expressed with `of` and from a runtime user or
actor. Its validity depends on the worldview.

### 3.3 Names and optionality

`named` gives a host construct a lower-case local identifier. `optional` and
`required` are most useful on k.IM dependencies:

```kim
model hydrology:WaterBalance
  observing
    climate:PrecipitationVolume in mm named precipitation required,
    hydrology:EvapotranspirationVolume in mm named evapotranspiration optional
  set to [precipitation - evapotranspiration]
;
```

These clauses express host-level resolution needs; they do not change the
concept.

### 3.4 Inline metadata

```observable
climate:AirTemperature :preferred true :source-kind "station";
hydrology:DischargeRate !deprecated;
```

Keys beginning with `:` may carry a value. Keys beginning with `!` are negative
flags. Metadata is extensible; do not use it to hide distinctions that belong
in the semantics.

## 4. Concept-expression base forms

### 4.1 Plain sequences and grouping

```observable
geography:Elevation;
biology:Eucalyptus biology:Tree;
(ecology:AboveGround ecology:Biomass) of biology:Tree;
```

Parentheses group an expression so a later modifier applies to the whole. Use
them whenever scope would otherwise be unclear.

### 4.2 Distribution

```observable
each biology:Tree;
count of biology:Tree;
```

`each` requests individual or distributed observations. The second expression
requests one derived count. Validity depends on concept type and context.

### 4.3 Unary operators

A unary operator applies to the complete terminal concept sequence immediately
after it. Predicates in that sequence qualify its operand before the operator
is applied. Predicates before the operator qualify the resulting observable:

| Expression | Grouping and meaning |
|---|---|
| `presence of ecology:Managed biology:Tree` | `presence of (ecology:Managed biology:Tree)`: presence of managed trees |
| `ecology:Managed presence of biology:Tree` | `ecology:Managed (presence of biology:Tree)`: the attribute qualifies the presence quality |

The second form requires an attribute compatible with the resulting quality;
syntactic grouping does not make an incompatible attribute valid. Parentheses
explicitly delimit an operand. Binary modifiers retain their existing grammar
scope: a modifier following an unparenthesized unary expression qualifies that
expression; put it inside the operand's parentheses to qualify the operand.
These rules do not change the associativity of other observable constructs.

| Syntax | Typical intent |
|---|---|
| `presence of X` | whether or where X is present |
| `magnitude of X` | magnitude associated with X |
| `distance to X` or `distance from X` | distance relative to X |
| `probability of X` | probability of X |
| `change in X` | change in X |
| `change rate of X` | rate of change |
| `uncertainty of X` | uncertainty associated with X |
| `level of X` | level representation |
| `type of X` | classification of X |
| `occurrence of X` | occurrence of an event or process |
| `count of X` | count of instances |

```observable
presence of biology:Tree;
distance to infrastructure:Road;
probability of hydrology:FloodEvent;
change rate of climate:AirTemperature;
uncertainty of ecology:HabitatSuitability;
```

Operators are semantic, not merely syntactic functions. Their valid operands
and inference behavior come from the worldview and Reasoner.

### 4.4 Explicit change

```observable
changed ecology:LandCoverClass;
changed ecology:LandCoverClass
  from ecology:Forest
  to ecology:UrbanArea;
```

This differs from `change in X`: `changed` describes a transition and may
constrain its endpoints.

### 4.5 Proportion, percentage, and ratio

```observable
proportion ecology:Forest in earth:Region;
percentage ecology:Wetland in earth:Watershed;
ratio of ecology:Input to ecology:Output;
```

The `in` operand of proportion or percentage states the whole. Do not insert
`of` after those keywords; it is not in the current grammar. A ratio requires
both operands.

### 4.6 Value

```observable
value of ecology:Pollination;
monetary value of ecology:Pollination;
monetary value of ecology:Pollination over agriculture:Crop;
```

`over` here states the comparison or beneficiary operand. It differs from the
numeric `over number` value operator.

## 5. Semantic modifiers

Expressions may chain binary modifiers:

| Operator | Relationship expressed |
|---|---|
| `of [each] X` | inherency or attribution |
| `and X` / `or X` | intersection or union |
| `causing [each] X` / `caused by [each] X` | causal direction |
| `for [each] X` | goal or beneficiary |
| `adjacent to [each] X` | adjacency |
| `contained in [each] X` / `containing [each] X` | containment |
| `with [each] X` | compresence |
| `during [each] X` | temporal co-occurrence |

```observable
ecology:Biomass of each biology:Tree;
hydrology:WaterFlow caused by climate:Precipitation;
ecology:HabitatSuitability for biology:Species;
infrastructure:Road adjacent to hydrology:River;
biology:Tree contained in earth:Forest;
hydrology:FloodEvent during time:AnnualPeriod;
```

The grammar also admits another concept expression as a modifier, enabling
predicate sequences and higher-order composition. Prefer explicit parentheses
in complex expressions.

### 5.1 Relationship endpoints

```observable
infrastructure:TransportLink
  linking geography:Origin
  to geography:Destination;
```

The head must have relationship semantics in the active worldview.

## 6. Value operators

Value operators constrain or transform values. They are part of the concept
expression and precede statement clauses such as `in unit` and `named`.

### 6.1 Comparisons

```observable
geography:Elevation > 500.m;
climate:AirTemperature >= 0.degC;
ecology:HabitatSuitability <= 0.8;
hydrology:DischargeRate == 12.m*m*m/s;
```

Supported comparisons are `>`, `>=`, `<=`, `<`, and `==`. The operand is a
number or quantity; quantities join a number to a unit or currency with `.` or
`/`.

### 6.2 Semantic and contextual filters

```observable
ecology:LandCoverClass is any ecology:Forest;
presence of biology:Tree when climate:PrecipitationVolume > 0.mm;
biology:Tree whose ecology:Biomass > 100.kg;
biology:Tree without ecology:Disease;
```

The grammar provides `is`, `where`, `when`, `whose`, `without`, `by`, and
`down to`. Their condition operand is itself a concept expression. A grammar
comment calls for conditions to contain a value operator, but the current
parser rule does not enforce that restriction.

### 6.3 Aggregation and existence

```observable
hydrology:WaterVolume total;
presence of biology:Tree exists;
climate:AirTemperature averaged;
hydrology:RunoffVolume summed;
```

Applicability depends on observable type and scale. Parseability is not proof
of semantic validity.

### 6.4 Arithmetic transforms

```observable
statistics:Index plus 1;
statistics:Score minus 10;
statistics:Score times 100;
economy:Amount over 1000;
```

Use these only when rescaling is part of the requested observable. Model
implementation math normally belongs in k.IM contextualization.

The grammar also admits a bare `!=` with no operand and specialized `by` and
`down to` forms. Treat these as provisional until validator and runtime
contracts define their intended use.

## 7. References, authorities, and selectors

A reference is a worldview concept, an authority identity, or a pattern
variable:

```observable
hydrology:River;
IUPAC:water;
GBIF:2435099;
presence of $target;
```

Authority prefixes are upper case and require an available authority component.
Parsing does not prove that the authority can resolve the identifier.

`not` may prefix a deniable attribute:

```observable
not ecology:Managed ecology:Forest;
```

The worldview must declare that attribute deniable.

The selectors `any`, `all`, and `no` alter matching:

- `any X` selects X or its children;
- `all X` enables generalized, primarily model-side matching; and
- `no X` selects compatible siblings without X or its descendants.

They are query selectors, not new concepts. Pattern variables require
substitution before an expression becomes concrete; `KimObservable` exposes
the pattern and variable collection.

The syntax and runtime semantic types retain `any`, `all`, and `no` as
`SemanticType.ANY`, `SemanticType.ALL`, and `SemanticType.NONE`, respectively.
Preserving these flags does not by itself implement every matching operation.

### 7.1 Declared status

Declared `abstract`, `subjective`, and `sealed` status is retained in the
corresponding semantic type flags and in concept references. Abstract status
is local to a declaration: a child of an abstract concept is concrete unless
that child explicitly declares `abstract`. Creating a restricted subclass does
not inherit the parent's `ABSTRACT` flag. Copies and observable promotion
preserve the status already established for the concept.

Every concept in the core ontology is automatically abstract. This is an
intentional convention; it does not make all descendants abstract. Core aliases
resolve the canonical core concept.

### 7.2 Status of composed expressions

The following first-pass rules apply independently to both `ABSTRACT` and
`SUBJECTIVE`. An expression retains its head's status unless a unary operator
transforms that head. A direct attribute carrying either flag gives that flag
to the expression. Clause fillers, including the filler of `of`, do not
contribute either flag to their bearer. Neither the primary operand nor a
comparison operand of a unary operator contributes status to its result.

For a concrete, non-subjective `X` and an abstract, subjective attribute `A`:

| Expression | Abstract | Subjective |
|---|---|---|
| `A X` | yes | yes |
| `X of A` | no | no |
| `presence of (A X)` | no | no |
| `A presence of X` | yes | yes |

The examples describe scope; the worldview must still allow the attribute and
clause types. These are expression rules, not subclass declaration inheritance.
They do not add propagation from identities, realms, roles, or logical operands;
policies for those combinations remain separate. Runtime evaluation uses resolved
atomic declaration status and projects the result onto returned concepts and
observables without changing canonical declarations.

This describes the current shared grammar. The
[observation-strategy proposal](OBSERVATION.md#45-a-dedicated-pattern-language-with-ordinary-observable-matches-retained)
recommends moving structural patterns, captures, and variable-based construction into the
Observation language, with a delimited `pattern { … }` form and typed constructors. Ordinary
observables remain valid strategy matches. That proposal has not changed the grammar; ordinary
selectors, logical observables, and semantic literals retain their meanings. Pattern-level
boolean tests must be distinguished from `and`/`or` in the observable itself.

## 8. Semantic literals in host languages

Where a host grammar expects a value, wrap observable semantics in double
braces:

```text
{{ <observable-semantics> }}
```

No semicolon appears inside the braces.

### 8.1 k.IM

k.IM uses observables directly for model outputs and dependencies, but braces
in literal and parameter positions:

```kim
@documentation(subject = {{probability of hydrology:FloodEvent}})
model probability of hydrology:FloodEvent;

define elevation_query as {{geography:Elevation in m}};
```

### 8.2 Worldview ontologies

Worldview clauses use expressions directly:

```kwv
process Flooding
  affects geography:Region
  emerges from probability of hydrology:FloodEvent
;
```

Annotations, maps, and other value positions can carry `{{ ... }}`.

### 8.3 k.Actors

k.Actors accepts observables only as semantic literals:

```kactors
action main:
    def query {{probability of hydrology:FloodEvent}}
    runtime.observe(query)
```

They can also be semantic match patterns:

```kactors
runtime.observe({{each biology:Tree}}):
    {{biology:Tree}} as tree -> console.info(tree)
```

The runtime decides whether the literal acts as a query, classifier, message
value, or observation request.

## 9. Annotations and shared literals

Derived host grammars can accept one or more annotations before an observable.
For example, k.IM model outputs are `AnnotatedObservable` values:

```kim
model @predictor(weight = 0.7)
  distance to infrastructure:Road;
```

A stand-alone `ObservableSequence` does not accept annotations before its
statements.

The shared grammar also defines numbers, ranges, quantities, strings, Booleans,
lists, maps, concepts, functions, parameters, and URNs for derived languages.
The host grammar still decides which value rule is accepted in each position:

- `Value` admits naked concept references;
- `Literal` admits `{{ observable }}` but not naked concept expressions; and
- `Variable` adds identifiers and constants.

## 10. Contextualized expressions

The shared grammar defines this host-only construct:

```ebnf
concept-expression [within concept-expression]
```

```kwv
quality UpstreamArea
  is geography:Area within hydrology:Watershed
;
```

`within` is not part of a stand-alone observable statement. It is accepted only
where the host asks for a `ContextualizedExpression`.

## 11. Common query patterns

```observable
// Direct concept
geography:Elevation;

// Qualified and distributed quality
(ecology:AboveGround ecology:Biomass) of each biology:Tree in kg;

// Derived observables
presence of biology:Tree;
count of biology:Tree;
probability of hydrology:FloodEvent;

// Relational query
infrastructure:TransportLink
  linking geography:Origin
  to geography:Destination;

// Filtered individual query
each biology:Tree whose ecology:Biomass > 100.kg;
```

In a model, the same language annotates a resource:

```kim
model urn:klab:agency:elevation:global:dem
  as geography:Elevation in m
;
```

The URN identifies the asset; the observable says what it can contribute.
Semantic discovery and ranking need both.

## 12. Authoring guidance

- **Express meaning before implementation.** Write what a user would ask for,
  not the filename, algorithm, provider, or storage layout.
- **Use the active worldview.** Reuse stable concepts and import the namespaces
  required by the host document.
- **Parenthesize complex expressions.** Human review should not depend on
  guessed operator scope.
- **Separate semantics from mediation.** Put meaning in the expression and
  units or currency in `in`.
- **Keep metadata secondary.** If a distinction affects compatibility or
  resolution, represent it semantically rather than only as a tag.

## 13. Review checklist

Check that:

- the expression has one clear main observable;
- every namespace and authority resolves under the intended worldview;
- predicates and operators are valid for their operands;
- parentheses make complex scope unambiguous;
- `each` reflects the intended distributed observation;
- units, currencies, ranges, and filters are compatible with the semantics;
- optionality and local names serve only their host-level purpose;
- `within` appears only where the host admits contextualized expressions;
- `{{ ... }}` is used whenever the host expects a semantic literal;
- metadata does not substitute for semantic distinctions; and
- both the parser and Reasoner validate the expression.

## 14. Current implementation status

The grammar defines source structure; full validity requires semantic services:

- `ObservableSemanticsForCondition` accepts any concept expression although its
  comment describes a required value operator;
- the bare `!=` form has no operand;
- numeric exponents such as `m^2` are not accepted by the current `Unit` rule
  because `^` is followed by a unit element;
- authorities, units, and semantic relationships need runtime registries or
  worldview reasoning;
- `KimObservableImpl.namespaces()` currently returns an empty set, and its
  formatter omits units, currency, range, formal name, and optionality; and
- parser acceptance does not prove that a compatible model, resource, adapter,
  or runtime exists.

Language evolution must keep `Observable.xtext`, `KimConcept`,
`KimObservable`, adapters and validators, Reasoner tests, and this guide in
sync. Because `.kwv`, `.kim`, and `.kactor` inherit this grammar, regression
fixtures should cover stand-alone expressions and every host embedding form.
