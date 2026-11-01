# Contribution review: milestone acceptance, notebook and delivery

## Recommendation

**Needs contract/coordination evidence.** The acceptance, restoration, notebook,
throughput and blocked-delivery tooling is implemented and locally validated.
Actual unchanged-Java scientific execution and deployment attestation remain
missing, so this is not an accepted scientific deliverable. Assessment follows
`KLAB_CLI_Contribution_Review.md`; this is the implementing agent's read-only
assessment, not independent review or maintainer acceptance.

## Reviewed state

- Repository: `integratedmodelling/klab-services`; isolated milestone worktree.
- Candidate: `bec1c1708551661ca7fca1b94ab964bb044dbdbf`, clean tree after commit.
- Increment base: `b8957532a`; full milestone base:
  `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Feature PR base for delivery: `feature/python-experiment-runner` at `7fae2ea97`;
  includes the separately assessed shared-workflow increment.
- Integration target: final `feature/python-client`. Refreshed remote remains at
  the milestone base; no new Java code was silently merged.
- Scope: original milestone sections 7–11; separate PR per feature now explicitly
  required by the user. Four core PRs are draft; delivery receives its own PR.
- Environment: isolated Windows Python 3.12.7; separate installed-wheel Python 3.11
  and core-only Python 3.12. No shared administration or computation performed.

## Capability and boundaries

Before this increment, real-Java acceptance/notebook/delivery orchestration was
missing. Now eight named suites use the existing runner and shared scientific
workflow; an isolated worker restores originals without resubmission; notebook 08
executes fresh real workflows and exports HTML; correctness-gated throughput runs
bounded workers 1/4 and larger grids; the builder checks identities/artifacts and
fails closed. Optional notebook libraries do not enter core SDK dependencies.

The fixture loader requires independently specified cases, oracles, authority
identity/relationships and declared unchanged-stack provenance. Declarations are
not runtime attestation: the acceptance adapter records `deployment_verified=false`,
and the builder refuses ready status in that condition. No override switch exists.
The tooling cannot prove authenticity of manually fabricated reports; its scope
is consistency of recorded trusted runs, not a cryptographic evidence service.

## Findings, ordered by consequence

1. **Contract/coordination evidence — deployment attestation and scientific matrix.**
   `tools/workflows/milestone.py:load_fixture`,
   `tools/run_milestone_acceptance.py`, `tools/build_milestone_deliverable.py:build`:
   exact running Java/component/worldview identities have not been verified.
   A four-cell independently justified fixture and a valid second identity are
   still required. The adapter's false verification flag is deliberate; do not
   manually flip it. Resolve the actual attestation interface/evidence before
   treating an eventual passing workflow as a PR-ready scientific deliverable.
2. **Unavailable environment — Python 3.13/Linux.** Local supported interpreters
   include 3.11 and 3.12, not 3.13; Windows checks cannot certify the Linux matrix.
   CI retains 3.11/3.12/3.13 and now includes optional notebook delivery checks.
   Pending CI is Not run, not silently passed.
3. **Demonstrated integration defect fixed before candidate.** The original recorder
   and its regression test globbed all numbered notebooks; adding notebook 08 made
   the existing seven-notebook check fail. Both now explicitly select 01–07 while
   the milestone runner validates 08 under its own explicit fixture contract.
   The original seven checks were retained, not weakened or deleted.
4. **Source-inspection correction before candidate.** The larger-grid throughput
   gate initially compared cell counts/model/deployment only. It now also requires
   matching experiment/variation/resolution names, semantic requests/options,
   source units, precision, CRS and extent, preventing comparison of unrelated
   workloads under a grid-size label. Discovery time is reported separately.

No further demonstrated blocking code defect was found in this bounded assessment.
Unperformed scientific notebook/throughput runs remain required, not optional polish.

## Validation actually performed

Executable content of candidate tested before commit; only documentation and Git
metadata changed afterward. `<external>` is the approved local OpenCode temp root.

| Check / exact command | Status | Establishes / limits |
| --- | --- | --- |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov=workflows.discover_compare_explain --cov=workflows.milestone --cov=build_milestone_deliverable --cov=run_milestone_acceptance --cov=run_milestone_throughput --cov=restore_milestone_results --cov-branch --cov-report=term --cov-report=json:<external>/discover-delivery-final-coverage.json` | Passed, exit 0; 1,392 tests, Python 3.12.7 | New workflow/delivery modules 100% unexcluded lines/branches; one existing Rasterio warning |
| `python -m pytest tests/unit tests/integration -q --tb=short` using `<external>/discover-delivery-wheel-311/Scripts/python.exe` | Passed, exit 0; 1,392 tests | Installed SDK wheel on Python 3.11; repository tests/tools, no editable SDK fallback |
| `python tools/check_feature_coverage.py <external>/discover-delivery-final-coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30` with the complete new-module arguments in `.github/workflows/python-client.yml` | Passed, exit 0 | Existing inherited and new module/changed-code gates; no threshold reduction |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82` | Passed, exit 0 | Established fatal-error static gate |
| `python -m pytest tests/functional/test_discover_compare_explain.py --collect-only -q -o addopts=` | Passed, exit 0; 8 collected | All named tests exist; not execution or live acceptance |
| `python -m build --outdir <external>/discover-delivery-wheel-20261028` | Passed, exit 0 | Wheel and sdist build; source distribution before final review-only documentation |
| Core-only installed wheel, `python -I -c ...` | Passed, exit 0 | All four APIs import; scientific/notebook modules absent by `find_spec` and `sys.modules`; Handle round trip succeeds |
| Real local kernel and HTML integration tests | Passed in both full suites | Exact Python executable used; synthetic smoke code only, not scientific notebook output |
| Builder CLI with absent acceptance/throughput files and fresh external output | Passed negative gate: exit 1 and `delivery.json` status `blocked` | Missing evidence cannot produce ready status |
| Full suite before recorder fix | Failed, exit 1; 1 failed/1,391 passed | Real regression caught and corrected before final passing runs |
| Real scientific suite/notebook/throughput | Blocked | No verified compatible fixture/build/component tuple or second identity; no shared compute pilot performed |
| Python 3.13/Linux matrix | Not run locally | CI configuration is not CI execution evidence |

SDK wheel SHA-256:
`ec9d891eeb840874f00f96e821760bd9a1e6059cda97758b7f8e047015621438`.
Private install logs and bulky coverage/kernel outputs remain external.

## Scientific and lifecycle evidence

Tests reject stale candidate/package/fixture identities, missing/failed/skipped
required cases, replacement restoration identities, notebook run reuse/unexecuted
cells, artifact hash changes, unsafe paths and unexpected state files. Synthetic
validator inputs are explicitly labeled and never committed as live scientific
results. Real local raster readers/kernel execution and actual subprocess guards
add evidence above mocks, but do not replace unchanged-Java computation.

Native exports remain bounded/atomic; every measured result uses the same full
oracle, analysis and recorded-effect checks. Cancellation/ambiguous mutation remains
the existing F4/Job contract. Partial failure is induced before one local worker
can submit; remote failure/timeout controls are not invented. Permissions require
distinct identities and authenticated positive controls. Remote work is explicitly
retained for recovery, not described as disposed. Throughput withholds scientific
rates on failure, retains attempts, reports unrun batches separately, uses wall
clock and nearest-rank p95, samples parent Python RSS, and marks remote Java memory
unavailable. It does not claim cold Java/cache conditions.

## Contributor coordination

No Java/component/worldview/Maven source changed. This remains Python remote-client
tooling, not a worker SDK or server administration product. Separate reviewed PRs
are documented in `../../discover-compare-explain-pr-stack.md`; none is merged.
No maintainer assignments or scientific acceptance are inferred.

## Smallest next actions

Establish the compatible unchanged-stack fixture, actual deployment attestation
and second identity; run all eight named cases at one identified candidate;
execute the real scientific notebook, measure the bounded throughput protocol,
and rerun available CI/platform checks. Preserve all failure/partial states.

## Suggested PR description

Adds the discover/compare/explain delivery journey around F1–F4: strict fixture and
candidate identities, eight named suites, isolated original-result restoration,
real notebook/HTML executor, correctness-gated throughput and fail-closed packaging.
1,392 offline tests pass on Python 3.12 and installed-wheel 3.11; original coverage,
lint/build and core-only gates pass. Draft: missing live scientific/deployment/
permission evidence; no scientific throughput or completed delivery claim.
