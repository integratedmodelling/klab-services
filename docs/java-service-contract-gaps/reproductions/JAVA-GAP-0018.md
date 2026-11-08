# JAVA-GAP-0018 — Resolver client dataflow encoding has no transport implementation

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **MISSING_API**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: resolver; component: ResolverClient.encodeDataflow versus ResolverService.encodeDataflow.
- Original roadmap: RC-P0.
- API: UNKNOWN_PENDING_REFERENCE UNKNOWN_PENDING_REFERENCE.
- Request/schema: klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/Resolver.java.
- Tested Java build/environment: 9db975b21146093a7962d29e28345a762519befd source inspection only.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Audited source ResolverClient.encodeDataflow returns null; ResolverService implements encoding. No client method was executed here and no native HTTP endpoint is inferred.

## Independent simulator implementation and fixtures

NOT_IMPLEMENTED: no substitute encoder; static source regression guard checks client/service distinction only, never produces encoded scientific dataflows.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: 

Exact executable controls:

- `developer-tools/java-gap-governance/tests/test_encoder_source.py::test_pinned_client_stub_is_not_absence_of_service_encoding`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0018 --output simulated-JAVA-GAP-0018.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0018 --output real-JAVA-GAP-0018.json
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

Original reproducer/evidence: klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ResolverClient.java

Confirmed missing client-boundary implementation, not absent Java service encoding and not fresh native execution. Need supported encoding authority and bounded client construction/transport fixture before native reproducer can run. Keep unsupported SDK disposition rather than invent endpoint or scientific encoding.

- klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/Resolver.java
- klab.services.resolver/src/main/java/org/integratedmodelling/klab/services/resolver/ResolverService.java
