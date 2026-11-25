# Profiling resolution and contextualization

Use the sequential `klab.staging.vxii.testsuite` as an end-to-end workload and one non-trivial model as a shorter feedback loop. Keep the suite JSON, synchronized service logs and JVM recordings together for each run.

## Establish a baseline

1. Record the build revisions of Runtime, Resolver, Reasoner and Resources, the JDK and heap settings, the Resources providers, the fixture and staging project revisions, and the Reasoner's accepted semantic session, revision and fingerprint.
2. Run the last known good build and the candidate on the same machine, with the same geometry, providers, fixtures and test order. Wait for the services and catalog to become ready. Measure the first run after restart and repeat on the same JVMs in fresh contexts. Compare medians and ranges from three warm runs when feasible. Record catalog reconciliation or worldview publication during the run.
3. Compare all 28 outcomes and the elapsed time of each action. Use `storage_views` as a simple control, `select_multiple_families` and `select_deterministic_four_families` for multi-predicate resolution, and `select_family_rollback` and `select_consumer_rollback` for retries and model dependencies. Include the terminal outcome of slow resolution attempts. A run that ends with a different plan or a `DEFERRED` result answers a different question about performance.

```powershell
$report = Get-Content ./klab.staging.vxii.testsuite.json -Raw | ConvertFrom-Json
$report.children | ForEach-Object {
    if ($_.delegate) { $_.delegate } else { $_ }
} | Where-Object type -eq test | ForEach-Object {
    [pscustomobject]@{
        Test = $_.urn
        Passed = $_.outcome
        Seconds = [Math]::Round(($_.end - $_.start) / 1000, 2)
        Started = [DateTimeOffset]::FromUnixTimeMilliseconds($_.start).ToLocalTime()
    }
} | Sort-Object Seconds -Descending | Format-Table -AutoSize
```

## Attribute elapsed time

Align service logs with the action timestamps. Where available, join records using the context, cycle, transaction, observation or activity ID. Run a representative action on its own when timestamps cannot distinguish overlapping requests.

| Phase | Evidence | Measure |
| --- | --- | --- |
| Runtime submission and admission | `Submission preparation timing` and `Resolution cycle admission timing` | Queue time, semantic lease preparation, binding and early terminal outcomes. |
| Reasoning and strategy construction | Reasoner `Semantic operation timing` for `cycle.resolving`, `cycle.buildObservable`, `cycle.computeObservationStrategies` and `cycle.resolveObservable` | Actor wait, argument binding, execution and encoding. Sample observable building, strategy selection, setup, lowering and OWL closure separately. |
| Model discovery and ranking | Resolver `Model query timing` | Resources discovery, mapping source to model, filtering, admission/ranking and candidate counts before and after filters. Inspect incomplete supplier diagnostics when there are zero results. |
| Recursive planning | Resolver `Resolution attempt timing` | Semantic calls and model queries per attempt. Check expensive dependency expansions, coverage searches and rejected candidates as well as the final plan. |
| Validation and dependency proofs | Reasoner `documentValidation`, `cycle.validateDocument` and `cycle.semanticDependencies` | Actual validator runs, proof rechecks and cache hits. Compare repeated requests for the same parsed document, executable context meaning and preparation flags. |
| Contextualization and commit | Runtime samples of `CompiledDataflow`, `CharacterizationLifecycle`, the scheduler and the graph/storage backend | Initialization, actuator execution, native discovery, graph and storage I/O, commit, rollback and follow-on work. Close the interval at client completion to include transport and polling. |

The slow-operation logs omit short calls. Resolver `semanticTime` includes the retained Reasoner transport and covers calls on the resolution thread. Reasoner `actorWait` measures time waiting for the actor monitor. Reasoner `execution` can include external calls, so inspect CPU and socket samples before attributing it to inference. The inner `documentValidation` timer records the full validator path, including dependency-proof work. A successful proof-cache hit returns before this timer is logged. The outer `cycle.validateDocument` can include a hit and can also enclose the inner timer. Count a nested pair once. Totals from concurrent requests measure work. Use the critical path to explain wall time.

Take before-and-after Reasoner status snapshots from the same accepted session. Use deltas in `semantic.caches` for concept, observable, syntax, resolving-head, strategy-literal and OWL cache activity. In `semantic.validation.sourceProofs`, compare `hits` and `misses` for static source proofs and `executableProofHits` and `executableValidations` for executable validation. When investigating builder reuse, compare the base, ordered operations and semantic revision. For strategy work, compare the request observable and context and look for repeatedly declared closed literals. Capture a representative baseline and candidate comparison for reasoning changes. Check equivalent plans and receipts as well as timing, and exercise cache invalidation after a source, worldview, strategy or authority change.

## Record JVM profiles

Run from the account that owns the service JVMs. Identify PIDs with `jcmd -l`. Create a recording directory. Start matching captures during a slow action in Reasoner and Resolver. Include Runtime when admission, graph access or contextualization dominates. Use the actual PIDs in place of the PowerShell variables below.

```powershell
jcmd $reasonerPid JFR.start name=vxii-reasoner settings=profile duration=3m filename=C:/temp/vxii-reasoner.jfr
jcmd $resolverPid JFR.start name=vxii-resolver settings=profile duration=3m filename=C:/temp/vxii-resolver.jfr
jcmd $runtimePid JFR.start name=vxii-runtime settings=profile duration=3m filename=C:/temp/vxii-runtime.jfr
jfr summary C:/temp/vxii-reasoner.jfr
jfr print --events jdk.ExecutionSample,jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.SocketRead,jdk.GarbageCollection C:/temp/vxii-reasoner.jfr
```

Use execution samples to find expensive parsing, building, matching and inference. Match monitor wait stacks to the operation holding the actor where possible. Use socket reads and thread parks to identify Resources transfers and Runtime callbacks. Check allocation and garbage collection during the same windows. Use a separate unprofiled run for timing comparisons.

## November 23, 2026 regression

The supplied report ran for 85 minutes 25 seconds with 24 of 28 tests passing. The earlier run of approximately 20 minutes is a reference from the same machine. A matched earlier report and corresponding service logs are needed to assign the increase to particular phases.

| Test | Evidence |
| --- | --- |
| `selected_model_input` | The Reasoner rejected cycle admission with HTTP 403 when the scope carried a focus without an owned observation ID. Admission requires the authenticated context and owning Runtime. |
| `select_known_different` | Member characterization failed after Resolver returned `DEFERRED` for `earth:Terrestrial of earth:Region`. Model discovery took 10.233 seconds, near the default 10-second candidate supplier deadline. |
| `select_partial_support` | The first member snapshot received a result without a committed collective producer. Resources logged a response serialization error in the same interval. The corresponding request still needs to be identified. |
| `select_family_rollback` | The expected failure and unchanged-state checks passed. The retry returned without a committed selected result. Resolver logged deferred Region child resolutions during the retry. |

Among the slow entries completed from 08:17 through 09:41 local time, Reasoner logged roughly 536 seconds executing `cycle.resolving`, 128 seconds executing `cycle.buildObservable` and 90 seconds executing `cycle.computeObservationStrategies`. The same entries show 165 seconds of actor wait and roughly one second of execution for `cycle.resolveObservable`. The `documentValidation` entries record about 384 seconds of execution. Use these thresholded, overlapping measurements to choose capture windows for the baseline comparison.

## Collective selection

Use the first completed selection assertion to bound initial execution and the first-to-repeated assertion interval to measure replay. Inside initial execution, record the root plan, native discovery, attribution staging, the first and last characterization resolutions, and final evidence collection. Count characterization requests by resolution outcome. A complete `NO_MODEL` result still costs a child resolution, and `CharacterizationLifecycle` waits for each one before moving to the next.

### Live rerun on November 23, 2026

The rebuilt stack shared catalog replies across member resolutions within an authenticated cycle, transferred each namespace source once per supplier reply, and limited generalized head checks to outputs offered by the catalog. The three actions ran sequentially in fresh contexts after the restarted stack had accepted the worldview. The comparison uses the supplied complete-suite report. Cache warmth and generated members differ between runs.

| Action | Supplied suite | Rebuilt stack |
| --- | --- | --- |
| `select_two_role_families` | Failed at 40.781 s, 0 of 1 assertions passed | Passed in 634.017 s, 3 of 3 assertions passed |
| `select_multiple_families` | Passed in 708.494 s, 3 of 3 assertions passed | Passed in 925.516 s, 3 of 3 assertions passed |
| `select_known_different` | Failed at 31.099 s, 0 of 1 assertions passed | Passed in 86.965 s, 5 of 5 assertions passed |

Resolver reported five deferred native Region resolutions for `select_two_role_families` and two for `select_known_different` in the supplied run. The live reruns had none. The latter passed its initial viability assertion in 25.393 seconds and went on to check the empty selection and unchanged native state. The two earlier runs ended at their first failed assertion.

The `select_multiple_families` wall time increased by 217 seconds. The root plan took 99 seconds inside Resolver, up from 24 seconds. Runtime and Resolver spent more time reaching that plan and starting member characterization. The 55 original characterization requests and 63 rebuilt requests all returned `NO_MODEL`. Their first-to-last completion interval fell from 514 to 489 seconds. Their logged time inside Resolver fell from 365 to 213 seconds. The interval from the last member resolution to the first assertion grew from 73 to 113 seconds. The interval from the first assertion to the end, including replay, fell from 58 to 53 seconds.

Reasoner logged 36 slow `cycle.resolving` calls in the supplied `select_multiple_families` run, with 222 seconds of execution. The rebuilt run logged zero slow full expansions and one slow `cycle.matchingResolutionHeads` call, with 2.7 seconds of execution. In the same windows, slow observable build execution rose from 46 to 127 seconds and slow document validation execution rose from 4 to 89 seconds. These are thresholded service totals. Logs in this run give neither a per-catalog cache hit count nor the source identities of all slow validations. Use fixed fixtures, repeated warm runs and cache hit, miss and source fingerprints to assign end-to-end changes to individual optimizations.

Use these boundaries to assess reuse across services.

- **Runtime evidence.** An inspection rebuilds the native view, reads member classifications and inherits evidence from source activities. A replay thread sample in `select_two_role_families` was waiting on Reasoner capabilities during evidence inheritance. The collector now memoizes successful checks of identical authority-free proofs for the same scope. Proofs with external authority dependencies are rechecked on every inheritance. The final checks still compare semantic identity, graph revision and transaction state. If distinct proofs dominate, batch the dependency requests and verify each returned witness against its request.
- **Runtime graph reads.** Runtime reads the cohort's committed `CLASSIFIED` and `CHARACTERIZED` activities in one Neo4j query. The query uses the same context visibility and current-member rules as the individual reads and retrieves shared activities once. Runtime also reads owned staged activities from the transaction ancestry.
- **Runtime attribution staging.** A `select_multiple_families` sample between characterization groups was blocked in retained Reasoner `satisfiable` while preparing a member attribution. Measure repeated semantic inputs across members. Deduplicate pure checks on the same concept and pinned semantic generation inside the batch, with per-member support, events and publication checks still applied.
- **Resolver and Resources.** Measure payload bytes and supplier calls separately from decoding, projection and filtering. Resolver shares detached authority-free source projections within the authenticated cycle using the exact supplier descriptor and source bytes. Visibility, candidate matching, spatial checks and executable admission still run for each request. Resources can reuse serialized namespace bytes while reevaluating project access for each catalog request.

### Warm comparison after the Runtime changes

On November 23, 2026, the three actions ran on the baseline and patched Runtime with the same Ruaha support, one fixed-geometry Region, and fixed classifier outputs. The benchmark used the original assertions and a private fixture namespace. Each version ran one complete warm-up round followed by two measured rounds in fresh contexts. Only Runtime restarted between versions. Reasoner, Resolver, Resources and the graph database stayed up. Local build and test work overlapped some runs.

| Action | Baseline warm runs (s) | Patched warm runs (s) | Median wall time |
| --- | --- | --- | --- |
| `select_two_role_families` | 57.742, 35.903 | 39.315, 38.419 | 46.82 s to 38.87 s (17% lower) |
| `select_multiple_families` | 76.861, 98.718 | 54.731, 49.297 | 87.79 s to 52.01 s (41% lower) |
| `select_known_different` | 29.264, 39.978 | 21.357, 19.788 | 34.62 s to 20.57 s (41% lower) |

Every run passed. The member characterization outcomes matched across runs. The first-to-repeated assertion interval for `select_multiple_families` fell from a median of 16.31 to 10.77 seconds. The paired changes and the single-member fixture limit what these samples say about each optimization and the full randomized suite.

### Full-suite rerun on the updated stack

The original sequential suite ran on the existing live services from 14:36 to 15:08 local time on November 23, 2026. This run used the suite's normal fixtures. It passed all 28 actions in 31m 39s. The supplied full-suite report passed 26 of 28 in 55m 29s. Total elapsed time fell by 23m 50s (43%). The two previous failures passed. `select_two_role_families` completed in 73.799 seconds and `select_known_different` in 66.527 seconds.

| `select_multiple_families` measurement | Supplied full suite | Updated full suite |
| --- | --- | --- |
| Action elapsed time | 708.494 s | 220.341 s |
| Root planning inside Resolver | 23.660 s | 8.855 s |
| Non-overlapping elapsed time covered by logged Resolver attempts | 396.808 s across 69 attempts | 54.727 s across 39 attempts |
| First-to-last member characterization completion | 514.348 s | 140.590 s |
| Slow Reasoner document validation execution | 3.708 s across 2 entries | 0 entries |
| Last characterization completion to first assertion | 72.816 s | 34.025 s |
| First-to-repeated selection assertion | 56.940 s | 16.855 s |

Both actions completed 55 member characterization requests with `NO_MODEL`. The updated run also had zero slow `cycle.validateDocument`, `cycle.resolving` and `cycle.matchingResolutionHeads` entries. The supplied run logged 222 seconds executing slow `cycle.resolving` calls. Observable building went from 46 to 4 seconds of logged execution, and strategy computation from 37 to 5 seconds. These service timings use a one-second elapsed threshold, and Resolver, Reasoner and characterization intervals overlap. Replay is measured from assertion boundaries and includes the native snapshot, repeat submission and repeated check. A thread snapshot during the repeated check showed Runtime waiting for Reasoner capabilities while revalidating the selection binding.

### Profiling member characterization

Set the JVM property `-Dklab.profile.characterization=true` on Resolver and Runtime to capture every characterization request, including calls below the usual one-second threshold. Join Resolver's model phase, attempt and job completion entries by worker within the test window. The model phase separates discovery, source mapping, filtering and admission, and identifies the retained Reasoner calls within mapping and filtering. Runtime logs preparation, resolver wait, plan application and commit for each completed child. Resolver wait includes the Resolver attempt, transport, and work outside the compiler. Add the per-child timings only within the same service to avoid double counting.

The instrumented baseline for `select_multiple_families` completed 47 characterization requests. Source-to-model mapping took 13.668 seconds, including 11.349 seconds on 1,269 Reasoner calls. Filtering took 5.567 seconds, including 5.544 seconds on 470 Reasoner calls. Resolver made 1,786 retained Reasoner calls in total, taking 36.263 seconds of 49.196 seconds inside the compiler. The earlier 55-request full-suite run predates the opt-in timers.

Resolver now shares the unadmitted projection across sibling attempts after a complete authority-free proof over the source and resolved semantic reads. The cache is bounded, and each consumer receives a detached model. Incomplete, unavailable and authority-backed proofs leave projection local to the attempt.

| Warm characterization average per request | Timing-only baseline | With projection reuse |
| --- | --- | --- |
| Source-to-model mapping | 291 ms | 5 ms |
| Retained Reasoner calls in mapping | 27 | 0 |
| Total retained Reasoner calls | 38 | 11 |
| Total retained Reasoner time | 772 ms | 592 ms |
| Resolver compiler time | 1,047 ms | 866 ms |

Both timed actions passed, with 47 and 123 characterization requests respectively. The original Region generator varies its subdivision counts and classifier results. First-to-last member completion was 135.090 and 349.994 seconds, and total action time was 212.254 and 518.375 seconds. Use the per-request phase comparison for this optimization. A fixed multi-member workload is needed to isolate the effect on action wall time.

### Batched candidate filtering on a fixed cohort

Resolver groups the eligible output concepts by model and sends them with the requested concept in one retained Reasoner call. The retained actor computes requested and output cores and checks exact and generalized matches. Resolver still applies lexical visibility, spatial constraints and executable admission. Reasoner advertises the batch in its extensible status metadata so older capabilities readers keep their existing wire contract. Missing batch support selects the individual-call path.

The benchmark adapted `select_multiple_families` to use 13 deterministic, non-overlapping Regions within the same Ruaha support and fixed classifier outputs. The testcase kept its original assertions and checked the fixture's discovery count. Both versions ran one excluded warm-up and two timed trials in fresh contexts. Every trial passed all four assertions and recorded exactly 55 `NO_MODEL` characterizations.

| Measurement | Individual filtering calls | Batched filtering |
| --- | --- | --- |
| Timed action wall times | 209.507 s, 162.920 s | 178.577 s, 125.446 s |
| Median action wall time | 186.214 s | 152.012 s |
| Filtering calls per timed trial | 550 | 55 |
| Median time in candidate filtering per timed trial | 8.271 s | 1.100 s |
| Median time inside Resolver across all 55 characterizations | 34.224 s | 21.153 s |
| Median first-to-last member completion interval | 87.282 s | 64.503 s |

The median wall time fell by 34.2 seconds (18%). The two versions' wall-time ranges overlap, and Reasoner and Resolver restarted for deployment. Use the per-phase measurements and the 90% reduction in filtering calls to distinguish the reproducible transport reduction from variation in overall run time.

### November 25, 2026 full-suite comparison

The November 25 suite passed all 28 tests in 54m 06.128s. The November 23 full-suite baseline
passed all 28 in 31m 39.302s. The increase was 22m 26.826s (70.9%), with every action slower.
The baseline predates the fixed-cohort batching trial above. The November 25 services restarted
shortly before the run, and the worldview and generated member work differ across the two runs.

| Action | November 23 (s) | November 25 (s) | Increase (s) |
| --- | ---: | ---: | ---: |
| `select_multiple_families` | 220.341 | 442.662 | 222.321 |
| `select_consumer_rollback` | 81.135 | 239.064 | 157.929 |
| `select_partial_support` | 76.699 | 204.572 | 127.873 |
| `select_two_role_families` | 73.799 | 157.873 | 84.074 |
| `select_native_operation_rollback` | 101.980 | 183.394 | 81.414 |
| `select_family_rollback` | 173.756 | 250.126 | 76.370 |

| `select_multiple_families` wall-clock interval | November 23 (s) | November 25 (s) |
| --- | ---: | ---: |
| Test start to root plan completion | 13.480 | 56.601 |
| Root plan to first characterization completion | 14.938 | 20.929 |
| First to last characterization completion | 140.590 (55 requests) | 310.098 (59 requests) |
| Last characterization completion to first assertion | 34.025 | 35.600 |
| First selection assertion to repeated selection assertion | 16.855 | 19.179 |

In the same action, logged slow model queries spent 13.256s and 3.212s in candidate filtering
and 28.075s and 40.925s mapping source to models. Fourteen baseline queries reported 37 retained
calls, and 32 current queries reported 28. That fits 27 mapping calls plus 10 individual filters
in the baseline or one batched filter in the current run. Reasoner recorded the batch endpoint in
other actions. The fixed-cohort trial showed zero mapping calls per characterization with a warm
projection. Projection reuse still exists in Resolver, but the live aggregate logs lack the
hit, miss and rejection reasons needed to identify which eligibility condition prevented reuse.

| Reasoner execution in slow records | Full suite November 23 (s) | Full suite November 25 (s) | Four-family action November 23 (s) | Four-family action November 25 (s) |
| --- | ---: | ---: | ---: | ---: |
| Full document-validation path including dependency proofs | 77.713 | 220.192 | No slow record | 6.277 |
| Observable building | 5.877 | 128.945 | 4.128 | 64.667 |
| Observation strategy computation | 15.959 | 66.092 | 4.590 | 46.436 |
| Semantic dependencies | 37.447 | 86.762 | 6.995 | 23.904 |
| Consequence rule lookup | 6.612 | 36.559 | No slow record | 3.996 |

These are thresholded execution totals. They can overlap Resolver work and omit short calls.
The November 23 run had 33 slow full validations, all paired with retained-cycle calls.
The November 25 run had 68, with 43 paired with retained-cycle calls (133.503s) and 25 other
calls (86.689s). Another 13 slow validations ran in the two minutes before the suite (49.926s).
The November 25 log has no per-document identity or validation mode for these records, so it
cannot show how many repeated the same source in the same validation context.
Consequence rule lookups measure one part of the engine. The logs lack the engine's elapsed
time and causal linkage for dependent submissions, so the larger sum does not establish how
much of the suite increase consequences caused.

Use the same warmed services and accepted worldview for alternating fixed-cohort runs, with
fresh contexts and identical discovery and classifier outputs. Compare ordinary submits with
`ctx.submit(observable !consequences)` on the root submissions under test. Keep consequences
enabled in Runtime settings for this comparison. Capture per-characterization timings, source
validation proof counters, and Runtime consequence worker samples. Track projection lookup
disposition by source hash and semantic binding to find out whether a missing shared session,
descriptor change, incomplete proof or authority dependency explains remapping. Check whether
repeated builder and strategy inputs can be reused within the pinned semantic generation before
adding caching. Separate Runtime's child preparation and transport waits from time inside
Resolver, since the characterization loop waits for each child in succession.

### Ontology saves and startup

The accepted Reasoner has a proof cache for `.kim` namespaces. Executable proofs also depend
on the execution-context semantics, preparation options, and the actor's derived-refinement
generation. Those inputs can change during a suite with identical files. On the November 25
service, the cumulative counters showed 53 executable proof hits and 65 executable validations.
Take counter deltas around a fixed-fixture run to quantify reuse independently of startup and
other clients.

A changed ontology save can submit a whole worldview candidate. The candidate compiles and
validates the captured ontologies and strategies, then checks the final OWL consistency and
classification. The accepted actor now retains its validation results for `.kwv` documents.
It reuses those results for matching source and parsed syntax at the same actor refinement
generation, and rebuilds the dependency witness for the requesting scope.
The November 25 Resources workspace records an `ecology.kwv` save at 10:43:06 that added an
entry-only LAND ecosystem binding. The Reasoner logged document validations of 37.803s and
47.606s in that period. Their records omit document identity, so they cannot be attributed to
that file. The save also came after the repeated startup failures described below.

Use `-Dklab.reasoner.traceCandidate=true` to time provider preparation, actor loading, document
validation and final classification. Compare the candidate generation and source hashes across
a no-op save and a changed save. `-Dklab.traceDocumentValidation=true` logs editor validation
requests on the Reasoner document endpoint with source identity.

Set `-Dklab.profile.documentValidation=true` on Reasoner to record every validation request
that reaches an accepted actor, including the retained Resolver path and proof hits. Match
`DOCUMENT_VALIDATION_PROFILE` records to the suite window. Group by document, source hash,
execution-context hash, semantic session and revision, and refinement generation. Compare the
exact proof key and requesting principal hash inside each group. Generated one-model
namespaces can omit the original source text. For those requests the proof key identifies
the parsed projection and its validation options. A request without either hash has no
exact-repeat classification. Report how many repetitions hit a proof and how many ran
the full validator again. For the strongest redundancy signal, count repeated full
validations that produce a complete, authority-free witness after an earlier equivalent
result with the same stable refinement generation.

### November 25 profiled suite

The suite passed all 28 tests in 51m 26.390s. The Reasoner recorded 190 namespace validation
requests reaching the accepted actor. All records used the same accepted revision and
started and finished at refinement generation 2.

| Retained-cycle requests | Saved source text | Generated model projection | Total |
| --- | ---: | ---: | ---: |
| Requests | 41 | 118 | 159 |
| Distinct exact groups | 11 | 65 | 76 |
| Repeated requests served by the proof cache | 30 | 53 | 83 |
| Repeated full validations | 0 | 0 | 0 |

Retained-cycle requests ran the full validator 69 times and reused a proof 90 times. Their
summed request times were 119.401s and 9.973s respectively. The Reasoner counters for the whole
suite window increased by 38 static proof hits and 53 executable proof hits, matching the
91 hits in the trace. Of the 31 requests outside retained cycles, 24 source-only calls used
generated namespaces without source text or an exact proof key. Those calls account for
42.542s of request time and remain unclassified for redundancy.

In a separate smoke test on the rebuilt Reasoner, both saved `land.kwv` requests reused the
accepted result. The actor work excluding monitor wait took 0.116s and 0.270s. Both
`ecology.kwv` requests took the full-validation path. A private reproduction of that miss
stopped at an external PubChem lookup timeout before the candidate was built.

Earlier November 25 startup logs show a different problem. From 10:10:39 to 10:29:21, one
Reasoner process logged 17 failures preparing the same authority bootstrap input. The failing
frame is `LandAuthority.configure` at the semantic-access binding check. The HTTP server
started at 10:07:21, and those retries delayed worldview readiness. The process used for the
staging suite recorded a single provider bootstrap sequence and zero such failures. Measure
HTTP startup and accepted-worldview readiness independently.
