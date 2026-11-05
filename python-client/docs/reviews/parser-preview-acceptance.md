# Contributor review: actual Resources parser/source receipts

2026-11-05 UTC; CLI/author review, not independent human approval.
Independent branch `feature/python-source-parser-acceptance`, base
`fe46ddc5ec3c188f62cd1d0b0703c6e54a4b9241`; does not stack on Q1's unaccepted code.

## Scope and architecture

Closes a bounded evidence gap between four-family DTO wire compatibility and
actual Resources parsing. Reuses SourcePreviews, current controller/WorkspaceManager
and owned fixture runner; no public API/Java/compiler/dependency changes. Scientist
value: inspect a source receipt with actual service/source identity rather than
assuming JSON round-trip means parsing works. Whole RC-P4.2 stays PARTIAL because
maintained revision and semantic validation are separate obligations.

## Risk-ordered findings / controls

1. No fake acceptance: valid and invalid texts must reach the real parsers, with
   pinned source hashes and service identity. Unit tests only check harness guards.
   Actual four-family outcomes remain NOT_RUN until the isolated service result.
2. Parsers may return diagnostics, null or transport/server errors. Invalid source
   must never become success; the report records refusal error types without
   inventing authoritative fine-grained syntax diagnostics. Positive receipts must
   first succeed so a universally failing service cannot satisfy acceptance.
3. Edge trimming is explicit and both hashes retained; no other source rewrite is
   allowed. Source/worldview tests use new synthetic material, not altered historical
   research. No model weights, scientific values or method are inferred.
4. Only capabilitiesGET and the four parseAssetPOST routes are permitted in the
   workflow audit. No write/submit/import/start/release route is invoked. It creates
   no user scopes/Jobs, so there is no remote scientific work to dispose.
5. Actor source is parsed, not started. The k.Actors grammar/current semantic API
   remain authoritative; a `return 0` example claims no persistent agent lifecycle.
6. Parent private-state ACL, hidden process launches, component pins, no ambiguous
   replay and existing-Java preservation apply. Families are attempted independently
   and failures retained; there is no blanket skip or whole-B acceptance.

## Required validation / merge

Actual named four-family parser check, installed noneditable wheel/source parity,
offline/wire/coverage/lint/package checks, exact candidate identity, final hosted
checks and normal eligible merge remain required. No tests or acceptance are
predeclared passed. Evidence belongs outside Git under `ab-three-study-20261105/`.
The prior failed Q1 runs remain unaccepted and separately retained.

## Initial actual parser diagnosis

`q5-live-v1` reached all four real parser families. Behavior valid/invalid checks
pass. Namespace's test source incorrectly used scalar `set to 3`; actual parser
diagnostic is `mismatched input '3' expecting RULE_EXPR`. Existing accepted model
syntax uses `set to [expression]`, so the fixture now uses `[3]`. Ontology/strategy
receipts passed SDK fidelity policy but the harness wrongly required trimming
even when the parser preserved all edges; receipt comparison now respects the
explicit `edge_whitespace_trimmed` flag and rejects inconsistent hashes. These are
test-contract corrections, not relaxed scientific semantics or Java changes.
One corrected frozen-candidate live attempt is required; no unbounded fixture loop.
Initial2860offline tests,16coverage gates, lint and build/install/source parity
pass; wheel SHA256 `f36a08561d7a958039b0ca81d024fe9b87ea1011865a1aaa6cab160a2bd9c26e`.
Those package checks are not actual parser acceptance. Final corrected-candidate
receipts remain required before a PR is proposed.

## Final tested candidate and recommendation

Implementation `96f6b0b243f739693e4a86850364e1e34b0531a2` passes actual
`q5-live-v2`: all four valid-source receipts, invalid-source refusals, explicit
source hashes/edge policies, one Resources identity, blank-source zeroHTTP and
parser/capabilities-only route audit. Namespace/ontology/strategy preserve edges;
behavior explicitly trims them. Behavior's malformed source is refused as
ProtocolError, not fabricated as a rich syntax-diagnostic receipt. Publication,
scientific execution and authoritative knowledge revision remain NOT_RUN/unknown.
All captured owned processes/listeners closed and preexisting Java identities
remained unchanged. No remote scientific scope/Job was created by the test.

Installed final gates: **2862offline tests,16coverage gates, lint, wheel/sdist,
noneditable installation and source parity PASSED**; one retained Rasterio
NotGeoreferencedWarning. Wheel SHA256
`0b59c80fe627510c8fc5b5f1e1c3752e96d65b717bf1407413a74d238f445fd6`,
sdist`29053376e638778a658379d62846ccb0f71d177f562f1f2a08e58755a6c6054b`.
Commands/identity/gate logs: prior external queue root`ab-q5-corrected/`;
actualJava/source/component/functional hashes and cleanup: current program root
`q5-live-v2/result.json`, original `q5-live-v1/` failure retained. Java production,
components and worldview are unchanged from the accepted isolated tuple.

**Ready for bounded maintainer review / normal eligible merge**, not independent
human approval or full RC-P4.2/B acceptance. Subsequent documentation-only updates
do not alter tested implementation. Hosted checks/protection/merge remain a
separate gate; no bypass or release authorization is implied.

Hosted outcome: PR201 OPEN, head8cdd6bec5. Python3.11–3.13/core/Windows/JenkinsPASS;
Maven packaging run41954884151FAILED initial and one same-head retry due missing
`org.integratedmodelling:klab.product:1.0.1-SNAPSHOT`. Public snapshot metadata404
corroborates availability failure; no Java source/dependency change was made.
**Merge remains blocked**, not waived as optional. Need the approved artifact or
separately reviewed build-contract recovery, then successful normal checks. This
does not revoke the separately pinned actual-parser result, nor close full B.

Packaging unblock: separatePR202mergednormally at6305e1e78 afterallreported
checksPASS, includingfullpackaging. This branch nowmergesthatacceptedCI-onlyfix;
no parser/SDK/Java/worldview/componentcodechanges. Compare exactfunctional/tool/
installedsource hashes withq5-live-v2before reusing the bounded accepted result.
Newfinal-headhostedchecks remain required; no failureiswaived or scientific
sourceupdated. OriginalfailedPR201checksandinitialfixturefailure remain evidence.
