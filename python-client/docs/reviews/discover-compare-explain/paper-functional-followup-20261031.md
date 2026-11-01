# Contribution review: paper research and actual hosted runner findings

## Recommendation

**Ready for maintainer review as a documentation/evidence batch.** Scientific
delivery still **needs contract/coordination evidence**. Keep PR #102 draft and
unmerged. This pass changes reports and the explicitly requested planning guide,
not the SDK, test bodies, Java, components, ontologies or scientific algorithms.

## Reviewed state

- Checkout: `feature/python-milestone-delivery`; starting/published candidate
  `cf7a0681bbe94ca81a9a7c218ce05475db52b5ae`.
- Integration base remains `feature/python-experiment-runner` at
  `0898fbd807355d81567f524fde04d782ad015142`; no retarget/rebase/merge.
- Included local changes: current compatibility follow-up, paper research,
  sanitized runner/capability reports, and the user's modified planning guide.
- User replaced final email with the independently hosted portfolio progress
  page. The separate portfolio repository must validate/publish its own changes;
  this review is not a claim that its deployment is already complete.

## Capability and boundaries

The reports now distinguish four exact hosted compatibility passes from a
subsequent failing real runner test. The three-paper research batch provides
concrete future tests and primary sources without mislabeling synthetic terrain
as a paper replication or adding placeholder scientific passes.

## Findings, ordered by consequence

1. **Required live acceptance failed:**
   `tests/functional/test_bounded_experiments.py:88`, first sequential case.
   Four experiments yielded three numerical wait timeouts and one context Job
   failure, not two successful results each. Seven Job handles were recorded.
   Root cause remains unverified. The smallest next step is an authorized,
   metadata/status-focused diagnosis with compatible fixture and explicit time
   limits—not changing the test to pass or automatically recomputing.
2. **Contract/prerequisite gap:** Runtime capability snapshot advertises only
   `component.jar` export schemata. Resources `geospatial.io` does not establish
   Runtime observation export support. Full exported numerical matrix/notebook
   acceptance needs an advertised compatible exporter and independent references.
3. **Scientific fixture gap:** public paper sources do not establish deployed
   trained resources, pinned scientific inputs or the original numerical oracle.
   Supply those before registering executable paper-replication cases. A 2014
   ARIES precursor is not asserted to be a k.LAB v1 experiment.

## Validation actually performed

Python 3.12.7 / Windows; existing hosted HTTPS deployment and matching ENGINE
certificate. Exact private paths remain outside the repository.

```powershell
$env:PYTHONPATH="$PWD/src"
$env:KLAB_EXPERIMENT_MODEL_URN='staging.vxii.basic.elevation-quantifier'
$env:PYTEST_ADDOPTS='--basetemp=<fresh-external-runner-work> --maxfail=1'
python tools/run_functional_acceptance.py --suite connected-bounded-experiments --allow-owned-work --profile examples/hosted-deployment.json --certificate <private-ENGINE-certificate> --report <fresh-external-runner.xml>
```

| Check | Status | Evidence / limit |
| --- | --- | --- |
| Real hosted runner, candidate `cf7a0681bb` | **Failed**, exit 1 | 3 collected; 1 executed and failed in 378.99s; 2 not run after maxfail; no automatic retry |
| Explicit authorization ceiling | **Observed within bound** | 20 Jobs/200 cells permitted; seven Job handles recorded; zero numerical processor outputs verified |
| Final test-owned cleanup | **Passed** | All six retained-context Jobs reported FINISHED at final check; three context and three session releases returned true; fourth experiment already released by runner |
| Late numerical result verification | **Not run** | FINISHED did not retroactively pass numerical assertions; no resubmission or late cell fetch |
| Capability/model inventory | **Completed, not science acceptance** | `initialize=False`, 55 rows/54 distinct model URNs, all visible namespaces staging; no new owned work |
| Primary literature/source checks | **Completed, initial batch** | Publisher full text for 2014/2019/2023; Zenodo metadata and Sicily v0.1 source prerequisite inspected |
| Scientific paper reproduction | **Blocked** | Missing trained resources/input versions/independent references; no algorithm port or source import |
| JSON parse and `git diff --check` | **Passed** | Reports parse; whitespace check exit 0 |
| SDK offline tests / package builds this pass | **Not rerun** | Documentation/evidence-only changes; earlier 1,599-test/CI result remains attached to `cf7a0681bb`, not relabeled a fresh run |
| Full milestone / throughput / numerical m-to-mm conversion | **Not run** | Separate prerequisites/authorization required |

The initial external cleanup helper omitted the required `user_id` argument for
`ContextScopeImpl`; corrected before releases. The final cleanup report confirms
the scoped releases. No unrelated sessions, services, terminals or restored
configuration were changed. No background Java service was created.

## Acceptance and next handoff

Publish the bounded failure and the research limitations, not a green milestone.
The portfolio may replay sanitized saved evidence and preserve historical videos
with explicit fixture/date labels. Its replay notebooks/videos must not be called
new live SDK executions. Two-user permission acceptance and scientific matrix,
export, original-result recovery, notebook and throughput remain outstanding.
