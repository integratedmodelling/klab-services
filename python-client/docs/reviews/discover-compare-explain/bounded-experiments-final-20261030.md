# Contribution review: PR100 bounded experiment runner

## Recommendation

**Needs contract/coordination evidence. Do not approve or merge yet.**

The review found and fixed a Python numeric-validation defect, with regressions
demonstrating the failure before the fix. The local candidate passes 1,295 unit
and integration tests, including real subprocess restoration and thread overlap.
That is not a live Java functional pass. Four-experiment sequential/parallel,
scientific fixture and permission/lifecycle acceptance remain unverified. No push
was made during this pass; updates are being batched to avoid repetitive CI emails.

## Reviewed state

- Repository/worktree: `integratedmodelling/klab-services`, `pr100-experiment-runner`.
- Actual target: `feature/python-execution-evidence` (#98).
- PR-reported base/merge-base: `b45e0b8c28c588881a708d338d3ab982a02522a6`.
- Initial clean head: `7fae2ea9722c7465059c57110444799c839265ae`.
- Fixed local candidate: `030aad10764ef533e4aa8d8e1a621b0dc04f9679`.
- Included `experiments.py`, Job local-cancellation extension, error type, public
  exports, manifest handling, runner tests, subprocess integration and feature docs.
  F1/F2/F3 inherited limitations remain distinct from F4-specific findings.
- Used `KLAB_CLI_Contribution_Review.md`, original milestone and existing runner
  review. Initial inspection/reproduction was read-only; fixes followed it.
- Current requirements: simple readable code, passing unit and functional tests,
  verified interactions with unchanged Java before merge. No Java/POM/dependency
  changes or additional scientific computations are authorized in this pass.

## Capability and boundaries

Scientists can run named independent typed experiments, sequentially by default
or with an explicit thread bound. Each worker owns its client/session/context;
result and Job handles survive partial failure. Local wait cancellation, remote
cancellation acceptance, terminal outcome and cleanup stay separate. A local batch
manifest does not imply durable server continuation, ownership or replay.

The runner reuses existing Context/Job/Transport contracts. It does not discover
scientific variations, invent successful output, retry ambiguous submissions or
modify Java to create missing functionality.

## Findings, ordered by consequence

1. **Acceptance/contract gap: actual Java functional execution is still required.**
   The existing loopback integration test is a Python HTTP fixture, not a Java
   service. No F4 sequential/parallel live scientific matrix was run here. A real
   prepared fixture and explicit execution allowance are required before this PR
   can satisfy the user's unit/functional/Java standard. The latest five scientific
   attempts are exhausted; do not reuse F2/F3 successes as F4 proof.
2. **Demonstrated Python validation defect, fixed locally.** At the initial head,
   `ExperimentRunner.run(timeout=10**400)` raises `OverflowError` from
   `math.isfinite` instead of the documented `InvalidRequestError`. The same leak
   occurs while loading oversized integer batch/outcome/cleanup durations from
   otherwise valid bounded JSON manifests. Eight new regression cases failed
   before the fix. The candidate catches conversion overflow at the input boundary
   and rejects before creating a client; it does not clamp or invent a timeout.
3. **Readability improvement, fixed locally.** The generic worker error handler
   selected among five states with nested conditional expressions. It now uses
   direct `if`/`elif` branches. No extra abstraction or different state semantics
   were introduced; the module retains full line/branch coverage.
4. **Test-environment issue, not a production defect.** The first subprocess test
   invocation used the PR99 editable environment. Its isolated `python -I` child
   correctly ignored PYTHONPATH and failed to import F4 exports from that older
   install. Created a separate standard venv and installed this worktree there;
   did not weaken the subprocess isolation test. The full suite then passed.
5. **Integration/CI coordination remains.** PR100's base predates the newest F2/F3
   follow-ups. Integrate deliberately, preserve feature/history boundaries, and
   rerun checks on the actual integrated candidate. Shared Java CI failure remains
   separate from client runtime compatibility. No bypass, self-approval, retarget,
   merge or force-push was performed.

No additional demonstrated production defect was found in the reviewed bounded
runner path. This is not an exhaustive guarantee or proof of live server isolation.

## Validation actually performed

Final environment: Windows Python 3.12.7, isolated
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr100-review-312/Scripts/python.exe`,
editable installation of this exact worktree. Commands ran from `python-client/`.
Coverage files were stored outside the worktree.

| Check / command | Revision / status | Meaning and limits |
| --- | --- | --- |
| New timeout and manifest regressions, `pytest tests/unit/test_experiment_runner.py -k 'unrepresentable_timeout or large_integer_durations' -q --tb=short` | Initial source plus tests: Failed, exit 1; all 8 fail with OverflowError | Reproduces the actual Python defect, no Java work |
| `python -m venv <external>/pr100-review-312`; that interpreter's `python -m pip install -e '.[dev,local,analysis]'` | Passed, exit 0 each | Correct isolated source installation; no shared environment replacement |
| `python -I -c "import klab_client; print(klab_client.__file__)"` | Passed, exit 0 | Resolves this PR100 worktree even with isolation |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov-branch --cov-report=json:<external>/pr100-review-20261030-full-coverage.json` | Exact 030aad107 content: Passed, exit 0; 1,295 tests | Includes independent worker subprocess restoration; one existing Rasterio warning |
| `python tools/check_feature_coverage.py <external>/pr100-review-20261030-full-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py --new-module src/klab_client/inspection.py --new-module src/klab_client/evidence.py --new-module src/klab_client/experiments.py` | Same content: Passed, exit 0 | Existing new-module 100% line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same content: Passed, exit 0 each | Existing lint and whitespace gates |
| Actual Java runner functional/scientific/permissions checks | Blocked / Not run | No prepared scientific fixture or renewed computation allowance |
| Remote candidate CI | Not run | Candidate saved locally, intentionally not pushed yet |

The initial wrong-environment focused invocation had 40 passing unit tests and
one failing subprocess import; it is not counted as a successful integration run.

## Scientific and lifecycle evidence

Unit tests cover ownership, real thread overlap, ordering, failed siblings,
ambiguous submissions, cancellation/deadline boundaries, cleanup and manifest
validation. A separate actual loopback HTTP server exercises create/restore in
fresh Python processes and verifies no duplicate submission on restoration.
Those are protocol/lifecycle checks, not actual Java scientific values or live
permission enforcement. No independent scientific oracle, unit conversion,
two-resolution comparison or real throughput result was established in this pass.

No scientific objects were created, services changed, credentials published or
other-user operations performed. The original local Java deployment remains as
restored after PR99's final functional run.

## Contributor coordination

Python-only changes over existing server interfaces. No Java, component, worldview,
ontology, algorithm, Maven or CI workflow modification. The user requested local
work and batched publication after excessive failure emails; local commits are
preserved without generating more CI runs. This is an implementing-agent review,
not independent maintainer approval.

## Smallest next actions

1. Prepare an authorized existing-Java scientific fixture and an explicit bounded
   allowance for the sequential/parallel/failure/lifecycle checks. Do not run them
   under the already exhausted 20-cell pilot allowance.
2. Add/run the appropriate shipped F4 functional acceptance against that fixture,
   fix resulting Python shortcomings, and record actual Java interactions.
3. Confirm integration/base handling and required review/checks. Only then consider
   approval/merge; publish a complete tested update rather than each small edit.

## Suggested PR description

Bounded independent Python experiment orchestration over existing Java interfaces.
Fixes oversized duration handling and makes error-state code directly readable.
1,295 unit/integration tests plus existing coverage/lint gates pass in an isolated
PR100 installation. Actual Java sequential/parallel/scientific/permission acceptance
remains blocked. Keep draft; no approval or merge yet.
