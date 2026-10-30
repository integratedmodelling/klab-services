# Python milestone: upstream integration, 30 October 2026

## Recommendation

**Needs contract/coordination evidence; keep the feature PRs draft.** The old
compiler failure is resolved locally by integrating existing upstream Java, not
by adding a default switch case or changing parser dependencies. Full packaging
and focused Java regressions pass. All five Python feature suites pass their
offline tests and established coverage/lint gates. Live acceptance remains separate.

## Reviewed state and integration

- Upstream: `develop` at `f9b8879ddf9d2b6544810912a9e11bb86118afce`.
- Milestone base before integration: `6bb7a8224d78ad864d16b7e2884cb7677ffdcf5c`.
- Initial upstream merge: `f2a0fe61cc3daf5d0f447d73871408cc7287f699` on
  `feature/python-discover-compare-explain-integration`.
- Retained Java regression fixtures updated in `7245e6ba0` and `e357c4521`.
- Ordinary merge commits propagate the base through #101 -> #99 -> #98 -> #100
  -> #102. No rebasing, force-pushing, PR retargeting, approval or PR merge.
- Upstream commit `b174ee5e6` (22 October) contains the missing parser
  `RELATIONSHIP` handling. The adapter moved to core services on 13 October.
  All five feature branches previously contained the same older Resources adapter.

## Conflict decisions

- Use upstream's existing Job publication, scope/request identity, context
  reconnection and unit-construction implementations. UnitImpl now renders its
  portable definition directly; no old builder workaround is needed.
- Keep private/explicit/group exclusion tests. Update their denial expectations
  from the older null return to upstream's authorization exception; denial is not
  weakened. Initialize newly required collaborators in old mock fixtures.
- Upstream treats null Job output as ABORTED with serialized null retrievable;
  retain that authoritative contract and both old/new lifecycle regressions.
- Preserve existing branch storage-registration safeguards and ACL-filtered
  context enumeration, including public contexts outside a caller's federation.
  The enumeration regression executes real Cypher using an isolated Neo4j harness;
  returned descriptors are still filtered through persisted ownership/ACL checks.
- Resolve documentation rename conflicts at upstream's `docs/workflows/` paths;
  retain valid `tools/review/run-review-tests.ps1` reproduction references.
- Combine functional-suite registrations and CI coverage inventories. Keep both
  resource-inspector tooling and milestone tooling covered; remove no check.
- Preserve published #102 documentation commit `88c95c0cf` through a merge.

## Additional demonstrated Python fix

The fresh #102 editable install exposed `source_identity()` assuming every
distribution has `METADATA`. An editable/source distribution can instead expose
`PKG-INFO`, causing AttributeError. #102 commit `7ae1ad278` reads either standard
metadata format, takes the version from the same selected distribution and fails
explicitly if neither exists. Regression tests cover both formats and missing
metadata; no fabricated evidence hash or weakened identity comparison.

## Validation actually performed

Windows, Temurin JDK21 and Python3.12.7. Java test forks use an external isolated
user home, not the running IDE's configuration. Python imports were explicitly
bound to each tested worktree. #100 and #102 use their own editable environments
for isolated-child tests. No live observation Jobs or computations were submitted.

| Check | Result | Scope / limits |
| --- | --- | --- |
| `mvnw.cmd -B -U clean package -DskipTests` at f2a0fe61c | Passed, exit0, all18 modules | Full packaging/test compilation, not Java test execution |
| Focused reactor tests after fixture updates | Passed, exit0, 72 tests, zero skipped | Jobs, scopes, ACLs, error mapping, units, storage registration, relationship translation and in-process Reasoner; not live two-user acceptance |
| #101 Python unit/integration suite | Passed, exit0, 1,167 tests | Integrated F1 candidate 65006c839 |
| #99 Python unit/integration suite | Passed, exit0, 1,354 tests | Integrated F2 candidate be89bad17 |
| #98 Python unit/integration suite | Passed, exit0, 1,415 tests | Integrated F3 candidate e4f021f23 |
| #100 Python unit/integration suite | Passed, exit0, 1,456 tests | Integrated F4 candidate d366c4c35 |
| #102 Python unit/integration suite after metadata fix | Passed, exit0, 1,564 tests | Exact 7ae1ad278 code content |
| Each branch's established coverage command and Ruff E9,F63,F7,F82 | Passed, exit0 | Full new-module line/branch and changed-code gates retained |
| Newly integrated live F1/F2/F3/F4/scientific suites | Not run | Prepared fixture/independent expectations or renewed computation authorization required |
| GitHub checks for updated heads | Pending publication/CI | Do not reuse older branch checks as current-head evidence |

Python command in each `python-client/`:
`python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov-branch --cov-report=json:<external-report>`.
For #99/#98/#100, append the 45-test `test_resource_inspection_workflow.py` coverage
run with `--cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance
--cov-branch --cov-append`, then run the exact coverage gate from that branch's
workflow. For #102, the full suite additionally measures
`workflows.discover_compare_explain`, `workflows.milestone`,
`build_milestone_deliverable`, `run_milestone_acceptance`,
`run_milestone_throughput`, `restore_milestone_results`,
`workflows.resource_inspection`, and `run_resource_inspector_acceptance`.

Focused Java command:

```powershell
./mvnw.cmd -B -pl klab.core.services,klab.services.reasoner,klab.services.runtime -am test '-Dtest=JobManagerTest,TokenAuthorizationFilterTest,IdentityHeaderValidationTest,ManagedScopeAccessTest,FederationSessionIdentityTest,RuntimeContextAuthorizationTest,PersistedContextAccessTest,FederatedContextPermissionsTest,StorageRegistrationTest,SemanticsBuilderUnitTest,RelationshipAxesTest,RelationshipAxesReasonerTest,DomainErrorMappingTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=<isolated-test-home> -Dklab.test.core.directory=<checkout>/klab.services.reasoner/src/main/resources/knowledge'
```

Earlier focused attempts failed because old mocks lacked newly required data/
collaborators, a real-worldview test needed its documented core-directory property,
and older permission tests expected null instead of an authorization exception.
Those failed runs are not counted as passes. Final commands above passed after
correction. Inherited upstream whitespace in historical logs was not rewritten;
locally edited conflict/test paths pass whitespace checks.

## Remaining PR acceptance

- #101: actual configured authority selection/documentation/resolution and
  independently verified hierarchy/predicates.
- #99: prior live F2 success remains historical; no new scientific allowance was
  spent to revalidate the updated Java integration. Current-head CI/review remains.
- #98: retained shared/private observations and two valid authorized identities.
- #100: actual sequential/parallel scientific execution and failure/lifecycle checks.
- #102: independent oracle, two variations by two resolutions, exact deployment
  attestation, all acceptance cases, executed notebook/HTML and throughput.
- #90 is a separate older federation draft, not modified in this pass. Its reported
  successful checks are historical (11 October), not proof for this integration.

No functionality or outstanding scientific requirement was dropped. The running
October30 installed Java stack was left untouched by the source integration.
Maintainer/independent review and current-head CI remain necessary before merging.
