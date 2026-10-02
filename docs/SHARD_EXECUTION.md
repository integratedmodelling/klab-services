# Runtime shard execution limits

`MAX_CONCURRENT_SHARD_TASKS` lets an operator bound the number of active quality-shard
computations across observation requests served by one Runtime service instance.
It applies to existing contextualizers, scalar initialization, and temporal scalar execution.
Components continue to receive the same named, task-local storage scanners.

The default is **0 (unrestricted)**, preserving the existing admission policy. A positive
integer enables the shared limit. Negative values and wrong types are rejected by the
settings API; an invalid negative value loaded from a manually edited properties file
fails admission with an actionable diagnostic.

## Configuration

Use the existing Runtime settings API, for example from a Java client holding a runtime:

```java
runtime.settings().set(Setting.MAX_CONCURRENT_SHARD_TASKS, 4).get();
```

The persistent property is:

```properties
runtime.max_concurrent_shard_tasks=4
```

It belongs in the Runtime's settings file (normally
`~/.klab/services/runtime/settings.properties`). The settings API updates the running
service; manually editing the file takes effect when settings are loaded at startup.

This setting is independent of `PARALLELIZE_OBSERVATIONS`:

- `PARALLELIZE_OBSERVATIONS` determines whether configured shard splitting is honored.
- `MAX_CONCURRENT_SHARD_TASKS` determines how many resulting quality-shard computations
  may execute at once, including across simultaneous observations and contexts.
- A limit of one serializes admitted quality work even if an observation has many shards.
  Multiple unsharded observations also share the limit.

Choose a limit using the component workload and available CPU, memory and downstream
capacity. The number of available processors is a possible starting point for CPU-bound
work, not a universal optimum for I/O-bound or internally parallel components.

## Execution and lifecycle

```text
resolve dependencies / plan and open scanner sessions
    -> queue for the runtime-owned shard controller
    -> check cancellation and acquire an execution slot
    -> invoke the quality component with its named scanners
    -> check cancellation and finalize a successful initialization shard
    -> release the slot, including on false/exception/cancellation
    -> close invocation-owned input sessions
    -> caller handles contextualization success/failure and transaction publication
```

`RuntimeService` owns one controller and `CompiledDataflow` passes it explicitly to
executors, including restored occurrence plans. `AbstractExecutor` uses it around quality
computation and initialization finalization. `TemporalScalarExecution` uses the same
controller around each scalar partition, retaining the transaction-owned write set.
Temporal execution remains sequential within one invocation; separate invocations compete
for the shared slots.

Admission is FIFO by task arrival, not round-robin by observation. A task arriving later
does not bypass an already queued task. A large observation may enqueue several tasks
before another observation arrives.

### Changes while work is running

- Increasing the limit admits additional queued work.
- Decreasing it leaves running computations intact. New tasks wait until the active count
  falls below the new limit; the active count can temporarily exceed a newly lowered limit.
- Setting it to zero removes the cap without replacing the controller or losing active work.
- Queued tasks recheck settings and scope cancellation at 50 ms wait intervals. This is
  polling, not a real-time scheduling guarantee.

### Cancellation, errors and shutdown

- An already cancelled initialization request fails before scanner binding.
- A queued task checks scope cancellation and thread interruption before invocation.
  Interrupt status is preserved. Removing a cancelled waiter allows later tasks to proceed.
- Running components retain the existing cooperative cancellation contract. Admission
  control does not forcibly terminate arbitrary Java/native component code. A cancellation
  observed after a component returns prevents successful completion; initialization checks
  again before finalization.
- Failure or cancellation releases the slot. A component returning false remains a failure;
  a concrete exception is retained by the existing executor reporting path.
- An individual component failure does not cancel unrelated requests. Initialization still
  waits for its sibling shard tasks to settle, as before.
- Runtime shutdown closes admission and wakes waiting tasks. New/queued work is rejected;
  already executing work retains the existing scope/service shutdown lifecycle.

Dependency scheduling, non-quality instantiation and other orchestration do not acquire
slots. Thus an ordinary dependency chain and a non-quality orchestrator that invokes
quality dependencies can execute with a limit of one. Inputs should be declared through
the dataflow dependency contract. A quality component which synchronously submits and waits
for additional admitted quality work can exhaust the slots; this mechanism does not provide
reentrant work-stealing for that pattern.

### Resource and transaction boundary

The limit bounds **active quality-shard invocations**, not all runtime threads or all
resource use. Scanner planning/opening and output allocation currently occur before
admission. Pending tasks retain their cursors and sessions. The controller does not impose
a queue-size, storage-memory, request-count or cluster-wide limit, and it does not control
threads started internally by a component.

Native initialization writes retain the [existing storage publication boundary](STORAGE.md#graph-atomicity-and-recovery-boundary).
Preventing finalization is not rollback of already written native values. Temporal outputs
continue to use their existing write-set transaction. Admission does not introduce a new
commit protocol or make initialization and temporal publication equivalent.

## Execution evidence

Each quality executor invocation adds a versioned JSON record under an
`im:shard-execution:<unique-id>` key in the existing execution activity metadata:

| Field | Meaning |
| --- | --- |
| `version` | Evidence format version, currently 1 |
| `admitted` | Tasks that acquired a slot, including subsequently failed/cancelled tasks |
| `succeeded` | Tasks returning true without an observed cancellation |
| `failed` | Failed admissions or false/exception task outcomes, excluding cancellation |
| `cancelled` | Cancelled admissions or task outcomes |
| `queueNanos` | Sum of time spent acquiring admission, including cancelled waits |
| `executionNanos` | Sum of admitted task time; includes initialization finalization |

The sums can exceed wall-clock elapsed time when tasks overlap. They exclude planning,
scanner opening, graph commit and later asynchronous persistence. A planning failure can
have zero attempted admissions; the owning activity/notifications remain the source of
overall execution outcome. The configured limit is a service setting, not a field in this
record; record its chosen value separately when benchmarking.

Evidence follows the existing storage-read metadata lifecycle: it belongs to the caller's
transaction and is removed by a rollback callback. Failed transactions need not publish
their activity; existing error notifications still carry execution failures.

## Executable scenarios

The tests use coordinated latches rather than elapsed-time thresholds to establish ordering
and concurrency. The integration fixture uses real runtime executors, reflected named
scanner binding, storage plans and buffers, following `StorageConsumerExecutionTest`.
Services and persistence infrastructure are mocked where that fixture requires them.

| Scenario | Executable evidence |
| --- | --- |
| Positive limit and unrestricted default; invalid values | `ShardExecutionTest.settingIsRuntimeOnlyAndPreservesUnrestrictedAdmissionByDefault` |
| Persist/reload the limit; reject invalid updates | `SettingsImplTest.shardExecutionLimitPersistsAndInvalidUpdatesPreserveTheLastValue` |
| FIFO admission and independent Runtime instances | `ShardExecutionTest.sharedCapacityAdmitsQueuedRequestsInArrivalOrder`, `separateRuntimeControllersDoNotBlockEachOther` |
| Increase/decrease/disable a live limit | `ShardExecutionTest.liveIncreaseAdmitsWorkAndDecreaseDrainsWithoutPreempting`, `disablingTheLimitReleasesWaitersWithoutReplacingTheController` |
| Cancel or interrupt a queued task | `ShardExecutionTest.cancellingTheHeadWaiterDoesNotCancelAnotherRequest`, `interruptionRemovesQueuedWorkAndPreservesInterruptStatus` |
| False, exception and Error release capacity | `ShardExecutionTest.falseExceptionsAndErrorsReleaseCapacityAndPreserveTheirCause` |
| Shutdown rejects queued/new work | `ShardExecutionTest.shutdownWakesQueuedRequestsAndRejectsNewWorkWhileActiveWorkDrains` |
| Activity evidence is removed on rollback | `ShardExecutionTest.executionEvidenceBelongsToTheActivityAndIsRemovedOnRollback` |
| Sequential/parallel settings give the same stored values | `ShardExecutionIntegrationTest.sequentialAndParallelSettingsProduceIdenticalStoredResults` |
| Restored compiled dataflows receive the same owner | `ShardExecutionIntegrationTest.compiledDataflowsReceiveTheSameRuntimeOwnedController` |
| Two observation scopes share a limit of two | `ShardExecutionIntegrationTest.twoObservationRequestsShareCapacityAcrossTheirSeparateScopes` |
| Queued cancellation prevents invocation/finalization and releases input leases | `ShardExecutionIntegrationTest.queuedCancellationDoesNotInvokeOrFinalizeAndReleasesInputLeases` |
| Request interruption stops queued and cooperative running work | `ShardExecutionIntegrationTest.interruptingTheRequestStopsItsQueuedAndCooperativeRunningTasks` |
| Concrete component failure; next request succeeds | `ShardExecutionIntegrationTest.concreteComponentFailureSurvivesAndTheNextRequestCanRun` |
| Cancellation observed before finalization | `ShardExecutionIntegrationTest.cooperativeCancellationDuringComponentExecutionPreventsFinalization` |
| Non-quality orchestration invokes dependencies with limit one | `ShardExecutionIntegrationTest.nonQualityOrchestrationCanExecuteDependenciesWithALimitOfOne` |
| Temporal scalar tasks compete for the same slots | `ShardExecutionIntegrationTest.temporalScalarWorkUsesTheSameAdmissionBoundary` |

`TemporalProcessIntegrationTest` additionally exercises real temporal storage/write-set
and scheduler behavior with a supplied shared controller. Its existing publication and
failure assertions are retained.

### Run the scenarios

For a narrated walkthrough, run this single scenario from the repository root with JDK 21:

```powershell
.\mvnw.cmd -q -pl klab.services.runtime -am '-Dtest=ShardExecutionIntegrationTest#demonstratesSharedLimitCancellationAndStoredResults' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

It compiles/restores two observation executors with four shards each and a shared limit of
two. Latches hold the admitted components long enough to inspect the queue. The walkthrough
prints asserted snapshots while it cancels the second request, raises and lowers the live
limit, releases the first request, verifies all 20 stored output cells, and executes a fresh
request at a limit of one. It also prints the activity's actual timing/count evidence.
Those controlled waits illustrate admission behavior; their durations are not performance
measurements. Service/graph infrastructure is mocked as in the other integration scenarios.

A successful run writes the same transcript to
`klab.services.runtime/target/shard-execution-demo.txt`. Each success line follows its
assertions; a failing scenario fails the Maven invocation.

From the repository root, using JDK 21 and the Maven wrapper:

```powershell
.\mvnw.cmd -pl klab.services.runtime -am '-Dtest=ShardExecutionTest,ShardExecutionIntegrationTest,SettingsImplTest,StorageConsumerExecutionTest,TemporalProcessIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Broader runtime regression selection:

```powershell
.\mvnw.cmd -pl klab.services.runtime -am '-Dtest=org/integratedmodelling/klab/services/runtime/**/*Test,TemporalProcessIntegrationTest,SettingsImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

On Unix use `./mvnw` with the same quoted arguments. Inspect the Surefire reports under
`klab.services.runtime/target/surefire-reports` and `klab.core.common/target/surefire-reports`.
The tests require no Python installation.

These scenarios establish admission and lifecycle behavior, not an HPC speedup. For workload
tuning, compare repeated runs with recorded shard layout, data size, limit and hardware;
measure wall time and actual process memory alongside the activity timing sums. Live-stack
acceptance and representative production throughput require the deployment's services,
worldview and components.
