# Java service contract gaps — executable A+B compatibility backlog

The production SDK is not allowed to silently switch to simulation after a real
error. Python simulator verification, native Java compatibility and scientific
Java execution are **independent**. This file itemizes boundaries; the sole current
campaign/progress ledger is `notes/python-roadmap-execution/AB_COMPLETION_LEDGER.md`.

Executable contract metadata:
[`developer-tools/klab-service-simulator/contract-cases.json`](../developer-tools/klab-service-simulator/contract-cases.json).
The listed pytest IDs drive the real SDK over actual isolated HTTP, not private
SDK decoders or a canned MockTransport. Read captures are pinned in the separate
dev package; no simulator dependency or fallback is shipped in the SDK wheel.

Contract evidence categories are **VERIFIED_JAVA**, **SIMULATED_PROVISIONAL** and
**JAVA_INCOMPATIBLE**, as defined in the simulator README and machine-readable case
classifications. Gap taxonomy (bug/missing/schema/untested) is a separate dimension.
Native source/harness assertions alone are not silently promoted to new observed
HTTP/runtime success. An untested property is not an observed incompatibility.

The local `klab.staging.vxii` reference is accessible and was inspected read-only:
terrestrial-Region initialization, lexical project/namespace and within focus,
5x4 scan/mediation checks, twelve-month relative updates, and committed selected-input
receipt inspection. Source hashes and distinctions between owner confirmation,
source inspection and actual execution are in
`evidence/ab-staging-vxii-source-reference-20261106.json` under the sole campaign ledger.
This demonstrates that temporal/selection/commit mechanisms have native Java reference
implementations; do not generalize this IDE's scanner bug into an absence claim about
Java end-to-end functionality. No Monday walkthrough or live repair is a Python gate.

## Gap register

|ID / classification / owner|A+B/API and exact request contract|Evidence, simulator/Java result, uncertainty and acceptance criterion|
|---|---|---|
|JAVA-SCANNER-001 **JAVA_BUG**; Resources remote numeric scanner bridge|RC-P2.1/2.3/2.5/CAP09; `Context.observe`→`POST /runtime/api/v1/submit`, native Observation+constraints, authorized klab-scope and originating Runtime klab-service; Job status/retrieve then StorageScan.Point values|Authentic registered USGS model selected historically; Runtime Job9FINISHED but empty/Error. Installed Resources bytecode passes null Scanner, RasterEncoder183NPE; exact handoff/reproducer in remediation report§12. SIM-GRID/SIM-USGS pass only simulation. Fresh read-only G0–G2does not clear this. Require attested fixed build plus one authorized original400cell Runtime/oracle comparison; no Python terrain replacement|
|JAVA-RESOURCE-CLEANUP-001 **MISSING_JAVA (source finding)**; Resources CRUD|RC-P4.3/4.4/CAP13/14; advertised `POST /resources/import/{schema}/{urn}` multipart file returns positive-long Job; normal resource retrieval after completion|Both native public Python import facades recognize actual advertised schemas. SDK multipart/Job socket tests pass. ResourcesProvider.delete has TODO RESOURCE/no supported resource-delete branch, so positive upload on preserved IDE is not safely cleanable. Installed deletion not re-executed; no claim a particular destructive request failed. Need explicit disposable/retained-resource acceptance tuple or implemented owned-resource cleanup; no pollution of live IDE to green a test|
|JAVA-CANDIDATES-001 **SCHEMA_DRIFT / NOT_YET_TESTED**; Resources deployment|RC-P4.1/CAP13; source ModelCandidateRequest version1and `POST /api/v1/models/candidates` (ServicesAPI.RESOURCES.MODEL_CANDIDATES)|Current source advertises modelCandidateProtocol; installed202610301844capability did not. No new Python forcing/model-selection wrapper invented from source alone. Need versioned advertised protocol and actual supported request/response; catalog lookup is not Resolver selection|
|JAVA-RETENTION-001 **NOT_YET_TESTED**; Runtime lifecycle|RC-P1.2/P3.3/CAP07; original session/context/Job references, owner credentials and service binding across recovery|Prior bounded real completedJob restoration exists; no full durable in-flight/history/incarnation guarantee. SIM-LIFECYCLE tests preserve ownership and refuse replay but cannot establish server retention. Require authoritative retention/incarnation contract and exact restart/expiry fixture; #89|
|JAVA-COMMIT-001 **NOT_YET_TESTED (remote compatibility)**; Runtime graph/events|RC-P3.1/P3.4/CAP10/11; bounded typed query and activity hints with explicit stale/refetch|Partial actual provenance/hints delivered; staging harness references native commit IDs, scoped selection receipts and temporal quality revisions. Their supported remote Python ordering/cursor/retention/version envelope and full historical lineage compatibility have not been established here, not proven absent in Java. SIM-PROVENANCE remains partial. Require matching advertised HTTP DTOs/receipts plus two-client race acceptance; no simulator guarantee becomes Java truth|
|SIM-CLOCK-001 **SIMULATOR_ASSUMPTION**; simulator maintainers|RC-P3.3/CAP07; status WAITING→STARTED→FINISHED after controlled polls, no elapsed-time dependency|SIM-LIFECYCLE and faults pass deterministic tests. Poll-driven scheduling is PROVISIONAL_SIMULATOR_CONTRACT, not how Java schedules work. Require only legal native states/terminal semantics from real service; do not demand identical timing or invent idempotency|
|SIM-METADATA-001 **SIMULATOR_ASSUMPTION**; simulator/scientific descriptor maintainers|RC-P2.1/2.2/2.5/CAP09; native GeometryImpl, StorageScan.Point offsets plus explicit `metadata.grid` fixture projection|Synthetic bounds/CRS/centres/units/row orientation/precision are fully tested. The grid metadata projection and UNSPECIFIEDfixture offset order are provisional, not alleged native Java metadata keys or universal traversal. Real Java descriptors require independent matching source/format/oracle; unknown mappings must remain unknown|
|PY-IMPORT-001 **PYTHON_BUG (public upload entry point delivered)**; Python client|RC-P4.3/CAP13/14; native advertised RESOURCE/STREAM multipart import through Resources Job|Production upload APIs merged in PR244. Both facades now traverse simulated advertised multipart upload→Resources Job→private catalog/retrieve with source-byte/hash receipt matching. Current native schema reads pass Java. Full semantic annotation/model-bound compute and authoritative input lineage remain uncompleted; neither upload nor retrieval proves Runtime used that input. No COMPONENT install or implicit annotation/model execution|
|SIM-IMPORT-001 **SIMULATOR_ASSUMPTION**; simulator maintainers|RC-P4.3/CAP13/14; one pinned GeoTIFF fixture, private returned resource and source-hash metadata|Native envelope/schema/ResourceSet shape comes from Java controller/source and public SDK contracts. Suggested-URN retention, private catalog admission, hash metadata and context-release cleanup are explicitly provisional, not newly observed Java promises. Require a versioned native admission/annotation contract and authorized positive-Java fixture before promoting these properties|
|SIM-EXPORT-001 **SIMULATOR_ASSUMPTION**; simulator maintainers|RC-P2.1/P2.4/CAP09; native export capability/Accept/scope/home Runtime route, exact immutable recorded TIFF|Sync/async unchanged SDK tests preserve all original bytes and atomic output on deliberate truncation/media/permission/size faults. `simulator.native.geotiff` identity and finite recorded applicability are provisional; no native Java exporter is claimed to have that identity. Java scanner remains blocked, and this small file is not provider-scale throughput evidence|
|SIM-INPUT-BINDING-001 **SIMULATOR_ASSUMPTION**; simulator/Resources maintainers|RC-P4.3/P4.4/CAP13/14; explicit native multipart import followed by exact semantic namespace parse and new-workspace/project/document ADD; Resolver plan names model and input|Source form `model <resource URN> as geography:Elevation in m;` comes from historical accepted namespace receipt. Finite parser/admission, selected fixture and `metadata.input` SHA/count/identities are provisional, not a general compiler or Java provenance guarantee. Both SDK facades traverse all400recorded cells after binding; real scanner/cleanup/semantic-admission gates remain unchanged|
|PY-ASYNC-AUTHORING-001 **PYTHON_BUG (fixed/delivered PR249)**; Python maintainers|RC-P4.3/P4.4/CAP14; native awaited parse/new-workspace/project/document ADD|AsyncAuthoring shares pure consent/source/identity/confirmation validation with Authoring and awaits transport directly. Unit cancellation/ambiguous-write controls, isolated HTTP workflow,3664SDK tests/18gates and hosted CI passed; PR249 remotely merged. No thread facade, role elevation, retries, update/lock/version guarantees or implicit cleanup. Genuine Java admission remains separate|
|SIM-ADAPTERS-001 **SIMULATOR_ASSUMPTION**; simulator/scientific fixture maintainers|RC-P2.1/P2.5/CAP09/15; frozen independent synthetic4x5native raster, optional scientific adapters|The existing independent N-oracle is unchanged; a first native TIFF materialization is separately pinned and served by the simulator without raster libraries. Tests verify all20values/masks/float64/CRS/metre units/centres and pandas null/NaN/repeated offsets. This is not Java scientific output, multishard/category/time support or provider accuracy. The original authentic TIFF's lack of sufficient internal georeferencing is a known fixture limitation, not a newly observed Java defect or authorization to regenerate it|
|SIM-SEMANTICS-001 **SIMULATOR_ASSUMPTION**; simulator/Reasoner maintainers|RC-P4.1/CAP05; native-shaped concept lookup, finite hierarchy/boolean predicates, canonical authority paging and passive documentation|Sync/async SDK workflows enforce exact code selection, stale worldview refusal, missing revision unknown, unavailable/unsupported distinct from empty success. The three-concept SIM vocabulary, hierarchy, predicate answers and two same-label codes are SIMULATED_PROVISIONAL, not observed Java ontology/provider facts or a compiler. Real authority/worldview compatibility needs its own authorized captures; no provider credentials or scientific outputs are synthesized|
|PY-ASYNC-ANALYSIS-001 **PYTHON_BUG (fix active)**; Python maintainers|RC-P2.5/CAP15 plus shared sync/async architecture; explicit awaited export association|Two before-fix tests reproduced missing async entry point and sync-helper AttributeError/unawaited coroutine for an async-bound observation. Added native async_export_for_analysis and early facade checks; focused interruption/cancellation/identity/hash tests pass. Existing simulator export endpoints suffice; no new service behavior or Java defect is inferred|

## Request/response and ownership rules

Plain behavior draft creation (RC-P4.2/P4.4/CAP14): Authoring/AsyncAuthoring now
reuse native BEHAVIOR parse and KlabDocument ADD with exact source/new-project/
consent/CREATE receipt checks. Plain subtype is validated before any draft write;
scripts/apps/testcases and existing-source selectors refuse. No instantiate, agent
start, compiler, source overwrite or publication is introduced. Named
SIM-BEHAVIOR-DRAFT-001 supplies only one literal synthetic draft on the two required
native route variants; admission/persistence remain SIMULATED_PROVISIONAL and
actual Java persistence/semantic validation NOT_RUN. Ontology creation/editing needs
its distinct project policy/source-version contracts; no generic equivalence is
inferred from native behavior document creation or principal-shared project locks.

Scope-selection correction (RC-P1.5/CAP03/04/18): shared Python validation now
rejects out-of-positive-Java-long focus/observer values, root/token inconsistencies,
observer-bearing root configurations and control characters before I/O. Raw Runtime
service methods check bound Context origin/known Runtime identity; matching-backend
requests use current credentials and still require server ACLs. Eighteen before-fix
protocol tests reproduced local acceptance/unintended MockTransport requests, not an
observed real credential leak or Java defect. Existing HTTP query routes verify
same-principal reads and different-principal private denial; no new simulator route
or behavior, membership guarantee, incarnation or revision authority is introduced.

Project-lock increment (RC-P4.4/CAP14): source-confirmed native GET project lock/
unlock routes return boolean from an ADMINISTRATOR controller. WorkspaceManager
shares ownership by stable authenticated principal; they are not editor-exclusive
leases or revision fencing. ProjectLock/AsyncProjectLock now expose consented,
origin-bound acknowledgements with no implicit unlock/retry. SIM-PROJECT-LOCK-001
adds only the routes required by that acceptance case; synthetic roles/finite lock
state remain SIMULATED_PROVISIONAL. No real project was locked or edited.
Native rejected-ontology authoring selectors do include sourceVersion checks;
generic accepted-document updates reject those selectors and lack the same source
version contract. This source distinction is not generalized absence of native
editing and not executed Java compatibility or authorization for blind overwrite.

SIM-NOTEBOOK-001 (RC-P3.5/CAP15) now executes the current SDK in an actual owned
exact-interpreter notebook kernel, while its service flow stays explicitly simulated.
Existing routes verify local wait cancellation with a surviving same-Job wait,
two contexts, same-principal reader restore/close, partial graph refresh and cleanup.
This does not promote historical served notebook receipts, establish positive
collaborator ACLs, or remove native commit/retention/scientific gaps. No new endpoint
or simulator source behavior is required by this acceptance test.

Native awaited graph correction (RC-P3.2/P3.5/CAP10): AsyncGraphBrowser and
AsyncGraphProjection reuse existing bounded typed membership/LINK queries, pure
decoding and partial retention. Both facades now snapshot relationship selections
before I/O. Cancellation and generation-race tests preserve stale markers; existing
HTTP fault controls prove awaited403/500/disconnect handling without adding an
endpoint. This is Python/simulator evidence only; native commit/revision/order and
current authorization remain unestablished, not inferred from cached data.

Native awaited deferred-registration correction (RC-P4.5/CAP14):
AsyncAgentExecutions shares exact consent/parser/identity/result checks and deferred
compile flags, with same-object async admission serialization. Protocol tests enforce
no resubmission after cancelled/unconfirmed admission and reject context drift across
awaits. This is not a durable intent ledger or service-side exactly-once guarantee.
Returned broker methods remain explicitly synchronous/thread-affine. Existing real
registration/lifecycle evidence is not newly executed by these tests; principal,
incarnation, cleanup and scientific acceptance gaps remain separately attributable.

Native awaited preview correction (RC-P4.5/CAP14): AsyncAgentPreviews uses the
existing parseAsset/BEHAVIOR and instantiate/agent compileOnly translation contract,
sharing fixed nonexecution flags and validation with the sync facade. Mock protocol
tests cover drift, cancellation, lost responses and malformed source without replay.
Historical real synchronous source-translation evidence is not new awaited Java
execution. No simulator behavior or broker lifecycle claim is introduced.

Temporal export increment (RC-P2.2/CAP09/15): Python now forwards the native
`storageEvent/storageStart/storageEnd` fields for explicit TEMPORAL_TRANSITION and
records an immutable requested slice. Controller/BaseService/Utils.Data.asPOD are
source evidence only: generic POD coercion may canonicalize event keys. Finite
SIM-TEMPORAL-EXPORT-001 reuses frozen bytes with a provisional event assignment;
this proves neither native temporal history nor revision/window leases. Actual
Java compatibility needs authorized matching retained temporal state and receipts,
not IDE repair or a simulator fallback. PR253's async association fix is remotely
merged at716dbca5c; its older “fix active” row is historical implementation context.

Native sources: `ServicesAPI`, `KlabServiceController`, `KlabJobController`, Resources
CRUD/provider controllers, `ScopeRequest`, ObservationImpl, GeometryImpl,
StorageScan.Point, KnowledgeGraphQuery/LinkImpl and actual read receipts. Each
case file records source/confidence, route/method, request shape, expected response,
pytest ID and Java status. Missing evidence is marked provisional/NOT_YET_TESTED,
not MISSING_JAVA merely because the simulator can do it.

Synthetic fixed elevations and recorded authentic elevations are different fixtures.
The latter checks handling of400frozen real cells but **cannot** establish actual
Java Runtime output. Real known failures are preserved as failed replay outcomes.
Recorded FINISHED status is sourced to the historical report; it is explicitly not
a fresh captured status response. Secret-free backend labels remain out-of-band.

## Running the cases and opportunistic Java comparison

```powershell
python -m pytest -q developer-tools/klab-service-simulator/tests
python -m klab_simulator.compare --mode simulated --endpoints simulator-endpoints.json --model-urn simulator.elevation --token-env SIM_TOKEN
python -m klab_simulator.compare --mode real --endpoints java-endpoints.json --model-urn scientific.examples.usgs_virginia.elevation-quantifier --token-env KLAB_ISSUED_TOKEN --readiness-receipt current-ide-receipt.json
```

For real Java, first consult MODELER_IDE_STATE.md and use its bounded read-only
checker for live-dependent work. Configure ordinary SDK endpoints/authorized
credentials explicitly; use the case metadata's matching public API call and retain
the exact distribution/IDs/UTC/request/result/error. Real mode never proxies through
the simulator. Safe read-only semantic/catalog captures were refreshed in this
campaign; no positive numerical Runtime or unsafe import retry is authorized by a
simulator pass. A real500is a real failure, not a trigger for synthetic substitution.

Scheduled Java workers, production issuer/roles and release approval remain external
gates. They do not block Python-only simulator CI or independent SDK implementation.
