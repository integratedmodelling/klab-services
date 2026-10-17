# Python/Java compatibility: current verified state

**Last verified: 17 October 2026.** Python base:
`3ef427d2058c2b1b140c92fbc4832a1c4dadc1e4`; current work is on
`feature/python-client-ide-throughput`. Changes are confined to `python-client/`.
No Java/API, worldview or ODO source was changed to obtain these results.
Progress-page retest code is pinned at
`a4dbc5f93693afbc433cbc858348b9cde85979c8`; the report-only follow-up does not
change the tested executables. This report is the merge handoff for PR #97 into
`feature/python-client`, **not develop**. The original SHA-pinned URL remains an
immutable historical report.

## Bottom line

**The Python client can authenticate to and use the real hosted Java services.**
The original semantic-publication failure is not universal to Python/Java
interaction: the hosted environment has accepted semantic knowledge and passes
the shared readiness gate. However, full scientific workflow compatibility is
**not certified**. The latest hosted checks total **7 tests: 4 passed, 3 failed,
zero errors/skips**, across two invocations. Unit preservation fails for metres
and millimetres; a tiny submitted terrestrial-region Job fails with an empty
dataflow. Saved **context** recovery in a genuinely fresh Python process passes;
this is not saved observation/Job recovery or server-restart durability.

The original report concerned a different, pinned local server/worldview/ODO
combination. Its failures remain valid historical evidence; that exact tuple has
not been rerun or demonstrated repaired in this pass.

## Verified compatibility matrix

| Capability / environment | Result | Evidence and limit |
|---|---|---|
| Offline unit/local-integration checks | **Passed** | 1,073 tests, zero failures/errors/skips; existing Ruff selection and feature coverage gates passed; not live scientific acceptance |
| Real ENGINE certificate authentication | **Passed** | Existing `HubCertificateProvider` authenticated through the certificate's matching HTTPS Hub; no TEST identity, disabled TLS, or SDK authentication workaround |
| Hosted service discovery and selection | **Passed** | Actual Python connection selected the four explicit HTTPS services below |
| Hosted model inventory | **Passed** | Resources advertises `im-main`, adopts `imod`, and returns 26 MODEL entries from `klab.staging.vxii`; entries can contain duplicate URNs; no suitable scientific reference model was selected |
| Hosted semantic publication/readiness | **Passed** | Accepted generation 1, bean/logical VALID, with 32 retained diagnostics; passing test ID below |
| Certificate reacquisition | **Passed** | `renew()` retains identity binding and selected service IDs; not deliberate credential expiry, principal switching, or independent issued-token onboarding |
| Onboarding/profile persistence | **Passed** | Four trusted operational services, nonempty model inventory, saved/reloaded profile equality |
| Session/context creation and release | **Passed** | Normal peer initialization and test-owned scopes; release confirmed in pilot reports and no cleanup errors in functional run |
| Fresh-process saved context recovery | **Passed** | Same context ID, different process ID, restored context is non-owning; no server restart |
| Tiny terrestrial-region computation | **Failed** | Submitted Runtime Job 3; HTTP requests succeed but retrieved result reports empty dataflow; no computed observation or cell values |
| Hosted observable preserves requested metres | **Failed** | Actual raw response lacks `unit`; `[m]` test below |
| Hosted observable preserves requested millimetres | **Failed** | Actual raw response lacks `unit`; `[mm]` test below |
| Paced semantic-identity diagnostic | **Passed, narrow scope** | Eight sequential requests, median 130.84 ms; NOT computation throughput, unit verification, independent scientific accuracy, or capacity testing |
| Local IDE service startup and identity handoff | **Passed at last local inspection** | Local services started and identify as the authenticated user after fixing the Java executable path and replacing stale anonymous processes; no source changes involved |
| Local IDE scientific readiness | **Blocked** | Local Resources has no workspaces/adopted worldview; local Reasoner input/accepted generation 0, NOT_CHECKED/UNAVAILABLE; no domain compilation was attempted |
| Original pinned local tuple, including exact ODO 0.1.0 capture | **Historical failure; not rerun** | Missing-import/domain failures recorded on 16 October, explained below |
| Cell values/conversion, exports, analysis, observation provenance | **Blocked downstream / not run** | No successful computed observation; not additional independently reproduced failures |
| Active cancellation, saved observation/Job recovery, Runtime restart and cold ACL restoration | **Not certified** | Need executable model, controllable active work, another identity and/or owned stack; no shared/IDE service restart attempted |

Full evidence, service IDs, source-manifest hashes, commands and timing samples:
[current verification record](results/ide-connected-semantic-verification.json).
The subsequent progress-page retest has its own
[verification record](results/progress-page-connected-verification.json), including
test IDs, commands, artifact hashes and packaging correction.
Environment: Windows 11, Python 3.12.7. The deployed hosted binary Git revision
has **not** been established. An opaque worldview ID is not a source revision.

## Exact live test IDs and results

The expanded opt-in connected suite was **actually executed against the hosted
services**: the six-test invocation returned **3 passed, 3 failed**, exit 1;
the separate renewal invocation returned **1 passed**, exit 0. Combined:
**7 tests, 4 passed, 3 failed, 0 errors, 0 skips**.
These are ordinary failures, not expected-failure marks or converted skips.

**Passed:**

```text
tests/functional/test_connected_semantics.py::test_connected_reasoner_publishes_accepted_semantic_knowledge
tests/functional/test_connected_workflows.py::test_connected_onboarding_and_profile_roundtrip
tests/functional/test_connected_workflows.py::test_connected_context_recovers_in_a_fresh_process
tests/functional/test_connected_workflows.py::test_connected_certificate_renewal_preserves_identity
```

**Failed:**

```text
tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit[m]
tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit[mm]
tests/functional/test_connected_workflows.py::test_connected_tiny_terrestrial_region_completes
```

The readiness test invokes the **same**
`workflows.develop_compatibility.require_semantic_readiness` gate used by the old
compatibility tests, borrowing an authenticated client. The old TEST-state call
path and assertions are unchanged. No real token is written to `scientist.json`,
and the borrowed client is not closed by that helper.

The unit tests issue a raw Reasoner request **before DTO conversion**, also call
the SDK's observable resolver, and check the requested unit. Their actual failure:

```text
AssertionError: Reasoner wire response does not preserve requested unit m; Python must not invent mediation
AssertionError: Reasoner wire response does not preserve requested unit mm; Python must not invent mediation
```

Source: [live connected tests](../tests/functional/test_connected_semantics.py),
[connected workflow tests](../tests/functional/test_connected_workflows.py),
[named-suite mapping](../tools/run_functional_acceptance.py),
[shared readiness gate](../tools/workflows/develop_compatibility.py).

### Reproduce the current hosted results

Use the Python branch/revision containing the connected tests above. **They are
newer than the original frozen `3ef427d20` report.** From its `python-client/`
directory, install the package and test dependencies:

```powershell
python -m pip install -e ".[dev]"
```

Review [the secret-free profile](../examples/hosted-deployment.json). It explicitly
trusts these destinations; the observed Hub advertisements matched them:

```text
Hub:       https://integratedmodelling.org/hub
Resources: https://services.integratedmodelling.org/resources/main
Reasoner:  https://services.integratedmodelling.org/reasoner
Resolver:  https://services.integratedmodelling.org/resolver
Runtime:   https://services.integratedmodelling.org/runtime/main
```

Use your own valid private ENGINE certificate and an existing parent directory.
Do not upload certificates/tokens or paste them into profiles/reports. Create a
fresh report directory, then run:

```powershell
$reports = "C:\existing-parent\fresh-connected-tests"
New-Item -ItemType Directory -Path $reports -ErrorAction Stop | Out-Null
python tools/run_functional_acceptance.py --suite connected-semantic-readiness --suite connected-unit-contract --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --report "$reports\connected.xml"
$testExit = $LASTEXITCODE
```

No `--state-dir` or synthetic fixture is required for these two connected suites.
The runner requires explicit profile/certificate paths; it does not silently use
ambient credentials. XML records the request, presence/absence of the unit in the
wire and SDK DTO, and accepted-generation evidence. File/configuration errors,
authentication failures and connection timeouts are different outcomes, not
reproduction of the unit failure.

For a direct pytest invocation of a single failing parameter:

```powershell
$env:KLAB_CONNECTED_PROFILE = (Resolve-Path examples/hosted-deployment.json).Path
$env:KLAB_CONNECTED_CERTIFICATE = "$HOME\.klab\klab.cert"
python -m pytest -o addopts= -o junit_family=xunit1 -m functional -vv -s "tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit[m]" --junitxml="$reports\metres.xml"
```

Replace `[m]` with `[mm]` for the other failing case. `-o addopts=` is essential:
ordinary pytest excludes `live` tests. Missing configuration fails, not skips.

These tests use `connect(..., initialize=False)`. They authenticate, read service
metadata and resolve observables; semantic resolution can warm server caches.
They **do not** update peer catalogs, import/edit projects, create sessions,
contexts or observations, or stop/restart services. Do not turn this small shared
environment diagnostic into an unapproved load test.

### Reproduce the expanded progress-page retest

Use checkout `a4dbc5f93693afbc433cbc858348b9cde85979c8` (or the report-only
follow-up/merge containing those exact tests). From `python-client/`, install
`python -m pip install -e ".[dev,local,analysis]"` for the complete offline checks.
For the connected tests alone, the core package plus `[dev]` suffices. Use the
same explicit profile and your own valid certificate as above. Create a **fresh**
report directory; every XML destination must be new.

The following reproduces the two actual invocations, rather than implying a
single seven-test execution:

```powershell
$reports = "C:\existing-parent\fresh-progress-retest"
New-Item -ItemType Directory -Path $reports -ErrorAction Stop | Out-Null
python tools/run_functional_acceptance.py --suite connected-semantic-readiness --suite connected-unit-contract --suite connected-onboarding --suite connected-context-recovery --suite connected-tiny-compute --allow-owned-work --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --report "$reports\progress-page-retest.xml"
$workflowExit = $LASTEXITCODE # observed 1: three real failures
python tools/run_functional_acceptance.py --suite connected-credential-renewal --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --report "$reports\progress-renewal.xml"
$renewalExit = $LASTEXITCODE # observed 0
```

`--allow-owned-work` explicitly authorizes normal peer initialization and creation
and release of **only this test's** sessions/contexts and one tiny Job. These
suites never use TEST controls, import projects, restart services, or release
other users' work. The runner rejects mixing them with owned TEST-stack suites.
The context fixture attempts context and session cleanup even after failures;
cleanup errors are not silently converted into success. Direct pytest execution
of work tests additionally requires `KLAB_CONNECTED_ALLOW_OWNED_WORK=1`.

To isolate the computation failure with the named suite:

```powershell
python tools/run_functional_acceptance.py --suite connected-tiny-compute --allow-owned-work --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --report "$reports\tiny-compute.xml"
```

To rerun each passing check alone, use its named suite:
`connected-semantic-readiness`, `connected-onboarding`,
`connected-credential-renewal`, or `connected-context-recovery` (the last requires
`--allow-owned-work`). Use a distinct fresh XML path on each invocation.

## Progress-page checklist: actual scope and blocked test IDs

Checklist source: [Python-client branch progress, 15 October preview](https://eric-resume.up.railway.app/python-client-progress/).
Its seven recordings use controlled local TEST services and synthetic models.
They were **not** rerun unchanged against hosted services: TEST gates, expected
values, component build and collaborator credentials cannot be assumed there.

| Demo | Hosted result and remaining boundary |
|---|---|
| 1. Connect and see services | Certificate onboarding, model listing, profile roundtrip and renewal pass. Independent issued-token onboarding, deliberate expiry and principal switching remain unrun. |
| 2. Area/observation/options | Typed 5 × 4 geometry and namespace request submitted; Job fails before a computed result. Cell values, m/mm conversion, scenario comparisons, observer and grid execution remain blocked/unrun. |
| 3. Job control | Submission, status polling, retrieval and failed-result reporting exercised. No confirmed active cancellation, timeout recovery or successful retained Job recovery; cancellation of an already failed Job returned false in pilots and is not a cancellation pass. |
| 4. Close/reopen work | Saved context restored in a different Python process, with same ID and non-owning semantics. Saved observation, Runtime/Resolver Job results and value equality remain unrun; no Runtime restart. |
| 5. Native exports | No computed observation, so GeoJSON/GeoTIFF discovery/download, masks, units, orientation and overwrite protection were not accepted against this hosted stack. |
| 6. xarray/recorded origins | Blocked by absence of a computed/exported field. Offline adapter/provenance tests pass but do not certify hosted lineage or scientific accuracy. |
| 7. Shared/private access | No second scientist's credentials supplied. Positive sharing control, private restoration/export/provenance denial and cold ACL checks remain unrun. |

Existing controlled-suite IDs below identify the **unrun acceptance gates**, not
additional current hosted failures. Their fixtures require a separately prepared
owned TEST stack; do not point their restart/admin/test-control workflows at the
shared deployment. To reproduce those controlled gates, use the existing
[functional-testing runbook](functional-testing.md) and its prepared state recipe.

```text
# Value reads, conversion and concurrency
tests/functional/test_compute_and_analyze.py::test_signed_authentication_scientific_lifecycle
tests/functional/test_compute_and_analyze.py::test_actual_concurrent_full_workflow
# Typed scope/observer/scenario/grid execution
tests/functional/test_typed_compute.py::test_typed_observe_options_are_honored_by_actual_scientific_execution
# Retained observation and Job references across processes
tests/functional/test_resume_saved_work.py::test_real_saved_handles_between_fresh_python_processes
# Actual native exports
tests/functional/test_export_deliverables.py::test_native_geojson_export_preserves_identity_and_known_geometry
tests/functional/test_export_deliverables.py::test_native_geotiff_export_preserves_values_units_and_mask
# Analysis, recorded relationships and notebook journey
tests/functional/test_scientific_analysis.py::test_scientific_analysis_and_recorded_origins
tests/functional/test_scientific_analysis.py::test_release_notebook_journey_uses_connections_and_recovers_the_original_field
# Sharing/private access and owned Runtime restart
tests/functional/test_connection.py::test_connection_access
tests/functional/test_develop_compatibility.py::test_python_and_java_services_work_before_and_after_runtime_restart
# Separate deployed-provider and independent-reference gates
tests/functional/test_provider_reference.py::test_live_elevation
tests/functional/test_provider_reference.py::test_live_elevation_reference
```

The page's async/events, broader scientific views, authoring, behaviors,
federation and durable-replay items are a **roadmap**, not implemented features
certified by these seven tests. No new acceptance claim is made for them.

## Current issue: tiny region Job returns an empty dataflow

**Failing ID:**
`tests/functional/test_connected_workflows.py::test_connected_tiny_terrestrial_region_completes`.

**Inspected inputs:** Resources retrieves
`staging.vxii.dependencies.terrestrial-region-explainer`; it advertises
`earth:Terrestrial earth:Region` with dependency `geography:Elevation in m`.
An inspected elevation model invokes `klab.generators.geospatial.terrain()`;
this is a synthetic generator, **not an independent real-terrain reference**.
Namespace is a lexical/ranking constraint, not forced model selection or evidence
that the deployed resolver selected that model. No resources/worldview were edited.

The test creates an `EXPLICIT_ACTION` context and submits:

```python
ObservationRequest(
    "earth:Terrestrial earth:Region",
    geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4),
    options=ObservationOptions(namespace="staging.vxii.dependencies"),
)
```

The rectangle is 500 × 400 projected metres in EPSG:3857, requesting 20 cells.
Runtime accepts submission and returns a Job ID; the test waits at most 90
seconds. Recorded functional failure (Job IDs are run-specific):

```text
JobFailedError: Job 3: Resolution of earth:Terrestrial earth:Region failed: empty dataflow; Submission failed
```

Earlier bounded pilots also failed for plain `earth:Region` and the corrected
terrestrial/namespace request. Session/context creation, semantic resolution,
submit, poll, retrieve and release HTTP calls returned 200; **HTTP 200 and Job
FINISHED do not establish scientific success** when the result contains errors.
Pilot context/session releases returned true. One preliminary pilot had a local
script `TypeError` from an unsupported `models_allowed` keyword; it was corrected
to the existing namespace option before the actual functional run. It is not a
separate SDK or Java defect.

**Impact:** no successful computed observation, values, verified grid, native
units or scientific throughput. Subsequent export/analysis gates cannot be
meaningfully certified with this result. The bounded request does not establish
that every deployed model fails or that computed values are wrong.

**Platform handoff:** establish the actual deployed binary revisions; correlate
Runtime/Resolver/Resources logs using the test-owned context and Job ID; inspect
strategy selection, model applicability/permissions, coverage, dependency
resolution and component availability. In inspected local Java source,
the `RuntimeService` resolution continuation reports empty dataflow after
empty or unsuccessful compilation/commit paths. That message is a symptom, not
a proven loss point or proof the deployed source matches. Supply a supported
executable model/namespace/geometry pairing or correct the owning deployed layer,
then rerun the exact failing ID without suppressing `JobFailedError`. No Python
retry or synthetic result can legitimately repair this server result.

## Python fixes and reproducible local/package validation

Commit `a4dbc5f93` adds the connected workflow tests and:

- Includes **only** `examples/hosted-deployment.json` explicitly in `MANIFEST.in`.
  The original source archive omitted this documented public profile; the rebuilt
  archive contains it. Private/user-created JSON profiles are not broadly included.
- Requires explicit `--allow-owned-work`, guards direct work-test invocations,
  clears ambient mutation authorization on read-only runs and rejects mixing
  shared-deployment suites with owned TEST-stack suites.
- Adds offline regressions in `tests/unit/test_connected_distribution.py`,
  `tests/unit/test_connected_workflow_safety.py` and
  `tests/unit/test_functional_runners.py`. No Java/semantics fix is claimed.

From the pinned checkout's `python-client/` with `[dev,local,analysis]` installed:

```powershell
python -m pytest -q tests/unit tests/integration --junitxml="$reports\offline.xml"
python -m pytest -q tests/unit tests/integration --cov=klab_client --cov-branch --cov-report=json:"$reports\coverage.json"
python tools/check_feature_coverage.py "$reports\coverage.json" --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/requests.py --new-module src/klab_client/handles.py --new-module src/klab_client/exports.py --new-module src/klab_client/connection.py --new-module src/klab_client/connection_models.py --new-module src/klab_client/credentials.py --new-module src/klab_client/_bootstrap.py --new-module src/klab_client/analysis.py --new-module src/klab_client/provenance.py
python -m ruff check src tests examples tools --select E9,F63,F7,F82
python -m build --outdir "$reports\dist"
```

Observed final offline run: **1,073 passed**, zero failures/errors/skips, one
existing Rasterio `NotGeoreferencedWarning`. The preceding branch-coverage run
contained 1,072 tests before the final renewal suite-routing parameter was added;
all existing 100% new-module line/branch and changed-code coverage gates passed.
Lint, sdist and wheel builds passed. A fresh core-only virtual environment installed
the wheel and passed Client/Handle roundtrip smoke checks with NumPy, Rasterio
and xarray absent. These are separate from the live scientific failures.

To verify the distribution correction and repeat the core-only smoke:

```powershell
python -c "import tarfile; t=tarfile.open(r'$reports\dist\klab_python_client-0.1.0.tar.gz'); assert 'klab_python_client-0.1.0/examples/hosted-deployment.json' in t.getnames()"
python -m venv "$reports\core-only"
& "$reports\core-only\Scripts\python.exe" -m pip install "$reports\dist\klab_python_client-0.1.0-py3-none-any.whl"
& "$reports\core-only\Scripts\python.exe" -I -c "import importlib.util,sys; from klab_client import Client,Handle; assert all(importlib.util.find_spec(n) is None for n in ('numpy','rasterio','xarray')); h=Handle('observation','home','https://runtime.invalid/runtime','s.c','s.c',observation_id=42); assert Handle.loads(h.dumps())==h; c=Client('https://runtime.invalid/runtime','secret'); c.close()"
```

## Current issue: requested units are missing from the deployed response

**Trigger:** authenticated `POST /api/v1/resolve/observable` on the selected hosted
Reasoner, with text/plain request bodies:

```text
geography:Elevation in m
geography:Elevation in mm
```

**Observed:** both return semantic URN and observable URN `geography:Elevation`,
numeric artifact type, and **no `unit` field**. The complete decoded wire payloads
were identical under canonical JSON hashing:

```text
de827c664051702ed4304f2f139b721088fb4ed9481f1a7d113688807979a7b8
```

**Python impact:** authentication, discovery and semantic identity resolution work,
but the current value/unit-conversion workflow cannot verify the requested
mediator. Reporting scientifically valid metre/millimetre throughput would be
unjustified. The unit checks submit no computation; the separate tiny-region
computation attempt above failed before producing values. This is **not evidence
that computed values are wrong**.

**Ownership/uncertainty:** Python did not discard the unit; it is absent before
DTO conversion. The deployed Java binary revision and any replacement mediation
contract are unknown. This is a demonstrated wire-contract gap for the existing
Python workflow, **not a proven Java root cause**. If a different supported
contract is intended, provide its specification for Python adaptation. Python must
not synthesize response units from the requested string.

### Java/platform investigation handoff for this issue

1. Reproduce both unit tests with the same explicit endpoints; identify deployed
   Reasoner, Resources, parser and API builds before comparing source behavior.
2. Trace the request through `ReasonerController.resolveObservable`,
   `ReasonerService.resolveObservableInternal`, Resources observable parsing,
   `declareObservable`/`declare`, and `SemanticsBuilder.buildObservable`. Inspect
   parsed units, constructed Unit, observable URN, candidate-actor copy/bridge and
   final HTTP serialization. Compare m versus mm at each boundary.
3. The inspected source at develop `d8e16b277a616b63165b857018722bba658c0007`
   contains `Observable.getUnit()`, unit handling in `ReasonerService.declare`
   and `SemanticsBuilder.buildObservable`. That does not establish what the
   hosted binary implements or identify the loss point.
4. Correct the owning layer or specify the supported alternative; preserve unit
   distinctions and add Java regressions across the actual private-candidate and
   HTTP paths. Rerun the two live Python tests without weakening their checks.
5. Only then validate a tiny test-owned scientific computation and its actual
   values, units, geometry, missingness and readback before reporting computation
   throughput. Independent provider accuracy is a separate reference gate.

## Original pinned-worldview findings: retained, not declared fixed

The [original frozen report](https://github.com/integratedmodelling/klab-services/blob/3ef427d2058c2b1b140c92fbc4832a1c4dadc1e4/python-client/docs/develop-worldview-compatibility-issue.md)
contains the complete historical Java call chain and original reproduction
runbook. Exact inputs:

| Input | Original identity |
|---|---|
| Java develop | `671a8181551a51b8a6b8080c3a13a2bf1cd0f048` |
| IMOD | `608bef150ced0a109db98a5aad64ba4461beaa54` |
| ODO | Release 0.1.0 from commit `aa2cc5611799ceed2aad52ae2c2a349da29e3c4b` |
| ODO SHA-256 | `eb7318d725a16bd6e68a4cc12955c7b94b885a48d9c3c4f7837d4cd24a8ec7e4` |

### Original issue A: uncaptured import

With no exact local ODO capture, the original Reasoner reported generation zero,
logical UNRESOLVED, and `CANDIDATE_ISOLATION_UNSUPPORTED: uncaptured OWL import`
for the declared ODO 0.1.0 URL. Exact dependency provisioning in the Python test
tooling removed that diagnostic. It did not alter or bypass Java isolation.

### Original issue B: domain compilation after provisioning

With the exact import supplied, both the combined candidate and **unchanged
develop** reported:

```text
acceptedGeneration: 0
beanStatus: VALID
logicalStatus: FAILED
Candidate build failed: ONTOLOGY_DOMAIN_INVALID: land requires a resolved domain
```

The throw site is `WorldviewDomainSupport.validate`, called from
`ReasonerService.compileStagedOntologies`. It rejects a null/NOTHING/non-DOMAIN
resolution. The old `land` declaration used
`in domain imod:Knowledge of (imod:Geosphere or imod:Biosphere)`.
The same guard still exists in inspected develop `d8e16b277`; it was not removed.

**Historical failing test IDs / symptoms:**

```text
tests/functional/test_develop_compatibility.py::test_python_and_java_services_work_before_and_after_runtime_restart
tests/functional/test_develop_compatibility.py::test_reasoner_publishes_accepted_semantic_knowledge
tests/functional/test_compute_and_analyze.py::test_signed_authentication_scientific_lifecycle
tests/functional/test_compute_and_analyze.py::test_actual_concurrent_full_workflow
```

The full compatibility test failed at readiness, **before** computation/restart
assertions. The minimal readiness test was actually run for the post-provisioning
domain failure; its missing-import variant was a documented recipe, not a recorded
execution of that new minimal test. The two original scientific tests encountered
Reasoner HTTP 500, `Accepted semantic knowledge is unavailable`.

Historical evidence: [initial missing-import failure](results/develop-compatibility-verification.json),
[exact-dependency failure/baseline comparison](results/develop-semantic-dependencies-verification.json),
[minimal readiness runner verification](results/develop-readiness-runner-verification.json).
Their commands/pins must be used to reproduce the **old** environment; do not load
old inputs into the shared hosted services to recreate a negative test.

### What the reinspection establishes—and does not

- Hosted services pass both the shared gate and the new connected readiness test
  with accepted generation 1. Neither original error was exposed there.
- The old candidate and hosted accepted-source maps each contain 28 entries, with
  27 common keys and **zero matching hashes** among those keys. Hash differences
  are not a count of semantic changes; line endings may also differ. Neither
  input equivalence nor a controlled old-input before/after repair is established.
- The local IDE stack has not loaded a worldview, so neither original failure
  has been exercised there after authentication.
- New upstream Java validation documentation names IMOD `253d1046a1e04b4b45bc67a32b93a65829faa2f5`
  and ODO 0.1.1. The public ODO 0.1.1 text matches the documented private core
  after CRLF conversion; the inspected newer `land` uses a simpler domain clause.
  This is a traceable alternative to investigate, **not** a locally deployed fix
  or proof that it matches the hosted corpus. See [input investigation](ide-connected-testing.md#local-worldview-investigation-traceable-inputs-not-a-deployed-fix).

For the old tuple, platform owners must still distinguish compiler defects from
unsupported/invalid worldview/ODO semantics. Reproduce in an isolated stack,
inspect parsed domain/resolver return/type, preserve candidate isolation and
last-known-good generation behavior, and obtain a supported input pairing or
correct the owning source. No Python retry, DTO coercion or relabeled ontology
can manufacture accepted server-owned scientific meaning.

## Remaining acceptance boundary

Passing offline tests and the connected readiness test does not mean all
functional tests pass. The **three current live failures above remain explicit**.
Context recovery passes, but successful computation, cancellation, saved
observation/Job, export, Runtime restart and ACL acceptance remain separate
unaccepted gates on this deployment. Existing deterministic
TEST-fixture tests may require Java test controls and must not simply target
Ferdinando's shared services or the user's IDE-managed processes.

No scientific throughput result is claimed. For the bounded read-only timing
commands and their limitations, see [IDE-connected testing](ide-connected-testing.md).
