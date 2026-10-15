# Python-client branch polish and release-preview pass

Requested 15 October 2026. Work remains on
`feature/python-client-scientific-analysis-provenance`; no publication is implied.

## Scope established from GitHub and local history

- Merged into `feature/python-client`: #84 foundation, #85 typed requests,
  #86 saved handles, #92 native exports/functional organization, #93 connection.
- Include the branch-carried corrected geospatial component patch and current
  uncommitted scientific analysis/provenance work.
- Source-review base for contribution attribution: PR #84's integration base
  `cc647483b80a903a81cac29b28b1fcec4eb541de`. The branch also inherits upstream
  work: do not call that unrelated work part of Eric's contribution.
- Preserve the earlier documentation organization and imported originals.
- Separate open #90 server integration, genuine production onboarding and future
  roadmap work from delivered capabilities. Remote PR states were read on this date;
  remote branches were not merged or reset.

## Checklist

- [x] Correct the four subsequent review findings with regression tests: Runtime
      identity before release, renewed/provider secret guards, file cleanup and
      explicit notebook-session release acknowledgement.
- [x] Corrected Windows source checks: 988 passed; unchanged coverage gates and lint passed.
- [x] Refresh corrected-source evidence: 988 source/installed Windows tests and
      988 on each Linux interpreter; 12 live workflows plus GeoJSON, seven fresh
      notebook recordings and successful owned cleanup.
- [x] Deliver the complete polish pass and both features together in draft PR #94;
      retain the empty description requested by the user.

- [x] Establish merged contributions, integration boundaries and preserved local work.
- [x] Review SDK modules, contribution-specific Java changes and component patch.
- [x] Make small simplifications/corrections with regression tests: bounded offset
      intake, specific exception handling and original-failure cleanup preservation.
- [x] Run strict selected Java regressions: 56 passed, no failures/errors/skips.
- [x] Run expanded controlled functional workflows: 12 passed plus one GeoJSON
      regression; includes the new native-connection-to-analysis tour. Owned cleanup passed.
- [x] Draft scientist-facing [release-preview notes](release-preview.md): what is
      delivered, short feature sections, limits and what comes next.
- [x] Replace the rejected slides with [seven actual notebook recordings](notebook-videos.md):
      34 cells and 10 plots executed in preflight and again during recording;
      includes actual kernel restart and two-user access. All MP4s decoded and
      owned stack cleanup passed. See [evidence](results/notebook-verification.json).
- [x] Complete optional-tooling/CI review and demo-evidence rejection tests.
- [x] Consolidate final offline coverage after adding 39 foundation-contract tests:
      930 passed in both Windows source and installed-wheel environments; existing
      coverage gates and lint passed (96.02% statements / 93.30% branches overall).
- [x] Finish clean installed-package validation: Windows and core-only smoke passed;
      Linux Python 3.11/3.12/3.13 each passed 930 tests, gates, lint and builds.
- [x] Final packaging check: all 22 wheel sources match; source archive includes
      byte-verified component patch/media and foundation tests, not private TEST state.
- [x] Finish branch-wide [review/evidence report](polish-review.md) and local
      documentation/media link checks. Verify original guides, selected credential
      absence, live SDK hashes, MP4 hashes and all eight owned ports closed.
- [x] Final scope check; no commit/push/PR/shared deployment unless separately authorized.

## Documentation/demo direction

Use concise feature sections with short clips and explicit status labels, inspired
by the requested patch-note style. A specific Wardogs reference link is pending;
do not claim to have inspected an unspecified patch. This is a branch preview,
not a tagged/public release. Existing version/support limits remain explicit.

Record actual notebook execution and useful output, not rendered claims or
slide-based API sketches. Capture only a dedicated browser context with public
example files; keep credentials and unrelated windows out of view. Reuse runnable
examples and add checks for the calculations/selections actually demonstrated.
Do not commit private state, model credentials or bulky diagnostic logs alongside clips.
