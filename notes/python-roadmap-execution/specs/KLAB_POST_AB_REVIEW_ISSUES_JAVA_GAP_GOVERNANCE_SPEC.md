# k.LAB — Post-A+B Completion Review, Issue Reconciliation, and Java Gap Governance

User-supplied queued execution specification, received 2026-11-06.

## Mission and trigger

Execute this specification only after the currently running A+B simulator-development campaign reports its final result. Do not interrupt, replace, or run concurrently with that campaign. This is a review, reconciliation, and governance pass—not an invitation to implement the remainder of the roadmap or restart IDE remediation.

Automatically begin this phase in the same development workflow at that checkpoint, without requesting another prompt or authorization for ordinary review/development actions. Preserve work and state across resets. Finish all remaining A+B implementation, tests, PRs, CI checks, and permitted merges first. After completing this review, stop and present results for a joint roadmap decision. Do not start roadmap C, historical research replication, or additional Java remediation.

Goals:
1. Independently assess what A+B actually delivered, including PRs, integration state, acceptance criteria, and limitations.
2. Review every applicable open GitHub issue, especially the ten historically tracked issues, against work now implemented in the Python client and simulated Java-compatible service.
3. Establish a durable, machine-readable Java Service Gap Register linking every simulated workaround to the actual Java contract, evidence, executable tests, and responsible upstream action.
4. Retire duplicate or fully superseded tracking issues when safe and permitted, without confusing simulation coverage with a real Java fix.
5. Deliver a concise decision brief enabling the user to decide whether and how to undertake roadmap C and/or remaining real-Java integration later. Do not start C or another large development campaign in this review.

## Essential starting sources

Verify actual branch, HEAD, working-tree, remote permissions, merge ancestry and current repository state first. Read as applicable:
- docs/contributor-guides/KLAB_Python_Client_Planning_Guide.md
- docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md (42 rows reported in last reconciliation; verify actual count)
- notes/python-roadmap-execution/AB_OUTSTANDING_ISSUE_RECONCILIATION.md
- notes/python-roadmap-execution/AB_COMPLETION_LEDGER.md
- notes/python-roadmap-execution/MODELER_IDE_STATE.md and its checker/evidence
- notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md
- Simulator source, scenario manifests, fixtures, contract tests, routing and replay implementation
- The A+B campaign's final commits, PRs, CI reports, package builds and release notes
- Repository contribution, GitHub issue, and PR rules
- Ferdinando's klab.staging.vxii harness if accessible; otherwise preserve it as an explicit unverified comparison target. Do not block on Monday's walkthrough.

Historical candidate GitHub issues: #89, #123, #124, #125, #126, #127, #129, #130, #144, #193. These are discovery seeds only: inspect their live titles, bodies, comments, labels, assignees, state and repository before classifying. Do not assume earlier summaries or the number ten are authoritative. Include additional relevant issues discovered by cross-references, PR references and current searches; distinguish issues owned by another maintainer or covering work outside A+B.

## Phase 1 — Independent A+B delivery audit

Reconstruct contributions using the remote repository, not just the CLI's task checklist. Inspect branches/merge bases, open and merged PRs, commits, diff scope, reviews, CI states and whether changes are incorporated into the intended maintained target branch. In particular, verify that PRs merged into a long-lived feature branch are actually reachable from the intended integration branch; do not equate a PR's merged flag with release integration.

For every A+B acceptance row produce one record with:
- acceptance_id, authoritative original requirement, and scope A/B/C/out-of-scope;
- implementation path and actual public Python API;
- associated commits/PRs, real merge target, build and package provenance;
- unit tests, simulator-driven HTTP tests, real-Java tests, and end-to-end science tests separately;
- contract confidence (JAVA_OBSERVED, HARNESS_EVIDENCED, SIMULATED_PROVISIONAL, JAVA_INCOMPATIBLE, NOT_EVALUATED);
- implementation status (COMPLETE, PARTIAL, NOT_IMPLEMENTED, SUPERSEDED, OUT_OF_SCOPE);
- acceptance status (REAL_VERIFIED, SIMULATOR_VERIFIED, BLOCKED_JAVA, BLOCKED_INFRA, FAILED, UNTESTED);
- explicit missing work, linked Java gap IDs and proof references.

Run focused automated test suites and package/lint checks for sampled critical paths, and the full release-relevant suite once if feasible. Verify that simulator tests use the production SDK public API, real HTTP transport and credible scope/job state—not only direct simulator internals. Test failure and cancellation paths as well as successful synthetic observation output. Prefer an actual packaged wheel/sdist install smoke test to source-tree-only imports. Do not conflate test count, task completion, merge count, or simulator success with true Java scientific acceptance.

Check simulator scope: separate dev/test tool, no accidental production SDK dependency on simulator modules, deterministic scenario seeds, no unexpected real-service calls, no leaking credentials, and no silent fallback on arbitrary Java HTTP errors. Record any findings as concrete issues or gaps without initiating an unrelated rewrite.

## Phase 2 — Open issue reconciliation

Build a fresh issue inventory using the GitHub API/CLI and include the ten candidate IDs above and any newly related issues. For each issue capture: repository, issue ID/URL, title, status, author/owner, relevant exact requirement(s), original acceptance statement, associated PRs, current reproduction, tests, simulator scenario(s), and Java gap IDs.

Classify each into one primary disposition:
- FIXED_IN_PYTHON: original Python defect addressed and regression tested.
- FIXED_IN_REAL_JAVA: real Java behavior verified fixed, with evidence and version.
- TRACKED_AS_JAVA_GAP: Java still missing/failing; simulator supplies dev/test substitute, not a real fix.
- SIMULATOR_ONLY: an expected client contract exists in a simulator, but real-Java behavior remains unverified.
- PARTIALLY_ADDRESSED: only some issue acceptance conditions satisfied.
- DUPLICATE_OR_SUPERSEDED: an identified canonical issue/register entry is the accepted successor, with no lost requirements.
- OUT_OF_SCOPE_C: worker SDK C, or another explicitly excluded scope.
- RESEARCH_OR_INFRA: studies, CI workers and other dependencies not equivalent to an A+B implementation gap.
- STILL_OPEN: independently actionable work remains.
- NEEDS_MAINTAINER_DECISION: ambiguous ownership/spec or issue closure policy.

### Issue closure policy — important

The user's goal is to reduce redundant open issues when their tracking function is fully replaced by the Java gap register and simulator contract tests. Support that goal, but keep closure semantics truthful.

Eligible to close (subject to GitHub permissions/maintainer conventions):
1. A Python defect has a committed/merged fix and passing regression evidence for all original acceptance conditions.
2. An issue was only asking for a mock/test fixture or A+B simulator capability, and that specific deliverable now exists with passing tests.
3. An obsolete/duplicate tracking ticket has a canonical maintained successor and an explicit closure comment explaining that Java functionality remains unimplemented/untested where applicable. The successor must be persistent, linked from the issue, and suitable for release tracking.

Not eligible to label as fixed or close merely because a simulator exists:
- A live Java implementation failure, absent API, source-level defect, interoperability incompatibility, or missing scientific runtime behavior.
- Issues involving user-facing Java functionality, security, data loss, or acceptance conditions beyond the simulator.
- Upstream maintainer-owned issues without explicit project policy to migrate them to a consolidated tracker.

When closing a truly superseded tracker, state “Closed as tracking migration / duplicate; Java implementation remains open under JAVA-GAP-####”, never “fixed by simulator.” Do not close an original product-bug issue just to reduce the open count if it is the only authoritative upstream work ticket. For disputed issues, draft a closure recommendation and comment text, then leave them open pending maintainer agreement. Do not change ownership, priorities or labels of others' issues without permission.

Make reconciliations idempotent: never double-comment or close twice; check last disposition and existing references. Preserve original issue links in both directions and track every acceptance condition before retirement. Do not automatically reopen unrelated closed issues.

## Phase 3 — Formal Java Service Gap Register

Create the following permanent artifacts (adapt paths to existing repository conventions, not the production client distribution):
1. docs/java-service-contract-gaps/README.md — purpose, rules, contract confidence, real-vs-simulated interpretation, how to add a gap, how to resolve it in Java, CI usage and maintainer handoff.
2. docs/java-service-contract-gaps/gaps.yaml — canonical machine-readable inventory; schema-validate in CI.
3. docs/java-service-contract-gaps/JAVA_GAP_STATUS.md — generated human-readable report with links and priorities, rather than manually diverging duplicate lists.
4. docs/java-service-contract-gaps/ISSUE_RECONCILIATION.md — every reviewed issue, disposition, closure action/status and evidence.
5. notes/python-roadmap-execution/POST_AB_DELIVERY_REVIEW.md — executive outcome, A+B row-by-row audit, residual risks, and suggested roadmap-C discussion.

Each gap must have a stable immutable ID (e.g. JAVA-GAP-0001, never reuse), and the following fields:

```yaml
id: JAVA-GAP-0001
title: "Runtime scientific observation scanner unavailable/null"
subsystem: runtime
java_component: "Runtime / specific source symbol if verified"
api_contract:
  method: POST
  path: "<actual observed endpoint; do not invent>"
  request_schema_ref: "<OpenAPI/DTO/capture>"
  expected_response_schema_ref: "<observed or explicitly proposed contract>"
requirement_ids: []
original_issues: []
python_test_refs: []
simulator_scenario_refs: []
real_java_test_refs: []
reproducer_ref: "<stable sanitized fixture/steps>"
observed_java_behavior: "<exact error and build, or NOT_TESTED>"
simulator_behavior: "<deterministic response/state, synthetic label>"
contract_confidence: SIMULATED_PROVISIONAL
java_status: CONFIRMED_DEFECT
python_status: SIMULATOR_VERIFIED
priority: P0
java_owner: UNASSIGNED
first_observed: "<ISO date>"
last_verified: "<ISO date>"
source_build_or_commit: "<exact version>"
resolution_criteria:
  - "same request passes against pinned real Java"
  - "same independent oracle checks pass when scientific results are involved"
links: []
notes: "<limitations>"
```

Actual schema can be adjusted to the repo's tooling, but retain all concepts. Never create a fabricated route, Java stack, defect, or version. If a field is unknown, use a defined unknown/null value and mark the evidence gap. A single Java defect may cover multiple simulator scenarios and GitHub issues; do not duplicate gaps for each test. Conversely, one broad GitHub issue may map to multiple distinct gaps. Document mapping both ways.

Require one linked executable contract case for each gap, ideally parametrized to run in simulator mode and real-Java mode with the same request fixtures and semantic assertions. Cases must be tagged with:
- SIMULATED_PASS — Python works against the proposed mock;
- REAL_PASS — verified against pinned real Java;
- REAL_FAIL — reproduced real Java failure (error, version, logs);
- REAL_NOT_RUN — no verified Java execution;
- CONTRACT_DRIFT — real and simulator disagree in schema/semantics.

Do not turn a REAL_NOT_RUN case green using the simulator. Do not automatically route unexpected real-Java failures into a simulation that hides them. Specify synthetic fixtures explicitly and preserve original real fixtures and independent oracles when relevant.

### Java-gap resolution lifecycle

DISCOVERED → REPRODUCED → SPECIFIED → JAVA_IMPLEMENTING → REAL_VERIFIED → CLOSED, with CONTRACT_DISPUTED, OBSOLETE, and BLOCKED branches. Simulator implementations can reach SPECIFIED but cannot alone reach REAL_VERIFIED or CLOSED as a Java capability.

When a Java fix lands: rerun the same real-Java contract test on a pinned version; compare output schema, state/lifecycle, authorization, cancellation, and scientific output as applicable; retire the hybrid fallback for that capability; update the gap and issues. A Java release passing a distinct harness does not automatically prove the specific Python flow works.

## Phase 4 — Harness comparison and divergence

If available without significant IDE work, inspect klab.staging.vxii and identify representative end-to-end request sequences and fixtures. Categorize matches/mismatches:
- actual observed Java contract;
- documented Java contract but unverified by this installation;
- provisional simulator contract needing Ferdinando's confirmation;
- genuine source defect or missing platform dependency.

Prioritize resolving contract uncertainty over expanding the simulator. Do not change Java product source or alter scientific models in this review. Preserve questions for Monday's walkthrough and a concrete gap handoff (e.g., initial session/context setup, service orchestration, numeric scanner behavior). No speculative claims that Java is absent because an ad hoc local environment failed.

## Phase 5 — Execution controls and reporting

- First perform a read-only inventory and generate closure candidates.
- Implement gap register, validation, tests, and cross-references before any issue closure.
- Only perform edits/closures within granted repository permissions and stated policy. Publish changes through existing PR/review workflow; do not bypass branch protections or declare merge before confirmation.
- If remote write rights are unavailable, produce ready-to-post issue comments and a closure manifest; continue the remaining audit rather than stopping.
- Keep the run bounded: review and classify, add traceability, test high-value paths, close safely eligible trackers. Do not start another simulator architecture rewrite, new research study, roadmap C, or Java remediation campaign.
- Preserve the user's IDE state, scientific resources and Python environments; this review is read-only against live services unless a bounded contract test is needed and safe.

## Required final summary

Give the user:
1. A+B delivery verdict: actual Python implementation completion, simulator acceptance, real-Java integration, published/merged status, and any failed/untested rows (counts plus exact IDs).
2. Issue disposition: issues reviewed, resolved by genuine implementation, closed as migrated/duplicate, open Java defects, unresolved external/research, maintainer decisions; actual link for each changed issue.
3. Java gap totals: CONFIRMED_DEFECT, MISSING_API, CONTRACT_PROVISIONAL, JAVA_FIXED_VERIFIED, NOT_TESTED, and highest-priority blockers.
4. CI contract matrix: simulator versus pinned Java, distinct pass/fail/not-run and where source/harness evidence stands.
5. A concise proposed path for the remaining roadmap (especially C) as a decision for the user, without starting it.

Success is an auditable, enforceable separation of Python implementation success, simulator support, and Java delivery obligations—not a smaller open-issue count.
