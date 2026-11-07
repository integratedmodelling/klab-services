# JAVA-GAP-0008 — Managed Runtime agent principal/incarnation and independent cleanup acceptance unverified

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **NOT_TESTED**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: agents; component: RuntimeService.compileAndRunAgent / AgentRegistry / native AMQP.
- Original roadmap: RC-P4.5, CAP-14.
- API: POST /api/v1/instantiate/agent.
- Request/schema: python-client/docs/agent-execution.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED complete managed Runtime execution/terminal cleanup. Native Java/Qpid wire interop exists and passed bounded CI; it is not managed registration/execution.

## Independent simulator implementation and fixtures

Explicit klab.experimental.managed-agents/1 finite synthetic lifecycle, NOT k.Actors parsing/compilation/registration/execution. Named idle/finish/fail fixtures, root-owner/Runtime/epoch binding, single-use start, stop acknowledgement distinct from terminal and later synthetic cleanup confirmation; CASrevision/purge/capacity/SDKrebind/restart loss/save rollback controls. No actual native/private-session cleanup proof.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-MANAGED-AGENT-001, SIM-MANAGED-BEHAVIOR-001

Exact executable controls:

- `python-client/tests/unit/test_agent_execution.py`
- `python-client/tests/unit/test_managed_agents.py`
- `developer-tools/klab-service-simulator/tests/test_managed_agents.py`
- `python-client/tests/unit/test_managed_behaviors.py`
- `developer-tools/klab-service-simulator/tests/test_managed_behavior_workflow.py`
- `developer-tools/klab-service-simulator/tests/test_managed_behavior_fixture.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0008 --output simulated-JAVA-GAP-0008.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0008 --output real-JAVA-GAP-0008.json
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

Original reproducer/evidence: python-client/docs/agent-execution.md

Java lifecycle already exists; do not label it absent. Proposed experimental routes are NOT native Runtime/AMQP authority; finite synthetic cleanup is not independent Java disposal evidence. Broker credentials are distinct from HTTP credentials; publication/stop acknowledgement is not terminal cleanup. SCRIPT/APP/TESTCASE private sessions/task restrictions/user singletons/sourcevalidation/custommessaging remain outside this reference.

- https://github.com/integratedmodelling/klab-services/issues/125
- python-client/docs/managed-agent-reference.md
- notes/python-roadmap-execution/evidence/full-roadmap-managed-agents-20261107.json
