# Functional workflows and test organization

Tests are grouped by execution level; functional suites are named for user outcomes.

Current readiness, test results and deployment/component dependencies are recorded
once in [export and functional verification](exports-verification.md).

**16 October integration check:** `develop-compatibility` remains **blocked**, not
green. The initial missing numbered ODO import was reproduced on unchanged develop.
Provisioning its exact source bytes removes that error, but candidate construction
then fails with `ONTOLOGY_DOMAIN_INVALID: land requires a resolved domain`.
See the [initial result](results/develop-compatibility-verification.json) and
[dependency-provisioned rerun](results/develop-semantic-dependencies-verification.json).
Scientific/restart assertions behind the readiness gate were not reached.

See [the detailed issue report and Java/source locations](develop-worldview-compatibility-issue.md)
for the observed call chain, comparison boundaries and unresolved platform question.

```text
tests/
  unit/          Offline contracts, validation, file semantics and oracle/rate guards
  integration/   Local HTTP + real subprocess checks without k.LAB services
  functional/    Complete user workflows against real configured services
  fixtures/      Shared independent reference payloads and subprocess fixtures
  conftest.py    Offline network guard and test-only import setup
tools/
  workflows/     Shared scientific scenarios, validation and measured repetition
  run_functional_acceptance.py
  run_workflow_throughput.py
```

## Named acceptance suites

| Suite | User outcome | Shared implementation |
|---|---|---|
| `compute-and-analyze` | Compute, read in metres/millimetres, reconnect; validate Jobs, identity, access and lifecycle | `workflows/compute_and_analyze.py`, `verify_fixture.py` |
| `develop-compatibility` | Python scientific roundtrips against the combined Java services before/after an actual Runtime-only restart; retain graph and verify private/shared/excluded context ACLs | `workflows/develop_compatibility.py`, `cold_context_check.py` |
| `resume-saved-work` | Save original work and restore/share it in separate authorized Python processes | `workflows/resume_saved_work.py`, existing saved-handles example |
| `typed-compute` | Execute supported typed requests and reject an invalid bound observer | existing typed-observe example |
| `geojson-export` | Produce independently verified geographic geometry, including fresh-process export | `workflows/geojson_export.py` |
| `geotiff-export` | Compute, save, restore and export a raster preserving every coordinate/value, unit and missing-cell mask | `workflows/export_deliverables.py` |
| `scientific-analysis-provenance` | Restore original numerical work in a fresh process, open labeled values/validity, inspect recorded execution links and test denied access | `workflows/scientific_analysis.py` |
| `release-notebook` | Certificate-based typed computation followed by fresh-process token-provider restoration, native export, analysis and recorded origins | Same scientific workflow with connection providers; no duplicate scientific oracle |
| `provider-reference` | Verify real configured model output, optionally against separately obtained reference data | `workflows/provider_reference.py`, `workflows/scientific_checks.py` |

`functional` identifies complete user workflows. `live` identifies their real-service
requirement. Functional tests carry both markers, so ordinary pytest still excludes
them. Local subprocess integration has its own marker and remains in offline CI.
No assertion was converted into a skip to make the reorganization pass.

## Captured semantic dependencies

Current develop's Reasoner requires explicit captured ontology imports before it
can publish accepted semantic knowledge. Capture the worldview's **declared** ODO
0.1.0 document from immutable upstream commit
`aa2cc5611799ceed2aad52ae2c2a349da29e3c4b`, with SHA-256
`eb7318d725a16bd6e68a4cc12955c7b94b885a48d9c3c4f7837d4cd24a8ec7e4`:

```powershell
python tools/semantic_dependencies.py --output C:\fresh-semantic-catalog
python tools/run_local_acceptance.py --server-repo C:\explicit-server-checkout --java C:\jdk21\bin\java.exe --worldview C:\pinned-imod --semantic-catalog C:\fresh-semantic-catalog --state-dir C:\fresh-owned-state --build --saved-handles --typed-observe
```

The capture is bounded, rejects redirects and mismatching bytes, and creates a
fresh directory. The fixture validates the bytes and `source-catalog.properties`
before staging them into its private `home/.klab/knowledge`; it records the source,
declared locator and hash in `semantic-dependencies.json`. Existing knowledge is
never overwritten. The exporter-enabled runner and direct `fixture_hub.py prepare`
also accept `--semantic-catalog`. Omit it only when deliberately testing an older
server contract or the missing-dependency failure.

This supplies the actual numbered release, **not** bundled ODO bytes relabeled as
that release. No Java/API, worldview, or ontology source is patched, and publication
checks remain enabled. Correctly provisioning an import does not make an invalid
semantic candidate acceptable; the current pinned worldview still fails the
domain check noted above.

`--server-repo` selects the Java checkout for **all** builds, classpaths, fixture
controls and initial/restarted services. If omitted, the runner uses the repository
containing the Python tools; it does **not** select develop automatically. The
console and `run-report.json` identify the selected checkout, HEAD commit, dirty
state and merge parent (if any). A dirty flag is not a complete source manifest;
use a clean pinned checkout and `--build` for reproducible comparisons. Without
`--build`, compiled outputs must already match that selected source revision.

Unchanged develop may lack the Python scientific Runtime controls. Reproduce
semantic publication independently without requiring or installing those controls:

```powershell
python tools/run_local_acceptance.py --server-repo C:\pinned-develop --java C:\jdk21\bin\java.exe --worldview C:\pinned-imod --semantic-catalog C:\fresh-semantic-catalog --state-dir C:\fresh-readiness-state --build --readiness-only
```

This mode starts only graphdb/Resources/Reasoner, runs the named `semantic-readiness`
suite and stops its owned services/authority. `passed` means readiness only;
`scientific_acceptance` remains false even on a readiness pass. There are no
scientific/restart/ACL/throughput assertions in this mode. Full acceptance still
requires compiled Runtime controls; the original prerequisite is not relaxed.
See the [issue runbook](develop-worldview-compatibility-issue.md#reproduction-and-evidence)
for exact revisions, prerequisites, the expected failure and Java investigation steps.

`develop-compatibility` is deliberately **mutating and owned-stack-only**. It
validates all five PID/argument-file identities before starting, restarts only
the owned Runtime, and verifies graphdb/Resources/Reasoner/Resolver keep their
original PIDs. Do not point it at production/shared state. It requires `[local]`
and Windows, as does the existing owned-stack launcher. The default
`run_local_acceptance.py` now invokes this named suite instead of keeping its
restart checks outside functional pytest.

The suite preserves the explicitly compiled server checkout when restarting,
even when the Python tools are in another checkout. It checks accepted semantic
readiness before scientific mutations: an available endpoint or local-authoring
operational flag is insufficient when `semantic.candidate.acceptedGeneration`
is zero. Pending/invalidation states receive a bounded readiness wait; rejected
or unresolved candidates fail with their diagnostics. The last redacted status
is retained as `develop-readiness.json`.

The test verifies the known 20-cell field in metres and millimetres (40 checks)
on each side of the restart, reuses actual context permission/restoration checks,
and reports JUnit properties plus `develop-compatibility.json` in the private
state directory. It does not claim persistent scientific buffer durability,
native Java-client interoperability, nonlocal shared-session acceptance, or
production Hub/provider accuracy.

## Run

### Existing authenticated deployments (read-only)

The current hosted compatibility results and exact passing/failing node IDs are
in [the compatibility report](develop-worldview-compatibility-issue.md). These
two suites require explicit trusted endpoints and a private ENGINE certificate,
not an owned TEST state:

```powershell
python tools/run_functional_acceptance.py --suite connected-semantic-readiness --suite connected-unit-contract --profile examples/hosted-deployment.json --certificate "$HOME\.klab\klab.cert" --report C:\existing-parent\fresh-connected.xml
```

They perform authentication, metadata reads and semantic resolution only. They
do not initialize peer catalogs, create scientific objects, import projects or
restart services. Do not substitute the mutating owned-stack suites below when
targeting shared services. These opt-in tests are excluded from ordinary pytest;
missing configuration is a failure, not a skip.

From `python-client/`, offline checks:

```powershell
python -m pytest tests/unit tests/integration
```

Against an already running explicitly prepared TEST stack:

```powershell
python tools/run_functional_acceptance.py --state-dir C:\state --suite compute-and-analyze --suite typed-compute --suite resume-saved-work --report C:\state\functional.xml
python tools/run_functional_acceptance.py --state-dir C:\state --suite geojson-export --suite geotiff-export --report C:\state\exports.xml
python tools/run_functional_acceptance.py --state-dir C:\owned-test-state --suite develop-compatibility --report C:\owned-test-state\develop-compatibility.xml
```

Reports require a fresh destination. Export suites require the appropriate genuine
installed component; GeoTIFF requires the corrected exporter and optional
`[throughput]` Rasterio dependency. Missing configuration or failed scientific
validation is a failure, not a skip. The provider-reference suite uses its separate
environment/reference contract from [live acceptance](live-acceptance.md).

The `scientific-analysis-provenance` suite requires `[analysis]` and the corrected
component. Its observed neighborhood remains partial and cross-fetch consistency
unknown even when acceptance passes. See [analysis workflow](scientific-analysis.md).

For owned stack creation/cleanup, use `run_local_acceptance.py` or
`run_export_acceptance.py`. Both invoke these named suites; the export runner keeps
GeoJSON acceptance separate from optional numerical throughput. Explicit server,
component and worldview provenance remain required.

The exporter-enabled owned runner can additionally select the other local suites
using repeated `--functional-suite compute-and-analyze`,
`--functional-suite resume-saved-work`, `--functional-suite typed-compute` and
`--functional-suite geotiff-export` options. Fixture preparation uses the current
scenario models and an explicitly selected server checkout's compiled Runtime
controls, so an older preparation script cannot silently omit typed scenario models.

Measured repetition uses the **same scenario functions**:

```powershell
python tools/run_workflow_throughput.py --workflow compute-and-analyze --fixture-config C:\state\scientist.json --server-state-dir C:\state --samples 8 --duration 15 --warmup 1 --concurrency 2 --report C:\state\compute-throughput.json
python tools/run_workflow_throughput.py --workflow export-deliverables run --fixture-config C:\state\scientist.json --server-state-dir C:\state --work-dir C:\state --samples 3 --duration 15 --warmup 1 --report C:\state\export-throughput.json
```

Compute supports `full-workflow` and `repeated-read`; numerical export supports
`full-roundtrip` and `repeated-export`. The one-sample GeoTIFF acceptance invokes
the same numerical runner with no warmup and no minimum measurement duration.
Warmups/seed are excluded from throughput; failures invalidate successful rates.
The old `workflow_benchmark.py` and `geotiff_throughput.py` commands are thin
compatibility entry points, not duplicate scenario implementations.

Multi-area batch modelling and richer nonuniform selection/conversion benchmarks
remain proposed additions. They have not been implemented by this reorganization.

## Validation and lifecycle

Shared scenario checks use explicit validation, including under `PYTHONOPTIMIZE=1`.
GeoJSON acceptance applies the same source ID/URN and complete known-rectangle
validator to both files. It rejects holes and crossed/clustered corners while
allowing valid cyclic starting-point changes and reversed winding at the declared
precision, without altering the exported bytes.
GeoJSON acceptance always attempts context and session release independently;
cleanup failures are reported without masking an existing scientific failure.
Provider acceptance preserves its documented diagnostic-retention policy on a
failed experiment and never acquires disposal ownership of a caller-owned context.
The saved-work example likewise retains failed test work/handles for diagnosis and
its explicit owner-directed release mode; it does not silently dispose of restored work.

Read [the authoritative verification record](exports-verification.md) for current
results, historical evidence and development/dependency status outside these tests.

## Connection suites

The existing runner also dispatches `connection-onboarding`,
`connection-recovery`, `connection-access` and `connection-readiness`.
Onboarding/recovery share the same two-provider compute/export/fresh-process
scenario; requesting both is redundant. For a running owned TEST state:

```text
python tools/run_functional_acceptance.py --state-dir <owned-state> --suite connection-onboarding --suite connection-access --suite connection-readiness --report <fresh-report.xml>
```

Prepare using the current `fixture_hub.py`; it adds `connection-profile.json`
and a private TEST `engine.cert`, retaining compatible `scientist.json`.
Requested missing profile/certificate configuration fails, not skips. The
authority implements the inspected ENGINE route/DTO using generated loopback
enrollment material and signed TEST credentials accepted by genuine services.
It does not validate production encrypted licenses or actual Hub agreements.

`run_export_acceptance.py --functional-suite connection-onboarding
--functional-suite connection-access --functional-suite connection-readiness`
provisions/cleans the owned exporter-enabled stack using its existing explicit
server/Java/worldview/component/state arguments. Connection acceptance uses
the pinned **published** GeoJSON component; it does not require corrected TIFF.
Shared value/unit and GeoJSON geometry validators remain active with Python
optimization. The parent connection closes before the recovery subprocess;
the original handle is restored without new scientific submission. Fresh
owner connections perform explicit context/session cleanup afterward.

Readiness disruption, unknown capability states, expiry/renewal, forbidden
origins, redirects and ambiguous mutation controls additionally run in offline
unit and local HTTP subprocess tests. Live readiness observes actual services
without stopping shared deployments. Real Hub onboarding is a separate recipe:
provide a trusted HTTPS profile and actual ENGINE certificate, run the example,
then a deployment-authorized known scientific workflow through the selected
Client and record Hub/server/worldview/component revisions and original IDs.
That gate is not satisfied by the controlled authority.
