# JAVA-GAP-0012 — Remote Java currentness correction lacks complete deployed acceptance

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **NOT_TESTED**; current execution **REAL_NOT_RUN**; historical **REAL_FAIL**.
- Service: runtime; component: ClientKnowledgeGraph.isObservationCurrent / ServiceContextScope.getObservation.
- Original roadmap: RC-P3.2, CAP-10.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: https://github.com/integratedmodelling/klab-services/issues/144.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Historical in-process production-client path threw UnsupportedOperationException. PR148 correction merged with bounded45-test and HTTP503 controls; issue explicitly retains paired-runtime deployed acceptance. Not freshly rerun.

## Independent simulator implementation and fixtures

Python graph HTTP failures retain honest stale/partial data; does not execute native Java ClientKnowledgeGraph

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-REFRESH-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_partial_refresh.py::test_native_awaited_refresh_uses_existing_routes_and_retains_honest_partial_pages`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0012 --output simulated-JAVA-GAP-0012.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0012 --output real-JAVA-GAP-0012.json
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

Original reproducer/evidence: https://github.com/integratedmodelling/klab-services/issues/144

Do not call the source fix absent or mark this JAVA_FIXED_VERIFIED from Python simulation. Preserve old-runtime compatibility and original semantic-binding/transaction assertions.

- https://github.com/integratedmodelling/klab-services/issues/144
- https://github.com/integratedmodelling/klab-services/pull/148
