# Implementation and integration work log

## Current status — checkpoint requested 5 October 2026

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

## Work to do, in execution order

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

Entries will be appended as work resumes after this checkpoint.
