# k.LAB contract-driven service simulator

**Development/test package only. Not a second scientific engine.** The production
`klab-python-client` wheel neither contains nor imports this package. The real SDK
uses its ordinary configured endpoints; no simulator fallback is installed in it.
Existing repository HTTP-fixture conventions (`http.server`) are reused, avoiding
a new web framework or runtime dependency for this finite vertical slice.

## Start and test

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
|empty-result / malformed-empty / failure|Deliberately empty scientific result, nonboolean empty flag, or ABORTED Job; errors remain visible|
|http500|Explicit Resources retrieval failure, traced once as HTTP500; no retry or synthetic replacement|
|pinned replay|Successful native semantic/model wire captures and historical null-scanner empty/Error response; not a new execution|

Synthetic bounds `[0,0,2000,2000]`, EPSG:3857, x/y, row0north, col0west,
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

For executable read-contract comparison use `python -m klab_simulator.compare`:
provide a flat JSON map of the four endpoint URLs (the startup descriptor's
`endpoints` member), an explicit model URN, mode and credential environment-variable
name. Real mode additionally requires a fresh matching read-only G0–G2receipt and
rejects identity drift. Results include per-call backend/method/route/HTTP status,
failures and untriaged real-gap records, never response bodies or credentials.
See `docs/JAVA_SERVICE_CONTRACT_GAPS.md` in the repository for exact commands.
