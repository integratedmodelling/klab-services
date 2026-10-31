# Contribution review: PR102 integration, simplicity and readability

## Recommendation

**Needs contract/coordination evidence; leave draft and unmerged.** The code-quality
pass found and corrected concrete defects;1,594 offline tests, existing coverage/lint,
builds and isolated core-only wheel smoke pass. No further demonstrated defect
remains from this review, but this is not exhaustive proof or scientific acceptance.
The user explicitly prohibits merging102 even if checks pass. Live matrix,
permissions, deployment verification, scientific notebook and throughput remain
required; no new computation allowance was granted or used here.

## Reviewed state

- Worktree: `pr102-milestone-delivery`; target remains `feature/python-experiment-runner`.
- Actual base: `0898fbd807355d81567f524fde04d782ad015142`.
- Entry head: `08c89610f1dee796a4babfc20f4ca674ba69b6ea`.
- Integration: `a3fd127df31ed67aed3e667210af57b43b168eae`. The suite-registration
  conflict was resolved additively: preserve all eight milestone suites and the
  new bounded-runner suite/explicit consent gate.
- Tested executable candidate: `4539feb7a08f4fa6e6c4c676d20b10b6d2380fd9`.
- Reviewed the entire delivery diff: oracle/workflow/fixture validators, candidate
  identity, acceptance adapters, notebook, restoration, throughput, builder,
  package/CI/tests/docs. Initial inspection was read-only; fixes followed it.
- Applied `KLAB_CLI_Contribution_Review.md`, original milestone, repository rules
  and the explicit SIMPLE/human-readable/no-merge directions. F1/2 are merged;
  F3/4 remain draft with live gates. Their latest fixes are inherited, not accepted
  merely by inclusion. Earlier reviews are historical, not this candidate's proof.

## Capability and boundaries

Combine four Python features into a reproducible two-variation/two-resolution
scientific comparison using existing Java, independent raster/analysis checks,
actual recorded evidence, original-result restoration, notebook/HTML and
correctness-gated throughput. Package consistent identified evidence or explicitly
report blocked. Controlled fixture checks are not measured provider accuracy;
plans are not execution/durable commit; handles do not confer ownership or replay.
No Java/parser/component/worldview/ontology/algorithm edits or service restarts.

## Findings, ordered by consequence

1. **Scientific input interpretation, fixed.** `milestone._request` used geometry
   truthiness, dropping explicitfalse,0,empty list/string/map. Five regressions
   failed before correction. Geometry is now absent or an object with exactly the
   six supported rectangular fields; malformed data cannot silently alter a request.
2. **Delivery integrity, fixed.** Source artifacts were checked but copied output
   was not. Injecting changed copy contents still produced `ready-for-review`.
   This regression failed before the fix. Copied artifacts now use the existing
   bounded path/hash check; mismatches produce a blocked package.
3. **Recorded model association, strengthened.** Source inspection found that the
   numerical chart-node gate did not explicitly link the inspected model URN to
   successful recorded resolution. Matrix, restoration and partial-failure paths
   now supply that URN. Tests reject unrelated models and failed resolutions.
   This does not turn partial evidence into complete lineage.
4. **Configuration boundaries, fixed.** Oversized affine/timeout integers leaked
   OverflowError; nontext deployment hashes leaked TypeError. Four regressions
   failed before correction, alongside the five geometry failures. A small finite
   number guard and explicit text checks now reject without clamping values or
   invoking the remote factory for an invalid timeout.
5. **Live acceptance remains blocked.** No accepted independent four-case fixture,
   fully verified deployment tuple or new numerical allowance is available. F3's
   shared-query failure and certified Resources semantic-readiness gap remain.
   Collection, kernel smoke and synthetic validator inputs are not live acceptance.

## Simplicity and human-readability assessment

**Passes this code-quality review after the changes**, not a blanket correctness
guarantee. Concrete improvements:

- Replace duplicated positional comparison tuples with one small named-field
  `_workload_identity` mapping, making fixed scientific choices inspectable.
- Traverse processed throughput results once for cell/byte/stage calculations.
- Name absolute/relative tolerances once per validation pass instead of repeatedly
  parsing them inside raster/analysis cell loops; preserve the scientific rules.
- Use explicit geometry branches and ordinary loops for model-selection evidence.
- Retain module boundaries, SDK interfaces, ownership/error/consent distinctions
  and fail-closed delivery. No generic validation engine, class hierarchy, dependency
  injection framework, new dependency or unrelated formatting/refactor was added.
- Standalone evidence helpers keep the earlier call shape; complete milestone
  paths always supply model identity. More lines in an explicit check are preferred
  over dense expressions that hide scientific or permission conditions.

## Validation actually performed

Windows Python3.12.7, dedicated `pr102-upstream-312/Scripts/python.exe`, selected
worktree SDK. Commands from `python-client`; reports/builds outside Git.

| Check / command | Status | Meaning / limits |
| --- | --- | --- |
| Focused new geometry/numeric/hash cases before fixes | Failed, exit1;9 regressions | Reproduces boundary defects;no live science |
| Altered-copy regression before fix | Failed, exit1;1 case | Reproduces incorrect ready status |
| Exact workflow pytest command: `tests/unit tests/integration`, all configured coverage modules, branch/json reporting | Passed, exit0;1,594 tests in45.89s | Actual local notebook kernel/HTML and subprocess tests;one existing Rasterio warning;not scientific notebook acceptance |
| Exact workflow `tools/check_feature_coverage.py` base and every new-module argument | Passed, exit0 | Unexcluded100% new-module line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`;`git diff --check` | Passed, exit0 each | Established fatal lint/whitespace |
| `python -m build --outdir <external>/pr102-readability-final-build-20261031` | Passed, exit0 | Wheel/sdist from corrected source |
| Fresh venv, final-wheel install and `python -I` core-only smoke | Passed, exit0 | Four capability exports import;NumPy/Rasterio/xarray absent/not imported;handle roundtrip |
| Relevant functional files with `--collect-only -q -o addopts= -m 'live and functional'` | Passed collection, exit0;16 cases | Eight milestone,three runner,two semantic,one inspector,two evidence;zero live assertions |
| `workflows.milestone.source_identity()` with explicit `src`/`tools` paths | Passed | Clean source identity and installed metadata/source match |
| Java matrix/permissions/scientific notebook/throughput | Blocked / Not run | Fixture/deployment gaps and no new computation consent |

Source SHA256: `e1c1edc3e0804a30e0aac67776cbae4eb8f641046d2bf1ae6bfbcb7e47d753a0`.
Installed SDK SHA256: `4f4172f267f656425a15094c5695624ea7b8f9528c44683d5cda4b1b11f33045`.
Documentation-only publication follows tested executable content. Confirm final
head's Java/Python3.11–3.13/core-only/Jenkins checks separately; CI is not live science.

## Scientific and lifecycle evidence

Keep independent reference provenance, values/units/CRS/transform/precision/masks,
valid zero, variations/resolutions and original-result identity as explicit gates.
Inspected model association is checked alongside the actual numerical computation
nodes. No expected value is substituted for a Java result. Missing/failed states
remain partial/failed. Deployment declarations are not running binary attestation;
the adapter still records `deployment_verified=false`, not a manual success flip.

No102 remote objects were created/released and no services were changed. Preserve
the prior restored services. Local kernel tests own/close their kernels. Credentials,
private certificates and machine state are not committed or packaged as evidence.

## Contributor coordination

Python-only delivery over existing interfaces; no new Java route, NiFi processor,
authority implementation or model. No inferred reviewer assignment, approving
self-review or maintainer impersonation. This implementing-agent assessment is not
independent maintainer approval. User review is explicitly reserved for later.

## Smallest next actions

1. Review this pass with the user; **do not merge102**.
2. Resolve fixture/readiness and F3/4 acceptance; select an independently justified
   matrix and verify the running Java/component/worldview tuple.
3. Obtain explicit bounded computation permission before acceptance, the separate
   scientific notebook or throughput. Record failures and cleanup honestly.
4. Reassess the resulting candidate; do not relabel missing evidence as a passing
   overall contributor recommendation. Put the computer to sleep after this pass
   as the user requested, without terminating the restored services.

## Suggested PR description

Integrates current feature fixes and preserves functional suites. Simplifies
scientific comparison and explicit geometry handling, rejects numeric/hash type
errors, verifies selected-model association and copied delivery artifacts.1,594
offline tests, coverage/lint, builds and core-only wheel smoke pass. Live matrix,
notebook, permissions, attestation and throughput remain blocked. Draft, reserved
for later user review and not authorized for merge.
