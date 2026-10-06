# Implementation and integration work log

Current user documentation lives under python-client/docs. Detailed development
history is retained here and is not shipped in the Python source distribution.

## Latest verified status

### Maintainer revisions — 6 October 2026

Separate current assessments: `python-client/docs/review.md` (server, Python
library, optional tooling). Documentation now contains concise current contracts
and reproduction instructions; earlier audits/guides remain in `history/` here.

Fixed persisted graph ACL authorization/restoration (federation is not authority),
domain HTTP error mapping, foreign-runtime submission/result binding, explicit
descriptor compatibility mapping, missing-geometry reads, strict security
assertions, bounded process lifecycle and fixture provenance. Added nonuniform
zero/missing-cell oracles, controlled in-flight cancellation, actual cold Runtime
restart checks, and an offline Python CI matrix.

The stronger collaborator-first cold restore exposed the need to preserve parent
session authority. Restored sessions now record the persisted owner; regression
checks deny collaborator session use and prove owner creation/release still works.
The first added Java mock used HashMap where Parameters was required; fixed and
rebuilt before the final fresh run. `klab-revision-live2` failed the stronger check
against the previous Runtime compilation; cleanup completed. No success claim
is based on that failed run.

Final targeted Java checks: `final-bounded-server-review3.log`, BUILD SUCCESS,
29 tests (17 core/2 Reasoner/10 Runtime), zero failures/errors/skips. Python 3.11
and 3.12: 91 passed, 4 live deselected. Isolated installed-wheel Python 3.11:
91 passed, 4 deselected from outside the checkout. Wheel/sdist build succeeded.
GitHub CI/Linux/Python 3.13 have not been executed locally.

Fresh `klab-revision-live3` ran 15:57:00–15:59:31 UTC on 6 October:
prepare/start, 2 live acceptance tests, persistent ACL prepare/Runtime-only
restart/check (collaborator first), full c1/c2/c4 and repeated-read phases all exit
0. Cleanup exit 0, all five JVM exits confirmed. Command flags were `--samples 1
--duration 1 --step-timeout 180`, Python 3.12.7, JDK 21.0.12.1, pinned imod
608bef150ced0a109db98a5aad64ba4461beaa54. Evidence is external under
`C:\Users\lumsd\AppData\Local\Temp\opencode\klab-revision-live3`; generated
credentials/state are not tracked. Short revision smoke runs establish correctness,
not a new sustained throughput/capacity result.

Remaining boundaries: production hub onboarding, genuine terrain/reference
accuracy, bulk/large-data, temporal/federated and soak. Candidate-context listing
uses a full graph scan plus Java ACL filtering; malformed rights fail closed.

The complete deterministic scientific workflow now passes with real local services,
normal signed ROLE_USER JWTs, actual arithmetic execution/storage readback, m/mm
conversion, exact spatial checks, separate-client attachment, and explicit release.
Actual failure, active cancellation, expired/invalid credentials and private-context
isolation tests also pass. A fresh-state orchestrated run passed acceptance and all
throughput phases with cleanup exit 0; results/recipe are in throughput.md and
results/local-throughput.json. This is isolated TEST authority/synthetic field
evidence, not production hub login or actual terrain/provider accuracy.

Earlier sections below retain the checkpoint's then-current state and plan.

## Historical status — checkpoint requested 5 October 2026

Acceptance boundary agreed with the user:
**A scientist can install the package, authenticate through a documented supported
path, create or attach to a context, submit a real computation, monitor its genuine
outcome, retrieve scientifically correct data, and manage its lifecycle without
unintended disposal.** Full-workflow throughput must also be tested reproducibly.

This checkpoint preserves work in progress, not a declaration of completion.
There has not yet been a successful full scientific workflow or throughput sample.
Historical audit/local-stack documents describe earlier states; this log tracks
subsequent remediation and actual attempts. No production-hub login is claimed.

### Completed changes

| Area | Work | Verification at checkpoint |
|---|---|---|
| Core Python client | Transport-backed facade, scope/session/context/job workflow, scientific point reads, explicit errors and boundaries | Original implementation/expanded acceptance tests; see verification.md |
| Local infrastructure | Portable JDK 21, Maven server build, required webui resource build, embedded Neo4j, all four services | Actual HTTP startup/probes, documented in local-stack.md |
| Worldview | Public imod revision 608bef150ced0a109db98a5aad64ba4461beaa54 loaded | Actual Reasoner operational status and semantic diagnostic calls |
| Unresolved observables | Reject owl:Nothing/NOTHING/VOID despite HTTP 200; captured regression fixture | Passed offline regression using actual captured wire payload |
| Region example | Correct geography:Region to earth:Region, optional explicit override for other deployments | Verified against loaded public worldview |
| Geometry | Stop treating a server key/hash as an encoding; explicit scalar/universal/empty codes, other encoding unsupported | Added regression tests |
| Scientific metadata | Return target semantic identity/unit for mediated reads and retain source identity/unit separately | Added regression test |
| Context integrity | Reject reads with a different runtime or context binding before HTTP | Added regression test |
| Wait budget | Check elapsed deadline during streamed response reads and after result decoding; close timed-out response | Added trickle/resume regression, gzip decoded once test. Blocking socket operations still use bounded per-phase HTTP timeouts; not hard-real-time scheduling. |
| Server unit builder | Parse string units through existing UnitService, retain portable definition in observable URN | SemanticsBuilderUnitTest: 2 passed, including wire round trip and invalid unit |
| Server job reporting | Null successful result becomes explicit failed job, not null status | JobManagerTest: 5 passed |
| Session submission | Supply unique request configuration.id as required by BaseService.declareSessionScope | Added after last successful Python suite; rerun pending at checkpoint |
| Test authority | Loopback-only authority using existing service auth-cert response contract, RSA-signed test JWTs, generated TEST certificates, one-hour issued credentials | Service authentication and signed-user scope advertisement/catalog calls exercised; not production identity validation |
| Deterministic model | Explicit test-only k.IM constant-field model: 123.25 m; expected mm values 123250 | Model catalog became nonempty; computation/readback not yet passed |
| Benchmark | Preliminary fresh-context full-workflow harness, stage timing, independent value checks, attach/resume/readback, explicit cleanup, failure report/nonzero exit | Harness under development, no successful sample or throughput claims |

### Last completed commands/results

Python environment: `C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311`.

* `python -m pytest -q`: **57 passed, 2 deselected** before the latest session-ID
  request correction. Rerun before accepting that correction.
* Root Maven command:
  `mvnw.cmd -B -ntp -pl klab.services.reasoner -am -Dtest=JobManagerTest,SemanticsBuilderUnitTest -Dsurefire.failIfNoSpecifiedTests=false test`
  (quote dotted -D arguments in PowerShell): **7 targeted Java tests passed**.
  First attempt exposed an incomplete test mock (OWL missing); corrected the
  fixture without changing expected unit/serialization assertions.
* Last local start exposed public endpoints for graphdb, Resources, Reasoner,
  Resolver and Runtime. This is process/HTTP readiness, not scientific acceptance.

### Integration attempts and corrections, in order

1. Existing temporary JDK exited with 0xC0000135; provisioned a complete portable
   official JDK and verified its checksum. No global Java installation changed.
2. Java-only compile succeeded, service startup failed on missing static/index.html.
   Built the existing Maven webui profile; real applications then started.
3. Anonymous public SDK calls succeeded, scientific endpoints returned 403.
4. Provisioned maintained public worldview using supported FILE-project startup
   configuration. Discovered wrong Region namespace, unit-builder stub and null
   import job status. Recorded observed responses instead of fabricating success.
5. Audited client and server contracts; findings in astra-audit-findings.md.
6. Implemented the bounded client/server fixes listed above.
7. Created a separate `klab-throughput` state directory with explicit TEST
   service certificates, a local signing authority and constant model fixture.
8. First certificate startup failed because the fallback certificate branch read
   properties before validating/loading the certificate. Launcher now passes
   `-cert` explicitly through the validated branch; no auth validation disabled.
9. Fixture manifest used string prerequisiteProjects although Java expects Pair
   entries. Removed the redundant list entry (worldview already declares the
   dependency); corrected generator and isolated fixture.
10. Services started with certificates. First workflow failed 403 because generated
    one-hour user credentials had expired during the long investigation. Added
    `fixture_hub.py issue` to refresh test credentials explicitly.
11. Refreshed signed-user requests successfully advertised scopes and read model
    catalog. createSession then failed. Although HTTP surfaced as 403, server log
    showed `session scope has no ID, cannot register a scope autonomously`; JSON
    error negotiation with Accept:text/plain masked the underlying failure. Fixed
    Python to supply a session ID as the Java client does.
12. Reasoner was not operational under service certificates because fixture hub
    response omitted peer service references. Added the existing ServiceReference
    inventory to the test auth response, restarted authority and all services.
13. Paused on the user's request for status. Latest restart exposed all public
    endpoints; **scientific workflow after session-ID/inventory fixes is not yet run**.

### State and evidence

* Branch: feature/python-client. Previous committed checkpoint: 490af42d4.
* Original local infrastructure evidence: `%LOCALAPPDATA%\Temp\opencode\klab-local-stack`.
* Current signed test deployment: `%LOCALAPPDATA%\Temp\opencode\klab-throughput`.
  Includes scientist.json/private key/enrollment configuration (never commit),
  generated model/certificates, server console logs, home/.klab state and smoke.json.
* Targeted Java test log: klab-local-stack/server-fix-tests.log; module Surefire reports.
* Runtime/service sources are this checkout's compiled classes, not a production deployment.
* Processes currently running at checkpoint: authority on 18090; graphdb 8382 and
  Bolt 7687; services on 8091–8094. Use the launcher-owned process cleanup. Do not
  confuse changing PIDs with progress or block waiting for a PID without inspecting logs.

## Checkpoint plan (execution record follows below)

1. **Single successful scientific sample:** refresh test credential, run one
   workflow, inspect the first real failure immediately. Confirm peer Reasoner
   readiness, scope creation, Region submission, model resolution, storage values,
   units and attach/readback. Keep reports for each attempt rather than overwriting.
2. Correct only the next essential contract/server issue demonstrated by that run;
   add a regression for every production correction. Do not weaken the oracle.
3. Prove authentication negative cases (invalid signature/expired/wrong-user scope)
   and distinguish isolated test-authority evidence from actual deployment login.
4. Validate returned geometry against CRS/bounds/resolution, not only shape. The
   audit's geometry-reference comparison finding remains open.
5. Strengthen lifecycle coverage: local close vs explicit release, real failed job,
   in-flight wait/resume, cancellation and attached-context ownership. Existing
   completed-cancel/zero-budget tests do not prove running-job behavior.
6. Finish benchmark tests: failure cannot count as success; missing/invalid data
   rejected; numeric summaries validated; attach/release failures invalidate run.
7. Run reproducible workloads at concurrency 1, 2, 4 with a declared sample count
   and duration. Separate fresh contexts/computation from repeated/cached reads.
   Record stage latencies, p50/p95/p99, verified workflows/s, verified cell reads/s,
   failures and available CPU/memory/IO evidence. Current point reads are serial
   per cell; do not call that bulk-data throughput.
8. Verify fresh-environment installation/build after final edits, update README,
   contract matrix, supported authentication instructions and final evidence.
9. Stop only test-owned processes and retain sanitized failure evidence. Commit
   reviewed milestones when requested; do not push/publish/deploy remotely.

## Working discipline after this checkpoint

* Lead with a concrete completed milestone or first failure, not "still working".
* After each live attempt, append command/result/root cause/next action below.
* Do not repeat startup or broad tests without a changed prerequisite or a concrete
  unresolved concern. Inspect logs promptly when readiness fails.
* Before widening a throughput run, require one sample with actual verified values.
* Test authority and deterministic constant field are explicit test fixtures;
  neither is evidence of production login, real terrain data or broad scientific accuracy.

## Resumed execution entries

### Attempt 01 after checkpoint 8089ac111

* Refreshed test-user token; ran workflow_benchmark.py with 1 sample/concurrency 1,
  report `klab-throughput/attempt-01.json`.
* Session and context creation succeeded; Region returned job ID 1. Actual job
  result failed with "empty dataflow" / "Model discovery failed". No values or
  throughput were reported. Offline suite rerun: **57 passed, 2 deselected**.
* Immediate log inspection: Resources ModelKbox's service-scope Reasoner was null.
  ServiceInstance only constructs advertised peers listed as essential/operational;
  ResourcesServer's Reasoner operational dependency was commented out, although
  semantic indexing requires it. Restored the nonessential operational dependency,
  preserving Resources-first initialization for worldview bootstrapping.
* Service-to-service test JWTs also expired during the long pause. Increased only
  fixture **service** token lifetime to 24 hours; user tokens remain one hour with
  explicit refresh. Restart test authority/services before retry; no JWT validation
  disabled or user privileges elevated.

### Attempt 02 and inspection of the returned Region

* Restored Resources' operational Reasoner dependency; rebuilt the affected
  server/reactor; restarted fixture authority/services with renewed service JWTs.
* Report `attempt-02.json`: session/context creation and Region job completed, but
  the harness required resolvedCoverage=1 and failed. Actual retrieved Region has
  positive persisted ID, empty=false, no notifications and correct EPSG:3857
  bounds/[5,4] shape, with resolvedCoverage=0.
* Source confirmation: ResolverService.unresolvedOutcome permits error-free
  acknowledgement of singular substantials without an explanatory model via
  NO_MODEL. Zero model coverage is legitimate for this Region; it is not a failed
  numeric calculation. Replaced the inappropriate Region assertion with committed
  OBJECT identity + exact geometry checks. Numeric quality coverage and all actual
  value/unit assertions remain strict.
* Added reusable exact grid CRS/bounds/regularity/shape validation, closing the
  audit's location-comparison gap. Next: retry quality computation/readback.

### Attempt 03 — numeric submission/storage registration

* Region acknowledgement succeeded under corrected contract. Numeric model
  submission reached the runtime but returned an error notification "Problem
  finalizing storage"; report `attempt-03.json` contains context and both job IDs.
* Source: resolution commits a quality's identity before ExecutorImpl.createStorage
  allocates storage. Graph ID assignment incorrectly required a pre-existing
  provisional allocation and marked its expected absence as an error.
* Added explicit local allocation presence check: migrate only allocated provisional
  storage, and still fail when an existing allocation cannot migrate. Made same-ID
  finalize avoid removing its own map entry. No numeric oracle was changed.
* Targeted StorageRegistrationTest: **2 passed**. Python suite: **58 passed,
  2 deselected**, including acknowledged Region and wrong-CRS/wrong-bounds checks.
* Next: restart only Runtime with rebuilt classes and retry one verified sample.

### Attempt 04 — literal shortcut is not computation throughput

* Quality completed with positive ID/coverage/unit, but storage point read failed:
  server had no storage allocation to reconstruct. Report `attempt-04.json`.
* The literal-source fixture (`model 123.25`) takes an inline-value shortcut and
  does not exercise scalar storage execution. Replaced the fixture with explicit
  arithmetic expression `model [100 + 23.25] ...`; the independent expected value
  remains 123.25 m / 123250 mm. This is a changed test workload, not fabricated
  data or an assertion weakened to treat metadata as storage readback.
* Plain-text-only Accept hid server diagnostics behind error negotiation/403.
  Transport now also accepts JSON errors at lower priority for text routes.

### Attempts 05–06 — first complete verified workflow

* Attempt 05 still used a cached source model. Inspection showed an empty model
  contextualization list and no allocated quality storage. Changed the explicit
  fixture to canonical action syntax `model geography:Elevation in m set to
  [100 + 23.25];`, restarted only Resources and Resolver to reload/reindex it.
* Attempt 06 **passed**: authorized session/context creation, acknowledged Region,
  real numeric job completion, positive observation ID, exact CRS/bounds/grid,
  20 actual stored cells = 123.25 m, 20 server-mediated cells = 123250 mm,
  separate-client attach/resume/readback, explicit context and session release.
* Report `attempt-06.json`: 1 verified workflow, 0 failures, 6.839 s wall time,
  6.124 s sample latency. Single cold sample is a smoke result, **not sustained
  throughput evidence**. Field is synthetic deterministic arithmetic, not real
  terrain. Signed TEST user credentials used; no scientist administrator key.
* Next: repeated fresh-context runs/concurrency and benchmark regression tests,
  cached-read measurements and authorization/lifecycle negative checks.

### Throughput and lifecycle verification

* Added bounded sustained scheduling, warmup exclusion, full workflow versus
  repeated-read modes, nearest-rank latencies and explicit five-JVM CPU/RSS/OS IO
  counters. Any failed sample invalidates successful throughput metrics.
* Offline benchmark regressions verify failure accounting, latency/rate math,
  bounded concurrency, exception recording and stop-new-submissions behavior.
* Ran full workflows at concurrency 1, 2, 4 and repeated reads at 4. All values and
  releases verified; original reports full-c1/2/4.json and read-c4.json outside Git.
* Initial cross-user test exposed a real server defect: connectContext and managed
  scoped lookup reused private contexts before checking owner/access rights.
  Added owner/explicit-rights checks to both routes, typed denial in the token
  filter, and persisted-visible-descriptor reconstruction rather than caller claims.
* Targeted Java selection: **18 passed** (JobManager 5, ScopeManager 7,
  ManagedScopeAccess 2, TokenAuthorizationFilter 2, StorageRegistration 2).
  Existing scope tests passed. Unit-builder tests previously added: **2 passed**.
* Actual verify_fixture.py now passes: computed/readback seed, genuine missing-model
  scientific failure, WAITING job cancellation accepted and confirmed INTERRUPTED,
  invalid/expired signature rejection, other-user private attach/job denial,
  local-close retention and named release. Reports security-lifecycle-fixed.json
  and lifecycle-complete.json.
* Added explicit public Client.initialize_user_scope using the existing
  notifyUserScope contract; no hidden import/constructor networking, credential
  discovery or local role grants. Documented the unsupported browser scoped
  credential path instead of suggesting it works. Scope bootstrap regression added.

### Fresh-state reproducibility run

* run_local_acceptance.py prepares an entirely new state, starts TEST authority/
  services, runs tests/test_local_workflow.py, benchmarks each phase, and always
  cleans up. Recipe supports optional server/webui/classpath build and refuses
  pre-existing prepared state and occupied ports.
* Actual command: runner with existing compiled classpaths, new klab-repro-final,
  --samples 8 --duration 15. UTC 2026-10-06 02:57:17 to 03:00:08. All seven steps
  returned exit 0, overall passed=true, owned services/authority stopped.
* Live pytest: **2 passed**, including actual active-cancel and concurrent samples
  with distinct context/observation IDs. This is not the older terrain/reference
  test, which remains separately opt-in and requires actual assets/reference.
* Fresh measured samples: c1 8/0 failures (0.355 workflows/s), c2 12/0 (0.678/s),
  c4 17/0 (0.949/s), repeated read 54/0 (3.389 read cycles/s, no recomputation).
  15 s minimum phases, small fixture/percentiles; no maximum capacity/large-data
  or production claims. Sanitized summary committed under docs/results.
* Remaining before final handoff: verify final scope-bootstrap wrapper in a fresh
  run, build/install updated wheel, update historical documents and commit final
  reviewed changes. Then sleep the computer as explicitly requested by user.

### Final verification after explicit Client scope bootstrap

* A second completely fresh state (`klab-repro-bootstrap`, minimum 1 sample /
  1 second per phase) passed prepare/start, 2 real live tests, all concurrency
  phases and repeated read, with cleanup exit 0. This confirms the final public
  initialize_user_scope wrapper, not only the earlier tool-local advertisement.
  It is a smoke/reproduction run, not the measured 15-second performance table.
* Final offline suite: **64 passed, 4 live tests deselected**. Two new local live
  tests passed explicitly; two terrain/reference tests remain separate and were
  not represented as passes.
* Final wheel/sdist build succeeded. Installed updated wheel into the separate
  noneditable klab-wheel-311 environment, imported with -I from outside checkout,
  checked public scope-bootstrap API and geometry, and pip check reported no
  broken requirements.
* All test-owned Java/authority processes were stopped by both fresh-state
  runners. No listeners remained on 8091–8094, 8382, 7687 or 18090 at verification.
* Final code/documentation checkpoint follows this entry. The user's explicit
  sleep request will be executed after status is saved and cleanup checked.
* Essential server repairs committed separately as **f7a9c67d5**, "Fix scientific
  storage registration and private scope authorization". Generated runtime
  extension/JTE caches are ignored, not included as contribution artifacts.

## Remaining boundaries after this contribution

* Production hub account/onboarding and scoped browser-session credentials are
  not validated; use documented trusted-network JWT path. Test authority is an
  explicitly isolated enrollment/signing fixture, not a new production protocol.
* Real terrain/provider golden reference, temporal/federated/coalesced workloads,
  large observations, bulk export and long soak/capacity tuning require dedicated
  assets/workloads. Per-cell HTTP may dominate large data retrieval.
* Historical original audit/log findings are snapshots, not all still-open defects.
