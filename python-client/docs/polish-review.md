# Branch-wide Python-client polish review

> **Follow-up review:** a subsequent read-only review reproduced four remaining
> binding/secret/cleanup issues. Their [corrections and new regressions](review-corrections.md)
> supersede this document's earlier recommendation. Corrected-source validation
> passed: 988 source/installed tests, the three-interpreter Linux matrix, 12 live
> workflows plus GeoJSON and seven fresh notebook recordings. See the current
> [verification summary](results/review-corrections-verification.json).

> **Presentation revision:** the initial slide-based demos were rejected after
> user review. They are historical artifacts, not the approved release presentation.
> The [new direction](demo-plan.md) uses actual notebook execution. A subsequent
> example/copy revision does not change the SDK source tested below.
>
> **Replacement completed:** [seven notebook videos](notebook-videos.md), with
> executed notebooks and [current evidence](results/notebook-verification.json).
> The latest source and installed-wheel offline suites each passed **942 tests**;
> SDK coverage/gates remain unchanged. The older counts below describe their
> historical review checkpoints, not the final notebook-tool test count.

## Recommendation and scope

Internal review of the merged Python-client contributions and current local
analysis/provenance work, following Eric's contribution review guide. No
independent approval, publication or
maintainer acceptance is implied. See the [task checklist](polish-plan.md).

Branch: `feature/python-client-scientific-analysis-provenance`.
HEAD/base for this local polish: `4ad3887cc6f5d4a816fc9aec5e0f170cfa5efcec`.
Contribution-attribution base: PR #84's integration base
`cc647483b80a903a81cac29b28b1fcec4eb541de`, not a blanket diff against old develop
which would misattribute inherited upstream work.

Read-only GitHub inspection on 15 October 2026 confirmed #84/#85/#86/#92/#93 merged
into `feature/python-client`; #90 remains open separately. No branch reset,
upstream merge, commit, push, PR creation or contributor assignment was performed.
The earlier organization changes and three supplied original guides are preserved.

## Review coverage and simplicity decisions

| Reviewed surface | Decision / retained boundary |
| --- | --- |
| `client.py`, `errors.py`, `transport.py` | Keep one synchronous transport and explicit Jobs/scopes; simplify the broad notification catch; bound iterable consumption; preserve primary errors at local close |
| `dto.py`, `api/knowledge.py`, `api/runtime.py` | Preserve checked Java DTO interpretation, exact unit/contextualization evidence and sparse reads; no guessed geometry or local reasoning |
| `api/primitives.py`, `api/scopes.py`, `api/services.py`, `api/modeler.py`, package initializers | Keep public compatibility facades; local Modeler tracking is explicitly not server execution or permission authority; unsupported methods stay explicit |
| `requests.py` | Keep typed defensive snapshots and supported constraints; do not replace validation with permissive dictionary shortcuts |
| `handles.py` | Keep the secret-free v1 schema, origin binding, exact identity restoration and no mutation replay; fix secondary-cleanup masking |
| `exports.py` | Keep bounded native-byte streaming, strict representation checks and atomic publication; fix error masking, not the export algorithm |
| `connection.py`, `connection_models.py`, `credentials.py`, `_bootstrap.py` | Keep credential providers, catalog/selection and trust boundaries separate; existing locks/deadlines/latest-state delivery are functional, not disposable abstraction; fix interrupted profile-save cleanup |
| `analysis.py`, `provenance.py`, `experiment.py` | Optional scientific dependencies; finite distinct coordinates, native precision/validity and partial read-only graph state; no snapshot/replay invention |
| Contribution-specific Java source and tests | Reviewed all 20 changed Java files (10 production, 10 tests) relative to the integration base; leave small domain-level unit, Job, storage and ACL corrections intact |
| Branch-carried geospatial patch | Reviewed the three production Java changes, regression tests and independent reader; preserve the pinned scientific correction rather than cosmetically rewriting an independently checked exporter |
| Acceptance/build/throughput tools and examples | Preserve named shared scenarios, controlled test-only gates, explicit ownership and independent scientific checks; examples/demo paths reuse them rather than copying a scientific oracle |
| CI and packaging | Retain existing coverage/lint gates and interpreter matrix; optional analysis tested separately from core-only installation; source distributions now include the carried component patch and release media |
| Release/demo tooling | No generic UI/framework or desktop capture; render small, labeled walkthroughs from actual passing reports, with source/digest/cleanup checks and negative tests |

The contribution-specific Java inventory (package paths relative to each module's
`src/main/java` or `src/test/java`) is:

- Core API: `KlabResourceNotFoundException`.
- Core services production: `StorageManagerImpl`, `JobManager`, `KlabErrorHandler`,
  `TokenAuthorizationFilter`, `ScopeManager`.
- Core services tests: `JobManagerTest`, `DomainErrorMappingTest`,
  `TokenAuthorizationFilterTest`, `ManagedScopeAccessTest`.
- Reasoner: `SemanticsBuilder`, `SemanticsBuilderUnitTest`.
- Resources server: `ResourcesServer`.
- Runtime production: `RuntimeService`, `KnowledgeGraphNeo4j`.
- Runtime tests: `RuntimeContextAuthorizationTest`, `PersistedContextAccessTest`,
  `StorageRegistrationTest`, `FixtureControls`, `FixtureControlsTest`.

This 20-file list is reproducible with
`git diff --name-only cc647483b80a903a81cac29b28b1fcec4eb541de -- '*.java'`.
`ScopeManagerTest` and `Neo4jQueryExecutionTest` are additional neighboring
regressions included in the 56-test run, not falsely attributed new contributions.

All 22 SDK modules were inspected, including compatibility scaffolds. Compatibility
types, origin checks and error-state distinctions were not removed just to reduce
line counts. The SDK polish is deliberately a handful of local edits, not a new
exception hierarchy, async framework, cache, retry engine or public API redesign.

## Findings and corrections

### Important: a bounded cell read eagerly consumed an unbounded iterable

`src/klab_client/client.py:239` (`Context.fetch_data`) converted all offsets to a tuple before checking the 256-cell
limit. A regression generator reproduced consumption beyond the budget; a truly
infinite input could hang or exhaust memory. It now inspects at most 257 offsets,
rejects overflow before HTTP, and reports a non-iterable as InvalidRequestError.

### Important: secondary cleanup could replace the actual failure

`src/klab_client/client.py:434`, `handles.py:300`, `exports.py:159` and
`connection_models.py:271`: the base Client context manager could replace SubmissionOutcomeUnknown,
ProtocolError or KeyboardInterrupt with a local close OSError. Saved-handle/export
temporary cleanup and an interrupted profile save had the same failure category.
Regression tests reproduced these paths. Original failures now propagate with
safe cleanup notes; a close/cleanup failure after otherwise successful work still
raises. Existing profile diagnostic wording is retained for compatibility.

### Simplification: catch the expected exception directly

`src/klab_client/client.py:125` (`Job.result`) caught every Exception around notification checking, imported
ServerError, tested its type and reraised all others. It now catches ServerError
directly. Malformed protocol responses still remain ProtocolError, not Job failure.

### Packaging: source distributions omitted the carried component patch

`MANIFEST.in:3`: the source manifest included tooling/docs/tests but not the component patch that
the builder needs. It now includes the patch/manifest/readme and the small release
media. SDK wheels remain library-only; analysis/demo tools remain optional.

### Release claims and outstanding contracts

Do not present open #90 as delivered, controlled TEST Hub exchange as genuine
production onboarding, a local corrected component as an upstream distribution,
offset pagination as a coherent snapshot, or partial incident links as complete
reproducible lineage. The [preview](release-preview.md) retains these distinctions.
The specific Wardogs reference link was not supplied; the feature-section/short-
clip layout is an original patch-note-style presentation, not a claimed inspection
or reproduction of a particular external patch.

## New functional journey and demo evidence

The `release-notebook` test uses the same scientific workflow with provider-backed
clients: native ENGINE certificate connection, typed nonuniform field computation,
secret-free handle, local close, fresh-process issued-token connection, original
observation restoration, native TIFF export, xarray analysis, recorded execution
and explicit refresh. Existing independent value/coordinate/mask oracles are reused.
Direct private export/provenance denials have a valid nonowner positive control.

The full controlled pass selected connection onboarding/readiness/access, typed
computation/observer rejection, saved work, scientific lifecycle/concurrency,
GeoTIFF export and both direct/provider-backed analysis. **12 functional tests and
one GeoJSON regression passed, zero failures/errors/skips**, with
`PYTHONOPTIMIZE=1`. All five owned JVMs exited and cleanup exit was 0.

Five 18-second MP4s, plus GIFs/posters, were generated from that evidence and decoded
successfully. Raster inspection uses actual exported values/coordinates; graph rows
use actual recorded relationships. They are labeled result walkthroughs, **not
terminal/desktop recordings or timing benchmarks**. The renderer's unit tests reject
failed/skipped/incomplete reports, changed identities, missing features, unconfirmed
cleanup and stale SDK source manifests. See [media provenance](media/preview/README.md).

## Validation record

No older feature count is substituted for the current candidate. Results:

- Strict selected Java reactor: **56 passed**, no failures/errors/skips, offline,
  JDK 21.0.12.1, on this checkout's unmodified contribution-specific Java source.
- Windows Python 3.12.7 source and installed-wheel suites: **930 passed each**, zero
  failures/errors/skips. Includes 39 additional foundation contracts covering
  malformed Job/status/selector replies, default/finite wait behavior, environment
  configuration, peer setup and exact raw-scoped submission.
- Overall SDK: **96.02% statement / 93.30% branch coverage** (2,799/2,915 statements,
  933/1,000 branches). Existing exclusions: 164 lines, unchanged; unsupported
  compatibility scaffolds are not represented as exercised implementations.
- All nine gated new modules have **100% unexcluded line and branch coverage**;
  changed-code gate passed against `ad2bc949c167e1c2e370973ce4427fe67cb1cc30`.
  Coverage exclusions/thresholds were not relaxed. Remaining older-module gaps
  are visible in the external coverage JSON, not described as 100% branch-wide.
- Final Windows lint (`E9,F63,F7,F82`), wheel/sdist build and dependency checks
  passed. Core-only wheel smoke verified all 22 installed source files and native
  export/handle use without numpy/rasterio/xarray/Pillow/FFmpeg installed or imported;
  scientific opening fails with the documented missing-extra error.
- Linux installed-wheel Python 3.11/3.12/3.13: **930 passed each**, no failures,
  errors or skips; installed-source identity, existing coverage gates, lint and
  builds passed. External evidence prefix:
  `polish-linux-<version>-20261015T142014`.
- The controlled full functional run above passed against the explicitly selected
  server checkout `244883d7b04f1eae2cc22e799575d33a9bdb2a43` and corrected component
  archive `e9fa36a605013743669077dbb87874915d98006130acc8119834a23b3cfcc6fa`.
- Selected Python SDK source manifest matched the live runner exactly. Native field
  SHA-256 is `42f5382ce76fb6b3376465c6e483592f02955ab5c14465bd72985178535f6998`.
- Component patch was freshly reconstructed: **22 Java tests plus eight independent
  TIFF checks passed**, with no failures/skips; no user component checkout changed.
  Its fresh archive hash is
  `5ceadedc38737e81190039b5d238b92734e5c0b862d378b237a4dfc40401c2f1`.
  The earlier pinned archive above was used for live acceptance; this rebuilt
  archive is not falsely claimed to be byte-identical or newly live-certified.

External evidence root: `C:/Users/lumsd/AppData/Local/Temp/opencode/`.
Private TEST configurations/enrollment material remain there and are not release
assets. The [verification summary](results/polish-verification.json) records source
and evidence hashes. The older [feature checkpoint](scientific-analysis-review.md)
remains historical rather than being silently overwritten.

## Reproduction commands

From `python-client/`, in an isolated Python environment:

```powershell
python -m pip install '.[dev,local,analysis,demo]'
python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json:coverage.json --junitxml=offline.xml
python tools/check_feature_coverage.py coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/requests.py --new-module src/klab_client/handles.py --new-module src/klab_client/exports.py --new-module src/klab_client/connection.py --new-module src/klab_client/connection_models.py --new-module src/klab_client/credentials.py --new-module src/klab_client/_bootstrap.py --new-module src/klab_client/analysis.py --new-module src/klab_client/provenance.py
python -m ruff check src tests examples tools --select E9,F63,F7,F82
python -m build --outdir '<external-dist>'
python -m pip check
```

Repeat in fresh Python 3.11/3.12/3.13 environments after installing the **wheel**,
not an editable checkout. Verify `klab_client.__file__` is in `site-packages` and
compare all SDK source hashes before testing. The external matrix driver records
every command/environment and gate invocation. Its initial failed provisioning
attempts (missing uv and stale managed-Python metadata) are not test failures or
passing evidence; fresh managed runtimes were provisioned without deleting old data.

The controlled live invocation (Windows, explicitly compiled integration checkout):

```powershell
$temp = 'C:/Users/lumsd/AppData/Local/Temp/opencode'
$env:PYTHONOPTIMIZE = '1'
python tools/run_export_acceptance.py --server-repo 'C:/Users/lumsd/Downloads/k1/klab-python-federation-integration' --java "$temp/klab-local-stack/jdk/jdk-21.0.12.1+1/bin/java.exe" --worldview "$temp/klab-local-stack/assets/imod" --component "$temp/python-branch-geotiff-build2/component/target/klab.component.geospatial-component-1.0-SNAPSHOT.jar" --component-sha256 e9fa36a605013743669077dbb87874915d98006130acc8119834a23b3cfcc6fa --component-source "$temp/python-branch-geotiff-build2/component" --state-dir "$temp/<fresh-state>" --functional-suite connection-onboarding --functional-suite connection-readiness --functional-suite connection-access --functional-suite typed-compute --functional-suite resume-saved-work --functional-suite compute-and-analyze --functional-suite geotiff-export --functional-suite scientific-analysis-provenance
```

The existing component source must contain the exact pinned patch. To independently
reconstruct and validate it instead, use a fresh work directory:

```powershell
python tools/build_geospatial_component.py --java-home "$temp/klab-local-stack/jdk/jdk-21.0.12.1+1" --source-repo 'C:/Users/lumsd/Downloads/k1/klab-component-geospatial' --offline --independent-reader --work-dir "$temp/<fresh-component-build>"
```

The strict Java run used the selected contribution regression classes in the Maven
reactor. Equivalent reproduction from the repository root with JDK 21 on PATH:

```powershell
./mvnw.cmd -o -pl klab.services.runtime,klab.services.reasoner -am '-Dtest=DomainErrorMappingTest,TokenAuthorizationFilterTest,JobManagerTest,ManagedScopeAccessTest,ScopeManagerTest,SemanticsBuilderUnitTest,Neo4jQueryExecutionTest,PersistedContextAccessTest,StorageRegistrationTest,RuntimeContextAuthorizationTest,FixtureControlsTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

See `polish-java-selected1.log` for the 56-test module summaries. This is
not a claim that every inherited Java test or deployment topology was run.

## Limits and recommendation

**Ready for maintainer review within the documented scope.** Production Hub
onboarding and independent separate-deployment acceptance are not available here;
open #90 remains separate. No full inherited Java reactor, benchmark recertification,
server-restart durability, globally consistent snapshot or complete provenance is
claimed. The media are presentation assets grounded in passing tests, not an
additional independent scientific oracle. No commit or push has been made.

## Contributor coordination and smallest next actions

No NiFi/provider-worker code or another contributor's branch was edited. The Java
contribution review leaves existing domain decisions intact; genuine deployment
onboarding and the separate server integration need their own acceptance evidence.
Scientific/spatial review remains valuable, but expertise does not assign anyone a
review duty. No review requests or messages were sent.

1. Maintainers review the bounded SDK fixes and scientific/API contracts.
2. Obtain real deployment credentials/environment before claiming production
   onboarding or separately deployed identity/permissions acceptance.
3. Commit/publish only after the user explicitly authorizes it.

## Suggested PR description

**Problem:** native exports did not yet provide an explicit analysis-ready xarray
view tied to bounded recorded origins; cleanup failures could hide primary errors
and sparse reads consumed iterables before enforcing their budget.

**Result:** optional single-band numerical rectilinear raster analysis with separate
validity and explicit units/coordinates, origin-bound read-only partial provenance,
small reliability corrections, a provider-backed fresh-process scientific journey,
and a documented branch preview with five short verified-result walkthroughs.

**Validation:** see the current verification summary, source/installed-package
tests, unchanged coverage gates, selected Java/component tests and controlled
scientific/permission workflows above. **Limits:** no production onboarding,
snapshot guarantee, complete lineage or upstream component release; open #90 is
not part of this delivered contribution. Version remains `0.1.0`.
