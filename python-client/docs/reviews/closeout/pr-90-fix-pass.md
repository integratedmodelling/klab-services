# PR #90 local fix pass — 2026-10-31

## Current state

The local origin correction is implemented and all ten new regressions pass;
broader validation remains incomplete. This is **not acceptance**.
The user authorized local fixes after the focused review. PR publication, service
changes, live submissions, merge, closure, and supersession remain unauthorized.

Fix worktree:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr90-combined-java-20261031`,
branch `work/pr90-origin-isolation-20261031`, based on GitHub test merge
`21da511166f4ef8f97f4a9c351e06e63e53b5421`.

## Scope

- Gate cached contexts and sessions against the requested Runtime and recorded
  host, including direct parent-session reuse during verified cold restoration.
- Require the remote Runtime to remain advertised and usable for the caller.
- Preserve direct missing-header access at the hosting Runtime; remote
  missing/blank origins fail closed.
- Keep the existing ACL checks and request-local identity binding.
- Explicitly reject, rather than silently redirect, same-wire-ID collisions
  across Runtimes. Simultaneous multi-origin catalog support is not implemented.
- Add deterministic `ScopeRuntimeOriginTest` regressions and correct one existing
  mock Runtime fixture to advertise its actual service ID.

## Before-fix evidence

`origin-regressions-before-corrected-fixture.log`: 10 tests, 8 assertion failures,
0 errors, 0 skips. Matching-origin and cold-context controls passed. An earlier
diagnostic fixture omitted the context's explicit catalog ID; that setup problem
was corrected before recording this before-fix result. No product code had been
changed for either before-fix run.

## Build environment isolation

Copied the existing Maven repository into
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr90-maven-pinned-20261016`.
Only that private copy's five language-module snapshot JARs/POMs and metadata
were aligned to the matching `20261016.113257` artifacts. Runs use Maven offline
mode and that explicit repository. The original shared cache is unchanged.

The artifact identities and SHA256s are in
`../pr90-closeout-evidence-20261031/pinned-language-artifacts.json` (resolved from
the approved OpenCode temporary directory, not relative to this Markdown file).
No catch-all switch, semantic algorithm change, POM edit, or test bypass was used
to handle the newer incompatible language snapshot.

Both the unchanged actual target and corrected candidate were checked under this
same pinned environment. Compilation succeeded, confirming the adapter can build
with the compatible observable-language enum. Both fail the same existing test:
`WorldviewValidationTest.editingCannotResolveForwardOrDeletedDeclarationsFromThePreviousSnapshot`,
with `Unknown concept: test:Later`. No worldview parser or test assertions were
changed to obtain a pass. The pinned environment resolves the compilation error,
but is **not a completely passing accepted dependency tuple**.

The corrected candidate's `ScopeRuntimeOriginTest` reports **10 tests, 0 failures,
0 errors, 0 skips** in `combined-fixed-pinned-strict.log` and its fresh JUnit XML.
The broader reactor command still exits 1. Completed `-fae` (fail-at-end) runs
on both revisions exercised independent modules without hiding the failure:

| Broader selected suites | Tests | Passed | Failed | Errors | Test skips | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Corrected combined candidate | 252 | 251 | 1 | 0 | 0 | 1 |
| Unchanged actual target | 192 | 191 | 1 | 0 | 0 | 1 |

The same worldview test is the sole failure on each revision. Resolver server,
Runtime, Runtime server, and GraphDB support modules succeeded. Reasoner server
and Resources server were **blocked/skipped reactor modules**, not passing tests;
zero test skips must not conceal those unexecuted modules. The counts differ
because #90 includes additional inherited tests, plus the ten new regressions.

Evidence: `combined-fixed-pinned-fail-at-end.log`, `target-pinned-fail-at-end.log`,
`combined-final-test-suites.json`, and `target-final-test-suites.json` in the private
evidence directory. No ignored-failure setting was used.

A separate explicitly narrower origin/identity selection completed successfully
through the full affected reactor, including the previously blocked server
modules:

| Focused origin/identity selection | Tests | Failures | Errors | Skips | Exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Corrected combined candidate | 73 | 0 | 0 | 0 | 0 |
| Unchanged actual target | 40 | 0 | 0 | 0 | 0 |

All 12 selected reactor modules succeeded in both focused runs. The target does
not contain several #90 test classes; the counts describe each revision's actual
tests, not equivalent test content. The candidate's 73 include the ten new
origin regressions. This focused PASS does **not** replace or relabel the broader
gate's FAIL. Mocked messaging setup logs include AMQP configuration errors; the
passing assertions do not establish a real broker connection or live federation.

Evidence: `combined-fixed-origin-identity-focused.log`,
`target-origin-identity-focused.log`, `combined-focused-test-suites.json`, and
`target-focused-test-suites.json`. Focused totals were filtered by the exact
selected class names, not summed from stale XML left by the broader run.

Real two-user/native multi-process tests remain NOT RUN.

## Independent source re-review

The independent reviewer in `ses_e6ce7b892ffeURVCWsSvpfoC1n` inspected the four
changed files after implementation, without running builds or modifying files.
It found no demonstrated new blocking source defect and confirmed that cached
context/session lookup and verified parent reuse now apply the origin gate.
Its provisional recommendation is **NEEDS CONTRACT/COORDINATION EVIDENCE**, not
approval or acceptance; post-fix test results were still pending at review time.

The reviewer subsequently independently checked the completed broad-suite logs,
suite totals, ten passing origin regressions, and all four source hashes. It
confirmed that the correction is locally regression-validated, with no
demonstrated remaining blocking defect attributable to the fix. Its overall
recommendation remains **NEEDS CONTRACT/COORDINATION EVIDENCE** because the
broader gate and real acceptance are incomplete. The focused server run was
still pending during that result-aware review.

## Reproduction and handoff

The broader command is the selection in `pr-90-focused-pass.md`, plus
`ScopeRuntimeOriginTest` on the corrected candidate, with `-o -fae` and:

```text
-Dmaven.repo.local=C:/Users/lumsd/AppData/Local/Temp/opencode/pr90-maven-pinned-20261016
```

The initial corrected pinned run used `clean test`; completed fail-at-end and
focused runs used `test`. Focused runs use the same affected server reactor,
without GraphDB support as a top-level selection, and this exact test selection:

```text
ScopeRuntimeOriginTest,FederationSessionIdentityTest,FederatedScopeControllerTest,FederatedContextPermissionsTest,IdentityHeaderValidationTest,FederatedIdentityTransportTest,ClientScopeCreationTest,ManagedScopeAccessTest,TokenAuthorizationFilterTest,RuntimeContextAuthorizationTest,PersistedContextAccessTest,ScopeManagerTest
```

The unchanged target omits `ScopeRuntimeOriginTest`. Focused runs do not use
`-fae`; both exited 0. All runs retain `-Dsurefire.failIfNoSpecifiedTests=false`
for reactor modules without matching classes; no ignored-test-failure setting
was used.

Changes remain uncommitted and unpublished on the isolated local branch. Four
files comprise the correction: `ScopeManager.java`, new
`ScopeRuntimeOriginTest.java`, one mock service-ID line in
`FederationSessionIdentityTest.java`, and the compatibility note. Source hashes,
resolved language JAR hashes, and patch files are retained in the private evidence
directory. No SDK changes, semantic mappings, ontology changes, service restarts,
live submissions, PR mutations, merges, or remote cleanup occurred.

Final process check found no agent-owned PR90 Java/command processes left running.
Original listener PIDs remain Reasoner 40900, Resources 37600, Resolver 42988,
Runtime 43696, and GraphDB 46944. No unrelated terminals were closed.

## Remaining acceptance

The demonstrated cached-origin defect is locally corrected. The shared worldview
failure still prevents a green broad gate; its underlying parser/environment
cause is not established, and no assertion was weakened to hide it. Resolve that
failure under its own demonstrated compatibility scope. Real ordinary two-user,
nonlocal-group/native-process, warm/cold reconnect, caller attribution and release
checks still require a compatible attested deployment and explicit bounded
setup/workload authorization. Final maintainer review/publication/disposition is
separate. #90 remains open/draft; #102 remains unmerged.

The reviewer noted inherited bare-ID registration replacement and concurrent
cold-restoration limitations. The fix rejects the demonstrated wrong-origin
cached lookup; it does not claim an atomic, multi-origin catalog redesign. Real
two-user/native evidence and the inherited audience-validation contract question
remain outside the demonstrated correction. Exact source hashes are recorded in
the private evidence directory's `fixed-source-sha256.json`.
