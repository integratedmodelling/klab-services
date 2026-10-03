# Backend outcome

Isolated worktree: `C:\Users\Ferd\Documents\Codex\2026-10-03\task-7\backend`; branch `codex/proposal-review-backend`. Base and verified remote develop: `0702088acf9bbc8adcfa0b90ea1ee865526864ed` (PR80/81/82). No applicable root AGENTS.md or .agents skills were found; the only tracked AGENTS.md is under docs/, which was not edited. Original Authority.java/Codelist.java changes are untouched.

## Implemented

Versioned typed proposal-review stage/command records and bootstrap dossier projection; shared candidate identity inspector and dossier structural/coverage validator; workflow 1.1 submit/request-changes/resubmit/advance/exact-decision lifecycle; immutable artifact/revision/action/import-context binding; server-owned actor/rationale/results; required terminal artifacts committed with the flow aggregate; rejection of stale revisions/candidates, corrupt payloads, fabricated descriptors and direct CRUD bypass. Scientific acceptance, parser checks, ontology application and PR publication are distinct.

The default validator is intentionally unavailable and blocks production acceptance. The acceptance success test injects an explicitly synthetic validator; no real ontology has been validated, applied or published by this task.

API handoff: [PROPOSAL_REVIEW_CONTRACT.md](PROPOSAL_REVIEW_CONTRACT.md). Research mapping: [DOSSIER_MAPPING.md](DOSSIER_MAPPING.md). Historical characterization: [review-evidence/WorkflowDemoAssessmentTest.java.txt](review-evidence/WorkflowDemoAssessmentTest.java.txt).

## Verification

Final offline reactor results are appended below after completion. Reproduce with `./run-review-tests.ps1` in a normal authorized shell. The restricted Java sandbox initially caused missing-package compilation errors; the same normal offline reactor used by the earlier assessment builds successfully. No install, deploy, network dependency resolution, or shared Maven cache edits were performed.

The baseline reproduced 14 tests with 3 stale expectation failures. Fixes update the bundled workflow count and optional asset-review candidate expectation, and explicitly require a fixture attachment in the atomic-initialization test. The original defect-characterization source remains unchanged as historical evidence; the new protocol tests assert desired safety behavior.

## Remaining work and deliberate boundaries

* Wire full context-pack JSON Schema, active grammar/parser, loaded Reasoner and current import revision/hash validation; verify proposal/action/ontology correspondence. Production acceptance must remain blocked until then.
* Standardize and validate the four-artifact research manifest, precise source-first question intent/provenance mapping and per-expression validation records. The mapping file identifies nonautomatic conversions. Research parse-pass is not imported as authoritative semantic success.
* Implement actual action-precondition execution/ontology application and optional PR handoff separately. No consequence engine, implication/detection runtime or automatic scientific consensus is claimed.
* ResourceInfo catalog synchronization remains a separate projection after the atomic flow aggregate write; multi-process CAS and aggregate/catalog transactions are not implemented.
* Preserve authenticated access: manager tests cover public readers and private reviewer artifact access. The existing HTTP filter requires authentication and the controller requires a user-backed EngineAuthorization; Role.USER is declared but HTTP method-security enforcement needs a dedicated integration test. No enabling method-security annotation was found in this checkout.
* Opted-in review flows block direct stage/flow deletion, creation and reopen to preserve evidence; archival/migration of existing version-1.0 reviews needs an explicit operation. Old definitions stay version-pinned and cannot take the former empty-terminal acceptance shortcut.

No backend push, PR, merge, deployment, production service change or klab-ide edit was performed. Local commit ID is provided in the final handoff.

### Final test result

Offline seven-module reactor: **BUILD SUCCESS**, exit 0.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ProposalDocumentTest | 2 | 0 | 0 | 0 |
| WorkflowSchemaTest | 5 | 0 | 0 | 0 |
| WorkflowManagerAuthorizationTest | 9 | 0 | 0 | 0 |
| ProposalReviewProtocolTest | 16 | 0 | 0 | 0 |
| **Total** | **32** | **0** | **0** | **0** |

The 16 protocol tests cover the full submit → request changes → revised proposal → stale candidate rejection → exact candidate acceptance path, default-validator blocking, missing ontology, changed imports, tampered payloads, forged actions, omitted/stale expectedRevision, evidence/CRUD bypass, public reader authorization, failed aggregate persistence, invalid/duplicate-key YAML, dossier references/coverage diagnostics, private reviewer downloads, fabricated terminal descriptors and incorrect revision ancestry. Validation success in protocol tests is synthetic and is explicitly not evidence of actual ontology/parser/Reasoner validity.

Surefire text reports are copied under `review-evidence/`. Full final log: `C:\Users\Ferd\Documents\Codex\2026-10-03\task-7\backend-final-tests.log`. `git diff --check` passes. No full HTTP/server suite was run.
