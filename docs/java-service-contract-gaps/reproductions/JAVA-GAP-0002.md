# JAVA-GAP-0002 — Supported owned RESOURCE cleanup contract absent in inspected delete branch

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **MISSING_API**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: resources; component: ResourcesProvider.delete RESOURCE handling (source finding).
- Original roadmap: RC-P4.3, RC-P4.4, CAP-13, CAP-14.
- API: POST /resources/import/{schema}/{urn}.
- Request/schema: python-client/docs/resource-imports.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED deletion on retained installation. Inspected ResourcesProvider.delete has no supported RESOURCE branch; no destructive probe performed.

## Independent simulator implementation and fixtures

Finite private imported resource with provisional test-owned cleanup

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-IMPORT-001, SIM-INPUT-BINDING-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_resource_imports.py::test_owned_upload_job_private_catalog_receipt_and_cleanup`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0002 --output simulated-JAVA-GAP-0002.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0002 --output real-JAVA-GAP-0002.json
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

MISSING_API is scoped source finding, not proof of absence on every deployment. Positive real upload requires approved disposable/retained-resource custody or supported cleanup; simulator cleanup is not a production delete API.

- docs/JAVA_SERVICE_CONTRACT_GAPS.md
- python-client/docs/resource-imports.md
