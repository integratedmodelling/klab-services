# Export and functional-workflow verification record

This is the authoritative current review/verification record for [PR #92](https://github.com/integratedmodelling/klab-services/pull/92)
and [issue #91](https://github.com/integratedmodelling/klab-services/issues/91).
Usage and scenario definitions belong in the linked guides, not in repeated status tables.

**Current recommendation: Ready for maintainer review of the corrected local
Python tree in the inspected scope.** The second review's reproduced geometry and
fresh-process identity gaps are resolved and verified below. Earlier recommendations
remain historical; this is not an exhaustive correctness or acceptance guarantee.
The user authorized publication after the full regression/reevaluation below.
Exact published commit and hosted-check status are tracked in PR #92's Git history
and publication follow-up. Earlier local/uncommitted-state descriptions apply to
their historical checkpoints. Original Needs fixes reviews are preserved below.

Review authority: Eric's `C:\Users\lumsd\Downloads\KLAB_CLI_Contribution_Review.md`,
dated 11 October 2026. Its inferred maintainer preferences are not official policy
or a guarantee of acceptance. The issue agreement and actual contracts take precedence.

## GeoTIFF correction integrated on feature/python-client

The user requested that the reviewed component changes be carried on the Python-
client branch and selected the buildable-patch approach. The actual correction,
Java regressions and independent reader are now tracked under
`python-client/component-patches/geospatial`, with
`tools/build_geospatial_component.py` producing the corrected standalone package.
No external open PR or new services reactor module is required.

The staged checkout is verified against upstream base `8d4ef866`, the patch SHA-256
`3100b1fb9c42398edfd00b43909dd7ba80857da5b6778fb8d45e3d58334885cc`, and the exact reviewed
source tree `f05b578a14c707d8fd34348e56adc9a96bf25d66` from correction `a1d36d30`.
The tree is rechecked after tests/packaging. Input component/user checkouts are not
modified; output uses fresh external work with bounded child-step deadlines.

Current integration checks:

- **634 Python offline tests passed** on Windows 3.12.7, with the existing coverage
  gates and lint. New checks reject changed patch content/unknown manifest versions
  and preserve an existing work directory without invoking Git or a compiler.
- Building solely from the branch-carried patch produced the exact reviewed tree,
  ran **22 component tests** with zero failures/errors/skips, and passed all eight
  independent written-file checks under optimization. The artifact was built with
  the existing component packaging layout, not as copied SDK Java classes.
- Fresh live acceptance used that staged source and its explicitly pinned artifact:
  both GeoJSON and one-sample restored GeoTIFF acceptance passed; measured serial
  roundtrip **3/3 verified**, zero failures, 17.843 seconds. Personal session and all
  five owned JVM/authority exits were confirmed, cleanup exit 0.
- Build report: `python-branch-geotiff-build2/component-build.json` in the external
  test workspace. Local archive SHA-256:
  `e9fa36a605013743669077dbb87874915d98006130acc8119834a23b3cfcc6fa`.
  Live reports/untouched files: `python-branch-geotiff-live1`.
- This was pre-publication branch integration evidence with explicit dirty-state/
  source identities. Exact final commit and hosted checks are tracked in Git/branch
  checks rather than relabelling these results as a new immutable-head run.

The corrected code and a reproducible build are included on this branch. Upstream
geospatial `main` and Maven distribution remain unchanged; earlier statements that
the correction was separately local describe their historical checkpoints. Server
compatibility #89/#90 and independent provider accuracy remain separate boundaries.

See [branch patch/build instructions](../component-patches/geospatial/README.md).

## Reviewed submission — original export PR #92

- Repository: `integratedmodelling/klab-services`, `python-client/`.
- Target/base: `feature/python-client`, `90bebb5f35e60ad002f106c8d655e66d7e00d89d`.
- Published candidate: `e39368757c452133d716993417c1c04f43d9c7f6`.
- Included commits: `6b73dd9df` (export SDK) and `e39368757` (functional organization).
- The initial review inspected a clean tree and made no contribution/GitHub changes.
- Build, hosted Python 3.11/3.12/3.13 and Jenkins checks passed at the published head.
- Server integration #90 and the geospatial correction are separate dependencies;
  they are not part of the Python PR's source ancestry or Java diff.

## Publication regression and complete reevaluation — 13 October 2026

### Recommendation

**Ready for maintainer review of the bounded Python contribution.** Full applicable
Python regression, the local user workflows, scientific references and Runtime
restart/access checks passed. No blocking or important defect was found in the
complete inspected contribution plus the corrections. This is not exhaustive
proof, official policy, maintainer approval or permission to merge. Publication
was separately authorized by the user after the read-only reevaluation phase.

### Reviewed state

- Target/base unchanged: `feature/python-client`, `90bebb5f35e60ad002f106c8d655e66d7e00d89d`.
- Pre-publication parent: `e39368757c452133d716993417c1c04f43d9c7f6`.
  Included local changes: export path preflight; shared scenario validation/cleanup;
  fixture lifecycle checks; negative/positive regression fixtures; consolidated docs.
- Executable source/test/tool/pyproject snapshot remains
  `0aaec53f9959dc142056e2390fef826184b3f026cf1c2dfcb414a140af2cfaa9`.
  Fresh installed-package jobs verify source identity before/after the checks.
- All commits and the complete diff against the agreed base were inspected. The
  upstream server identity changes in #90 are not silently imported or credited to
  the Python feature. No Java source changes or geospatial algorithm changes are
  included in this submission.
- The issue scope remains native advertised export/file transfer plus the explicitly
  requested functional organization. #85/#86 are merged foundations; #89/#90 remain
  separate server work. No unresolved maintainer review comments were present when
  this reevaluation began.

### Capability and boundaries

Scientists can choose an advertised native observation format, restore original
authorized work and download it with bounded body memory and atomic no-clobber or
explicit replacement. Validation independently checks controlled geometry/data,
source identity, units and missingness where supplied by the representation.
Authoritative permissions, generation and scientific algorithms stay on the server.
There is no new bulk/window protocol, general GIS adapter, production-provider
accuracy or complete separate-deployment certification.

### Findings, ordered by consequence

1. **Resolved demonstrated defects:** optimization-safe checks; independent owned
   cleanup with primary-failure preservation; NUL path rejection before HTTP; complete
   rectangle topology/coverage and source ID/URN validation in both GeoJSON files.
   Regressions include each reproduced failure and legitimate rotated/reversed rings.
2. **Open coordination dependency:** the full live regression used explicitly selected
   server `244883d7b` and the separately corrected local TIFF component. A second fresh
   GeoJSON run used the unchanged published component. Numerical generation still needs
   independent component review/publication; #89/#90 rollout gates remain incomplete.
   These are not hidden inside the Python PR or labelled as completed platform work.
3. **Evidence interpretation:** legacy computation benchmark Java hashes describe the
   client checkout's reference sources, not the separately deployed server. The full
   live envelope pins the actual server commit, clean state and five deployed compiled
   class hashes. Read those together; do not treat reference-source hashes as proof of
   a deployed Java revision.
4. **Optional documentation enhancement completed:** one current verification record,
   usage guides that link to it and clearly labelled historical reports. No further
   optional requirement is imposed as an acceptance gate.

### Validation actually performed

This was a fresh requested full regression, not reuse of completed matrix steps.
Commands operated on external temporary state and installed environments. Windows
used Python 3.12.7; fresh Linux installs used 3.11.17, 3.12.15 and 3.13.16. Every
applicable command/step below exited 0.

| Check / command | Status | Evidence and limits |
|---|---|---|
| `python -B -m pytest -p no:cacheprovider --basetemp C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-publication-windows-tests tests/unit tests/integration --cov=klab_client --cov-branch --cov-report=json:C:/Users/lumsd/AppData/Local/Temp/opencode/pr92-publication-windows-coverage.json` | **Passed: 630**, Windows 3.12 | All maintained offline tests, including stream/file/identity, scientific/rate guards and actual local HTTP/subprocess integration. Coverage file outside checkout. |
| `check_feature_coverage.py` on that JSON, base `ad2bc949c167e1c2e370973ce4427fe67cb1cc30`, requests/handles/exports modules | **Passed** | Existing unexcluded new-module and changed-code gates. |
| Fresh venv + install `<checkout>[dev,local]`; SDK hash check; full `tests/unit tests/integration` suite; coverage evaluator; lint; wheel/sdist build | **Passed: 630 each**, Linux 3.11/3.12/3.13 | Full exact command vectors/source hashes in `pr92-publication-<version>-0aaec53f9959-20261013T182720.json`; logs and build outputs alongside them. Optional scientific readers are absent from these installed offline environments. |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | **Passed** | Static checks and patch whitespace. |
| Named local suites: compute-and-analyze, resume-saved-work, typed-compute, geojson-export, geotiff-export | **Passed: seven**, optimization 1 | Actual signed-user TEST stack, genuine corrected component, independent data/geometry/mask/source checks and explicit owned cleanup. |
| `cold_context_check.py prepare`; stop/start only Runtime; `cold_context_check.py check` | **Passed** | Graph retained; private denial, explicit sharing, owner/ACL preservation, collaborator-first parent authority and public exclusions before/after reconstruction. Actual denials are `AuthorizationError`, with valid-user positive controls. |
| All computation/read throughput modes and both numerical export modes | **Passed**, table below | Verified warmups/seeds excluded; every measured sample validated, no failures, actual wall intervals retained. Not capacity/robust tail-latency evidence. |
| Fresh `run_export_acceptance.py` with official published component | **Passed**, optimization 1 | Both GeoJSON files satisfy independent complete geometry and source identity against the unchanged distribution; owned scopes and all five JVM exits confirmed. |
| External provider/reference tests (two) | **Not run** | Independent external data/authorized provider environment unavailable; synthetic data is not substituted for a reference. |
| Full Java test reactor / broader server candidate-versus-baseline comparison | **Not applicable to changed Python source / not run here** | No Java source changes. Separate #89/#90 regression requirements remain open. Hosted Maven packaging uses `-DskipTests`; a green build is not a full Java test claim. |
| Hosted checks on the forthcoming published fixes | **Pending at pre-publication checkpoint** | Assessed separately at the new immutable PR head; earlier green statuses are not reused. |

The measured modes in `pr92-publication-full-live` all completed with zero failures:

| Mode | Concurrency | Verified | Actual wall seconds |
|---|---:|---:|---:|
| Full compute/analyze | 1 | 8 | 28.336 |
| Full compute/analyze | 2 | 9 | 18.039 |
| Full compute/analyze | 4 | 13 | 18.928 |
| Seeded repeated read | 4 | 40 | 16.041 |
| Full numerical restore/export | 1 | 4 | 26.251 |
| Seeded repeated numerical export | 2 | 10 | 18.232 |

The fresh full live envelope is `pr92-publication-full-live/full-regression.json`.
It records each exact command, source hashes, selected server/component provenance,
deployed class hashes and exit status. Named-suite JUnit, cold checks, worker reports,
untouched TIFFs and all benchmark summaries are in the same state directory. All
five test-owned JVM exits and authority cleanup were confirmed, final cleanup exit 0.
The separate published-component run is `pr92-publication-published-geojson`.

### Scientific and lifecycle evidence

Actual computations retain original Job/observation identity across clients and
fresh processes. Independent geometry checks require the complete rectangle in
both files; TIFF checks require all value/world-coordinate associations, valid zero,
one NaN, nodata mask and source unit. Scientific algorithms and tolerances were not
relaxed. Wait/cancel/failure and access controls remain distinct, with actual private
denial and successful authorized controls. The tests use small controlled fixtures;
no production onboarding, observed terrain or arbitrary exporter claim follows.

### Contributor coordination and smallest next actions

The Python work is Eric's feature slice; component and server work remain separately
scoped. No expertise invitation is treated as a reviewer assignment. Publish the
reviewed fixes as requested, verify hosted checks at the new head, and obtain the
maintainer/branch owner's decision on merge and deployment boundaries. Existing
component publication and #89/#90 completion are not silently declared done.

### Suggested PR description update

Describe the corrected optimization-safe validation, complete geometry/source checks
for both files, independent cleanup and early invalid-path errors. Link this record;
state fresh 630-test checks on four interpreter/platform combinations, seven local
functional checks, restart/ACL and all measured modes, plus public-component GeoJSON.
Keep external provider tests, Java test-reactor claims and separate component/server
rollout explicitly limited. Exact commit/hosted status belongs in the publication
follow-up rather than an assertion that old green checks apply to new source.

## Initial review — 13 October 2026

**Recommendation: Needs fixes.** The SDK is a useful bounded slice using existing
contracts, but the shared functional scenarios had reproduced false acceptance
and cleanup defects. Green coverage did not establish those failure paths.

| Finding | Classification | Trigger and observed consequence | Smallest correction |
|---|---|---|---|
| Optimization removes scenario validation | Blocking functional-validation defect | Ordinary assertions in `workflows/geojson_export.py`, `resume_saved_work.py` and `provider_reference.py` disappear under optimization. A mocked GeoJSON run accepted incorrect identity/coordinates under `-O`; normal execution rejected it. | Explicit validation and an optimized negative subprocess regression. |
| Cleanup depends on an assertion/short-circuit | Important lifecycle defect; hold combined PR | `assert context.release() and session.release()` skips session release when context release fails, and skips both calls under optimization. | Independent cleanup attempts outside assertions, preserving the primary failure. |
| Malformed destination escapes the SDK error contract | Smaller SDK defect | A NUL filename downloaded through two mocked requests, then raised raw `ValueError` instead of `ExportIOError`; temporary files were cleaned. | Local destination preflight before HTTP and a meaningful negative regression. |
| Corrected numerical component is unpublished | Contract/coordination question | Successful TIFF evidence requires a separate locally corrected component and server #90. A reviewer cannot reproduce it from the Python PR alone. | Keep the dependency explicit and obtain separate component review/publication. |
| Repeated status across documents | Optional maintainability improvement | Counts, hashes and readiness appear in several guides and can drift. | One authoritative verification record; retain clearly labelled historical evidence. |

### Checks performed for that review

Windows Python 3.12.7, no live service mutations. Bytecode/cache writes were disabled
and pytest temporary files were isolated under the approved external test workspace.

```powershell
python -B -m pytest -p no:cacheprovider --basetemp C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-review-pytest tests/unit/test_exports.py tests/unit/test_geotiff_throughput.py tests/unit/test_benchmark.py tests/unit/test_functional_runners.py tests/integration/test_handle_process.py
```

- **Passed:** 93 focused tests; PR metadata, ancestry, contract and clean-tree inspection.
- **Failed expected contract:** isolated optimized GeoJSON fault probe accepted wrong
  identity/coordinates and performed no releases; normal execution rejected it and
  called both releases. The same mock ran with normal Python and `python -B -O -c`.
- **Failed cleanup expectation:** isolated execution of the candidate's cleanup
  statement with context release returning false never attempted session release.
- **Failed exception contract:** NUL destination probe returned raw `ValueError`
  after consuming the mocked stream; no temporary files remained.
- **Passed source comparison:** all 16 SDK hashes and the numerical runner hash
  matched the retained earlier live evidence. That report records a dirty
  pre-commit tree at `6b73dd9df`; it is not a fresh live run at the published head.
- **Not run:** fresh live services and external provider/reference acceptance.

## First reevaluation after remediation — 13 October 2026 (superseded)

The same contribution guide was reread after implementation. Evaluation was a
read-only inspection/check phase; recording its outcome here was separately
requested by the user. No commit, push, GitHub publication or merge was performed.

### Recommendation

**Ready for maintainer review of the bounded local Python changes.** The three
demonstrated defects are resolved with regression evidence; no new blocking defect
was found in the inspected scope. This is not exhaustive proof or maintainer
acceptance. Numerical rollout still requires separate component review/publication
and an agreed supported server combination.

### Reviewed state

- Target/base unchanged: `feature/python-client`, `90bebb5f35e60ad002f106c8d655e66d7e00d89d`.
- Published PR head remains `e39368757c452133d716993417c1c04f43d9c7f6`; the reevaluation
  includes authorized local changes to export path preflight, shared scenario
  validation/cleanup, fixture lifecycle checks, regression fixtures/tests and docs.
- Tested source/test/tool/pyproject snapshot SHA-256:
  `a2dd7af14c6fc665623a8e0f345a0d601bda121d7a00e0fc64570325af9c0c83`.
  Per-file hashes are retained in the installed-package matrix reports. Final
  record-writing changes only documentation, not that executable snapshot.
- The API preserves existing server contracts and known origin/Runtime binding.
  No Java or exporter algorithm changes are included. Server #90 is not silently
  substituted for the PR base.

### Capability and boundaries

A scientist can discover a native observation representation, restore existing
work and stream its bytes into an atomic local file. Functional acceptance and
throughput share explicit scientific validation that survives optimization.
No new server protocol, scientific re-encoding or disposal of caller-owned work
is introduced. Provider experiments retain failed work for diagnosis as documented;
owned GeoJSON acceptance always attempts both releases.

### Findings and resolution

| Original finding | Reevaluated status and evidence |
|---|---|
| Optimized false acceptance | **Resolved.** `scientific_checks.require` explicitly raises on failure. All former assertions in shared scenarios and the reused fixture lifecycle checker are explicit checks. Subprocesses with optimization 0/1 reject wrong GeoJSON identity/corners, recovery identities, failed reports and a foreign provider Runtime. Valid optimized GeoJSON still succeeds. |
| Incomplete/masking cleanup | **Resolved.** `release_owned` attempts every supplied scope, including when a release returns false or raises. GeoJSON registers cleanup before context creation. Tests exercise failed context creation, unsuccessful/raising releases and a scientific failure combined with cleanup failure; the original error survives with credential-safe cleanup notes. |
| NUL destination error | **Resolved.** Export preflight rejects NUL strings before capability/HTTP calls and raises `ExportIOError`. Four regressions cover string/Path inputs and invalid leaf/parent components; no files are created. |
| Reproducible component/server combination | **Open coordination dependency.** The explicit local component/source pin and separate server revision remain necessary. Nothing in this reevaluation certifies the failing published TIFF exporter or completes #89/#90. |
| Repeated documentation status | **Implemented.** Current findings, counts, hashes and readiness are centralized here. Usage guides link to this record; full older reports are retained under `docs/history/` with historical labels. |

### Validation actually performed

Windows interpreter: `C:\Users\lumsd\AppData\Local\Temp\opencode\klab-exports-312\Scripts\python.exe`
(Python 3.12.7). Linux installed-package checks used Python 3.11.17 and 3.13.16.
All commands below exited 0. Commands are from `python-client/` unless noted.

| Check / command | Environment and revision | Status / establishes |
|---|---|---|
| `python -B -m pytest -p no:cacheprovider --basetemp C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-fixes-final-all tests/unit tests/integration --cov=klab_client --cov-branch --cov-report=json:C:/Users/lumsd/AppData/Local/Temp/opencode/pr92-fixes-final-coverage.json` | Final local executable tree, Windows 3.12; bytecode disabled, coverage file outside checkout | **Passed: 600.** Includes 23 scenario fault/cleanup checks and four NUL-path checks; positive/negative subprocesses exercise optimization 0/1. |
| `python tools/check_feature_coverage.py C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-fixes-final-coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/requests.py --new-module src/klab_client/handles.py --new-module src/klab_client/exports.py` | Same Windows source; existing project gates | **Passed.** Unexcluded new-module line/branch and changed-code gates. |
| Fresh install of `<checkout>[dev,local]`, `python -m pytest -q tests/unit tests/integration --cov=klab_client --cov-branch`, installed SDK hash verification and the same coverage evaluator | Snapshot above, Linux 3.11/3.13; exact command vectors in external matrix reports | **Passed: 600 per interpreter.** Tests actual installed SDK; optional Rasterio is not required for offline/core checks. |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `python -m build --outdir <external-dist>` | Final source, Linux matrix; lint also run on Windows | **Passed.** Static checks and wheel/sdist packaging. |
| Owned exporter-enabled stack command below | Final executable snapshot; Windows/JDK 21.0.12.1+1, optimization 1, explicit server/component/worldview pins | **Passed.** Seven local functional checks; 3/3 serial numerical samples plus verified warmup, zero failures; owned session cleanup/all five JVM exits confirmed. |
| External provider/reference acceptance | Requires separately supplied genuine provider/reference data | **Not run.** Provider identity failure was regression-tested with mocks, not certified as real-provider science. |
| New Java algorithm/server tests | Python-only remediation; Java dependencies unchanged | **Not applicable.** Earlier component evidence is dependency evidence, not a new Java validation claim. |
| Hosted checks of the remediated tree | Changes are not published | **Not run.** Green hosted checks at the old PR head are not attributed to this local candidate. |

The owned live command used `PYTHONOPTIMIZE=1` and `PYTHONDONTWRITEBYTECODE=1`:

```powershell
python tools/run_export_acceptance.py --server-repo C:\Users\lumsd\Downloads\k1\klab-python-federation-integration --java C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\jdk\jdk-21.0.12.1+1\bin\java.exe --worldview C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\assets\imod --component C:\Users\lumsd\Downloads\k1\klab-component-geospatial\target\klab.component.geospatial-component-1.0-SNAPSHOT.jar --component-sha256 46175d3ed1f81f90c4ac1ce60774091baff118cd1fbc3e6f61a7f2183ff00482 --component-source C:\Users\lumsd\Downloads\k1\klab-component-geospatial --state-dir C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-remediated-optimized1 --throughput --samples 3 --duration 15 --warmup 1 --throughput-mode full-roundtrip --concurrency 1 --functional-suite compute-and-analyze --functional-suite resume-saved-work --functional-suite typed-compute --functional-suite geotiff-export
```

The expected pytest optimization warning was recorded; scenario validation uses
explicit checks, not ignored assertions. Live reports, JUnit and untouched TIFFs
are in `pr92-remediated-optimized1`. Installed Linux reports are
`organized-3.11-a2dd7af14c6f.{json,log}` and
`organized-3.13-a2dd7af14c6f.{json,log}` in the external test workspace.

### Scientific and lifecycle evidence

The fresh optimized run verified native storage/Job identity, typed options,
authorized recovery/collaborator controls, GeoJSON geographic corners and all
20 TIFF value/world-coordinate associations with valid zero, NaN/mask and source
unit. It used the same independently specified oracle and declared GeoJSON
precision; neither values nor tolerances were relaxed. The 17.603-second serial
interval is small-field workflow evidence, not a performance improvement claim.
No new temporal, categorical, provider-accuracy or separate-deployment claim is made.

### Contributor coordination and smallest next actions

The user owns the Python slice. Typed requests/saved handles are merged foundations;
server identity/permissions remain #89/#90 and numerical generation belongs to the
geospatial component. No other contributor was assigned review duties or contacted.
The smallest remaining delivery actions are to publish these Python fixes when
requested, obtain separate component review/publication, and agree the supported
server combination before describing full numerical deployment as generally available.

### Suggested PR description update

Record optimization-safe scientific/recovery checks, independent owned cleanup
that preserves primary failures, and local invalid-path rejection. Link this single
verification record, report the new 600-test installed/offline checks and fresh
optimized functional run, and retain explicit unpublished-component/server limits.

## Second review and complete-feature correction — 13 October 2026

The user requested another read-only review with the same contribution guide.
It included the authorized local remediation at published head `e39368757`, and
all 58 recorded executable snapshot files still matched the `a2dd7af14c6f` manifest.
The review ran 120 focused checks successfully but reproduced additional false
acceptance paths with isolated mocked clients/files. No live services or code
changes were part of that review.

**Second-review recommendation: Needs fixes**, superseding the first reevaluation.

| Finding | Reproduction and requested correction |
|---|---|
| Incomplete rectangle oracle — blocking scientific-validation defect | A tiny four-corner polygon near the north-west corner, a crossed corner ordering and a rectangle with an extra hole were all accepted. The any-corner predicate allowed reused reference corners and inspected only the first ring. Require one exterior ring, complete one-to-one corner coverage and boundary ordering, allowing rotations/reversed winding at the same declared tolerance. |
| Missing fresh-process URN check — important identity-validation defect | A correct primary file with `s.c.42` and a fresh file with `different.context.42` passed with identical numeric ID/geometry. Apply the same complete source identity/geometry validator to both files. |

The user then authorized implementation. `geojson_export.validate_reference` now
checks the complete single-feature structure, source ID/URN, one closed 2D finite
exterior ring, and one of the eight permitted boundary rotations/windings. Every
ring position binds to a different expected geographic corner. Both primary and
fresh-process files use it; equivalent ring ordering is accepted without rewriting
the exported bytes. The geographic formula and `1e-6`-degree tolerance are unchanged.

Regressions cover the new invalid geometry cases in either file, the fresh wrong
URN, positive rotated/reversed outputs in either file, and all eight reference ring
orders. Subprocess cases run with optimization 0/1 and verify both owned releases.

### Second correction verification and reevaluation

**Recommendation: Ready for maintainer review of the bounded local changes.** The
known four false-acceptance cases now fail with meaningful messages, under normal
and optimized execution. Correct ring rotations/windings remain accepted, and the
genuine published exporter passes the stronger independent check in both processes.
No additional blocking defect was found in this inspected correction; the separate
server/component deployment decisions remain open.

Reviewed state: published head/base remain `e39368757` / `90bebb5f3`; all earlier
authorized local remediation is included. The final executable/test/tool/pyproject
snapshot SHA-256 is
`0aaec53f9959dc142056e2390fef826184b3f026cf1c2dfcb414a140af2cfaa9`.
The later additions to this record are documentation-only. Fixes remain uncommitted
and unpublished; old hosted checks do not validate this snapshot.

| Check / command | Environment and status | Establishes / limits |
|---|---|---|
| `python -B -m pytest -p no:cacheprovider --basetemp C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-geometry-focused tests/unit/test_workflow_validation.py` | Windows 3.12.7; **Passed: 53**, exit 0 | Clustered/crossed/holed polygons rejected in either file; fresh wrong URN rejected. Full scenario subprocesses use optimization 0/1, verify both releases, and accept valid rotations/reversals. Direct controls cover all eight boundary orders without altering input. |
| `python -B -m pytest -p no:cacheprovider --basetemp C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-geometry-all tests/unit tests/integration --cov=klab_client --cov-branch --cov-report=json:C:/Users/lumsd/AppData/Local/Temp/opencode/pr92-geometry-coverage.json` | Same local tree, Windows 3.12.7; **Passed: 630**, exit 0 | Existing SDK contracts, file/stream/identity errors and new meaningful negative cases; coverage file outside checkout. |
| Existing `check_feature_coverage.py` command with `pr92-geometry-coverage.json`, base `ad2bc949c167e1c2e370973ce4427fe67cb1cc30`, and requests/handles/exports modules | Windows; **Passed**, exit 0 | Existing unexcluded new-module and changed-code gates. |
| Fresh installed-package matrix: `<checkout>[dev,local]`, SDK source-hash check, `python -m pytest -q tests/unit tests/integration --cov=klab_client --cov-branch`, coverage evaluator, lint and `python -m build` | Linux 3.11/3.13; **Passed: 630 each**, all steps exit 0 | Actual installed SDK, wheel/sdist and test/tool snapshot; exact command vectors and full per-file hashes in matrix reports. |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Windows; **Passed**, exit 0 | Static checks and patch whitespace. |
| Published-component owned live command below | Windows 3.12/JDK 21; optimization 1; **Passed: one full GeoJSON scenario**, exit 0 | Native and fresh-process files independently satisfy complete geometry and source identity; owned context/session release and all five JVM exits confirmed. |
| Numerical/provider/separate-deployment live reruns | **Not run for this correction** | Numerical workflow, provider accuracy and server behavior were not changed; earlier results remain identified as earlier evidence. |
| New Java checks / new hosted checks | **Not applicable / Not run** | No Java source changes; local work is not published. |

The fresh live run set `PYTHONOPTIMIZE=1` and `PYTHONDONTWRITEBYTECODE=1` and used:

```powershell
python tools/run_export_acceptance.py --server-repo C:\Users\lumsd\Downloads\k1\klab-python-federation-integration --java C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\jdk\jdk-21.0.12.1+1\bin\java.exe --worldview C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\assets\imod --component C:\Users\lumsd\.m2\repository\org\integratedmodelling\klab.component.geospatial\1.0-SNAPSHOT\klab.component.geospatial-1.0-20260926.083046-222-component.kar --state-dir C:\Users\lumsd\AppData\Local\Temp\opencode\pr92-geometry-published-optimized1
```

Evidence: `pr92-geometry-published-optimized1/{run-report.json,exports.xml,export.log}`
and its untouched files. The archive is the unchanged official hash recorded below,
not the corrected local numerical component. The expected pytest optimization
warning is recorded; explicit scientific checks remain active. Matrix reports:
`organized-3.11-0aaec53f9959.{json,log}` and
`organized-3.13-0aaec53f9959.{json,log}` in the external workspace.

The corrected known-rectangle oracle introduces no scientific re-encoding, new
dependency or general geometry adapter. The independently calculated projection
and declared tolerance are unchanged. The smallest remaining delivery steps are
publication of these local Python fixes when requested and separate component/server
coordination. Suggested PR update: describe complete one-ring boundary validation
and source ID/URN checks for both files, the new negative controls and this fresh
published-component evidence. No contributor was assigned or contacted.

## Scientific/deployment evidence and boundaries

The bounded SDK preserves native bytes. It does not repair exporter algorithms,
invent scientific metadata, or guarantee bounded server-side generation memory.
Scientific acceptance uses independent geographic/raster references, not just
successful HTTP delivery or an encode/decode round trip.

The retained local deployment used signed ordinary users, server
`244883d7b04f1eae2cc22e799575d33a9bdb2a43` (draft #90), and worldview
`608bef150ced0a109db98a5aad64ba4461beaa54`. Services ran on one machine; this does
not establish cooperation between separately deployed installations.

| Component | Provenance and scientific result |
|---|---|
| Published `1.0-20260926.083046-222` | SHA-256 `e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644`. Genuine GeoJSON identity/corners passed. Numerical TIFF was vertically inverted and lacked measurement-unit/nodata tags: serial 0/1 and repeated 0/2; successful rates were suppressed. |
| Separately corrected local build | Geospatial base `8d4ef8665016761eb12a504a304f02eb3b55f1c7`, dirty local correction; genuine Maven archive SHA-256 `46175d3ed1f81f90c4ac1ce60774091baff118cd1fbc3e6f61a7f2183ff00482`. Independent Rasterio 1.5.2/GDAL 3.12.2 verified all 20 value/coordinate associations, valid zero, one NaN, nodata mask and `degree_angle` unit. Its source remains separate local work requiring review/publication. |

The corrected TIFFs in retained evidence are 622 bytes, SHA-256
`f867a405b9d7aa133fef61233ea3d166e6a6b7ecbeefef576848439636b4a2d9`.
Before remediation, the reorganized tree passed seven local functional checks,
3/3 serial numerical samples and 12/12 concurrency-two repeated exports. Owned
session cleanup and all five JVM exits were confirmed. Earlier corrected pilots
passed 3/3 serial and 14/14 repeated samples. These are small synthetic workflow
measurements, not production capacity or a controlled before/after comparison.

External retained evidence: `geotiff-throughput-pilot`,
`geotiff-throughput-repeated-pilot`, `geotiff-corrected-serial1`,
`geotiff-corrected-repeated1`, `organized-functional-serial2` and
`organized-functional-repeated1`, including reports, logs and untouched TIFFs.
The pre-remediation installed Linux source/test/tool snapshot prefix is
`9fa8ad1cae21`; its 573-test evidence does not validate subsequent changes.

The server identity/shared-session work under #89/#90, external provider accuracy,
and broader separate-deployment checks are unfinished or not run. No reviewer
assignment, merge or publication is implied by this record.

## Navigation

- [Export API and file policy](stream-exports.md)
- [Functional suites, shared scenarios and commands](functional-testing.md)
- [Numerical oracle and throughput modes](geotiff-throughput.md)
- [Original export checkpoint](stream-exports-review.md)
