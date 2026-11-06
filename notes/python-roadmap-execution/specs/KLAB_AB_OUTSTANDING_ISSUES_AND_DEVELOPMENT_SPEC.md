# k.LAB Python Client A+B — Reconcile Outstanding Issues, Then Resume Development

## Mission

Return to Python-client A+B engineering **now**, using the newly repaired local Modeler as evidence where relevant but not letting the IDE become the critical path again. First determine which outstanding issues were caused by the previously broken Java stack and whether they are now genuinely resolved; then select and complete the highest-value independently executable A+B development work.

**Expected outputs:**
- `notes/python-roadmap-execution/AB_OUTSTANDING_ISSUE_RECONCILIATION.md` — deduplicated evidence-backed issue disposition ledger.
- Updates to `docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md` and appropriate A+B plan/checkpoint, limited to independently justified status changes.
- Implementation, focused automated tests, and reviewable PR(s) for selected A+B work (if authorized existing repo workflow permits); report actual local/remote merge status, never assume merge.
- References to `notes/python-roadmap-execution/MODELER_IDE_STATE.md` and latest Modeler remediation report.

## Primary sources — examine actual current state

1. `docs/contributor-guides/KLAB_Python_Client_Planning_Guide.md`: scope A = dependable scientific Python client; B = remote Engine-grade Python user client; C = worker SDK **excluded**.
2. `docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md` and any current acceptance checklist, milestone/queue summaries, contributor instructions, and roadmap change log.
3. Active Git branches, PRs, issue trackers, open/closed issue history, CI results, and unmerged work; use authenticated connected repository tools where available, otherwise existing CLI Git/GitHub tooling. Do not rely on stale cached snapshots.
4. Local test suite, relevant integration fixtures, test skips/xfails/quarantines, documentation workarounds, and fault reports — including historical Java-service failures.
5. `notes/python-roadmap-execution/MODELER_IDE_STATE.md` plus the final updated `MODELER_REAL_DEM_REMEDIATION_REPORT.md`. Distinguish the reported Java Runtime null-scanner defect from Python-client issues.
6. Previous real-science acceptance scenarios (nonuniform **N** native grid/zero/nodata/orientation; **S** alternate-model selection/privacy), the 19-study feasibility review, and any CI Level-4 scientific-runner dependencies. These are historical leads; verify live state/PR IDs before asserting closure.

## Phase 0 — Baseline without redoing environment remediation

Read the IDE state contract. Run its read-only refresh **once** if available, and record required gates. The Modeler historically reached live Resolver selection of a registered, authentic DEM-backed elevation model; Runtime numerical execution failed on a confirmed Java null-scanner defect. A working Resolver does not imply full Runtime acceptance.

Check Git working tree, HEAD, relevant branches, pending PRs and CI before making changes. Preserve unrelated work. Do not upgrade the Java distribution, install components, restart local services, edit `imod`, or fix the scanner in this A+B pass.

## Phase 1 — Full inventory of outstanding A+B work

Build a unified ledger from **all** sources, not just GitHub issues:
- Open GitHub issues and PRs; candidate duplicates and issues closed as workarounds;
- Acceptance-map incomplete/conditional/untested rows from the original 24 + additional capability rows (verify actual counts);
- TODO/FIXME and excluded, skipped, quarantined, xfailed tests;
- Earlier Java dependency blockers misfiled under Python-client failures;
- CI Level-4 runner or artifact limitations;
- Scientific scenarios and original research-study readiness gaps;
- Regression gaps introduced by recent Python PRs;
- User-facing A+B API/documentation compatibility and installation/packaging gaps.

For each item record: stable ID or file location, scope A/B/out-of-scope C/Java/platform/infra, original symptom, provenance, owning subsystem, expected behavior, relevant IDE gates, prior evidence, current evidence, risk/severity, effort, and recommended action. Deduplicate without erasing issue history.

## Phase 2 — Determine what the repaired stack actually changes

Classify each item as one of:

- `RESOLVED_VERIFIED`: exact original failing workflow/test rerun passes on the specified relevant environment; result is reproducible and traceable.
- `BLOCKER_REMOVED_RETEST_REQUIRED`: prior prerequisite (Reasoner/worldview/model catalogs/Spatial/Resolver) is now demonstrably fixed but original acceptance behavior has not yet passed.
- `STILL_FAILS_REPRODUCED`: rerun of the original behavior fails with a precise signature.
- `JAVA_RUNTIME_BLOCKED`: direct dependency on the confirmed Java null-scanner failure (or another specifically reproduced Java blocker); Python cannot resolve this alone.
- `INFRA_OR_CI_BLOCKED`: e.g., external authorized Java runner/pinned artifacts; document exact owner.
- `PYTHON_ACTIONABLE`: missing/incorrect Python API, correctness, compatibility, or testing; actionable regardless of live IDE.
- `OBSOLETE_SUPERSEDED`: obsolete roadmap behavior with evidence/rationale and replacement; do **not** assume legacy feature is desired.
- `UNKNOWN_INSUFFICIENT_EVIDENCE`: cannot substantiate a current disposition; include specific bounded next test.

**Rule:** never close an issue solely because all four services report operational, Reasoner accepted `imod`, the model exists, or Resolver selected it. A passing isolated Java result does not close end-to-end Python acceptance. Record test environment and provenance for every claimed resolution.

## Phase 3 — Bounded retest campaign

Run a **targeted and finite** test matrix of the prior failure cases most likely affected by the repaired stack:
1. Semantic discovery/authorization using accepted `imod` and current service identities.
2. Python-side model and resource listing/retrieval against real registered catalogs.
3. Context creation and Resolver/model-selection behavior **only if authorized, safe, and not guaranteed to hit the known null-scanner defect**; avoid repeated destructive or duplicate Jobs. The documented Java failure may be sufficient to classify dependent tests blocked.
4. Existing N/S integration cases in their **original isolated harness**, plus any direct live IDE check only where contracts and data match. Do not conflate the two environments.
5. API compatibility tests for async/status/activity/provenance, snapshot serialization, nonuniform raster, partial refresh, authorization, and streaming, selected according to actual outstanding rows.
6. CI Level-4 runner availability: determine whether prerequisites have changed; do not mark completed simply because developer IDE services work.

For each retest, retain old failure signature, new result, exact command, branch/commit, service distribution, environment, timestamps, and test evidence. Prioritize direct evidence; do not turn this into an exhaustive scientific-replication run.

**Explicit scientific honesty:** the real USGS DEM has an independently frozen 400-cell oracle and Java direct contextualization passed, but end-to-end Runtime numerical output remains unverified due to null scanner. Keep research replication statuses separate from baseline Python functionality and do not imply that any of the 19 original studies has been reproduced.

## Phase 4 — Prioritize and execute A+B development

After the bounded audit, choose work by impact to A/B acceptance, user-visible utility, independence from Java Runtime blockers, effort, regression risk, and reviewer-friendly size. Prefer a coherent feature or regression fix with deterministic tests over another umbrella architecture rewrite.

Suggested candidate classes **only if inventory confirms them are still open**:
- Python UX/API improvements revealed by live model/resource discovery and registered DEM;
- incorrect Python error mapping for unavailable contexts/scanners and truthful diagnostics;
- robust clients for model catalog, semantic retrieval, authorization and provenance;
- unit/integration regression tests against fixed, non-mutating responses and N/S oracle cases;
- clearer isolation of offline, local-IDE, and remote-engine connection profiles;
- CI/release readiness of A+B independently of unavailable Level-4 infrastructure.

Do not reimplement merged features, chase C/worker-SDK scope, rewrite product Java to repair null scanner, or try to deliver all outstanding issues in one PR.

For each selected item: reproduce → specify acceptance criteria → implement minimal change → write deterministic test(s) → run relevant existing test suites and coverage gates → contributor-guide review → produce one coherent PR if repo workflow/permissions allow. Track branch base and accurate status. If blocked by Java, move immediately to the next Python-actionable item; do not begin IDE remediation.

## Phase 5 — Audit outputs, acceptance-map updates, and handoff

Create `AB_OUTSTANDING_ISSUE_RECONCILIATION.md` with:

1. Executive summary: original open inventory count, duplicates, breakdown by disposition, items truly verified resolved versus unblocked but untested.
2. Inventory methodology and exact evidence snapshot (`git` SHA, tracker query time, IDE-check time, runner identity).
3. One row per outstanding item with ID, A/B/Java/infra, original symptom, gate/test evidence, status, owner and exact next action.
4. **Former IDE-blocker delta**: issues resolved or changed by worldview, registered DEM/adapters, Neo4j Spatial, and successful Resolver selection; list any new Java null-scanner dependencies. Do not bulk-close.
5. Ranked backlog, separating immediately actionable A+B engineering from Java maintainer handoffs and external CI blockers.
6. Development actions actually taken: code paths, tests, branch/PR links/IDs, review outcome, actual merge status, acceptance-map changes.
7. Remaining gaps and narrow next A+B milestone; no generic 'needs more research' conclusion.

Update acceptance-map rows to `passed` only with directly linked passing evidence for the row's declared environment. For `BLOCKER_REMOVED_RETEST_REQUIRED`, keep the criterion incomplete and annotate changed prerequisite. Do not erase the original reason for a deferred gate.

## Execution rules and timebox in terms of scope, not clock

- First reconcile the **whole** active backlog, not cherry-picked open GitHub issues.
- Limit live retesting to cases whose outcomes can reasonably change with the repaired services; no broad Java diagnosis.
- After the audit, complete at least **one small, independent A+B item** if the inventory identifies an authorized, feasible candidate; otherwise deliver an explicit ranked implementation queue with concrete next PR specs and the reason no implementation was safe.
- Keep working on Python A+B if the IDE fails; record the exact environmental gate and choose an independent item.
- No unsolicited Java patches, ontology changes, distribution upgrades, plugin installs, service restarts, scheduled watches, or scientific research replication.
- Do not claim PRs merged until verified in the authoritative remote repository; no push or merge without existing authorization/workflow.
- Finish with useful development progress, not another standalone IDE repair report.

## Done when

The user can distinguish **actually resolved issues**, **newly unblocked but not retested work**, **still-live Python defects**, and **external Java/infra blockers**; every conclusion has evidence and maps to the acceptance criteria; the A+B plan reflects reality; and concrete Python development has resumed without making full IDE scientific execution a prerequisite.
