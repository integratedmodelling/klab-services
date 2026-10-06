# Astra review and audit handoff: k.LAB Python client

Prepared **5 October 2026**. This document is a standalone entry point for an
independent review of what is implemented, what was actually verified, and what
still prevents scientific end-to-end and throughput acceptance.

## Bottom line

We have an installable, synchronous Python client with transport-backed services,
scope/job abstractions, indexed scientific-data retrieval and offline/live test
code. We have **actually built and started the local four-service stack plus
Neo4j**, loaded a maintained public worldview, and called public service APIs
from Python.

We have **not** successfully completed the authorized scientist workflow
`session -> context -> observation submission -> completed computation -> actual
scientific values`. We have **no scientific throughput measurements**. An
independent review must not turn infrastructure readiness, synthetic tests or
implemented methods into evidence that those acceptance boundaries passed.

## 1. Review target and checkpoint status

| Item | Value |
|---|---|
| Repository | integratedmodelling/klab-services |
| Active worktree | `C:\Users\lumsd\Downloads\k1\klab-python-client` |
| Branch | `feature/python-client` |
| Committed checkpoint HEAD | `490af42d4aedea1fa6ae1951b36ae872c827c543` |
| Server source baseline | `75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef` |
| Public worldview inspected/loaded | integratedmodelling/imod, `608bef150ced0a109db98a5aad64ba4461beaa54` |
| Original assignment | `C:\Users\lumsd\Downloads\klab-python-client-cli-spec.md` |
| Specification reference revision | `e24b756f0815d2f51034d62282a175857f5be15c`; unavailable in this local object database |

Committed work:

1. `2bd150dcd` — Replace Python client placeholders with transport-backed services and jobs.
2. `a4a74eaf3` — Add Python scientific example, live acceptance and contract documentation.
3. `490af42d4` — Strengthen Python live acceptance with reference and lifecycle checks.

**Review the working tree as well as those commits.** The local-stack investigation
and its corrective client/example changes have not been committed at this handoff.
A review of HEAD alone misses the latest work. The latest successful 53-test run
was against that working tree, not an unchanged 490af42d4 checkout.

Post-checkpoint changes include:

* `src/klab_client/api/services.py`: reject HTTP-200 observable results containing
  owl:Nothing/NOTHING/VOID instead of presenting them as a resolved observable.
* `src/klab_client/experiment.py`: default Region is now earth:Region; callers
  can explicitly supply a different deployment-validated region_definition.
* `tests/test_live.py`, `tests/test_live_checks.py`, `tests/test_client.py`: update
  the Region definition and add a regression based on the observed server response.
* `tests/fixtures/unresolved-observable.json`: actual captured unresolved response;
  provenance in tests/fixtures/README.md.
* `tools/local_stack.py`: Windows development launcher/probe/owned-process cleanup.
* MANIFEST.in includes tools in the source distribution; README and documentation
  record the investigation and revise the earlier incomplete assessment.
* `docs/local-stack.md` and this audit handoff are new documents.

For an external review, transfer the working-tree files as well as the branch.
`git diff` does not include untracked files; consult `git status --short` and read
the listed new files. Do not transfer generated keys, credentials or private state
as part of a review bundle. Preserve the separate Python-bridge/shard worktrees.

## 2. Product and implemented workflow

Package: **klab-python-client**, import **klab_client**, Python **3.11+**, version
0.1.0, one runtime HTTP dependency (httpx). The product is an importable package
for scripts/notebooks, not a new end-user command-line application or Python
execution backend inside Java workers.

The scientist-facing API is implemented around:

```python
from klab_client import Client
from klab_client.experiment import run_elevation

with Client.from_env() as client:
    observation, metres, millimetres = run_elevation(client)
```

This illustrates available code, **not a command known to succeed on the current
anonymous local configuration**.

| Capability | Current implementation | Verification boundary |
|---|---|---|
| Installation/build/import | setuptools package, httpx dependency, dev extras, wheel/sdist | Built and imported from an isolated Python 3.11 environment outside the checkout |
| HTTP transport | Explicit endpoints and per-service issued credentials; TLS/timeouts; closeable synchronous client; origin-bound requests; redirects disabled; redacted diagnostics | Offline request/error tests; live public calls succeeded |
| Reasoner | Remote concept/observable resolution and capabilities; preserve wire fields | Offline fixtures; privileged live diagnostic semantic probes; ordinary user resolution returned 403 |
| Resources | Remote capabilities/list/resolve/retrieve; raw asset/dependency DTOs | Offline checks; public capabilities and privileged local catalog inspection; ordinary listing returned 403 |
| Session/context | Creation, existing-context attachment, focused scope tokens, named remote release | Implemented and offline-tested; authorized creation/attachment not yet demonstrated live |
| Runtime submission | POST `/api/v1/submit`, ResolutionRequest envelope, transported observation, provenance agent, constraints | Checked against Java/client/controller source; offline serialized-request tests; no successful live scientific submission |
| Jobs | Server ID, status, polling/result retrieval, wait timeout/resume, cancellation acceptance distinct from interruption | Meaningful offline state/race tests; scientific-job lifecycle remains live-unverified |
| Scientific cells | POST `/api/v1/observation/value`, StorageScan.Point; explicit offsets/curve/slice/semantics; integers/Decimal/None/booleans/semantic keys | Offline decoding/request checks only; no scientific values retrieved live |
| Ownership | Client close is local; remote release is explicit; attached contexts not auto-disposed | Offline tests; deeper live attachment/reread checks exist but have not passed |
| Remaining scaffold methods | Implemented remote calls or explicit UnsupportedOperationError; Modeler/DTO construction remain local | Full inventory in public-api.md; independently audit completeness |

Scientific read calls request **1–256 explicit cells per call**, implemented as
individual HTTP point reads. They are not bulk export, pagination, streaming
arrays, or a proven high-throughput data interface. Observation metadata and
`.get_value()` are not substituted for storage values.

## 3. What was actually run

| Verification | Outcome | What it establishes |
|---|---|---|
| Latest offline Python suite | **53 passed, 2 live tests deselected**, Python 3.11.9 | Client/checker behavior against offline fixtures and synthetic HTTP; not live scientific compatibility |
| Python packaging | Wheel and source distribution built; updated wheel installed/imported in separate isolated environment; unresolved-observable regression and pip check passed | Installability and packaged client code |
| Java server build | 13 targeted reactor entries compiled successfully | These servers/dependencies compile; commands used -DskipTests, so no Java test-suite pass is claimed |
| Shared Web UI build | Maven webui profile: npm ci, Vue typecheck and Vite build succeeded | Required real static resources generated |
| Local startup | Graphdb and Resources/Reasoner/Resolver/Runtime answered live HTTP 200 | Actual processes and public endpoints work |
| Runtime database | Health endpoint reported connected Neo4j 2025.10.1 | Actual graph database connectivity, not durable observation correctness |
| Maintained worldview | Public imod loaded via Resources FILE-project startup configuration; Reasoner subsequently operational | Semantic project provisioning is possible locally |
| Public Python calls | Actual Client capabilities/status/health succeeded for all four services | Python-to-service public HTTP interoperability |
| Ordinary anonymous calls | Session creation, observable resolution and resource listing returned HTTP 403 | Concrete authorization boundary |
| Live scientific test against running endpoints | **Failed at Runtime createSession, HTTP 403**; reference test separately failed for missing KLAB_ELEVATION_REFERENCE; 2 failures, zero skips | Real stack reached; scientific acceptance did not pass |
| Privileged localhost diagnostics | Catalog: 1 PROJECT, 0 MODEL, 0 RESOURCE; semantic responses decoded | Infrastructure/semantic inspection, not ordinary scientist authorization |
| Throughput | **Not run** | No submissions/s, completed computations/s, latency percentiles, sustained concurrency, transfer bandwidth or resource-scaling evidence |

Historical counts (39, then 52 passing offline tests) appear in verification.md as
milestone history. **53 is the current working-tree count.** Live tests initially
failed preflight for missing configuration; the later attempt reached the running
server and failed authorization. These are different outcomes.

The expanded live suite includes three all-cell traversal comparisons, unit
conversion, completed-job cancellation, zero-budget timeout/resume, a new-client
attach/reread cycle, and an independent reference comparison. The code exists,
but the current live run did not reach those assertions. Completed cancellation
would not prove active cancellation; zero-budget timeout would not prove an
in-flight computation was still running.

## 4. Gaps and observed defects

| Priority / kind | Gap | Evidence and needed resolution |
|---|---|---|
| Blocking configuration/input | Authenticated owner and authorized scientist credentials/peer scope advertisement absent | Services started as anonymous; actual ordinary calls 403. Provision a legitimate certificate or an existing authenticated Engine response, advertise peers, then verify per-service credential/scope propagation. An invented token or local role set is not authority. |
| Blocking scientific assets | No executable elevation model or covering dataset installed | Loaded imod is a worldview/strategy project; catalog MODEL and RESOURCE counts are zero. Obtain a compatible maintained model/data fixture, or explicitly design a deterministic real-computation test fixture. Do not replace computation with mocked values. |
| Blocking demonstrated server behavior | String-unit declarations silently discarded | Actual m/mm resolution returned NUMBER semantics with no unit. SemanticsBuilder.withUnit(String), lines 291–293, returns this without applying the unit; ReasonerService.declare invokes it. Repair separately with tests for unit parsing, portable serialization and stable semantic/URN handling; repeat live probes. |
| Blocking reference validation | Independent expected values absent | KLAB_ELEVATION_REFERENCE is missing. Unit conversion/plausibility alone can accept consistently wrong values. Pin dataset/provenance/grid/sampling/datum and all 20 reference cells with justified tolerances. |
| Fixed in working tree; audit | Wrong example Region namespace and acceptance of unresolved observables | geography:Region returned HTTP 200 + owl:Nothing/VOID without error notifications. earth:Region resolved to OBJECT. Default corrected; MissingAssetError guard and actual capture fixture added. Audit compatibility across intended worldviews. |
| Demonstrated server error reporting defect | Null job outcome yields status:null | Local-admin project import could not create a workspace for anonymous; importer returned null. JobManager.status supplied null status, not ABORTED/FINISHED. Python rejects it as ProtocolError. Fix server outcome semantics without declaring null a successful scientific result. |
| Compatibility/consistency concern | Fundamental-type warnings during worldview loading | Reasoner reported operational/consistent, but startup logged mappings/missing fundamental-type warnings. Independently assess compatible language/worldview revisions; flags do not certify scientific consistency. |
| Live coverage missing | In-flight cancellation, deterministic failure, meaningful running timeout, coalescing, credential expiry, cross-user isolation, temporal reads, provenance/durable recovery | Offline coverage does not establish live pass. Need explicit repeatable fixtures and expected outcomes. |
| Performance coverage missing | Complete pipeline throughput and large-result data path | Establish correctness first, then distinguish fresh computation/cache/coalescing effects and benchmark representative concurrent workloads and retrieval sizes. Current per-cell HTTP design may be a bottleneck. |

Explicit unsupported product boundaries include binary Avro worker
contextualization/job-data decoding, dataflow encoding, bulk export, automatic
query/consumer geometry conversion, contextual-unit/range/currency adapters,
pandas/xarray adapters, and full Java Modeler/API parity.

Scope/user authentication is higher risk than package installation. Passing an
administration-key request does not prove the SDK's ordinary issued-credential
path. The optional launcher `--auth-package-file` path is **implemented but was
not exercised with a legitimate authenticated Engine response**.

## 5. Setup discoveries: configuration gaps successfully resolved

* Existing temporary Java was incomplete and exited with Windows 0xC0000135.
  Provisioned a complete official Temurin 21 portable ZIP with published SHA-256.
* Maven 3.9.5 wrapper/dependency cache was usable. No missing language artifact
  blocked the targeted compilation in this environment. Snapshot dependencies
  remain mutable and another environment may resolve differently.
* Docker was not installed, but embedded Neo4j made it unnecessary here.
* Java-only compilation omitted the mandatory dashboard resource. All four
  service applications failed because static/index.html was missing. The existing
  Maven webui profile solved that; no fake HTML/controller bypass was used.
* A shared isolated home/data tree supports local peer client.properties/key
  discovery. Startup Resources FILE configuration loaded the public worldview.
* A generated server-key was used for narrowly scoped localhost catalog/semantic
  diagnostics. It was not added as the scientist-facing SDK authentication path.
* Remaining actor-instantiation/Mongo monitor messages were nonfatal to observed
  startup. Their relevance to full scientific execution has not been ruled out.

This corrects our earlier premature assessment that no running/configured stack
was enough reason to stop. The stack was feasible; attempting it exposed more
specific authorization, asset and server-behavior gaps.

## 6. Evidence locations and reading order

All paths below are relative to the repository unless absolute:

1. `python-client/README.md` — API/configuration/installation and limitations.
2. `python-client/docs/contracts.md` — operation/route/DTO/identity/source matrix.
3. `python-client/docs/public-api.md` — every original scaffold method's status.
4. `python-client/docs/local-stack.md` — actual local build/launch/probe results.
5. `python-client/docs/live-acceptance.md` — deeper acceptance/reference semantics.
6. `python-client/docs/verification.md` — chronological commands/outcomes.
7. `python-client/src/klab_client/{client,transport,dto,errors,experiment}.py`
   and `api/{services,runtime,knowledge,scopes,primitives,modeler}.py`.
8. `python-client/tests/` — test_client.py, test_live.py, live_checks.py,
   test_live_checks.py, fixtures/README.md and both JSON fixtures.
9. `python-client/tools/local_stack.py` — local development process lifecycle.
10. `python-client/docs/pr-draft.md` — draft framing to audit for overclaims.

Raw host-local evidence is under:
`C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack`:

* build-servers.log, build-webui.log, build-classpaths.log.
* Service console logs and generated service logs under home/.klab/services/.
* sdk-probe-result.json, admin-probe-result.json, semantic-probe-result.json.
* unresolved-observable.json and anonymous-live-acceptance.xml.
* Pinned assets/imod clone, JVM argument/PID files and isolated state.

These temporary artifacts are not a portable/hermetic test environment. Initial
failed-attempt logs were overwritten by later launch logs; their specific errors
and remediation are recorded in local-stack.md. The unresolved response fixture
is retained in the repository. Raw state includes generated keys; review
sanitized reports/code rather than copying the whole state directory externally.

**All test-owned JVMs were stopped.** A fresh review must start the stack again;
current absent listeners are intentional cleanup, not evidence startup failed.

## 7. Reproduce the review checks

From the worktree root:

```powershell
git status --short --branch
git log --oneline -10
git diff 75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef..HEAD -- python-client
git diff -- python-client
```

Inspect untracked handoff/launcher/fixture files separately. From python-client/:

```powershell
$python = 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe'
& $python -m pytest -q
```

For a fresh environment, use README's `.[dev]` installation instructions.
For server compilation, required webui build, classpath generation, isolated
startup/probe/cleanup and FILE-project configuration, follow local-stack.md.
Java setup builds intentionally used -DskipTests; run relevant Java tests before
accepting a server fix, not an unrelated full build merely to inflate coverage.

After configuring **legitimate** per-service credentials, authorized peer scopes,
compatible scientific assets and the independent reference:

```powershell
& $python -m pytest -o "addopts=" -o "junit_family=xunit1" -m live -s tests/test_live.py --junitxml=live-acceptance.xml
& $python examples/elevation.py
```

Do not run with fabricated identity or invented reference values and call that
normal-user scientific acceptance. Local-admin/synthetic integration experiments
can be useful, but must be named as such with their remaining boundaries.

## 8. Specific questions for Astra

Please start with an independent read-only audit; separate observations from
inferences and proposed fixes. Review the current working tree, not just HEAD.

1. **Claims and evidence:** Are README/contract/PR statements accurately bounded
   by actual results? Do any method names/defaults imply authority, completion,
   units, ownership or compatibility that has not been established?
2. **Transport/identity:** Verify origin-bound credentials, redirects, redaction,
   ambiguous mutation outcomes and the genuine issued-credential path. Trace
   server authorization through owner identity, user-scope advertisement and
   peer context reconstruction; distinguish supported local administration.
3. **DTOs/semantics:** Independently compare Python serialization with Java
   @CLASS/field/null/enum conventions. Check geometry, normalized observable URNs,
   units/missingness, and the new NOTHING/VOID rejection. Audit what unknown wire
   fields are preserved versus silently discarded or given misleading defaults.
4. **Scopes/jobs:** Examine focus/observer grammar, ownership, local close versus
   remote release, cache expiration, cancellation races, wait deadlines and
   subscriber semantics. Check the null-outcome server defect against source.
5. **Scientific reads:** Verify StorageScan.Point defaults and native-source,
   temporal, unit and traversal semantics. Are unsupported query/consumer cases
   explicit? Can point reads report misleading scientific metadata?
6. **Tests/oracle:** Is the independent reference truly adequate and correctly
   located/georeferenced? Identify what mocked fixtures and zero-timeout or
   completed-cancel checks cannot prove. Assess missing live failure/isolation
   cases and assertions that could pass on echoed inputs or cached results.
7. **Setup:** Audit source-versus-installed classpath selection, isolated config,
   credential handling, occupied-port/PID checks and cleanup. Review the untested
   auth-package option and force-stop behavior; startup readiness is not science.
8. **Server repair scope:** Confirm the unit stub's causal role and design the
   smallest correct fix, including portable unit/URN preservation. Identify any
   other essential server changes before promising the example can complete.
9. **Performance:** Specify a scientifically correct minimal workload and
   benchmark plan: fresh versus cached/coalesced jobs, concurrency, latency
   percentiles, completion throughput, data transfer, memory/CPU/IO, failures and
   cleanup. Assess whether per-cell HTTP must be replaced with a supported bulk
   route before representative throughput can be measured.

Requested audit output:

* Prioritized findings with **file/line references**, evidence, impact, and a
  distinction between reproduced defects, source-based concerns and unverified
  assumptions. Include meaningful evidence contradicting a concern.
* A capability/acceptance matrix corrected where our handoff overstates support.
* Exact tests/commands run and outcomes; no skipped/preflight-failed integration
  reported as a pass.
* A minimal ordered remediation plan, separating client changes, essential server
  fixes, authorized configuration and scientific fixtures/data.
* Clear acceptance criteria for one complete scientific run and for subsequent
  throughput measurements. Do not certify full Java parity or maintainers'
  approval of this scope.

No publication, remote deployment, PR submission or merge has occurred in this
work. Do not expose credentials or discard unrelated work during the audit.
