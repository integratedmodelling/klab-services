# Contribution review: bounded experiments (F4)

## Recommendation

**30 October follow-up:** see the [current contributor review](bounded-experiments-final-20261030.md).
An oversized-duration validation bug is fixed locally with eight regression cases;
1,295 unit/integration tests pass in a separate PR100 environment. The update has
not been pushed. Actual Java functional/scientific checks still require an
authorized fixture and renewed computation allowance before approval/merge.

**Needs contract/coordination evidence.** The bounded Python runner has passing
offline lifecycle, concurrency and fresh-process checks, but no unchanged-Java
four-cell scientific acceptance or measured scientific throughput. This is the
implementing agent's source/check assessment under `KLAB_CLI_Contribution_Review.md`,
not an independent reviewer or maintainer acceptance.

## Reviewed state

- Repository: `integratedmodelling/klab-services`, `python-client`.
- Integration base: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Candidate: `f38f7d8565cf768a9a9d0ce7ba916b6a70903745` on
  `feature/python-discover-compare-explain`; no uncommitted executable changes.
- F4-specific delta follows `b45e0b8c2`; F1–F3 inherited contribution assessments
  remain applicable, with all their live evidence limitations retained.
- Scope: supplied discover/compare/explain milestone, copied under contributor
  guides. Single integration branch is used; publication was explicitly authorized
  by the user after implementation. No PR, merge, service administration or source
  changes outside Python/documentation are authorized by that instruction.

## Capability and boundaries

A scientist can submit bounded independent typed experiments sequentially or using
threads, process actual observations while each worker's connection is open, and
inspect input-ordered outcomes and retained handles. Factory-created owned clients
reuse Context/Job/Transport contracts, including the native Job polling/retrieval
path; a private cancellation callback adds local boundary checks rather than
duplicating transport. No new dependency or scientific algorithm is introduced.

The runner does not discover scientifically valid variations, assert output validity,
replay ambiguous submissions, or provide durable batch continuation. The manifest
is local inspection data, not authority to access remote work. Per-experiment timeout
does not forcibly interrupt arbitrary factory/processor code or in-flight HTTP.

## Findings, ordered by consequence

1. **Contract/coordination question — live scientific and permissions evidence.**
   `experiments.py:282–381` exercises real Context/Job operations but available
   tests use deterministic transports or a local test HTTP server. Actual unchanged
   Java executions, compatible knowledge/component identities, four genuine matrix
   cells and permission boundaries are unverified. Obtain the deployment/fixture
   prerequisites listed in `../../discover-compare-explain-status.md`, then run the
   shared scientific acceptance without changing Java or scientific semantics.
2. **Contract/coordination question — deployment readiness.** Earlier local service
   probes showed empty Resources workspaces and no accepted Reasoner generation;
   hosted connection setup failed before readiness assertions. Local authenticated
   Resources does advertise CREATE; anonymous READ-only capabilities must not be
   mistaken for the IDE user's rights. Resolve the IDE catalog/form issue and load
   compatible existing knowledge through an authorized procedure.
3. **Validation limitation — broad pytest collection.** Running bare `pytest`
   collected same-named functional/unit modules and failed with four import errors.
   The established offline command explicitly selects `tests/unit tests/integration`
   and passed. Do not report the broad invocation as passing or treat it as evidence
   that live functional tests ran.

No additional demonstrated F4 defect was identified in this bounded assessment;
this is not exhaustive proof. Delivery/integration work remains unfinished.

## Validation actually performed

Commands ran using the isolated `discover-compare-explain-312/Scripts/python.exe`
from `python-client`, Windows, Python 3.12.7. `<external>` means the approved local
OpenCode temporary directory, not a committed artifact. All executable content
tested matches candidate `f38f7d856`; documentation was committed afterward.

| Check / command | Status | Establishes / limits |
| --- | --- | --- |
| `python -m pytest -q --tb=short --cov=klab_client --cov-branch --cov-report=term --cov-report=json:<external>/discover-f4-full-coverage.json` | Failed, exit 1 | Four collection errors from duplicate functional/unit module names; no acceptance claim |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov-branch --cov-report=term --cov-report=json:<external>/discover-f4-full-coverage.json` | Passed, exit 0 | 1,287 tests; one existing Rasterio georeferencing warning; F4 module 100% lines/branches |
| `python tools/check_feature_coverage.py <external>/discover-f4-full-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py --new-module src/klab_client/inspection.py --new-module src/klab_client/evidence.py --new-module src/klab_client/experiments.py` | Passed, exit 0 | Existing unexcluded new-module and changed-code coverage gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82` | Passed, exit 0 | Established fatal-error static gate |
| `git diff --check` | Passed, exit 0 | Implementation whitespace check before commit |
| Real Java four-cell science, permissions, throughput | Blocked | Missing compatible deployment and independently verified fixture |
| Installed-wheel/interpreter matrix and executed notebook | Not run | Remaining delivery work, not implied by editable-package testing |
| Java build | Not applicable to this Python-only delta | Cannot establish running binary compatibility |

## Scientific and lifecycle evidence

`test_experiment_runner.py` covers real four-thread overlap, ordered association,
sibling preservation, shared ownership rejection, deadlines, cancellation request
versus acceptance, ambiguous submissions, cleanup failure, processing while open,
manifest bounds and native Job cancellation boundaries. Each worker owns independent
session/context/client state; local close does not imply remote cancellation.

`test_experiment_process.py` starts an actual loopback HTTP server, creates four
results in one isolated process, restores their original handles in another, checks
values and identities and asserts only four submissions occurred. Its zero-valued
test payload is protocol evidence, not an independent scientific reference or live
Java persistence/authorization evidence. Remote disposal is opt-in and only follows
a known terminal outcome; timeout/unknown outcomes retain work.

## Contributor coordination

No Java, worldview or component changes were made. No new contributor assignment,
maintainer agreement or current overlap claim is inferred. Changes to the separate
IDE need its maintainer's diagnosis; broadening permissions is not supported by the
observed local-authenticated capability result.

## Smallest next actions

Resolve the owned deployment/knowledge/fixture prerequisites, finish the shared
workflow and delivery harness, and run live acceptance and honest throughput.
Do not rename offline overlap evidence as scientific acceptance.

## Suggested PR description

Adds bounded independent Python experiments over existing Context/Job APIs with
explicit ownership, input-ordered outcomes, local cancellation, opt-in terminal
cleanup and credential-free inspection manifests. Includes deterministic lifecycle
tests, actual thread overlap and loopback/fresh-process restoration checks. Offline
suite: 1,287 tests plus existing coverage and lint gates. This checkpoint includes
F1–F3 but does not complete unchanged-Java scientific acceptance or final delivery.
