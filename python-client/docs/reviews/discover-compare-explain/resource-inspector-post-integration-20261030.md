# Contribution review: PR99 after upstream integration

## Dependent merge review after PR101 merged

**Current recommendation: bounded F2 passes contributor review; merge after exact-head
CI succeeds.** The user explicitly authorized the upstream-first dependent merge.
PR101 merged at `58d2a0ca4ce8ff85235272c4221b014f1a6c3cae` into
`feature/python-discover-compare-explain-integration`; PR99 now targets that branch.
It no longer targets draft101's branch. No PR102 work is authorized in this pass.

The integrated executable candidate is `617e36ed8fca91c5ab9baae4bb654e99e3b9b042`.
The only added executable change versus the previously reviewed99 candidate is
PR101's independently reviewed/live-tested semantic-identity rejection. F2's
production source/workflow and all Java source remain unchanged. Read-only diff
inspection found no new blocking/important defect or untested conflict resolution.
The limitations and contributor coordination below remain explicit.

Revalidation on this exact integrated executable content:

- Passed exit0:1,362 unit/integration tests, using the same full pytest/coverage
  command below with external `pr99-dependent-merge-review-coverage.json`.
- Passed exit0: exact branch coverage gate, all new-module arguments retained;
  Ruff E9,F63,F7,F82 and whitespace checks.
- Passed exit0: `python <external>/pr99-dependent-live-20261030.py prepare`, then
  `python <external>/pr99-dependent-live-20261030.py test`. The actual shipped
  functional body passed once in17.26s on October30 binaries. This was a separately
  authorized new test, not reuse of the older live result.
- One owned session/context, Jobs4/5, observation166, resolution Activity170 and
  numerical Activity171;20/20 finite cells in m; inspected model found in accepted
  resolution. Source SHA256 matches the current client after restoration.
- Both releases confirmed;16 configuration/catalog files byte-exact; all original
  component/plugin inventories preserved; zero contexts/projects; five listener/PID
  matches and no visible service consoles. No source or scientific algorithm edit.

See [fresh integrated evidence](../../results/resource-inspector-dependent-merge-20261030.json).
Final publication changes only evidence/docs relative to that tested content.
Confirm current head/base and all GitHub/Jenkins checks before the authorized merge;
no approving self-review, forced history change or check bypass.

The remainder is the earlier detailed review, retained as historical context.

## Recommendation

**Ready for maintainer review of bounded F2.** The demonstrated credential-display
defect is fixed. 1,357 unit/integration tests, established coverage/lint checks and
the actual shipped functional test pass on the same executable source. The new
October30 Java stack returned 20 finite cells in metres and recorded the inspected
model in this observation's accepted resolution. No remaining demonstrated F2
blocking defect was found. This is implementing-agent evidence, not an independent
review or an approving GitHub self-review. Confirm current-head CI and the actual
stacked destination before merging; do not bypass checks.

## Reviewed state

- Repository/worktree: `integratedmodelling/klab-services`, `pr99-resource-inspector`.
- Actual target: `feature/python-semantic-explorer`, draft PR101, not develop/main.
- Base/merge-base: `e4dc73aa433e62eabc833fe87cc3e32d99360351`.
- Initial clean published candidate: `2b922f1a236cb852a930c0e2171a510c35c33807`.
- Fixed executable candidate: `144bcb7fd9e3b4962933722d409c6faf0dc10848`.
- Reviewed complete F2 diff: ResourceInspector and Resources method keyword
  additions, bounded workflow, tests, diagnostics/docs and inherited upstream
  integration. The reviewed Java baseline includes upstream relationship handling;
  this pass adds no Java, parser, dependency or scientific source change.
- Read root collaboration instructions, contributor CLI review guide and original
  milestone. Evidence-based read-only inspection/reproduction preceded the fix.
- User explicitly authorized one new two-observation-Job/one-session/context/20-cell
  test and temporary fixture preparation with restoration. No automatic retry.

## Capability and boundaries

Inspect an explicit model/resource at the configured Resources service, retaining
origin, advertised observables/dependencies and available provider metadata without
claiming execution, exhaustive dependencies or Resolver selection. The separate
bounded pilot verifies an inspected model's association with actual computed output.
No global search, model forcing, remote descriptor following or new Java contract.

## Findings, ordered by consequence

1. **Demonstrated credential-display defect, fixed.** `inspection.py` formerly
   returned unredacted asset/request/reference URNs and origin service IDs, although
   `raw` metadata was redacted. A service echoing a configured credential in those
   fields exposed it through typed views, dataclass repr or summaries. Reproduced
   with a fake token; two new tests failed before the fix. Lines119–126 produce a
   redacted display origin while keeping the original stamp for identity checks;
   lines160/212/223 use redacted identifiers in returned views. A third regression
   proves two different actual service IDs still fail drift checks even when their
   displayed redactions would match. Normal canonical identities are unchanged.
2. **Former compiler/CI blocker is resolved by upstream integration.** At published
   head2b922f1a2, GitHub Java run41137904899, all Python jobs and Jenkins succeeded.
   The fixed source needs its own current-head checks after publication. Do not
   carry the old red-CI claim forward or treat the older green run as current-head
   validation of a new commit.
3. **Stacked merge destination needs a deliberate decision.** Merging99 into its
   existing target folds F2 into draft101's branch; it does not merge into main or
   complete101's live acceptance. Preserve history and feature boundaries, and do
   not silently retarget/rebase. GitHub identityDobbes is also the author: no
   approving self-review or reviewer impersonation.

No additional demonstrated important/blocking F2 defect was found. Passing tests
do not prove exhaustive correctness or the full scientific milestone.

## Validation actually performed

Windows, Python3.12.7 in `pr99-inspector-312`, explicit PYTHONPATH to this worktree.
Commands ran in `python-client/` except the external preparation/functional helper.
Raw coverage, JUnit, setup backups and sanitized logs are retained outside Git.

| Command/check | Tested content | Result / limits |
| --- | --- | --- |
| `python -m pytest tests/unit/test_resource_inspector.py -k 'echoed_credentials or redacted_origin' -q --tb=short` | Original2b922f1a2 source plus regressions | Failed exit1: two leaks reproduced; raw-identity drift guard passed |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance --cov-branch --cov-report=json:<external>/pr99-post-integration-review-coverage.json` | Exact144bcb7fd code content | Passed exit0:1,357 tests; one existing Rasterio warning |
| Exact `check_feature_coverage.py` command from `.github/workflows/python-client.yml`, all existing new-module arguments, external coverage report | Same content | Passed exit0: existing100% new-module line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same content | Passed exit0 each |
| `javap` inventory of October30 Runtime's `SemanticType`, compared with SDK | New installed Java API | Passed:81 exact names on each side; distinct from parser enum |
| `python <external>/pr99-oct30-recheck.py prepare` | Selected October30 binaries, unmodified pinned projects/published archives | Passed exit0; administrative imports/readiness only, no observations |
| `python <external>/pr99-oct30-recheck.py test` | Exact144bcb7fd source; actual shipped functional pytest body | Passed exit0:1 actual test in20.13s, not collection or mocks |
| Functional invocation: `pytest tests/functional/test_resource_inspection.py -q --tb=short -o addopts= -m 'live and functional' --junitxml=<external>/functional.xml -p no:cacheprovider` | Same content | Passed; one JUnit-family warning, no skipped/failed tests |
| Post-test restoration and source-hash verification | Same code and selected build | Passed:16 byte-exact config/catalog files; original component/plugins restored; zero contexts/projects; five verified listeners; no visible service consoles |

The first old-harness preparation check rejected the new Reasoner's four existing
authority components before any service/configuration change. The external helper
was updated to preserve them, then preparation succeeded. No scientific test ran
during that initial check. There was only one actual functional invocation.

## Scientific and lifecycle evidence

See [sanitized current result](../../results/resource-inspector-oct30-stack-20261030.json).
All five service processes used installed build202610301844; staging9d20c1c236e5a033487ee47b34cd910c3ebbbe51
and IMOD1328000fff63a1951465b1f7b130c0af15264c95 were clean and unchanged.
Published generator/geospatial/spatial archive hashes were verified before use.

One owned session/context, Jobs4/5, observation148, accepted resolution Activity152
and successful numerical Activity153. The accepted graph names
`staging.vxii.basic.elevation-quantifier`. Grid shape5x4, EPSG:3857 and exact bounds
were verified on returned observations, then20/20 finite cells read in m.
Context/session releases were confirmed. Installed-source SHA256
`6c50d6c8bf74141fca8880dbbabcea63f42865fbc1d073e8049794854a6a5788`
matches the reviewed code after restoration.

Only native loopback-owner profile/Hub bootstrap was substituted in the external
harness. Java HTTP responses, typed requests, results, graph associations and test
assertions were real and unmocked. Hosted Hub bootstrap, exact source/binary
attestation, independent terrain accuracy and distinct-user permission proof are
not claimed. Those are not silently substituted for the separate milestone/F3 gates.

## Contributor coordination and next action

Python-only security fix over existing contracts; no semantic/scientific algorithm
edit. Upstream Java package and72 focused regression tests already pass on the
shared baseline. PR98's second-user fixture and the full scientific matrix remain
separate outstanding work, not backwards dependencies of99.

Publish the reviewed fix/report together, verify exact-head CI, and obtain the
user/maintainer's decision on the current stacked merge destination. No further
computation is authorized or needed to repeat this successful bounded check.

## Suggested PR description

Bounded resource/model inspection with explicit origin and partial metadata.
Fix credential echoes in typed/display identity fields without weakening original
identity checks. 1,357 offline tests, coverage/lint and actual October30 Java
functional/model-association check pass. Synthetic terrain and partial provenance
limits remain explicit; full milestone/F3 acceptance is separate.
