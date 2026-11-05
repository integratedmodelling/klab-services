# Native async advertised exports — contract before implementation

Acceptedbase `a3e8949c5da648007895694641a9f02e8bc2d6d0` (PR218), branch
feature/python-native-async-export. PR218 allCI/exactevidence/normalmerge/ancestry/
sourceequality/postmerge33PASS,ownedbranchretired. Bounded saved-handle restoration
does not close deleted-state/in-flight history or other A/B gates.

Implement awaited AsyncClient.export_formats/export_to_file through existing native
AsyncTransport.stream, shared pure media/schema/length/observation validation and
current Java export endpoints. No synchronous network/thread facade, full-response
buffering, Python rendering, Java exporter registration or scientific transformation.
Advertisement is availability, not applicability. Preserve native bytes and exact
media/schema/observation identity; complete200 identity-encoding, declaredlength,
nonempty and optionalmaxbytes checks unchanged. Keep sync API behavior intact.

Bound chunks to existing64KiB, same-directory temporary file, handle shortwrites,
fsync/close before atomic default no-clobber or explicit overwrite publication.
Cancellation/errors must close HTTP/file, remove only owned temporary files and
preserve existing destinations. Preserve primary errors with type-only secondary
cleanup notes. Local filesystem calls remain bounded synchronous operations, not
a promise of nonblocking disk; all network I/O is awaited natively.

Require source-derived negative/failure/cancellation/atomicity tests and full
installed gates, then fresh owned Java GeoJSON identity/CRS/coordinates and a
separately owned substantial native numeric stream/integrity/interruption slice.
No NonuniformExportFixture reset or synthetic category/time substitute. One coherent
feature; exactcandidate review/normalCI/eligiblemerge before acceptance promotion.

Fullcategory/time/provider/revision/maintainedexporter breadth, productionHub,
managedagents/deletedstate/restart-history and originalstudies remain separate.
Qualitywarning retained; no fullA/B/whole-row/release/publication claim.

## Development checkpoint

Native export methods and fresh Java workflow are implemented;142focused source-
checkout tests across async/sync exports and async client PASS, lintPASS. Shared
pure binding/schema parsing extracted without changing sync behavior. Async network
streams await chunks; local shortwrites/fsync/close/atomicpublication remain bounded
synchronous filesystem operations. Cancellation and injected failures preserve the
existing destination and close response/remove owned staging files. Secondary
descriptor/temporary cleanup notes do not mask primary errors.

Actualworkflow reuses unchanged GeoJSON rectangle/identity oracle and native-stream
1024x1024 numeric raster oracle, checking all1,048,576cells in bounded128x128 windows.
It requires measured64KiB native chunks and interrupted-new/overwrite preservation.
This is not a new scientific computation/provider implementation or reset of the
single-use nonuniform fixture. Full installed/live/review/normalCI remain pending.
