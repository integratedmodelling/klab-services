# Contributor review: bounded actual native stream workload

2026-11-05 UTC; author/CLI review, not independent human approval. Basec491dbbff,
executablecandidatec283bd437; branchfeature/python-native-stream-workload.

## Recommendation

Ready for bounded maintainer review after the exact evidence below; not human
approval or release. The bounded workload is
useful but cannot close full scale/provider/category/time requirements. No new
production SDK, Java, scientific model/fixture, dependency or worldview code.

## Capability, architecture and scientific contract

Reuses accepted scenario selection234.5 and recorded served model-URN evidence,
existing native streaming/atomic publication, and creator-only scope cleanup.
All1,048,576 native cells are independently checked in64windows, at most128x128
cells per read; shape/axis/CRS/unit/mask and fixed input support are prespecified.
Exact value equality is justified by binary-representable234.5. Coordinate tolerance
1e-7m is an arithmetic/serialization allowance, not source/provider accuracy.
No Python-generated scientific output substitutes for Java. Local Rasterio files
are unit controls, not live scientific acceptance. Old nonuniform20cell oracle is
not reset or reused at incompatible cardinality.

## Findings and validation so far

No demonstrated important/blocking defect in the inspected harness. 79focused
workload/export tests and lintPASS; full installed gates active, actualJavaNOT_RUN.
Native chunk instrumentation is test-only on one Client instance and restores
transport/iterator hooks even on failure; it does not change public SDK behavior.
Only deliberate injected read failures count as interruption evidence; genuine
transport failures fail the test. Digest/staging/destination/response checks are
separate from reader checks. RSS is sampled at chunks, not a peak-memory proof.

## Identity, lifecycle, state and performance

All remote creates are in one isolated owned session/context, with exact endpoint
and Runtime bindings retained by existing APIs. Native retries are two explicit
read-only fault experiments, never automatic scientific mutation replay. All owned
releases are attempted and primary errors retained. No credentials enter the
scientific report; only synthetic model/observation identity and measurements.
The runner uses private directories, hidden owned processes and explicit ports/
artifact hashes; unrelated Modeler/Java must remain unchanged.

One fixed workload (decoded8MiB, download8..64MiB) is not representative-provider
capacity, multi-shard/time or long soak. No throughput/RSS threshold is invented;
the bounded chunk invariant and file-publication failures are checked directly.
No shared server/provenance revision, original scientific input or historical
replication claim. Categorical keyed exporter absence is source-specific, not a
blanket Java blocker; missing category/time contracts stay visible.

## Coordination and next actions

No competing contributor ownership inferred and no reviewers assigned. Maintainer
support/release and public corrected-exporter publication remain separate. Complete
exact installed/live/cleanup checks, append outcomes without erasing failed cases,
then normal final-head CI/protections/eligible merge and branch retirement.

## Final exact-candidate checks

Executablecandidate `c283bd437`, base `c491dbbff`; subsequent edits are Markdown
only and must be verified before evidence reuse. No demonstrated blocking or
important defect found in the reviewed bounded slice. Complete diff and server
exporter/stream paths reviewed for usefulness/fit/contract/science/identity/
lifecycle/provenance/performance/maintainability and contributor coordination.

| Check / command | Exact environment/evidence | Result and limit |
|---|---|---|
| `run_gates.py --repo <checkout> --label ab-q4-native-stream` | c283bd437, installed noneditable Python3.12.7 | PASS:2,977tests/17coveragegates/lint/build/sourceparity; retained Rasterio warning |
| Focused native-workload/export tests | Same harness | PASS:79tests, including wrongvalue/unit/bounds/dtype/mask and genuine-failure rejection |
| `run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21> --components <corrected-components.json> --state-dir <q4-native-stream-live> --scenario native-stream-workload` | Same pinned Java/core/components, synthetic consolidation worldview | PASS:actualservedscenario,1,048,576cells/64windows,exact234.5/m/float64/validity,bounds/axes/CRS within predeclared tolerance |
| Actual native stream and read faults | q4-native-stream-live/result.json | PASS:8,390,163bytes/129chunks,max65,536;twoinjectedfaultsafteronechunk,response/stagingcleanup,newdestinationabsent,existingvalidfilebyte-identical |
| Owned lifecycle | Same live result | PASS:creatorreleases,ownedprocesses/listenersclosed,existingJavaunchanged |
| Hosted CI/normal merge | Final documentation head | Pending; required before merge |

Native SHA256 `45ab2c5acdb3fad439fea7d3c75112dd8e122a3798ef84ed5474104f1fd1f23f`.
Successful export elapsed0.297s; sampled RSS59,326,464bytes at baseline and at
chunk checkpoints (growth0). Interrupted-case sampled growth2,650,112 and352,256
bytes. These are observations of this one process/run, **not universal peak-RSS,
throughput capacity or server-memory guarantees**. Raw noData/unit/georeference
and original destination integrity are separately checked; backend temp-file
cleanup beyond response closure is not inferred from a client check.

Wheel `2acd249c1a199830f902ed9bc7b935dd84fec3430ee3b61416968c759911a01e`;
sdist `c9cc3c59b202ef0724c97189cad28acee06f129ae00efac2588686180f5d21d9`.
External `ab-q4-native-stream/identity.json`, `q4-native-stream-live/result.json`
retain exact arguments, hashes and observations. FullQ4/RC-P2.4 and category/time/
provider-soak/release remain incomplete. Suggested PR: this bounded actual native
numerical streaming/interruption evidence, not a broad performance promise.
Smallest next action is normal final-head CI and eligible merge.
