# Bounded actual native numerical stream workload

## Q4a contract — before implementation

Base `c491dbbff934e8192e4f04d5fb62c13cfb8cbfda`; branch
`feature/python-native-stream-workload`. Reuse existing accepted scenario
`consolidation.scenario` with literal234.5 and actual served model evidence from
PR200. No new model, worldview, Java, exporter or reset of the20cell fixture.

Workload:1024x1024 cells, EPSG:3857 bounds200000..302400 /6000000..6102400,
100m cells; every native numerical value234.5m, valid. One fixed initialization
support, not a temporal cube. Values234.5 are exactly binary-representable, so
require exact equality in all1,048,576 cells. Require native float64/unit/mask,
shape/axis/CRS/bounds. Predeclared coordinate tolerance1e-7m accommodates binary64
serialization/projection arithmetic near6e6m (not a provider accuracy claim).

Require advertised native GeoTIFF, served scenario-model URN and recorded partial
execution evidence, not a Python model override. Decode all raster windows at
most128x128 rather than materialize a complete array. The current native TIFF
writer uses uncompressed default parameters: require at least8MiB actual bytes,
bounded by64MiB. Record actual transfer/chunk counts, maximum chunk length,
sampled client RSS before/at chunks and elapsed time; no universal peak-RSS or
throughput threshold is invented. Prior controlled64MiB socket/fault acceptance
is not relabeled as this actual Java workload.

Two explicit interrupted GET experiments after one emitted chunk must publish no
partial new destination and must preserve an existing valid destination byte for
byte under overwrite=True. Prove the interruption was injected, not an arbitrary
network failure, and confirm response/staging cleanup. No automatic retry or
scientific write replay. Only creator-owned context/session releases are allowed;
owned process/listener cleanup and unrelated-Java preservation remain required.

## Separate Q4 blockers and limits

Pinned GeodataIO.java291–300 advertises numerical S2 export (other dimensions
size1). Lines429–458 leave categorical export unregistered and keyed writer
unimplemented; category coverage/auxiliary work remains incomplete at484–506.
No class dictionary or time/revision/block protocol is guessed (#123). Actual
provider capacity/representative long soak, multidimensional/time and categorical
legends, authentic year/mask/class bindings and published-study accuracy remain
unproved. This synthetic workload cannot close wholeRC-P2.1/P2.2/P2.3/P2.4/Q4,
release/public-exporter publication or any historical replication gate.

Implementation/installed/live acceptance pending at this contract checkpoint.

## Verified bounded outcome — 2026-11-05

Atc283bd437:2,977installedtests/17gates/lint/build/sourceparityPASS. Actual Java
`native-stream-workload` PASS:1,048,576values in64windows,exact234.5m/float64/validity,
declared geometry/CRS/axes. Native8,390,163bytes in129chunks,max65,536bytes;
SHA256`45ab2c5acdb3fad439fea7d3c75112dd8e122a3798ef84ed5474104f1fd1f23f`.
Both deliberate interrupted reads passed staging/response/new-file/existing-file
safety checks. Creatorcleanup and ownedprocess/listenercleanupPASS; unrelatedJava
unchanged. One successful export measured0.297s and sampled RSS growth0bytes at
chunk checkpoints; this is **not** a universal peak-memory/throughput assertion.
See the [author/CLI review](reviews/native-stream-workload.md) for commands and
limits. Normal hosted/merge/release acceptance remains separate.
