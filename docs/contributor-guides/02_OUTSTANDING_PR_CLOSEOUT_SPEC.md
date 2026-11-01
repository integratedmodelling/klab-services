# Outstanding Contribution & Integration Closeout — CLI Execution Specification

**Status:** Ready for controlled implementation planning, **not** an instruction to merge, close or publish PRs.  
**Baseline:** 2026-10-31 public inventory; revalidate at execution.  
**Scope:** All publicly identified currently outstanding contributions, including separately scoped Java identity work.

## Human-readable overview — why this closeout exists

We have useful Python functionality, but several active contributions remain draft because important live acceptance tests have not passed. Rather than start the pasture milestone on unstable foundations, finish and scientifically validate the existing work first. The aim is not to close GitHub tickets for appearances: the aim is to deliver accepted, independently reviewed contributions—or document honestly why a specific one is blocked or superseded. Preserve previously merged work.

## Required preliminary audit (must run before edits)

1. Read `AGENTS.md`, repository instructions, `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`, modified planning guide, original milestone specification, `python-client/docs/contracts.md`, `public-api.md`, `functional-testing.md`, `scientific-analysis-contracts.md`, and applicable review reports.
2. Record `git remote -v`, local branches, working-tree status, active worktrees, upstream `develop` and `feature/python-client` SHA, each PR's head/base SHA, draft/open/closed state, CI outcomes and review comments; never assume branch ancestry.
3. Inventory **every currently open PR** in the relevant repository(s) and compare with the baseline below. Add any newly found PR to the live tracker; do not assume the list remains exhaustive.
4. Identify the actual Java, worldview, Hub, Runtime, resource components and credential/test-user deployment tuple, including binaries/checksums where possible; when unavailable mark `UNATTESTED`.
5. Record environmental permissions and limits. Read-only exploration is allowed; live submissions, remote deletion, deployment/import, PR changes and merges need explicit authorization from the project owner/environment administrator.
6. Create isolated worktrees for proposed fixes, preserving unrelated edits. Review is read-only; implementations/fixes occur only after review phase closes.

## Baseline PR ledger

| PR | Work | Recorded 31-Oct state | Independent review gate |
|---|---|---|---|
| https://github.com/integratedmodelling/klab-services/pull/90 | Cross-service identity, authorization and federation integration | Open draft, separate Java/integration track | Java strict/native/federation and two-user identity tests, exact combined tree |
| https://github.com/integratedmodelling/klab-services/pull/98 | F3 scoped execution-evidence viewer | Open draft, nonowner two-user fixture blocked | Authenticated owner/nonowner tests, evidence belongs to computed observation |
| https://github.com/integratedmodelling/klab-services/pull/100 | F4 independent bounded experiment runner | Open draft, hosted sequential acceptance failed | Sequential + two-worker + partial-failure; numerical validation; no cross-run leakage |
| https://github.com/integratedmodelling/klab-services/pull/102 | Scientific matrix, notebook and PR delivery evidence | Open draft, full acceptance false | Required combined 12-case gate, notebook, evidence, installed package, re-run on final exact candidate |

Do not reopen merged #80/#81/#84/#85/#86/#92/#93/#94/#95/#96/#97/#99/#101 solely for this work. Revalidate affected functionality in final combined gates. Recheck the live PR list before declaring completion.

## Execution sequence and dependency policy

**C0 — Freeze baseline**. Produce `closeout-baseline.md` and machine-readable PR ledger with SHA/status, branch graph, environment, evidence timestamp. Classify overlap between PRs #90/#98/#100/#102. Identify missing server binaries, accounts, advertised exports and model fixtures.

**C1 — PR #90 (parallel, Java-led).** Inspect cross-service identity/routing against current `develop` and proposed Python integration. Verify origin-qualified identity and authority, positive/negative private/shared data access, service advertisement for both users, permissions for reconnect/release, lifecycle and regressions. No merge until relevant strict/native Java tests and combination checks pass on actual candidate. If issue is a **test harness deficiency** rather than a source defect, fix the minimal fixture with evidence; if new Java behavior is required, isolate and coordinate it. Deliver read-only contributor review and reviewer-ready PR notes.

**C2 — PR #98 (F3).** Before asserting Java defect, fix two-user fixture configuration: each principal receives compatible authenticated service advertisements, accepted semantic knowledge and usable runtime setup. Reproduce owner-success, authorized-shared success and unauthorized-private denial with correct statuses; show evidence association to actual activities/results, no caller information leak. Scope cleanup must not delete others' work. Test missing/stale/denied evidence. Repeat against selected candidate and document server/harness differences.

**C3 — PR #100 (F4).** Reproduce the 31-Oct hosted failure: three timed-out numeric waits, one context failure, two downstream cases unexecuted. Preserve failed run IDs and job handles; distinguish delayed completion, missing data materialization, wrong status transition, timing budget or service defects. No blind retries after ambiguous mutations. Implement only demonstrated fixes and prove independent sequential experiments, bounded two-worker execution, sibling failure isolation, no leaked handles, numerical value checks, recovery and cleanup. Include time/cost bounds and explicit workload authorization (historical ceiling 20 Jobs/200 cells is a **limit**, not an acceptance pass). Never mark `FINISHED` as equivalent to validated numeric output.

**C4 — PR #102 (delivery).** Once F3/F4 dependencies are resolved, run the exact current **12-case acceptance matrix** described in source (4 compatibility + 8 integration), without silently dropping a case. Independently verify two variation × two resolution scientific outcomes, units/masks/coordinates, actual advertised exports or document blocking missing Java exporter; real two-user permissions; fresh-process reopening of **original results**, not recomputation; bounded parallel/failure behavior; executable installed-wheel notebook, results plots and comparisons; throughput measurement with environment/run IDs. Generate sanitized hashed machine evidence plus readable summary, and refuse final READY when any mandatory gate fails. Verify any local corrected GeoTIFF component independently; do not quietly claim it is deployed or publish a substituted raster as Java-native export.

**C5 — Combined integration and review.** Re-fetch target branches; calculate exact candidate tree/SHA and provenance; check upstream drift, conflicts, dependencies, working-tree cleanliness, package/build/lint, relevant Java and Python gates. Rerun affected scientific and authorization checks against the **same** candidate. For every PR and integrated result, invoke read-only `KLAB_CLI_Contribution_Review.md` review; classify `READY FOR MAINTAINER REVIEW`, `NEEDS FIXES`, or `NEEDS CONTRACT/COORDINATION EVIDENCE`; make minimum corrections, retest and re-review. Prepare PR description packages only. No automatic publication, approval, merge or closing action.

## Mandatory acceptance checklist by PR

### #90 — Java identity & federation

- [ ] Exact merged candidate checked against both source branches, with relevant inherited security fixes.
- [ ] Authorized shared case succeeds; unauthorized private case fails safely; access/release rules tested across two actual principals.
- [ ] Service advertisements, issuer/audience/origin, Runtime binding and identity changes behave according to actual contracts.
- [ ] Relevant native/strict/federation Java tests pass on exact combined tree, or PR remains blocked with precise evidence.
- [ ] Read-only contributor review and maintainer-readable compatibility report exist.

### #98 — recorded evidence

- [ ] Owner can inspect original activity and related numerical result.
- [ ] Valid shared access and private denial tested using two correctly configured principals; no HTTP 500 masking a fixture error accepted as security evidence.
- [ ] Evidence missing/expired/not-authorized states handled accurately; no fabricated activities.
- [ ] Exact candidate tested, isolation/cleanup verified and contributor review completed.

### #100 — bounded runner

- [ ] Independently calculated numerical outputs from actual Java runs checked; no zero-cell technical pass.
- [ ] Sequential and authorized two-worker cases succeed and preserve experiment isolation.
- [ ] One intentionally failing sibling does not corrupt surviving work; recoverable handles preserved.
- [ ] Timeout/cancel/unknown-completion and release semantics explicitly measured.
- [ ] Contributor review completed on actual candidate and intended base.

### #102 — integrated delivery

- [ ] Source-defined 12 acceptance cases have individual status PASS / FAIL / BLOCKED / NOT RUN and run IDs.
- [ ] Scientific 2×2 variations/resolutions and actual result-oracle check completed.
- [ ] Notebook actually executed from installed package with matching artifacts/results; evidence digest and stack identities match.
- [ ] Exports and permissions are truly validated; no test waivers masquerade as passes.
- [ ] Relevant performance measurements are recorded, not invented or inferred from a cap.
- [ ] Final contributor review, PR package, docs and limitations aligned with actual evidence.

## Disposition states and project closeout rule

For each PR assign exactly one: `MERGED/ACCEPTED`, `READY_FOR_MAINTAINER_REVIEW`, `BLOCKED_EXTERNAL_DEPENDENCY`, `NEEDS_FIXES`, `SUPERSEDED_WITH_MAINTAINER_APPROVAL`, or `CLOSED_NOT_MERGED_WITH_RATIONALE`. **Never convert `READY` into `MERGED` without maintainer action.** If external dependencies cannot be completed, closeout may issue a **conditional handoff report**, but MUST NOT claim all PR acceptance criteria met. Project-wide `CLOSEOUT_ACCEPTED` only if every targeted PR is merged/accepted or explicitly superseded with an agreed traceable replacement and no unmet mandatory claim.

## Required artifacts (names illustrative; follow repository conventions)

- PR ledger (URL, head/base SHA, current status, owner, dependencies, acceptance result, review report).
- Exact commands and environment manifest for every test, including skipped cases.
- Reproduced defect records, minimum fixes, cleanup and rollback details.
- `reviews/closeout/{pr-90,pr-98,pr-100,pr-102,integration}.md` under repo-conventional review folder.
- Sanitized evidence manifest hashes, installed package version/hash, Java/worldview/component/deployment tuple, executed notebook and truthful throughput results.
- Plain-English `CLOSEOUT_SUMMARY.md`: what was finished, what was blocked, what can scientists now do, and what PaL work may rely upon.

## Rules for the implementing CLI

Do not claim acceptance based on green offline CI alone; do not alter production deployment, Java APIs, ontology/worldview, shared data, tokens or remote PR state without explicit authorization; do not hide test failures, overwrite original evidence, auto-retry ambiguous submissions, or expand this closeout into a new general SDK milestone. If unavailable services prevent live acceptance, preserve the blocker and move independently executable parts forward. Stop and seek owner approval when authorization or scientific meaning changes materially.

## Sources

- Status evidence (2026-10-31): https://eric-resume.up.railway.app/python-client-progress/
- PRs: https://github.com/integratedmodelling/klab-services/pull/90 , /98 , /100 , /102
- Original scientific milestone: `docs/contributor-guides/KLAB_Python_Discover_Compare_Explain_Milestone.md`
- Review authority: `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`
