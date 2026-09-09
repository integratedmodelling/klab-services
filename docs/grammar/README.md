# Observation grammar reference and extension history

[Observation.xtext](Observation.xtext) is the revision **0.4** reference for the accepted language
baseline (2026-09-09). It is reviewable Xtext source kept beside the design, not the active grammar
in `klab-languages`. See the [language guide](../OBSERVATION.md#language-guide) for graph naming,
implicit output, unary semantic-operator patterns and the distinction between parsing and execution.
The implemented decisions are accepted; the generation narrative below is historical.
The maintainer has now inserted the proposed grammar into `feature/observation-revision` in
`klab-languages` and now reports successful compilation of its dependent languages and sample.
The initial logical-pattern failure and identifier correction are recorded below as history.
Revision 0.3 removes the conflicting local
identifier overrides and corrects three ranges in shared Observable, as authorized by the
maintainer. Generated sources have not been edited by the agent. The new syntax adapter and seven
contract tests pass with the generated parser, including all concrete syntax node types and the
three running fixtures. The initial strategy semantic API and LanguageAdapter boundary are also
implemented, retaining interface-based Jackson registration. See the
[S3 progress record](../OBSERVATION.md#s3--add-named-plans-through-every-language-and-service-layer)
for exact evidence and remaining scoping, Reasoner, dataflow, and execution work.

The design authority is [OBSERVATION.md](../OBSERVATION.md), with the eight-strategy baseline in
[OBSERVATION_STRATEGIES_EXAMPLE.md](../OBSERVATION_STRATEGIES_EXAMPLE.md). The intended first parser
fixture is [observations-proposed.txt](../examples/observation-strategies/observations-proposed.txt).
[Context blocks](contexts-proposed.txt) and [dataflow source](dataflow-proposed.txt) supply additional
draft forms. The received legacy source remains untouched.

Revision 0.4 makes `to name` optional on graph producers. An unnamed producer is terminal;
name graphs consumed by inputs or explicit `yield`. No shared grammar rule changes.
The Observation parser was regenerated and installed locally with Maven. Other installations
need regeneration before using the unnamed spelling.
Verification: Observation-only Maven generation and installation succeeded; 11 services tests
(`ObservationPipelineTest` and `ObservationStrategyAdaptationTest`) passed against the installed
parser, covering unnamed parsing, null-name transport and compiled resolution. The sibling
syntax-adapter regression is included for the next full languages test run. Existing ANTLR
ambiguity warnings remain; generation reported no errors.

## Scope of the sketch

| Concern | Rules | Status/interpretation |
|---|---|---|
| Document boundary | `ObservationDocument`, `StrategyDocument`, `ExecutableDocument` | Distinct strategy and executable ASTs; versioned generated namespace |
| Selection | `StrategySelection`, `MatchAlternative` | Comma-separated alternatives mean OR; bare observables retained; patterns and external matchers have distinct entry keywords |
| Structured patterns | `PatternBlock`, `PatternExpression`, field/capture/logical/operator rules | Dedicated Observation-owned AST with typed kinds, flags, activities, captures, scalar tests, and operand ordering |
| Setup | `LetSetup`, `EnsureSetup`, `StrategyExpression` | Ordered tuples/calls and conjunctive pure guards; variable references are separate from closed observables |
| Graph values | `GraphProducer`, `GraphInput`, `GraphReference`, `GraphMerge`, `PlanYield` | Producers with optional terminal names, ports, two-input merge, terminal result, and typed optional fallback |
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

### Revision 0.3: shared range correction, local overrides removed

The maintainer reported ANTLR error 208 for unreachable `RULE_LOWERCASE_ID` and
`RULE_UPPERCASE_ID` in both the runtime and IDE generated grammars. The local overrides changed
terminal precedence: the broad mixed-case identifiers could now win before the inherited
lowercase/uppercase tokens. This approach is withdrawn, not worked around with more terminals.

The active Observation grammar and this sketch no longer redeclare those terminals. Instead,
`CAMELCASE_ID`, `BACKCASE_ID`, and `LOWERCASE_ID_TRAILING_COLON` in shared Observable now use
`('A'..'Z' | 'a'..'z' | '_' | '0'..'9')*` after their unchanged initial letter. This preserves
identifier rule ordering, letters, digits, and underscores, while excluding the punctuation
accidentally admitted by `'A'..'z'`. No other shared rules were changed.

Regenerate the Observable language dependency and its consumers: Observation, Worldview, Kim,
and KActors, including IDE/web artifacts where applicable. The maintainer will retest the stack.
Verify the logical remainder cases plus ordinary lowercase, uppercase, mixed-case, digit-suffixed,
and underscore-bearing identifiers, concept/authority references, units, metadata, and literals.
The expected change is at punctuation boundaries, not valid identifier spellings. No claim of
successful regeneration or full-stack parsing is made from source inspection alone.

### Revision 0.2: logical remainder diagnosis and superseded local fix

The generated parser already distinguishes an additional operand from `rest …` using lookahead.
A standalone probe of the generated lexer reproduced the real problem:

```text
input:  logical(or, operands = canonical [capture first as any, rest remaining])
tokens: ... 'rest', RULE_BACKCASE_ID("remaining]"), ')'
```

The shared `Observable.xtext` used `'A'..'z'` in `CAMELCASE_ID`, `BACKCASE_ID`, and
`LOWERCASE_ID_TRAILING_COLON`. That ASCII interval admits `[`, backslash, `]`, `^`, underscore,
and backtick as well as letters. The lexer therefore consumes `remaining]` as one identifier,
leaving the parser without a closing bracket. `rest tail]` reproduces the same error; changing
the remainder variable name does not fix it. Adding whitespace before `]` avoids this particular
lexical error but is not an acceptable syntax requirement.

Revision 0.2 attempted to override those three terminals locally with this continuation set:

```xtext
('A'..'Z' | 'a'..'z' | '_' | '0'..'9')*
```

Their initial-letter constraints and the trailing colon on `LOWERCASE_ID_TRAILING_COLON` are
unchanged. Underscores and digits remain accepted; bracket/backslash/caret/backtick punctuation
does not. The initially attempted shared correction was withdrawn to avoid changing other host
languages. Inspection confirms Worldview, Kim, and KActors inherit Observable directly, not
Observation; none of their grammar files or the shared grammar has a diff from this fix.
Those local overrides would also affect ordinary observable expressions *inside Observation*, so the retest scope included
concept/authority references, units, metadata, and bracketed literals in strategy/dataflow source.
No change to `LogicalPattern`, `RemainderCapture`, generated Ecore classes, or
`ObservationStrategySyntaxImpl` was needed for this fix.

The original revision-0.2 instruction was to regenerate **Observation**, including the IDE/web parser/lexer
artifacts used for retesting. Check [lexer-regressions.txt](lexer-regressions.txt), the three logical
strategies in the eight-strategy fixture, and their generated ASTs. In particular confirm one
explicit operand, `remainder.name == "remaining"`, and the expected logical connector/order.
The lexer probe establishes the old failure; only regeneration and parsing can establish the fix.
The active Java syntax adapters remain the maintainer's next step, retaining `ParsedObject`
inheritance and source locations as in the existing language APIs.

### Revision 0.1: initial sketch

Revision 0.1 was checked by source comparison, rule/reference inventory, and fixture/name review.
**No agent-run Xtext generation, parser execution, Ecore validation, or runtime test was performed
for revision 0.1.** See revision 0.2 for the subsequent maintainer report and lexer reproduction.
The static checks are only an editing aid. This file is not registered in a build or installed
as a language. Keep revision history here as S1/S3 change it; keep the eight-strategy translation,
context example, and executable example synchronized with every surface change.

The first generator-backed acceptance set must include the eight translated strategies,
comma-disjunction cases, ordinary observable matches with conditions, scoped contexts, logical
captures, invalid names, terminal misuse, strict replay definitions/computations, and adaptive
continuations rejected under replay. Verify encode/parse equivalence separately from graph
execution and provenance reconstruction.

**Continuation prompt:** “Continue S3 from KimObservationStrategy, KimObservationPlan, and their
LanguageAdapter translations. Implement lexical/structural validation and model-version admission,
then extend the initial tier-0 Reasoner/Resolver implementation recorded in OBSERVATION S3c. Preserve interface-based Jackson registration
without API annotations or dependencies. Keep the syntax and populated JSON regression corpora
coordinated; extend resolver composition progressively and leave executable dataflow semantics for their later stages.”

The original integration prompt remains applicable to broader S3 work: “Review the grammar as part of S1,
alongside OBSERVATION.md and EX00–EX09. Resolve syntax/AST/validator decisions without changing
the active grammar. When S3 is authorized, generate it in an isolated klab-languages worktree,
add parser and scoping fixtures, migrate adapters, and record actual generation/test evidence.”
