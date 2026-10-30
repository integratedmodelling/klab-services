# Contribution review: PR99 bounded resource/model inspector

## Recommendation

**Ready for maintainer review of the bounded Python feature; not merge approval.**
The readability follow-up, unit/integration checks, actual shipped functional test
and numerical/model-association checks against unchanged Java passed on the same
executable source. No remaining demonstrated blocking Python defect was found.
The shared Java CI failure, stacked destination and outstanding maintainer decision
must be handled explicitly before merging; they are not a requirement to extend
Java for the client. Do not bypass checks or treat this implementing-agent report
as independent approval or full scientific milestone acceptance.

## Reviewed state

- Repository: `integratedmodelling/klab-services`; isolated `pr99-resource-inspector`.
- Actual PR target: `feature/python-semantic-explorer` (#101), not `develop` or the
  final `feature/python-client` target.
- Base and merge-base: `69c424a3b0a06b87d9da2c8cc621b481833379ad`.
- Initial candidate: `251a81429d248bd63e6d32ee97135ceb9bb3a31d`, clean tree at entry.
- Reviewed readability candidate: `f0ce00c3b58984e23ebf6b2d235fc48f4c57dd0a`.
  Tests/live execution ran on the exact content subsequently committed there.
  Functional report's installed-source hash matches current client source.
- Read `KLAB_CLI_Contribution_Review.md`, root collaboration instructions and the
  original discover/compare/explain milestone. The guide's review phase was
  read-only; optional readability work and this report followed that inspection.
- User requires simple, human-readable code, passing unit and functional tests,
  and verified interactions with existing Java before approval/merge.
- Reviewed complete diff versus actual base; F2 implementation, shared Resources
  method keyword additions, bounded pilot, functional/compatibility tests and
  diagnostics/docs separately. No Java, POM, dependency or semantic source change.

## Capability and boundaries

A scientist can inspect an explicit model/resource from the selected Resources
service, retain origin/scope/provider metadata, and distinguish advertised
observables/dependencies from actual model selection. A separately authorized
20-cell pilot verifies that the inspected elevation model occurs in the accepted
recorded resolution associated with actual numerical output.

No global model search, model forcing, exhaustive dependency traversal, new server
route, parser enum requirement or automatic credential routing is introduced.
Missing metadata stays unavailable/partial. The inspector does not create scientific
objects; only its explicitly authorized acceptance pilot does.

## Findings, ordered by consequence

1. **Contract/coordination — shared build and merge decision, not a demonstrated
   Python feature defect.** `.github/workflows/build.yml:18` invokes a root Maven
   build. Exact published candidate251a81429 failed Java compilation at
   `LanguageAdapter.java:1234` in run41094031208; all Python jobs completed green.
   Jenkins separately reports ERROR; its underlying cause is unverified. Current
   base's effective-rules API returned `[]`; that is not a full assertion about
   every repository/organization protection setting, nor permission to hide a red
   build. Maintainer must decide treatment of that shared build-input mismatch.
   Client compatibility does not depend on introducing a new parser type.
2. **Contract/coordination — actual stacked destination.** #99 targets still-draft
   #101's feature branch. Agree on preserving that stack and propagating accepted
   changes before merge; do not silently retarget/rebase or merge into the final
   Python branch. Authenticated GitHub identity `Dobbes` is also the PR author;
   an approving GitHub self-review is not available. Do not impersonate a reviewer.
3. **Optional readability improvement — resolved.** `inspection.py` constructed
   multi-field results positionally. Candidatef0ce00c3b uses explicit field names
   and one local provider-metadata mapping; no new abstraction or semantic behavior
   was added. Existing tests verify field association, redaction, bounds and errors.

No additional demonstrated blocking/important Python defect was found. This is a
bounded contribution assessment, not an exhaustive correctness proof.

## Validation actually performed

Interpreter: `C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-inspector-312/Scripts/python.exe`,
Windows Python3.12.7. Python test commands ran in `python-client/`. Coverage/JUnit
artifacts were isolated outside the worktree. No unit tests mocked the live Java
responses used by the functional run.

| Check / command | Revision / environment | Status and result |
| --- | --- | --- |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance --cov-branch --cov-report=json:<external>/readable-coverage.json` | Exact f0ce00c3b executable content | Passed, exit0;1342 tests; one existing Rasterio warning |
| Existing `.github/workflows/python-client.yml:25` `check_feature_coverage.py` command, with every existing `--new-module` argument and `<external>/readable-coverage.json` | Same content, basead2bc949c167e1c2e370973ce4427fe67cb1cc30 | Passed, exit0; existing100% new-module line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same content | Passed, exit0 each |
| `python -m pytest tests/functional/test_resource_inspection.py --collect-only -q -o addopts= -m 'live and functional' -p no:cacheprovider` | Same content, no service operations | Passed, exit0; one exact test selected |
| `python C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-final-functional-20261030.py` | Same content; unchanged published services/knowledge/components; native local-owner bootstrap | Passed, exit0; actual shipped pytest functional test1 passed in16.76s; workflowpassed=true |
| Functional pytest invoked by the helper: `pytest tests/functional/test_resource_inspection.py -q --tb=short -o addopts= -m 'live and functional' --junitxml=<external>/functional.xml -p no:cacheprovider` | Same content | Passed; 2 warnings: already-imported anyio assertion rewrite and existing record_property/xunit2 warning; no failed/skipped test |
| GitHub Python3.11/3.12/3.13/core-only checks at251a81429; underlying run41094040397 | Prior published head before cosmetic follow-up | Passed; run completed/success despite one stale per-check IN_PROGRESS display |
| Latest readability-head GitHub checks | f0ce00c3b, before publication | Not run yet; must check published head separately |
| Hosted Hub login / exact binary-source attestation / second identity | This pass | Not run; not represented as passed by the local-owner run |

Initial functional invocation inherited `addopts="-m 'not live'"` and deselected
the test (exit5). It ran no test body, created no session/context and submitted no
observation Jobs. Corrected selection was verified with collection-only before
the single actual scientific attempt. No submitted observation was retried.

## Scientific and lifecycle evidence

The actual functional test used unchanged service distribution202610271533,
GraphDB202610161554, existing staging9d20c1c236e5a033487ee47b34cd910c3ebbbe51,
IMOD1328000fff63a1951465b1f7b130c0af15264c95 and previously digest-verified
published generator/geospatial/spatial archives. All preparation was headless.
Existing native local-owner connection bootstrap replaced only profile/Hub login;
no Java DTOs, responses, computation, data reads or workflow assertions were mocked.

- Final authorized attempt5: one session/context, region Job4, elevation Job5,
  context `decd099fc109495ca5b542746ee3e4d9.1qq3lhlfyg`.
- Existing terrestrial Region context; actual5x4 EPSG:3857 grid/bbox verified.
- 20/20 finite numerical cells in m; accepted model
  `staging.vxii.basic.elevation-quantifier`; recorded activities133/134.
- Partial provenance remains partial; no durable-root-commit/exhaustive-lineage claim.
- Existing synthetic generator, not measured elevation or independent accuracy.
- Context/session release confirmed.16 starting config/catalog paths and plugin
  inventories restored; zero remaining Runtime contexts/configured projects;
  five original-service listener/PID records verified; zero visible service consoles.
- Original deployment restored after both preparation invocations. Backups,
  sanitized service logs, raw pilot/JUnit/coverage evidence and inactive archives
  retained; service caches retained; no full-database rollback.
- Latest five-attempt allowance is now exhausted. No further computation authorized.

Independent scientific accuracy/matrix/throughput and F3 diagram rendering are N/A
to this bounded F2 claim, not silently removed from the broader milestone.
Foreign-owner isolation of new F3 scoped activity operations remains F3 work.

External evidence:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-final-functional-selected-20261030`
and `C:/Users/lumsd/AppData/Local/Temp/opencode/pr99-final-review-251a81429-20261030`.

## Contributor coordination

Python changes only, over existing Java interfaces. No Java/language/component
maintainer assignment, approval, credential disclosure, shared-service changes or
other-user operation. Open PR stack and actual heads/bases were inspected. Keep
the shared Maven diagnostic separate from client runtime compatibility and record
a maintainer decision rather than guessing a semantic mapping or bypassing checks.

## Smallest next actions

1. Publish the reviewed readability candidate and report, then check its CI.
2. Obtain maintainer decision on shared Java/Jenkins status and actual stacked
   integration destination; obtain any required independent approval. Do not merge
   merely because the feature tests passed.
3. Review #98 next under the same guide and stronger unit/functional/Java standard;
   preserve its valid nonowner-fixture requirement and exhausted numerical budget.

## Suggested PR description

Bounded typed inspection over existing Java MODEL/RESOURCE routes, explicit origin
and partial metadata, plus an authorized real-model/result acceptance pilot.
Readable keyword-based DTO assembly,1342 passing unit/integration tests, passing
shipped functional test and actual unchanged-Java numerical/model association.
Existing Java API compatibility retained. Shared Java CI and stacked merge/review
decision remain disclosed; no merge or independent scientific acceptance claimed.
