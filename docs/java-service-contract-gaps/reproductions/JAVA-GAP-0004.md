# JAVA-GAP-0004 — Durable Job retention and Runtime incarnation across restart unverified

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **NOT_TESTED**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: runtime; component: Runtime JobManager / scope and Job lifecycle.
- Original roadmap: RC-P1.2, RC-P3.3, CAP-07.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: python-client/docs/job-binding.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED full in-flight/history/restart chain. Historical completed-Job restoration and controlled single-Runtime scope restart are narrower evidence.

## Independent simulator implementation and fixtures

Finite retained IDs/expiry plus explicitly owned opt-in SQLite synthetic checkpoints across process restart; ordinary SDK saved context/Job/observation handles reattach and retrieve exact retained synthetic values. Tuple/corruption/single-writer/persistence failure checks, durable cleanup and zero automatic mutation replay. This is not native Java retention/incarnation evidence.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-LIFECYCLE-001, SIM-RESTORE-001, SIM-FAULTS-001, SIM-DURABLE-RESTORE-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_lifecycle.py::test_expired_job_empty_status_is_not_confirmed_cancellation`
- `developer-tools/klab-service-simulator/tests/test_lifecycle.py::test_fresh_sdk_process_restore_does_not_create_or_dispose_remote_state`
- `developer-tools/klab-service-simulator/tests/test_durable_recovery.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0004 --output simulated-JAVA-GAP-0004.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0004 --output real-JAVA-GAP-0004.json
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

Original reproducer/evidence: python-client/docs/job-binding.md

No new durable endpoint or retention promise inferred from version1 saved references or process-local binding. Publish authoritative lifetime/incarnation and owned restart fixture first.

- https://github.com/integratedmodelling/klab-services/issues/89
