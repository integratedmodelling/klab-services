# JAVA-GAP-0009 — Generic accepted-source conditional update/history/publication contract unestablished

Supporting runner/evidence only. The canonical contract, status and inline reproduction are in
[JAVA_GAPS_FOR_FERDINANDO.md](../JAVA_GAPS_FOR_FERDINANDO.md#java-gap-0009).
Generated from the synchronized index; simulator evidence is not native acceptance.

## Engineering decision and immediate next action

- Classification: **CLIENT_CONTRACT_PROPOSAL**; workflow: [Accepted-source revision safety](../JAVA_GAPS_FOR_FERDINANDO.md#inputs-and-provenance).
- User-impact priority: P1; Java owner of record: UNASSIGNED (do not infer assignment from issue authorship).
- Required native input / owner action: Resources maintainer must identify supported accepted-source version/conflict/validation/publication semantics and a disposable project. Principal-shared native locks and ADD-only authoring are narrower existing alternatives.
- Expected after Java change or usage clarification: The agreed accepted-source operation makes stale concurrent updates conflict and binds publication to exact source and knowledge validation; native readback and permissions confirm the result without automatic mutation retry.
- Historical observations and source findings retain their original scope; current-build execution is not inferred.

## Classification and affected contract

- Independent status triplet: CONTRACT_PROVISIONAL; SIMULATOR_PASS; REAL_NOT_RUN.
- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: authoring; component: ResourcesProvider / WorkspaceManager authoring and project lock operations.
- Original roadmap: RC-P4.2, RC-P4.4, CAP-14.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: python-client/docs/revision-draft-reference.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Source only: project lock ownership is principal-shared, not revision-fenced. Specialized rejected-ontology selectors include sourceVersion; generic accepted documents lack that same admitted selector contract. No live project mutation.

## Independent simulator implementation and fixtures

Existing ADD-only native source projections and principal-shared boolean locks remain separate. Explicit opt-in klab.experimental.drafts/1 private root-owner draft CAS retains8names/8versions, exact source/hash and UNVALIDATED_PRIVATE_DRAFT; stale writes409, evicted history410, capacity429. Private draft SQLite restart/corruption/rollback/cleanup remains unvalidated, not accepted publication. Separate advertised acceptedDocumentProtocol reference now implements independent ephemeral source CAS, server-owned finite diagnostics and atomic source/knowledge-conditioned reference publication/catalog readback; edits invalidate validation but preserve old publication snapshots, stale source/knowledge and invalid diagnostics block publish, owner/root denial, lost reply readback without replay and cleanup. Eight declared literal namespaces, no native compiler, scientific admission, ontology repair or real Resources publication.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-PROJECT-LOCK-001, SIM-BEHAVIOR-DRAFT-001, SIM-INPUT-BINDING-001, SIM-REVISION-DRAFTS-001, SIM-NOTEBOOK-PRINCIPAL-RACE-001, SIM-CONDITIONAL-AUTHORING-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_project_locks.py::test_native_project_lock_principal_sharing_and_explicit_release`
- `developer-tools/klab-service-simulator/tests/test_behavior_drafts.py::test_nonexecuting_behavior_draft_add_uses_exact_native_source_and_never_instantiates`
- `python-client/tests/unit/test_revision_drafts.py`
- `developer-tools/klab-service-simulator/tests/test_revision_drafts.py`
- `developer-tools/klab-service-simulator/tests/test_notebook_principal_races.py`
- `python-client/tests/unit/test_accepted_documents.py`
- `developer-tools/klab-service-simulator/tests/test_accepted_documents.py`
- `developer-tools/klab-service-simulator/tests/test_accepted_document_fixture.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0009 --output simulated-JAVA-GAP-0009.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0009 --output real-JAVA-GAP-0009.json
python developer-tools/java-gap-governance/native_probe.py --gap JAVA-GAP-0009 --output preflight-JAVA-GAP-0009.json
```

The real-mode command intentionally records REAL_NOT_RUN and exits3 without network calls.
It is an executable missing-adapter disposition, **not** an actual native test or a simulator fallback.

## Required native startup prerequisites and executable test plan

1. Obtain authorization for an owned disposable native fixture and its cleanup policy; never reuse unrelated Jobs.
2. Pin service/component build hashes, endpoint identities, principal roles, worldview and fixture revisions; consult MODELER_IDE_STATE.md and run its bounded read-only gates only for this live-dependent work.
3. Start the maintained service tuple using the operator's supported procedure. Python-only development does not authorize IDE/service repair.
4. Supply a reviewed bounded native adapter for the exact contract and run the linked public Python workflow with explicit real endpoints, no automatic retries or simulation fallback.
5. Capture request/response/error, positive/negative authorization, independent scientific checks where relevant and owned cleanup; compare with the acceptance criteria above.
6. If no reviewed adapter/build/permission exists, retain NOT_RUN. Do not invent native endpoints or turn simulator success into a Java fix.

Existing native test references: none reviewed for this complete gap

## Custody, related trackers and remaining questions

Original reproducer/evidence: docs/JAVA_SERVICE_CONTRACT_GAPS.md

This does not deny all Java editing. Proposed private draft HTTP route is not native Java endpoint authority. Independent installedSDK notebook clients verify same-owner CASrace/foreign denial/history/cleanup, not native source admission or cross-principal collaboration. Need maintained accepted-source version/conflict/permission/validation/publication semantics; private CAS alone is not full authoring completion. #129 remains a release-decision umbrella, not evidence this native gap is fixed.

- https://github.com/integratedmodelling/klab-services/issues/129
- python-client/docs/project-locks.md
- python-client/docs/behavior-source-authoring.md
- python-client/docs/revision-draft-reference.md
- python-client/docs/notebook-principal-races.md
- notes/python-roadmap-execution/evidence/full-roadmap-notebook-principal-races-20261107.json
