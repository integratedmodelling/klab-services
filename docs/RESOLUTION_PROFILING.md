# Profiling resolution and contextualization

Use the sequential `klab.staging.vxii.testsuite` as an end-to-end workload and one non-trivial model as a shorter feedback loop. Keep the suite JSON, synchronized service logs and JVM recordings together for each run.

The [November 26 investigation](#november-26-2026-full-stack-investigation) includes the deployed-suite runner, a fixed thirteen-member workload and the validation restart experiment.

## Checking changes for regressions

Choose the smallest check that exercises the changed behavior through its normal entry point.

| Change | Check | What to compare |
| --- | --- | --- |
| Reasoning, model mapping, member selection, submission or shared transport | Run the fixed 13-member selection against the reference and candidate distributions. Add a focused counter assertion when it can catch the same regression in the module tests. | Match the geometry digest and all four assertions. The reference did 55 characterizations, 69 `NO_MODEL` jobs, one resolved job and 440 mapping calls per trial. Explain increases in calls per unit of work even if the trial gets faster. |
| Publication, validation, fingerprints, authority providers or startup | Seed valid records, restart the same build with the same authorized inputs, validate again, then change one private source and repeat it. Exercise a genuine ontology edit when candidate construction changes. | Count full static visits, durable hits and provider bootstraps separately from executable admissions. An unchanged publication produced zero synthetic one-model visits. A changed dependency must invalidate the affected proof. Record cold and warm costs separately. |
| Metadata or serialization | Exercise the actual client and provider with the representative project. | Check the returned information, permission checks, response bytes and number of requests. Project-manifest and worldview-header responses should omit ontology statements on providers supporting the projection. |
| Cross-service or broad changes, release candidates, and periodic checks of the integrated branch | Run the canonical VXII suite with its original sources. | Compare all action outcomes and assertions, then per-action time, generated members or characterization children, and Resolver job outcomes. The November 26 reference passed 28 actions and 143 assertions. Random instantiators make a single full-suite time a poor merge threshold. |

For fixed timings, verify the deployed artifact and fixture hashes, use the same runner,
providers and profiling flags, and keep competing jobs off the machine. Exclude one warm-up,
then report the median and range of three fresh-context trials per build. Compare both to the
immediate predecessor and to a retained, versioned known-good build under comparable conditions.
The latter comparison catches a series of small regressions. The November 26 timings refer to
the measured distribution, which included working-tree changes.

Investigate a candidate warm median **more than 15% slower than either reference** as a
starting rule. Repeat reference, candidate and reference on the same host. Check call counts, bytes
and validation outcomes first, then profile the stage that grew. A reproduced slowdown or
unexplained increase in a deterministic counter needs a fix or a documented feature cost.
Keep the long-term reference until a deliberate update records the reason and comparable
before-and-after results. Calibrate the timing threshold as runner history accumulates.
[Run commands and fixture setup](#reproducing-the-measurements)

For CI, run deterministic work-count and reuse tests on code pull requests, including
shared-library and dependency changes. `ModelAdmissionReceiptTest`, `SourceValidationProofReuseTest`
and `AuthorityBootstrapTest` already exercise publication, restart and provider reuse.
A nightly canonical suite on a dedicated authenticated stack can catch interactions outside
those tests. Retain each suite report and its counts with the deployed revision and fixture hashes
so a regression can be traced back to the change that introduced it.

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

### Observable building and strategy computation

The retained Reasoner timing logger records calls with at least one second of elapsed time.
Its counts measure slow calls. Compare the full call counts and latency distributions whenever
both are available.

| Full-suite run | Resolver jobs | Member characterizations | Slow observable builds | Slow strategy computations |
| --- | ---: | ---: | ---: | ---: |
| November 23 | 818 | 430 | 3 | 10 |
| November 25 first run | 935 | 480 | 81 | 41 |
| November 25 validation-profile run | 887 | 480 | 33 | 26 |

In `select_multiple_families`, the characterization counts were 55, 59 and 67, and all
recorded characterization outcomes were `NO_MODEL`. Slow builds numbered 2, 44 and 12,
and slow strategy computations numbered 4, 26 and 18. In the first November 25 run, 43 of
44 slow builds and 25 of 26 slow strategy computations completed between the first and last
member characterization completions. This window contained no slow consequence-rule lookup.
Most of the increase in the recorded time for the two operations was in actor execution,
with little time attributed to waiting for the actor monitor.

Runtime builds an observable for each characterization before asking Resolver for a plan.
Resolver can compute strategies even when it ultimately reports `NO_MODEL`. That work
occurs for ordinary Region attribution in this test. The generated attribution mix varied
between runs. The slow records omit fast calls, caller identities and operation inputs,
which are needed to distinguish additional calls from calls crossing the timing threshold.

Set `-Dklab.profile.characterization=true` on Runtime and Resolver to count characterizations
and measure preparation, Resolver wait, candidate mapping and filtering. Capture Reasoner
JFR during the same action for CPU, allocation, monitor and I/O evidence. JFR can be started
with `jcmd` against the running JVM. The Reasoner's document-validation and candidate
publication flags cover different paths.

Set `-Dklab.profile.semanticOperations=true` on Reasoner, Runtime and Resolver for all calls to
`buildObservable` and `computeObservationStrategies` at the Reasoner API boundary, including
short calls. Join `SEMANTIC_OPERATION_CLIENT` and `SEMANTIC_OPERATION_REASONER` by `id`. The
caller record gives the call site and round-trip time. The Reasoner record gives the cycle,
semantic session and revision, refinement generation, input shape, payload and geometry hashes,
result, and phase timings. Local expression work within these calls contributes to execution time.

Group calls by operation, semantic session, revision, refinement generation and shape hash.
Use the argument and support hashes for a stricter comparison within each group. Repeated
signatures identify work to investigate for reuse. Observer, support and resolution scope can
still require independent results. Use a fixed cohort, compare warmed runs, and vary the
worldview and service build independently to attribute a regression.

The native testcase function `klab.tests.fixtures.fixed(members=13)` selects a deterministic
row of cells in normalized envelope coordinates. The four-family benchmark combines those
members with fixed classifier outputs and checks for 55 characterization completions. With
semantic profiling enabled, Runtime logs the ordered cell geometry digest as `FIXED_FIXTURE_GRID`.
Verify the digest, assertion outcomes and characterization count in every trial.

#### November 25 warmed fixed-cohort trace

The instrumented stack ran one excluded warm-up in 201.393 seconds, then two timed
trials in fresh contexts. All three trials passed four assertions, emitted the same 13-cell
geometry digest and completed exactly 55 `NO_MODEL` characterizations. Every Reasoner
record paired with its caller record. Both timed trials used the same accepted semantic
revision and refinement generation.

| Measurement | First timed trial | Second timed trial |
| --- | ---: | ---: |
| Action wall time | 159.867 s | 164.922 s |
| First-to-last characterization completion | 74.327 s | 69.813 s |
| Summed characterization wait on Resolver | 51.555 s | 51.329 s |
| Source-to-model mapping | 19.191 s | 17.491 s |
| Reasoner calls during source-to-model mapping | 1,485 in 15.713 s | 1,485 in 14.290 s |
| Candidate filtering | 55 calls in 0.819 s | 55 calls in 0.751 s |
| First-to-repeated selection assertion | 19.741 s | 25.471 s |

| Reasoner operation | Calls per trial | Distinct shapes | Distinct payload and support groups | Median actor execution | Median caller round trip | Summed caller round trips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `buildObservable` | 250 | 31 | 79 | 27.430 / 25.576 ms | 65.557 / 58.257 ms | 17.828 / 16.673 s |
| `computeObservationStrategies` | 75 | 15 | 75 | 3.364 / 3.065 ms | 37.769 / 31.940 ms | 3.142 / 3.182 s |

The shape counts matched exactly across trials. Each characterization issued one build
and one strategy call. For each operation those 55 calls had seven shapes and 55 distinct
payload and support groups. Across the whole action, selection admission and checks issued
135 builds, and attribution staging and characterization preparation issued 55 each. This
puts Runtime selection checks ahead of strategy computation for investigating reuse. Resolver
source-to-model mapping also remains a larger target than candidate filtering.

Every traced call in the warmed trials finished below the one-second Reasoner logging
threshold. No document validation reached that threshold in either timed trial. In two
earlier diagnostic attempts with the same grid, which stopped at a Runtime fixture proof
failure after characterization, the counts were fixed at 181 builds and 75 strategy calls.
Build actor execution fell from 52.554 seconds on the first attempt after Reasoner startup
to 7.104 seconds after warm-up. Strategy actor execution fell from 7.606 to 0.468 seconds.
The November 23 and 25 thresholded logs cannot establish how total call volume changed.

#### Runtime selection reuse and batched model mapping

Runtime reuses its first locally checked selection binding within an execution attempt or
selection query. Each check still matches the request, geometry, focus, observer and
semantic publication. A change in the retained actor's refinement or in the observable
payload triggers a new semantic capture. Resolver resolves each uncached model's output
and dependency ports, including their annotation lookups, in one retained Reasoner call.
It uses the reported semantic reads for the same authority-free projection proof.

The updated stack ran the same 13-cell fixture. The excluded warm-up took 442.048 seconds.
A burst of validations overlapped replay in the first timed trial, so a third timed trial
was added while the services were still warm. All four trials passed all four assertions,
matched the previous geometry digest, and completed 55 `NO_MODEL` characterizations each.

| Measurement | Previous timed trials | Updated timed trials |
| --- | --- | --- |
| Action wall time | 159.867 / 164.922 s | 261.750 / 177.325 / 196.481 s |
| Runtime selection builds per trial | 135 | 75 |
| Total observable builds per trial | 250 | 190 |
| Strategy computations per trial | 75 | 75 |
| Reasoner calls during source-to-model mapping | 1,485 | 440 |
| Source-to-model mapping time | 19.191 / 17.491 s | 11.694 / 10.638 / 13.289 s |
| Candidate discovery time | 12.582 / 11.464 s | 18.133 / 16.221 / 21.396 s |
| First-to-repeated selection assertion | 19.741 / 25.471 s | 107.856 / 25.897 / 29.133 s |

Selection build calls fell 44% and mapping calls fell 70%. All three full actions took
longer than the previous timed trials. In the first updated trial, the replay and inspection
interval overlapped 18 full Reasoner validations totaling 77.223 seconds. Two selection
builds spent a combined 13.779 seconds waiting for the actor in that interval. The validation
records identify the timing and execution path, with no source-level attribution for this
run. The second and third updated trials had zero logged validations over one second during
replay. In the third trial there were none during the entire action, and the longer
characterization interval accounted for most of the remaining wall-time difference.

#### Additional characterization time in the final timed trial

The 55 Runtime child timers pair in order with the 55 Resolver job completions. The
Resolver records give one model query per compiler attempt. Comparing the previous
164.922-second action to the updated 196.481-second action isolates the characterization
interval from its first child's preparation to the last child's commit. This interval
increased by 23.421 seconds.

| Characterization measurement | Previous | Updated | Change |
| --- | ---: | ---: | ---: |
| First preparation to final commit | 71.204 s | 94.625 s | +23.421 s |
| Runtime child preparation | 3.136 s | 5.851 s | +2.715 s |
| Time awaiting Resolver | 51.329 s | 63.365 s | +12.036 s |
| Time between timed children | 16.739 s | 25.409 s | +8.670 s |
| Inside Resolver compiler | 34.351 s | 44.628 s | +10.277 s |
| Candidate discovery within compiler | 11.464 s | 21.396 s | +9.932 s |
| Source-to-model mapping within compiler | 17.491 s | 13.289 s | −4.202 s |
| Candidate filtering within compiler | 0.751 s | 1.330 s | +0.579 s |
| Compiler time outside model query | 4.645 s | 8.613 s | +3.968 s |
| Resolver wait outside compiler | 16.978 s | 18.737 s | +1.759 s |

Apply, commit and admission round to zero at the timer resolution. Discovery covers the
supplier query and result merge, including any catalog reuse and local response processing.
These timings group those costs together. Retained Reasoner calls within the compiler fell
from 1,595 to 550 and their cumulative client time fell from 16.816 to 12.270 seconds.

Three classifier-family transitions before children 15, 29 and 42 accounted for 8.654
seconds of the 8.670-second increase between children. Runtime staged 41 observable builds
across the three transitions in each run. Their cumulative caller time increased from
2.724 to 4.704 seconds. The remaining 6.674 seconds of the increase within those transitions
fell outside the observable-build timers.

#### Replay validation provenance

The original 18 slow calls completed between 18:49:23.574 and 18:51:11.430 on November
25. They spent 77.168 seconds in the full validator and 0.055 seconds waiting for the
actor. The logs contain the direct HTTP timing records and omit source identity, caller
and refinement. The retained `cycle.validateDocument` timing was absent throughout the
burst. Those historical records establish the path and timing of the 18 calls.

A subsequent Reasoner-only restart enabled the existing document-validation profile.
Tomcat completion times and loopback socket ownership identified the requesting process.
The same fixed fixture passed four assertions with 55 characterizations. Its replay
recorded 12 full, direct, source-only validations in 31.411 summed seconds. All used
knowledge revision 1, began and ended at refinement 2, and had empty execution context.
Ten requests matched Resources, including all five requests of at least one second. The
two remaining short requests lacked a unique socket match.

| Namespace | Saved source hash prefix | Saved requests | Generated requests without source text |
| --- | --- | ---: | ---: |
| `staging.vxii.test.selection_native_dependency` | `bee1dce506a2` | 1 | 5 |
| `staging.vxii.test.selection` | `5b18fa483786` | 1 | 2 |
| `staging.vxii.test.stac.basic` | `9416c64f2318` | 1 | 2 |

The three saved hashes matched the corresponding workspace files, whose modification times
precede the original replay. Resources' catalog reconciliation fingerprints each namespace
with the Reasoner session. Restarting Reasoner changes that session and creates a new
actor-local proof cache, so an unchanged saved source can need a new publication admission.
Catalog descriptor inference also validates individual models as synthetic one-model
namespaces. The static proof cache rejects these source-less requests, which explains why
nine of the twelve reproduced validations carried neither a source hash nor a proof key.
The catalog reconciler yields between namespaces while retained cycles are active. The
unbound replay and inspection calls can therefore overlap resumed reconciliation.

## November 26, 2026 full-stack investigation

The November 26 measurement used the local four-service distribution built at 08:01, staging project
`7e26bd305d`, and IMOD `b6df082868`. The actual testcase file had SHA-256
`a52d097e2dbcf2407b85a3ae2e9c71ff58e98a8d559922c61c781ab120cc9319`.
The testcase already had a local modification in the staging checkout at the beginning of
the investigation. All canonical runs used those exact bytes.
The distribution advertised batched model ports and retained-refinement checks. Those changes were
present in the working tree. The difference between commits `cd3ab45dc` and `72c6e874e` added
project-review handling. Benchmark comparisons use the actual deployed observation paths.
The four local services used Java 21.0.6 and G1 with a 512 MiB initial heap. The Reasoner
and Resolver each had a 2 GiB maximum. Resources and Runtime each had a 4 GiB maximum.
The separate benchmark client and private ontology runner used Temurin 21.0.13.

### Full-suite result

| Canonical run | Suite wall time | Resolver `NO_MODEL` | Runtime characterization children | Passed |
| --- | ---: | ---: | ---: | ---: |
| November 23 reference | 31m 39.302s | 776 | — | 28 / 143 |
| November 25 reference | 51m 26.390s | 845 | — | 28 / 143 |
| November 26 starting distribution | 40m 43.264s | 757 | 386 | 28 / 143 |
| First optimized build | 30m 58.104s | 828 | 442 | 28 / 143 |
| Restarted with saved validation records | 25m 55.624s | 873 | 474 | 28 / 143 |
| Delivered build with worldview routing headers, fresh restart | **23m 45.336s** | **876** | **461** | **28 / 143** |

Each row passed all 28 actions and 143 assertions, with 38 resolved and four deferred
Resolver jobs. The 25m 55.624s run saved 14m 47.640s (36.3%) over its starting distribution and
used 49.6% less time than the November 25 reference. The November 26 runs used the same
unchanged project sources. Their stochastic instantiators produced different amounts of work.
The first optimized run had an early and a warm Flight Recorder window, as did the starting
run. The restarted builds used the lightweight timing flags. These are observed suite times.

The delivered build saved **16m 57.928s (41.7%)** against the starting distribution and
used 53.8% less time than the November 25 reference. Its 119 additional unsuccessful
Resolver jobs illustrate the workload variability. The 130.288 s difference between the
two optimized restarts also includes different member work and service/JVM conditions.
The separate saved-ontology measurements isolate the effect of the routing change.

The interval from first service process start to first action was 310.8, 223.3 and 261.9 s
on the first three November 26 runs. Those intervals include service startup, client setup, catalog
access and the driver's readiness checks. Measured from process start, the 25m 55.624s run completed
in 30m 17.5s, compared with 45m 54.0s on the starting distribution.
The delivered run reached its first action in 142.7 s and finished in 26m 08.0s from
process start.

<details>
<summary>All 28 actions on the starting distribution and both optimized restarts</summary>

| Action | Starting build (s) | First restart (s) | Released build (s) | NO_MODEL starting / first restart / released | Passed assertions per run |
| --- | ---: | ---: | ---: | ---: | ---: |
| `check_substantial_identity` | 79.503 | 12.819 | 9.681 | 1 / 1 / 1 | 3 |
| `resolve_quality` | 110.577 | 24.981 | 19.269 | 1 / 1 / 1 | 1 |
| `ns_resolution` | 230.747 | 111.779 | 73.745 | 25 / 9 / 12 | 7 |
| `storage_views` | 9.034 | 8.298 | 6.415 | 1 / 1 / 1 | 8 |
| `monthly_process` | 45.944 | 44.847 | 37.398 | 0 / 0 / 0 | 6 |
| `stream_relationships` | 18.227 | 19.099 | 13.076 | 13 / 23 / 13 | 3 |
| `classify_existing_regions` | 50.108 | 64.181 | 43.110 | 29 / 29 / 33 | 5 |
| `classify_concrete_self` | 82.065 | 50.860 | 27.537 | 39 / 41 / 33 | 3 |
| `select_realm` | 291.474 | 78.550 | 80.385 | 41 / 27 / 49 | 5 |
| `select_attribute` | 178.510 | 79.722 | 70.015 | 26 / 40 / 32 | 2 |
| `select_identity` | 143.605 | 49.409 | 58.581 | 24 / 16 / 22 | 2 |
| `select_role` | 56.281 | 47.719 | 56.486 | 24 / 28 / 30 | 2 |
| `select_two_role_families` | 38.445 | 67.488 | 66.862 | 12 / 45 / 39 | 3 |
| `select_multiple_families` | 189.112 | 163.125 | 183.976 | 49 / 79 / 89 | 3 |
| `select_deterministic_four_families` | 142.956 | 109.092 | 96.592 | 74 / 89 / 64 | 8 |
| `select_known_different` | 52.753 | 49.711 | 76.581 | 21 / 23 / 75 | 5 |
| `selected_model_input` | 61.680 | 44.960 | 45.938 | 45 / 29 / 29 | 4 |
| `select_partial_support` | 90.785 | 63.179 | 47.264 | 58 / 62 / 50 | 6 |
| `select_subtype` | 40.845 | 33.178 | 33.799 | 19 / 17 / 15 | 5 |
| `select_empty_native` | 14.117 | 10.222 | 10.340 | 1 / 1 / 1 | 8 |
| `select_missing_classifier` | 9.518 | 7.441 | 9.916 | 17 / 11 / 25 | 2 |
| `select_family_rollback` | 94.654 | 100.360 | 66.479 | 64 / 94 / 63 | 7 |
| `select_consumer_rollback` | 86.189 | 63.255 | 72.087 | 39 / 67 / 75 | 6 |
| `select_explicit_observer` | 64.462 | 63.714 | 44.028 | 26 / 42 / 28 | 3 |
| `classify_without_focus` | 63.326 | 49.228 | 38.446 | 29 / 23 / 17 | 6 |
| `select_native_operation_dependency` | 80.820 | 51.529 | 51.402 | 15 / 21 / 29 | 14 |
| `select_native_operation_rollback` | 113.111 | 83.246 | 81.799 | 62 / 52 / 48 | 10 |
| `observer_instantiation` | 4.347 | 3.571 | 4.084 | 2 / 2 / 2 | 6 |
| All | 2443.264 | 1555.624 | 1425.336 | 757 / 873 / 876 | 143 |

</details>

On the first optimized restart, `select_attribute` fell from 178.5 to 79.7 s while its
unsuccessful jobs increased from 26 to 40. `classify_existing_regions` rose from 50.1 to
64.2 s with 29 unsuccessful jobs on both runs. Equal job counts still leave differences in
the semantic payload, service contention and JVM warmth.

### Reproducing the measurements

Start the four local services and run the client as the owner of the staging project. Point
`$runtime` at the directory containing the Runtime distribution JARs. The client uses the deployed
project's testcase and its normal authenticated agent, waits for completion, and writes the full
test report with per-action times, outcomes and assertion counts. Each invocation needs a fresh
output directory.

```powershell
java -cp "$runtime/*" tools/validation/VxiiSuiteProfile.java results/full
java -cp "$runtime/*" tools/validation/VxiiSuiteProfile.java results/quality resolve_quality 3
```

For the controlled selection trial, temporarily place the supplied private
`tools/validation/vxii-fixed/selection_batch_benchmark.kim` in the staging project's
`src/staging/vxii/test` directory. Wait for Resources to publish the namespace. The matching
testcase discovers thirteen members, checks the four-family selection twice, and checks the native
snapshot across replay. Remove the temporary fixture after the trials. Run the complete suite with
the canonical project sources.

```powershell
java -cp "$runtime/*" tools/validation/VxiiSuiteProfile.java results/fixed select_multiple_families 4 tools/validation/vxii-fixed/testsuite.kactors
```

Compare two reports with the Resolver logs for their respective intervals. Run the comparison in the
service's timezone. The log switches can be repeated for rotations. The report also works with
the two JSON files alone.

```powershell
python tools/validation/compare_vxii_profiles.py results/before/run-1.json results/after/run-1.json --before-log before/resolver.log --after-log after/resolver.log
```

For timing, exclude the first fixed trial and retain every subsequent measurement. Record the
number of `Characterization child timing` events and their outcomes. Use
`-Dklab.profile.characterization=true` on Runtime and Resolver. The final build emits the
fixed grid digest with this flag and exposes mapping decisions in the Resolver status field
`model.projection.profile`. Enable `-Dklab.profile.documentValidation=true` on the Reasoner
to match source checks to the timed interval. The complete suite also
exercises random instantiators. November 23, November 25 and the original November 26 run passed
143 assertions each and logged 776, 845 and 757 Resolver `NO_MODEL` job outcomes respectively.
The number of resolved and deferred jobs was 38 and four on all three runs. A change in elapsed
time can therefore reflect both a change in work per job and a change in the number of jobs.

### Where unchanged-source validation came from

Resources makes its first catalog publication after the Reasoner accepts a worldview. A service
can be operational while this reconciliation is still working through the project. The original
November 26 run made 20 full direct validations of saved namespaces and 56 full direct
validations of generated single-model namespaces from startup through suite completion. They
occupied 187.1 and 129.0 summed seconds respectively. Of those calls, 15 and 49 completed during
the timed suite. The retained Resolver path made ten further full saved-namespace validations
during the suite, taking 46.3 summed seconds. Every one of the 85 repeated exact proof keys hit
the existing Reasoner cache. Nine saved namespace names had two keys for the same file hash,
actor and principal. A generated request without source text or a proof key cannot be grouped as
an exact repeat by namespace name alone.

The namespace visitor already checks the models in that namespace. Descriptor stores now consume
the result for the exact parsed model object inside the same caller's publication. A changed
parsed document produces a new validation request. A detached model outside publication receives
its own check. Runtime still admits the installed executable model against its actual observation
context and model dependencies.

Four valid staging namespaces had incomplete static footprints. Their parsed parameter maps
contained bare identifiers such as `adapter: stac` or `adapter: wcs`. The static visitor treats
these as literal names. The obligation collector now accounts for them in static validation and
keeps their executor footprint incomplete. All 21 project namespaces then had complete static
results. The disk partition uses the authenticated account, server, federation and membership
because an identity's credential ID changes at login.

A restart exposed a second difference between the catalog and the client. For the same
`staging.vxii.test.stac.basic` file, the served namespace had proof key `d9afbe32d1fc`.
Removing only its Resources service ID from the saved parsed bean reproduced the early catalog
key `6189c2b3ff81`. The static namespace visitor does not consult that routing field. The
static key now omits it. Executable and ontology keys retain it. The namespaces
`staging.vxii.test.events.basic` and `staging.vxii.test.authorities` each contained only a
name and a version. A complete static visitor with zero semantic reads now records that empty
dependency set. Replaying it still requires the same scoped accepted worldview and source.

The Reasoner stores successful, complete static results in its own
`semantic-candidates/validation-records` directory. The record contains the source hash, the
checksummed validation response, and a dependency witness. Its key covers the parsed document,
validator implementation, service, caller and accepted worldview. Set iteration order and document
timestamps are incidental. Statement order, source text and authored semantic fields affect the
key. Reads recompute the scoped dependency witness and bind the response to the current actor.
Missing, corrupt, changed or unproved inputs use the ordinary validation path. The store writes
atomically and retains up to 256 records and 16 MiB. The Reasoner reports
`durableSourceHits` and `durableSourceWrites` in `semantic.validation.sourceProofs`.
`DURABLE_SOURCE_PROOF` in the document validation trace identifies a successful disk reuse.
The implementation fingerprint includes class bytes from the validator, shared k.LAB modules,
HermiT and OWLAPI. Repacking identical classes preserves the key. A changed class in those
artifacts can trigger one cold pass, including on development builds sharing a SNAPSHOT version.
This happened when the shared Resources client gained the worldview header request. Its first
Reasoner instance wrote 24 records with zero disk hits although the project files matched.
Those 24 full validations took 48.6 summed seconds.
Reuse resumes on subsequent instances of that build. Narrower invalidation across releases
would require a separately maintained version for the validator and its semantic dependencies.

A changed ontology enters the worldview candidate builder. That builder materializes OWL axioms,
exports observer declarations from its validators, and checks the final closure before publishing
the actor. A file verdict alone cannot recreate that runtime state. Read-only validation of an
already accepted ontology can consume its actor's captured result or a checked static record.
An ontology edit still goes through candidate admission.

The restart control seeded 21 staging namespaces, two saved IMOD ontologies and one private
namespace edit. A fresh process with the same scoped worldview reproduced 22 of the 23 project
and ontology results directly from disk. All 23 were valid and statically complete. The one
full visit took 9.62 s for `staging.vxii.basic`. Its file and parsed key matched. Its dependency
set includes a captured TAXA authority witness that the fresh actor could not reproduce.
Later reads were actor-cache hits. The previously validated private `xdivs=6` edit returned
from disk in 36 ms. Changing it to 7 triggered a 1.634 s validation and an immediate repeat
returned in 21 ms.

The delivered build repeated the experiment with two more service processes. It again
reused 20 of 21 staging namespaces and both IMOD ontologies from disk. Only
`staging.vxii.basic` needed a full static visit. The private `xdivs=6` edit returned
from disk in 25 ms, the new `xdivs=7` edit validated in 2.427 s and its immediate
repeat returned in 30 ms. The original 21 staging source hashes, 26 IMOD ontology
hashes and locally modified testcase hash all matched after the experiments. The private
fixed-selection fixture was removed.

During the canonical suite the starting distribution made 25 full saved-namespace visits
(210.2 summed seconds) and 49 full single-model visits (119.2 s). The restarted build
made one saved-namespace visit and zero single-model visits. It still completed 65 full
executable admissions (30.1 s, down from 68.9 s), plus 53 executable proof hits. Before
and during the suite, 20 saved staging namespaces actually reused their disk records.

On the 25m 56s run, the single remaining full static visit represented 0.62% of suite
time. All static namespace calls together, including proof verification, occupied 22.9
summed seconds. Even taking all of those seconds off the critical path would save under
1.5%, with other work held fixed. On the starting distribution the full static visits
accounted for 329.4 summed seconds, or 13.5% of wall time if all had been on the critical
path. CPU contention and garbage collection can also affect concurrent requests.
The delivered run again made one full saved-namespace visit (7.27 s) and zero generated
namespace visits. All static calls occupied 21.9 summed seconds, 1.54% of suite time.
The 65 full executable admissions took 27.0 s and the 53 executable proof hits took 1.68 s.

### Where observation time went

On the original November 26 run, the Resolver logged 579 resolution attempts. The profiler
records every characterization and other attempts that take at least one second.
These attempts accounted for 1,031 s of summed time, including 519 s in semantic client calls. In 520
logged model queries, discovery took 175 s, source-to-model mapping 130 s, filtering 148 s and
admission/ranking 162 s. Slow cycle admissions accumulated 229 s and slow Runtime submission
preparations 109 s. These intervals overlap and omit fast calls. Adding them would double count
part of the critical path. Runtime also completed 386 `NO_MODEL` characterization children during
the suite. A single child can trigger multiple Resolver jobs.

Flight Recorder captured all four JVMs for 150 s early in the suite and for 100 s during
`select_multiple_families`. In the original early Reasoner capture, 633 execution samples followed
the OWL hierarchy into clause compatibility during document validation. Another 457 visited
entailed clause owners while building speculative concepts. The actor monitor accumulated
109.5 thread-seconds of contention. The optimized Reasoner uses a strict pairwise subsumption
test on unclassified, buffered HermiT where the ontology has no pending changes. It retains
complete hierarchy queries for callers that require the set, reuses completed clause bounds
within an OWL revision, and avoids building generalized qualities that cannot match an offered
model head. Resources caches its Jackson field descriptions per class. The Resolver constructs
the prioritizer once a query has candidates to rank.

In the original 150-second Resources recording, 411 of 704 execution samples landed in
polymorphic serialization and 136 in its reflective field enumeration. In the optimized
recording, 127 of 235 landed in serialization. The warmed Resolver spent 24 of 59 original
samples in polymorphic deserialization. Runtime's child timers recorded the delay across
the service boundary, and its original early recording contained only 176 execution samples.
This combination explains how a slow submission can consume little Runtime CPU while it
waits for semantic work, catalog transfer or resolution.

| Reasoner recording | Original | First optimized run |
| --- | ---: | ---: |
| Early, collection count / total pauses | 184 / 5.83 s | 151 / 3.54 s |
| Early, observed heap growth between collections | 41.73 GB | 31.96 GB |
| Four-family action, collection count / total pauses | 192 / 4.15 s | 229 / 5.62 s |
| Four-family action, observed heap growth between collections | 42.42 GB | 50.21 GB |

Heap growth is the sum of positive differences between the used heap after one collection and
before the next. Allocation-sample weights can include allocations before the recording began.
The four-family action produced 49 `NO_MODEL` jobs on the original run and 94 on the first
optimized run. Its higher allocation and collection rates need to be read alongside the extra
work. OWL entailments used to discover applicable clause bounds remained hot in both recordings.

At suite completion the original actor had recorded 24,551 hierarchy-cache misses and 22,172
satisfiability misses. The first optimized actor recorded 7,001 and 10,315 respectively. Its
complete clause-bound cache served 17,454 hits and accumulated 2,986 misses. These counters
cover startup and the suite on each JVM.

Characterization timing covers every child in these runs. The original 386 children averaged
201 ms in Runtime preparation and 902 ms awaiting the Resolver. The first optimized run's 442
children averaged 126 ms and 616 ms respectively. Both recorded zero milliseconds in apply and
commit for every `NO_MODEL` child. Discovery in these empty characterization queries, which
previously included creating a prioritizer, fell from 200 ms per child to 10 ms. Average
source-to-model mapping was 132 ms and 127 ms per child. These figures locate the savings
more precisely than a comparison of the two wall times alone.

On the 25m 55.624s run, Runtime preparation averaged 91 ms per child and Resolver waiting averaged
522 ms. Discovery averaged 7.6 ms, mapping 101.5 ms and filtering 14.1 ms. Apply and commit
again rounded to zero for every `NO_MODEL` child. The 518 profiled Resolver attempts totalled
453.7 s, including 262.1 s in semantic calls. The 535 logged model queries spent 28.1 s in
discovery, 75.1 s mapping, 73.7 s filtering and 73.5 s in admission/ranking. Slow cycle
admission queues accumulated 137.7 s and slow Runtime submissions 21.4 s. Event thresholds,
nesting and concurrent work apply to these totals.
On the delivered build the 461 characterization children averaged 85 ms preparing in
Runtime and 492 ms awaiting the Resolver. Discovery averaged 7.2 ms, source mapping 93.0 ms
and filtering 13.5 ms per child.

| Canonical action | Original time | First optimized time | Original / optimized `NO_MODEL` jobs |
| --- | ---: | ---: | ---: |
| `ns_resolution` | 230.747 s | 86.226 s | 25 / 11 |
| `select_attribute` | 178.510 s | 53.826 s | 26 / 28 |
| `select_two_role_families` | 38.445 s | 155.841 s | 12 / 48 |
| `select_multiple_families` | 189.112 s | 262.975 s | 49 / 94 |

The two-role action took four times as long while issuing four times as many unsuccessful
Resolver jobs. The attribute action got faster while issuing slightly more jobs. The fixture
and the implementation both affect the observed wall-time distribution.

### Fixed selection and the fastest measured strategy

The controlled run alternated the deployed Resolver and Runtime implementations, their two
predecessor classes from `72c6e874e`, and the deployed implementations again. Only Resolver
and Runtime restarted between periods. Resources and the accepted Reasoner kept the same
processes. Each period began with one excluded warm-up and used a fresh observation context
per trial. All twelve runs passed four assertions, discovered exactly 13 members, emitted grid
digest `9ec3891ab3177a17e28180e7a9970774a97e00b489a9a81e62bd27076ba39438`, and completed
55 characterization children, 69 `NO_MODEL` Resolver jobs and one resolved job.

| Implementation period | Warm-up | Three timed actions | Median action | Median child wait on Resolver | Median source mapping | Mapping calls per trial |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| Batched ports and selection binding reuse | 101.753 s | 101.688 / 81.159 / 71.462 s | 81.159 s | 20.203 s | 7.322 s | 440 |
| Previous per-port mapping and repeated binding | 105.156 s | 88.850 / 91.791 / 80.269 s | 88.850 s | 25.616 s | 12.089 s | 1,485 |
| Batched ports and binding reuse restored | 90.844 s | 80.946 / 82.652 / 83.041 s | 82.652 s | 20.384 s | 6.563 s | 440 |

A focused test JVM shared the machine during part of the 101.688 s timed action.
Excluding that action leaves 76.311 s as the median of the other two in its period. The
restored period had three isolated measurements. Both batched periods had lower medians
than the predecessor period, with overlapping individual times. The best measured action
took 71.462 s. Batching removed 70.4% of mapping calls in every trial. The controlled
comparison changed mapping and selection binding together, so the wall-time difference
cannot be assigned entirely to either one.

In each batched period, all 280 eligible jobs used the shared catalog session. All 32
attempts to establish an authority-free cross-member model projection encountered an
authority dependency. The mapper therefore continued to resolve the affected ports in the
actual member scope. On the restored period's middle trial, the saved-namespace proof took
4 ms and six executable proof hits together took 7 ms. Removing those checks would have
little effect on the 82.652 s action.

The fastest measured configuration retains a warm accepted actor and Resources catalog,
reuses static source records across restarts, batches a model's ports in the retained
Reasoner call, and reuses the checked selection binding within its attempt. The next
substantial opportunity is to batch the independent read-only work across member
characterizations while retaining per-member geometry, caller visibility, outcomes,
provenance and rollback. Runtime currently creates, resolves and commits one child at a
time. A shared negative answer would require a proof of equivalence across those contexts.
The 82.652 s action on the restored implementation included 20.364 s of child wait.
Removing that wait entirely would still leave 62.288 s with other work fixed.

### Genuine source edits and the authority cache

The edit runner uses the authenticated local Resources parser, the live namespace validator
and a private copy of IMOD for ontology candidate construction. With JDK 21, point
`$reasoner` and `$runtime` at the two product JAR directories. Use the same output directory
in two JVMs. The second invocation changes the ontology again and can reuse the first
invocation's provider data. The `first` and `second` source subdirectories must be new.

```powershell
java "-Dklab.reasoner.traceCandidate=true" -cp "$reasoner/*;$runtime/*" tools/validation/VxiiEditProfile.java results/edits "$env:USERPROFILE/.klab/knowledge" "$env:USERPROFILE/.klab/services/reasoner" first
java "-Dklab.reasoner.traceCandidate=true" -cp "$reasoner/*;$runtime/*" tools/validation/VxiiEditProfile.java results/edits "$env:USERPROFILE/.klab/knowledge" "$env:USERPROFILE/.klab/services/reasoner" second
```

The namespace control changed `xdivs` in the eight-model `staging.vxii.test.selection` source.
It parsed a private file through Resources and validated it through the authenticated Reasoner.
The original build took 7.179 and 7.193 s for two different edits, then 33 and 32 ms for their
immediate repeats. The first optimized build took 4.200 and 3.624 s, then 80 and 33 ms.
Resources parsing took 88–280 ms across these four edits. The staging project's files stayed
untouched throughout this control.

The ontology control added a subtype of `atmosphere:SpecificHumidity` to the real IMOD
`atmosphere.kwv` and submitted the resulting 12-statement ontology in a private, complete
worldview candidate. The original build took 241.853 s after capture. Provider preparation took
148.97 s, including 97.63 s for its bootstrap actor. Building the accepted actor took another
57.10 s. Twenty-eight document visits took 29.25 s. Removing even every document visit would
save at most 12.1% of that run with the other stages held constant. A second edit on the
original build took 107.800 s when an existing provider capture matched. Provider preparation
then took 3.72 s.

Separate JVMs using copies of the same provider data, authorized plan and input could select
different authority configuration directories. The observed directory prefixes included
`83991da93b7fb`, `e4bdf9ffa6c00` and `8563ed44be1ed`. The provider input and plan stayed
at `9ff0fc6318c2` and `bdab3a55d594`. The persistent configuration digest had serialized
maps, including immutable codelist and boundary maps, in iteration order. A miss rebuilt a
bootstrap actor and could fetch `CHEM:CID_962` from PubChem. Three private cache-cold attempts
timed out obtaining that identity. The final implementation orders map keys recursively for this
digest. It can also import an unexpired exact capture whose previous key it can independently
recompute. Provider plan, authorization, declaration content and freshness still participate in
reuse.

| Private IMOD edit | Build after capture | Provider preparation | Accepted actor load | Document visits |
| --- | ---: | ---: | ---: | ---: |
| Original, cold provider capture | 241.85 s | 148.97 s | 57.10 s | 29.25 s |
| Original, exact provider capture | 107.80 s | 3.72 s | 67.4 s | 32.4 s |
| Corrected key, first capture | 179.07 s | 98.07 s | 53.71 s | 23.17 s |
| Corrected key, different edit in a fresh JVM | 99.38 s | 4.92 s | 70.77 s | 17.09 s |
| Warm provider, checked-in edit runner, first JVM | 55.14 s | 3.02 s | 36.46 s | 12.60 s |
| Warm provider, checked-in edit runner, second JVM | 79.15 s | 2.65 s | 56.77 s | 16.58 s |

Both corrected-key JVMs computed configuration `a76a1cd87f3a`. Their legacy hashes were
`f2a4f8c8205f` and `964a59b8cde7`. The second JVM accepted a different subtype declaration.
Its provider phase skipped building an authority bootstrap actor. The 99.38 s includes a new
accepted actor and all 28 document validations. Even eliminating every document visit in that
run would leave about 82.3 s. Further gains on this edit depend mainly on safely reusing compiled
ontology state across candidates.

The two runs of the checked-in runner began with the verified provider capture. Both accepted
the complete private IMOD candidate. The fastest observed edit took 55.14 s after capture,
including 36.46 s of actor construction. On that run, even a zero-cost document visitor would
leave 42.5 s. The same runner's two genuine namespace edits validated in 1.901 and 1.118 s
on the warmed service. Immediate repeats took 15 ms each.

The editor's direct validation path also checked the project type by retrieving the complete
project before entering the Reasoner timer. Two authenticated reads of IMOD took 6.155 and
6.784 s. The encoded project occupied 2,190,842 bytes, including its documents. Its manifest
occupied 590 bytes. Resources now supports a manifest projection on the existing project
retrieval endpoint. It checks project READ permission before projecting the response. A client
can use the same request against an older provider that returns the complete project.

On the build with the manifest projection, a full read took 3.370 s, followed by manifest reads of 39 and 14 ms.
The projected HTTP response was 1,335 bytes and contained zero ontologies. End-to-end initial
reads of the saved `ecology` and `atmosphere` ontologies still took roughly nine seconds each
on that instance, down from 14.9 and 12.7 s in the preceding instance. Those timings include
the saved-source diagnostics lookup and the Reasoner's dependency checks.

With the accepted actor already warm, four reads of the two saved ontologies still spent
6.07–7.52 s inside the validation request. Fetching an individual saved ontology separately
took 184–211 ms. This user scope had two worldview providers. Project ownership discovery
retrieved the entire worldview list from both. One timed lookup spent 0.62 and 0.46 s
reading capabilities and 4.43 and 0.94 s retrieving the local and remote worldviews.

The Resources list endpoint now supports a caller-scoped worldview header for this owner
lookup. It includes project ownership, document URNs, projects, ontology imports and the
provider's completeness flag. The Reasoner runs the existing precedence, collision and
import-order checks over these headers. A provider running the previous endpoint can
return its full worldview in the same list. Candidate construction still takes the full
worldviews, including their statements and authority declarations.

The live local provider returned the same routing data in 13,370 response bytes with zero
ontology statements, down from 2,194,443 bytes. Client time fell from 2.987 to 0.616 s.
The remote provider ran the previous protocol and returned its full 2,192,275 bytes and 406
statements for both requests (0.809 and 0.825 s). Both supplied the same projects, URNs and
imports as their ordinary authorized reads.

On the delivered build, the four warmed validation requests for `ecology` and
`atmosphere` took 4.979, 4.245, 3.816 and 5.733 s, compared with 6.074, 6.415, 7.521
and 6.803 s before the header projection. The accepted actor's proof checks took 10–60 ms
on the four new requests. The remaining wall time includes authenticated provider discovery,
the older remote provider's full response, saved diagnostics retrieval and transport.

The implementation checks compiled the affected modules against the tested distribution and
ran 300 focused JUnit tests successfully. These cover source and executable admissions,
changed and corrupt records, authority access and freshness, edits and candidate construction,
OWL cache invalidation, model selection, and worldview project precedence and imports.
One additional test requires a separately exported accepted OWL fixture and was skipped.
