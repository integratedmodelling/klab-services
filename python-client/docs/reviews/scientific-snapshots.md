# Contribution review: offline scientific-read snapshots

## Recommendation

**Ready for maintainer review.** The local-only addition closes CAP-12's bounded
scientific inspection gap without adding a remote-state abstraction or dependency.
Author-performed read-only review against the actual base, with independently
specified scalar checks and an independently authored JSON fixture; not an
independent human approval. No remaining demonstrated blocking defect was found
within scope. Hosted checks and normal merge are still required.

## Reviewed state

- Integration: `feature/python-experiment-runner`, base
  `87ef80ff589428f7c088ba60abcb7cf2b88276eb`.
- Product candidate: `cf7f10db83e2a711bcf0519cac81d422e6c3d345`.
- Review includes the complete base diff: map, codec, validation extraction,
  ScientificData methods, example, tests, API/usage docs and CI gate. No inherited
  integration changes. Subsequent review/evidence text does not alter product code.
- Source-matched wheel, Python 3.12.7 on Windows; full dependency freeze/private
  commands and failures retained in the evidence directory below.
- Original nine PRs verified merged and ancestors. Newer develop is not silently
  substituted. Component #11 remains unmerged and is not required by this feature.

## Capability and boundaries

A scientist can retain an already-read 1..256-cell result as bounded JSON and
inspect it in a fresh offline Python process, optionally as the existing pandas
view. Large integers, Decimal wire precision, zero/False/null, native unit/semantic
metadata, explicit slice/traversal and optional origin survive. No world-coordinate
mapping is inferred. The codec reuses existing scalar/geometry/origin validation;
there is no scientific algorithm, new endpoint, remote mutation or retry.

This is not graph or committed-state recovery, a server snapshot, an authenticated
provenance package, scientific recalculation, worker execution or bulk export.
Filesystem policy is explicitly caller-owned. Metadata/data can be private; no
Client/credential state is serialized, but the codec is not a general redactor.

## Findings, ordered by consequence

1. **Fixed blocking defect:** snapshot metadata keys could silently collapse during
   JSON conversion (`{1: "a", "1": "b"}`). Reproduced three failing regression
   cases against the earlier installed candidate; `_scientific_snapshot.py` now
   rejects non-string keys before emission, including nested metadata.
2. **Fixed important defect:** explanatory table attributes incorrectly consumed
   the exact snapshot byte budget. An exact-fit snapshot failed its own reload
   budget test. The shared internal metadata-validation cap and actual encoded
   snapshot budget are now separate; exact UTF-8 boundary tests pass.
3. **Fixed diagnostic defect:** invalid origin URL leaked a ConfigurationError
   rather than a snapshot-input diagnostic. The existing origin rejection remains
   intact; the codec converts that category to a generic InvalidRequestError.

No scientific oracle, authorization rule, coverage gate or negative assertion was
weakened. Unknown schema versions and columns fail, harmless supplied geometry
extensions survive. Valid schema edits remain caller-provided data, not signed
server truth; this is an explicit limitation, not an integrity claim.

## Validation actually performed

Commands run from `python-client`, with an explicitly installed noneditable wheel:

| Check / command | Status | Establishes / limits |
|---|---|---|
| `python -m build --wheel --sdist --outdir <evidence>/final-dist` | Passed | Distribution builds, no publication |
| `python -m pip install --force-reinstall --no-deps <wheel>` and complete source-hash comparison | Passed | All43 package modules exactly match source bytes |
| `python -m pytest -q tests/unit tests/integration` with the unchanged workflow's coverage targets and `--cov-branch --cov-report=json:<evidence>/final-coverage.json` | Passed | **2817 tests**, one retained Rasterio georeferencing warning; not live Java acceptance |
| All16 `tools/check_feature_coverage.py` commands in `.github/workflows/python-client.yml`, same report/base/module arguments | Passed | Unexcluded new-module100% line/branch and changed-code gates; none removed |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82` | Passed | Established lint selection |
| `python -I examples/offline_scientific_read.py` | Passed | Installed SDK, network-forbidden fresh process, exact known values and invalid-schema failure |
| Separate fresh venv, wheel installed without extras, same demo plus absent optional-module probes | Passed | pandas/NumPy/xarray/Rasterio not installed or imported |
| Independent authored `tests/fixtures/scientific-snapshot-v1.json` | Passed | Loader checked without its own serializer, explicit geometry/slice retained |
| New real Java/scientific/provider execution | Not applicable | No server code/contract or scientific computation changed; historical S/I evidence remains bounded, not reattributed |

Wheel SHA256: `1a91cffa3942f515c2b1c61dbba092e729e21607c40a63bd6357ff006fdc24f9`.
Sdist SHA256: `3fc7070b631007a8f3498021f5842ea5701ca28781b3b4789e22488813670d9d`.
Raw commands, exit statuses, dependency freeze, source identities and earlier
failures are retained under
`C:/Users/lumsd/AppData/Local/Temp/opencode/scientific-snapshot-evidence-20261104/`.

## Scientific and lifecycle evidence

Independent scalar references include `2**53 + 1`, Decimal tuple `1.2300`, zero,
explicit null, false and Unicode text. The hand-authored fixture retains supplied
EPSG:3857 metadata without claiming those coordinates were newly scientifically
validated. Original wire text is the sole scalar encoding. Categories reject
without dictionary/worldview contracts. No credential input or network path is
introduced; the subprocess forbids socket connection. Local serialization cannot
grant server authorization or release another user's work. No background service,
thread, event loop, remote scope or new worktree is created by this feature.

## Contributor coordination and integration risks

No open services PR overlapped this contribution at baseline. Existing accepted
Connection/handles/Jobs retain their APIs and origin rules. The only refactor is
extracting existing table validation for reuse; table regressions remain green.
Version1 is a local inspection format, distinct from remote Handle version1.
No contributors were assigned or contacted and no release approval is implied.

## Smallest next actions / suggested PR description

Push this bounded contribution, confirm hosted Python3.11/3.12/3.13/core/Windows
checks and any required reviews, merge normally, verify actual integration, then
mark CAP-12 accepted in the existing map. Keep RC-P3.2/full graph recovery partial.
Problem: CSV or saved remote handles cannot faithfully retain an indexed read for
offline inspection. Outcome: bounded lossless local snapshots on ScientificData.
Example: `restored = ScientificData.loads(data.dumps())`. Validation and limits
are the table above; there is no new scientific computation or server guarantee.
