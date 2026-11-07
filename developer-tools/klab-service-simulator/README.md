# k.LAB contract-driven service simulator

**Development/test package only. Not a second scientific engine.** The production
`klab-python-client` wheel neither contains nor imports this package. The real SDK
uses its ordinary configured endpoints; no simulator fallback is installed in it.
Existing repository HTTP-fixture conventions (`http.server`) are reused, avoiding
a new web framework or runtime dependency for this finite vertical slice.

## Start and test

`--scenario copied-composition` combines two copied reference snapshots into a
C-owned declared arithmetic result. Source origins remain caller assertions, not
remote grants; no coordinator remote fetching/live federation/revocation propagation.
See [reference boundaries](../../python-client/docs/copied-composition-reference.md).

`--scenario revision-convergence` provides explicit experimental checkpoint CAS/
history over completed synthetic observation IDs. Ephemeral only, durable-storage
mode refuses; not native scientific commits or full lineage/notification convergence.
See [reference boundaries](../../python-client/docs/reference-revision-convergence.md).

`--scenario provider-avro` serves independently generated finite raw Avro request/
Data Job fixtures, not a codec imported from the SDK. Unknown valid requests refuse
as outside this fixture. No runtime fastavro dependency, worker ABI or scientific
scanner repair is implied. See [binary provider scope](../../python-client/docs/provider-binary-contextualization.md).

`--scenario provenance-dependencies` provides finite declared dependency and plan
edges for existing provenance readers (SIM-PROVENANCE-DEPENDENCIES-001/JAVA-GAP-0016).
No computed dependency, complete lineage or native committed revision is claimed.
See [reference boundaries](../../python-client/docs/provenance-dependency-reference.md).

SIM-CANDIDATE-SOURCES-001/JAVA-GAP-0003 uses `--scenario candidate-sources` for
source-defined protocol1 Resources catalog snapshots. Deterministic namespaceJSON,
source hashes and synthetic permissions are not native semantic admission or model
selection. The SDK requires advertisement and never falls back or delegates Runtime
scope to a supplier. See [candidate source inspection](../../python-client/docs/model-candidate-sources.md).

SIM-NOTEBOOK-PRINCIPAL-RACE-001/JAVA-GAP-0009/0015 adds exact owned-kernel acceptance
with separate SDK clients, a same-owner private CAS race, foreign-principal denial,
reader-close preservation and explicit cleanup. No native sharing/admission or
issuer approval is inferred; see
[notebook boundaries](../../python-client/docs/notebook-principal-races.md).

SIM-BOOTSTRAP-RECOVERY-001/JAVA-GAP-0015/0007 exercises existing SDK bootstrap,
localSDKexpiry/reacquisition through one400cellJob, denial/no-replay and actual owned
Runtime outage/recovery/service-IDdrift. No automatic standby work or native issuer/
composition claim. The launcher supports explicit bounded identityseeds; default7
and no-visible-console ownership/cleanup unchanged. See
[acceptance scope](../../python-client/docs/bootstrap-recovery-acceptance.md).
Fast-refusal hosted controls exposed an SDK bug losing the selected UUID after a
completed failed probe; the correction retains the last verified same-URL identity
without retaining old readiness or accepting actual changed identities. This is a
Python recovery correction, not a native issuer/federation fix.

SIM-READ-PLAN-ANALYSIS-001/JAVA-GAP-0006/0014 materializes complete reference
snapshots offline after grant/context/service release, preserving numerical masks,
affine-centre layout and exact source binding. Content/manifest hashes are NOT native
commits/signatures/provenance graphs; local mutable derivatives require separate
attestation. See [analysis limits](../../python-client/docs/read-plan-analysis.md).

SIM-MANAGED-AGENT-001/JAVA-GAP-0008 adds proposed finite synthetic managed lifecycle
fixtures under `klab.experimental.managed-agents/1`. This executes NO k.Actors source
and creates NO script/app/testcase private session. Stop acknowledgement, terminal
fixture state and later synthetic cleanup confirmation are distinct; native Java
resource disposal remains unverified. See
[reference scope and limits](../../python-client/docs/managed-agent-reference.md).

SIM-READ-PLAN-001/JAVA-GAP-0006/0014 adds explicit proposed snapshot shards under
`klab.experimental.read-plans/1`. Bounded pinned numerical buffers and digests are
not native committed revisions. Current-owner/root/Runtime grants are process-local,
expire and disappear on restart; epoch fences prevent accidental reuse. No automatic
replan/replay or real-service fallback. See
[proposal and limits](../../python-client/docs/read-plan-reference.md).

SIM-CATEGORY-CODES-001/JAVA-GAP-0014 exports a separately pinned2x3int16synthetic
code TIFF (`category-code-export`, observable `synthetic:CategoryCode`, units absent).
SDK caller-declared `RasterCategories` labels preserve all raw codes/masks, including
zero/negative values, and reject unknown valid codes. No native categorical
dictionary, ontology classifier or scientific measurement is inferred. Point reads
refuse422because that mapping is unestablished; export/analysis remains explicit.
See [categorical derivative contract](../../python-client/docs/raster-categories.md).

SIM-REVISION-DRAFTS-001/JAVA-GAP-0009 adds explicit opt-in private draft CAS over
proposed `klab.experimental.drafts/1`, not native Resources authoring/publication.
Exact source SHA256 and expected revision prevent blind concurrent overwrite;
bounded8names/context and8versions/name fail explicitly on eviction/capacity.
SQLite preserves draft revisions, validates all retained history before restore,
rolls back failed durable saves and cleans released scopes. Every response says
UNVALIDATED_PRIVATE_DRAFT, even if the retained text resembles a model/behavior.
No parser, compiler, agent or publication executes. See
[reference contract](../../python-client/docs/revision-draft-reference.md).

SIM-DURABLE-NOTIFICATIONS-001/JAVA-GAP-0005 offers an explicitly experimental
retained Job-hint reference, NOT native AMQP replay. First activation establishes
a tail cursor; only subsequent finite Job transitions are recorded. Sync/async SDK
reads return immutable batches and checkpoints, but callers decide when processing
is complete before persisting a returned cursor.128event retention and exact epoch/
sequence checks produce explicit RESYNC_REQUIRED on a gap—never partial or guessed
history. Opt-in SQLite retains reference logs; old checkpoints keep prior state but
do not fabricate pre-activation events. Released contexts remove logs. See
[protocol and limitations](../../python-client/docs/durable-notification-reference.md).

SIM-DECLARED-BANDS-001/JAVA-GAP-0014 adds a separately pinned synthetic3band2x3
GeoTIFF over existing export transport. `declared-band-export` serves the immutable
fixture; all18values, per-band missingness and metre units are preserved. The SDK's
optional `RasterBandAxis` supplies explicit caller labels/provenance; no native time,
category dictionary, history or committed revision is inferred from band order.
Default analysis still refuses unlabelled multiband data. Authentic/native defect
fixtures are unchanged. Dedicated scientific CI installs all extras and exercises
the export→local-analysis→cleanup flow; core-only jobs may skip optional analysis.

Opt-in SIM-DURABLE-RESTORE-001 (JAVA-GAP-0004/0017) preserves one wholly simulated
workflow across owned process restart. Pass `--state-file <owned>.klab-sim.sqlite`
in an existing private directory **outside live `.klab`**. SQLite synchronous
transactions/checksummed bounded JSON snapshots retain scopes, Jobs, observations,
finite authoring/import state and worker task results. The default remains volatile.
No Authorization credentials or active worker publication leases are stored.
An OS single-writer lock prevents two simulators sharing a state file. Wrong
version/scenario/seed, foreign SQLite file, corruption or excessive snapshots fail
closed, never silently create replacement scientific state. Failed checkpoint
writes return507 and roll back the in-memory mutation, not a false durable success.
Real/replay modes reject simulated state files; no hybrid fallback.

On restart, registrations clear and in-flight worker leases become
RECOVERY_REQUIRED. Published/cancelled outcomes stay retained; old leases cannot
publish. Recompute requires explicit owner consent via `recover_task`; no automatic
redelivery/retry. Native Java retention/incarnation/worker semantics remain
**REAL_NOT_RUN**. The durable test kills/restarts only its owned simulator, restores
ordinary installed SDK handles on the same explicit origin, checks exact synthetic
values and fenced worker outcomes, then verifies cleanup remains empty after a
third process restart. This is not crash-safe native scientific continuation.

SIM-WORKER-001 / JAVA-GAP-0017 now provides a separate experimental worker vertical
for RC-P5/CAP-17. Install `./python-worker` alongside the ordinary SDK and simulator
to run the full development suite. The explicitly opt-in `/experimental/worker/v1/`
proposal is **not a verified native Java endpoint**: owner-scoped tasks, separate
worker role, claim lease, validated atomic publication/provenance, cancellation,
failure/release and honest reconnect. The simulator executes no scripts; the separate
worker runs a trusted operator-selected script in an isolated process. Native ABI,
durable restart and hardened deployment isolation remain unfinished, not implied by
synthetic known-answer success. See [worker contract](../../python-worker/README.md)
and the maintained [Ferdinando handoff](../../docs/java-service-contract-gaps/JAVA_GAPS_FOR_FERDINANDO.md).

SIM-BEHAVIOR-DRAFT-001 (RC-P4.2/P4.4/CAP14) adds only the required native
BEHAVIOR parse/ADD variants for one literal synthetic draft. It reuses existing
workspace/project ownership and finite authoring state. Unknown source refuses;
duplicate ADD cannot overwrite; no instantiate, Job, broker, source update,
publication or implicit unlock is simulated. This is SIMULATED_PROVISIONAL source
admission/persistence, not a k.Actors parser, compiler, scheduler or Java execution.

SIM-OFFLINE-SNAPSHOT-001 completes the fresh-process CAP12 regression using existing
routes only. Two bounded SDK read snapshots preserve all400 frozen synthetic cells;
after owned scopes are released, a fresh SDK process reloads them with every socket
audit event forbidden. Snapshot bytes/offsets/geometry/source metadata and rejection
of unknown versions are checked. This offline inspection is neither live permission,
retained-server state, a revision lease nor Java scientific execution. No simulator
source behavior, route, oracle or production SDK change is needed.

SIM-PROJECT-LOCK-001 (RC-P4.4/CAP14) requires exactly the native project lock/unlock
routes. Finite `sim-token-admin-alice`/`sim-token-admin-bob` credentials model the
controller's administrator guard, with explicit principal-shared booleans and
different-principal contention. These roles/state are SIMULATED_PROVISIONAL, not
production identity grants or editor/revision leases. Existing explicit ADD creates
the bounded project; no source overwrite, history, implicit unlock or broader admin
endpoint is simulated. Lock state is reported only by the existing test control.

Named RC-P3.5/CAP15 notebook acceptance reuses existing HTTP routes and the existing
exact-interpreter private-kernel helper. Install SDK `[notebook,local]` to run it;
dedicated `notebook-client` CI requires those imports instead of silently skipping.
The actual kernel exercises two contexts, local-only cancellation with a surviving
same-Job waiter, same-principal reader restoration/close, and partial graph refresh.
Kernel/owned-scope cleanup and absence of remote cancellation/recreation are checked.
This is SIMULATED service acceptance, not Java science, notebook GUI support or a
positive collaborator ACL guarantee. No simulator endpoint/source behavior is added.

RC-P2.2 temporal export acceptance reuses the existing `nonuniform-export` route
and frozen TIFF bytes. Only the finite assignment `fixture:transition`, start0/end1
is available; other/missing/duplicate query fields or unavailable selected states
fail422, never fall back to an unselected result. This assignment is
SIMULATED_PROVISIONAL, not native temporal history or temporal scientific accuracy.
No new endpoint, regenerated oracle, or Java execution is involved.

```powershell
python -m pip install ./python-client './developer-tools/klab-service-simulator[test]'
python -m klab_simulator --port 38000 --scenario elevation --seed 7
python -m pytest -q developer-tools/klab-service-simulator/tests
```

Startup emits one structured JSON descriptor with backend, scenario, seed, four
logical service URLs and stable synthetic service IDs. The four services use
`/reasoner`, `/resources`, `/resolver`, `/runtime` on one owned loopback port. Pass
`--port 0` for an ephemeral port. Production8091–8094,8382,7687are refused. Bind
address is always127.0.0.1. Stop the owned foreground process normally; tests create
hidden subprocesses and terminate/wait in finally, including failures. No writes
to `.klab`, service directories, credentials, databases or Java installations.

Only synthetic credentials `sim-token-alice` / `sim-token-bob` are accepted; these
are public test constants, not production secrets. Public health/capabilities use
native route shapes. Private scopes/jobs/observations remain owner-bound; an ID
from a different service/context/principal is not automatically relocated.

SDK configuration example (the published client has no special simulation switch):

```python
from klab_client import Client, Endpoint, ObservationRequest, RectangularGeometry

with Client('http://127.0.0.1:38000/runtime', 'sim-token-alice', agent_name='alice',
    runtime_service_id='sim-runtime-7',
    reasoner=Endpoint('http://127.0.0.1:38000/reasoner', 'sim-token-alice'),
    resources=Endpoint('http://127.0.0.1:38000/resources', 'sim-token-alice'),
    resolver=Endpoint('http://127.0.0.1:38000/resolver', 'sim-token-alice')) as client:
    client.initialize_user_scope()
    session = client.create_session()
    context = session.create_context()
    try:
        request = ObservationRequest('geography:Elevation in m',
            geometry=RectangularGeometry(0, 2000, 0, 2000, 20, 20))
        observation = context.observe(request).result(timeout=10)
        first = observation.fetch_data(range(256))
        second = observation.fetch_data(range(256, 400))
        assert len(first.values) + len(second.values) == 400
    finally:
        context.release()
        session.release()
```

`tests/test_vertical.py` additionally uses public connection discovery/authentication,
model listing/retrieval, separate Resolver selection, every400values, everycell
centre, units/shape/CRS/orientation/precision, snapshot serialization, provenance,
native async and positive-controlled privacy denial. No Java request occurs.

## Modes and ownership

Contract evidence uses three independent categories throughout case metadata and
structured traces:

- **VERIFIED_JAVA**: an attributed observed Java response or authoritative Java
  harness evidence, restricted to the exact property supported by that evidence.
  Replaying such a response remains backend **REPLAY**, not live Java execution.
- **SIMULATED_PROVISIONAL**: proposed behavior where Java evidence is incomplete.
  Older `PROVISIONAL_SIMULATOR_CONTRACT` wording is an alias. All finite state-machine
  traces use this category; source-derived DTO shapes alone do not verify behavior.
- **JAVA_INCOMPATIBLE**: an observed simulator/actual-Java difference, with exact
  environment and reproduction provenance. The historical USGS empty/Error result
  versus simulated numerical success remains one such difference; it is not a claim
  that every Java deployment fails the same way. Untested behavior is not automatically
  incompatible.

The accessible local `klab.staging.vxii` project was inspected read-only as a
reference. Its manifest, basic generated terrain, testsuite initialization/focus,
native storage/mediation, monthly process and actual selected-input receipt patterns
are attributed by file hashes in the single ledger's evidence directory. Ferdinando's
working-workflow confirmation is retained separately from our source inspection;
no new Java run or wire capture was performed. No Monday walkthrough dependency is
introduced. Staging's generated terrain is not substituted for either frozen oracle.

Finite semantic exploration uses the ordinary `SemanticExplorer` /
`AsyncSemanticExplorer`: declared concept/observable lookup, a three-concept
synthetic hierarchy/predicate table and two `SIM` authority codes with the same
display label. Paging and explicit canonical-code selection are required; labels
never auto-select a concept. Documentation remains an inert URL descriptor (no
fetch/credential forwarding). `semantic-unavailable` returns native-shaped
UNAVAILABLE, while unknown authority names return UNSUPPORTED, not empty success.
All vocabulary/answers are SIMULATED_PROVISIONAL and not a real authority catalogue
or ontology compiler. `worldviewId` is supplied; absent knowledgeRevision remains
unknown. Authenticated test-only worldview change invalidates stored candidates;
fresh search is required without inventing revision/durable semantic leases.

`ResourceInspector` / `AsyncResourceInspector` can inspect advertised model meaning,
listed resources and native-shaped resolve results. Catalog advertisements are not
Resolver selection/execution or complete dependencies. Synthetic provider URLs in
ResourceSet.services are inert descriptors and never expand configured endpoint
trust or cause automatic requests to remote hosts.

- **simulated (default):** finite coherent local state. Trace backend SIMULATED.
- **replay:** `--mode replay`; exact pinned sanitized Reasoner/model reads and the
  historical failed Runtime result. Trace backend REPLAY. No session creation or
  missing-route synthesis. Unknown cases fail501. Historical FINISHED status comes
  from the remediation report, explicitly not a newly captured status response.
- **real:** `--mode real --real-endpoints endpoints.json` validates/prints explicit
  credential-free four-service SDK configuration and exits. It starts no proxy or
  simulated service. Configure the ordinary SDK directly with authorized credentials;
  real failures remain failures. This command itself does not test real Java.
- **hybrid:** deliberately not implemented yet; optional after complete workflow
  ownership is established. Never route by HTTP500, timeout or unrecognized defect.

A workflow is wholly owned by one configured backend. Simulator state maps all
session/context/Job/observation IDs to their synthetic owner and originating service.
The SDK's own origin/Runtime binding guards remain unchanged. A failed real request
cannot consult this state or silently become a synthetic success. Backend/source
labels are in startup descriptors and structured traces, not injected into DTOs.

## Scenario and evidence catalog

|Scenario|Origin / behavior|
|---|---|
|elevation|SYNTHETIC_SIMULATOR: metres=100+2row+3column,20x20; corners100/157/138/195; independent fixed400cell JSON oracle|
|zero-negative-nodata|Separate synthetic fixture offsets0..3=0,-7.5,null,NaN; base oracle unchanged|
|usgs-recorded|AUTHENTIC_FIXTURE_SIMULATED_DELIVERY: exact original20x20USGS float32 cells, not computed by Java Runtime|
|nonuniform-export|SYNTHETIC_SIMULATOR: separately frozen4x5float64/metre/zero/negative/nodata native TIFF from the pre-existing independent N-oracle; no native point-offset inference|
|empty-result / malformed-empty / failure|Deliberately empty scientific result, nonboolean empty flag, or ABORTED Job; errors remain visible|
|http500|Explicit Resources retrieval failure, traced once as HTTP500; no retry or synthetic replacement|
|pinned replay|Successful native semantic/model wire captures and historical null-scanner empty/Error response; not a new execution|

Base elevation synthetic bounds `[0,0,2000,2000]`, EPSG:3857, x/y, row0north, col0west,
centres `(50+100col,1950-100row)`, float64/native metre interpretation. No resampling
or implicit CRS/unit conversion. A mismatched geometry is rejected. UNSPECIFIED
point offsets follow the declared fixture order; no universal Java traversal claim.

The original USGS oracle hash22ed9057…and exact TIFF hash2c9fcd07…were verified
before copying. The distributable oracle redacts **only two private local paths**;
all400expected values and scientific metadata are unchanged. Its distinct hash
3fe5383b…and the TIFF hash are enforced before use. Original and projected identities,
licence/source and comparison limitations are in `fixtures/usgs-manifest.json`.
Replay bytes are also hash-pinned. Never regenerate expected data after a failure.

## State, safety and fidelity limits

### Resource file import slice

`ResourceImporter` / `AsyncResourceImporter` now negotiate the native advertised
`raster.geotiff.import` RESOURCE/STREAM schema and upload the pinned bundled TIFF
as multipart `file`, with literal write consent. Poll its Resources Job before
claiming simulated admission. The returned ResourceSet identifies the resource;
catalog/retrieve expose it only to its synthetic owner. Acknowledgement alone does
not materialize it; cancellation does not produce a resource. Duplicate/pending
URNs, unknown schemas, malformed/multiple file fields and other raster bytes fail.
This finite importer is not a general GeoTIFF validator or Java raster adapter.

Example using the `client` and `context` above:

```python
from klab_client import ResourceImporter
receipt = ResourceImporter(client, context).upload(
    'developer-tools/klab-service-simulator/src/klab_simulator/fixtures/usgs-native-20x20.tif',
    urn='simulator:alice:uploaded', schema_id='raster.geotiff.import',
    media_type='image/tiff;application=geotiff', allow_write=True)
resource_set = receipt.job.result(timeout=10)
asset = client.resources.retrieve(resource_set['results'][0]['resourceUrn'], 'RESOURCE')
assert asset.raw['metadata']['delegate']['sourceSha256'] == receipt.source_sha256
```

Source-hash metadata, deterministic private admission and deletion on owning context
release are **PROVISIONAL_SIMULATOR_CONTRACT**, not Java retention/cleanup guarantees.
No source path/name is retained or logged. At most 32 import slots (including pending
Jobs) are admitted per isolated process; explicit reset/release clears owned state.
This verifies upload/catalog/receipt handling, **not** semantic annotation, model
binding, actual use of uploaded input by Runtime, or the complete RC-P4.3 journey.

The separately selected `--scenario import-workflow` adds one finite explicit
semantic-source journey. Use the public `Authoring` / `AsyncAuthoring` APIs to
parse/admit exactly:

```text
namespace simulator.uploaded;
model simulator:alice:uploaded as geography:Elevation in m;
```

The native `model <resource URN> as <observable>;` source form is evidenced by the
historically accepted USGS namespace receipt. This fixture does **not** parse other
k.IM source or run a compiler: other drafts fail loudly. Create a new workspace,
project, then document through explicitly consented ADD calls. Before the owned
resource and exact binding are admitted, Resolver/Runtime refuse the workflow.
Afterward the plan names both the admitted model and uploaded resource, and the
recorded 400-cell observation exposes a fixture-specific `metadata.input` receipt
matching resource/model identity and actual uploaded-byte SHA/count. Sync/native
async tests verify all cells against the unchanged independent oracle.

This establishes the finite Python import/semantic-source/selection/data-handling
workflow, **not** Java scientific execution, arbitrary source admission, immutable
historical lineage or a server resource-annotation API. Admission, selection and
`metadata.input` are PROVISIONAL_SIMULATOR_CONTRACT. The partial recorded MEASURED
relation remains explicitly partial. ADD assets are retained after SDK/context
close; the test-owned simulator is explicitly reset after evidence checks, never
misrepresented as native Java workspace/resource cleanup.

At most16sessions/32contexts/128Jobs,4096trace entries,16concurrent request threads,
1MiBrequest body,3sconnection read timeout. Per-test process isolation. No remote
requests, credential discovery or external downloads. Test controls are disabled
unless `--test-mode` and synthetic control credential are both supplied; explicit
reset invalidates all handles and is not a production incarnation guarantee.

Jobs advance WAITING→STARTED→FINISHED by bounded status polls, not elapsed sleeps.
That schedule, synthetic selection rationale, simplified error body, deterministic
fixture and custom `metadata.grid` projection are **PROVISIONAL_SIMULATOR_CONTRACT**,
not newly discovered Java guarantees. Real captured DTOs and native source evidence
are distinguished in `contract-cases.json` and `docs/JAVA_SERVICE_CONTRACT_GAPS.md`.
Unknown routes fail loudly; unimplemented exports/authoring/import breadth are not
fabricated successes. The first slice does not complete the entire A+B roadmap.

Default CI installs both packages cleanly and runs pure state, HTTP SDK and replay
tests on Python3.11/3.13with no Java/IDE/Neo4j/production credentials or paid services.
The ongoing `AB_COMPLETION_LEDGER.md` tracks remaining rows and actual PR merges.

Lifecycle acceptance now runs two separate SDK processes against one retained
simulator process: secret-free context/Job/observation handles restore without any
create/submit/release during restoration, and closing either SDK does not dispose
remote state. Explicit cleanup is performed afterward by the owning test. Zero-time
wait deadlines preserve the exact Job for later result/cancellation without replay;
four concurrent native-async contexts isolate one cancelled Job from three successes.
The test-only authenticated `POST /__sim__/expireJobs` control yields native EMPTY
status (JobManager.status source), distinct from confirmed cancellation or a released
context's 404. Expiry control/timing is provisional, not Java durable retention.

Partial graph refresh uses the production `GraphBrowser`/`GraphProjection` over
actual HTTP. Authenticated test-mode `POST /__sim__/queryFault` accepts only an
existing context plus `none`, `denied`, `http500` or `disconnect`. Each read fault
propagates and invalidates retained pages; after explicit recovery/refetch only the
requested page becomes uninvalidated. Other pages stay stale, consistency remains
UNKNOWN, and local retained data never establishes current authorization. A forced
disconnect is traced as no HTTP response, not an HTTP success. No retry, automatic
resubmission, global resync, durable event order or committed-version lease is
implied. Fault controls are SIMULATED_PROVISIONAL and never enabled in production.

Recorded native exports are advertised only for `usgs-recorded` and the dedicated
`export-interrupted`, `export-media-mismatch`, `export-denied` fault scenarios.
The schema identity `simulator.native.geotiff` is explicitly simulator-only; the
native capability/`GET /export/OBSERVATION/{urn}`/Accept/scope/Runtime-binding shapes
are reused, not a claim that installed Java advertises that identity. Delivery
serves the **unchanged pinned original TIFF**, not a Python-generated terrain grid.
Sync/async file SHA checks cover every original byte (all cells and metadata).
Fault tests deterministically truncate the advertised stream or deny/change its
media type; atomic publication preserves existing output and removes temporary
files. Byte-limit rejection also leaves the owned observation readable. These
small recorded files test protocol/atomicity, not representative-scale throughput.

`nonuniform-export` is a separate synthetic 4x5 single-shard fixture, initialized
from the **pre-existing independent** SDK N-oracle (`nonuniform-export.json`), not
generated expected values from the simulator. Its first native materialization and
oracle bytes are hash-pinned; the base 400-cell synthetic/USGS oracles are unchanged.
The server only serves these finite bytes and never imports NumPy/rasterio/xarray.
Scientific-adapter tests verify all20values, two missing cells, a valid zero, negative
values, float64 precision beyond float32, metre band units, EPSG:3857, transform and
every x/y centre. File/array/source association remains revision-unpinned and partial.
Native point-offset mapping is deliberately refused for this export-only fixture.

The independent optional scientific-adapters CI job installs/checks all four
scientific dependencies before running these cases, preventing a missing dependency
from silently skipping acceptance. Core package/HTTP tests do not require them.
Pandas views preserve null versus NaN, zero, negative values and repeated offsets
without inventing world coordinates. The unchanged authentic USGS crop does not
carry sufficient internal georeferencing for `open_raster`; its rejection is an
explicit fixture limitation, not permission to stamp oracle CRS/units into it or
a newly observed Java defect. Authentic point delivery keeps its separate evidence.

For executable read-contract comparison use `python -m klab_simulator.compare`:
provide a flat JSON map of the four endpoint URLs (the startup descriptor's
`endpoints` member), an explicit model URN, mode and credential environment-variable
name. Real mode additionally requires a fresh matching read-only G0–G2receipt and
rejects identity drift. Results include per-call backend/method/route/HTTP status,
failures and untriaged real-gap records, never response bodies or credentials.
See `docs/JAVA_SERVICE_CONTRACT_GAPS.md` in the repository for exact commands.
