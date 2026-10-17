# Python/Java compatibility: current verified state

**Last verified: 17 October 2026.** Python base:
`3ef427d2058c2b1b140c92fbc4832a1c4dadc1e4`; current work is on
`feature/python-client-ide-throughput`. Changes are confined to `python-client/`.
No Java/API, worldview or ODO source was changed to obtain these results.

## Bottom line

**The Python client can authenticate to and use the real hosted Java services.**
The original semantic-publication failure is not universal to Python/Java
interaction: the hosted environment has accepted semantic knowledge and passes
the shared readiness gate. However, full scientific workflow compatibility is
**not certified**. The current hosted unit-preservation contract fails two actual
functional tests. Computation, conversion, exports, cancellation, saved-handle
recovery and restart/ACL workflows have not been rerun successfully on this
deployment.

The original report concerned a different, pinned local server/worldview/ODO
combination. Its failures remain valid historical evidence; that exact tuple has
not been rerun or demonstrated repaired in this pass.

## Verified compatibility matrix

| Capability / environment | Result | Evidence and limit |
|---|---|---|
| Offline unit/local-integration checks | **Passed** | 1,060 tests, zero failures/errors/skips; existing Ruff selection passed; not live scientific acceptance |
| Real ENGINE certificate authentication | **Passed** | Existing `HubCertificateProvider` authenticated through the certificate's matching HTTPS Hub; no TEST identity, disabled TLS, or SDK authentication workaround |
| Hosted service discovery and selection | **Passed** | Actual Python connection selected the four explicit HTTPS services below |
| Hosted model inventory | **Passed** | Resources advertises `im-main`, adopts `imod`, and returns 26 MODEL entries from `klab.staging.vxii`; entries can contain duplicate URNs; no suitable scientific reference model was selected |
| Hosted semantic publication/readiness | **Passed** | Accepted generation 1, bean/logical VALID, with 32 retained diagnostics; passing test ID below |
| Hosted observable preserves requested metres | **Failed** | Actual raw response lacks `unit`; `[m]` test below |
| Hosted observable preserves requested millimetres | **Failed** | Actual raw response lacks `unit`; `[mm]` test below |
| Paced semantic-identity diagnostic | **Passed, narrow scope** | Eight sequential requests, median 130.84 ms; NOT computation throughput, unit verification, independent scientific accuracy, or capacity testing |
| Local IDE service startup and identity handoff | **Passed at last local inspection** | Local services started and identify as the authenticated user after fixing the Java executable path and replacing stale anonymous processes; no source changes involved |
| Local IDE scientific readiness | **Blocked** | Local Resources has no workspaces/adopted worldview; local Reasoner input/accepted generation 0, NOT_CHECKED/UNAVAILABLE; no domain compilation was attempted |
| Original pinned local tuple, including exact ODO 0.1.0 capture | **Historical failure; not rerun** | Missing-import/domain failures recorded on 16 October, explained below |
| Full scientific computation/throughput, exports, cancellation, saved handles, Runtime restart and cold ACL restoration | **Not run in this pass / not accepted on this deployment** | Readiness and offline success do not establish these capabilities; owned-stack mutation/restart tests must not target shared services |

Full evidence, service IDs, source-manifest hashes, commands and timing samples:
[current verification record](results/ide-connected-semantic-verification.json).
Environment: Windows 11, Python 3.12.7. The deployed hosted binary Git revision
has **not** been established. An opaque worldview ID is not a source revision.

## Exact live test IDs and results

The current opt-in connected suite was **actually executed against the hosted
services**: **3 tests, 1 passed, 2 failed, 0 errors, 0 skips**, process exit 1.
These are ordinary failures, not expected-failure marks or converted skips.

**Passed:**

```text
tests/functional/test_connected_semantics.py::test_connected_reasoner_publishes_accepted_semantic_knowledge
```

**Failed:**

```text
tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit[m]
tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit[mm]
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
unjustified. No scientific computation was submitted, so this is **not evidence
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
functional tests pass. The **two current live failures above remain explicit**.
Full computation, cancellation, saved-handle, export, Runtime restart and ACL
acceptance remain separate unrun gates on this deployment. Existing deterministic
TEST-fixture tests may require Java test controls and must not simply target
Ferdinando's shared services or the user's IDE-managed processes.

No scientific throughput result is claimed. For the bounded read-only timing
commands and their limitations, see [IDE-connected testing](ide-connected-testing.md).
