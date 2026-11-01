# Read-only review: assumption-explicit PaL experiment specification

## Recommendation

**Ready for maintainer review — specification only.** Separate reviewer found
no mandatory or important contribution defects. This does not approve scientific
hypotheses, taxonomy/ontology admission, live execution or full-study reproduction.
The requested experiments, client demonstrations and videos are not delivered yet.

## Reviewed state and scope

- Base: `17d0f31cc2d6f0fc7cf76ec3789759435b191324`.
- Initial candidate: `d9f28e49ab59174b3e544fe288c76468e3e10410`.
- Reviewed follow-up: `60e34f3fc8b7453545ea9921afd6353f1bba2c8b`.
- Branch: `feature/pal-assumption-explicit-demo`.
- Reviewer: separate read-only session `ses_e66c8a093ffestl4CQICuH0O97`.
- Repository instructions, contributor review guide and original milestone read.
- Initial diff: five files, specification/docs and eight unit checks; follow-up adds
  compact metadata receipts and a ninth check. No production SDK, Java, models or
  `.kwv` changes. Working tree clean before/after both review passes.
- No edits, services, resource registration, communications or remote mutations
  by reviewer. Remote PR status was not queried. Parent has made no push/PR.

## Findings

**Mandatory defects: none identified within this bounded scope.** Not exhaustive
proof of correctness or future Java compatibility.

Two optional improvements identified initially were addressed and re-reviewed:

1. Assert exact four-context, twelve-output and three-outputs-per-context limits,
   rather than only checking a total that could hide redistributed limits.
2. Retain portable compact retrieval metadata/QA methodology, instead of relying
   solely on an absolute external evidence directory. New receipts include
   requested/actual bounds, transforms, hash/units/nodata, public request recipe,
   input-QA counts and mutable-endpoint/nonpublication warnings. No raster bytes
   were included. Receipt details match the external records inspected.

## Validation actually performed

| Check | Independent result / limits |
| --- | --- |
| Complete base/candidate scope and changed-file inspection | PASS |
| Initial focused tests | PASS: 8 tests, 0.04s, exit 0 |
| Follow-up focused tests | PASS: 9 tests, 0.09s, exit 0 |
| Base-to-candidate whitespace | PASS in both passes |
| Mixed crop, both vegetation TIFF and specification hashes | PASS against receipts/contract |
| Actual archived classifications, expressions, T_RC_MAX and fr description | PASS source fidelity: ten/nine entries, unchanged equations, all ten thresholds25, fr0–1 |
| New portable crop metadata versus external receipts | PASS |
| Full regression / lint | Producer final logs inspected, not independently rerun: 1707 passed, one warning, 43.18s; fatal-error lint passed |
| Raster QA counts | Consistent between records/specification; NOT independently recomputed by reviewer |
| Java scenario bindings/numerical fields, permissions, notebooks/videos | NOT RUN / outside review authorization |

Focused runs used existing Python `pr102-upstream-312/Scripts/python.exe`, explicit
current `python-client/src;python-client/tools` imports, bytecode/cache disabled,
and fresh approved external pytest basetemp. No repository files were written.

## Scientific and presentation boundaries checked

- Scenario B is explicitly physical-interpretation sensitivity with the daily
  threshold held fixed, not a purported pure unit conversion or producer fact.
- No radiance-to-energy conversion or repair of raw input units is proposed.
- Source equations, constants and operational legend entries remain fixed.
- No synthetic fixture, metadata receipt, parser pass, finished Job or test total
  is promoted to scientific reproduction.
- Four passing modern demonstrations from the earlier audit are not claimed as
  authentic PaL execution; old synthetic video footage is not repurposed as PaL.
- Missingness, categorical equality, source interpolation, output dtype/tolerances,
  independent field references and native-export units remain explicit gates.
- The 100 raw zero pixels are not called a jointly valid zero-output test.
- Existing ExperimentRunner ownership and comparison-matrix semantics are respected:
  two patches are not mislabelled as two resolutions of one area.
- Existing recorder mechanisms are reused in the plan; no second SDK or recorder
  implementation was introduced.

## Smallest next actions

Review and authorize minimum project-local operational definitions and actual
scenario bindings. Freeze grid/normalization/masks/missingness/tolerances and
independent references, including any required zero control. Verify permissions
and run authorized Java work within the sequential four-context/sixteen-total-Job
cap. Validate actual results before recording; keep P4/full-study acceptance
separate. These are recommendations, not approval or evidence those steps ran.

## Prepared PR description — not published

Specify a user-selected PaL Radiation programme preserving archived operational
categories and equations while exposing uncertain radiation support as two
conditional hypotheses. Add bounded-workload and scientific/demo acceptance
contracts, portable input receipts and nine specification tests. No runnable
scenario bindings, live results, notebooks or videos are included. Original-product
interpretation and full-study reproduction remain unconfirmed.
