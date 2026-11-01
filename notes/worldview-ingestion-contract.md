# Worldview ingestion contract: implementation and remaining interfaces

Branch: `codex/worldview-ingestion-contract`
Base: `3b01ceaa907000ffccafac97444029027c81d30b` (`develop`)
Worktree: `C:\Users\Ferd\Documents\Codex\2026-10-12\task-4\klab-services`

## Implemented

- Ontology `in domain` ownership is persisted as declaration annotations, including nested declarations. Aliases and referenced/imported concepts retain their defining domain. Reasoner domain lookup checks this ownership before the legacy ancestry fallback.
- KnowledgeDomain declarations require effective explicitly authored interpretation targets; provenance-bearing inherited links count. Every target must specialize PhysicalDomain. Anonymous OWL existential inference is deliberately insufficient. The approved property is `odo:interpretsPhysicalDomain`; ODO artifacts are not changed here.
- Concrete qualities owned by KnowledgeDomain require effective describes grounding. Inherited grounding counts; abstract declarations may defer. Describes remains legal elsewhere.
- Declaration compilation stages all description/bearer clauses and checks required properties before emitting those clauses. `within` now participates in validation and translation.
- Bearer reconciliation preserves asserted/inherited/inferred/equivalent property and quantifier information. Only explicitly marked k.LAB semantic bearer constraints form a shared universal intersection. Imported existential witnesses remain independent; subproperty universals do not become superproperty universals. Proven required-witness conflicts reject; remaining uncertainty is preserved.
- Derived necessary restrictions affect the describing concept only, preserving original restrictions and recording authored/inherited/derived evidence. Existential and all-values bearer restrictions do not identify bearer instances.
- Raw `as` source is retained in OWL annotations with UNRESOLVED status and a DESCRIPTION_VALUE_UNRESOLVED warning. Document validation returns UNAVAILABLE (including nested declarations), so its `valid()` certificate is false. The raw text is neither rejected merely for lacking typed transport nor certified as typed validity, value matching or runtime enforcement. Partitionless discretizes remains explicitly unsupported.
- `KimValueMappingValidator.validateDiscretization(classification, applicability)` reuses the numeric partition checker and additionally checks declared-range endpoint coverage. Empty/NaN intervals reject. Ordinary classifications remain nonexclusive/nonexhaustive. Callers must normalize compatible units before calling this structural checker.
- Increases/decreases are documented as nondecreasing/nonincreasing within fixed scope and conditions, allowing plateaus and implying no causality. Marks assigns no value, truth or threshold. Models remain responsible for computation.
- Post-translation inconsistency diagnostics identify a validation/translation defect for accepted input, rather than presenting OWL rejection as the intended validation contract.

## Existing type matrix retained pending review

| Relation | Existing source | Existing target |
|---|---|---|
| describes | Quality or Predicate | Quality |
| increases/decreases | Quality | Quality |
| marks | Quality | Presence |
| classifies | Predicate | Quality |
| discretizes | Ordering | Quantifiable (translation currently blocked without a partition) |

No language relation is mapped to the proposed unconstrained general describes property. Removing the marks Presence target check would also be a type-matrix change, so that check remains; it does not assign a boolean value. Matrix broadening is independent of bearer reconciliation, ownership, necessary characterization, and partition checks and can be reviewed separately.

## Remaining integration blockers

1. ODO 0.1.1 domain kinds and interpretsPhysicalDomain are supplied by the separate ODO track. This branch neither edits nor copies those artifacts. Domain tests provide isolated fixtures matching their names; production activation requires integrating that track.
2. The new authored-link API field survives JSON transport, but the external language grammar/adapter does not yet provide an approved source-language form. No form was invented. A KnowledgeDomain parsed without authored links will correctly reject. The minimal language addition must carry source-located explicit physical-domain targets into `KimConceptStatement.getInterpretedPhysicalDomains()`.
3. `getDescriptionValue()` is only raw source text. Minimum input for future normalization: typed scalar/range/category, explicit endpoint inclusion, source unit and unit-compatibility evidence, identity/authority reference with known/unknown ancestry, applicability and actual/required/permitted modality, and lexical provenance. Necessary characterization must never become reverse sufficiency or computation; unknown ancestry must remain unresolved and category matching must use self-plus-specializations closure. This is a proposal, not an implemented transport schema.
4. Description declarations have no partition/applicability-range field. A future adapter can call the existing numeric validator overload after unit normalization. Its coverage check clips categories to the applicability range; overhang and outside gaps are allowed, while in-range nonoverlap, contiguity and endpoint ownership are required. Current NumericRange beans also lose authored endpoint presence/reversal information in some construction paths; preserve that in the future AST rather than guessing from defaults.
5. There is no approved clause input expressing a cross-bearer alignment relationship. Incompatible bearer constraints reject; they cannot be waived by equal bearer types or by assuming the same instance.
6. Runtime enforcement/transport is intentionally not implemented. Coordinate future normalization with the separate proposal `normalizeDescription(DeclarationClauseInput, SemanticSnapshot, PrecisionPolicyRef)`. Its `bindContracts`/`evaluateFact` remain separate runtime work.
7. This increment stages clause emission, not a whole-worldview scratch-ontology transaction. Existing declaration construction creates class/parent scaffolding before clause validation and existing authoring mode can retain rejected scaffolding with errors. Full preflight/rollback across a worldview remains follow-up work; this is not claimed as complete ingestion atomicity.

## Verification

See task-root `stage1-tests.log`, `stage2-tests.log`, and final aggregate log for actual results. Initial sandbox attempts (`baseline-tests.log`, `baseline-detail.log`) were blocked by Java `AccessDeniedException` resolving the shared cached JUnit jar. Scoped offline tests subsequently ran outside that sandbox with a task-local writable Maven head repository and isolated test home; no shared cache or security configuration was changed.

Stage 1: 9 existing declaration/clause tests passed. Final counts are recorded in the completion report.

## Changed files

- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/KimConceptStatement.java`
- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/impl/KimConceptStatementImpl.java`
- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/lang/kim/impl/KimValueMappingValidator.java`
- `klab.core.api/src/test/java/org/integratedmodelling/klab/api/lang/kim/KimValueMappingValidatorTest.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWLSemanticClauseSupport.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDeclarationSupport.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/DescriptionBearerSupport.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDomainSupport.java`
- `klab.services.reasoner/src/test/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDeclarationSupportTest.java`
- `klab.services.reasoner/src/test/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewIngestionContractTest.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/OWL.java`
- `klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/SemanticsBuilder.java`
- This note.

Verification commands use Maven offline with:

```text
-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-4/m2
-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository
-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-4/test-home
```

Focused: `mvn -o -pl klab.services.reasoner -am test -Dtest=WorldviewIngestionContractTest,WorldviewDeclarationSupportTest,OWLSemanticClauseSupportTest,KimValueMappingValidatorTest -Dsurefire.failIfNoSpecifiedTests=false` plus those isolation flags.

Aggregate: `mvn -o -pl klab.services.reasoner,klab.services.resources -am test` plus those isolation flags.

The source checkout remains on `develop`; its original four untracked `review-evidence` directories were preserved. No live service, ODO artifact, subjective/any resolution code, shared cache, or persistent security configuration was modified. No push, publication, merge or deployment was performed.

Focused verification completed: `stage3-tests.log` reports BUILD SUCCESS: 3 API tests plus 20 Reasoner tests, including 11 new ingestion cases, all passed. `stage2-tests.log` recorded a compile error in reload cleanup that was repaired before this successful run.

The unrestricted combined aggregate encountered two errors in unchanged `ExportDispatchTest` code: `missingPngTriggersComponentLoadAndCapabilityRefresh` throws `NoSuchElementException` at line 53, and `overloadedExportRetainsTransportedAnnotationAndRequestParameters` reaches a null `serviceFinder` in `ComponentRegistry.getFunctionDescriptor`. These are not ingestion-contract assertions. They have not been independently reproduced at the pristine base, so this report does not label them proven pre-existing failures. The full aggregate is not a pass. Its API suite (59 tests) and common suite (210 tests) passed before these errors.

The unrestricted aggregate was interrupted using its own execution session after four confirmed failures, before completing unrelated agent-runtime tests. In addition to the two ExportDispatchTest errors, `BehaviorAnalyzerTest.integerLiteralsWidenForFloatingPointVerbsIncludingInspectorUnitcheck` and `javaIntegerParametersAcceptOnlyLongLiteralsWithinRange` failed because generated Java accesses a package-private test fixture. No live application process was stopped. The follow-up `worldview-review-tests.log` uses the repository review selection (WorkflowSchemaTest, WorkflowManagerAuthorizationTest, ProposalReviewProtocolTest, ProposalDocumentTest, IsolatedProposalCandidateValidatorTest, ProposalSchemaValidatorTest) plus `Worldview*`, `OWLSemanticClauseSupportTest`, and `KimValueMappingValidatorTest` across Reasoner/Resources and their reactor dependencies. This does not erase or replace the unrestricted aggregate failure.

## Historical bounded checkpoint requested by parent (superseded below)

The follow-up worldview/review run was stopped through its own execution session at the parent's requested checkpoint while compiling core services. Reasoner/Resources verification in that run is incomplete, not passed. Initial sandbox test runs were blocked; focused stage3 completed with 23 passes; the unrestricted aggregate exposed four failures and was interrupted; no full aggregate pass is claimed. After the successful focused run, the existing negative marks type regression test and the existing (currently unreachable because partition input is unsupported) discretizes Quantifiable target check were restored. Those final compatibility-preserving edits still need the next verification run.

All changes are uncommitted in the isolated branch. Test-generated `klab.core.services/extensions.idx` and `jte-classes` were preserved outside the repository in task-root `test-generated/`. No task-owned Maven verification run remains intentionally active. New contract fixtures are synthetic; guide examples were not used as conformance oracles.


## Next language-form decision (not implemented)

Read-only grammar evidence: `C:\Users\Ferd\git\klab-languages`, HEAD `8f5c29363f4362bddd13f9fdd4f43e7e2c9fb024`.

- `org.integratedmodelling.languages.worldview/src/org/integratedmodelling/languages/Worldview.xtext`: OntologyPreamble has exactly one `in domain ConceptExpression`. PrimaryConceptDefinition and nested ConceptDefinition have no interpretation-link clause. Existing `requires`, `implies`, `links`, and `describes` have different settled meanings and must not be overloaded.
- The same grammar's DescribesClause already accepts `describes p:Quality as 3`, `as 3 to 5`, `as p:Category`, `as true`, and `as false` in a surrounding declaration. Both numeric range endpoint values exist in that syntax. It does not accept a Quantity in that position and does not expose range-boundary inclusion. Its public Description record contains only `(type, target, String valueSource)`.
- `org.integratedmodelling.languages.observable/src/org/integratedmodelling/languages/Observable.xtext` already has generic Annotation and ParameterList rules, including a single string literal parameter. Thus `@interprets("p:Physical")` before a concept statement is already syntactically expressible as a generic annotation. No validator or adapter currently gives that annotation the approved interpretation-link meaning. This is not yet reachable ingestion into interpretedPhysicalDomains.

Two minimal options, both requiring the user's semantic/form choice:

1. **Dedicated clause (recommended proposal, currently invalid grammar):** append `interprets p:Physical` to a KnowledgeDomain declaration; optionally a comma-separated list for multiple physical domains. Add one InterpretsClause member to both primary and nested definitions, expose typed references in the language API, collect dependency/reference edges, and adapt to interpretedPhysicalDomains. This preserves the single owning ontology domain.
2. **Use the existing generic annotation syntax (syntactically valid, semantics unimplemented):** `@interprets("p:Physical")` on the KnowledgeDomain declaration. Reserve the annotation name and define validated reference/list conversion and dependency edges before adapting it. This avoids a new grammar keyword, but string references lack the current typed ConceptRef path and require deliberate validation. Do not treat the annotation as functional today.

The relation's approved OWL name does not select either language form. No new source syntax, annotation semantics, type-matrix broadening, or grammar edits were made here. The API field currently supports programmatic/JSON input only.

## Smallest whole-worldview staging boundary

Current concrete mutation risks remain:

- loadKnowledge assigns the new worldview/observationReasoner, clears source/domain/authority state, and calls OWL.initialize, which resets/disposes the old graph, BEFORE declaration/domain validation finishes.
- updateKnowledge releases old ontologies/authority namespaces before the replacement is validated, and replaces the mutable worldview source list before compilation succeeds.
- buildInternal creates class/parent axioms, annotations and concept metadata and calls indexer.index before WorldviewDeclarationSupport.compile. Domain annotations are also bound before clause validation. Its resolver callback can itself create compound concept expressions. Child failures are caught individually and compilation continues; build catches failures and returns null.
- Saving only the declaring ontology's axiom set would therefore not roll back OWL class/namespace caches, indexes, imported/generated expressions, source hashes, domain mappings, authority bindings, strategy state or service readiness. This patch is NOT whole-worldview atomic and is not the full stated before-live-OWL boundary.

The smallest safe solution is an ingestion-local candidate snapshot, not a runtime redesign:

1. Build a candidate from authoritative source documents, with its own OWL manager/reasoner, class/namespace maps, index, domain/source/diagnostic maps, authority-binding plan, and observation-strategy state. Resolve/compile against that candidate, never via the live `this::declareConcept`. Buffer service/source-tag/index/persistent-configuration effects until commit.
2. Run k.LAB source, typed-clause, domain, bearer and reference preflight against the candidate semantic view. Preserve unresolved authoring results separately; UNRESOLVED value evidence must not certify typed validity, matching or operational enforcement. Do not publish a candidate as fully validated while required normalization is unavailable. Authoring admission and operational publication are distinct decisions.
3. Translate the validated plan into the isolated candidate graph, then use OWL consistency/satisfiability checks as software postconditions. Accepted semantic input failing this postcondition produces VALIDATION_TRANSLATION_BUG and discards the candidate; it does not modify the live graph or redefine validity as OWL's acceptance.
4. Publish one KnowledgeSnapshot reference under the existing ingestion lock, with one new revision. Readers capture that snapshot for their entire operation; source hashes, domain maps, indexes, authority state and strategies cannot be swapped independently. Defer disposing the previous reasoner until active readers finish. On failure, retain the previous operational snapshot and return candidate diagnostics; editing can retain the rejected source separately.

Required transaction regressions: a failing second declaration after a valid first leaves all live graph/index/hash/domain/binding state unchanged; failed child/import/domain/value preflight leaves the previous snapshot queryable; no source-tag/configuration effect escapes a failed candidate; successful publication changes revision exactly once and exposes a coherent snapshot. This proposal is localized to Reasoner ingestion and does not add runtime contracts or computations.

## Independent-review fixes

- Property/quantifier-tagged imported restrictions retain their independent witnesses. Explicit k.LAB translation marks known universal bearer mapping; generic restrictSome is not relabeled. SemanticsBuilder's only change is emitting that provenance marker after an INHERENT modifier, not changing subjective/any resolution.
- Authored applies-to is staged as one union, participates in dependent bearer scope, and stays distinct from predicate applicability.
- Inherited/equivalent bearer checks run even without local describes/within clauses.
- Numeric partitions are checked after intersection with declared applicability, allowing overhang and outside gaps.

`final-review-tests.log`: completed BUILD SUCCESS, 114 selected tests (3 API, 7 common, 25 Reasoner, 79 Resources), zero failures/errors/skips. This run covered the earlier compatibility restorations. The revised as handling and independent-review fixes were edited afterward and are being verified separately in `review-fix-tests.log`; do not attribute those changes to the earlier success.

The subsequent focused run `review-fix-verified-tests.log` compiled all revised implementation successfully. All 17 ingestion tests passed; its only failure was the existing serialization assertion expecting a plain axiom even when OWL serialization merges it with its provenance-annotated copy. The test now checks the same necessary restriction ignoring annotations and separately verifies that the universal-bearer provenance survives reload. Earlier `review-fix-tests.log` exposed an obsolete exact-envelope test assertion (fixed to allow overhang); `review-fix-final-tests.log` exposed a package-private helper access (fixed with the public Concept overload).

Final selected aggregate command, with the isolation flags above:

```text
mvn -o -pl klab.services.reasoner,klab.services.resources -am test -Dtest=Worldview*,OWLSemanticClauseSupportTest,KimValueMappingValidatorTest,WorkflowSchemaTest,WorkflowManagerAuthorizationTest,ProposalReviewProtocolTest,ProposalDocumentTest,IsolatedProposalCandidateValidatorTest,ProposalSchemaValidatorTest -Dsurefire.failIfNoSpecifiedTests=false
```

This selection exercises API value mapping, worldview transport, OWL ingestion/reload and Resources worldview/proposal/workflow review paths. It is not the unrestricted aggregate, whose four failures remain separately recorded above.

## Final verification and handoff

`C:\Users\Ferd\Documents\Codex\2026-10-12\task-4\final-contract-tests.log` completed BUILD SUCCESS (exit 0): **121 tests, zero failures/errors/skips** — 4 API, 7 common, 31 Reasoner (including 17 ingestion-contract tests), and 79 Resources. This run includes every final implementation and test edit, including raw-value UNAVAILABLE certification, quantifier/property preservation, authored applicability scope, inherited-only validation, coverage clipping, and serialization provenance. `git diff --check` passed afterward. No verification session remains active.

The implementation remains an uncommitted, reviewable increment, not completion of the entire settled ingestion contract. Source-language interpretation links, typed values/units/ancestry/modality, declared partition input and whole-worldview candidate publication remain the explicit blockers described above. The next language decision is dedicated typed `interprets p:Physical` syntax versus reserved generic annotation semantics; no currently parsed form populates the new API field.

The original checkout was rechecked at handoff: still `develop`, with the same four original untracked review-evidence directories and no tracked changes. Worktree branch and base remain as stated at the top. All 14 changed/new files are listed in the manifest. No ODO files, runtime enforcement implementation, subjective/any resolution algorithms, live services, or local memories were modified. The narrow SemanticsBuilder change only attaches universal-bearer provenance to an existing INHERENT translation.

## Follow-up: cardinality and inherited interpretation grounding

The complete 121-test source snapshot was copied to task-root `review-snapshot-121/` before follow-up edits (all 14 manifest files). The original `final-contract-tests.log` is retained. The new work remains on the same isolated branch/base and changes no additional paths.

The reviewer identified a residual maximum-cardinality conflict. Reconciliation now checks the owner class plus all staged bearer restrictions as a conjunction, retaining inherited/equivalent axioms and property scope. Bootstrap proves only sound individual lower/upper-bound contradictions (including zero maximum/exact cardinalities); it does not sum independently satisfiable existential witnesses. Remaining cases stay UNRESOLVED until a DL reasoner proves compatibility or impossibility. Added cases cover direct maximum zero, inherited exact zero, super/subproperty scope, and a multi-witness cardinality conflict that bootstrap leaves unresolved and HermiT rejects.

Interpretation grounding now accepts effective explicitly authored inherited links. Anonymous existential entailment still does not count. Parent obligations remain additive; targets are not intersected into one physical domain or one bearer. `WorldviewDomainSupport` exposes:

```java
record InterpretationGrounding(Concept target, String declaringConcept, boolean inherited) {}
List<InterpretationGrounding> effectiveInterpretations(OWL owl, Concept owner)
void markInterpretation(OWL owl, Ontology ontology, Concept owner, Concept target)
```

`markInterpretation` is called only after typed validation/property preflight. It emits an annotated necessary link using `odo:interpretsPhysicalDomain` and marker `urn:klab:worldview:authored-interpretation`. The collector follows asserted/equivalent/inferred named ancestry, preserves the originating declaration, and ignores unmarked existentials. The existing `interpretations(...)` preflight validates effective targets and returns only local targets for emission. No bearer property is emitted by these helpers. A new test covers inheritance, additive local targets, anonymous-witness rejection and serialization provenance.

The user accepted `about` for a separate implementation track. Read-only grammar inspection found no existing keyword/operator use across the language grammars. Worldview.xtext:19 already accepts ConceptExpression in the domain slot; Observable.xtext:45/60/83 supplies expression/modifier/operator routing. Thus the needed grammar extension is a typed ABOUT modifier. Existing OF maps through SemanticSyntaxImpl:269 and LanguageAdapter:372 to INHERENT, and SemanticsBuilder:592 emits isInherentTo; this path must not be reused for domain subject matter. The existing domain parser also suppresses syntax-layer semantic diagnostics, so adapter/Reasoner type validation must remain explicit.

ABOUT's general AST representation belongs to the separate track and must be extensible for future subject-matter uses. The current `interpretedPhysicalDomains` field and these helpers are only the settled KnowledgeDomain-to-PhysicalDomain projection. Future normative uses must not automatically become interpretsPhysicalDomain links. No grammar or adapter change was made here.

`cardinality-contract-tests.log` was interrupted through its own execution session when inherited grounding was added to scope; it is incomplete. The combined final selection is recorded separately in `cardinality-inheritance-tests.log`.

Combined final verification completed BUILD SUCCESS (exit 0): **125 tests, zero failures/errors/skips** — 4 API, 7 common, 35 Reasoner (including all 21 ingestion-contract regressions), 79 Resources. All follow-up source/test edits are included. `git diff --check` passed. No verification run remains active.

Baseline coordination was checked read-only against task-2's completed `base-aggregate-tests.log` and `patch-aggregate-confirmation.log`. The untouched base is exactly 3b01ceaa with no tracked diff; both runs reproduce the initial four failures and four additional core-services failures/errors (657 tests, 4 failures, 4 errors, 2 skips in that module). This establishes baseline reproduction of the originally observed failures, but does not turn this task's interrupted unrestricted aggregate into a pass.
