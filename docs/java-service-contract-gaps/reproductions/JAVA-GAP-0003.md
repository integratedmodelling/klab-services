# JAVA-GAP-0003 — Installed Resources does not advertise source model-candidate protocol

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **NOT_TESTED**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: resources; component: ServicesAPI.RESOURCES.MODEL_CANDIDATES / ModelCandidateRequest.
- Original roadmap: RC-P4.1, CAP-13.
- API: POST /api/v1/models/candidates.
- Request/schema: python-client/docs/model-candidate-sources.md.
- Tested Java build/environment: Repository source baseline004a93196 inspected: ModelCandidateRequest/ModelCandidateSources, ResourcesProviderController, ResourcesClient supplier-scope stripping and SemanticValidationRequest.sourceHash. Historical installed202610301844 capability omission remains separate; no fresh Java execution..
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Historical installed202610301844 capability omitted modelCandidateProtocol; newer source defines version1. Current endpoint compatibility NOT_TESTED.

## Independent simulator implementation and fixtures

Explicit candidate-sources fixture advertises source-defined protocol1, bounded ModelCandidateRequest selectors and ResourceSet source snapshots. Synthetic project-owner READ policy plus source-shaped public/private/project-private/scenario/inactive filtering; exact source UTF8SHA256/member/namespace/provider checks. Sync/nativeasync SDK refuses unadvertised or failed suppliers, does not delegate Runtime workflow headers or fall back. No selection/admission/publication/Job or scientific execution.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-INSPECTION-001, SIM-SELECTION-001, SIM-CANDIDATE-SOURCES-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_semantics.py::test_meaning_based_catalog_inspection_remains_advertisement_not_selection`
- `python-client/tests/unit/test_model_candidates.py`
- `developer-tools/klab-service-simulator/tests/test_model_candidates.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0003 --output simulated-JAVA-GAP-0003.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0003 --output real-JAVA-GAP-0003.json
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

Original reproducer/evidence: notes/python-roadmap-execution/MODELER_IDE_STATE.md

Source-defined route/DTO, not an invented Java API. Client inspection is now implemented behind exact protocol1advertisement, bounded validation and no legacy fallback; source snapshots are not semantic admission or authoritative model selection. Need matching maintained deployed DTO/principal/visibility/error captures for fresh real compatibility; original NOT_TESTED status unchanged.

- docs/JAVA_SERVICE_CONTRACT_GAPS.md
- python-client/docs/model-candidate-sources.md
- notes/python-roadmap-execution/evidence/candidate-source-authority-20261107.json
- notes/python-roadmap-execution/evidence/full-roadmap-candidate-sources-20261107.json
