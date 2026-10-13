# Python branch work and nearest complete workflow

## Current branch boundaries

Functional suites and shared scenario entry points are described in
`python-client/docs/functional-testing.md`. Offline tests are grouped under
`tests/unit` and `tests/integration`; actual user workflows under `tests/functional`.
The reorganization passed 573 offline tests across Windows 3.12 and installed Linux
3.11/3.13, seven local functional checks, and both numerical throughput modes.

- Upstream `feature/python-client` contains the #84 foundation and merged client
  PRs #85 (typed requests, issue #87) and #86 (saved handles, issue #88).
- Combined integration commit: `90bebb5f35e60ad002f106c8d655e66d7e00d89d`.
- Both client issues are closed. Their additive merge passed 486 maintained tests,
  both coverage gates and hosted Python 3.11/3.12/3.13 checks.
- Server cross-service identity and permissions integration is separately published as draft PR #90,
  issue #89, candidate `244883d7b`. It has strict selected Java and live/restart
  evidence, but separate native-Java process identity-group and server cached-origin gates remain open.
- New work: issue #91 (Dobbes), `feature/python-client-stream-exports`, based on
  the combined client. The initial export commit was published without a PR;
  on 13 October the user requested a combined export and functional-workflow PR.

## What we have built

### Execution foundation (#84)

Synchronous configured Runtime/Reasoner/Resources/Resolver clients, explicit peer
initialization, session/context create/attach/focus and Job submission/status/result/
cancellation. DTOs preserve supported semantics and raw server fields. Indexed
scientific reads retain explicit geometry, units, traversal, temporal selection,
precision and valid zero/false versus missingness. Wait timeout keeps the Job;
ambiguous mutations are not automatically retried. Client closure is local only.

Shared Java corrections preserved unit compatibility, failed/null Job outcomes,
storage finalization identity, exact persisted ACLs/exclusions, private parent
ownership and explicit malformed/missing/denied domain responses. The isolated
synthetic test deployment is not production onboarding or provider validation.

### Typed requests (#85 / #87)

Validated observation/context/grid/geometry/options facades and `Context.observe`.
Supported namespace/project/scenario/observer constraints use existing consumers;
unsupported forced-model/arbitrary choices fail explicitly. Defensive wire
snapshots and reusable metadata/options avoid accidental mutation or duplication.
Bound AGENT observers, existing selection ranges and available Runtime identities
are checked locally. Focus/read/submission share canonical-origin checks.

### Saved references (#86 / #88)

Strict secret-free version-1 context/observation/job JSON; offline inspection and
atomic file saves; explicit restoration through caller-configured endpoints and
current capabilities/attach/scoped graph membership. Runtime/Resolver decoder
meaning and Job state stay distinct. Capturing/restoring work does not resubmit,
recreate, cancel or acquire disposal ownership. Encoded credentials and known
foreign bindings are rejected; process recovery is not server-crash durability.

### Streamed files (#91)

Existing advertised observation formats, native scoped export GET, 64 KiB raw
download chunks, applicable MIME/length/encoding/byte-budget checks, synced
temporary files, atomic no-clobber/explicit replacement and honest error cleanup.
No binary response buffering, per-cell pseudo-export, new server exporter or
optional scientific dependency is required by the core client.

Verified format: substantial-observation GeoJSON using an unchanged official
published geospatial component. The reference geometry is independently checked;
a fresh-process saved-handle example also exports it. Numeric GeoTIFF/PNG are
advertised by that component but not certified by this bounded acceptance.

## End-to-end logic (pseudocode)

```text
configure trusted service URLs and already-issued credentials
open client                     # local resources only
explicitly initialize permitted service peers when required

create or attach authorized session/context
build typed observation request with supported options and geometry
validate snapshots, IDs, observer semantics and known Runtime binding
resolve definition through Reasoner when requested
submit once to Runtime          # no retry after an ambiguous mutation

poll original scoped Job within caller's wait budget
if timeout: retain Job identity for later use
if failed/interrupted/unavailable: report the distinct outcome
otherwise: retrieve and decode actual server result
read selected cells with explicit geometry/curve/time/unit semantics

capture versioned reference without credentials or authority snapshots
save reference atomically
close Python client             # do not release remote work implicitly

in a new process:
    supply URLs/credentials independently
    load and validate reference offline
    match configured origin and verified Runtime/peer identity
    attach exact root and recheck current access/asset membership
    recover original observation or Job without replacement work

    inspect advertised native export schemas
    require one unambiguous requested representation
    stream authorized observation bytes to unique temporary file
    check representation and available length/byte-limit information
    sync and close; publish atomically
    on interruption/error: close stream, remove temporary, preserve old file

release only explicitly selected, test-owned remote work when intended
close local clients
```

## Closest full workflow

The bounded workflow now demonstrated is:

**typed Region creation → known geometry → native GeoJSON export → secret-free
handle → fresh-process restore and example export → independent geometry/identity
verification → explicit test-owned cleanup.**

The closest wider scientific workflow to complete is:

**typed numerical field → saved handle → fresh-process restore → advertised raw
GeoTIFF → independent reader validates the known field and supplied metadata.**

Proposed acceptance:

1. Compute a controlled nonuniform 5×4 field in EPSG:3857 with independently
   specified values, one valid zero and one missing cell.
2. Save the observation/Job reference, close Python and restore the same IDs and
   current allowed scope in a new process.
3. Confirm the genuine numeric GeoTIFF exporter is applicable, then stream its
   result without changing the stored scientific algorithm or re-encoding bytes.
4. Use an independent optional test reader to check shape, orientation, transform,
   CRS, dtype/precision, every sampled coordinate/value, zero and missingness mask.
5. Require the source measurement unit and NaN nodata/mask; report absent revision
   tags rather than inferring metadata from the spatial CRS.
6. Exercise interruption/access-negative cases and preserve existing output/work.
7. Release only owned test scopes and confirm cleanup.

Why this is nearest: typed computation, exact references, controlled values,
atomic streaming and a real advertised exporter already exist. The missing work
is format-specific applicability and scientific raster validation, not a new
protocol, broad metadata model or production provider study. This proposal has
now been implemented as the development-only GeoTIFF throughput test. The original
published exporter failed scientific validation and emitted no successful rates.
A separate local geospatial correction now passes the independent oracle, including
source units and the missing-cell mask: 3/3 serial recomputations and 14/14
concurrency-two repeated exports. The component's 10 Java tests and client's 566
maintained tests pass. See `python-client/docs/geotiff-throughput.md` for exact local
artifact provenance and measurement limits. Python throughput/functional tooling
is included in the export review branch; the component correction remains separate
local, uncommitted work. The published archive remains the failing one.
