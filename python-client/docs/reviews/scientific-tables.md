# Contribution review: precision-preserving indexed pandas views

## Recommendation and scope

**Ready for maintainer review**, subject to final-head CI/package checks. This is
an author-conducted read-only review pass, not independent third-party approval.
Base: normal async PR #105 merge
`a7875e4ab98c56c2fa9f4665c8b5681bd76552bf` on
`feature/python-experiment-runner`. Feature branch: `feature/python-scientific-tables`.

This is a bounded scientific **representation** feature: optional pandas conversion
of 1–256 checked point-read rows. It is not the entire roadmap's multidimensional,
lazy, geospatial, categorical or temporal-discovery interface. There are no new
server endpoints, computations, Java/worldview changes or backend algorithms.

## End-to-end behavior inspected

Sync/async indexed reads capture declared primitive/contextualization and a passive
origin (configured URL, configured/cached Runtime ID or None, root context and
focus/observer scope). Origin drift across a read or configured credential material
in it fails closed. This is not an immutable storage revision or authorization.

`to_dataframe` validates row/metadata budgets, primitive and wire text/null parity,
then lazily imports pandas. Explicit object dtype retains large integers, Decimal,
False, text and None; duplicate offsets/order stay exact. Geometry/slice/origin
metadata are snapshotted without inventing axes/dates/units. Known categorization
is rejected even when stored numerically; a dictionary/worldview contract is needed.

Public methods retain compatibility: new ScientificData fields have defaults,
existing value codecs/HTTP reads are reused, and core imports need no pandas/NumPy.
Frame metadata describe initial input, not independently certified physics or
semantically enforced arithmetic after pandas operations.

## Findings and resolutions

No unresolved blocking defect found within this bounded review; not exhaustive
proof. Review and validation addressed these concrete issues:

- A primitive NUMBER alone could hide known numeric categorization: record the
  supplied contextualization and reject known CATEGORIZATION explicitly.
- Capturing origin only after multiple point requests could label earlier values
  with newly changed identity knowledge: capture before, compare afterward.
- Mutable attrs could alias source geometry/slice dictionaries: JSON snapshot them,
  retain missing metadata as None and enforce a serialized metadata budget.
- Unchecked scalar/Null values could misrepresent a modified source DTO: compare
  exact primitive and decoded original text, including Decimal tuples/nonfinite values.
- Direct sync loopback HTTP in a pytest process correctly hit the existing offline
  guard. Move the explicit loopback fixture into a bounded hidden subprocess, as
  existing integration tests do; do not weaken the guard or mark a fake live pass.
- An inherited async network-error test used an unnecessarily short wait deadline
  and could time out under full coverage before exercising the network error. Use
  no wait deadline for that branch; timeout-specific tests remain unchanged.

## Validation actually performed

| Check | Result / limits |
| --- | --- |
| Full offline unit/integration regression | PASS: 1891 tests, one existing Rasterio warning, 53.05s |
| Existing/async/new table coverage gates | PASS: no exclusions or lowered thresholds; table module 100% lines/branches and changed-code gate passes |
| Lint, wheel/sdist build | PASS |
| Installed core-only wheel | PASS: scientific imports absent; conversion explicitly requests `[tabular]`, no substitute DataFrame |
| Installed `[tabular]` wheel | PASS: arbitrary integer, Decimal precision, None and descriptive metadata retained; imports lazy until conversion |
| Real loopback HTTP subprocesses | PASS: sync and async value reads to pandas, origin, zero/null and duplicate offsets; bounded listener/thread cleanup |
| Representation reference tests | PASS: exact known scalar/text values, booleans, Decimal NaN, explicit event metadata, attrs isolation and negative cases |
| New real Java/provider/PaL experiment | NOT RUN / not claimed; converter has no backend operation or scientific model |

The initial offline failure log is preserved separately from the passing second
pass. No test-owned JVM/service was started for this pure representation feature.
The loopback fixture is not described as Java. Hosted results must be checked at
the published final head rather than inferred from local results.

## Scientific and lifecycle limits

`wire_missing` describes the explicit null token, not a guessed pandas validity
mask. A nonfinite numeric response stays nonfinite, and zero/False stay present.
Metadata can be edited/dropped by pandas/CSV/concatenation; this view is not a
faithful archival package, revision lease, semantic-unit enforcement system or
derived-result import/provenance path. Read-origin metadata are not credentials or
permissions; private context identifiers still need review before publication.

No native GeoTIFF metadata was repaired, and geospatial issue #10 remains open.
No scientific pass is inferred from a DataFrame, package build or test count.

## Contributor coordination and next actions

No maintainer/contributor is assigned review work by this report. Verify final
CI and source/package scope, then normal merge only under the user's sequential
feature instruction. Keep dictionary-backed categories, multidimensional/time
selection and lazy/chunk contracts as explicit future work rather than silently
extending this representation feature.
