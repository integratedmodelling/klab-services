# Historical verification and handoff

**Current results supersede the historical milestones below:** complete deterministic
scientific workflow and signed-JWT lifecycle acceptance pass on the isolated TEST
deployment. Fresh-state acceptance/throughput recipe and actual measurements are
in [throughput.md](throughput.md); the chronological [work log](work-log.md) records
the repairs and remaining production/real-provider boundaries.

Date: 4 October 2026. Branch: `feature/python-client` in the separate worktree
`C:\Users\lumsd\Downloads\k1\klab-python-client`.
Server-contract baseline: `75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`.
The specification's `e24b756f0815d2f51034d62282a175857f5be15c` could not be resolved
in the local object database; routes and payloads were reconciled against actual
checked-out controllers and Java client/mapper sources instead.

Implementation revisions can be identified with:

```powershell
git log --oneline 75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef..feature/python-client
```

## Actual verification commands and outcomes

Commands ran from `python-client/` unless noted. Temporary environments were
created under the harness's approved temporary directory, keeping generated
dependencies out of the worktree. `Test-Path -LiteralPath` verified parent paths
before venv/build creation.

```powershell
py -3.11 -m venv "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311"
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" -m pip install -e ".[dev]"
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" -m pytest -q
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" -m build
```

Original implementation milestone: editable install succeeded; offline suite **39 passed, 1 deselected**
on Python 3.11.9; isolated sdist and wheel builds succeeded. The deselected item
is the opt-in live test, not counted as an integration pass. The initial offline
run exposed a request-count assertion error (10 actual requests versus 9 expected);
that assertion was corrected and subsequent suites passed.

Clean wheel environment:

```powershell
py -3.11 -m venv "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-wheel-311"
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-wheel-311\Scripts\python.exe" -m pip install "C:\Users\lumsd\Downloads\k1\klab-python-client\python-client\dist\klab_python_client-0.1.0-py3-none-any.whl"
```

From `C:\Users\lumsd\AppData\Local\Temp\opencode` (outside source checkout):

```powershell
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-wheel-311\Scripts\python.exe" -I -c "import sys, klab_client; from klab_client.experiment import rectangle_geometry; from klab_client.errors import ConfigurationError; print(sys.version); print(klab_client.__file__); assert 'site-packages' in klab_client.__file__; assert rectangle_geometry().size() == 20; client = klab_client.Client('https://runtime.invalid'); client.close(); print('Built-wheel import, geometry and local lifecycle verified')"
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-wheel-311\Scripts\python.exe" -m pip check
```

Results: imported `klab_client` from the clean environment's site-packages;
checked the concrete experiment geometry and network-free local lifecycle;
`No broken requirements found.` Package import/client construction made no
network calls. `git diff --check` passed; Git's Windows line-ending notices are
not whitespace failures. No Java code changed; no broad Java build was required.

## Explicit live invocation — blocked, not passed

```powershell
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" -m pytest -o "addopts=" -m live -s tests/test_live.py
```

Outcome: **1 failed**, with the actionable configuration error listing missing
`KLAB_RUNTIME_URL`, `KLAB_RUNTIME_TOKEN`, `KLAB_REASONER_URL`,
`KLAB_REASONER_TOKEN`, and `KLAB_AGENT_NAME`. No remote scope was created and
no real scientific computation/data retrieval was attempted. There is therefore
**no measured live elevation result** and no live acceptance claim.

To complete acceptance, provide those endpoints/issued credentials, the
deployment's registered service IDs (`KLAB_SERVICE_IDS`, and originating runtime
ID for peer context requests), working runtime graph/storage, and the maintained
worldview/elevation model/resource covering the specified rectangle. Optionally
set `KLAB_CONTEXT_ID` to an accessible existing context. Then run the command
above and `examples/elevation.py`. A successful run must return actual Region and
Elevation observations, retrieve real storage values and assert the independent
metre-to-millimetre invariant. The source-derived offline fixtures do not replace
that result. The full-stack compatibility/acceptance boundary remains open.

## Public API and unsupported boundaries

New Client/Session/Context/Job workflow, closeable origin-bound transport,
explicit errors, scientific indexed-cell results, source-derived fixtures and
opt-in acceptance are implemented. Existing classes/ABCs and local Modeler/DTO
behavior are retained. Fake production service success is replaced by remote
requests or explicit exceptions. Runtime.submit/Resolver.resolve return Jobs;
Geometry.encode no longer serializes arbitrary values. Full existing-method
coverage and compatibility changes are listed in [public-api.md](public-api.md).

At the original milestone no essential missing server route was found for the
selected native-cell vertical slice, and no server fix was introduced. Its blocker
was unconfigured live access and scientific assets. The later actual local startup
investigation below supersedes that preliminary assessment with observed defects.
Unsupported: binary Avro worker contextualization/job data decoding; dataflow
encoding; bulk export/array adapters; automatic query/consumer geometry and
contextual-unit/range/currency conversion. Direct resolver resolution returns a
dataflow DTO and is not a default execution route.

Remaining review risk: real deployment authentication/scope advertisement,
polymorphic Java DTO compatibility, grid construction, provider selection and
unit-mediated storage data must be demonstrated live. Source references and
fixture provenance make these assumptions auditable without pretending they
were integration-tested.

## Follow-up: more thorough scientific acceptance

The follow-up starts from client revision
`a4a74eaf3549d419b3571021812e729ad6e204e2` and strengthens tests/documentation without
changing production package code. Current offline outcome on Python 3.11.9:
**52 passed, 2 deselected** using the same `-m pytest -q` command/environment above.
The added tests validate coordinate/traversal agreement, independent-reference
rejection of plausible but biased values, strict reference metadata/tolerances,
and the live harness's attach/resume/cleanup and failed-fixture retention paths
with explicitly synthetic HTTP. They do not establish any live pass.

Actual expanded live invocation:

```powershell
& "C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe" -m pytest -o "addopts=" -o "junit_family=xunit1" -m live -s tests/test_live.py --junitxml="C:\Users\lumsd\AppData\Local\Temp\opencode\klab-live-acceptance.xml"
```

Outcome: **2 failed**, zero skipped. The workflow test reported the same five
missing endpoint/credential/agent variables. The independent-reference test
reported missing `KLAB_ELEVATION_REFERENCE`. The JUnit failure report was written
to the specified temporary path; no remote scope or scientific result exists.
Relevant local listener checks (including YAML ports 8091, 8093, 8094) found no
listening services. No deployed endpoints/credentials were inferred from source
configuration, and no stack was launched or deployed.

[live-acceptance.md](live-acceptance.md) specifies the deeper suite, independent
reference provenance/schema, reproducible commands, per-cell evidence and exact
remaining live limits. In-flight cancellation, deterministic failed computation,
cross-user authorization and temporal/provider provenance still need suitable
deployment-owned fixtures. Completed-job cancellation and zero-budget timeout
must not be reported as proof of those different behaviors.

## Follow-up: actual local startup and probes

On 5 October 2026, starting from `490af42d4`, completed the build/startup/auth/asset
investigation requested after the checkpoint. [local-stack.md](local-stack.md)
records exact setup commands, main classes, dependency findings, discovered
failures and reproducible launcher/probe instructions.

All four services and the embedded Neo4j support application ran and exposed
actual HTTP endpoints. The public imod worldview was fetched at
`608bef150ced0a109db98a5aad64ba4461beaa54`, configured through supported FILE-project
startup configuration, and loaded by Reasoner. Actual public Python SDK calls
succeeded. Anonymous scientific API calls failed with HTTP 403. The live suite
was invoked against those endpoints and failed at session creation (403), with
its reference test separately failing for missing oracle data: **2 failed, zero
skips**. There was no completed scientific observation or throughput measurement.

Server defects observed: string-unit declarations are discarded by
SemanticsBuilder.withUnit(String); null import outcomes produce status:null in
JobManager. The older example Region namespace also resolved to owl:Nothing.
Corrected the example to earth:Region and made Python reject unresolved
observable results, backed by an actual captured response fixture.
Current offline verification: **53 passed, 2 deselected**, Python 3.11.9.
The validation JVMs are stopped after the investigation; generated data/logs
remain in the isolated temporary state directory for diagnosis.
