# Validation increment and review fixes

Historical results for commit d6e781d. The subsequent `SCHEMA_VALIDATION_INCREMENT.md` supersedes the Java JSON Schema blocker below with a real NetworkNT provider; the other boundaries still apply.

This follows pinned first-slice commit `e6ad32ca048c5e9468ab722909c44b541ee98b7a` on the same local branch. The first commit remains intact. No service startup, authoritative source change, publication or IDE edit is included.

## Additive contract for IDE

`ProposalReview.CheckKind` adds **ADAPTATION**. The default WorkflowManager validator now performs actual Worldview Xtext parsing and LanguageAdapter conversion using a fresh WorldviewValidationScope for every candidate. Parser diagnostics include line positions; adaptation diagnostics include line/offset when the AST provides them. Candidate checksum/contextDigest remain in the frozen StageData candidate. ADAPTATION PASS is additionally required for acceptance. A fresh scope cannot prove current imported context, so imported hydrology remains BLOCKED even when it parses. JSON Schema, authoritative imports and Reasoner remain blocked in the Java service. No new HTTP endpoint or Command field is introduced.

The offline pilot manifest and its PILOT_MANIFEST/ROOT_CONTEXT results are **not new service DTO enum values** and must not be deserialized as server Check records. They are separate local validation evidence. The final acceptance gate remains blocked; no client report can override it.

Submitted bootstrap-comments and supporting-material descriptors are now carried immutably into peer/community review. Their original attachment IDs/checksums are preserved. Explicitly assigned REVIEWERs may execute only transitions that admit REVIEWER, in a contributing review stage with a non-SUBMIT proposal-review operation. They do not gain generic state editing or upload/deletion rights. Request-changes defaults the returned editing stage's owner to the flow's author.

Unknown Command versions fail. Existing shared JSON transport deliberately ignores unknown fields for compatible reads; ignored fields never authorize decisions or contribute to candidate identity. The full proposal schema independently rejects unauthorized document properties when the offline schema check runs. Extra YAML documents, empty trailing documents and multiple JSON roots are rejected by the candidate inspector. Action uniqueness is linear using a set.

Application-side limits (before validation/hashing/persistence, after HTTP deserialization): proposal 4 MiB, ontology 2 MiB, other upload 16 MiB, stage total 32 MiB, 256 attachments/stage, 10,000 actions/proposal, 5,000 dossier records, 16,000 rationale characters. These do not replace HTTP request-body limits. The byte/stage attachment limits apply to workflow uploads generally; the constants live in ProposalReview for client discoverability.

## Four-artifact local pilot

`tools/review/validate_package.py` uses the real **jsonschema Draft202012Validator**, with the repository's unchanged context-pack 1.3 schema and strict single-document YAML. It checks exactly four role-distinct artifacts: PROPOSAL, ONTOLOGY, DOSSIER and SOURCE_QUESTIONS. Each manifest entry freezes saved-byte SHA-256 plus the same proposal ID/revision; the proposal's internal identity must match. Paths must remain in the package. Explicit assetMapping connects the narrow proposal asset to a stable research concept; broader dossier entries are not silently included as proposed/approved assets. Import revision IDs must match the proposal manifest; missing authoritative imported hashes are BLOCKED and mismatching supplied current snapshots FAIL.

The role set is a **version-1 pilot package contract**, not a silently imposed production standard. The preserved research JSON and Markdown have no native proposal revision field: their relationship to this revision is the manifest's exact saved-byte binding. A package checksum is not scientific approval or proof of correspondence between every research idea and the narrow candidate.

Real fixtures are under `klab.services.resources/src/test/resources/review/hydrology`. Copied byte-for-byte from task-4's `imod-strawman/experiments/strawman-2026` working tree (base HEAD `98c68ba58b206c077add6fbdeb2c6d6e06fe3782`): review/surface-catchment-r1.yaml, candidate/src/hydrology.kwv, bootstrap/hydrology/dossier.json, QUESTIONS_SOURCE_FIRST.md and backend-dossier.sample.json. The research files were local working changes, so HEAD is provenance context, not a claim that every fixture is committed there. Exact copied hashes are in manifest.json. Explicit mapping: proposal `concept-surface-catchment` → research `hydrology-SurfaceCatchment`. The larger dossier contains unresolved candidates; it does not replace the minimal proposal or prove its scientific correctness.

`review-evidence/hydrology-package-validation.json` records real schema/manifest PASS and import/scientific/root/Reasoner blockers, including schema hash and library version. The program always reports acceptance BLOCKED; even forged scientific/root flags cannot manufacture a human decision.

## Why schema and Reasoner are not wired into production yet

The only cached Java JSON Schema validator found is Justify 2.0.0, which supports Draft 4/6/7. The actual proposal schema declares Draft 2020-12. Downgrading the declared dialect or writing a partial schema interpreter would misstate validation. The ontology task has isolated Python jsonschema 4.26.0/PyYAML dependencies; these power the executable offline package checks without adding a Python subprocess runtime requirement to Resources.

The existing ReasonerService.validateDocument hook requires knowledgeReady, initialized OWL and an available Resources service. For changed ontology bytes it can synchronize authoritative saved sources and call knowledge update paths. It is not a read-only isolated draft validator. This increment does not invoke it or bootstrap a new reasoner/service. Resources parse/adaptation machinery is reused directly with a fresh nonpersistent scope.

Concrete next integration: select and pin a maintained Draft 2020-12 Java validator using an authorized dependency setup; implement the same bundled-schema checks behind ProposalCandidateValidator; agree a transport manifest referencing real attachment IDs and bind its digest to Candidate before adding upload/decision handling; provide a verified revision/hash import snapshot and a Reasoner validation session that cannot synchronize or publish authoritative sources. Preserve scientific/root blockers independently of parser success. No new ontology engine or PR publisher is required.

## Storage correction

Unbound attachment removal now persists descriptor removal before attempting payload garbage collection. A failed aggregate put leaves both descriptor and blob intact. The blob is retained while any stored flow descriptor still references it. Failed garbage collection leaves a safe orphan and logs a warning. The existing catalog projection and multi-process CAS limitations remain unchanged.

## Reproduction

Java: `./run-review-tests.ps1` (offline reactor; test home inside task-7).

Offline package checks require Python's existing jsonschema and PyYAML packages. In this workspace they were read from task-4/.python-deps through a process-local PYTHONPATH; no packages were installed and no shared cache was changed:

```powershell
$env:PYTHONPATH='C:/Users/Ferd/Documents/Codex/2026-10-03/task-4/.python-deps'
python tools/review/test_validate_package.py
python tools/review/validate_package.py klab.services.resources/src/test/resources/review/hydrology
```

Final test counts and commit are reported in the handoff after the regression run completes.


## Final validation results

Offline seven-module reactor: **BUILD SUCCESS**, exit 0.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ProposalDocumentTest | 2 | 0 | 0 | 0 |
| IsolatedProposalCandidateValidatorTest | 5 | 0 | 0 | 0 |
| ProposalReviewProtocolTest | 24 | 0 | 0 | 0 |
| WorkflowManagerAuthorizationTest | 9 | 0 | 0 | 0 |
| WorkflowSchemaTest | 5 | 0 | 0 | 0 |
| WorldviewValidationTest | 12 | 0 | 0 | 0 |
| **Java total** | **57** | **0** | **0** | **0** |

Offline Python package suite: **14 passed**, zero failures/errors. Together: **71 passing tests**. The five isolated-validator tests use the actual parser/adapter and real hydrology bytes/DTO projection; no passing mock validates the real fixture.

Four independent defects have regression coverage in ProposalReviewProtocolTest (24 tests total). All six Java suite reports and the actual hydrology diagnostic output are under `review-evidence/increment/`; Python results are in `review-evidence/package-tests.txt`. Full final reactor log: `C:\Users\Ferd\Documents\Codex\2026-10-03\task-7\backend-validation-final.log`. `git diff --check` passed. Original checkout changes remain untouched.
