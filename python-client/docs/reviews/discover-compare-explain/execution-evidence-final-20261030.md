# Contribution review: PR98 recorded execution evidence

## Recommendation

**Needs contract/coordination evidence. Do not approve or merge yet.**

The existing implementation has real unchanged-Java owner-positive evidence and
passing unit tests. This pass closes a test-delivery gap by adding a shipped
functional suite with a valid nonowner positive control and private-access denial.
The suite still needs an authorized live fixture; collection and mocked guardrail
tests are not a functional pass. No production SDK or Java changes were needed.

## Reviewed state

- Repository/worktree: `integratedmodelling/klab-services`, `pr98-execution-evidence`.
- Actual target branch: `feature/python-resource-inspector` (#99).
- PR-reported base and Git merge-base: `e4580f17fc70622aa36b9c8dd7e6a93cf8737cd2`.
- Current target branch ref: `a40932317ea9cd0e2e0cfec223672baa663919b3`.
  These are different: the latest F2 follow-up has not been integrated into F3.
- Initial reviewed head: `002a0e3fff209be2771e67e2d812d240d3f89008`, clean at entry.
- Functional-test candidate: `7e86bd3b4cdbab520067e868c9d998830ebfe129`.
- Contributor review guide, milestone scope, existing feature review and actual
  diagram/permission/transport contracts applied. Initial code review was read-only;
  the functional test follow-up and this report are subsequent work.
- User requires simple readable code, unit tests, functional tests and actual
  existing-Java interactions before approval/merge. No Java extension or dependency
  upgrade is a Python client requirement.

## Capability and boundaries

The viewer decodes existing Dataflow and scoped recorded Activity diagrams into
bounded read-only views. It preserves actual identities/outcomes, missing metadata,
plan-versus-execution labels and unknown durable commit. It does not request a new
plan to explain prior execution, follow arbitrary diagram URLs, execute metadata,
invent scientific meaning or claim complete lineage.

The added functional suite reads already computed 20-cell results. It restores
handles, verifies actual numerical data and recorded model/contextualizer identity,
and checks owner/private and nonowner/shared positive controls before accepting a
nonowner denial. It creates no sessions, contexts, observations or scientific Jobs,
and does not release the operator's retained work.

## Findings, ordered by consequence

1. **Blocking acceptance gap: new shipped functional suite has not run against a
   valid two-user fixture.** The earlier owner-positive pilot is not a nonowner
   check. No `scientist.json`, `execution-evidence.json` or configured fixture
   environment was found in the approved workspace. Supply two valid authorized
   identities plus still-live shared/private observations. The owner must retain
   access before and after denial, and the nonowner must successfully read shared
   data/evidence. Invalid credentials, expired contexts and mocked denial are not
   substitutes. See `docs/execution-evidence.md` for the exact fixture and command.
2. **Test-delivery shortcoming: fixed in code, execution still pending.** The prior
   feature had no dedicated shipped functional test despite an external positive
   pilot. Candidate7e86bd3b4 adds two tests and registers `execution-evidence` in
   the existing runner. Guardrail unit tests prove that missing fixtures do not
   skip, identical credentials reject, invalid credentials do not count as denial,
   unexpected private access fails, and owner access is rechecked. These five
   tests use offline mocks and are not described as Java acceptance.
3. **Integration coordination: base drift.** Complete source review distinguishes
   F3's feature-specific delta from inherited F1/F2. The current target has later
   F2 fixes/tests/review changes absent from F3's ancestry. Agree on integration
   preserving published history and separate PRs, then test the actual integrated
   candidate before merging. Do not silently retarget/rebase or assume clean CI
   from an older head validates conflict resolution.
4. **External CI/approval coordination.** Prior published F3 head has green Python
   CI and the separately documented Java compiler failure. Latest follow-up CI must
   be checked after publication. The current GitHub identity is also the PR author;
   an approving self-review is not available. No check bypass or reviewer
   impersonation is justified by the Python test results.

No new demonstrated defect was found in the bounded production decoder/fetch
implementation. This is not exhaustive proof. No extra abstraction, broad refactor,
new dependency or production Java change was added to address the test gap.

## Validation actually performed

Windows Python3.12.7 at `pr99-inspector-312/Scripts/python.exe`; source explicitly
selected with `PYTHONPATH=pr98-execution-evidence/python-client/src`, not the editable
PR99 installation. Commands ran in `python-client/`. Coverage output was external.

| Check / command | Candidate and status | Establishes / limitation |
| --- | --- | --- |
| `python -m pytest tests/unit/test_execution_evidence.py tests/unit/test_execution_evidence_functional.py tests/unit/test_functional_runners.py -q --tb=short` | Exact7e86bd3b4 content: Passed, exit0;90 tests | Decoder and functional-test guardrails/routing, not live denial |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov-branch --cov-report=json:<external>/pr98-functional-guardrails-20261030-coverage.json` | Same content: Passed, exit0;1260 tests | One existing Rasterio warning; no functional suite included |
| `python tools/check_feature_coverage.py <external>/pr98-functional-guardrails-20261030-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py --new-module src/klab_client/inspection.py --new-module src/klab_client/evidence.py` | Same content: Passed, exit0 | Existing new-module100% line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same content: Passed, exit0 each | Fatal lint and whitespace gates |
| `python -m pytest tests/functional/test_execution_evidence.py --collect-only -q -o addopts= -m 'live and functional' -p no:cacheprovider` | Same content: Passed collection, exit0;2 tests | Test selection only; no assertions/services executed |
| `python tools/run_functional_acceptance.py --suite execution-evidence --state-dir <authorized-state> --report <fresh-report.xml>` | Blocked / Not run | No authorized retained two-user fixture available |
| Prior real Java numerical/persisted-diagram pilot | Historical002a0e3ff implementation: Passed owner-positive control | Recorded in execution-evidence-local-acceptance-20261029.json; not a new functional pass for this follow-up |

## Scientific and lifecycle evidence

The earlier unchanged Java run produced 20 finite synthetic elevation cells in m
and persisted charts naming the inspected model and terrain contextualizer. The
SDK source remains unchanged by this follow-up; nevertheless the new functional
suite itself is explicitly unrun. No independent measured-elevation accuracy,
complete lineage, snapshot lease or durable-root-commit claim is made.

All test-created scopes from earlier live passes were released and original
services/configuration restored. All five latest authorized computation attempts
are now used after the final PR99 functional run. No additional scientific work
was submitted for this F3 follow-up. Reading supplied retained fixtures would not
consume a computation attempt; creating replacement scientific work needs renewed
authorization. The tests never operate as an unrelated user without an explicitly
authorized fixture and never dispose of caller-owned work.

## Contributor coordination

Existing Java HTTP contracts only. No Java/POM/component/worldview changes,
credential publication, other-user operations, PR merges, retargeting or history
rewrites. This is the implementing agent's guide-based assessment, not independent
maintainer approval. Code is deliberately direct: one fixture loader, one client
factory, one recorded-result check and two tests using existing client APIs.

## Smallest next actions

1. Supply the authorized two-user retained fixture and run the named functional
   suite. Fix any resulting Python defect; do not manufacture server evidence.
2. Resolve current base/integration and shared CI handling explicitly; rerun
   applicable checks on the integrated candidate and obtain required review.
3. Only consider approval/merge after the functional/Java interaction checks pass.

## Suggested PR description

Bounded read-only execution evidence over existing Dataflow and scoped Activity
APIs, with honest partial/missing/plan labels. Live owner-positive charts verified
historically. Adds a shipped read-only owner/nonowner functional suite and offline
guardrails;1260 unit/integration tests and existing coverage/lint gates pass.
Functional two-user fixture, integrated-candidate verification and maintainer CI/
review decision remain required. Keep draft; do not approve or merge yet.
