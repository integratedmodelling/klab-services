# JAVA-GAP-0011 — Legacy frozen worldview fixture differs from current ODO/source-validation contracts

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **CONTRACT_DRIFT**.
- Service: semantics; component: RuntimeFixtureCompatibilitySourceTest / source semantic admission.
- Original roadmap: RC-P4.1, RC-P4.2, RC-R1, CAP-05, CAP-16.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: https://github.com/integratedmodelling/klab-services/issues/193.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Historical original ODO expectation496bc663 differs from current5c020dac after additive impliesPredicate change. Legacy243-file fixture also has DECLARATION_CLAUSE_SOURCE_INVALID and absent selected authority descriptors; not numeric execution failure.

## Independent simulator implementation and fixtures

Finite SIM vocabulary and stale-worldview refusal, not migration or original semantic acceptance. Separate source-validation fixture admits two namespace literals over existing Java-shaped Reasoner validate/document DTOs, pins exact source/version/hash and knowledge service/worldview/revision/session/content, preserves Error diagnostics and explicit stale revalidation without publication/executable admission. Namespace-only SDK refuses native ontology synchronization paths; finite diagnostics are not an ontology/compiler, native knowledge repair or replacement legacy fixture.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-SEMANTICS-001, SIM-SOURCE-VALIDATION-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_semantics.py::test_changed_worldview_refuses_stored_candidate_and_requires_fresh_search`
- `python-client/tests/unit/test_source_validation.py`
- `developer-tools/klab-service-simulator/tests/test_source_validation.py`
- `developer-tools/klab-service-simulator/tests/test_source_validation_fixture.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0011 --output simulated-JAVA-GAP-0011.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0011 --output real-JAVA-GAP-0011.json
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

Existing native test references: python-client/tests/functional/test_source_validation.py

## Custody, related trackers and remaining questions

Original reproducer/evidence: https://github.com/integratedmodelling/klab-services/issues/193

Explicitly version/validate a replacement if needed; preserve original oracle and source negatives. Do not turn the simulator vocabulary into an authoritative worldview.

- https://github.com/integratedmodelling/klab-services/issues/193
