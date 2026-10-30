# Contribution review: PR101 semantic explorer

## Recommendation

**Needs contract/coordination evidence. No approval or merge.** A malformed-score
exception leak is fixed locally; 1,167 unit/integration tests and established
coverage/lint gates pass. The shipped authority/hierarchy functional suite collects
two tests, but has not run against Java. Collection and offline guards are not live
acceptance. All changes remain local under the user's publication pause.

## Reviewed state

- Repository: `integratedmodelling/klab-services`, `pr101-semantic-explorer`.
- Actual PR target, verified with `gh pr view 101 --json number,state,isDraft,baseRefName,baseRefOid,headRefName,headRefOid,url`:
  `feature/python-discover-compare-explain-integration`, base
  `6bb7a8224d78ad864d16b7e2884cb7677ffdcf5c`; open draft.
- Initial clean local/remote head: `69c424a3b0a06b87d9da2c8cc621b481833379ad`.
- Tested local candidate: `b4f51ac7573fb0130fbc88377c0e4cf3aeca4605`.
- Reviewed the feature diff against that actual base (10 original paths), including
  semantics, shared immutable projections, exports, docs and credential-safe
  acceptance runner; then included the six local fix/suite/documentation paths.
- Original milestone remains unchanged: Python over existing Java, simple readable
  code, unit and actual functional/Java evidence before merge. No Java, dependency,
  worldview, ontology or scientific-algorithm changes. No more scientific
  computation is authorized. Existing read-only review preceded these fixes.

## Capability and boundaries

A scientist searches an explicitly configured local authority, explicitly selects
a canonical code, obtains documentation descriptors without fetching external
content, resolves the identity, and queries direct hierarchy and semantic
predicates. Java remains authoritative. There is no inference, automatic candidate
selection, unbounded traversal, permanent semantic cache or atomic-snapshot claim.
Knowledge stamps detect observed drift, not every possible concurrent change.

## Findings, ordered by consequence

1. **Required live contract evidence remains blocked.** Actual authority
   selection/documentation/resolution and independently specified direct hierarchy
   and predicate expectations have not passed against unchanged Java at this
   candidate. Added `tests/functional/test_semantic_explorer.py`, registered the
   `semantic-explorer` suite and documented the exact fixture contract in
   `docs/semantic-explorer.md`. It requires an authorized `scientist.json` plus
   independent `semantic-explorer.json` expectations; none is available. There are
   no skipped/fallback successes. Semantic resolution may materialize knowledge;
   authorization is needed even though no numerical Job is submitted.
2. **Demonstrated malformed-response handling defect, fixed locally.**
   `semantics.py`, authority score validation: a JSON integer score `10**400` or
   `-(10**400)` escaped as `OverflowError` from `math.isfinite`, rather than the
   documented `ProtocolError`. Both new tests failed against the original source.
   Catch conversion overflow at that boundary and reject the invalid score; do not
   clamp it, silently drop it or invent a score. Existing valid scores retain their
   value/type. Normal existing-Java responses are not alleged to emit these values.
3. **Optional readability improvement, implemented.** Knowledge/result/candidate/
   page construction used long positional argument lists with similar strings.
   Named fields now make each DTO's meaning explicit, without a new abstraction
   or wire/state change. Existing full line/branch gates still pass.
4. **Integration/CI coordination remains.** Follow-ups in downstream stacked PRs
   are not automatically present here. Do not silently retarget, rebase or treat
   separate passing branches as an integrated candidate. Shared Java CI's adapter
   switch error is distinct from F1 runtime compatibility; neither ignoring it nor
   demanding a Java upgrade follows from this review. Jenkins cause is unverified.

No further demonstrated production defect was identified in this bounded review;
that is not exhaustive correctness or maintainer approval.

## Validation actually performed

Windows Python 3.12.7; interpreter
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-inspector-312/Scripts/python.exe`.
Commands ran in PR101 `python-client/`, with `PYTHONPATH` explicitly set to this
worktree's `python-client/src`, not the interpreter's PR99 editable installation.
No F4 isolated-child import claim is made for this F1 environment.

| Command / check | Revision / status | Establishes / limits |
| --- | --- | --- |
| `python -m pytest tests/unit/test_semantic_explorer.py -k oversized_authority -q --tb=short` | Original source plus two tests: Failed, exit 1; 2 OverflowErrors | Reproduces the malformed-score defect without Java |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov-branch --cov-report=json:C:/Users/lumsd/AppData/Local/Temp/opencode/pr101-review-20261030-coverage.json` | Exact b4f51ac75 content: Passed, exit 0; 1,167 tests, one existing Rasterio warning | Offline regression/contracts and functional fixture guardrails, not Java acceptance |
| `python tools/check_feature_coverage.py C:/Users/lumsd/AppData/Local/Temp/opencode/pr101-review-20261030-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py` | Same content: Passed, exit 0 | Existing 100% new-module line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same content: Passed, exit 0 each | Existing lint and whitespace gates |
| `python -m pytest tests/functional/test_semantic_explorer.py -o addopts= -m functional --collect-only -q --tb=short` | Same content: Passed collection, exit 0; 2 tests | Discovery only; no test bodies or Java calls |
| Actual Java authority/hierarchy functional suite | Blocked / Not run | Needs authorized prepared state and independently verified expectations |
| Remote CI for local candidate | Not run | Deliberate no-push policy; no CI rerun requested |

During suite development an offline AST guard initially also inspected fixture
dictionary `.get` calls and failed; it now restricts its checks to test bodies.
That was a new test defect, not a production HTTP-content fetch. The final complete
suite passed after correction. Raw coverage is outside the worktree.

## Scientific and lifecycle evidence

Source-derived tests preserve canonical identities, labels, classifications,
notifications, limits, missing knowledge revisions, drift and predicate argument
order. Server knowledge stays authoritative. Python cannot supply independent
proof that the chosen concepts have the promised scientific relation.

The new suite's expected URNs and direct parent/child relation must come from
independent Java/worldview evidence, not echoed Python outputs. It checks both
directions of `is_a` and both concepts' satisfiability. It does not establish all
reasoning correctness, authorization across distinct identities or numerical
scientific accuracy. No observation/session/context lifecycle or scientific Jobs
are created by it; semantic resolution itself is not universally side-effect-free.

No live test body ran, deployment configuration changed or credentials published
during this review. Prior F2/F3 live results are not reused as F1 acceptance.

## Contributor coordination

Python-only use of existing Reasoner HTTP contracts, no new dependency or Java
contract. No messages, remote commits, approvals, merges, CI bypass or notification
changes. This report is implementing-agent evidence, not independent review.

## Smallest next actions

1. Supply an explicitly authorized prepared semantic state with independently
   verified authority/hierarchy expectations; run the shipped named suite on Java.
2. Resolve stack integration, shared CI and independent review handling; validate
   the actual integrated candidate. Keep publication batched and user-authorized.
3. Preserve all outstanding milestone scientific acceptance; do not replace it
   with mocks, collection or this offline coverage result.

## Suggested PR description

Semantic authority discovery/explicit selection and direct hierarchy/predicate
inspection over existing Java. Reject malformed oversized authority scores using
the documented protocol error and make DTO construction directly readable.
1,167 offline tests and coverage/lint gates pass. Shipped live suite requires an
authorized independently specified fixture and has not run on Java. Keep draft.
