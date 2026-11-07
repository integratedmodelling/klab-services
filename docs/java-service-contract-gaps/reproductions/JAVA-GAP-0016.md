# JAVA-GAP-0016 — Remote committed revision and complete historical lineage contract unestablished

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: runtime; component: KnowledgeGraphQuery/LinkImpl / native committed selection receipts.
- Original roadmap: RC-P3.1, RC-P3.4, CAP-06, CAP-10.
- API: POST /runtime/api/v1/query.
- Request/schema: python-client/docs/graph-projection.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED complete remote lineage/revision snapshot and committed race contract. Native harness source explicitly inspects committed local input receipts; bounded real provenance reads already exist. Read-only source review confirms existing GET/api/v1/commit?id and ContextScope authorization; DigitalTwinImpl uses temporary commitCache.getIfPresent (source200entries/10minuteafteraccess, not deployed retention promise). Native commit reports exist but do not establish contiguous numeric revisions/durable/fullgraph reconciliation.

## Independent simulator implementation and fixtures

Partial plans/recorded effects with unknown durable consistency. Explicit provenance-dependencies fixture declares MEASURED/CONTRIBUTED_TO/planned RESOLVED edges in one owner root. Separate revision-convergence fixture captures completed synthetic observation IDs with expected-head CAS, bounded exact history, two independent clients and explicit refresh; ephemeral only, durable mode refuses. Neither establishes native committed science, full lineage, notification convergence, computed dependency or cross-service snapshot coherence.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-PROVENANCE-001, SIM-REFRESH-001, SIM-PROVENANCE-DEPENDENCIES-001, SIM-REVISION-CONVERGENCE-001, SIM-COMMIT-CACHE-001, SIM-GRAPH-CONVERGENCE-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_partial_refresh.py::test_failed_refresh_invalidates_all_pages_then_only_explicit_page_becomes_fresh`
- `python-client/tests/unit/test_async_provenance.py`
- `developer-tools/klab-service-simulator/tests/test_provenance_dependencies.py`
- `developer-tools/klab-service-simulator/tests/test_revision_convergence.py`
- `python-client/tests/unit/test_commits.py`
- `developer-tools/klab-service-simulator/tests/test_commits.py`
- `developer-tools/klab-service-simulator/tests/test_commit_fixture.py`
- `python-client/tests/unit/test_reference_commits.py`
- `developer-tools/klab-service-simulator/tests/test_graph_convergence.py`
- `developer-tools/klab-service-simulator/tests/test_graph_commit_fixture.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0016 --output simulated-JAVA-GAP-0016.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0016 --output real-JAVA-GAP-0016.json
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

Existing native test references: python-client/tests/functional/test_commits.py

## Custody, related trackers and remaining questions

Original reproducer/evidence: notes/python-roadmap-execution/evidence/ab-staging-vxii-source-reference-20261106.json

Separate from durable notification cursor gap0005: committed graph/lineage semantics need matching remote DTOs and real two-client proof, not inferred AMQP ordering. Synthetic two-client fixture links do not supply native commits or scientific provenance authority. Public native-shaped commit-cache read primitive preserves exact IDs/changegroups/linktriples/unknown missinggroups and currentroot identity; null is explicitunavailable. It is not complete graph/durable/contiguous revision authority and does not waive independently runnable full finite checkpoint/delta/gap-resync reference work.

- https://github.com/integratedmodelling/klab-services/issues/126
- python-client/docs/provenance-dependency-reference.md
- notes/python-roadmap-execution/evidence/full-roadmap-provenance-dependencies-20261107.json
- python-client/docs/commit-inspection.md
- notes/python-roadmap-execution/evidence/commit-inspection-source-authority-20261107.json
