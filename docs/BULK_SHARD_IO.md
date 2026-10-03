# Bounded bulk I/O for durable shards

The durable shard writer and native-buffer restoration path transfer primitive payloads in
reusable blocks of at most **64 KiB**, rather than making a stream call for each value.
This reduces Java-level encoding/decoding and stream-dispatch overhead for large scientific
states. The previous streams were already buffered: the optimization is not a claim that
the old implementation performed a disk operation for every value.

`StorageManagerImpl` retains file ownership, header validation, flushing, synchronization
and publication. Its package-private `ShardBufferIO` helper handles only primitive payload
transfer, using the existing ojAlgo `Access1D`/`Mutate1D` contracts. Public buffer save/load
signatures are unchanged. There is no new dependency or operator setting.

## Compatibility and resource boundaries

- Version-1 files keep the 24-byte header: `KLAB` magic, version, type ordinal, element
  width and value count. Header and payload remain big-endian.
- Headerless legacy files are still read using the host's native byte order, with the
  existing format detection and length checks. This does not make legacy files portable
  across machines with different byte orders.
- DOUBLE, FLOAT, INTEGER, LONG, KEYED and BOOLEAN keep their existing primitive widths.
  Long values never pass through floating-point conversion; KEYED payloads remain integer
  codes with dictionary/worldview validation owned by the existing storage layer.
- Floating-point writes explicitly use `doubleToLongBits`/`floatToIntBits` to preserve
  `DataOutputStream`'s canonical NaN encoding. Signed zero, infinities, subnormals and
  ordinary values retain the existing format. Reading legacy NaN payloads is also tested.
- Each transfer allocates one heap-backed payload buffer, at most 64 KiB, and reuses it.
  Small and empty payloads allocate only their required size. Existing stream buffers and
  the observation's native buffers are additional memory; this is not a total-memory cap.
- A complete input block is read before it is decoded. A short read is filled across
  subsequent reads; EOF and I/O errors propagate. Earlier decoded blocks can already have
  changed the destination when a later block fails. This is not whole-array rollback.
- Successful writes still flush and call `FileDescriptor.sync`. Asynchronous persistence
  and temporal preparation keep their existing temporary-file/rename lifecycle, including
  temporal `force(true)`. No graph/filesystem atomicity guarantee is added.

See [storage publication and recovery](STORAGE.md#graph-atomicity-and-recovery-boundary)
for the distinction between durable payloads and committed observations.

## Executable scenarios

`ShardBufferIOTest` supplies an independent scalar-format oracle and tests:

| Scenario | Evidence |
| --- | --- |
| All six types, empty/single-value data, block minus one/exact/plus one, and several blocks with a tail | 36 byte-for-byte file comparisons and cross-reader cases |
| Headerless native-endian compatibility | 24 legacy-file cases across all types |
| Big/little-endian payload decoding through streams returning at most seven bytes per read | 12 fragmented-read cases |
| Legacy NaN payloads and signed zero | Raw-bit checks for DOUBLE and FLOAT |
| Invalid magic/version/type/width/count; short and oversized files | Rejection before destination mutation |
| EOF after a complete block | Earlier block retained, partial block not decoded |
| Read/write failures | Original exception retained; caller owns stream closure |
| Huge logical primitive sources and destinations | Both directions reuse the same bounded buffer, without observation-sized allocation |
| Payload-size overflow | Rejected before payload allocation/transfer |
| Concurrent transfers | Independent data and files for all six types |

The scalar oracle is retained in test sources from the version-1 path at `721f37365`,
independently of the production block codec. It is also the benchmark comparator.

`BulkShardStorageTest` exercises production storage and file lifecycles:

1. Persist a 32,896-cell observation with multiple native shards, including missing data;
   close its manager, reconstruct from descriptors in reversed order, and verify every
   value through planned readers and native scanners. Opening planned readers restores
   native buffers; subsequent native scans reuse them.
2. Fail an asynchronous replacement after one complete payload block. Verify the existing
   destination bytes remain intact, partial temporary output is removed, and a retry succeeds.
3. Fail temporal file preparation after a complete block. Verify the existing file and
   pending-file cleanup.
4. Verify the public boolean save/load methods report file failures through the scope.

The integration fixture uses real `StorageImpl`, `StorageManagerImpl`, maintenance tasks
and durable files. Scope/graph descriptors are mocked. Native buffers are in memory to
avoid coupling these tests to ojAlgo's mapped-file cleanup behavior on Windows. This is
not a live service-stack or power-loss test.

### Correctness commands

From the repository root with JDK 21:

```powershell
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=ShardBufferIOTest,BulkShardStorageTest,StorageManagerImplTest,StorageReconstructionTest,StorageScanTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,ConformantScanTest,SpatialScanTest,TemporalStorageTest,KeyedStorageTest,ValueMediationTest,HistogramUtilsTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q -pl klab.services.runtime -am '-Dtest=StorageConsumerExecutionTest,TemporalProcessIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

These selections passed **165 core storage checks and 18 runtime checks**, with zero
failures, errors or skips on the measured Windows environment. This includes 79 new codec
cases and four new storage-lifecycle cases. Existing primitive/gather/mediated scanner
allocation checks continued to report zero bytes per million reads.

For just the printed storage walkthrough:

```powershell
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=BulkShardStorageTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Use `./mvnw` with the same quoted arguments on Unix. Unix execution and the full repository
suite were not run for this verification.

## Reproducible measurements

`ShardIOBenchmarkIT` is opt-in and asserts correctness, never timing thresholds. Run it
separately from correctness tests so mocking/instrumentation does not affect the measurement:

```powershell
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=ShardIOBenchmarkIT' '-Dklab.shard.io.rounds=7' '-Dklab.shard.io.warmups=3' '-Dsurefire.reportNameSuffix=benchmark-1' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=ShardIOBenchmarkIT' '-Dklab.shard.io.rounds=7' '-Dklab.shard.io.warmups=3' '-Dsurefire.reportNameSuffix=benchmark-2' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Each command creates a fresh test JVM. Each size/type has three warm-up pairs and seven
measured pairs, alternating comparator order. Both paths write the same data, include
durable synchronization, and read immediately afterward. Every restored value and both
files' byte identity are checked outside timing. The benchmark includes all six types at
262,144 and 4,194,304 values; KEYED measurements concern code payloads, not dictionaries.

The latest table and raw samples are written to
`klab.core.services/target/shard-io-benchmark.txt`; suffixed Surefire XML files retain the
output of each run. Ratios are scalar median divided by bulk median (larger is better).

### Observed results

Measured on Windows 11 amd64, Temurin 21.0.12.1+1, Maven wrapper 3.9.5, NTFS temporary
storage, 16 processors visible to the JVM, approximately 15.74 GiB maximum Java heap.
Source baseline: `721f37365` plus this contribution. Before changing production code, the
comparator was checked against the original path; timings for large DOUBLE/FLOAT/LONG
payloads were approximately equal and files matched.

Second fresh-JVM run, 4,194,304 values per type; medians in milliseconds:

| Type | Payload MiB | Scalar write | Bulk write | Write ratio | Scalar read | Bulk read | Read ratio |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| DOUBLE | 32 | 78.511 | 26.503 | 2.96x | 73.661 | 13.935 | 5.29x |
| FLOAT | 16 | 52.740 | 14.909 | 3.54x | 61.190 | 10.830 | 5.65x |
| INTEGER | 16 | 54.757 | 13.725 | 3.99x | 62.992 | 10.971 | 5.74x |
| LONG | 32 | 80.112 | 27.739 | 2.89x | 72.999 | 12.954 | 5.64x |
| KEYED | 16 | 53.624 | 12.612 | 4.25x | 62.929 | 9.840 | 6.40x |
| BOOLEAN | 4 | 46.656 | 16.172 | 2.89x | 40.566 | 18.098 | 2.24x |

Across both fresh-JVM runs, large-payload write ratios ranged from **2.71x to 4.48x**;
read ratios ranged from **2.24x to 6.48x**. These are local file-I/O measurements, not
confidence intervals or guaranteed deployment gains.

### Measurement scope

- Reads are **warm-cache**, immediately following writes. Cold-cache reads were not measured.
- Source/target arrays are in-memory ojAlgo buffers. Mapped-source workloads and remote
  filesystems were not benchmarked. The host's physical disk model/cache policy was not
  characterized.
- Timing covers file opening, header handling, payload transfer and closure; writes also
  include `sync`. It excludes input generation, buffer allocation, byte/value checks,
  histogram rebuilding, temporary-file publication and graph transactions.
- Both full arrays exist in the fixture. The bounded-buffer tests establish the payload
  scratch bound; peak process memory was not measured.
- Histogram reconstruction, allocation and other work can dominate full observation
  restoration. A payload read ratio is not an end-to-end restoration speedup.

The existing storage format and public storage API allow this optimization to benefit
normal persistence/restoration without changes to scientific components or models.
