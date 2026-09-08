# Running Observation.xtext sketch

[Observation.xtext](Observation.xtext) is revision **0.1** of the proposed Observation language.
It is reviewable Xtext source kept beside the design, not the active grammar in `klab-languages`.
It has not been run through the Xtext generator or used to parse the example documents. Treat
inferred Ecore types, lexer behavior, parser decisions, scoping, and serializer behavior as
unverified until S3 supplies that evidence. No generated sources or active sibling files changed.

The design authority is [OBSERVATION.md](../OBSERVATION.md), with the eight-strategy baseline in
[OBSERVATION_STRATEGIES_EXAMPLE.md](../OBSERVATION_STRATEGIES_EXAMPLE.md). The intended first parser
fixture is [observations-proposed.txt](../examples/observation-strategies/observations-proposed.txt).
[Context blocks](contexts-proposed.txt) and [dataflow source](dataflow-proposed.txt) supply additional
draft forms. The received legacy source remains untouched.

## Scope of the sketch

| Concern | Rules | Status/interpretation |
|---|---|---|
| Document boundary | `ObservationDocument`, `StrategyDocument`, `ExecutableDocument` | Distinct strategy and executable ASTs; versioned generated namespace |
| Selection | `StrategySelection`, `MatchAlternative` | Comma-separated alternatives mean OR; bare observables retained; patterns and external matchers have distinct entry keywords |
| Structured patterns | `PatternBlock`, `PatternExpression`, field/capture/logical/operator rules | Dedicated Observation-owned AST with typed kinds, flags, activities, captures, scalar tests, and operand ordering |
| Setup | `LetSetup`, `EnsureSetup`, `StrategyExpression` | Ordered tuples/calls and conjunctive pure guards; variable references are separate from closed observables |
| Graph values | `GraphProducer`, `GraphInput`, `GraphReference`, `GraphMerge`, `PlanYield` | Named producers, ports, two-input merge, terminal result, and typed optional fallback |
| Arbitrary contexts | `ContextPlan`, `MemberPlan` | Singular and collection graph-bound scopes; deferred execution is determined by availability, not an anonymous strategy body |
| Submitted inputs | `ObservationDefinition`, `ValueDefinition`, `StoredValue` | Typed object/value records, identities/links, geometry, literal or resource-backed payload |
| Execution | `ExecutionObservation`, `ExecutionApply`, `ExecutionMerge`, `ExecutionReference` | Selected implementations with explicit argument/port bindings; no implicit model lookup |
| Adaptive execution | `ExecutionContinuation` | Explicit captured plan node, rejected in strict replay mode |

The sketch omits the legacy uppercase `for PREDICATE QUALITY` shorthand, unimplemented semicolon
match connector, implicit strategy `apply`, `transform … through …`, and anonymous deferral.
Those require a version-1 parser plus explicit migration, not permissive fall-through in version 2.
Identification strategies are recognized but need their own output validator; this draft does
not settle identification-specific execution contracts.

## Surface decisions made concrete here

- Pattern fields use `kind = quality;`, not `kind: quality;`. The shared Observable lexer has
  name-with-colon terminals; avoiding colons removes that particular token competition.
- Capture syntax is `capture first as any`, not `capture first: any`. Rest capture is
  `rest remaining`; node fields remain conjunctive, while `either(...)` expresses OR.
- Calls require parentheses, including zero-argument guards. A dotted or quoted call ID avoids
  collisions with language keywords; `collective` and `inherent` are explicit unqualified forms.
- The shared `PATTERN_VARIABLE` terminal is reused for `$name` tokens in Observation-owned
  strategy expressions. A local `ConceptReference` override omits its variable alternative for
  ordinary observable values. This is a migration sketch: verify that inherited calls use the
  override and preserve imported Ecore compatibility. It does not change other host grammars.
- Producer targets accept bare ordinary observables or typed expressions. Composite closed
  targets may be wrapped as `{{ … }}` to make their boundary explicit before operation-level
  `within`, `with inputs`, or `to`. No variable placeholders are needed inside observable syntax.
- Strategy graph options use `with (key = expression, …)`. The proposed optional syntax is
  `optional otherwise <typed-expression>`; absence must have a declared meaning.
- `merge … yielding <target>` is terminal without `into`; producers and context blocks always
  name their outputs. A sole named producer may be the implicit final result.
- Executable `observe name as {{ observable }}` contains explicit `apply` calls. Calls identify
  an implementation and version and bind arguments with `ref node output port` or stored values.
  Executable `merge` always has a named output. `using` on an executable observation records a
  strategy identity, not a request to run it again.
- The executable `continuation` explicitly names its input, cardinality, bound context variable,
  enclosing request, and captures. It embeds a typed plan only in `adaptive` mode. Strict replay
  contains its already resolved executable fragments instead.

The dataflow sample's versions and implementation IDs are illustrative. They do not claim an
installed component or runtime capability. Geometry strings require the approved geometry codec;
definition link roles and implementation signatures require registry validation. Literal/value
encoding is a grammar starting point, not a complete scientific data serialization specification.

## What grammar cannot enforce

Xtext cross-references describe model links, but linking and semantic scope are separate from
parsing. See the official [grammar reference](https://eclipse.dev/Xtext/documentation/301_grammarlanguage.html)
and [runtime/scoping guide](https://eclipse.dev/Xtext/documentation/303_runtime_concepts.html).
The draft uses cross-references for graph and executable node references; capture and `$name`
lookup requires an explicit typed symbol layer, including branch-dependent capture exports.

| Validator/service | Required work |
|---|---|
| Scope provider | Expose prior named graph results and legal outer captures; hide unnamed/terminal steps; implement nested context/member scopes; reject duplicate or forward graph names |
| Pattern validator | Field uniqueness/types, capture branch compatibility, reserved names, presence/absence, operator slots, scalar types, bounded unordered matching, deterministic normalization |
| Expression checker | Functor signatures, tuple arity, pure guards, undefined/reserved variables, closed observable checks, semantic versus observation-reference values |
| Strategy validator | Unique reachable output, terminal position, no dangling producers, optionality, structural recursion progress, rank-zero policy and explicit prerequisite bindings |
| Resolver | Actual port/observable compatibility, operator signatures, coverage, candidate isolation, context/geometry compatibility, no invented endpoint pairings |
| Executable validator | Definitions and prerequisites close all references; context types and cardinality; known versions/ports; no unresolved capture or semantic search in replay |
| Runtime/builder | Registry availability, supported geometry/payload codecs, transaction/snapshot lineage, continuation scheduling, committed provenance completeness, identity remapping |

`PlanStep` is the inferred supertype referenced by graph bindings; the scope provider must filter
it to named value-producing steps. `ExecutionDeclaration` similarly includes both definitions and
execution nodes; type checking decides whether a referenced declaration exposes an observation,
scalar, collection, or output port. Do not interpret broad Ecore reference types as broad semantic
permission. Previous-output references within an actuator require ordered port validation, not
an accidental self-dependency cycle in the observation graph.

A returned zero-member collection is distinct from resolution failure even though neither
distinction is expressible by a token in the grammar. Similarly, boolean and categorical merge
semantics remain S1 review items. Successful parsing of a signature does not approve its science.

## Deliberate gaps before generator integration

1. Generate the draft in an isolated language worktree and resolve inferred Ecore/override issues.
   The root rule is `ObservationDocument`, replacing the old `Strategies`; adapters must migrate.
2. Audit inherited lexer overlaps, especially `LOWERCASE_ID`/`NAMESPACE_PATH`, keyword names,
   qualified identifiers, `PATTERN_VARIABLE`, units, and inherited metadata literals. This draft
   avoids new broad terminals but does not claim the inherited lexer is conflict-free.
3. Exercise lookahead at comma-separated match alternatives versus `if` guard arguments; both
   are explicitly specified, but only parser tests prove that the grammar implements that split.
4. Verify calls versus bare observable targets and operation-level `within`. Require `{{ … }}`
   at ambiguous boundaries rather than silently changing the observable being requested.
5. Freeze complete executable-node fields. Coverage, geometry, metadata, and requirements are
   sketched, but units/currencies, scheduling, port sequences, operator versions, sharding, and
   provenance identifiers need a field-by-field mapping to the final portable Dataflow model.
   Add explicit productions or versioned records before claiming lossless persistence. Generic
   metadata must not substitute for required execution semantics.
6. Review additional operation families (unary value computation, explicit temporal feedback,
   mediation) and identification strategies; do not introduce undocumented implicit `apply` logic
   to fill these gaps. The closed merge enum covers the initially proposed binary families only.
7. Choose the actual language/AST version policy and migrate legacy source with diagnostics.
   `version 2.0` and the `/v2` EPackage URI are placeholders for that decision.

## Revision and validation protocol

Revision 0.1 was checked by source comparison, rule/reference inventory, and fixture/name review.
**No Xtext generation, parser execution, Ecore validation, or runtime test has been performed.**
The static checks are only an editing aid. This file is not registered in a build or installed
as a language. Keep revision history here as S1/S3 change it; keep the eight-strategy translation,
context example, and executable example synchronized with every surface change.

The first generator-backed acceptance set must include the eight translated strategies,
comma-disjunction cases, ordinary observable matches with conditions, scoped contexts, logical
captures, invalid names, terminal misuse, strict replay definitions/computations, and adaptive
continuations rejected under replay. Verify encode/parse equivalence separately from graph
execution and provenance reconstruction.

**Continuation prompt:** “Review docs/grammar/Observation.xtext revision 0.1 as part of S1,
alongside OBSERVATION.md and EX00–EX09. Resolve syntax/AST/validator decisions without changing
the active grammar. When S3 is authorized, generate it in an isolated klab-languages worktree,
add parser and scoping fixtures, migrate adapters, and record actual generation/test evidence.”
