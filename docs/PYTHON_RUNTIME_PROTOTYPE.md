# Bounded Python computation: integration findings and prototype

Inspected and tested September 30, 2026 on branch `python-bridge-baseline`, HEAD
`75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`, with the uncommitted Python contribution.
The proposed handoff referenced `be5811ffce740ec1fb713d83aaf6fb51bb7ba8fb`;
this inspection uses the actual checkout instead. Remote refs were not refreshed.

**Status: isolated scalar-contract/storage prototype. Production registration and
atomic observation publication remain unresolved.** The expanded contribution proves
bounded transfer, numerical correctness and cancellation, and reproduces a native-buffer
rollback gap. It does not meet the proposed registered Runtime integration milestone.

## Integration boundary findings

Paths below are relative to the repository root; line numbers refer to the inspected HEAD.

| Source | Finding |
| --- | --- |
| `klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/ScalarComputation.java:19-60` | Existing named-input, shard-scanner computation contract; suitable for an isolated transformation prototype. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java:464` | The runtime computation builder selects `ScalarComputationGroovy`. Implementing the interface alone does not register Python. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/CompiledDataflow.java:1167-1187` | Expression/lookup/constant resolvers go to the scalar builder; other service calls go to `ContextualizerExecutor`. A component-provided runtime function is a candidate production boundary. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/ContextualizerExecutor.java:34-69,99-155` | Component method parameters bind named input scanners; reflection invokes the function and propagates false/exception outcomes. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/AbstractExecutor.java:62-66,86-154` | Quality initialization plans/opens aligned inputs, then opens native output writers and finalizes each successful shard independently. Non-initialization quality calls are rejected on this path. |
| `klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/storage/StorageImpl.java:398-465` | Writable native scanners wrap the existing buffer and reset histogram/readability bookkeeping. Opening a writer and writing values are not an abortable transaction. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/ScalarOperationExecutor.java:26-42` | Initialization uses `AbstractExecutor`; later scalar execution uses `TemporalScalarExecution`. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/TemporalScalarExecution.java:19-65` | Transaction-owned read sessions and write-set output scanners bind the scalar computation; false stops execution. |
| `klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/storage/LocalTemporalWriteSet.java:23-29,195-214,223` | Temporal writes explicitly reject initialization. Changes are staged at prepare; the overlay keeps maps of changed values, so bounded process transfer does not establish bounded total storage memory. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/CompiledDataflow.java:1293-1330` | Success submits the contextualization result and commits; failure fails the execution scope. This does not by itself undo native-buffer mutations. |
| `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java:674-850` | Graph commit and registered rollback actions govern publication. No generic undo of initialization scanner writes is established by this path. |
| `klab.services.resolver/src/main/java/org/integratedmodelling/klab/services/resolver/DataflowCompiler.java:245` | The strategy APPLY-emission placeholder is still present. The isolated prototype bypasses the resolver; a future model must prove its actual compiled service-call path rather than assume APPLY works. |

Python here transforms existing values, so resource ingestion is not the motivating
extension role. The prototype implements the existing `ScalarComputation` interface;
it neither changes builder selection nor introduces a public backend API.

### Specific decision needed

Before registering a production computation, agree on:

1. Whether to expose a component runtime function or provide a supported opt-in scalar
   builder selection for Python. The current builder is fixed to Groovy.
2. An atomic initialization output lifecycle. The smallest compatible direction to
   investigate is runtime-owned staging for the **whole observation**, with activation
   only after all shards succeed and disposal on failure. This belongs at the existing
   transaction/storage boundary, not inside an NDVI function. It has not been implemented.
   Temporary-file staging inside each Python call would not protect against another
   shard failing or failure while copying staged values into native output.
3. The smallest provisioned environment for a model-driven live acceptance test, and
   whether maintainers already have work on these boundaries.

A temporal-only prototype could investigate the existing write set, but would not
solve initial observation creation and would still need supported computation selection.
No architecture here is presented as maintainer-approved.

## Expanded implementation

All new code remains under `klab.core.services/src/test`.

- `PythonBatchedNdvi` implements `ScalarComputation`, binds `red`, `nir`, and `self`,
  and reuses `PythonProcessRunner` and the unchanged `ndvi_worker.py`.
- Each invocation handles an aligned, fresh DOUBLE scanner partition. The caller owns
  spatial planning, scanner lifetime and output publication. The prototype is instantiated
  per invocation; its metrics/runner state are not designed for shared concurrent use.
- Batch size is 1–128 cells, one real process per batch, sequential batch ordering.
  The adapter retains only a batch of input/output JSON, not a full observation array.
  The existing 16 KiB request/stdout/stderr capture bounds still apply.
- Storage `NaN` becomes JSON `null`; null results become `NaN`. Infinity fails.
  The existing worker enforces reflectance in [0,1]; Java validates each complete response's
  cardinality, finite numeric/null types, and NDVI range before writing that batch.
- A single configurable deadline covers the partition's batches (maximum 120 seconds),
  with checks before reads, process calls and writes. Cleanup can extend beyond that
  deadline. Scope cancellation is polled during worker execution at up to 50 ms wait
  intervals; interruption also exits the runner. The runner terminates and reaps workers.
- Worker stdout/stderr are drained concurrently. Workers must not spawn children.
- Earlier successful batches remain in the supplied output if a later batch fails.
  `execute` returns false and retains the concrete cause for prototype diagnostics.
  It never calls `finalizeRun` or claims ownership of a transaction.

The existing six-cell demo retains its complete-response-before-write behavior.
Its storage fixture is reused for the expanded checks, with configurable grid dimensions
inside the same small geographic extent.

### Reviewable change groups

1. Existing subprocess/NDVI contribution plus the isolated scalar-contract adapter.
2. Batching, scope cancellation, real-worker failure tests and the native rollback-gap
   reproduction. These do **not** complete the plan's no-partial-publication acceptance.
3. Java-reference comparisons, local measurements and this integration decision record.

These are proposed review groups, not commits made during this session.

## Validation and reruns

Environment: Windows 11 amd64, PowerShell 5.1, Temurin JDK/javac 21.0.12.1+1,
Maven wrapper 3.9.5, Python 3.12.7, pytest 8.3.5. Java was installed but absent from
the initial shell; selecting the existing JDK resolved setup. No artifact/dependency
blocker occurred and no dependency version changed.

Exact setup used locally:

```powershell
$env:JAVA_HOME = 'C:\Users\lumsd\AppData\Local\Programs\Temurin21\jdk-21.0.12.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:KLAB_PYTHON_EXECUTABLE = (Resolve-Path '.\.venv\Scripts\python.exe').Path
```

Use your own JDK path on another machine. From the repository root:

```powershell
.\.venv\Scripts\python.exe -m pytest -q klab.core.services/src/test/resources/python-bridge
.\mvnw.cmd -q -pl klab.services.runtime -am '-Dtest=PythonBatchedNdviIT,PythonNdviDemoIT,PythonProcessIT,StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest,StorageConsumerExecutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

# Expected exit 1: all three integration classes fail setup with useful instructions.
$env:KLAB_PYTHON_EXECUTABLE = $null
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=PythonBatchedNdviIT,PythonNdviDemoIT,PythonProcessIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dsurefire.reportNameSuffix=missing-python' test
$env:KLAB_PYTHON_EXECUTABLE = (Resolve-Path '.\.venv\Scripts\python.exe').Path
```

The new class alone can be rerun with:

```powershell
.\mvnw.cmd -q -pl klab.core.services -am '-Dtest=PythonBatchedNdviIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Unix equivalent after the [bridge setup](PYTHON_BRIDGE.md#setup-from-the-repository-root):

```sh
"$KLAB_PYTHON_EXECUTABLE" -m pytest -q klab.core.services/src/test/resources/python-bridge
./mvnw -q -pl klab.services.runtime -am '-Dtest=PythonBatchedNdviIT,PythonNdviDemoIT,PythonProcessIT,StorageScanTest,SpatialScanTest,StorageConsumerIntegrationTest,StorageConsumerExecutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
env -u KLAB_PYTHON_EXECUTABLE ./mvnw -q -pl klab.core.services -am '-Dtest=PythonBatchedNdviIT,PythonNdviDemoIT,PythonProcessIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dsurefire.reportNameSuffix=missing-python' test
```

| Executed check | Actual outcome |
| --- | --- |
| Independent Python suite | 47 passed |
| `PythonProcessIT` | 6 passed |
| `PythonNdviDemoIT` | 3 passed |
| `PythonBatchedNdviIT` | 5 passed |
| Core storage selection | 28 passed |
| `StorageConsumerExecutionTest` | 6 passed |
| Missing-Python selection | 3 expected setup errors, no skips |

**95 unique positive checks passed**, with zero positive-test failures, errors or skips.
Surefire text reports were inspected in each module's `target/surefire-reports`.
The five new cases check Java-reference agreement over six size/batch combinations,
later-batch native mutations, active-worker cancellation, timeout/malformed response,
and pre-cancelled execution without launching a worker. Existing subprocess checks
also exercise wrong response lengths/types, nonzero exits and pipe flooding.

No registered Python function, model resolution, real graph transaction, live observation
execution, full repository suite or Unix execution was performed. The existing runtime
regressions passing does not turn the new core-module prototype into Runtime acceptance.
The native mutation test uses real buffers with mocked service/persistence infrastructure;
it establishes buffer visibility, not durable graph publication or rollback of committed data.

## Measurements

One measured run in the successful full selection above; fixed order 64 then 128,
no warm-up protocol, no repetitions/statistical claim, and no timing assertions.
Each result was verified through fresh storage readback against a Java calculation
with tolerance `1e-12`, including missing values and zero denominators.

| Cells | Batch | Processes | Elapsed ms | Read/encode ms | Process ms | Write ms | Java used heap before/after (bytes) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 6 | 64 | 1 | 103.3791 | 36.1880 | 66.5718 | 0.0135 | 38459792 / 41629008 |
| 6 | 128 | 1 | 45.6952 | 0.8691 | 44.5543 | 0.0124 | 42588104 / 42803856 |
| 257 | 64 | 5 | 227.3590 | 1.8979 | 225.0402 | 0.1081 | 43312408 / 44870784 |
| 257 | 128 | 3 | 135.7904 | 0.5676 | 134.8922 | 0.0550 | 44870784 / 45518040 |
| 4097 | 64 | 65 | 2822.3369 | 7.5963 | 2812.5975 | 1.2381 | 46476800 / 40102080 |
| 4097 | 128 | 33 | 1451.4930 | 3.1370 | 1447.0567 | 0.5769 | 40519456 / 32788176 |

- Elapsed time uses `System.nanoTime` around adapter execution. It excludes fixture
  setup, finalization and verification readback. Read time includes input scanning and
  JSON encoding; write time measures output `add` calls, not persistence.
- Process time combines interpreter startup, concurrent transfer, Python computation,
  response validation and cleanup. Those costs overlap and are not independently
  instrumented; there is no startup-only or Python-computation-only measurement.
- Heap samples use `Runtime.totalMemory() - Runtime.freeMemory()` before and after
  execution. They include the whole test JVM and are neither peak nor incremental
  allocation measurements. GC can make the second value smaller. Fixtures allocate
  full arrays/buffers; off-heap buffers, JVM RSS and **Python memory were not measured**.
  This is preliminary evidence, not proof of bounded whole-system memory use.
- The process interval dominates at these sizes. The 128-cell setting halves the
  process count for 4097 cells and was faster here. This does not establish a speedup
  over Java; the Java reference is for correctness, not a benchmark competitor.
- Console output and Surefire XML identify sizes, batches and timings. Worker identity
  is the checked-in `ndvi_worker.py` and protocol version 1; no production provenance
  activity is emitted by the isolated fixture.

Next: resolve the initialization publication/extension decision, prove the registered
runtime path, then add a minimal model and live acceptance. A regional building-area
reduction remains a follow-on: it needs agreed units/datasets and reduction semantics
beyond this per-cell transformation.
