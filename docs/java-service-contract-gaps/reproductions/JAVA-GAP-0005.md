# JAVA-GAP-0005 — Durable remote notification cursor and gap-aware resync contract unestablished

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: runtime; component: Runtime activity messaging / notification recovery.
- Original roadmap: RC-P3.1, CAP-10, CAP-11.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: python-client/docs/durable-notification-reference.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Fresh installed Python/simulator retained-reference tests; NOT fresh native Java execution or AMQP acceptance; last verified 2026-11-07.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED maintained durable cursor/order/retention protocol. Native transient messaging exists; historical run18 is not durable replay.

## Independent simulator implementation and fixtures

Explicit opt-in klab.experimental.notifications/1 proposed HTTP reference: bound root owner, immutable Job lifecycle batches and origin/epoch/sequence cursors,128event retention, contiguous replay/duplicates, RESYNC_REQUIRED on gaps/epoch loss, SQLite persistence and released-scope cleanup. Explicit activation covers subsequent Job hints only; caller checkpoints advance only after caller processing, no fabricated earlier history or full convergence. Separate native async client; existing transient AMQP behavior unchanged.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-REFRESH-001, SIM-DURABLE-NOTIFICATIONS-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_partial_refresh.py::test_failed_refresh_invalidates_all_pages_then_only_explicit_page_becomes_fresh`
- `python-client/tests/unit/test_notifications.py::test_owned_queue_manual_ack_timeout_duplicate_and_out_of_order_hints`
- `python-client/tests/unit/test_durable_notifications.py`
- `developer-tools/klab-service-simulator/tests/test_durable_notifications.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0005 --output simulated-JAVA-GAP-0005.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0005 --output real-JAVA-GAP-0005.json
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

Original reproducer/evidence: https://github.com/integratedmodelling/klab-services/issues/126

Native endpoint UNKNOWN_PENDING_REFERENCE; experimental GET /experimental/notifications/v1/events is an explicit proposal, not a discovered Java API. Need maintained native ordering/scope/incarnation/retention/expiry/overflow/revocation and same-fixture resync/convergence acceptance. Native AMQP message IDs are not these reference cursors; no production simulator dependency or real-error fallback.

- https://github.com/integratedmodelling/klab-services/issues/126
- python-client/docs/durable-notification-reference.md
- notes/python-roadmap-execution/evidence/full-roadmap-durable-notifications-20261107.json
