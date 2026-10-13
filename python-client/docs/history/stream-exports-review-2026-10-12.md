# Historical contribution review: streamed observation exports (#91)

This records the original export checkpoint, before PR #92's combined review.
Recommendations and next actions below are historical. Current findings and
verification are in [the authoritative record](../exports-verification.md).

## Recommendation

**Ready for maintainer review as a bounded client contribution**, after the recorded
candidate checks. This is not an official policy finding or acceptance guarantee.
At this original feature checkpoint, no PR was requested or opened. The scientific acceptance is substantial-observation
GeoJSON, not all advertised formats, raster metadata or provider accuracy.

## Reviewed state

- Checkout: `klab-python-stream-exports/python-client` in `klab-services`.
- Target/base: `feature/python-client`, `90bebb5f35e60ad002f106c8d655e66d7e00d89d`.
- Feature branch: `feature/python-client-stream-exports`, issue #91, assignee Dobbes.
- Pre-commit candidate: that base plus the explicit export API/transport, tests,
  example, docs, development-only acceptance runner and added export coverage gate.
- Exact source/test snapshot SHA-256: `79292c1bf453307fce054a514907058efbfa53b3666171933acdd70b8bdc8a2e`,
  recorded externally in `export-candidate-manifest.json`. Documentation additions
  do not change this snapshot; Git and the issue update record the stored commit.
- Live server: explicit separate candidate `244883d7b04f1eae2cc22e799575d33a9bdb2a43`,
  including develop `3b01ceaa9` / federation `16e1be908`. Those fixes are not silently
  incorporated into this feature's ancestry; #89 / draft PR #90 remain separate.
- Exporter: unmodified official published component `1.0-20260926.083046-222`,
  archive SHA-256 `e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644`.

## Capability and boundaries

A scientist can inspect the configured Runtime's advertised representations and
download a bound committed observation to a caller-chosen file with bounded body
memory and atomic publication. The generic example restores an existing handle
in a fresh process and exports it without releasing existing remote work.

Input flows through known Runtime binding validation, capabilities, the existing
`/export/OBSERVATION/{urn}` route and the server's selected exporter. Native numeric
context-qualified URNs follow `ObservationImpl.idFromUrn`; applicability and ACLs
stay server-side. The client preserves raw bytes and supplied format metadata.

No new exporter, re-encoding, server-side bounded-memory guarantee, temporal or
semantic export facade, DTO catalog/provenance redesign, scientific adapter or
durable operation/replay contract is delivered.

## Findings, ordered by consequence

No blocking client defect was found in the final inspected source. This is bounded
evidence, not exhaustive proof.

1. **Dependency/setup failure, resolved with a supported distribution:** the current
   geospatial source tip fails compilation against the selected API because of
   its obsolete Reference.Exporter annotation. That attempt is Blocked. The
   acceptance environment instead installs the unchanged published archive above,
   which advertises and executes GeoJSON/GeoTIFF/PNG. No fake exporter or scientific
   algorithm patch was introduced.
2. **Scientific precision evidence:** the initial `1e-7`-degree corner tolerance
   failed for legitimate six-decimal coordinate quantization. The declared
   `1e-6`-degree policy is tied to the pinned export precision. Four distinct corners,
   closed polygon, asset identity and independent inverse Mercator coordinates
   are still required; this does not allow arbitrary geometry/axis errors.
3. **Explicit limitations:** EOF without a declared checksum/length does not prove
   application-level completeness. Network timeout is per read, not a total transfer
   deadline. Capability metadata uses the existing buffered JSON path. Hard-link
   unsupported filesystems fail explicitly. The tested GeoJSON has no raster values,
   measurement units or validity mask; these are N/A rather than fabricated fields.
4. **Optional later improvements:** typed export parameters, numeric GeoTIFF
   acceptance, additional integrity metadata where supplied and format-specific
   adapters belong to later bounded issues, not acceptance requirements invented
   from inferred maintainer preferences.

## Validation actually performed

Commands run from `python-client` using the identified isolated interpreters.

| Check / command | Revision/environment | Status | Establishes / limits |
| --- | --- | --- | --- |
| `python -m pytest -q --cov=klab_client --cov-branch --cov-report=json` | Recorded source snapshot; Windows Python 3.12 | Passed: 550, 8 live deselected | Maintained deterministic and legacy contracts |
| `python tools/check_feature_coverage.py coverage.json --base 90bebb5f3 --new-module src/klab_client/exports.py` | Same snapshot | Passed | Unexcluded 100% new-module line/branch and changed-code gates |
| Fresh `uv pip install '<checkout>[dev,local]'`, installed-module hash checks, same pytest/gate commands | Linux Python 3.11/3.12/3.13 | Passed | Each fresh install tests actual candidate source, not editable leakage |
| `python -m ruff check src tests examples tools/run_export_acceptance.py --select E9,F63,F7,F82` | Windows and Linux matrix | Passed | Targeted static checks |
| `python -m build` | All three Linux environments | Passed | Wheel and source distribution |
| Core-only installed Python 3.11 wheel smoke | Fresh Windows environment, HTTP forbidden | Passed | No optional science/dev dependency required for export API |
| `python tools/run_export_acceptance.py --server-repo <explicit-server> --java <JDK21> --worldview <pinned-imod> --component <pinned-kar> --state-dir <fresh-state>` | Recorded source snapshot, JDK 21.0.12.1+1, server/exact component above | Passed | Genuine scoped GeoJSON, fresh-process generic example, reference geometry, owned cleanup |
| Pristine source component Maven `package` | geospatial source tip `8d4ef866` and selected host API | Blocked | Dependency mismatch, not a passing build or client defect |

Detailed commands, exit codes, hashes and reports are external in the named matrix
and TEST-state directories. Pre-commit live reports explicitly record a dirty
client and its full production hashes. Later commit/checks must be identified
separately instead of relabelling this run.

## Scientific and lifecycle evidence

- Genuine `geospatial.io.geojson` exporter and native asset ID/URN are checked.
- Expected geometry comes from independent EPSG:3857 inverse projection of the
  known 200000–200500 / 6000000–6000400 rectangle, with declared angular tolerance.
- The generic example runs in another Python process with independently supplied
  credentials, restoring a secret-free reference and producing the same geometry.
- A generated 64 MiB response tests under 2 MiB traced client allocation; file
  publication waits for complete streaming/closure, not a pre-buffered bytes body.
- Truncation, wrong MIME/parameters/encoding/status, byte limits, HTTP failures,
  partial/zero disk writes, interruption, cleanup failure and two concurrent
  no-clobber writers have meaningful checks.
- Existing files survive failure; redirects are not followed; binary error bodies
  are not buffered or echoed. No remote cancel/resubmit/release is hidden in export.
- Test-created contexts/sessions and all five owned JVMs were explicitly cleaned.
- Raster values/zero/missingness, scientific adapters, provider accuracy, large
  live exporter capacity and nonlocal federation are not certified by this test.

## Contributor coordination

This implements the roadmap's existing-exporter slice under Eric's issue #91.
Official geospatial implementation remains another repository's dependency, not
newly authored client science. Arnab's Python conventions and Andrea's spatial
experience may inform future feedback; no review duty or assignment is inferred
from expertise or invitations. NiFi-specific workflow ownership is untouched.

## Smallest next actions

Record the scoped commit, verify its source identity and fresh candidate checks,
push the feature branch and keep #91 open for eventual review/merge. Do not open
a PR. The nearest broader scientific workflow is numeric observation restoration
and GeoTIFF export with independent raster/value/mask checks, not a new bulk protocol.

## Suggested future PR description

Wrap existing advertised native observation exporters with explicit format
selection, bounded client download memory and failure-safe local publication.
Provide a fresh-process handle-to-file example and meaningful stream/file/identity
negative controls. Report the verified GeoJSON/reference geometry and exact
server/component compatibility, while keeping other formats and scientific
metadata/adapters explicitly unverified. No PR had been created at that checkpoint.

## Follow-up review submission

On 13 October 2026 the user requested the export feature and functional-workflow
organization together in a PR to `feature/python-client`. See
[functional testing](../functional-testing.md) for the shared scenarios, 573-test
offline matrix, seven local functional checks and rerun numerical throughput.
The GeoTIFF evidence explicitly uses a separately corrected local component; its
Java correction is not part of the Python PR, and the old published component's
numerical scientific failure remains recorded.
