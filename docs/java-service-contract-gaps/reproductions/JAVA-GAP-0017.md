# JAVA-GAP-0017 — Computation-worker registration, lease and atomic output protocol pending native authority

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: worker; component: Native provider/worker role and transport not established; existing Resources contextualization is a distinct Avro contract.
- Original roadmap: RC-P5, CAP-17.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: python-worker/README.md.
- Tested Java build/environment: Repository source eda8697daf827d77066a5a2a7f90da4d56faf6d1 inspected; native worker transport UNKNOWN_PENDING_REFERENCE.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Maintainers pin initial computation-worker role, native endpoint/ABI, registration, principal/scope provisioning and supported deployment boundary
- Same declared nonuniform/missingness fixture passes on separately owned native backend/worker with exact geometry/unit/validity/provenance and no partial publication
- Reject foreign identities, unknown versions, invalid geometry/units/budgets; verify lease fencing, cancellation, duplicate/ambiguous delivery, restart and independently confirmed cleanup
- Provide operator-approved resource/isolation/security and package/provenance policy; trusted process isolation is not an adversarial sandbox or hard resident-memory guarantee

## Actual observed Java behavior

NOT_TESTED. ServicesAPI source exposes Resources contextualize endpoints, not proof of a maintained remote computation-worker registration/lease/result protocol. No current native worker endpoint or ABI is asserted missing globally.

## Independent simulator implementation and fixtures

Explicit opt-in klab.experimental.worker/1 HTTP proposal under /experimental/worker/v1: context-owner/worker-role registration, bounded nonuniform inputs, single claim lease, trusted isolated Python script, atomic validated output with hashes, cancellation/failure/release and no automatic redelivery. Opt-in durable synthetic checkpoint fences lost leases into RECOVERY_REQUIRED after process restart, retains published/cancelled results, and requires explicit owner recomputation consent; no automatic continuation or scientific reasoning/Java execution.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-WORKER-001, SIM-DURABLE-RESTORE-001, SIM-WORKER-OUTPUT-CUSTODY-001

Exact executable controls:

- `python-worker/tests/test_worker.py`
- `python-worker/tests/test_client_failures.py`
- `python-worker/tests/test_containment.py`
- `python-worker/tests/test_output_custody.py`
- `developer-tools/klab-service-simulator/tests/test_worker_vertical.py`
- `developer-tools/klab-service-simulator/tests/test_durable_recovery.py`
- `developer-tools/klab-service-simulator/tests/test_worker_output_custody.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0017 --output simulated-JAVA-GAP-0017.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0017 --output real-JAVA-GAP-0017.json
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

Original reproducer/evidence: python-worker/README.md

C now in scope under full-roadmap directive; issue127 remains open because a proposal/local process is not actual backend integration. Explicit experimental routes are not authoritative Java APIs. Worker polls retained task state and fails closed on cancellation/status loss; startup-gated Windows Job Object/POSIX process-group cleanup removes owned descendants after all outcomes. Explicit Limits.memory_bytes applies Windows aggregate Job committed-memory limit or POSIX inherited per-process RLIMIT_AS, not a cross-platform aggregate RSS guarantee; defaultNone claims no memory cap. Trusted scripts only: deliberate POSIX setsid escape, hostile-code sandboxing, total disk quota and maintained native transport/ABI/deployment acceptance remain unestablished. No server-supplied code/credentials/scientific model port or production SDK dependency on simulator.

- https://github.com/integratedmodelling/klab-services/issues/127
- python-worker/README.md
- docs/contributor-guides/KLAB_Python_Client_Planning_Guide.md
- klab.core.api/src/main/java/org/integratedmodelling/klab/api/ServicesAPI.java
