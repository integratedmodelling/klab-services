# Reboot/session handoff — 28 October 2026

## 29 October continuation

### Latest state: end-to-end local testing exhausted; restoration verified

**Pause checkpoint:** the user requested commit/push and resumption later. The
earlier cleanup report missed four Windows Terminal windows created by service
relaunches. They were subsequently identified by their exact service executable
title and hidden, not terminated; the unrelated existing terminal was untouched.
Verified listeners remain GraphDB PID 39860 (native launcher), Reasoner 42780,
Resources 33012, Resolver 20756 and Runtime 41292. All native PID records match
their listening processes. Future launches must be headless; visual terminal
cleanup is part of completion. Do not start further testing during this pause or
treat the three-attempt budget as renewed on resumption.

The user expanded authorization to local preparation, configuration/component
installation/restarts, Python fixes if demonstrated, restoration and publication,
with **three attempts × at most two observation Jobs on 20 cells**. Hosted/source
changes and merges remain excluded. **All three attempts are exhausted**:
1. Context creation failed: Neo4j spatial.layers missing; session release confirmed.
2. Preflight failed after a distribution/classpath mismatch; no owned objects.
3. Region Job 3 completed; elevation Job 4 failed with empty dataflow. Both owned
   context and session release confirmed; zero test contexts/spatial layers remained.

Local setup used clean pinned existing IMOD/staging sources, published generator
and geospatial KARs, and a digest-verified published spatial plugin. Final attempt
had operational Reasoner and terrain advertised on Runtime. No Python defect was
demonstrated; no Java/scientific/source workaround was made. Resources indexing
POST failures and Reasoner parser exceptions are diagnostic leads, not a proven
root cause. Numerical/model association remains missing. Offline: **1,246 pass**.

Temporary local changes were reverted: all 16 starting configuration/catalog paths
match original bytes; test components removed; spatial plugin no longer active;
original October 16 service distribution relaunched. Four local capability routes
respond; baseline Resources is empty and Reasoner nonoperational again. Test assets,
backups, sanitized captured logs and service index/cache data remain; no entire-DB
rollback was performed. PR99 remains draft. See
`docs/results/resource-inspector-local-acceptance-20261029.json` and the review.
Do not run a fourth attempt under the exhausted budget. Continue with native
model-discovery/indexing diagnosis without Java/scientific source changes.

### Earlier local preflight and read-only checks (historical)

The user subsequently authorized one bounded **local** pilot. It was attempted
and stopped at model inspection before creating any session/context/Job. Local
Resources returns an empty HTTP 200 body for the model and lists zero workspaces,
projects and models; Reasoner has no worldview/accepted generation 0. Native
local-owner read-only settings access succeeds: Runtime remote-component loading
is true. No provisioning, installation, imports or restart occurred. See
`docs/results/resource-inspector-local-pilot-20261029.json` and the updated review.
Do not treat this as a successful computation or silently retry the pilot.

See [the current read-only deployment handoff](PR99_DEPLOYMENT_HANDOFF.md) and
[timestamped public metadata](results/resource-inspector-reboot-check-20261029.json).
The recovered `80a60edc5` head's Python 3.11/3.12/3.13/core-only CI passed; Maven
still failed on the unchanged Java switch. Hosted IDs have changed. Reasoner
initialized during checks and a fresh authenticated inventory succeeded, but the
Resources-versus-Runtime generator advertisement concern persists. No new
computation or service change was performed. Operator logs/build/loading evidence
and fresh pilot authorization are still required. Historical handoff follows.

## Active task

Finish **PR #99: F2 resource/model inspector** first. Each feature has its own PR
and must be assessed using `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`.
Do not mark missing live evidence complete or treat passing offline tests as science.

## Checkout and checkpoints

- Active worktree: `C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-resource-inspector`.
- Active branch: `feature/python-resource-inspector`.
- Saved F2 follow-up: `872032a36a0f2e117c0ad7ee1d4d8d604e95b786`, pushed to origin.
  This handoff is a subsequent documentation-only commit.
- PR: https://github.com/integratedmodelling/klab-services/pull/99 (draft).
- PR base: `feature/python-semantic-explorer` at `69c424a3b`.
- Original combined worktree remains intact at
  `C:/Users/lumsd/Downloads/k1/klab-python-discover-compare-explain`.
- Combined/delivery checkpoint: `88c95c0cfe4c33202e9c462d054d2cdaf970ebf5`.
- Milestone base: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Separate draft PRs: F1 #101, F2 #99, F3 #98, F4 #100, delivery #102.
  None is merged. Downstream branches have not integrated the F2 follow-up;
  integration must be additive and deliberate, never a force-push/rewrite.

## What just happened

The user authorized **one** hosted pilot: one owned session/context, at most two
observation Jobs for a requested 5×4 EPSG:3857 area, readback and owned cleanup.
That authorization was consumed. No further computation is authorized by this note.

- Inspected `staging.vxii.basic.elevation-quantifier` successfully.
- The region Job completed; the elevation Job failed with `JobFailedError`.
- Java confirmed release of both owned context and session.
- After cleanup, the original Job status returned `MissingAssetError`; detailed
  failure diagnostics were not recovered.
- Read-only follow-up: Resources advertises `klab.component.generators`,
  `klab.component.geospatial`, and `klab.generators.geospatial.terrain`.
  Runtime and Resolver advertise only their internal component and no terrain call.
- This mismatch is observed, **not proof of the exact failure cause**. Pinned Java
  supports conditional remote component loading, so do not conclude solely from
  capability absence that execution is impossible.
- No imports, component installation/correction, service configuration changes,
  restarts, other-user access or extra scientific submissions were performed.

## Current implementation and evidence

- `tools/workflows/resource_inspection.py`: bounded F2 pilot, scoped recorded-model
  association, component/function inventory, conservative prerequisite guard,
  pre-cleanup safe Job diagnostics and explicit owned cleanup.
- `tools/run_resource_inspector_acceptance.py`: explicit authorization/fresh report.
- Existing functional runner suite: `connected-resource-inspection`.
- `tests/functional/test_resource_inspection.py`: actual live acceptance case.
- `tests/unit/test_resource_inspection_workflow.py`: deterministic lifecycle,
  bounds, scope, failure, credential-message omission and prerequisite checks.
- `docs/results/resource-inspector-hosted-pilot-20261028.json`: committed sanitized
  failed-pilot and capability evidence. Includes original identifiers for operator
  log correlation; no credentials/arbitrary stack traces.
- `docs/reviews/discover-compare-explain/resource-inspector-followup.md`: complete
  contribution-guide reassessment. Recommendation remains **Needs
  contract/coordination evidence — not ready**.
- `docs/resource-inspector.md`: current pilot instructions and limits.

The prerequisite/diagnostic improvements followed the failed pilot. They have not
been presented as a successful second computation. F2 SDK production source stayed
unchanged from the earlier reviewed checkpoint; new work is acceptance tooling.

## Verified checks

- F2 follow-up: **1,246 offline tests pass** on Windows Python 3.12.7, one existing
  Rasterio warning. F2/new pilot modules have 100% unexcluded line/branch coverage;
  existing changed-code coverage, Ruff fatal-error gate and whitespace checks pass.
- Exact executable follow-up candidate: `a7d57fc7b85732b60d65ac2452664029dcd6cbd4`;
  later `872032a36` is review-only documentation.
- New F2 CI was in progress when last inspected; do not assume its result.
- Combined checkpoint `88c95c0cf`: Linux Python 3.11/3.12/3.13, coverage/lint/builds
  and core-only CI passed. Windows combined suites: 1,392 tests on Python 3.12 and
  installed-wheel Python 3.11. These are not new F2-head acceptance.
- Separate Maven CI failed at `LanguageAdapter.java:1234`, non-exhaustive switch;
  Java is unchanged by this work. Jenkins also reported an error on the earlier
  F2 head. Triage without assuming a Python defect or permission to edit Java.

## Next actions, in order

1. Recheck Git state and PR #99 CI. Preserve both worktrees and all existing commits.
2. Have the selected deployment operator correlate committed pilot context/Job IDs
   with Runtime/Resolver logs, and verify the generator loading/routing path,
   permissions, existing archive provenance and exact service builds.
3. Establish an already compatible unchanged execution deployment, or obtain
   separate explicit authorization for any administration. Do not install anything
   on shared services or change Java/components/worldview source under this task.
4. Obtain separate authorization for another bounded F2 pilot after prerequisites
   are established. Demonstrate actual numerical result and inspected-model URN in
   its recorded accepted resolution, and confirm owned cleanup.
5. Rerun applicable tests and contributor review before changing draft/readiness.
   A full four-cell milestone matrix is not an unnecessary prerequisite for the
   narrower F2-only pilot.

## Environment and safe commands

F2 Python:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-inspector-312/Scripts/python.exe`.
Run from the active worktree's `python-client` directory:

```powershell
& C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-inspector-312/Scripts/python.exe -m pytest tests/unit tests/integration -q --tb=short
gh pr view 99 --repo integratedmodelling/klab-services --json headRefOid,statusCheckRollup
```

Private certificate contents/tokens must never be printed, committed or persisted
in evidence. Existing credentials are acquired privately by the current provider;
only explicit file paths are passed. Avoid default long pytest tracebacks for live
checks. Do not run a new live computation merely to restore session context.

External artifacts, if retained after reboot: `pr99-final-coverage.json`,
`pr99-hosted-pilot-20261028.json`, `pr99-install.log` under the approved OpenCode
temporary directory. Committed evidence/reviews survive even if temp artifacts or
this temporary worktree are removed. Clone/checkout the pushed feature branch if
necessary; the authoritative milestone specification is committed under
`docs/contributor-guides/KLAB_Python_Discover_Compare_Explain_Milestone.md`.

## Broader unfinished work (preserve)

F1 selected-authority/hierarchy acceptance, F3 computed recorded evidence, F4 real
four-case scientific/isolation/ownership acceptance, a valid second identity,
running deployment attestation, actual scientific notebook execution/throughput,
and final clean-candidate evidence package remain outstanding. Delivery tooling
exists in PR #102; declarations intentionally cannot produce ready delivery.
The local IDE last had empty Resources knowledge and Reasoner generation 0;
service processes/ports must be rechecked after reboot rather than assumed live.
