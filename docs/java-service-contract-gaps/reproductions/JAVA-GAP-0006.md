# JAVA-GAP-0006 — Published remote block/window/revision-lease API missing from inspected controller

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **MISSING_API**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: runtime; component: Runtime READ_VALUE StorageScan.Point controller.
- Original roadmap: RC-P2.2, CAP-09.
- API: POST /runtime/api/v1/observation/value.
- Request/schema: python-client/docs/contracts.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Source audit: published scalar Point transport, not maintained plan/open/read-block/close/revision lease. Not a negative probe of every deployment.

## Independent simulator implementation and fixtures

Native point mapping remains separate. Explicit proposed klab.experimental.read-plans/1 adds root/Runtime/current-owner snapshot grants, bounded contiguous hashed shards, complete assembly and sync/nativeasync SDK. Process-local16grant cap/TTL/64KiBmanifest; release/expiry/restart loss explicit410, new epoch/no automatic replan. Failed durable request restores ephemeral rows/epoch. Content revision is REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT, not native committed science.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-GRID-001, SIM-TEMPORAL-EXPORT-001, SIM-READ-PLAN-001, SIM-READ-PLAN-ANALYSIS-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_state.py::test_unimplemented_read_mediation_time_or_fill_curve_is_not_synthetic_success`
- `python-client/tests/unit/test_read_plans.py`
- `developer-tools/klab-service-simulator/tests/test_read_plans.py`
- `python-client/tests/unit/test_read_plan_analysis.py`
- `developer-tools/klab-service-simulator/tests/test_read_plan_analysis.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0006 --output simulated-JAVA-GAP-0006.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0006 --output real-JAVA-GAP-0006.json
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

Original reproducer/evidence: https://github.com/integratedmodelling/klab-services/issues/123

Path names existing native point route; proposed /experimental/read-plans/v1/open and shard/release routes are NOT native Java authority. Maintained native block/descriptor/revision lease contract remains UNKNOWN_PENDING_REFERENCE. Same-fixture real block test cannot be implemented until published; simulator hashes do not satisfy native acceptance.

- https://github.com/integratedmodelling/klab-services/issues/123
- python-client/docs/read-plan-reference.md
- notes/python-roadmap-execution/evidence/full-roadmap-read-plans-20261107.json
