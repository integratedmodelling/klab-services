# Assisted observable composition

The first consumer is the concept modal in the sibling `klab-ide` project. Its `SemanticComposer`
contains the query field, proposal table, accepted expression and `ObservableCard`. A host supplies
an asynchronous Continue action receiving a validated `Observable`; the component has no knowledge
of digital-twin submission. This lets a forthcoming Worldview view reuse it with a different action.
The Eclipse `klab/.../SearchView.java` informed the interaction, not the implementation or API.

## Request/response contract

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

## Validation and current coverage

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
- Full trait-base conflicts, domain/inherency compatibility and other worldview-sensitive rules as
  shared validator contracts, including better explanations of why a proposal was excluded.
- Context-aware proposal ranking, personal defaults/history and search-result pagination. Initial
  searches currently examine a bounded Lucene candidate window, so this is not an exhaustive browser.
- An ontology-independent completion policy for concept-only editing in the Worldview view. This
  component currently returns observables; bare predicates are intermediate components.

Unsupported operations are withheld rather than represented as successfully implemented paths.

## Rule catalog and extension points

There are three distinct decisions: whether a token may be entered, whether the current expression
is complete, and whether its resulting observable can be submitted in a particular context. Keep
these decisions separate when adding rules. An incomplete prefix is not a validation error, and a
satisfiable expression is not necessarily submit-ready.

| Rule / current behavior | Source of truth / implementation | What remains to extend |
| --- | --- | --- |
| Root accepts observable heads, predicate prefixes, unary operators and groups | `SemanticScope.root()`; `SemanticSearchSession.replay()` | Concept-only completion for ontology editing |
| `each` starts an expression or follows an applicable semantic clause, including `of`; it is never repeated in the same operand position | `ObservableValidator.conceptAttributeRules()` (`EACH` requires `COUNTABLE`); session qualifier replay | Additional concept attributes; explicit grammar-position rules for future qualifiers |
| A predicate prefix needs a head, unless a unary operator turns it into an observable | `replay()` concept branch and `complete()` | Base-trait uniqueness, predicate applicability, incompatible roles and predicate-only groups |
| Unary operand categories | `ObservableValidator.unaryOperatorRules()`, with `UnarySemanticOperator.allowedOperandTypes` as fallback | Second operands, comparison clauses and operator-specific worldview constraints |
| Clause argument categories | `ObservableValidator.clauseRules()`, with `SemanticLexicalElement.argument` as fallback | Shared rules for every clause; compatibility with the head and already-bound roles |
| Clause applicability and duplicate roles in a lexical frame | `SemanticLexicalElement.applicable`; `Frame.used` | Restrictions inherited from named concepts and clauses nested in previously completed operands |
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

### Explicit collectives and keyboard scopes

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

### Adding or strengthening a rule

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

## Digital-twin action

The modal captures the selected digital twin and rejects continuation if it changed. If there is
no twin, it asks the existing controller to obtain a default context. `ModelerImpl.observe` accepts
a raw `Observable` and uses `ObservableSubmission` to construct an unresolved observation:

- Qualities (and other dependent observations, currently processes) require a context observation
  and use its geometry.
- Substantials, including enumerable relationships, are converted through the semantic builder to
  collective observables without mutating the selected observable.
- Collectives use the selected observer's `PERCEIVES` geometry. Missing observer/geometry is an
  error; the observer's own `OCCUPIES` geometry is never used as a fallback.

The resulting observation follows the existing Runtime submission, progress, cancellation identity,
notification and graph-update route. No identity is fabricated for an individual substantial.

## Verification

Focused tests cover state transitions and invalid/stale edits with a controlled Reasoner, shared
operand rules, literal bounds, balanced normalization, a real loopback HTTP JSON round trip through
`ReasonerClient`, and unresolved-observation geometry/collective adaptation. These do not establish
live worldview semantic behavior or a rendered JavaFX workflow. A live acceptance pass should compose
a worldview quality, add a context clause, exercise nested groups/undo, then submit it in a twin with
a context observation; repeat with a substantial and an observer with a perceived extent.
