# Runtime shard admission: local PR preparation

This is a local preparation record, outside the contribution worktree.

Published as draft PR **#80**:
https://github.com/integratedmodelling/klab-services/pull/80

Targets `integratedmodelling/klab-services:develop` from
`Dobbes/klab-services:feature/bounded-shard-execution`.
The published PR adds an explicit review-focus request for feedback on the admission
boundary and synchronous nested quality submissions.

## Proposed title

Runtime: add shared execution limits for compute-intensive workloads

## Proposed PR body

### Motivation

As k.LAB composes larger scientific workflows, it needs control over how much computational
work runs at once on the available hardware. This PR addresses that resource-management
aspect of HPC in the existing local Runtime: operators can bound active quality-shard
computations across observation requests, independently of how the data is partitioned.

Each contextualizer currently submits shard tasks to its own virtual-thread executor.
Several observations can therefore schedule many CPU-intensive or I/O-heavy component
invocations concurrently without a shared Runtime-level cap. Virtual threads make that
concurrency inexpensive to schedule, but do not bound the resources consumed by the
components themselves.

For example, four observations with sixteen shards each can offer sixty-four tasks for
execution. Configuring a shared limit of eight lets at most eight quality-shard invocations
run at once across those requests, while the remaining tasks wait. Operators can tune this
limit to the workload and hardware, adjust it while work is running, and cancel queued work.
The recorded queue and execution times provide evidence for that tuning.

This provides a resource-aware execution control for larger scientific workloads through
the existing computation paths. The default remains unrestricted, so adoption is opt-in.

### Behavior

- Add `MAX_CONCURRENT_SHARD_TASKS` through the existing settings API. The default, `0`,
  preserves unrestricted admission; positive values enable a per-Runtime cap.
- Share one controller across compiled/restored executors, covering quality initialization
  and temporal scalar partitions, including inline process assignments.
- Admit queued tasks in FIFO arrival order. Support live limit changes: increases admit
  more work, decreases let running tasks drain, and zero removes the cap.
- Check cancellation while queued, preserve interrupt status, and release capacity after
  completion or failure. Runtime shutdown rejects queued and new admissions.
- Keep dependency orchestration and non-quality instantiation outside admission.
- Record task counts and aggregate queue/execution time in existing activity metadata,
  following the transaction-owned storage-read evidence pattern.

Example:

```java
runtime.settings().set(Setting.MAX_CONCURRENT_SHARD_TASKS, 4).get();
```

Configuration, lifecycle details and executable scenarios are documented in
`docs/SHARD_EXECUTION.md`, linked from the storage and testing guides.

### Execution boundary

The cap governs active quality-shard invocations. Scanner planning/allocation and queued
storage resources remain outside that bound. Running components retain cooperative
cancellation, and existing initialization/temporal publication semantics are preserved.

Declared dependencies and non-quality orchestration can execute with a limit of one.
A quality component that synchronously submits and waits for additional same-runtime
quality work can exhaust the slots; reentrant scheduling is outside this change.

Activity timings are summed task time, not wall time. No throughput speedup is claimed.

### Validation

Verified on Windows 11 amd64, Temurin JDK 21.0.12.1+1 and Maven wrapper 3.9.5, against
`develop` at `721f37365` plus this contribution:

```powershell
.\mvnw.cmd -q -pl klab.services.runtime -am '-Dtest=org/integratedmodelling/klab/services/runtime/**/*Test,TemporalProcessIntegrationTest,SettingsImplTest,StackImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

**209 passed, 11 skipped, zero failures or errors.** All 24 new cases ran successfully:
12 controller/configuration cases, 11 runtime/storage integration cases and one
persisted-setting case.

The optional skips are the ten `SpatialLifecycleLiveTest` cases requiring `klab.test.neo4j`
and one generator-source check requiring `classifier.generator.source`.

The scenarios cover shared capacity across observation scopes, FIFO waiting, live
configuration, cancellation/interruption, failure recovery, input-lease cleanup, restored
dataflow ownership, temporal execution and storage readback. Integration tests use real
executors, reflected named-scanner binding and storage buffers, with service/registry/graph
infrastructure mocked following existing repository fixtures.

A narrated, assertion-backed walkthrough is available with:

```powershell
.\mvnw.cmd -q -pl klab.services.runtime -am '-Dtest=ShardExecutionIntegrationTest#demonstratesSharedLimitCancellationAndStoredResults' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

It shows two four-shard requests sharing two slots, cancellation of one queued request,
live limit changes, verified stored output and successful subsequent execution. Controlled
latches make the scheduling states observable; this is not a performance benchmark.

The selection also includes existing temporal scheduler/write-set, embedded persistence,
settings and distribution tests. Full-repository, live service-stack and Unix testing were
not performed.

### Fixture compatibility

Update stale function-lookup mocks in `OccurrenceExecutorTest`, `ClassificationExecutionTest`,
`MemberCharacterizerExecutorTest` and `MemberClassifierExecutorTest` to the scope-aware
overload. Their failures were reproduced before correction; existing assertions are retained.
Other executor fixtures are adapted to supply the explicit runtime-owned controller.

## Maintainer-perspective review

### Why this is a functional contribution

The operator setting changes admission of existing production computations. It does not
depend on a new scientific algorithm, a Python transport, or a new extension framework.
The ownership boundary is the existing Runtime service; the publication boundary remains
the existing executor/transaction lifecycle. Dependencies continue to be resolved before
quality execution. The canonical documentation includes specific executable examples.

### Deliberate boundaries to review

- Default `0` avoids silently changing deployed concurrency. A positive setting is opt-in.
- FIFO is by task, not equal-share scheduling among contexts/users.
- Planning/opening scanners still precedes admission; a queued request can retain storage
  resources. This is not a memory-budget implementation.
- Running Java/native components remain cooperatively cancellable; shutdown admission
  closure is not forced thread termination.
- A quality component must not synchronously wait for additional same-runtime admitted
  quality work while holding a slot. Such components need declared dataflow dependencies
  or an explicit future design for reentrant scheduling. The ordinary non-quality
  instantiation/dependency route is outside the cap and has a limit-one regression case.
- Activity timing is summed task time, not wall time or profiling of CPU/disk separately.
- Runtime constructor signatures changed internally to make controller ownership explicit;
  no new public core extension interface was introduced.

Before choosing deployment defaults, confirm component workload patterns and measure
representative concurrency/latency/resource tradeoffs. No maintainer endorsement or
performance gain is claimed by this preparation record.

## Local workspace state

- Contribution: `C:\Users\lumsd\Downloads\k1\klab-bounded-shards`
- Branch: `feature/bounded-shard-execution`, based on `origin/develop` at `721f37365`;
  contribution HEAD is `9d20023424454d8b09681221c826f0fb70cf89fd`.
- Original Python prototype and IDE changes remain in `klab-services` on its original branch.
- Test-generated `extensions.idx`/`jte-classes` artifacts were inspected and removed from
  the contribution worktree; Surefire/build outputs remain under ignored `target` directories.
- Committed and pushed to `Dobbes/klab-services`; draft PR #80 is open. The contribution
  worktree is clean. GitHub reported `MERGEABLE` when checked after creation; no status
  checks were listed yet.

Published commits:

1. `936e4655f` — Fix scope-aware function lookup mocks in runtime tests.
2. `9d2002342` — Add runtime-wide admission limits for quality shard execution.

All 26 published file paths were checked against the reviewed base-branch diff; the
published head matches the local commit. The original Python/IDE work is outside this PR.
