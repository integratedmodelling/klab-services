# Contribution review: PR100 after the current PR98 review

## Recommendation

**Needs contract/coordination evidence; keep draft and unmerged.** The integrated
candidate passes1,474 offline tests, coverage and lint. It now ships a dedicated
Java functional suite rather than relying only on a Python HTTP fixture. That suite
has been collected and its consent/configuration guards tested, but it has not run
against Java. PR98's real nonowner/shared acceptance also remains blocked. Neither
new test code nor green CI replaces those missing live results.

## Reviewed state

- Worktree: `pr100-experiment-runner`, `feature/python-experiment-runner`.
- Target remains `feature/python-execution-evidence`, not the milestone branch:
  PR98 has not merged. Base `e96d54fd4803bf9a8dacfa7a8ab319eb8cb43dae`.
- Integration merge `b1b674a4d72bf0d050b6c3303df16bbaf46fcbd6` preserves the
  published chain and includes the reviewed F1/F2/F3 fixes without conflict.
- Tested executable candidate `63912f474009d45d54b4049e1b8ce24acfb13b6b`.
- Follow-up merge `f661c31029437870eb1951845fa3e9c1a45038f3` adds only PR98's
  metadata-readiness review note; no tested executable content changed.
- Included full feature delta against that actual base, existing numeric-validation
  and readability fixes, Job local-cancellation behavior, runner/manifest APIs,
  tests and the new functional-suite delivery. No Java or scientific source changes.
- Applies the contributor review guide, bounded F4 agreement, existing current
  Java Context/Job/permission/lifecycle contracts and explicit no-PR102 instruction.

## Capability and boundaries

Run independent named typed experiments on freshly owned connections and scopes,
with bounded threads, per-worker deadlines and explicit retention/release policy.
Capture recoverable Job/result identities across failure without replaying unknown
submissions. Loading a local manifest is not server continuation or a snapshot lease.

The new shipped suite selects an existing explicitly named model. It validates real
grid metadata, finite values/units and recorded model association for four sequential
and four two-worker experiments, then checks sibling survival after an intentional
Python processor failure on two more computed results. Before-transmission guards
bound each worker to one session/context and two observation Jobs. The full workload
is at most20 Jobs/10 contexts/200 cells and requires separate authorization.

## Findings, ordered by consequence

1. **Live acceptance remains missing.** No PR100 computation allowance has been
   granted. The added suite is not a live pass. It also does not establish remote
   Job failure, remote cancellation, restart behavior, independent accuracy or
   throughput. Do not silently drop those limitations or call processor failure
   a remote scientific failure.
2. **Functional-test delivery gap, addressed but not accepted.** Previously no
   dedicated shipped F4 functional body existed. `test_bounded_experiments.py`
   now contains three real-service cases, and `connected-bounded-experiments` is
   registered in the existing runner with its explicit owned-work consent gate.
   Direct pytest invocations also reject absent consent/profile/certificate/model
   settings before connecting. Offline guards do not substitute HTTP responses.
3. **Dependency acceptance blocks merge.** PR98 now has an actual owner-positive
   pass, but its ordinary nonowner/shared query returns500 and private denial was
   not reached. Both users need verified Java service advertisements and compatible
   peer authentication; fixture readiness remains unresolved. The tests released
   their scopes and restored the October30 services. No earlier fixture or F2
   result is reused as F4 acceptance.
4. **Earlier production numeric defect remains fixed.** Timeout conversion and
   oversized manifest durations reject with InvalidRequestError; regression tests
   remain present. The direct worker-state branches retain the original behavior
   without introducing a new abstraction. No additional demonstrated production
   runner defect was found in this source review; this is not exhaustive proof.

## Validation actually performed

Windows Python3.12.7, dedicated `pr100-review-312/Scripts/python.exe` editable
installation, explicit `PYTHONPATH=pr100-experiment-runner/python-client/src`.
Commands run from `python-client` with external coverage output:

| Check / command | Status | Meaning / limits |
| --- | --- | --- |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance --cov-branch --cov-report=json:<external>/pr100-dependent-coverage.json` | Passed, exit0;1,474 tests in40.02s | Includes real isolated subprocess restoration and thread overlap;one existing Rasterio warning;no Java functional claims |
| `python tools/check_feature_coverage.py <external>/pr100-dependent-coverage.json` with the exact workflow base/new-module arguments plus `--new-module src/klab_client/evidence.py --new-module src/klab_client/experiments.py` | Passed, exit0 | Existing new-module100% line/branch and changed-code gates |
| `python -m pytest tests/unit/test_bounded_experiments_functional.py tests/unit/test_functional_runners.py -q --tb=short` | Passed, exit0;38 tests | Final consent/configuration guards and suite routing, not Java execution |
| `python -m pytest tests/functional/test_bounded_experiments.py --collect-only -q -o addopts= -m 'live and functional' -p no:cacheprovider` | Passed collection, exit0;3 cases | Selection only;zero scientific operations |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`;`git diff --check` | Passed, exit0 each | Fatal lint and whitespace |
| Shipped Java functional suite | Blocked / Not run | Requires compatible prepared fixture and its own new computation allowance |

## Scientific and lifecycle evidence

No PR100 sessions, contexts or scientific Jobs were created in this pass. Terminal
owned release, failed siblings, deadlines, local/remote cancellation distinctions,
ambiguous submission retention and manifest privacy have deterministic unit checks.
The existing process integration server is Python, not Java. The new live suite's
unknown outcomes retain recoverable manifests for operator follow-up rather than
forcing disposal or resubmission. Numerical validity beyond the bounded fixture,
two-variation/two-resolution reference comparisons and throughput are unverified.

## Contributor coordination

Python-only test delivery and inherited reviewed fixes. No Java, parser, component,
ontology, worldview or algorithm edits; no reviewer impersonation or approving
self-review. PR102 remains untouched. This is an implementing-agent contributor
assessment, not independent maintainer approval or permission to bypass checks.

## Smallest next actions

1. Resolve the compatible fixture/service-binding gate and PR98 acceptance first.
2. Obtain separate explicit F4 computation permission and run the shipped cases;
   retain/resolve unknown work and verify cleanup. Record missing remote-failure
   and cancellation evidence honestly rather than extrapolating from unit tests.
3. Recheck the actual resulting base/head and required CI. Merge only after the
   dependency and applicable contributor acceptance gates pass.

## Suggested PR description

Bounded independent Python experiment orchestration over existing Java APIs, with
recoverable handles and explicit failure/retention policy.1,474 offline tests and
coverage/lint pass. Adds an explicitly authorized real-Java sequential/parallel and
sibling-processor-failure suite; collected, not live-executed. Keep draft pending
PR98 and the remaining authorized Java/scientific/lifecycle evidence.
