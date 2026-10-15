# Contribution review: scientific analysis and recorded origins

> Historical feature checkpoint before the expanded branch polish pass. For the
> current working tree, use the [branch-wide polish review](polish-review.md).
> The hashes and counts below describe the explicitly identified earlier candidate.

## Recommendation

**Ready for maintainer review within the documented bounded scope.** The client
opens faithful single-band rectilinear numerical rasters and associates them with
typed, read-only, partial recorded neighborhoods. Controlled real-service
compute/restoration/export/analysis/lineage and access-denial checks passed. This is
an internal implementation review following Eric's contribution guide, **not an
independent reviewer approval**, production certification or permission to publish.

## Reviewed state

- Repository: `klab-python-client`; integration target: `feature/python-client`.
- Base and current HEAD: `4ad3887cc6f5d4a816fc9aec5e0f170cfa5efcec`.
- Candidate: uncommitted working tree on
  `feature/python-client-scientific-analysis-provenance`, 15 October 2026.
- The existing staged/unstaged/untracked documentation organization work is
  preserved, including the imported originals, historical notes and review-evidence
  renames. It is not a new scientific feature or fresh validation of old reports.
- Feature-specific production changes: new `analysis.py` and `provenance.py`, public
  exports in `__init__.py`, optional `[analysis]` extra, and a backwards-compatible
  optional response-byte budget/status-code annotation in `transport.py`.
- Feature tests/tooling: two unit modules, shared scientific workflow/fresh-process
  worker, functional suite selection, one example and supporting documentation.
- The existing hosted matrix now installs `[analysis]` and measures both new
  modules through the existing coverage gate; a separate core-only job guards
  dependency independence. Hosted execution of these edits is not claimed.
- No Java/component source changes, branch-base replacement, remote refresh, commit,
  publication, PR creation, merge or contributor contact was performed.
- See [contract inventory](scientific-analysis-contracts.md). No claim of current
  remote issue/PR status or maintainer agreement beyond the supplied references.

The tested 22-file production manifest, normalized to LF and sorted by package-
relative path, has SHA-256
`69fc94c4d55cf8344d1b00a564a12ef98663dcf57df858eabff1ee421f7a63c6`.
The manifest encoding is compact sorted-key JSON mapping `src/klab_client/...`
paths to LF-normalized SHA-256. The analogous 84-file executable manifest covering
`src/`, `tests/`, `tools/`, `examples/` is
`6f91bad1a19cf20875e857fab29a57c865b80fac4cde7661ba13af021bfe9b6d`.
Production sources matched the final live runner's file manifest and all installed
wheel files; tests are of this dirty tree, not merely the HEAD commit.

## Capability and boundaries

Before: native files and sparse indexed cells were available, but scientists had
to interpret exported rasters and raw graph DTOs themselves.

After: restore an observation, `export_for_analysis`, `open_raster`, inspect native
`value` and independent `valid` arrays, `fetch_provenance`, and `trace_analysis`.
Native dtype, pixel-center coordinates, transform, CRS, units and masks are retained.
The artifact digest identifies local bytes. The provenance view is bounded and
read-only, preserves parallel links, labels planned references and explicitly stays
partial. Matching origin-qualified saved handles establishes association only.

Excluded: rotation/shear conversion, arbitrary multidimensional/category adapters,
server chunks/snapshots, complete recursive lineage/resource-version recovery,
authoring, automatic replay, graph caches/events and worker execution. Estimated
owned-buffer preflight is not a total process/native-RSS guarantee. Filesystem and
Rasterio invalid-file errors remain their native error types.

## Findings, ordered by consequence

No remaining demonstrated blocking or important defect was found in the bounded
candidate after fixes and revalidation. This is not exhaustive proof.

### Contract boundary: export/query snapshot consistency

- Location: `analysis.py:73–79`; `provenance.py:54–61`; contract inventory.
- Trigger: artifact export and recorded-graph fetch occur as separate operations.
- Evidence: inspected APIs provide no common authoritative read lease/revision.
- Consequence: matching observation IDs, timestamps or local digest cannot establish
  a coherent historical snapshot or portable reproduction.
- Treatment: explicit unknown consistency and partial views; no coherent/replay
  claim. A future stronger promise requires a server contract, not a client flag.

### Contract boundary: neighborhood is not complete lineage

- Location: `provenance.py:90–138` and selected `RELATIONSHIPS` vocabulary.
- Trigger: scientifically meaningful inputs/versions may lie beyond the incident
  neighborhood or be unexposed by the current graph.
- Treatment: one bounded page with explicit offset/refresh and `partial=True`;
  absence is not translated into nonexistent dependencies. Full agent attribution,
  external resource version closure and replay remain follow-up work.

Resolved implementation findings included rejecting detached Handle objects at the
export facade, rejecting malformed unhashable endpoint discriminators, conservatively
accounting for coordinate buffers, detecting changed local artifacts, and requiring
identity content encoding for byte-bounded JSON. Focused regression tests cover
these; earlier successful live1/live2 runs are not reused as acceptance of the changed
production files. Final live3 and final offline/package results are used instead.
The review also identified that the old CI install omitted scientific extras;
the targeted workflow change prevents the new scientific tests from silently
skipping in that matrix and retains a separate core-only installation check.

### Resolved important scientific defect: unrepresentable coordinates

- Location: `analysis.py:128–137`; real-TIFF regression cases at the end of
  `tests/unit/test_scientific_analysis.py`.
- Trigger: finite affine coefficients can overflow during pixel-center calculation,
  or a large origin/small spacing can collapse distinct centers at float64 precision.
- Reproduced: two independently written TIFF fixtures initially returned infinity
  or duplicate centers instead of raising; the new tests failed before correction.
- Correction: require finite, strictly ordered centers in each native axis direction
  before reading arrays. Four x/y overflow/collapse fixtures now reject explicitly.
  Existing north-up/south-up and reversed-x acceptance remains intact.
- Revalidated: final 860-test source and installed-wheel suites, original coverage
  gate, and actual live3 workflow under `PYTHONOPTIMIZE=1` all passed.

## Validation actually performed

Windows, Python 3.12.7. Isolated environment:
`C:/Users/lumsd/AppData/Local/Temp/opencode/klab-analysis-isolated-312`.
Installed-wheel environment: `klab-analysis-wheel-312` under that same external
parent; no editable SDK/source substitution. Final core-only environment:
`klab-analysis-core-final-312`.

| Check | Status | Evidence / limits |
| --- | --- | --- |
| Full offline unit/integration suite | Passed | 860 tests, zero failures/errors/skips; source environment |
| Installed wheel offline suite | Passed | Same 860 tests, zero failures/errors/skips; all 22 SDK Python files matched source hashes |
| Existing new-module and changed-code coverage gates | Passed | Both new modules have unexcluded 100% lines/branches; changed production lines/arcs covered |
| Established lint | Passed | `E9,F63,F7,F82` across src/tests/examples/tools |
| sdist and wheel build | Passed | External `analysis-provenance-dist4`; final documentation packaging refreshed separately |
| Core-only installed wheel | Passed | No NumPy/Rasterio/xarray installed/imported; local Client lifecycle, saved reference round-trip, mock native export and actionable missing-extra diagnosis |
| Package dependency consistency | Passed | Installed extra environment `pip check`: no broken requirements |
| New controlled functional workflow | Passed | Fresh-process original observation, all 20 values/coordinates, native dtype/units/mask, statistic, recorded execution and refresh |
| Direct new-path access controls | Passed | Valid nonowner session positive control; nonowner export and provenance both AuthorizationError; no denied local output |
| Existing GeoJSON/connection access regressions | Passed | One GeoJSON and one connection-access test; final functional XML totals two tests including new workflow |
| Cleanup and public evidence | Passed | Five JVM exits, authority stopped, all owned ports checked closed; actual TEST tokens absent from selected public JUnit/JSON reports |
| Actual supported production deployment/Hub | Not run | Controlled TEST authority is not genuine Hub onboarding or deployment certification |
| Linux / Python 3.11, 3.13 matrix | Not run | No new cross-platform/multi-interpreter certification claimed |
| New Java/full reactor suite | Not applicable | No Java source changes; existing explicitly selected compiled services exercised through HTTP |

The single offline warning is expected Rasterio `NotGeoreferencedWarning` from the
identity-transform rejection fixture, not a skipped check or accepted bad mapping.
An initial ad hoc core smoke check used an already-buffered mock export response;
it correctly failed the existing unread-stream guard. Repeating with an unread
ByteStream passed; production streaming behavior was not weakened.

### Exact validation commands

From `python-client/`, `python` below denotes the source environment executable
above, except the installed-wheel run uses that environment's executable.

```text
python -m pip install -e ".[dev,analysis,local]"
python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json:C:/Users/lumsd/AppData/Local/Temp/opencode/analysis-final-coverage.json --junitxml=C:/Users/lumsd/AppData/Local/Temp/opencode/analysis-final-offline.xml
python tools/check_feature_coverage.py C:/Users/lumsd/AppData/Local/Temp/opencode/analysis-final-coverage.json --base 4ad3887cc6f5d4a816fc9aec5e0f170cfa5efcec --new-module src/klab_client/analysis.py --new-module src/klab_client/provenance.py
python -m ruff check src tests examples tools --select E9,F63,F7,F82
python -m build --outdir C:/Users/lumsd/AppData/Local/Temp/opencode/analysis-provenance-dist4
python -m pytest tests/unit tests/integration -q --junitxml=C:/Users/lumsd/AppData/Local/Temp/opencode/analysis-installed-offline.xml
python -m pip check
```

Final controlled stack command (all steps exit 0), with `PYTHONOPTIMIZE=1` in the
invocation environment:

```powershell
python tools/run_export_acceptance.py --server-repo C:\Users\lumsd\Downloads\k1\klab-python-federation-integration --java C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\jdk\jdk-21.0.12.1+1\bin\java.exe --worldview C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\assets\imod --component C:\Users\lumsd\AppData\Local\Temp\opencode\python-branch-geotiff-build2\component\target\klab.component.geospatial-component-1.0-SNAPSHOT.jar --component-sha256 e9fa36a605013743669077dbb87874915d98006130acc8119834a23b3cfcc6fa --component-source C:\Users\lumsd\AppData\Local\Temp\opencode\python-branch-geotiff-build2\component --state-dir C:\Users\lumsd\AppData\Local\Temp\opencode\analysis-provenance-live3 --functional-suite scientific-analysis-provenance --functional-suite connection-access
```

## Scientific and lifecycle evidence

- Controlled 5x4 EPSG:3857 field, native float64, north-up transform
  `(100,0,200000,0,-100,6000400)`. All 20 coordinate/value associations checked
  against the existing independently specified XY oracle and again in xarray.
- One valid zero, one NaN/masked cell, source `degree_angle` (not metre CRS units),
  masked sum **409**, six incident recorded links including a successful numerical
  Activity effect. Local exported SHA-256:
  `42f5382ce76fb6b3376465c6e483592f02955ab5c14465bd72985178535f6998`.
- Offline additional fixtures verify float64 value `16777217.125`, shifted origin,
  asymmetric shape, both native axis directions, exact uint64 values above signed
  int64, missing/conflicting units, unsupported inputs and budget rejection.
- Scoped origin/Runtime binding rejects before HTTP when inconsistent. Current
  membership is rechecked; real nonowner export/provenance access is denied with
  a valid credential positive control. Export/native response errors stay errors.
- Fresh process restores the original ID without resubmission. Reader files close,
  owned sessions release and cleanup preserves primary failures. No durable
  server-crash recovery or revision-pinned read claim.
- Selected server checkout: `244883d7b04f1eae2cc22e799575d33a9bdb2a43`, clean.
  This acceptance does not promote/merge that separate server candidate.
- Worldview: `608bef150ced0a109db98a5aad64ba4461beaa54`; manifest SHA-256
  `44d6ae17b2d85d43d09b8056349706682c6bb9ada7858f4b28c4a07074f7fab5`.
- Corrected component archive SHA-256:
  `e9fa36a605013743669077dbb87874915d98006130acc8119834a23b3cfcc6fa`.
  Source build uses the branch-carried patch over `8d4ef866...`, reconstructed tree
  `f05b578a14c707d8fd34348e56adc9a96bf25d66`; the dirty component source checkout
  intentionally contains that applied reviewed patch, not unrelated edits.
- Versions: NumPy 2.5.4, Rasterio 1.5.2, xarray 2026.9.0, HTTPX 0.28.1,
  pytest 9.1.1. Real-provider scientific accuracy/capacity were not evaluated.

External detailed evidence is under
`C:/Users/lumsd/AppData/Local/Temp/opencode/`: `analysis-final-offline.xml`,
`analysis-installed-offline.xml`, `analysis-final-coverage.json`, build/install logs,
and `analysis-provenance-live3/{run-report.json,functional.xml,exports.xml}` plus
the run's `analysis-provenance-*/analysis.json`. Private state/configuration files
contain TEST enrollment material and must not be published. The compact
[verification summary](results/scientific-analysis-verification.json) contains no
credentials. Source/installed manifests and closed ports were checked after runs.

## Contributor coordination

This is Python scientific-reader/read-only graph work. It reuses the existing
native exporter correction and leaves its publication, server identity/integration
gates and other contributors' work separate. No one was contacted or assigned.
Remote issue/PR state was not refreshed; no claim of exclusivity or acceptance.

## Smallest next actions

Review the bounded public API and representation choices. For broader support,
run the installed-package interpreter/platform matrix and acceptance against an
actual supported deployment. Complete server snapshot/resource-version contracts
before promising complete reproducible lineage or coherent export/query snapshots.
No further backend feature is necessary for the bounded demonstrated workflow.

## Suggested PR description

**Python: open faithful numerical rasters and inspect recorded observation origins**

Add an optional xarray/Rasterio analysis slice preserving native values, explicit
validity, pixel-center coordinates, units and precision. Associate exact-byte
native exports with existing origin-qualified observation handles. Add bounded,
typed read-only recorded neighborhoods with explicit partial/unknown-consistency
semantics, rather than presenting resolver plans as execution or inventing replay.

Validation: 860 offline tests in source and installed-wheel environments; existing
coverage/lint/package gates; controlled fresh-process compute/export/analysis and
recorded-execution checks, direct authorization denials and owned cleanup. Initial
scope is a single numerical rectilinear band; no complete lineage, new exporter,
snapshot/chunk protocol, shared deployment mutation or production certification.
