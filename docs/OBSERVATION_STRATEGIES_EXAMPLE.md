# Running strategy compatibility example

This is the comparative companion to [the observation proposal](OBSERVATION.md). The maintainer
supplied the only strategy document currently used and reports that its simpler strategies work,
while others may not. Treat that as usage evidence, not a claim that every clause works in the
inspected checkout. No supplied strategy was executed during this documentation stage.

The baseline files are:

- [Supplied version 1.0 source](examples/observation-strategies/observations-1.0.source.txt), retained
  with its names, ranks, comments, and spelling, including unresolved TODOs.
- [Proposed translation](examples/observation-strategies/observations-proposed.txt), a design fixture
  covering all eight named strategies. Its `.txt` extension prevents it from masquerading as a
  deployable document. Its syntax now parses and adapts in the Observation regression suite;
  its strategy semantic beans also round-trip through the registered Jackson interfaces.
  The tier-0 forms also exercise initial Reasoner/Resolver lowering and dataflow compilation in
  `ObservationPipelineTest`, with external services replaced by doubles. Rank-1 composition and
  live runtime execution remain pending; see OBSERVATION S3c.
- [Draft Observation.xtext](grammar/Observation.xtext) and [grammar notes](grammar/README.md),
  maintained alongside the translation. Revision 0.3 uses `field = value` and
  `capture name as pattern`; the generated parser passes the translation and its normalized
  encode/reparse check. See the S3 progress record for syntax-bean validation evidence.

**Confirmed by the maintainer:** commas between match alternatives mean **disjunction**. Do not
reinterpret `for Subject, Agent, Relationship` as requiring all three categories. The existing
legacy syntax adapter's comma-to-`ALL` mapping conflicted with this intent. The revised AST and
Reasoner implement OR deliberately. Commas inside argument
lists or a list of guard checks are separators, not automatically disjunctions; those productions
need their own explicit contracts. The meaning of the legacy semicolon match connector remains
an S1 decision, not something established by the comma clarification.

## 1. What must be preserved, repaired, or decided

The translation preserves the eight strategy identities and ranks. It translates **intended
observation behavior**, not every implementation accident. It replaces implicit graph assembly
with named graph outputs and validated bindings. Proposed validation may reject requests that
the current loose matcher accepts accidentally; such changes must have a documented reason.

| Stable case | Supplied strategy | Intended behavior | Current source limits | Proposed translation |
|---|---|---|---|---|
| EX01 | `substantial.direct`, rank 0 | Explain an individual subject, agent, or relationship directly; allow acknowledgement without a model where legitimate | `optional` is not a reliable strategy-level no-solution policy; Resolver's trivial fallback checks non-collective `SUBJECT`, not every named substantial kind | Explicit singular type alternatives and full-request lookup; lifecycle-owned no-model acknowledgement after alternatives, with eligible kinds reviewed |
| EX02 | `dependent.direct`, rank 0 | Directly explain a concrete quality or process in context | The comment claims a `$context` test, but the body does not contain one; source lookup does not enforce an explicit exact-whole-match mode | Explicit context guard and full-request lookup; no dependent observation in an absent context |
| EX03 | `countables.direct`, rank 0 | Instantiate concrete subjects, agents, or events, then acknowledge outcomes | Runtime implements instantiation follow-up; strategy must not duplicate it; category guards do not mean every operator-derived observable remains countable | Collective shape plus full-request lookup; required follow-ups stay in Runtime |
| EX04 | `relationships.direct`, rank 0 | Resolve collective endpoint subjects and observe their relationships | Endpoint extractor functors are absent from the inspected built-in library; generic union of three child graphs does not bind roles; connection follow-up throws an unimplemented exception | Named endpoint graphs passed to named producer ports; the selected connection model chooses actual links; acknowledgement belongs to Runtime |
| EX05 | `countable.union`, rank 1 | Resolve disjunctive countable branches and union their instances | Operator splitter returns the original concept twice; logical matching is ordered head/tail; `apply` has incomplete extraction/lowering; semantic arguments are not graph references | Capture first/remainder structurally, retain collective semantics, recursively resolve named graphs, union members by stable identity |
| EX06 | `enumerable.quality.union`, rank 1 | Union boolean or categorical results | `type.categorical` is absent from the inspected built-in library; value union behavior is not established; same splitter/`apply` gaps | Typed operand compatibility guard and a binary logical-union signature selected by value space |
| EX07 | `enumerable.quality.intersection`, rank 1 | Intersect boolean or categorical results | Uses `odo:EnumerableQuality` instead of the `imod` aliases, lacks a concrete/context check, and has the same splitter/`apply` gaps | Typed enumerable compatibility guard, explicit context/concreteness policy, and logical-intersection signature |
| EX08 | `quality.split.predicate`, rank 1 | Resolve an unqualified quality and transform it through its predicate model | Trait splitter uses `findAny`, excludes roles, and has no typed tuple contract; transformation inputs are inferred from names/tags | Deterministic semantic split, named base/transformer graphs, explicit open port, typed `transform` merge |

The code audit behind these statements is [OBSERVATION.md, Section 2](OBSERVATION.md#2-current-source-trace).
The current built-in `TypeFunctors` also lacks the supplied `observations.objects.union` and
`observations.values.*` runtime implementations in the inspected Reasoner/Runtime source trees.
External extensions may supply them; absence here is not proof they are unavailable in the
maintainer's installation. Pin the actual worldview and component versions before runtime tests.

## 2. Translation conventions and small proposal additions

The proposed file uses the pattern vocabulary and graph operations in
[Section 4](OBSERVATION.md#4-proposed-strategy-language-and-intermediate-representation), plus the
following explicit design contracts. These names are proposed capabilities, not claims about an
existing functor registry:

| Form | Meaning |
|---|---|
| `kind = one_of(subject, agent, …)` | OR over semantic kind tests; kinds can overlap through inheritance without creating duplicate strategy matches |
| `ensure f(), g()` | Post-match/setup pure guard checks combined by AND; any false check rejects applicability before graph production; errors remain diagnostics |
| `request.fully_specified($this)` | Concrete, consistent, sufficiently specified request with a valid activity and complete required semantic roles; does not remove predicates or operators |
| `context.exists()` | Test the actual current context binding; does not require a fictitious `$context` occurrence in a semantic expression |
| `collective(x)` | Idempotently construct the collective request, retaining all other semantic structure |
| `relationship.source/target(x)` | Extract declared endpoint semantics with roles intact; missing endpoints fail validation |
| `observe … with inputs(source = sources, target = targets) to connections` | Bind named graph outputs to typed producer input ports; unresolved model dependencies use ordinary resolution, but these supplied ports must not be independently rediscovered |
| `operands.same_countable_family(a,b)` | All logical leaves belong to one of the original subject/agent/event/relationship families; mixed-family unions are not silently added by this migration |
| `operands.enumerable_compatible(a,b)` | Boolean or categorical operands admit a reviewed value-space signature, compatible bearer/support, and valid result semantics |
| `predicates.split_first(x)` | Deterministic tuple of removable predicate and complete remaining observable; no mutation or loss of mediators/operators |
| `predicate.concrete`, `transform.applicable` | Predicate and base admit a transformation contract; abstract classification is a different case |

The `with inputs` addition is general: it supplies already produced graphs to a model-search
operation with declared input roles. It is not a relationship-only instruction. It does not
weaken binary merge: a producer may have several named dependencies, while each semantic merge
still has exactly two operands. The final connection graph depends explicitly on both endpoint
graphs, so it is the sole strategy result and the earlier producers are not dangling outputs.
Bind roles by the validated model contract, not by the first input tag or by assumed names in
arbitrary component functions. Model search and validation must check the supplied role contracts.

Guard syntax makes the earlier proposal's post-setup guard concrete, reusing the currently
unimplemented `ensure` spelling. Guards run in source dependency order after pattern success;
they may follow the `let` bindings they reference, but must all precede graph production.
Reserved `$this` and `$context` cannot be rebound by `let`, captures, or context/member variables.
Ordinary bare observable matches remain available; `one_of` merely makes this fixture's disjunctions
explicit without importing foundational aliases into its type tests.

## 3. Direct strategies and semantic lifecycle

### EX01: optional explanation is not optional existence

The original `observe $this optional` should not become an empty successful graph when model
lookup fails. The translated producer returns a normal graph or no solution. After applicable
strategies are exhausted, the semantic lifecycle may acknowledge an eligible existing individual
without computational explanation. That fallback needs an explicit eligibility contract and
must not swallow an invalid plan, component exception, or failed execution.

Current `ResolverService.resolve` permits a trivial dataflow for a non-collective observable with
the `SUBJECT` flag. Determine through worldview fixtures whether agents inherit that flag and
which other kinds legitimately admit unexplained acknowledgement. In particular, do not assert
that an individual relationship exists without establishing its endpoints and identity.
The original direct individual alternatives omit `Event`; adding individual-event coverage is a
separate completeness decision rather than an unnoticed change in this translation.

### EX02–EX03: match the full request and preserve standard follow-ups

The dependent guard repairs the mismatch between the comment and body. Quality and process
resolution still produce different activities; accepting both does not mean interpreting every
model as a numerical measurement. A temporal process requires its schedule contract.

Countable instantiation retains predicates and logical structure when seeking a full-meaning
model. Operators that change the semantic head, such as `count of` or `presence of`, produce
quality requests and belong under the appropriate quality route. No explicit deferred block is
added for acknowledgements. If instantiation returns a complete zero-member collection, that is
a successful empty answer, not failure to find an instantiator.

### EX04: prerequisites reveal a rank-zero policy issue

The supplied relationship strategy is not a single-producer strategy: it resolves endpoints
before observing the whole relationship. This conflicts with a literal “rank zero has exactly
one producer” rule. Recommended policy: **rank zero uses a direct model for the entire requested
output, but may prepare mandatory contextual inputs through explicit bindings**. Recursive
endpoint resolution is such preparation; decomposing the output into alternative meanings remains
a positive-rank fallback. Record this distinction in S1 instead of silently changing its rank.

Resolve the two endpoint collections without conflating their roles, even when both have the
same semantics or share instances. The connection model determines qualifying pairs; the strategy
does not prescribe a Cartesian product, self-links, or reverse edges. A complete empty endpoint
collection can yield a complete empty relationship collection only when the connection contract
supports that conclusion. Missing endpoint coverage is not evidence that no relationships exist.
Runtime must acknowledge each created relationship before reporting required lifecycle completion.

The legacy strategy always requests collective endpoints. Preserve that behavior in this fixture.
Using existing singular endpoints or non-directional bonds belongs in additional fixtures using
the reviewed scope/port contracts; do not force directional source/target semantics onto a bond.

## 4. Logical composition

### EX05: collective union

The old two-output `type.operator.splitfirst` name obscures that the second result may be an
entire remainder expression. The proposed pattern captures `first` and `remaining` and explicitly
restores collective status for both recursive requests. Three or more disjuncts therefore reduce
progressively without repeating the original request. Preserve modifier scope across decomposition;
if it cannot be preserved unambiguously, reject the decomposition rather than drop the modifier.

Example: the left graph returns individuals `{a,b}` and the right `{b,c}`. The terminal
`logical-union` returns `{a,b,c}` under the union observable, with `b` represented once by identity
and retaining provenance from both branches. It must not arbitrarily assert both predicates on
every member. Both branch discovery obligations must be complete for a complete union answer;
one graph's full spatial footprint does not prove the other branch has no additional individuals.

### EX06–EX07: booleans and categories require different signatures

The source comment explicitly includes booleans and categories; a predicate named `categorical`
must not accidentally exclude booleans. The translation uses a typed enumerable guard rather
than that undocumented test. It also replaces the isolated `odo:EnumerableQuality` reference
with a portable structural/value-space check. This does not assert that the supplied alias is
invalid; its availability and allowed use depend on the actual worldview project.

For booleans with known values, use ordinary OR and AND. The recommended unknown-value policy
for S1 review is three-valued logic: `true OR unknown = true`, `false AND unknown = false`, while
`false OR unknown` and `true AND unknown` remain unknown. A proven cell result may be known even
when one input is unknown; data validity must follow the operator's truth table. This is separate
from mandatory model resolution and from the completeness of a collection search.

For categorical qualities, generic set union/intersection is **not automatically valid**. A
categorization normally yields one concept, not an arbitrary set of labels. S1 must select a
supported signature: for example, an explicit set-valued result space with a legitimate observable,
or a declared mapping from combined input concepts to one concept in a declared output space.
Two different scalar categories cannot be silently turned into a list, chosen by traversal order,
or replaced by an arbitrary superclass. Thus both categorical translations are structurally
complete but their value policy remains a review gate. Until approved, incompatible categorical
combinations must fail clearly; they must not be advertised as working union/intersection.

## 5. Predicate transformation

EX08 is the closest match to the current working transformation path. Preserve direct whole-meaning
lookup at rank zero, then use rank one only for unmet coverage. `rawquality` names the resolved
base graph; `transformer` names a model graph with an open input expecting that quality.
`merge … using transform` closes the port, derives and validates the predicate-qualified output,
and restricts coverage to support justified by both inputs.

The translation adds concrete/applicability guards because an abstract predicate calls for
classification-related behavior, not arbitrary numeric transformation. This is an explicit
narrowing of the loose `PREDICATE QUALITY` match, not an assertion that the supplied strategy was
designed to implement abstract classification. Role decomposition is a separate extension unless
the supplied worldview establishes that a role has the same transformation contract.

For more than one predicate, the splitter must be deterministic and prove progress. A fixed
ordering alone is insufficient for non-commuting transformations: the worldview must justify
the sequence, a model must explain the full combination, or the fallback must reject ambiguity.
The transformer must consume the base through its bound port rather than recursively requesting
the original predicate-qualified quality and looping. Preserve units, authority references,
value operators, and remaining predicates through the split and output validation.

## 6. The running “must work” acceptance matrix

These are specification fixtures, not automated tests or current pass reports. Stable IDs survive
grammar revisions. S1 must resolve pending policies and turn examples into concrete worldview,
model, and expected-data fixtures. A release cannot claim the corpus works by merely parsing it.

| ID | Required positive case | Required failure/edge case | Implementation stages |
|---|---|---|---|
| EX00 | Comma-separated alternatives accept a subject or agent independently; pure guard checks conjunct | Subject must not need all listed kinds; forbidden `$this`/`$context` rebinding rejected | S1–S3 |
| EX01 | Whole-meaning individual model wins; eligible no-model acknowledgement works | Collective cannot pass singular guard; invalid model execution is not converted to trivial success | S2, S4–S6 |
| EX02 | Context-bound concrete quality and process resolve through their correct activities | Missing context rejected; predicate/operator/inherency information remains in the model query | S2–S5 |
| EX03 | Instantiator returns two individuals, both acknowledged exactly once | Zero outcomes complete; failed required acknowledgement blocks completion | S5–S6 |
| EX04 | Endpoint graphs bind to the connection model and created links are acknowledged | Same-type endpoints retain distinct bindings; no invented Cartesian product; incomplete discovery is not empty success | S3–S6 |
| EX05 | Overlapping membership unions deduplicate; three operands reduce to the intended union | Identical original request is not recursively repeated; missing branch cannot be hidden by geometry coverage | S2, S4, S8 |
| EX06 | Boolean OR; at least one approved categorical-union fixture before claiming full compatibility | Unknown truth-table cases; incompatible category combination rejected; no implicit numerical union | S1, S8 |
| EX07 | Boolean AND; at least one approved categorical-intersection fixture before claiming full compatibility | Unknown truth-table cases; unavailable foundational alias not required by the translated type pattern | S1, S8 |
| EX08 | The base is computed once and transformed through its declared port; qualified output validated | Missing/ambiguous input port; abstract predicate; non-progressing split; partial transform support | S2–S5 |
| EX09 | Export/rebuild/replay supported outputs, including submitted roots and dependency references | Missing provenance, payload, or operator version rejects strict replay; no unannounced strategy search | S3, S5–S10 |

For every applicable case record the expected matcher result/captures, strategy ordering, graph
nodes and bindings, coverage/completion state, executable numerical or identity result, and
provenance/source round-trip. Pin worldview aliases and external functor components from the
actual deployment. User-reported success of simpler strategies should be reproduced first as
a baseline before replacing their implementation.

This corpus does **not** cover every proposed capability: `whose`, arbitrary observed-context
binding, distribution/aggregation, countable intersection, individual-event direct resolution,
configuration detection, abstract expansion, roles, bonds, and future temporal changes need
additional cases. Keep this document as the baseline while adding such cases under separate IDs;
do not silently expand the supplied eight strategies and call that a faithful translation.

## 7. Revision protocol and next prompt

Keep the supplied source file immutable as the received fixture. Revise the proposed companion
and this comparison as S1 decisions are made; record changed assumptions and affected EX IDs.
When syntax is implemented, add an executable fixture alongside the design text and record parser,
Reasoner, Resolver, Runtime, and replay evidence separately. Do not mark a case passed based only
on expected behavior or a successful bean round-trip.

**Next prompt:** “Continue S1 in docs/OBSERVATION.md using the supplied observations 1.0 corpus
and docs/OBSERVATION_STRATEGIES_EXAMPLE.md as the required compatibility baseline. Confirm comma
disjunction, rank-zero contextual prerequisites, no-model acknowledgement, boolean/categorical
composition, and predicate ordering. Revise the proposed translation and turn EX00–EX09 into
concrete acceptance specifications. Keep unresolved scientific policies explicit; do not modify
runtime behavior or claim tests pass during the design review.”
