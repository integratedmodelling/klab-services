# Cleanup and source-structure review - 1 November 2026

## Outcome and scope

All 17 tracked PRs are merged, into their recorded targets, not necessarily develop
or a tagged release. PR #102 is tooling-only; scientific acceptance remains deferred.
PR #94 now has a description; #95/#99/#101 now lead with verified current status,
results and limitations. Their older descriptions remain labelled historical text.
No PR target, accepted head, merge or test gate was changed during this cleanup.

The prematurely started PaL work was removed: acquisition script/bytecode, local
archives and inventories, research note, worktree and empty local branch. Original
user specifications were preserved. Portfolio `2c58db1` corrects status to planned,
not started; deployment `7475933831` and public browser verification passed.

## Local work disposition

The owner selected separate archival publication rather than deletion or porting
of unique old experiments. These are historical snapshots, not active integration,
newly tested contributions or requests for review/merge:

| Archive branch | Commit | Contents |
| --- | --- | --- |
| `archive/python-worker-prototype-20261101` | `1f65281b56e1d3c8d5967a7f7f4eaa5445371dd0` | Old subprocess/NDVI/scalar prototype, original tests/docs and IDE metadata |
| `archive/python-develop-integration-20261101` | `cbbd00b4f85340cd12875cb15e3e48593b7ee4b1` | Existing unfinished merge of `188caeb47` and `671a81815`, explicitly archived rather than adopted |
| `archive/python-closeout-notes-20261101` | `36be2bf508b61146d42b05398243cd549dc403fb` | Historical plans and closeout evidence with superseding archive-status notice |

Each snapshot was clean and exactly matched its remote head after publication.
Credential-pattern scanning of changed files returned no candidates; this is a
bounded check, not a proof that arbitrary prose contains no sensitive information.
No new PR was created and none of the archive branches was merged into an active branch.

Obsolete validation/comparison worktrees were removed only after verifying that
their changed/index contents were already published and no Java/node command line
referenced them. Counts: six changed files in the old #90 validation tree, 334
staged file contents in the stripped PR-review tree. Missing tracked baseline files
were recreated, not interpreted as intended source deletions. After a Windows
partial-removal error, 714 residual tracked files were verified against published
`671a81815` before the residual directory was removed; other residuals were caches.
The resulting inventory showed 23 registered worktrees and zero dirty worktrees.

## Source review method

Compared each accepted head with its merge base and retained the actual landed
merge-parent delta separately. Comparing only the accepted head with a later base
can falsely attribute parallel-branch deletions to a feature. Inspected substantive
SDK paths and selected Java/acceptance-tool paths; inventoried production changes
for all 17 PRs. Python AST function spans/branch nodes guided inspection, not verdicts.
This is a targeted maintainability review, not a claim to have certified every
line of upstream imports, generated DTOs, documentation, tests or recorded media.

## Per-PR assessment

| PR | Assessment of the substantive changes |
| --- | --- |
| #80 | Shard admission is centralized rather than independently throttled in each executor. The monitor/active-count logic is understandable; acquire/release and cancellation pairing are necessary complexity, not removable decoration. |
| #81 | `ShardBufferIO` isolates bounded primitive transfer from storage ownership/header validation. This is a particularly small, cohesive extraction; retaining the type switch is clearer than a generic serializer framework. |
| #84 | Client/scopes/jobs, DTO decoding and HTTP are separated; the new Java fixes are mostly local guards and outcome corrections. Broad initial contribution, not a small patch. Workflow orchestration is denser than the public API. |
| #85 | Immutable request/options objects translate to the existing wire schema. Validation is explicit; do not replace it with unchecked dictionaries merely to shorten the code. |
| #86 | Saved-handle schema, credential rejection, runtime binding and restoration are cohesive. Atomic file creation and primary-error preservation justify the extra cleanup branches. |
| #90 | A broad integration PR, not uniformly a Python port. Runtime configuration loading is a clear extraction, but scope recovery mixes authorization, cached ownership and federation reconstruction and remains a Java maintainability hotspot. No Java rewrite was made. |
| #92 | Streaming export separates format/result handling from transport; explicit acknowledgement and atomic output retain clear failure semantics. Acceptance-tool lifecycle duplication remains. |
| #93 | Selection, credentials, catalog data and connection behavior have useful boundaries. `Connection.refresh` still combines observation, selection/pinning, client creation and peer notification; this deserves smaller named stages. |
| #94 | Optional analysis imports remain lazy and raster ownership uses a context manager. Reliability corrections preserve existing error/identity semantics. Some compact inline validation/raise statements reduce readability without reducing real complexity. |
| #95 | Readiness/dependency capture is explicit and fail-closed. Long runner `main` functions mix argument validation, fixture construction, process lifecycle and report writing. |
| #96 | Documentation-only reproduction clarification; no runtime simplification question. |
| #97 | Bounded probe and its independent oracle are direct functions, with no need for a new framework. It is explicitly not scientific throughput. |
| #98 | Evidence DTOs and bounded decoding are cohesive and reuse the inspection sanitizer. `fetch_execution_evidence` still combines context parsing, graph retrieval/filtering, association and activity retrieval. |
| #99 | Resource/model inspectors are direct wrappers over existing Java endpoints, with typed bounded results. A shared generic inspection engine would add indirection without demonstrated benefit. |
| #100 | Runner ownership and outcomes are explicit, but `_one` has too many lifecycle responsibilities (details below). Necessary ambiguity handling should remain. |
| #101 | Semantic operations delegate to Java instead of adding a Python reasoner. Authority search explicitly validates/selects candidates. Dense short statements are a readability issue, not evidence of a second reasoning implementation. |
| #102 | Workflow/fixture/evidence layers are separated, but local deployment verification concentrates several independently testable checks in one function. Validators must not be loosened to make this shorter. |

## Concrete simplification candidates, in priority order

1. **#100 - `src/klab_client/experiments.py:286-394`, `ExperimentRunner._one`**
   (109 lines in accepted source). It claims the client, creates scopes, submits two
   stages, waits, validates results, processes them, classifies failures and releases
   resources. Extract result-binding validation, outcome classification and owned
   cleanup as named helpers first. Preserve unknown submissions, cancellation,
   terminal-only release, retained handles and primary versus cleanup errors.
   Re-run runner identity/failure/cancellation tests and the offline suite; a
   behavioral change would also require bounded original live acceptance.
2. **#102 - `tools/workflows/deployment.py:48-187`, `verify_local_launch`**
   (140 lines). Extract repository identity, pinned file inventory, staged project
   verification and per-service launch verification. Pass explicit verified inputs;
   do not introduce a flag that bypasses checks or cache stale results. Preserve
   process birth/executable, actual argfile/classpath, plugin/JDK/project hashes,
   capabilities identity, budgets and sanitized errors. Re-run mutation tests that
   alter those bindings; live acceptance would still need its existing before/after checks.
3. **#92/#95 - `tools/run_export_acceptance.py:16-150` and related runners.**
   The setup/run/report/cleanup pattern is repeated. A small owned-stack context
   manager and explicit step recorder could reduce duplication. Do not collapse
   different launch trust assumptions or silently broaden service ownership. Test
   partial startup failure, timeout, interrupt and cleanup failure before adoption.
4. **#93 - `src/klab_client/connection.py:181-239`, `Connection.refresh`.**
   Separate catalog observation, pinned selection and initial-client construction.
   Keep readiness invalidation before probing, identity pins, trust restrictions,
   explicit peer initialization and the shared deadline. Test stale identity,
   interrupted refresh, authentication errors and policy attempts to expand trust.

These are maintainability findings, not newly demonstrated runtime bugs. Removing
validation, flattening unknown/failure outcomes, automatic retries or introducing
large generic frameworks would not be improvements. No speculative refactor was
mixed into archival cleanup; active executable source is unchanged.

## Verification and retained failure

- Portfolio progress tests and build passed before `2c58db1`; public browser checks
  passed against the exact deployed correction. Existing layout/media were retained.
- A first fresh Python command used an environment whose editable install still
  pointed to the older `pr102-milestone-delivery` tree: **37 failed, 1641 passed**.
  The log is retained externally, not erased or represented as a source pass.
- The import path was then explicitly bound to accepted `pr102-final-acceptance-20261101`
  source and tools; the actual module path was asserted and recorded. No source,
  tests or gates changed. Result: **1678 passed, 1 warning in 43.35s**. The warning
  is the expected Rasterio identity-georeferencing case. This isolates the initial
  command's wrong-source setup; it does not turn its failures into acceptance.
- No Java tests or live scientific runs were executed in this cleanup. Archival
  code was saved, not newly accepted. Missing native GeoTIFF units remain tracked
  in geospatial issue #10, and full #102 scientific acceptance remains outstanding.

External detailed inventories/diffs/logs: local
`C:/Users/lumsd/AppData/Local/Temp/opencode/cleanup-review-20261101/` and
`C:/Users/lumsd/AppData/Local/Temp/opencode/verification-audit-20261101/pr-diffs/`.
They support this summary; they are not all published artifacts or accepted code.
