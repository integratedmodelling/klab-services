# Native async advertised export review — in progress

Author/CLI review, not independent human approval. Basea3e8949c5(PR218), branch
feature/python-native-async-export. Contract recorded before code. One coherent
clientfeature, not a Java exporter/scientific fixture/provider SDK rewrite.

## Architecture/contracts

Await native Runtime capabilities and AsyncTransport.stream; reuse pure media,
schema, length and bound-observation validation. No synchronous network/thread
facade or full-body buffering. Sync entrypoint still enforces sync Client; Async
entrypoints remain explicit. Native bytes/media/schema preserved, no conversion or
applicability inference. Fixed64KiB iterator, same-directory staging, shortwrite loop,
completeidentity200/nonempty/length/budget checks, fsync/close then atomicno-clobber
or explicitoverwrite. Localdisk calls remain bounded synchronous operations.

Cancellation is BaseException and must close native response/descriptor and remove
only owned temporary files; never publish partial output or alter existing file.
Primary transport/cancellation/cleanup failures retained with type-only secondary
notes. Explicitdestination policy and redirects/TLS/auth/Runtimebinding unchanged.
No remote mutation/cancellation/submission/release/replay introduced by export.

## Validation checkpoint

142focused source-checkout export/async-client tests PASS, lintPASS. Negative
controls cover unadvertised/ambiguousmedia, invalidpolicy/destination, response
type/parameters/encoding/partial/length/empty/budgets, nativeReadError/accessdenial,
localtaskcancel, shortwrite/zero/write/fsync/close/link/replace/mkstemp/cleanup errors.
Existing sync export tests pass. Full installed/coverage gates and actualJava
GeoJSON/native substantial stream/integrity/interruption/creatorcleanup pending.

Actual harness reuses acceptedGeoJSON/native raster oracles unchanged; any failure
is retained/classified before bounded correction or independentwork. No old PASS
log is finalcandidate evidence without exact source/artifact equivalence. Primary
report-save errors likewise must not mask existing failures. Review not final;
noPR/wholeformat/provider/category/time/revision/asyncHub/managed/A-B/study/release
acceptance claimed. Qualitywarning and existing originalstudy blockers separate.
