# Worldview syntax and OWL translation audit

Reviewed 2026-09-13 against the working trees of `klab-services` and sibling
`klab-languages`, including the preceding status and observable adaptation fixes.

**The declaration pipeline is not complete.** Most Worldview clauses cannot reach
their OWL handlers. Some information disappears in the language bean; additional
information disappears in `LanguageAdapter.adaptConceptDefinition`. Consequently,
successful parsing or successful declaration is not evidence that all supplied
constraints were asserted. No production translation or core ontology changes are
made by this audit; the gaps below are the implementation backlog.

## Evidence and reproducibility

Source boundaries inspected:

- [Worldview grammar](../../klab-languages/org.integratedmodelling.languages.worldview/src/org/integratedmodelling/languages/Worldview.xtext): **active** rules through `ChildConcept`; the large commented-out legacy grammar is not treated as supported syntax.
- [Language declaration contract](../../klab-languages/org.integratedmodelling.languages.observable/src/org/integratedmodelling/languages/api/ConceptDeclarationSyntax.java) and [bean](../../klab-languages/org.integratedmodelling.languages.worldview/src/org/integratedmodelling/languages/ConceptDeclarationSyntaxImpl.java): constructors, `parseBody`, both `parseConceptReference` overloads.
- [Ontology bean](../../klab-languages/org.integratedmodelling.languages.worldview/src/org/integratedmodelling/languages/OntologySyntaxImpl.java) and [semantic bean](../../klab-languages/org.integratedmodelling.languages.observable/src/org/integratedmodelling/languages/SemanticSyntaxImpl.java): domain and `within` handling.
- [Service adapter](../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/lang/LanguageAdapter.java): `adaptOntology`, `adaptConceptDefinition`, `adaptSemanticToken`.
- [Service declaration contract](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/KimConceptStatement.java) and [implementation](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/impl/KimConceptStatementImpl.java).
- [Reasoner](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java): `build`, `buildInternal`, `compileInternal`, `registerEmergent`.
- [Builder](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/SemanticsBuilder.java), [OWL helpers](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWL.java), [axiom translation](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/Ontology.java), and [core constants](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/CoreOntology.java).
- Actual RDF/XML property declarations in [odo.owl](../klab.services.reasoner/src/main/resources/knowledge/odo.owl) and [klab.owl](../klab.services.reasoner/src/main/resources/knowledge/klab.owl), rather than inferring property existence from Java constants. Runtime installations may have extracted/custom core files; those require the same check against their loaded IRIs.

Executed successfully outside the sandbox, against the locally installed updated
observable/worldview language snapshots:

```powershell
mvn -o -pl klab.services.resources,klab.services.reasoner -am '-Dtest=WorldviewClauseAuditTest,WorldviewOwlRestrictionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

[WorldviewClauseAuditTest](../klab.services.resources/src/test/java/org/integratedmodelling/klab/services/resources/lang/WorldviewClauseAuditTest.java)
parses 27 real declarations covering the principal clause families, constructs
the language beans and adapts them. It writes a diagnostic inventory to
`klab.services.resources/target/worldview-clause-audit.txt`. It intentionally does
**not** assert that the missing transfers are correct: a green diagnostic run is
not clause conformance. The observed losses include `Local is Entity` losing its
parent, five populated bean clause collections becoming empty service collections,
and empty requirement/description collections already at the bean boundary.

[WorldviewOwlRestrictionTest](../klab.services.reasoner/src/test/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewOwlRestrictionTest.java)
uses the real `OWL`/`Ontology` implementation and OWLAPI to verify the direction of
subclass and single-filler existential axioms. Both tests pass. The remaining
handler findings below are source inspection, not full-worldview entailment tests.

## Clause coverage

`C` is the declared class, `P` its parent and `X` a clause target.
`C ⊑ ∃p.X` means an existential restriction, not a universal restriction, exact
cardinality, or closed-world requirement. “Candidate” means the property exists
but its intended use still needs agreement; it is not an implemented mapping.

| ID / active clause | Grammar → language bean | Bean → service syntax | Reasoner / OWL result and missing work |
|---|---|---|---|
| W01 `is P` | Qualified expressions retained; **local `ConceptRef` returns null** | `declaredParent` transferred when present | `buildInternal` asserts `C ⊑ P`. Lower-level direction verified. Local references can silently fall back to the core parent instead. |
| W02 `equals P` | Alias flag and qualified parent retained | Transferred | `build` installs a delegate concept; it does **not** declare a distinct class equivalent to P. This is alias identity, not `EquivalentClasses`. Its early return skips other declaration clauses and children. Nested child aliases go through `buildInternal` instead of `build`, so alias handling differs. |
| W03 `is core P` | Core flag and reference retained | `upperConceptDefined` from encoded parent | `build` resolves canonical core class, adds types and installs delegate. Same early-return caveat. Validate that P is one atomic core ID. |
| W04 `is/equals P within X` | **Context not read from `IsClause`** | `declaredInherent` remains null | No consumer of `getDeclaredInherent` in the reasoner. Preserve context separately before defining its OWL interpretation; do not silently substitute ordinary `of`. |
| W05 `inherits T,...` | `inheritedPredicates` populated | **Not transferred** to `traitsInherited` | Dormant `buildInternal` loop calls `OWL.addTrait`. Identity/realm/attribute use trait-root restricting properties and existential restrictions. Roles are ignored by `addTrait`. |
| W06 `affects Q,...` | **AffectsClause not parsed by bean**; no API target list | Service has `qualitiesAffected`, but empty | Existing loop would emit `C ⊑ ∃odo:affects.Q` for each Q. Property exists; inspect its multiple-domain semantics below. |
| W07 `creates X,...` | Same missing bean handling; `creates` discriminator lost | `observablesCreated` remains empty | Existing loop would emit `C ⊑ ∃odo:creates.X`. Property exists, with problematic multiple ranges. |
| W08 `applies to X,...` | `appliesTo` populated | **Not transferred** to `ApplicableConcept` entries | No declaration consumer. `OWL.setApplicableObservables` exists and uses `odo:appliesTo` with union fillers; no call from `buildInternal`. Agree list semantics before wiring it. |
| W09 `links S to T` | Ordered source/target lists populated | **Not transferred** to `subjectsLinked` | Dormant loop calls `defineRelationship`: `C ⊑ ∃odo:impliesSource.S` and `C ⊑ ∃odo:impliesDestination.T`. Both properties exist. These are restrictions on the relationship class, **not** property domain/range axioms. Validate endpoint count and resolution. |
| W10 `emerges from X [within Y],...` | Semantic beans retained, including their contextualization | **Not transferred** to `emergenceTriggers`; semantic adapter also ignores contextualization | Existing loop only registers nonabstract concepts in an in-memory emergence map; it emits no `odo:emergesFrom` restriction. Property exists. Decide whether OWL and operational trigger registration are both required, including scoped triggers. |
| W11 `implies X [within Y],...` | Stored in implementation's `implies` list, **not exposed by interface** | No matching `KimConceptStatement` field; contextualization also lost | No declaration handler. `odo:impliesObservable` is a candidate for plain implications, not proof of an encoding for conditional `within`. |
| W12 `requires identity X,...` | Empty parser branch. `Requirement` record holds **only a kind**, no targets | Target list exists in service API but stays empty | Dormant handler refers to **missing `odo:requiresIdentity`**. Generic `odo:requires` exists but has intersecting ranges; not a safe automatic replacement. |
| W13 `requires realm/extent/attribute X,...` | Same empty branch; `RequiresType` also **omits ATTRIBUTE** | Service lists exist but unfilled | No declaration loops. Decide generic versus typed requirement properties and validate target types. |
| W14 `requires authority A [parameters]` | Kind exists; **no authority ID or parameter payload** retained | Only `authorityRequired` string exists, no parameters | No declaration handler. Decide runtime authority validation and persistence; it need not be an object-property restriction. |
| W15 `describes Q [as number [to number] / concept / boolean]` | TODO; `Description` record holds **only kind**, no target/value | Existing target/type pairs cannot represent the `as` payload; no adaptation | No consumer. `odo:describesQuality` is a candidate for Q; numeric range/concept/boolean semantics require a separate representation and policy. |
| W16 `increases with Q` | Same TODO | No adaptation | No consumer. `odo:increasesWith` exists; range has both Ordering and Quality. |
| W17 `decreases with Q` | Same TODO | No adaptation | No consumer. Core spelling is **`odo:descreasesWith`**, not `decreasesWith`; range is Ordering only. Migration/type policy needed. |
| W18 `marks Q` | Same TODO | No adaptation | No consumer. Candidate `odo:marksQuality` exists. Deniability/nonzero semantics are not asserted. |
| W19 `classifies Q` | Same TODO | No adaptation | No consumer. Candidate `odo:classifiesQuality` exists. Do not confuse with unary `type of Q`. |
| W20 `discretizes Q` | Same TODO | No adaptation | No consumer. Candidate `odo:discretizesQuality` exists. |
| W21 `deniable as Alias` | **Clause ignored**, alias text lost. Declarable-type `deniable attribute` flag is separate and retained | No denied-alias field | No alias/complement/covering implementation for this clause. Unary negation has a separate missing-property problem below. |
| W22 `has [disjoint] children ...` | Children and `childrenDisjoint` retained | Children transferred; **disjoint flag has no service field** | Ordinary children get `Child ⊑ C`; no pairwise disjointness. `sealed abstract` child status is retained but no covering axiom or derivation enforcement follows. |
| W23 `Child within X` | **Short-child context ignored** | `declaredInherent` unfilled | No scoped-child semantics. Nested declarations share the other clause losses. |
| W24 metadata / docstring / annotations | Docstring and annotations handled; body metadata not read | Docstring/annotations transferred; arbitrary declaration metadata not transferred | RDFS comment and runtime annotation collection exist; not equivalent to persisting all metadata as OWL annotations. |

Declaration modifiers are not OWL restrictions merely because they survive as
`SemanticType` flags. `abstract` receives the ODO annotation in `buildInternal`;
subjective status is projected in runtime concepts but is not similarly emitted
there. Sealed coverage/disjointness and deniable-alias semantics remain missing.
Top-level `PrimaryConceptDefinition` does not accept `sealed`; the nested rule does.
The old grammar comment suggesting automatically abstract children conflicts with
the agreed explicit-child status rule and should be corrected during grammar work.

Ontology preamble: name, version, imports, root marker and core-import aliases/URLs
reach `KimOntology`. **Non-root `parseDomainConcept` returns null**, and adapting
that null domain is unsafe. Preamble metadata is not transferred beyond the
docstring comment. OWL initialization loads core knowledge from extracted files;
the declaration checks above do not establish arbitrary imported-core deployment
or domain validation correctness.

## Existing OWL restrictions and shared observable forms

The axiom constructor takes `Axiom.SubClass(superclass, subclass)`; `Ontology`
correctly reverses those arguments when producing OWLAPI's subclass-first axiom.
`restrictSome` produces `SubClassOf(C ObjectSomeValuesFrom(p X))`, verified by test.
It does not implement `only`, closed-world validation, or mandatory data acquisition.

| Observable component | Implemented route / property | Limits |
|---|---|---|
| `of`, `with`, `caused by`, `causing`, `for`, `during`, `adjacent to` | Builder restrictions via `odo:isInherentTo`, `hasCompresent`, `hasCausant`, `hasCaused`, `hasPurpose`, `observedDuring`, `isAdjacentTo` | Properties exist. Resolved operands must be checked; skipped null/invalid constraints can weaken results. |
| `linking S to T` | `odo:impliesSource`, `odo:impliesDestination` | Separate from the dropped Worldview `links` declaration clause. |
| Direct identity/realm/attribute | Root-specific subproperties of `hasIdentity`/`hasRealm`/`hasAttribute`, or subjective trait fallback | `withTrait` conflict predicate resolves `added` twice instead of `added` and `original`; it can replace unrelated traits. Missing lexical roots can cause omission/error. |
| Direct role | Direct builder can restrict `odo:hasRole` | Reasoner's reconstruction calls **stub `withRole`**, losing roles on that route. |
| `contained in`, `containing` | Adapter throws | Candidate part properties exist, but spatial containment versus part semantics is undecided; no mapping should be guessed. |
| `and`, `or` | Reasoner combines resolved operands; direct builder ignores logical operands | `OWL.getIntersection/getUnion` assert **subclass-of** the expression, not equivalence to it. This gives necessary conditions only and may be too weak for canonical expression identity. |
| `not` / deniable negation | Reasoner negates; direct builder ignores boolean negation (separate unary NOT branch exists) | `makeNegation` creates a sibling plus a restriction using missing `odo:isNegationOf`; no OWL complement/disjoint covering equivalence is asserted there. |
| Ordinary unary qualities | Factories generally restrict `klab:describesObservable` to operand | Property exists; change/changed additionally use `odo:changes`/`odo:changed`; rate directly describes its operand. Some factories also copy inherency. |
| Ratio, proportion/percentage, value/monetary value comparisons | `klab:isComparedTo` alongside operand restriction where provided | Property exists. Operator-specific operand validity and nested grouping still need real-parser/entailment fixtures. |
| `any`, `all`, `no`; status flags | Syntax/runtime flags retained | No corresponding OWL quantifier translation is implied. In particular `all` is not automatically `allValuesFrom`, and `no` is not a complement axiom. |
| Value constraints, units/defaults/directives | Separate observable layer | Value-operator transfer/identity/OWL construction remains incomplete; string mediation/default builder stubs remain. See the earlier audit. |

Additional lower-layer risks: the multi-filler `restrictSome` overload adds axioms
to the **property's ontology**, unlike the single-filler target-ontology selection.
This can place worldview axioms in core ontologies and affect unload/persistence.
`Ontology.define` can omit a restriction when lookup returns null; its universal
restriction branch dereferences the property before its null check. Broad catches
and continued declaration processing do not guarantee failure is atomic.

## Core property issues

| ID | Evidence in bundled core | Required action |
|---|---|---|
| O01 | Java `REQUIRES_IDENTITY_PROPERTY = odo:requiresIdentity`; absent from both OWL files | Define typed requirement properties or explicitly redesign mapping to generic `requires`; do not enable the dormant handler first. |
| O02 | `IS_NEGATION_OF = odo:isNegationOf`; absent | Specify deniable negation (complement within a parent, disjoint siblings, annotations, etc.) before adding a property. Current factory alone is not complement semantics. |
| O03 | Java `IS_SUBJECTIVE = odo:isSubjectiveTrait`; OWL declares annotation `odo:isSubjective` | Align annotation IRI and hydration/emission paths. Distinguish object property `odo:hasSubjectiveTrait`, which does exist. |
| O04 | Java ordering rank uses `klab:orderingRank`; declared annotation is `odo:orderingRank` | Align namespace and persist/reload tests. |
| O05 | `odo:descreasesWith` spelling and Ordering-only range | Choose a stable IRI/migration and intended allowed filler types before wiring `decreases with`. |
| O06 | `odo:requires` has four ranges: Attribute, Extent, Identity, Realm | Multiple range axioms mean intersection, not alternatives. Review union range or typed subproperties. |
| O07 | `odo:affects` has Event and Process domains; `creates` and `emergesFrom` each have Countable and Quality ranges; `describesQuality` has Attribute and Quality domains; `increasesWith` has Ordering and Quality ranges | Each repeated domain/range is conjunctive. Check satisfiability under class disjointness and replace with explicit unions if alternatives were intended. Existence of a named property does not establish soundness. |
| O08 | No agreed payload properties for description values, authority parameters or contextualized implication/emergence | Preserve structured syntax first, then choose OWL datatype/reified relations versus operational metadata. These are design gaps, not simply misspelled properties. |

## Ordered completion plan

1. **Restore lossless beans.** Resolve local references with namespace and scope;
   retain both kinds of `within`; model affects/creates separately; add targets and
   payloads to Description/Requirement, including ATTRIBUTE and authority parameters;
   expose implications; retain metadata and denied aliases. Register nested names
   at the correct point for same-ontology references. Add parser-to-bean assertions
   for every W-row and each allowed payload alternative.
2. **Restore the service contract and adapter.** Add missing fields (disjointness,
   denied alias, contextualization, implications and payloads), then copy every
   field recursively. Existing list fields are not evidence of implemented transfer.
   Add bean-to-Kim and serialization tests with nonempty, distinct sentinel values.
3. **Resolve core contracts O01–O08.** Agree quantification, list conjunction versus
   disjunction, conditional `within`, negation, closed-world requirements and alias
   clause legality. Verify property kind, domain/range and imports with OWLAPI;
   use a reasoner to check consequences on deliberately disjoint fixture classes.
4. **Wire one complete declaration compiler.** Enable existing affects/creates/
   links/inherits handlers only after transfer and core checks; implement absent
   clauses, route aliases/children consistently, reject unsupported combinations
   explicitly, and preserve runtime emergence registration where needed. Repair
   role/trait reconstruction, logical equivalence and restriction placement.
5. **Gate on OWL entailment and persistence.** For each W-row assert emitted axiom
   and non-emission of unintended reverse/universal restrictions; also prove its
   intended entailment and satisfiability. Include multiple targets, local names,
   nested children, alias/core declarations, scoped clauses, invalid references,
   repeated declarations/cache hits, serialization, unload and ontology reload.

Until these steps pass, the pipeline cannot be signed off as preserving the
meaning of every Worldview declaration. The existing status/observable regression
tests remain useful but do not cover this missing declaration-clause pipeline.
