# JAVA-GAP-0015 — Production issuer/license/roles and full deployed principal matrix unverified

Supporting runner/evidence only. The canonical contract, status and inline reproduction are in
[JAVA_GAPS_FOR_FERDINANDO.md](../JAVA_GAPS_FOR_FERDINANDO.md#java-gap-0015).
Generated from the synchronized index; simulator evidence is not native acceptance.

## Classification and affected contract

- Independent status triplet: NOT_TESTED; SIMULATOR_PASS; REAL_NOT_RUN.
- Java: **NOT_TESTED**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: authentication; component: Hub/Engine authorization and service credential provisioning.
- Original roadmap: RC-P1.1, RC-P1.3, RC-P1.4, CAP-01, CAP-02, RC-P3.5, CAP-18, RC-R2.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: python-client/docs/support-and-release.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED complete production issuer/license/role/expired-credential and two-deployed-Runtime matrix. Existing controlled native principal/privacy slices are narrower historical evidence.

## Independent simulator implementation and fixtures

Synthetic credentials/private owners/administrator roles only. ExistingSDK connect→singleJob→400cells with injected local SDK expiry/reacquisition; expired intake/principal drift/provider failure refuse before scientific HTTP and no replay. Owned mixed-Runtime outage/recovery preserves explicit selection, marks incomplete readiness unknown, sends no standby work; changed endpoint serviceID requires explicit reconnect. Separate advertised shared-read reference grants ephemeral Runtime READ to current synthetic principal; public sync/nativeasync SDK owner inspection/grant/revoke, read-only Observation/Job restoration, denied mutation/disposal, expired410 versus revoked403, mid-point revocation without partial data, lost reply owner lookup without replay and owned cleanup. Owner/grantee/TTL protocol and granular rights are explicitly provisional, not native ACL translation, production issuer/license or federation acceptance.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-DISCOVERY-001, SIM-SCOPE-SELECTION-001, SIM-PROJECT-LOCK-001, SIM-BOOTSTRAP-RECOVERY-001, SIM-NOTEBOOK-PRINCIPAL-RACE-001, SIM-SHARED-READ-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_scope_selection.py::test_same_backend_context_reads_use_current_credentials_and_foreign_binding_is_zero_http`
- `developer-tools/klab-service-simulator/tests/test_project_locks.py::test_administrator_role_remains_server_authoritative_and_unknown_project_false_is_not_a_diagnosis`
- `developer-tools/klab-service-simulator/tests/test_bootstrap_recovery.py`
- `python-client/tests/unit/test_connection.py::test_completed_failed_probe_preserves_identity_pin_without_readiness_or_failover`
- `python-client/tests/unit/test_connection.py::test_failed_unselected_runtime_never_inherits_selected_runtime_identity`
- `python-client/tests/unit/test_shared_access.py`
- `developer-tools/klab-service-simulator/tests/test_shared_access.py`
- `developer-tools/klab-service-simulator/tests/test_shared_access_fixture.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0015 --output simulated-JAVA-GAP-0015.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0015 --output real-JAVA-GAP-0015.json
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

Existing native test references: python-client/tests/functional/test_shared_context_read.py

## Custody, related trackers and remaining questions

Original reproducer/evidence: https://github.com/integratedmodelling/klab-services/issues/89

Operator evidence gap, not an allegation of missing Java authentication. Hosted fast-failure controls exposed a Python identity-pin loss bug; corrected failed probes retain only last verified same-selected-URL identity while readiness stays failed/unknown and actual drift still refuses. Local expiry clock/synthetic HTTP controls do not establish production issuer/license/deployed principal approval. Need authorized supported deployment, positive/negative principals and consented cleanup; production timeouts/retry/trust policies unchanged.

- https://github.com/integratedmodelling/klab-services/issues/89
- https://github.com/integratedmodelling/klab-services/issues/129
- python-client/docs/bootstrap-recovery-acceptance.md
- notes/python-roadmap-execution/evidence/full-roadmap-bootstrap-recovery-20261107.json
- notes/python-roadmap-execution/evidence/full-roadmap-bootstrap-pin-fix-20261107.json
