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

## Exact installed candidate / actual acceptance pending

4dd0ef841 onaccepteda3e8949c5:
`run_gates.py --repo <checkout> --label ab-native-async-export` PASS:
3,154installedtests/all17gates/lint/build/sourceparity. Focused142testsPASS.
Wheel ec73863fdbd8c5c2db522a512aa0fe4c601b526ac5b1e6b2a3d363f8b65817c3;
sdist68375d799daaf3ca831ecad0c82aa88a645f53fbbd7ad141b1c35519844909f0.
Exact noneditableWindowsPython3.12.7, inheritedRasterio warningretained.

One fresh actual command ACTIVE, not predeclaredpass:
`run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21>
--components <corrected-components.json> --state-dir <native-async-export-live>
--scenario async-native-export`. Require genuine native GeoJSON rectangle/identity
and substantial native raster shape/CRS/unit/transform/allcells/validity, measured
boundedchunks and interrupted-new/overwrite preservation, creator/servicecleanup.
No newmodel/source/NonuniformExportFixture reset; local NumPy/Rasterio oracle only
checks bytes produced by Java, not substitute scientific computation. NoPRyet.

## Final exact-candidate recommendation

**Ready for bounded maintainer review**, author/CLI not independenthumanapproval.
Tested4dd0ef84190bf8545aac025e186816ae4fc4c9b1 onaccepteda3e8949c5. Laterdelta
Markdownonly;rawSDK/sharedsynccontracts/harness/installed/liveidentity verified.
Full diff reviewed for usefulness/architecture/security/identity/wirecontracts/
scientificmeaning/lifecycle/primaryerrors/state/performance/maintainability.
No remaining demonstrated important/blocking defect in this bounded export slice.

| Check | Verified outcome and boundary |
|---|---|
| Full installed command above | PASS:3,154tests/all17gates/lint/build/parity;142focused async/sync export/client tests |
| Fresh owned Java command above | PASS:nativeGeoJSON sourceidentity/rectangle/coordinates; native1,048,576cells/validity/unit/dtype/CRS/transform/bounds checked in64boundedwindows |
| Stream/failure/lifecycle | PASS:8,390,163nativebytes/129chunks/max65,536bytes; injectednew/overwrite interruptions preserve destinations, close responses/remove staging; creatorcontext-session/service-listenercleanup;unrelatedJavaunchanged |
| Identity/privacy | PASS:exactSDK/sharedsync/harness/installed/live candidate; actual native SHA256 and reportcredentialabsence checked |
| Final-head hostedCI/protections/normaleligiblemerge | Pending; required before merge |

Private native-async-export-live and native-async-export-final-validation.json
bind executable/artifacts/oracles/stream/nativebytes/cleanup. NativeSHA256
45ab2c5acdb3fad439fea7d3c75112dd8e122a3798ef84ed5474104f1fd1f23f;
exactwheel/sdist above. No model/source/Javaexporter/scientificfixture or expectation
change; original nonuniformfixture notreset. LocalNumPy/Rasterio checks do not replace
Java computation. Taskcancel is unit-tested; actualfaultcontrols are explicitly
injected readerrors, not provideravailability/performance guarantees. Localdisk
calls remain bounded synchronous; no wholeevent-loop/disklatency promise.

Category/time/provider/revision/fullformat and maintainedmixeddeployment matrices,
asyncHub/managedagents/fullA-B/studies remainunaccepted. Qualitywarning separate;
no release/support/contributor/reviewer assignment or publication authorization.
Next normalPR/CI/exact-headmerge/ancestry/postmerge/ownedbranchretirement.

## Hosted coverage failure retained / bounded contract test correction

PR219 finalhead19a92f020 hostedUbuntuPython3.11–3.13 offline tests passed, but
changed-branch checking failed on async final byte-count success arc64→66.
LocalWindows coverage reported that same arc executed; build/Jenkins/core/Windows
checks passed. This is not green hosted acceptance and no merge is permitted.
Cross-platform tracing difference is a hypothesis, not a proven root cause.

Extract the unchanged completed-byte-count predicate into a shared pure sync/async
validator, exercise its nonempty/matching/unknown/mismatched length outcomes
directly, and add public async small-complete exports with and without length.
No bytes/expectations/limits/thresholds/exclusions changed. Preserve failed hosted
logs; require fresh exact installed gates, actual Java and final-head hosted checks
before reusing acceptance. No failed scientificrun or blind rerun is concealed.
