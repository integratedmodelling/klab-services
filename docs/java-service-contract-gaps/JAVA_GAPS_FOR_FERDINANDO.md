# Java service contracts: reproducible handoff for Ferdinando

## Purpose and scope

The Python A+B+C client and worker use a development-only service simulator to
exercise contracts while native compatibility remains incomplete. A simulator
implementation is **not evidence that Java lacks a capability**. This file is the
canonical human-facing account; `gaps.yaml` is its synchronized machine index.
Supporting captures and tests preserve evidence, not separate authoritative gap
inventories. No Java remediation, ontology migration, research replication or
production acceptance is authorized by this handoff.

Contents: [summary](#executive-summary), [triage](#priority-triage),
[quick start](#quick-start), [records](#gap-records),
[native backlog](#native-execution-backlog), [setup history](#resolved-setup-history),
[appendix and issue crosswalk](#appendix).

## Executive summary

Audited integration: **`9db975b21146093a7962d29e28345a762519befd`**, merged
[PR #319](https://github.com/integratedmodelling/klab-services/pull/319), on
`feature/python-experiment-runner`. This is not `develop`; their merge base is
`1e75df4e94d72007fe1b96c3f898f3f0fbefab60`. Evidence discovery began on
2026-11-08 UTC. No Java execution occurred during this consolidation.

The retained register has **18 distinct stable records**: **2 confirmed historical
defects**, **4 source-scoped missing API contracts**, **7 provisional contracts**,
and **5 untested native integrations**. No record is a verified native fix.
Resolved setup incidents below are **not** missing Java features and are excluded
from these counts. These are classifications, not 18 newly reproduced failures.
All current native execution statuses are `REAL_NOT_RUN`. Fresh simulator results
are **SIMULATOR_PASS for 0001–0017**; 0018 is **SIMULATOR_NOT_IMPLEMENTED**, with a
passing source-only guard rather than an encoder substitute. On 2026-11-08 UTC,
the installed-package suite passed 416 simulator/source guards, 1,319 SDK tests
and 116 worker tests with no skips/failures/errors. Earlier environment failures
remain in the receipt index; no native result was inferred from these tests.

|Native status|Records|
|---|---|
|CONFIRMED_DEFECT|2|
|CONFIRMED_MISSING_API|4|
|CONTRACT_PROVISIONAL|7|
|NOT_TESTED|5|
|NATIVE_WORKS_VERIFIED|0|
|RESOLVED_SETUP|0|
|SUPERSEDED|0|

What definitely failed in the tested environments:

* **0001:** build `202610301844` selected the USGS elevation model but passed a null
  numeric scanner to the geospatial encoder; Job 9 returned empty/Error, not a
  400-cell field. This is not evidence that Ferdinando's current build fails.
* **0010:** preserved native GeoTIFF bytes retain correct constant values and
  spatial layout but lose the measurement-unit metadata. Reopening those bytes
  is not a fresh Runtime execution or confirmation of an official fix.

Missing contracts are narrowly scoped: owned RESOURCE deletion in the inspected
branch (0002), remote bounded block/read-plan leases (0006), and composed-twin
migration/failover (0007), and client-boundary dataflow encoding (0018). Existing scalar reads, local storage views, trusted
attachments and in-process functions must not be described as absent.

The remaining records concern deployed compatibility, authorization, lifetimes,
scientific mappings and provisional reference protocols. In particular Java
agent compilation/lifecycle already exists (0008); a maintained source currentness
fix already merged (0012); native commit reads already exist (0016). What remains
unverified is the complete matching deployed workflow, not those capabilities'
existence. Native staging test source covers quality resolution, spatial storage
views, unit mediation and monthly transitions; its source was inspected, not run.

**Requests for Ferdinando:** identify the supported service/component tuple and
staging invocation; confirm the source-scoped API findings; choose published
contracts for read plans, durable events, revisions and workers; provide approved
fixture/principal/cleanup manifests; and retain native request/response plus
independent scientific oracles before closing any native obligation. Python
implementation delivery through #319 remains separate from this acceptance.

## Priority triage

`P(id)` below means the executable static command in [quick start](#quick-start)
with `--gap id`. It runs **NOW**, without services. It does not reproduce a native
defect. `S(id)` is the simulator command there. Every complete native workflow
requires the special environment described in its record.

|ID|Priority / simple title|Service / native evidence|Simulator scope|Consequence / reproduce|Issue / maintainer question|
|---|---|---|---|---|---|
|[0001](#java-gap-0001)|P0 / Null numerical scanner|Resources / historical failure|Recorded data delivery|No Runtime field; P/S(0001), native blocked|No dedicated issue; which published build fixes scanner transport?|
|[0010](#java-gap-0010)|P1 / Lost TIFF units|Geospatial / retained native bytes|Export guards|Unit attribution lost; P/S(0010)|[geo #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10); which official artifact preserves units?|
|[0002](#java-gap-0002)|P1 / Owned resource cleanup|Resources / source branch|Private import custody|Cannot promise cleanup; P/S(0002)|No dedicated issue; supported delete policy?|
|[0003](#java-gap-0003)|P1 / Candidate discovery version|Resources / installed/source mismatch|Advertised protocol 1|Inspection is not selection; P/S(0003)|No dedicated issue; deployed protocol/build?|
|[0004](#java-gap-0004)|P1 / Durable Job lifetime|Runtime / untested full chain|Restart/restore controls|Stale handles; P/S(0004)|[#89](https://github.com/integratedmodelling/klab-services/issues/89); retention/incarnation policy?|
|[0005](#java-gap-0005)|P1 / Durable event cursors|Runtime / provisional|Gap-aware refresh|Missed updates; P/S(0005)|[#126](https://github.com/integratedmodelling/klab-services/issues/126); cursor authority?|
|[0006](#java-gap-0006)|P1 / Remote blocks and leases|Runtime / source-scoped missing API|Finite read plans|Bounded remote arrays; P/S(0006)|[#123](https://github.com/integratedmodelling/klab-services/issues/123); published lease contract?|
|[0008](#java-gap-0008)|P1 / Managed agent acceptance|Runtime / untested deployed ownership|Lifecycle reference|Stop acknowledgement is not disposal; P/S(0008)|[#125](https://github.com/integratedmodelling/klab-services/issues/125); native fixture?|
|[0009](#java-gap-0009)|P1 / Conditional source updates|Resources / provisional|CAS/history reference|Lost updates; P/S(0009)|[#129](https://github.com/integratedmodelling/klab-services/issues/129); admitted revision contract?|
|[0011](#java-gap-0011)|P1 / Frozen worldview compatibility|Reasoner/Resources / fixture drift|Source validation|Cannot silently change oracle; P/S(0011)|[#193](https://github.com/integratedmodelling/klab-services/issues/193); versioned fixture decision?|
|[0012](#java-gap-0012)|P1 / Paired-runtime currentness|Runtime / merged source fix|Partial refresh|Deployed acceptance missing; P/S(0012)|[#144](https://github.com/integratedmodelling/klab-services/issues/144); paired-build receipt?|
|[0014](#java-gap-0014)|P1 / Scientific mappings|Runtime / provisional remote tuple|Arrays/raster/labels|Wrong axes/units/masks; P/S(0014)|[#123](https://github.com/integratedmodelling/klab-services/issues/123); matching native descriptors?|
|[0015](#java-gap-0015)|P1 / Production principal matrix|All / untested deployment|Isolation/refusal controls|Wrong tenant/identity; P/S(0015)|[#89](https://github.com/integratedmodelling/klab-services/issues/89), [#129](https://github.com/integratedmodelling/klab-services/issues/129); authorized roles?|
|[0016](#java-gap-0016)|P1 / Revisions and lineage|Runtime / provisional complete graph|Commit/convergence reference|Local hashes not authority; P/S(0016)|[#126](https://github.com/integratedmodelling/klab-services/issues/126); retention/revision authority?|
|[0017](#java-gap-0017)|P1 / Computation worker backend|Resources/Runtime / provisional|Lease/output/process custody|No native backend acceptance; P/S(0017)|[#127](https://github.com/integratedmodelling/klab-services/issues/127); supported role/ABI?|
|[0007](#java-gap-0007)|P2 / Composed twin migration|Runtime / connect stubs|Copied composition only|No failover guarantee; P/S(0007)|[#124](https://github.com/integratedmodelling/klab-services/issues/124); supported distributed operation?|
|[0013](#java-gap-0013)|P2 / Finite semantic substitutes|All / provisional|Synthetic admission/selection|No native science proof; P/S(0013)|No dedicated issue; maintained fixture/oracle?|
|[0018](#java-gap-0018)|P2 / Client encoder stub|Resolver / source-only missing client implementation|Not implemented; source guard only|Cannot encode through this client; P(0018)|No dedicated issue; supported transport or explicit unsupported contract?|

## Quick start

Use Python 3.11+, a repository checkout, and an isolated virtual environment.
Install development dependencies from the repository root:

```sh
python -m pip install 'jsonschema>=4.23,<5' './python-client[dev,analysis,tabular,notebook,local,events]' ./python-worker './developer-tools/klab-service-simulator[test]'
python developer-tools/java-gap-governance/govern.py validate
python developer-tools/java-gap-governance/govern.py generate --check
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0001 --output simulated-0001.json
python developer-tools/java-gap-governance/native_probe.py --gap JAVA-GAP-0001 --output preflight-0001.json
```

The last command exits **3**, even if static custody passes: native execution is
`REAL_NOT_RUN`. Exit 0 from the simulator means its deterministic tests passed,
not Java. Exit 1 from a failing test suite is not acceptance. The native probe
uses exclusive output creation: choose a new receipt filename each time.

To run a **native-only read-only capability scenario**, create a private JSON file
outside the repository. Replace the example base URL, identity and build with the
operator's verified service tuple; do not guess a service's base path:

```json
{
  "approved_read_only": true,
  "base_url": "https://native.example.invalid/resources",
  "expected_service_id": "operator-supplied-service-id",
  "native_build": "operator-supplied-build-sha",
  "token_env": "KLAB_NATIVE_PROBE_TOKEN"
}
```

Supply that environment variable privately, then run:

```sh
python developer-tools/java-gap-governance/native_probe.py --gap JAVA-GAP-0003 --connect /path/to/private-native.json --output native-capability-0003.json
```

On Windows use the actual private config path instead of `/path/to/...`.
The probe makes exactly one GET to `base_url/public/capabilities`, with a ten-second
request timeout and a 64 KiB body limit; redirects and proxy inheritance are
disabled. It requires HTTPS except on loopback. Credentials are never put in
URLs or receipts. An identity-matched response still exits **3**: the selected
  gap's behavioral test was **not** executed. Exit **4** means a configuration,
connection, response or identity gate blocked capture, with no simulator fallback.
It cannot distinguish an unavailable API from missing permission by guessing.

Prepared native workflow tests live in `python-client/tests/functional/`.
They require an explicitly approved `KLAB_FIXTURE_STATE_DIR` containing
`scientist.json` and each test's exact additional manifests. Merely creating an
empty directory or passing collection does not make them executable acceptance.
Do not run mutation/restart tests against an unrelated retained installation.

### Shared reproduction conditions (apply to every record)

* **Environment:** no new native tuple is claimed. Historical build/component
  details below qualify only that observation. Obtain exact service identities,
  component hashes, worldview/ontology revision, resources, scopes, principal
  rights, fixture hashes and cleanup authorization before behavioral native runs.
* **Auth and payload:** use the maintained SDK's selected-service and scope
  handling; do not invent scope headers or replace broker credentials with HTTP
  credentials. The capability probe sends no scientific payload and grants no
  mutation rights. Unknown gap endpoints/DTOs remain unknown or proposed.
  Existing SDK HTTP transport sends the configured credential in `Authorization`
  and the exact scope token in `klab-scope` when scoped. Do not infer that every
  deployment uses the same credential scheme. The separate probe accepts an
  explicitly supplied bearer token only. Native HTTP 401/403 remain authentication/
  authorization failures; a 404 is not automatically proof of missing capability.
* **Static reproduction:** `native_probe.py --gap JAVA-GAP-NNNN --output UNIQUE.json`
  validates local test-file custody. This is executable **preflight**, not an
  executable reproduction of the complete native contract. All 18 complete native
  reproductions remain `BLOCKED_WITH_REASON` as specified below.
* **Simulator reproduction:** `govern.py contracts --mode simulated --gap
  JAVA-GAP-NNNN --output UNIQUE.json` runs that record's exact indexed tests in
  isolated pytest groups. Expected: no skips/failures/errors and `SIMULATED_PASS`.
  Fixtures are synthetic unless explicitly identified as recorded native bytes.
  Mode is simulated, not a live proxy. Full stdout/stderr is `UNIQUE.pytest.log`.
* **Actual result:** native `REAL_NOT_RUN` in this consolidation; historical
  observations are separately labeled. Fresh simulator/guard results are pinned in
  the [18-record receipt](evidence/consolidation-20261108/consolidation-ee8bf4e4d-all18.json)
  and [full stdout/stderr](evidence/consolidation-20261108/consolidation-ee8bf4e4d-all18.pytest.log).
  Execution: 2026-11-08T03:17:22Z–03:22:02Z, commit
  `ee8bf4e4dde8b893de36a2601b2b86aa88e264c6`, Python 3.12.7 / Windows,
  separately installed SDK/worker/simulator in a clean environment. This common
  receipt contains each record's exact selected tests and assertion scope;
  0018 records NOT_IMPLEMENTED rather than simulator parity.
* **Cleanup:** static probes create only the chosen receipt. Simulator fixtures
  own their loopback state; remove only those owned receipts/state after retaining
  evidence. Native cleanup must be agreed before any mutation; never terminate
  unrelated services, change ontologies or delete retained resources.
* **Closure:** pin the maintained tuple and run positive/negative authorization,
  state/error and applicable independent scientific checks without fallback.
  Retain sanitized full receipts, timestamps, request/response and cleanup proof.
  A capability response or local manifest SHA alone cannot close a record.

## Gap records

### JAVA-GAP-0001

**Title:** Resources remote numeric scanner is null on preserved USGS workflow

**Scope:** resources — Resources / geospatial scanner transport

**Native status:** CONFIRMED_DEFECT

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P0 / unknown. **Impact:** the real elevation workflow cannot
return a numerical field after successful model selection. Roadmap RC-P2.1,
RC-P2.3, RC-P2.5, CAP-09; alias `JAVA-SCANNER-001`; no dedicated product issue.

**Observed contract:** historical Runtime submission selected model
`scientific.examples.usgs_virginia.elevation-quantifier` with resource
`resources.eric.lumsden:staging:usgs:virginia20211229`. The registry's
`POST /runtime/api/v1/submit` identifies the surrounding submission, not a
standalone scanner endpoint. Expected: 400 native cells with correct orientation,
mask and metres. Actual HISTORICAL: Job 9 empty/Error;
`NullPointerException: Cannot invoke Storage$DoubleScanner.add(double) because filler is null`
at `RasterEncoder.java:183`, through `RasterAdapter.encode:102`. Installed bytecode
passes `aconst_null` at offset 180 and invokes the adapter at 207.

**Environment/fixtures:** installed Modeler build `202610301844`.
Historical live attempt window: **2026-11-06 02:58–03:11 UTC**, as recorded in the
remediation report; the consolidation did not rerun it. Original DEM SHA256
`026ec5429ddb1197b46247dd97832e2abe246be112e7133f4fce5029fea50de5`;
20×20 EPSG:4269 crop `2c9fcd0748b9ef3d8c036a59af5c1419a826ba14262a3a80d089b1d59023fb26`;
independent oracle `22ed905702f9e8bed9dc6a98f07ff7762877459c55cb50e581ec1a6fa7519b76`.
The isolated Java reader matched 400 source cells; Runtime produced none. Source
NAVD88 attribution is not proof of an embedded output vertical GeoKey.

**Run now:** the shared commands with `--gap JAVA-GAP-0001`. Named simulator test:
`developer-tools/klab-service-simulator/tests/test_vertical.py::test_native_async400_and_authentic_recorded_delivery_not_java`.
Scenarios SIM-USGS-001, SIM-GRID-001, REPLAY-001, SIM-PROVIDER-AVRO-001 exercise
recorded/synthetic transport, not scanner execution. Evidence:
[historical remediation report](../../notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md).
**Native blocker:** the report's `NullScannerReproducer.java`, crop and full raw
receipt are not a maintained runnable repository fixture. Recover and review them,
pin the corrected tuple and authorize the original resource before running.
**Request / close:** attest scanner transport correction, then compare all 400
Runtime cells, masks, axes and unit provenance against the unchanged oracle.

### JAVA-GAP-0002

**Title:** Supported owned RESOURCE cleanup contract absent in inspected delete branch

**Scope:** resources — Resources import and owned cleanup

**Native status:** CONFIRMED_MISSING_API

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** an upload workflow cannot promise
owned-resource cleanup. RC-P4.3, RC-P4.4, CAP-13, CAP-14; alias
`JAVA-RESOURCE-CLEANUP-001`; no dedicated issue.

**Contract:** import uses `POST /resources/import/{schema}/{urn}`; this is not a
delete route. Inspected `ResourcesProvider.delete` lacks a supported RESOURCE
branch. This is a source-scoped finding, not an executed denial on every build.
Source location: [ResourcesProvider.delete](../../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java),
lines 1434–1474, including the explicit TODO RESOURCE at line 1473.
Request/response and asynchronous import Job semantics:
[resource-import contract](../../python-client/docs/resource-imports.md).
Cleanup authorization, delete DTO/errors and retention policy remain unestablished.
Expected: owned import receipt and agreed deletion/retention outcome; actual native
delete NOT_RUN (no destructive probe).

**Run now:** shared commands with `--gap JAVA-GAP-0002`; simulator selector
`developer-tools/klab-service-simulator/tests/test_resource_imports.py::test_owned_upload_job_private_catalog_receipt_and_cleanup`;
SIM-IMPORT-001, SIM-INPUT-BINDING-001. Synthetic private catalog cleanup does not
establish a native delete API. **Native blocker:** no approved disposable resource
and maintained cleanup adapter. **Request / close:** publish supported deletion or
explicit retained-resource policy and verify ownership/foreign denial and cleanup
using a hashed disposable fixture, without deleting unrelated resources.

### JAVA-GAP-0003

**Title:** Installed Resources does not advertise source model-candidate protocol

**Scope:** resources — Resources model-candidate inspection

**Native status:** NOT_TESTED

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** inspect candidate model sources
without treating an advertisement as authoritative selection. RC-P4.1, CAP-13;
alias `JAVA-CANDIDATES-001`; no dedicated issue.

**Contract:** source-defined `POST /api/v1/models/candidates` is gated by exact
`modelCandidateProtocol` version 1. Historical build `202610301844` did not advertise
it; newer source defines it. No claim that the maintained Java API is absent.
Schemas/payloads: [model candidate sources](../../python-client/docs/model-candidate-sources.md).
Expected: bounded source snapshots bound to Resources identity, visibility and
revision; explicit unsupported-protocol refusal, no legacy fallback. Actual fresh
endpoint NOT_RUN. A snapshot is not semantic admission or selected-model authority.

**Payload capsule:** source request is `{"version":1,"namespaces":[],"projects":[],"scenarios":[]}`
with explicit bounded selectors where needed. Response is ResourceSet MODEL
members and matching NAMESPACE descriptors, with source under
`klab.model.candidate.source.v1` and UTF-8 SHA256 under
`klab.model.candidate.sourceHash.v1`. Supplier credentials only: no scope,
originating Runtime or transaction delegation. Protocol must be exact integer 1;
404/500/denial/transport failure are errors, not an empty successful catalog.

**Run now:** shared commands with `--gap JAVA-GAP-0003`; native-only capability
capture is shown in quick start. Simulator: `python-client/tests/unit/test_model_candidates.py`
and `developer-tools/klab-service-simulator/tests/test_model_candidates.py`;
SIM-INSPECTION-001, SIM-SELECTION-001, SIM-CANDIDATE-SOURCES-001. **Native blocker:**
matching deployed protocol/identity/visibility fixture and response oracles missing.
**Request / close:** identify supported deployed build and verify same/foreign
principal, paging, DTO errors and exact advertisement before candidate requests.

### JAVA-GAP-0004

**Title:** Durable Job retention and Runtime incarnation across restart unverified

**Scope:** runtime — Runtime Job lifecycle and restoration

**Native status:** NOT_TESTED

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** resume saved Jobs without replaying
work or acquiring cleanup ownership. RC-P1.2, RC-P3.3, CAP-07; alias
supplemental native dependency RC-P3.5;
`JAVA-RETENTION-001`; [#89](https://github.com/integratedmodelling/klab-services/issues/89).
**Contract:** no new durable endpoint is established. Saved references pin selected
Runtime and scope; expiry/unknown status must not be called confirmed cancellation.
DTO/binding: [Job binding](../../python-client/docs/job-binding.md). Historical
completed-Job restoration and a controlled Runtime scope restart are narrower than
the in-flight/history/restart chain. Expected: documented retention/incarnation,
terminal state and ownership; actual complete native chain NOT_RUN.

**State capsule:** bind positive Job ID, executing service, exact scope token,
home/executing Runtime URLs and known identities. Status/result/cancel must never
silently rebind. Empty status means unavailable/expired, not successful cancellation.
An accepted boolean cancellation reply is not terminal interruption. Drift during
cancel yields unknown mutation outcome, not retry or rollback. Wait timeout/task
cancellation leaves the original Job; no automatic resubmission.

**Run now:** shared commands with `--gap JAVA-GAP-0004`;
`developer-tools/klab-service-simulator/tests/test_durable_recovery.py`;
SIM-LIFECYCLE-001, SIM-RESTORE-001, SIM-FAULTS-001, SIM-DURABLE-RESTORE-001.
Synthetic checkpoint restoration proves reference behavior, not deployed retention.
**Native blocker:** no owned restart fixture or authoritative lifetime policy.
**Request / close:** publish retention/incarnation semantics; execute a two-process
restore and owned Runtime restart with in-flight and expired Jobs, no resubmission,
no foreign disposal, and complete cleanup proof.

### JAVA-GAP-0005

**Title:** Durable remote notification cursor and gap-aware resync contract unestablished

**Scope:** runtime — Runtime event retention and client refresh

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** refresh cached observations after
disconnect without silently losing updates. RC-P3.1, CAP-10, CAP-11;
[#126](https://github.com/integratedmodelling/klab-services/issues/126).
**Contract:** native transient messaging exists. Proposed
`GET /experimental/notifications/v1/events` is **not** a native Java endpoint.
Reference payload/cursors/errors: [durable notification reference](../../python-client/docs/durable-notification-reference.md).
Expected: scope/incarnation-bound ordering, retention, expiry, overflow, revocation
and explicit gap resync. AMQP message IDs are not this cursor protocol. Historical
transient run 18 is not durable replay; complete native result NOT_RUN.

**Proposed payload capsule:** query `protocol=klab.experimental.notifications/1`,
`limit=1..64`, optional `epoch` plus `after`; response `protocol, epoch, first,
next, state, events, coverage`. Events carry exact contiguous `sequence`, bounded
`job_id`, `service` and JOB_WAITING/STARTED/FINISHED/ABORTED/INTERRUPTED kind. Cursor
binds Runtime URL/ID/root/epoch/sequence, never credentials. First read creates a
tail, not history. Reference coverage is SINCE_EXPLICIT_REFERENCE_ACTIVATION_ONLY;
128-event overflow, epoch loss or future cursor returns RESYNC_REQUIRED with no
partial history. Caller checkpoints only after processing; 64 KiB strict JSON cap.

**Run now:** shared commands with `--gap JAVA-GAP-0005`;
`developer-tools/klab-service-simulator/tests/test_durable_notifications.py`;
SIM-REFRESH-001, SIM-DURABLE-NOTIFICATIONS-001. Deterministic duplicate/order/overflow
fixtures prove client refusal/resync only. **Native blocker:** maintained durable
protocol, retention fixture and adapter not published. **Request / close:** agree
cursor authority and run same-fixture reconnect/gap/revocation convergence against
native state without inferred delivery guarantees or automatic replay.

### JAVA-GAP-0006

**Title:** Published remote block/window/revision-lease API missing from inspected controller

**Scope:** runtime — Runtime scientific bounded remote reads

**Native status:** CONFIRMED_MISSING_API

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** bounded array/window reads rather
than unbounded point loops. RC-P2.2, CAP-09;
[#123](https://github.com/integratedmodelling/klab-services/issues/123).
**Contract:** inspected native `POST /runtime/api/v1/observation/value` transports
scalar Point values, not plan/open/read-block/close leases. Proposed
`/experimental/read-plans/v1/open` and shard/release routes are not Java authority.
Request/shape basis: [contracts](../../python-client/docs/contracts.md). Expected:
bounded windows, explicit geometry/masks/revision and lease release; unsupported
fill curves/time mappings refuse. Actual deployed block API NOT_RUN, source-scoped
absence only; local native storage views are a different capability.
Source boundary: [Runtime controller](../../klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java)
and [ServicesAPI](../../klab.core.api/src/main/java/org/integratedmodelling/klab/api/ServicesAPI.java).

**Run now:** shared commands with `--gap JAVA-GAP-0006`;
`developer-tools/klab-service-simulator/tests/test_read_plans.py` and
`developer-tools/klab-service-simulator/tests/test_reference_windows.py`;
SIM-GRID-001, SIM-TEMPORAL-EXPORT-001, SIM-READ-PLAN-001,
SIM-READ-PLAN-ANALYSIS-001, SIM-SELECTED-READ-PLAN-001. Finite synthetic/reference
shards are not native committed revisions. **Native blocker:** no maintained
remote block DTO, lease authority or reviewed adapter. **Request / close:** publish
contract, then verify exact hashed native cells, budget, expired/foreign leases,
mask/axis semantics and release on success/error/cancellation.

### JAVA-GAP-0007

**Title:** Composed-twin migration/failover contract absent from inspected connect stubs

**Scope:** federation — cross-Runtime composition and migration

**Native status:** CONFIRMED_MISSING_API

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P2 / unknown. **Impact:** attaching an existing context must
not be represented as migrating a distributed twin. CAP-03;
[#124](https://github.com/integratedmodelling/klab-services/issues/124).
**Contract:** inspected `connect(ContextScope)` stubs return null; no maintained
remote composition/migration endpoint is asserted. Existing trusted attachment
works. Architecture: [distributed twins](../DISTRIBUTED_TWINS.md). Expected contract
must specify ownership, identity, delegation/revocation and failure recovery;
actual migration NOT_RUN. Copying references/hashes grants no native authority.
Null source stubs: [ClientContextScope](../../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/scope/ClientContextScope.java)
at lines 233–235 and [ServiceContextScope](../../klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ServiceContextScope.java)
at lines 850–853.

**Run now:** shared commands with `--gap JAVA-GAP-0007`;
`developer-tools/klab-service-simulator/tests/test_public_copied_composition.py`;
SIM-SCOPE-SELECTION-001, SIM-LIFECYCLE-001, SIM-BOOTSTRAP-RECOVERY-001,
SIM-COPIED-COMPOSITION-001, SIM-PUBLIC-COPIED-COMPOSITION-001. Synthetic copied
arithmetic is not composed-twin acceptance. **Native blocker:** product operation,
security/identity contract, two-backend fixture and adapter absent.
**Request / close:** define the supported distributed operation and demonstrate
failure/recovery/revocation with independently verified native outputs and cleanup.

### JAVA-GAP-0008

**Title:** Managed Runtime agent principal/incarnation and independent cleanup acceptance unverified

**Scope:** agents — Resources parsing, Runtime registration and AMQP control

**Native status:** NOT_TESTED

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** Python-managed agent execution must
retain owner/incarnation and prove terminal disposal. RC-P4.5, CAP-14;
[#125](https://github.com/integratedmodelling/klab-services/issues/125).
**Contract:** native `POST /api/v1/instantiate/agent` and Java compilation/lifecycle
already exist. Payload/DTOs: [agent execution](../../python-client/docs/agent-execution.md).
Bounded native Java/Qpid wire interoperability is historical evidence, not managed
registration/execution acceptance. HTTP and broker credentials are distinct;
publish/stop acknowledgement is not terminal cleanup. Proposed experimental agent
routes are not Runtime/AMQP authority. Actual complete managed native run NOT_RUN.

**Run now:** shared commands with `--gap JAVA-GAP-0008`;
`developer-tools/klab-service-simulator/tests/test_managed_behavior_workflow.py`;
SIM-MANAGED-AGENT-001, SIM-MANAGED-BEHAVIOR-001. Finite synthetic terminal state
does not independently observe Java disposal. **Native blocker:** approved source,
principal, broker, Runtime incarnation and independent disposal observer missing.
**Request / close:** run maintained native owner/foreign/revoked lifecycle and
cancel/failure cleanup; separately qualify SCRIPT/APP/TESTCASE private sessions,
task restrictions, USER singletons and source validation instead of extrapolating.

**Native registration/control capsule:** parse exact BEHAVIOR source through
Resources, check identities, instantiate once with `doNotStart` and both doNotBind
flags. Explicit principal key and Runtime incarnation are operator assertions, not
derived from uptime. Lost/malformed post-admission response is
SubmissionOutcomeUnknown, never automatic replay. AMQP uses native Java Message
format and maintained agent exchange; local close detaches only. Confirmed broker
publication is not execution. Pinned Qpid 9.2.0 wire fixture selects explicit
unconfirmed publication because required Pika confirm/nack advertisement is absent;
this must not silently downgrade another deployment's required policy.

### JAVA-GAP-0009

**Title:** Generic accepted-source conditional update/history/publication contract unestablished

**Scope:** authoring — Resources source admission, version and publication

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** concurrent authoring must not lose
accepted source updates. RC-P4.2, RC-P4.4, CAP-14;
[#129](https://github.com/integratedmodelling/klab-services/issues/129).
**Contract:** native project locks are principal-shared, not revision-fenced.
Specialized rejected-ontology selectors include `sourceVersion`; generic accepted
documents do not thereby acquire the same admitted selector contract. Proposed
private drafts/CAS HTTP is not native publication authority. Reference payloads:
[revision drafts](../../python-client/docs/revision-draft-reference.md). Expected:
explicit version/conflict, validation, permissions, history and publication; no
live project mutation or complete native acceptance was run.
Inspected lock/admission source: [WorkspaceManager](../../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/storage/WorkspaceManager.java)
and [Resources controller](../../klab.services.resources.server/src/main/java/org/integratedmodelling/resources/server/controllers/ResourcesProviderController.java).

**Proposed payload/state capsule:** GET/PUT
`/experimental/drafts/v1/{opaque_name}` under bound root context, exact protocol
`klab.experimental.drafts/1`, UTF-8 source ≤16 KiB and checked source SHA256,
expected revision 0 for create or exact read revision for update. Response remains
UNVALIDATED_PRIVATE_DRAFT, never accepted source. CAS conflict is HTTP 409;
evicted history 410; failed durable persistence 507; write disconnect/5xx means
unknown outcome and requires explicit read/reconciliation. Reference retains at
most eight names/context and eight versions/name; no publication operation.

**Run now:** shared commands with `--gap JAVA-GAP-0009`;
`developer-tools/klab-service-simulator/tests/test_notebook_principal_races.py` and
`developer-tools/klab-service-simulator/tests/test_accepted_documents.py`;
SIM-PROJECT-LOCK-001, SIM-BEHAVIOR-DRAFT-001, SIM-INPUT-BINDING-001,
SIM-REVISION-DRAFTS-001, SIM-NOTEBOOK-PRINCIPAL-RACE-001, SIM-CONDITIONAL-AUTHORING-001.
Same-owner CAS and foreign denial prove only the reference protocol.
**Native blocker:** agreed generic accepted-source revision/publication contract
and disposable project fixture. **Request / close:** verify two-client stale
revision conflict, authoritative history, admission and permission/cleanup outcomes.

### JAVA-GAP-0010

**Title:** Published native continuous GeoTIFF loses measurement-unit metadata

**Scope:** geospatial — Runtime export / geospatial component

**Native status:** CONFIRMED_DEFECT

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** scientific export consumers lose
measurement-unit attribution. RC-P2.1, RC-P2.5, CAP-09, CAP-15;
[geospatial #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10).
**Contract:** `GET /export/OBSERVATION/{urn}`, with service prefix determined by
selected deployment. Expected native GeoTIFF band unit `m`; HISTORICAL actual
`dataset.units == (None,)`, band tags `{}`. Native values 123.25, 5×4 float32,
EPSG:3857 and transform `(100,0,200000,0,-100,6000400)` are correct. Do not stamp
units into original bytes to make the test pass.

**Environment/fixture:** services `a0a013a188fb752907f2d143a61b86f4a9cb0a0b`,
component `1.0-20261025.094856-223`, archive SHA256
`c5192eec7fe1d47a5b4a4750c334ea3ff88520afe8d096e04c8640c3cfb289c6`.
Retained TIFF SHA256 `940b21aee3d6eaa7e70c7baef52d29d48d577398b0352ccb0e43347ca37ba2ce`;
[encoded artifact manifest](../../developer-tools/java-gap-governance/fixtures/native-geotiff-missing-unit.json).
The retained artifact manifest does not contain the exact original Runtime
execution timestamp. That is an evidence hole; issue update time and component
build time must not be substituted for execution time.

**Run now:** shared commands with `--gap JAVA-GAP-0010`, or
`python -m pytest -q developer-tools/java-gap-governance/tests/test_native_export_capture.py`.
Expected: preserved defect assertions pass while authentic bytes remain unchanged;
this is **historical artifact verification**, not new Java execution.
SIM-ADAPTERS-001, SIM-EXPORT-001 cover client export behavior.
**Native blocker:** official corrected component tuple and approved producing
fixture/adapter missing. **Request / close:** capture a newly generated official
native export with embedded units and unchanged scientific/spatial/mask precision.
Local component correction and simulator exports do not close upstream #10.

### JAVA-GAP-0011

**Title:** Legacy frozen worldview fixture differs from current ODO/source-validation contracts

**Scope:** semantics — Reasoner / Resources worldview and authority fixtures

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** native compatibility tests cannot
silently replace a frozen worldview oracle. RC-P4.1, RC-P4.2, RC-R1, CAP-05, CAP-16;
[#193](https://github.com/integratedmodelling/klab-services/issues/193).
**Contract/environment:** no invented endpoint. Historical original ODO SHA256
`496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57`
differs from current `5c020dac9bd9d1dcd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3`
after additive `impliesPredicate`. The old 243-file fixture also reports
`DECLARATION_CLAUSE_SOURCE_INVALID` for `life:SocialGroup` and `ecology:Community`
EMERGES_FROM; TAXA/CHEM descriptors require installed authority providers. This is
not proof of a numerical algorithm failure or a current missing reasoning API.

**Run now:** shared commands with `--gap JAVA-GAP-0011`;
`developer-tools/klab-service-simulator/tests/test_source_validation.py`;
SIM-SEMANTICS-001, SIM-SOURCE-VALIDATION-001. Expected synthetic changed-worldview
refusal, not authoritative ontology admission. Actual fresh native fixture NOT_RUN.
**Native blocker:** agreed versioned original/replacement fixture and provider
identities. **Request / close:** preserve old SHA/assertions and negatives, then
explicitly version and semantically validate any replacement. No ontology rewrite
or repair is requested here; separate minimal native fixture success does not
migrate the original worldview.

### JAVA-GAP-0012

**Title:** Remote Java currentness correction lacks complete deployed acceptance

**Scope:** runtime — paired Runtime currentness and remote observation reads

**Native status:** NOT_TESTED

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** awaited refresh across paired
Runtimes must preserve currentness/semantic transaction rules. RC-P3.2, CAP-10;
[#144](https://github.com/integratedmodelling/klab-services/issues/144),
[merged correction #148](https://github.com/integratedmodelling/klab-services/pull/148).
**Contract:** historical production-client path threw `UnsupportedOperationException`.
The source correction merged with bounded 45-test and HTTP 503 controls. Do not
call that fix absent. No new endpoint/payload is asserted; deployed currentness
uses maintained native read/refresh routes. Expected: original semantic binding,
transaction and old-runtime compatibility assertions, with honest partial pages;
actual complete paired-runtime deployment NOT_RUN.

**Run now:** shared commands with `--gap JAVA-GAP-0012`;
`developer-tools/klab-service-simulator/tests/test_partial_refresh.py::test_native_awaited_refresh_uses_existing_routes_and_retains_honest_partial_pages`;
SIM-REFRESH-001. Synthetic partial-page tests do not verify the deployed fix.
**Native blocker:** matching paired Runtime tuple, currentness fixture and operator
rights. **Request / close:** retain native paired-build positive/503/old-runtime
receipts and original transaction assertions; keep issue open until acceptance.

### JAVA-GAP-0013

**Title:** Finite simulator scheduling, admission and semantic answers remain provisional substitutes

**Scope:** simulator-contract — Reasoner / Resources / Resolver / Runtime substitutes

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P2 / unknown. **Impact:** SDK discovery, selection, input
binding, dependency inspection and notebooks need deterministic development
behavior without claiming native reasoning/science. RC-P1.1, RC-P1.5, RC-P3.3,
RC-P4.1, RC-P4.3, CAP-04, CAP-07, CAP-13, CAP-14; no dedicated issue. Historical
supplemental dependencies RC-P0, CAP-08, CAP-18;
aliases SIM-CLOCK-001, SIM-IMPORT-001, SIM-INPUT-BINDING-001, SIM-SEMANTICS-001.

**Contract:** [case index](../../developer-tools/klab-service-simulator/contract-cases.json)
separates captured DTO properties from provisional scheduling/admission. There is
no single new native endpoint. Poll-step timing, exact-source compiler substitute,
finite vocabulary, synthetic imports, private graph pagination and provider golden
fixtures are not native guarantees. Expected native validation requires maintained
fixtures, source/provider identities and independent semantic/scientific oracles;
actual whole-chain native parity NOT_RUN. Native-shaped dependency membership
retains closure UNKNOWN and edges UNAVAILABLE; reference graph completeness is not
authoritative scientific lineage. Data Job controller media/fall-through findings
are source questions, not executed HTTP defects.

**Deduplication:** the separately actionable source-level client encoder stub is
tracked in [0018](#java-gap-0018), not hidden in this provisional umbrella. It is
not evidence of absent service encoding or a newly executed native failure.

**Run now:** shared commands with `--gap JAVA-GAP-0013`;
`developer-tools/klab-service-simulator/tests/test_vertical.py::test_synthetic400_discovery_resolver_values_provenance_and_owned_cleanup`;
`developer-tools/klab-service-simulator/tests/test_dependency_graphs.py`.
Covered families: discovery/lifecycle/faults/selection/inspection/import/input/
semantics/behavior/notebook/replay/provenance/provider/revision/composition/
observation/material/dependency/annotated-input cases in the index.
Exact scenario IDs: SIM-DISCOVERY-001, SIM-LIFECYCLE-001, SIM-FAULTS-001,
SIM-SELECTION-001, SIM-INSPECTION-001, SIM-IMPORT-001, SIM-INPUT-BINDING-001,
SIM-SEMANTICS-001, SIM-BEHAVIOR-DRAFT-001, SIM-NOTEBOOK-001, REPLAY-001,
SIM-PROVENANCE-DEPENDENCIES-001, SIM-PROVIDER-AVRO-001,
SIM-REVISION-CONVERGENCE-001, SIM-COPIED-COMPOSITION-001,
SIM-OBSERVATION-SELECTIONS-001, SIM-MATERIAL-SELECTIONS-001,
SIM-DEPENDENCY-MEMBERSHIP-001, SIM-SOURCE-BOUND-DEPENDENCIES-001,
SIM-ANNOTATED-INPUT-PROVENANCE-001.
**Native blocker:** complete matching semantic/input/provider tuple and oracles;
the umbrella does not claim each route is missing. **Request / close:** qualify
properties individually with native fixtures; preserve narrower captured truths
and refuse unsupported reasoning instead of synthesizing native success.

### JAVA-GAP-0014

**Title:** Synthetic grid metadata, selected temporal export and descriptor mappings need real tuple evidence

**Scope:** scientific-data — Runtime scientific reads, descriptors and exports

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** correct array/raster/category/time
interpretation. RC-P2.1, RC-P2.2, RC-P2.5, CAP-09, CAP-12, CAP-15;
supplemental dependencies RC-P2.4, CAP-08;
[#123](https://github.com/integratedmodelling/klab-services/issues/123); aliases
SIM-METADATA-001, SIM-EXPORT-001, SIM-ADAPTERS-001.
**Contract:** native-shaped `GET /runtime/export/OBSERVATION/{urn}` and selected
export DTOs do not establish every multishard/time/category mapping.
[Native async contract](../../python-client/docs/native-async.md). Reference
`klab.experimental.read-plans/1` supports explicit float64/2D/CRS/affine,
north-to-south origin and complete hashed shards for finite numerical fixtures;
unsupported mappings refuse. Caller-declared labels are not native dictionaries.
Expected: exact values, masks, precision, CRS, axes, units and slice semantics;
actual general remote tuple NOT_RUN. Staging storage-view and monthly-process
source checks are real native contracts, not fresh REST tuple execution here.

**Run now:** shared commands with `--gap JAVA-GAP-0014`;
`developer-tools/klab-service-simulator/tests/test_scientific_adapters.py::test_nonuniform_native_raster_xarray_precision_mask_coordinates_and_source_trace`;
`developer-tools/klab-service-simulator/tests/test_reference_raster_labels.py`.
Families GRID/USGS/EXPORT/ADAPTERS/TEMPORAL/OFFLINE/DECLARED-BANDS/CATEGORY-CODES/
READ-PLAN-ANALYSIS/SELECTED-READ-PLAN/REFERENCE-RASTER-LABELS. Recorded bytes and
synthetic outputs retain distinct origins. Offline manifest hashes are not server
signatures, committed revisions or lineage. **Native blocker:** published matching
descriptors and native fixture rights/oracles. **Request / close:** compare exact
native slice/block/export tuples, including unknown mappings and refusal controls;
never rewrite authentic TIFF metadata or call local hashes native provenance.

Exact scenario IDs: SIM-GRID-001, SIM-USGS-001, SIM-EXPORT-001, SIM-ADAPTERS-001,
SIM-TEMPORAL-EXPORT-001, SIM-OFFLINE-SNAPSHOT-001, SIM-DECLARED-BANDS-001,
SIM-CATEGORY-CODES-001, SIM-READ-PLAN-ANALYSIS-001, SIM-SELECTED-READ-PLAN-001,
SIM-REFERENCE-RASTER-LABELS-001.

### JAVA-GAP-0015

**Title:** Production issuer/license/roles and full deployed principal matrix unverified

**Scope:** authentication — all selected services and operator identity policy

**Native status:** NOT_TESTED

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** no credential/principal leakage or
cross-tenant reads during selection/refresh/recovery. RC-P1.1, RC-P1.3, RC-P1.4,
CAP-01, CAP-02; [#89](https://github.com/integratedmodelling/klab-services/issues/89),
supplemental dependencies RC-P3.5, CAP-18, RC-R2;
[#129](https://github.com/integratedmodelling/klab-services/issues/129).
**Contract:** not a claim of missing Java authentication. Issuer/license/role and
two-deployed-Runtime matrix require supported operator policy, not a fabricated
auth endpoint. [Support/release contract](../../python-client/docs/support-and-release.md).
Expected: per-client credentials, selected identity, failed refresh refusal,
bounded access, correct expiry/revocation and zero foreign ownership. Historical
controlled privacy slices are narrower; complete deployed native matrix NOT_RUN.

**Run now:** shared commands with `--gap JAVA-GAP-0015`;
`python-client/tests/unit/test_connection.py::test_completed_failed_probe_preserves_identity_pin_without_readiness_or_failover`;
`developer-tools/klab-service-simulator/tests/test_shared_access.py`.
SIM-DISCOVERY-001, SIM-SCOPE-SELECTION-001, SIM-PROJECT-LOCK-001,
SIM-BOOTSTRAP-RECOVERY-001, SIM-NOTEBOOK-PRINCIPAL-RACE-001, SIM-SHARED-READ-001.
Hosted controls found a Python identity-pin loss bug; correction preserves only
last verified same-selected-URL identity while readiness stays failed/unknown.
Synthetic expiry is not production issuer approval. **Native blocker:** authorized
positive/negative principals, license/role policy and two-deployment fixture.
**Request / close:** operator-approved complete matrix with sanitized identity and
owned cleanup receipts, no timeout/retry/trust-policy relaxation.

### JAVA-GAP-0016

**Title:** Remote committed revision and complete historical lineage contract unestablished

**Scope:** runtime — Runtime graph, commit reports and revision authority

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** explain dependencies and converge
two clients on authoritative committed state. RC-P3.1, RC-P3.4, CAP-06, CAP-10;
supplemental dependency RC-P3.5;
alias JAVA-COMMIT-001; [#126](https://github.com/integratedmodelling/klab-services/issues/126).
**Contract:** existing `GET /api/v1/commit?id=...` with ContextScope authorization
and `POST /runtime/api/v1/query` are not a complete durable lineage protocol.
`DigitalTwinImpl` temporary commit cache is source-configured at 200 entries and
ten minutes after access, not a deployed retention guarantee. Native reports and
bounded historical provenance reads exist. Expected: exact commit IDs/change
groups/link triples, explicit unavailable null/missing groups; complete graph,
durable retention and contiguous numeric revisions remain unestablished.
DTO/projection: [graph projection](../../python-client/docs/graph-projection.md).
Complete native two-client revision/lineage race NOT_RUN; event ordering (0005) is
separate from committed authority.

**Run now:** shared commands with `--gap JAVA-GAP-0016`;
`developer-tools/klab-service-simulator/tests/test_commits.py` and
`developer-tools/klab-service-simulator/tests/test_graph_convergence.py`.
SIM-PROVENANCE-001, SIM-REFRESH-001, SIM-PROVENANCE-DEPENDENCIES-001,
SIM-REVISION-CONVERGENCE-001, SIM-COMMIT-CACHE-001, SIM-GRAPH-CONVERGENCE-001,
SIM-EXECUTION-EXPLANATION-001. Synthetic checkpoints/deltas and local SHA manifests
are not Java commits or scientific authority. **Native blocker:** maintained
revision/retention DTO contract and matching two-client fixture.
**Request / close:** establish authoritative revisions, historical access and
gap-resync behavior; run native race/provenance checks with independent oracles.
Source cache boundary: [DigitalTwinImpl](../../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java)
and the Runtime controller linked in 0006. Cache limits are version-specific source
settings, not observed deployed retention or durable history authority.

### JAVA-GAP-0017

**Title:** Computation-worker registration, lease and atomic output protocol pending native authority

**Scope:** worker — proposed Runtime worker/backend integration

**Native status:** CONTRACT_PROVISIONAL

**Simulator status:** SIMULATOR_PASS
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P1 / unknown. **Impact:** owner-scoped Python computation
workers need maintained backend integration, not a Java algorithm port. RC-P5,
CAP-17; [#127](https://github.com/integratedmodelling/klab-services/issues/127).
**Contract:** existing Resources contextualize endpoints are not proof of a
computation-worker registration/lease/result ABI. Proposed experimental worker
routes are not native Java APIs. [Worker request/lifecycle reference](../../python-worker/README.md).
Expected: capability/principal registration, lease/ack/cancel, idempotent atomic
output publication, durable recovery, bounded values/masks/geometry/units and
process ownership. Actual native worker execution NOT_RUN. No global absence claim.

**Run now:** shared commands with `--gap JAVA-GAP-0017`;
`python-worker/tests/test_containment.py` and
`developer-tools/klab-service-simulator/tests/test_worker_output_custody.py`;
SIM-WORKER-001, SIM-DURABLE-RESTORE-001, SIM-WORKER-OUTPUT-CUSTODY-001.
Synthetic leases and trusted-script process tests prove only the reference.
Windows Job Object/POSIX process groups remove owned descendants after outcomes;
optional memory limits mean Windows aggregate committed memory or POSIX inherited
per-process RLIMIT_AS, not a portable aggregate RSS cap. Deliberate POSIX `setsid`
escape, hostile-code sandboxing and total disk quota are not established.
**Native blocker:** selected supported product role, maintained backend ABI,
deployment rights and scientific transport fixture. **Request / close:** agree
native registration/lease/publication/cancellation and qualify the complete
backend workflow. C implementation is delivered; #127's native obligation stays.

**Proposed wire capsule:** prefix `/experimental/worker/v1/`, header
`X-Klab-Worker-Protocol: klab.experimental.worker/1`, current credential and owned
context. POST register `{worker_id,operation}`→REGISTERED; POST tasks
`{operation,input}`→QUEUED/task ID; POST claim `{worker_id}`→IDLE or LEASED with
input/lease; GET tasks/{id}→state/output/provenance; POST publish
`{lease,output,input_sha256,implementation_sha256}`→PUBLISHED/output hash;
fail→FAILED, cancel→CANCELLED, release→RELEASED/unfinished leases ABORTED.
Restarted active lease→RECOVERY_REQUIRED; recover requires
`confirm_recompute:true`. Unknown credential 401, wrong role 403, input 422,
version/stale lease/terminal conflict 409, capacity 429. Field rank 1..4, ≤4096
numeric cells, unique axes/coordinates, declared CRS/unit or null, explicit
boolean validity mask; invalid cells null, zero/negative valid. Output cannot
change input layout/unit/validity in this reference. No native route is inferred.

### JAVA-GAP-0018

**Title:** Resolver client dataflow encoding has no transport implementation

**Scope:** resolver — Resolver Java client/service encoding boundary

**Native status:** CONFIRMED_MISSING_API

**Simulator status:** SIMULATOR_NOT_IMPLEMENTED
**Native execution status:** REAL_NOT_RUN

**Priority / owner:** P2 / unknown. **Impact:** the client cannot supply supported
scientific dataflow encoding. Python `client.resolver.encode_dataflow(dataflow)`
currently raises `UnsupportedOperationError` before HTTP (same explicit boundary
in the async facade), rather than returning synthetic encoding. RC-P0 operation inventory; no dedicated issue or
simulator scenario; newly allocated next unused stable ID (not a renamed old gap).
**Contract:** audited source at integration SHA above:
`ResolverClient.encodeDataflow(Dataflow)` returns null at lines 103–105 while
`ResolverService.encodeDataflow` implements encoding from line 345. The interface
method exists; **the missing implementation is at the client transport boundary**.
No REST route/header/request DTO or native return payload is inferred. Source:
[client](../../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ResolverClient.java),
[service](../../klab.services.resolver/src/main/java/org/integratedmodelling/klab/services/resolver/ResolverService.java),
[interface](../../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/Resolver.java).
Expected supported behavior: maintained encoding contract or explicit unsupported
disposition, not fabricated scientific output. Actual **source-only** null stub;
no Java method or native request executed during this audit.

**Run now:** shared static command with `--gap JAVA-GAP-0018`, and
`python -m pytest -q developer-tools/java-gap-governance/tests/test_encoder_source.py::test_pinned_client_stub_is_not_absence_of_service_encoding`.
Expected: static assertions distinguish the null client stub from implemented
service encoding. This is neither a simulator encoder nor native execution.
The governance runner records `SIMULATOR_NOT_IMPLEMENTED`, not `SIMULATED_PASS`,
for this record. **Simulator reason:** no maintained remote encoding transport/DTO
authority; the SDK must retain its explicit unsupported disposition.
**Native blocker:** supported client construction/transport fixture and maintained
encoding authority/oracle are not established. A source guard is not a native
reproducer. **Request / close:** specify the client contract, then run a bounded
native client invocation with a maintained dataflow fixture and exact expected
encoding/error. Existing service implementation must not be described as absent.
Public refusal control:
`python -m pytest -q python-client/tests/unit/test_client.py::test_explicit_unsupported_and_unconfigured_methods`.

## Native execution backlog

* **Unavailable local prerequisites:** 0001 needs the missing maintained scanner
  reproducer and frozen crop/oracle custody; 0002 needs an approved disposable
  resource and cleanup policy; 0011 needs explicit fixture/provider versioning.
* **Staging-harness questions:** 0003, 0013, 0014 need exact service initialization,
  namespace/project, context geometry/focus, maintained invocation and REST mapping.
  Source access is available; runnable environment and execution are not verified.
* **Contract decisions:** 0005 durable cursor; 0006 block/lease; 0007 distributed
  operation; 0009 admitted-source revisions; 0016 committed graph authority;
  0017 worker role/ABI; 0018 client encoding authority. Capability probes cannot replace these decisions.
* **Confirmed historical defects:** 0001 scanner; 0010 unit export. Reproduce on
  maintained builds with unchanged oracles before asserting still broken or fixed.
* **External deployment constraints:** 0004 owned restart/retention fixture;
  0008 broker/principal/disposal observer; 0012 paired-runtime acceptance;
  0015 production issuer/license/roles. Missing rights do not authorize repairs.

**Missing genuinely executable complete native reproducers:** every record
0001–0018, for the individually stated reasons above. Static preflight exists for
all 18. Historical TIFF inspection exists for 0010. Prepared functional tests are
plans requiring external manifests; neither class is counted as a native test run.
Native tests actually run in this consolidation: **0**.

## Resolved setup history

The historical Modeler sequence accepted `imod`, loaded 26 ontologies, provisioned
the geospatial KAR and installed Neo4j Spatial, then successfully selected the USGS
model/resource. These are **RESOLVED_SETUP**, not currently missing Java APIs.
They do not prove scanner transport or scientific output works. Source/test fixture
drift in 0011 remains a separate unresolved obligation.

Evidence and timestamped gates:
[Modeler state](../../notes/python-roadmap-execution/MODELER_IDE_STATE.md) and
[remediation history](../../notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md).
For live-dependent work only, use the bounded checker documented in that state
file. Do not require IDE readiness for static/simulator work. No repair or fresh
gate transition was performed here. Old local reproduction commands may reference
private artifacts; they are historical evidence, not portable runnable instructions.

## Appendix

### Source provenance and receipt custody

* Campaign PRs #290–319: descriptions and file lists read; #319 merge is the audited
  integration SHA above. Original 42 acceptance IDs/history remain unchanged in
  [acceptance audit](acceptance-audit.json),
  [execution queue](../../notes/python-roadmap-execution/roadmap-execution-queue.json)
  and [A+B ledger](../../notes/python-roadmap-execution/AB_COMPLETION_LEDGER.md).
* Staging repository `integratedmodelling/klab.staging.vxii` was publicly accessible
  and cloned read-only at `0de15fbafab988455de87edd7d4581a7c22000ac`
  (commit date 2026-11-04T17:58:30+01:00). `resolve_quality`, `storage_views`,
  `monthly_process` in `testcases/klab/staging/vxii/testsuite.kactors` were inspected.
  Views D2_XY/D2_YX/D2_XInvY and mm factor 1000, 12 monthly transitions in 2014 and
  relative −10 m are source assertions, **not executions here**.
* Earlier staging file hashes are retained in
  [source-only manifest](../../notes/python-roadmap-execution/evidence/ab-staging-vxii-source-reference-20261106.json).
* Historical native TIFF bytes and expected/actual metadata are in the fixture
  manifest linked in 0010. Private historical scanner receipts are not silently
  promoted into repository custody. A receipt filename in an old report is not
  proof the file currently exists.
* Historical issue #193 references the selected-query production correction in
  [merged PR #191](https://github.com/integratedmodelling/klab-services/pull/191).
  Preserve that narrower source correction; do not reclassify it as currently
  missing or infer complete native parity from it or the broad baseline failure
  inventory. It is not a newly unresolved gap or a resolved local setup incident.
* [Full receipt index](evidence/consolidation-20261108/receipt-index.json) retains
  27 sanitized full artifacts and original/sanitized SHA256 custody, including
  both failed environment attempts, the intermediate clean 17-record pass, the
  18-record pass, 65-test governance XML and all 18 static preflights. Sanitization
  replaces user/private root paths and hostnames, never clips stdout/stderr.
  The first attempt used an old installed SDK with no worker; the second inherited
  an incompatible NumPy ABI through system site packages. The final environment
  was created without system site packages. These are test-environment incidents,
  not new Java product defects.
* The 18 static preflights exited 3 with REAL_NOT_RUN, pinned source file hashes and
  implementation commit. They made no native connection. The complete 18-record
  run includes 17 simulator passes and the 0018 source-only NOT_IMPLEMENTED guard.
  Source-union accounting is 37 modules / 33 CLI scenarios / 54 indexed cases /
  42 current roadmap rows; original history and aliases remain intact.

### Live issue-to-gap crosswalk

Read-only audit on 2026-11-08 UTC covered all 16 services issues (10 open) and the
one geospatial issue (open), including bodies/comments and campaign PR references.
Owners/labels/status were not changed. Current snapshot details and historical
acceptance statements are retained in [issues.json](issues.json). The links below
are tracking obligations, not a suggestion to close issues on simulator success.

Exact live metadata is retained in [live review](live-issue-review.json), queried
at 2026-11-08T02:55:46Z. Assignee Dobbes and label `enhancement` remain on closed
#87/#88/#91. Open #89 is unassigned with labels `enhancement`, `java`. All other
audited issues have no assignees or labels. Issue authors are not automatically
Java owners. No closure recommendation authorizes changing any owner or label.

|Issue / current title|State|Outstanding obligation / gaps / simulator tests|Recommendation|
|---|---|---|---|
|[#89](https://github.com/integratedmodelling/klab-services/issues/89) — Validate Python client integration with cross-service identity and shared-session permissions|OPEN|Native scope/lifecycle/principal and delivery evidence; 0004/0015; lifecycle and scope-selection tests|Keep open native obligations; Python delivery is separate|
|[#123](https://github.com/integratedmodelling/klab-services/issues/123) — Backend prerequisite: versioned remote scan/block/slice contract for Python access|OPEN|Published bounded reads/scientific mappings; 0006/0014; read-plan and scientific-adapter tests|Keep open; finite arrays are not native acceptance|
|[#124](https://github.com/integratedmodelling/klab-services/issues/124) — Specify and implement twin composition/migration contracts required by Python federation|OPEN|Composition/migration/failover; 0007; copied-composition tests|Keep open; attachment is narrower|
|[#125](https://github.com/integratedmodelling/klab-services/issues/125) — Complete Python agent execution/control and confirmed terminal cleanup beyond source preview|OPEN|Native managed ownership/cleanup; 0008; managed-agent/behavior tests|Keep open acceptance; native lifecycle already exists|
|[#126](https://github.com/integratedmodelling/klab-services/issues/126) — Define durable notification replay/resync contract beyond best-effort Python hints|OPEN|Durable cursor/committed lineage; 0005/0016; durable-notification/commit tests|Keep open; transient messaging/cache reads are narrower|
|[#127](https://github.com/integratedmodelling/klab-services/issues/127) — Specify provider/worker SDK role, execution and backend integration contracts|OPEN|Native role/ABI/backend; 0017; worker/output-custody tests|Keep open; C reference delivery does not integrate backend|
|[#129](https://github.com/integratedmodelling/klab-services/issues/129) — Record Python release support/version decisions and scientific/backend publication gates|OPEN|Operator/release policy, accepted source; 0009/0015; authoring/principal tests|Keep open decisions; not inherently a Java product defect|
|[#130](https://github.com/integratedmodelling/klab-services/issues/130) — Provide PaL operational artifacts and independent original-study reference contracts|OPEN|Original research inputs/oracles/rights; related 0001/0014, no simulator fulfills it|Keep open research obligation; no replication authorized|
|[#144](https://github.com/integratedmodelling/klab-services/issues/144) — Remote Context observation reads reach unsupported lifecycle-currentness default|OPEN|Paired-runtime deployed acceptance after merged #148; 0012; partial-refresh tests|Keep open acceptance; preserve source fix|
|[#193](https://github.com/integratedmodelling/klab-services/issues/193) — Track legacy synthetic fixture incompatibility with current ODO and source validation|OPEN|Versioned worldview/provider fixture; 0011; source-validation tests|Keep original negatives/oracle; no ontology repair|
|[geospatial #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10) — Native continuous GeoTIFF export omits observation measurement units|OPEN|Official published unit-preserving native artifact; 0010; native-export-capture test|Keep open; historical bytes still lack units|
|[#87](https://github.com/integratedmodelling/klab-services/issues/87) — Python client: add typed observation requests and context options|CLOSED|Historical Python tracking; original obligations/links retained in snapshot|Preserve closure; no new native proof|
|[#88](https://github.com/integratedmodelling/klab-services/issues/88) — Python client: save and restore context, observation and job handles|CLOSED|Historical Python tracking; original obligations/links retained in snapshot|Preserve closure; no new native proof|
|[#91](https://github.com/integratedmodelling/klab-services/issues/91) — Python client: stream observation exports to files|CLOSED|Historical Python tracking; original obligations/links retained in snapshot|Preserve closure; no new native proof|
|[#142](https://github.com/integratedmodelling/klab-services/issues/142) — Baseline Java test failure: required POST error-detail expectation contradicts status-only implementation|CLOSED|Historical setup/test correction; not a current missing API|Preserve closure and corrected-test scope|
|[#145](https://github.com/integratedmodelling/klab-services/issues/145) — Repair baseline core-service test setup for Java-only builds and authority selection|CLOSED|Historical test correction; not a native product fix by simulation|Preserve closure and evidence|
|[#147](https://github.com/integratedmodelling/klab-services/issues/147) — Baseline Java acceptance inventory: semantic, temporal and lifecycle regressions block Python integration|CLOSED|Historical baseline failure inventory; related 0011, not all confirmed product bugs|Preserve diagnostic failures without broad defect claims|

### Source-union coverage and current roadmap supplements

The [source inventory](simulator-source-inventory.json) conservatively fingerprints
**37 simulator modules**, **33 CLI source scenarios**, and every path-like AST
literal, independently of `contract-cases.json`. The existing case index has
**54 contract cases**, all covered by the
register's scenario references; that index is not the sole source of coverage.
Module families map to the records
above: imports/authoring→0002/0009/0013; candidates→0003; persistence→0004/0017;
notifications→0005; read plans/windows/raster labels→0006/0014; composition→0007;
managed agents/behaviors→0008; drafts/accepted documents→0009; exports→0010/0014;
semantics/source validation→0011/0013; access→0015; commit/graph/revision/explanation
modules→0016; worker→0017. Shared state/server span the complete register.
Fixtures/provider transport map to 0001/0013/0014. Generic protocol, comparison,
selection, dependency and replay helpers map to 0013. These are conservative review
boundaries, not a proof that every source statement implements a Java endpoint.

The current queue's 42 rows survive unchanged. Six rows with historical empty
links receive explicit supplemental native dependencies in that inventory:

|Requirement|Native dependency / canonical record|
|---|---|
|RC-P0 — operation inventory / compatibility diagnostics|0013/0018: provisional source contracts and unsupported Resolver client encoding|
|RC-P3.5 — notebook concurrency / own-wait cancellation|0004/0015/0016: Job lifetime, principal isolation and graph reconciliation|
|RC-P2.4 — bounded stream / interrupt|0014: native export mapping/workload; local bounded buffering alone is not native scale|
|CAP-08 — sparse typed values / missingness|0013/0014: semantic/scalar answers and scientific missingness mapping|
|CAP-18 — architecture / irreversible boundaries|0013/0015: unsupported authority assumptions and principal fences|
|RC-R2 — supported public release|0015: operator support/issuer policy; publication remains separately unauthorized|

Run `python developer-tools/java-gap-governance/source_coverage.py` to detect source
or scenario drift. New modules/scenarios require an explicit family classification;
changed source hashes require review and refreshed inventory. CI conservatively
requires canonical Markdown, register and tests in the same PR for **any** SDK,
worker or simulator source change, including local-only changes. This deliberately
errs toward review rather than silently inferring that a change is not Java-facing.

### Status glossary and preserved aliases

`CONFIRMED_DEFECT` means scoped observed native failure, not every deployed build.
`CONFIRMED_MISSING_API` here means the inspected source boundary, not global Java
absence. `CONTRACT_PROVISIONAL` is a proposed or incomplete authority contract.
`NOT_TESTED` means the relevant complete native workflow was not attempted.
`REAL_NOT_RUN` is never a Java pass. `SIMULATOR_PASS` concerns only its stated
reference/guard assertions. `RESOLVED_SETUP` is environment history.

All original JAVA-GAP-0001–0017 and aliases listed in the records/index survive;
JAVA-GAP-0018 is the newly allocated client-encoding record.
No old ID is renumbered or recycled. Local SHA256 manifests prove byte custody,
not authoritative Java commits, signatures, semantic admission or scientific lineage.

Inventory change log, 2026-11-08: moved core reproductions into this file; separated
the status triplet; added a bounded native-only capability/static probe; preserved
scanner/TIFF failures and setup history; allocated 0018 for the distinct client
encoder boundary; recorded fresh substitute/preflight results, exact live issue
titles and conservative source/same-PR gates. Delivery is tracked in
[PR #320](https://github.com/integratedmodelling/klab-services/pull/320); a draft PR
or a green local suite is not evidence of completed review/remote merge.

Standing rule: every changed Java-facing SDK/worker/simulator assumption must
update this document, `gaps.yaml` and relevant runnable tests in the same PR.
No simulator result closes an unresolved native, research or release obligation.
