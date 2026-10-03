# Proposal review API and stage-extension contract — version 1

Worktree: `C:\Users\Ferd\Documents\Codex\2026-10-03\task-7\backend`
Branch: `codex/proposal-review-backend`
Base: `0702088acf9bbc8adcfa0b90ea1ee865526864ed`; verified remote develop with `git ls-remote` (PR80/81/82 included).

## Transport and type ownership

Existing `Flow.State` is the stage. Existing `ResourcesService.transitionFlow` and `POST ServicesAPI.RESOURCES.FLOW_TRANSITIONS` carry the extension; initialization uses the existing atomic initialization endpoint. No IDE-specific HTTP API is introduced. The API classes are in `org.integratedmodelling.klab.api.services.resources.workflow`:

* `Flow.State.proposalReview`: nullable `ProposalReview.StageData`, server-owned.
* `Flow.TransitionRequest.proposalReview`: nullable `ProposalReview.Command`.
* `Command(version, candidate, rationale, dossier)`; version must be 1.
* `Candidate(proposalId, revisionId, supersedesRevision, proposal, ontology, actionIds, contextDigest)`.
* `Artifact(attachmentId, checksum)`; checksum is lowercase SHA-256 of stored bytes.
* `StageData(version, candidate, status, actor, rationale, validation, dossier)`.
* `Status`: IN_REVIEW, CHANGES_REQUESTED, ACCEPTED, REJECTED.
* `Check(kind, status, messages)`; kinds IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, ADAPTATION, REASONER, SCIENTIFIC_REVIEW, APPLICATION, PR_HANDOFF; statuses PASS, FAIL, NOT_RUN, BLOCKED.
* `BootstrapDossier(evidence, concepts, questions, qualityAnalyses, unresolvedSemantics, coverageShortfalls)` and its concrete nested record types are defined in `ProposalReview.java`. Lists are immutable snapshots. These transport records are distinct from `org.integratedmodelling.common.review.ProposalReview`, which models context-pack document review records.

`org.integratedmodelling.common.review.ProposalCandidateBinding.inspect(proposalArtifact, ontologyArtifact, proposalBytes)` is shared by backend and IDE. It extracts exact proposal/revision/action binding and contextDigest. It checks identity structure and rejects duplicate YAML keys; it does **not** establish full JSON Schema validity. contextDigest is SHA-256 over the UTF-8 compact Jackson JSON tree of `proposal.existing_ontologies`, preserving source field/list order. Use the shared inspector rather than reimplementing this serialization in IDE Java. The backend independently verifies descriptor ownership, media, descriptor checksum and actual bytes, then repeats inspection.

## Smallest vertical slice

1. Initialize editing with one context-pack 1.3 proposal (`bootstrap-proposal`, `application/vnd.klab.proposal+yaml`), comments (`bootstrap-comments`, `application/vnd.klab.comments+json`) and optional ontology (`candidate-ontology`, `application/vnd.klab.ontology`). Set first transition to `submit`, with `proposalReview: {version:1, candidate:null, rationale:"Review requested", dossier:null}`. Only atomic initial submission permits null candidate: the server derives it from the unambiguous uploaded proposal and optional ontology.
2. Read the returned peer-review stage's `proposalReview`; it carries the frozen candidate, server results and dossier. IN_REVIEW means inspectable, not approved, and may contain blocked checks.
3. Request changes with `request-changes`, exact current candidate, nonblank rationale, and current `expectedRevision`. The new stage is editing and retains CHANGES_REQUESTED data. Earlier attachments and decisions remain immutable.
4. Upload a new proposal to this editing stage with the same proposal ID, a new revision ID and supersedes_revision equal to the reviewed revision. Add current comments. Upload a new ontology or select a previously stored ontology if genuinely unchanged. Use the shared binding inspector and submit the exact candidate, optionally with revised dossier, with `submit` and current expectedRevision. Submit is also resubmit.
5. `open-input` advances the same candidate to community-review. `public-input` and `request-changes` return to editing. Accept/reject decisions require exact candidate and rationale; refreshing expectedRevision cannot make an old candidate approvable.

Source stage authorization, owner/assignee constraints and transition roles still apply. Pass targetState.owner/assignees when assigning the next stage to another editor/reviewer. Review-stage candidate uploads are blocked: request changes before revising a candidate. Comments/supporting material can still be added. A targetState must not contain fabricated attachment descriptors; the server constructs terminal descriptors from verified existing payloads. Unknown command versions and negative expectedRevision (outside atomic initialization) fail.

## Workflow and acceptance behavior

`ontology-expert-review` is version 1.1. Transition metadata `proposalReviewOperation` opts into SUBMIT, REQUEST_CHANGES, ADVANCE, ACCEPT or REJECT. Other workflows retain ordinary transitions. Existing version 1.0 flows retain their pinned schema; no silent migration occurs. All terminal transitions now enforce target required attachments, including old definitions, and reject supplied descriptor-only targets. This deliberately closes the previous empty-terminal acceptance path.

Acceptance requires the exact reviewed proposal and ontology plus one PASS each for IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, ADAPTATION and REASONER. The validator must resolve current imported revisions/hashes and verify that the reviewed ontology corresponds to the proposal and approved action set. Checks are rerun at acceptance; a changed imported dependency blocks the old decision. A passed parser never implies scientific validity, implication/detection support or executed consequences. Human scientific acceptance records the authenticated actor and rationale separately.

`ProposalCandidateValidator` is the read-only integration seam, injected through a WorkflowManager constructor. The default implementation performs real isolated parsing/adaptation and **cannot accept** because schema/current-import/Reasoner checks remain BLOCKED. No full Draft 2020-12 Java schema or authoritative import/loaded-Reasoner provider is wired. Test PASS implementations are explicitly synthetic fixtures proving lifecycle gates, not real ontology validation. APPLICATION and PR_HANDOFF remain BLOCKED even after a test-supported acceptance; no ontology apply, commit or PR publication is implemented by the service.

Acceptance aliases verified existing immutable payload descriptors into the terminal state and writes that state, review data, source closure and transaction history in one `putFlow` aggregate operation. It never depends on a post-create callback or creates new blobs during acceptance. Failed aggregate writes leave the prior flow unchanged. ResourceInfo synchronization remains a separate existing catalog projection: aggregate/catalog atomicity and multi-process store CAS are **not** claimed. Manager synchronization covers one process only. Post-create callbacks remain notifications.

Direct create/delete/reopen of opted-in review stages/flows is blocked to prevent bypassing the audit lifecycle; archival needs a separately designed operation. A normal title/description update may round-trip unchanged server review data but cannot forge it or alter state status. Bound payloads cannot be deleted. Ordinary workflows keep their existing CRUD policy. Public-read retains the authenticated HTTP filter, the controller requirement for an EngineAuthorization backed by UserScope, the declared @Secured(Role.USER), and existing manager visibility rules. It does not introduce anonymous access. Effective Role.USER method-security enforcement has not been proven by an HTTP integration test; no enabling method-security annotation was found in this checkout. Reviewers can download the shared immutable proposal through an accessible review-stage descriptor.

## Bootstrap/dossier integration

Evidence, derived-type/ancestry claims, typed relationship endpoints, process/event bindings, question/concept incidence, explicit intent, invalid probes, unresolved semantics and quality analyses have typed read fields. `BootstrapDossierValidator.errors/validate/coverage` is in core API for IDE and service reuse. It checks stable IDs and references and distinguishes unknown information from proposed categories. The 5/5/5/5 concepts and 15 questions are coverage targets, never scientific gates or permission to invent entries.

See `DOSSIER_MAPPING.md` for the explicit hydrology research 0.1 mapping and incomplete conversions. The four-artifact research manifest is not yet standardized or validated here; bulk source files remain supporting attachments. Java-service JSON Schema validation, per-expression validation outcomes, authoritative import/Reasoner resolution, transport-manifest binding, comments/action-precondition execution and application/PR handoff remain integration work. The follow-up provides real offline Draft 2020-12 schema and pilot-manifest checks, plus real isolated ontology parser/adaptation checks in the default service validator. Keep the default acceptance gate blocked until the required provider is implemented and tested.

## Verification

The final test counts and local commit are recorded in `BACKEND_RESULT.md`. The original assessment harness is preserved in `review-evidence/WorkflowDemoAssessmentTest.java.txt`; it is historical characterization, not a green safety test. The three stale baseline expectations are corrected without changing the asset-review policy.

## Follow-up increment

See VALIDATION_INCREMENT.md for the additive ADAPTATION check, real isolated parser/adapter behavior, review evidence and assignee fixes, strict single-document parsing, limits, and the separate offline four-artifact pilot. The original production/schema/Reasoner blockers remain explicit.
