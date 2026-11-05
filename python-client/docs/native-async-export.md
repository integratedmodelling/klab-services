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

## Installed checkpoint

Executable4dd0ef841: `ab-native-async-export` PASS with3,154installedtests/all17
coveragegates/lint/build/sourceparity;142focused async/sync export/client testsPASS.
Exact noneditableWindowsPython3.12.7; inheritedRasterio warning retained.
Wheel `ec73863fdbd8c5c2db522a512aa0fe4c601b526ac5b1e6b2a3d363f8b65817c3`;
sdist `68375d799daaf3ca831ecad0c82aa88a645f53fbbd7ad141b1c35519844909f0`.
One fresh owned Java run `native-async-export-live` is active. Actual GeoJSON/native
values/metadata/stream/interruption/creator/servicecleanup are not predeclaredPASS;
no PR yet or full-scale/format acceptance.

## Verified bounded outcome

Executable `4dd0ef84190bf8545aac025e186816ae4fc4c9b1`:3,154installedtests/
all17gates/lint/build/parity and142focused testsPASS. One fresh owned Java runPASS:
native897byteGeoJSON exact observationidentity/rectangle/coordinates (existing1e-6
degree tolerance); native8,390,163byteGeoTIFF with1,048,576cells checked across64
bounded128x128windows. Exact234.5m values/validity, float64, EPSG:3857, transform/
bounds/missingvalue metadata match the unchanged independent oracle.

Native stream129chunks, max65,536bytes; responseclosed. Two deliberately interrupted
read-only streams each stopped after onechunk: no partial newdestination, existing
valid raster unchanged on overwrite, responsesclosed and ownedstagingfilesremoved.
Creatorcontext/session and ownedprocess/listenercleanup confirmed; unrelatedJava
unchanged. No Javaexporter/model/fixture/source/scientifictransform change or reset.
NativeSHA256 `45ab2c5acdb3fad439fea7d3c75112dd8e122a3798ef84ed5474104f1fd1f23f`.

Exact installed/rawSDK/harness/liveidentity and private reportcredentialabsence/
nativebytehash verified. Localdisk operations remain bounded synchronous calls;
network awaited natively. Actualinterruption is injectedReadError, not a simulated
scientificresult or provider reliabilityclaim. Taskcancellation is separately
unit-tested; broad deployedcancellation/provider/time/revision matrices notclaimed.
Normalfinal-headPR/CI/eligiblemerge pending;fullA/B/studies/release incomplete.
