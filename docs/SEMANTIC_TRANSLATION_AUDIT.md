# Semantic translation audit and completion plan

Reviewed 2026-09-12 against this working tree and the sibling `klab-languages` source.
Existing annotation-related edits were preserved. This is a source audit, not a claim that
the full language-to-OWL pipeline passes integration tests.

## Translation path

1. The original beans live in `../klab-languages/org.integratedmodelling.languages.observable/src/org/integratedmodelling/languages/`:
   `SemanticSyntaxImpl`, `api/SemanticSyntax`, `api/ObservableSyntax`, and
   `validation/LanguageValidationScope`. Worldview declarations originate in
   `org.integratedmodelling.languages.worldview/.../ConceptDeclarationSyntaxImpl`.
   `ConceptDescriptor` carries namespace, name, primary type, abstract status and pattern-variable
   status. `ConceptData` adds generic qualification and negation. Semantic beans carry unary
   operators, restrictions, logical operands and value operators.
2. `klab.services.resources/.../lang/LanguageAdapter` adapts declarations through
   `adaptConceptDefinition`, expressions through `adaptSemantics` / `asTokens` /
   `adaptSemanticSequence` / `adaptSemanticToken`, and observable wrappers through `adaptObservable`.
   `WorldviewValidationScope.createConceptDescriptor` feeds declaration information back into
   language validation. The `ConceptData` overload of `adaptSemantics` is a separate, lossy path.
3. `klab.core.api/.../lang/kim/impl/KimConceptImpl` stores the semantic tree and caches its
   canonical definition in `urn`. `finalizeDefinition` renders it; `resetDefinition` invalidates
   the cache. `format` is another rendering path. `KimObservableImpl` stores observation-specific
   fields separately. These are syntax objects, not reasoned concepts.
4. `klab.services.reasoner/.../ReasonerService.build` / `buildInternal` install worldview
   declarations. `declareConcept` delegates to `declareInternal`, which resolves the head,
   applies restrictions using `internal/SemanticsBuilder`, composes logical operands and handles
   negation. `declare(KimObservable, ...)` applies observable attributes using the same builder.
5. `SemanticsBuilder.buildConcept(KimConcept)` is also independently reachable through the public
   builder API. It resolves names, invokes OWL unary factories, creates restricted subclasses,
   adds predicate and contextual restrictions and finalizes concepts. It is not equivalent to
   `ReasonerService.declareInternal`: notably it does not process logical operands or the
   `isNegated` flag. Its unary `NOT` branch is a separate representation.
6. `owl/OWL` and `owl/Ontology` create and register `ConceptImpl` objects. Finally
   `ObservableImpl.promote` wraps the concept and snapshots its abstract status, reference name,
   artifact type and contextualization. Existing annotation collection creates additional
   concept copies at the declaration/builder boundaries.

Paths abbreviated with `...` above have the usual `src/main/java/org/integratedmodelling/klab/services`
prefix in service modules, `src/main/java/org/integratedmodelling/klab/api` in the API module,
and `src/main/java/org/integratedmodelling/common/knowledge` for runtime implementations.

## Abstract status: definition and actual propagation

The `Semantics.isAbstract` contract says observations of an abstract concept cannot exist;
atomic status is stated, whereas expression status is attributed by reasoning. This is an
application-level property, not equivalent to OWL satisfiability or ordinary subclass inheritance.
In particular, a concrete child of an abstract parent is legitimate.

| Boundary | Current behavior | Consequence |
| --- | --- | --- |
| Declaration bean to `KimConceptStatement` | Copies `isAbstract`; adds `SemanticType.ABSTRACT` | Two representations initially agree. |
| Generic-quality declaration adaptation | Replaces the entire type set with the parent's after declaration flags were added | Can erase local abstract/sealed/subjective flags or import parent flags while keeping a different declaration boolean. |
| Descriptor/reference to `KimConcept` | Maps primary type; ignores descriptor abstract status and `ConceptData.generic`; the private overload also ignores `ConceptData.negated` | Syntax-level contextualization cannot reliably distinguish abstract references. Textual qualification in the leaf name is not structured preservation. |
| Declaration to OWL | `buildInternal` emits `NS.IS_ABSTRACT` for abstract declarations (also core namespace); `Ontology.addMetadata` sets the runtime boolean | Explicit declarations have a working in-memory propagation path. |
| Core concepts | `OWL.makeConcept` explicitly marks core-ontology concepts abstract | Core aliases resolve canonical concepts; alias-local abstract semantics need a defined policy. |
| Derived subclasses | `OWL.makeSubclass` passes parent types into a fresh class assertion but does not emit abstract metadata; builder's abstract FIXME only sets collective status | A derived concept may carry `ABSTRACT` in its types while `isAbstract()` is false. There is no visible complete expression rule here. |
| Unary/logical construction | No common abstract-status evaluator across the factories/build paths | Abstract behavior cannot be assumed consistent across equivalent entry points. |
| Concept copies and collective/singular variants | Copy the runtime boolean | Preserves the status already established; does not repair it. |
| Observable promotion | Copies `concept.isAbstract()` | Preserves upstream mistakes. Later `setSemantics` does not refresh the independent boolean. |
| Generic observable | Independent generic flag and components; `ConceptImpl.isGeneric()` is always false | The documented implication generic => abstract is not enforced by these setters. |
| Ontology reload | `Ontology.scan` calls `OWL.makeConcept` with empty types; that method does not hydrate abstract metadata | Reload equivalence needs an explicit round-trip test and a metadata hydration path, not just testing fresh declarations. |

Do not repair this by blindly inheriting `ABSTRACT` from every component, or by changing
`isAbstract()` to OR the type flag with the boolean. Either would encode an unapproved rule and
can prevent intended concretization.

## Confirmed defects fixed in this change

- **Relationship endpoint corruption:** language beans store LINKING source and target as two
  ordered entries. The adapter flattened both into a single source sequence, then selected entry
  zero as target. It now adapts source and target independently in their original order.
- **Intersection identity:** the adapter set the intersection type but not `Expression.INTERSECTION`;
  regenerated URNs consequently used `or`. Both representations are now set. The formatter now
  renders the head followed by operands, matching canonical generation, and closes parentheses;
  its former expression-only branch skipped the head and returned before closing parentheses.
- **Concept copy identity:** copies now retain `serviceId` and `nonSemanticId`, including copies
  made during collective conversion and annotation adaptation.
- **Observable range and generic preservation:** rebuilding an observable retains its generic flag
  and range. `withRange` now stores the range and returns the builder instead of null;
  `buildObservable` installs it on the result. This fixes the broken fluent declaration path.
- **Wrong default-value accessor:** the reasoner now reads `getDefaultValue` when handling a default.
  Default application is still incomplete because the receiving builder method is a stub.

## Remaining gaps and soundness risks

| Priority | Location | Finding / required completion |
| --- | --- | --- |
| P1 | `LanguageAdapter.adaptSemanticSequence` | Its documented unary/predicate precedence defect remains: `Normalized change rate of Elevation` may attach the predicate to the wrong level. A flat token fold is insufficient. Empty tokens also reach `getLast`. |
| P1 | `SemanticsBuilder.buildConcept` | Direct builder ignores logical operands and boolean negation. Canonical URNs must never advertise constraints omitted from OWL. Consolidate the two compilation routes. |
| P1 | Abstract-status path | No authoritative expression rule, duplicated state and potential reload drift, as above. |
| P1 | `LanguageAdapter.adaptObservable`, `KimConceptImpl.finalizeDefinition`, `SemanticsBuilder.addValueOperator` | Value operators are respectively not transferred, omitted from canonical identity, and a commented-out implementation. Distinct expressions can lose distinctions. |
| P1 | `SemanticsBuilder.withUnit(String)`, `withCurrency(String)`, `withDefaultValue`, `withResolutionException` | Return the builder without implementing the request. String unit/currency overloads are used by reasoner declaration. Implement and test mediation/defaults/directives or reject unsupported requests explicitly. |
| P1 | Builder failure handling | Unresolved heads/unary factory nulls can be dereferenced. `declareInternal` can skip unresolved restrictions and traits, yielding weaker semantics, and broad catches can return partial results. Preserve source diagnostics and prevent successful publication of incomplete concepts. |
| P2 | `SemanticsBuilder.withObserverSemantics` | Dereferences the field `observerSyntax` rather than the supplied concept, causing failure on first use. Observer round-trip preservation also needs completion. |
| P2 | `KimConceptImpl` transforms | Shallow child sharing, mutable type sets and cached URNs require a mutation audit. `removeComponents(UNARY_OPERATOR)` mutates a shared observable child and can dereference null; setter calls do not universally invalidate identity. |
| P2 | `LanguageAdapter` descriptor overload | Also omits namespace/project/source context and structured reference qualification. Audit every bean field against API support; define how authority references and pattern variables survive both routes. |
| P2 | Nonsemantic/pattern observables | Public `declareObservable` dereferences semantics before its downstream nonsemantic branch. Pattern adaptation may leave semantics null. Test and dispatch these before semantic ontology lookup. |
| P2 | `ObservableImpl` copying | `serviceId` and specialized components are omitted; resolution directives share a mutable collection. Promotion/replacement metadata and independent abstract/contextualization fields need lifecycle invariants. |
| P2 | Unsupported restrictions | CONTAINING and CONTAINED_IN throw; malformed LINKING arity and mixed AND/OR should receive diagnostics at the boundary, including beans supplied without parser validation. |

Annotations already have collection/copy work in this working tree; this audit preserves that work.
Semantic identity, annotation identity and service-local registry identity should be tested together,
especially for canonical core aliases and collective variants.

## Staged completion

### 1. Establish a runnable conformance baseline

Repair the module build/test baseline; run the new targeted tests below. Build the sibling language
artifacts at a recorded revision rather than assuming installed snapshots match inspected source.
Add real-parser fixtures for atomic references, unary operators with predicates inside/outside,
each binary restriction, nested AND/OR, negation, collective modifiers, patterns and nonsemantic
observables. Compare bean -> Kim tree -> regenerated source -> parsed tree. Require both URN and
formatter to preserve grouping and endpoints. Include malformed beans and source-located errors.

### 2. Decide the abstract/generic contract

Decide and record a truth table for:

- abstract atomic declarations versus concrete children;
- abstract heads specialized by concrete identities, realms, attributes or roles;
- abstract contextual fillers and unresolved abstract predicates;
- unary operators, especially count, presence, type and probability;
- unions, intersections, negation and collective conversion;
- generic requests versus concepts, and alias-local modifiers versus canonical identity.

Recommended direction: keep declared status separately identifiable; use one semantic evaluator
for effective expression status and generic components. Derive runtime boolean/type projections
from that result. Do not infer application abstractness from OWL satisfiability alone. Acceptance:
every row has expected syntax status, runtime status and allowed observation behavior, with
concrete-child cases guarding against accidental inherited abstractness.

### 3. Consolidate compilation and fail incomplete requests explicitly

Compile one structured tree through one implementation for both declaration and builder APIs.
Preserve scope and grouping before canonicalization. Complete negation, logical operands, operator
values, mediation, defaults/directives and observer fields; validate unsupported constructs rather
than silently dropping them. Decide default/inline-value and value-operator identity semantics
before serializing them into concept URNs. Acceptance: equivalent entry points produce equivalent
OWL restrictions and observable attributes; invalid constituents never produce a successful weaker
concept. Run OWL entailment checks, not only string comparisons.

### 4. Make identity and persistence stable

Centralize canonicalization and cache invalidation or adopt immutable syntax nodes. Deep-copy
mutable state only where ownership requires it. Establish a single update path for type/abstract/
generic/contextualization fields. Persist and hydrate the effective status and any provenance
needed to recompute it. Acceptance: fresh declaration, cache hit, JSON round trip, ontology reload,
alias lookup and singular/collective conversion agree; no shared canonical object is relabeled.

### 5. Gate completion on integration behavior

Exercise real language beans through resources -> reasoner -> observable, including resolver
classification/characterization and `type.abstract`, `type.concrete`, `predicate.concrete` functors.
Add a field coverage table as a release check so grammar acceptance cannot outpace runtime support
silently. Completion means all supported constructs preserve their meaning and unsupported ones
produce deliberate diagnostics, with the abstract truth table passing across persistence boundaries.

## Validation of this change

Added `SemanticTranslationTest` (endpoints, intersection URN regeneration and formatting),
`ConceptCopyTest` (identity and abstract preservation through copy/promotion), and
`ObservableTranslationTest` (range/generic rebuild and fluent range replacement).
These are regression tests, not yet executed successfully.

Attempted:

```powershell
mvn -o -pl klab.services.resources -am '-Dtest=SemanticTranslationTest,ConceptCopyTest,ObservableTranslationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The initial targeted invocation (before adding `ObservableTranslationTest`) stopped in API test
compilation with missing `Data.FillCurve` and other API symbols. A compile-only reactor attempt
with `-Dmaven.test.skip=true` stopped in `klab.core.common` on missing API packages such as
`org.integratedmodelling.klab.api.authentication`. Neither reached the changed service compilation
or regression execution. `git diff --check` found no whitespace errors. Full behavioral verification
remains outstanding; no abstract propagation policy was changed in this patch.
