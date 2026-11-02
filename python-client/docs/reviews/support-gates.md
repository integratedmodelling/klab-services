# Contribution review: support matrix and release-evidence checker

## Recommendation and scope

Ready for maintainer review as **development support/release tooling**, subject to
final tests and hosted CI. This author-conducted read-only review is not independent
approval and does not authorize publication or certify the scientific roadmap.
Base: normal PR #107 merge `bb4f62ce01bb41d93f506ee34c19687b401d8640`.

The support matrix separates package eligibility from verified Python/OS/backend
coverage, records the bounded merged features and links open blockers #108–112.
No support owner/SLA, deprecation commitment, tag or release is assigned implicitly.
Package description is corrected to mention the existing bounded async workflow;
version remains 0.1.0 and no public SDK methods change.

## Tool behavior and findings

The standalone checker uses stdlib only, runs no external command/network/service
and writes no report itself. It accepts an explicit candidate, evidence root and
bounded manifest. Fixed required gates cannot be omitted to create eligibility;
missing entries become UNKNOWN. A PASS needs same-candidate local byte-pinned
evidence. Duplicate states/keys, wrong candidates, invalid paths, symlinks,
missing/changed/oversized evidence fail closed. Both stat size and actual bytes
read are bounded, preventing an append/stale-stat case from unbounded hashing.

Output contains states and structural evidence verification only; never raw paths,
evidence contents, credentials or private exception details. Exit 0 means eligible
for human review, 3 means valid but nonpassing/incomplete evidence and 2 means
invalid evidence. `publication_authorized` is always false, including exit 0.

The critical nonclaim is explicit: an operator can fabricate a PASS or its log.
Hashes bind supplied bytes; they do not authenticate producers, parse test truth,
certify scientific validity or confer authorization. The trusted stable-directory
assumption excludes filesystem races/privileged adversaries as a security proof.
Human review of references, compatibility, scale and maintainer permission remains
mandatory. This is not an automatic package-release workflow.

## Verification scope

Focused tests exercise positive structural eligibility without publication,
FAIL/UNKNOWN/BLOCKED/NOT_RUN and missing gates, candidate mismatch, malformed and
duplicate records, changed evidence, path traversal, symlink rejection, nonfiles,
oversize and actual-read growth budgets, script entry and sanitized CLI exits.
Existing/new coverage and package checks must remain green. Loopback or simulated
receipt files are software tests, not genuine scientific/scale/compatibility passes.

Verified local results: **55 focused cases**, **1995 offline unit/integration
tests** (one existing Rasterio warning, 66.09s), new checker 100% unexcluded
line/branch coverage, all existing coverage gates, lint and wheel/sdist build pass.
A fresh installed core wheel imports sync/async/graph/scientific-read DTOs and
closes local clients without optional scientific packages; archive inspection
confirms the checker is not installed as an SDK module and package metadata
accurately mentions bounded native async support. Final published-head CI remains
a separate merge gate.

No new scientific Job, authoring mutation, agent or broker/Runtime service was
launched for this feature. No scientific acceptance is inferred from offline test
counts, synthetic receipt hashes or package builds. The actual release remains
blocked until the required evidence and human decisions exist. Merge of this
bounded tooling is not completion of all release/support or scientific gates.

## Next actions

Check final-head CI, package metadata and source-only tool boundary; merge normally
under the user's one-feature-per-PR instruction. Return to the explicitly deferred
platform/scientific blockers with the documented scope/authorization decisions.
Do not publish or sleep under an all-work-complete condition while those remain.
