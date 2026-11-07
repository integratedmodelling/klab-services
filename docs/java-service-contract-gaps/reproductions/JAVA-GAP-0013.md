# JAVA-GAP-0013 — Finite simulator scheduling, admission and semantic answers remain provisional substitutes

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: simulator-contract; component: Native Job/scope/Resources/Reasoner compatibility, not a missing Java scheduler/compiler.
- Original roadmap: RC-P1.1, RC-P3.3, RC-P4.1, RC-P4.3, CAP-07, CAP-13, CAP-14.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: developer-tools/klab-service-simulator/contract-cases.json.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED whole proposed finite scheduling/admission/semantic substitute. Narrow captured DTO/read properties retain separate JAVA_OBSERVED attribution.

## Independent simulator implementation and fixtures

Clockless controlled polls, exact-source literals, fixed SIM vocabulary, bounded private synthetic state; never local reasoning or Java execution

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-DISCOVERY-001, SIM-LIFECYCLE-001, SIM-FAULTS-001, SIM-SELECTION-001, SIM-INSPECTION-001, SIM-IMPORT-001, SIM-INPUT-BINDING-001, SIM-SEMANTICS-001, SIM-BEHAVIOR-DRAFT-001, SIM-NOTEBOOK-001, REPLAY-001, SIM-PROVENANCE-DEPENDENCIES-001, SIM-PROVIDER-AVRO-001, SIM-REVISION-CONVERGENCE-001, SIM-COPIED-COMPOSITION-001, SIM-OBSERVATION-SELECTIONS-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_vertical.py::test_synthetic400_discovery_resolver_values_provenance_and_owned_cleanup`
- `developer-tools/klab-service-simulator/tests/test_input_workflow.py::test_explicit_import_semantic_source_resolver_and400cell_input_receipt`
- `python-client/tests/unit/test_selections.py`
- `developer-tools/klab-service-simulator/tests/test_observation_selections.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0013 --output simulated-JAVA-GAP-0013.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0013 --output real-JAVA-GAP-0013.json
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

Existing native test references: python-client/tests/functional/test_observation_selections.py

## Custody, related trackers and remaining questions

Original reproducer/evidence: developer-tools/klab-service-simulator/README.md

One compatibility-assumption umbrella, not one alleged Java defect per route/test. State its narrower property when resolving; native timing need not match poll-driven simulation. Binary provider golden fixtures are independently generated finite matches, not a generic Avro service or native computation. Source Data Job controller lacks explicit media and falls through after streaming; deployed HTTP behavior NOT_TESTED, no observed defect or cleanup proof inferred.

- developer-tools/klab-service-simulator/contract-cases.json
- python-client/docs/provider-binary-contextualization.md
