# JAVA-GAP-0007 — Composed-twin migration/failover contract absent from inspected connect stubs

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **MISSING_API**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: federation; component: ClientContextScope.connect / ServiceContextScope.connect.
- Original roadmap: CAP-03.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: docs/DISTRIBUTED_TWINS.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Source audit found connect(ContextScope) stubs returning null; no maintained remote composition/migration API established. Existing trusted attachment works.

## Independent simulator implementation and fixtures

Reject foreign binding. Separate explicit copied-composition scenario combines two independently client-read reference snapshots into a C-owned result with origin-qualified IDs and pinned source vector; C-only release preserves A/B. Coordinator does not fetch/verify upstream authority: source claims labelled caller asserted, not remote grants. No native live twin/federation, migration, failover or revocation propagation simulated.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-SCOPE-SELECTION-001, SIM-LIFECYCLE-001, SIM-BOOTSTRAP-RECOVERY-001, SIM-COPIED-COMPOSITION-001, SIM-PUBLIC-COPIED-COMPOSITION-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_scope_selection.py::test_same_backend_context_reads_use_current_credentials_and_foreign_binding_is_zero_http`
- `developer-tools/klab-service-simulator/tests/test_bootstrap_recovery.py`
- `developer-tools/klab-service-simulator/tests/test_copied_composition.py`
- `python-client/tests/unit/test_copied_composition.py`
- `developer-tools/klab-service-simulator/tests/test_public_copied_composition.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0007 --output simulated-JAVA-GAP-0007.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0007 --output real-JAVA-GAP-0007.json
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

Original reproducer/evidence: https://github.com/integratedmodelling/klab-services/issues/124

Copied-reference synthetic arithmetic is not native composed-twin acceptance. Full product operation/ownership/identity/security/revocation contract and real execution still required. Source hashes are not scientific commits or authority grants; native status unchanged.

- https://github.com/integratedmodelling/klab-services/issues/124
- python-client/docs/copied-composition-reference.md
