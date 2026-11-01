# Contribution review: PR102 hosted compatibility extension

## Recommendation

**Ready for user review of this bounded compatibility extension.** The exact
hosted regressions from the historical report now pass: semantic readiness,
requested metres, requested millimetres, and the original tiny region request.
No Java or Python runtime workaround was needed during this recheck. This does
not identify the upstream fix or certify the original pinned local tuple.

**The overall milestone still needs contract/coordination evidence.** Its independent
matrix, permissions, notebook, throughput and complete deployment attestation have
not passed as a combined run. The user prohibits merging #102; keep it draft.

## Reviewed state

- Target: `feature/python-experiment-runner`, base
  `0898fbd807355d81567f524fde04d782ad015142`.
- Actual hosted checks used clean candidate
  `e9581c01bfef3c072c52134d57e6a3e66657ed84` and the matching installed SDK.
- Subsequent gate implementation: `e27268a4fb2a5fb0ebd3bcc782dda5b59ce3f4de`.
- The hosted test bodies and `python-client/src` are byte-unchanged by this
  extension. Live evidence identifies its actual source; it is not mislabeled as
  an execution of the entire subsequently expanded adapter.
- Reviewed the original compatibility report, exact connected tests, raw response
  checks, owned cleanup fixture, suite routing, acceptance adapter and delivery
  validator against `KLAB_CLI_Contribution_Review.md` and the simplicity requirement.
- User explicitly expanded #102 to test/fix this report, Python only. The test
  request was bounded to the original one-Job, 20-cell request with no automatic
  retry. This is not permission for the larger milestone or throughput workload.

## Capability and boundaries

The milestone now requires four existing compatibility cases in addition to its
eight integration cases. `milestone-compatibility-units` reuses the original three
semantic/readiness tests; `milestone-compatibility-compute` reuses the original
tiny-region test. The full adapter selects both, and the builder requires all
twelve outcomes. Missing, failed or skipped compatibility checks cannot qualify a
delivery as accepted. Read-only routing clears ambient computation permission;
the tiny request still requires explicit owned-work consent.

No duplicate scientific scenario, new framework, fake output, inferred unit,
weakened assertion, Java/Maven/component/model/worldview edit, shared-service
restart or administrative import was introduced.

## Findings, ordered by consequence

1. **Historical hosted failures no longer reproduce.** Actual raw responses now
   contain `unit.definition=m` and `unit.definition=mm`; their unit-bearing URNs
   and response hashes differ, and the SDK preserves both units. Accepted semantic
   generation is 1 with logical VALID. The original region Job returns a bound
   observation instead of the recorded empty-dataflow error. No client runtime
   fix is justified when these unchanged checks already pass.
2. **Stale status documentation, corrected.** The issue document still presented
   the 17 October failures as current. A dated current section and sanitized actual
   evidence now supersede that hosted status while retaining the original report
   as history. Server binary commit/fix attribution remain explicitly unverified.
3. **New scope needed an acceptance gate.** Previously, omitting the four historical
   compatibility results still passed the milestone's eight-case validator. A new
   regression demonstrated that omission before the gate change. The adapter and
   required-test set now include them. This is an implementation of the user's
   expanded scope, not a claim that the earlier eight-case scope was dishonest.
4. **Scientific limits remain material.** The unit tests prove wire/SDK unit
   preservation, not numerical metre-to-millimetre conversion. The region test
   checks a returned observation, not twenty independently verified numerical
   elevation values. The original local Java 671a818 / IMOD 608bef / ODO 0.1.0
   combination was not rerun. None of these limits is converted into success.

## Simplicity and contributor fit

Keep one authoritative test body for each regression. Two suite aliases and a
named read-only set make routing explicit; four required test IDs make the final
gate inspectable. Tests cover routing, consent and rejected incomplete reports.
No SDK interface or runtime implementation changes were needed. This follows the
existing runner/manifest structure rather than adding another test framework.
No remaining demonstrated code defect was found in this bounded extension; that
is not exhaustive proof or independent maintainer approval.

## Validation actually performed

Windows Python 3.12.7, dedicated `pr102-upstream-312` interpreter, explicit worktree
SDK and `examples/hosted-deployment.json`. Credentials stayed private. Commands
ran from `python-client` with fresh external logs/JUnit reports.

| Check / command | Status | Evidence and limits |
| --- | --- | --- |
| Existing runner: `--suite connected-semantic-readiness --suite connected-unit-contract --profile examples/hosted-deployment.json --certificate <private-path> --report <fresh>/semantics-units.xml` | Passed, exit 0; 3 tests in 9.16s | Exact original assertions inspect wire data before DTO conversion; no observation Jobs |
| Existing runner: `--suite connected-tiny-compute --allow-owned-work` with the same explicit profile/certificate and fresh `tiny-region.xml` | Passed, exit 0; 1 test in 103.36s | One session/context, one submitted Job 1, requested 20 cells, observation 14; passing teardown confirms both releases; no automatic retry |
| Authenticated metadata/raw-unit follow-up, `initialize=False` | Passed | Four operational service identities and distinct canonical redacted response hashes; no new scopes or observation Jobs |
| New missing-compatibility gate regression before the change | Failed, exit 1 | Old validator accepted a report lacking the newly required cases |
| Exact workflow full unit/integration and coverage command | Passed, exit 0; 1,599 tests in 49.55s | Existing Rasterio warning; offline/real local kernel/process checks, not the complete Java milestone |
| Exact existing feature-coverage gate, all original arguments | Passed, exit 0 | No coverage exclusion or reduced threshold |
| Ruff E9,F63,F7,F82 and `git diff --check` | Passed, exit 0 | Existing fatal lint and whitespace |
| Wheel/sdist build and isolated current-wheel core-only smoke | Passed, exit 0 | NumPy/Rasterio/xarray absent and not imported; no runtime dependency expansion |
| All expanded milestone selectors with `--collect-only` | Passed collection, exit 0; 12 cases | Correct routing, not a full live milestone execution |
| Diff check of `src`, original hosted test bodies, Java and Maven against e9581c01b | Passed | No runtime/assertion or Java changes hidden in this extension |

See [sanitized live results](../../results/hosted-worldview-compatibility-20261031.json)
for actual source/SDK/service identities and raw report hashes. The logs/JUnit are
external; no private certificate, token, arbitrary server response or machine state
is committed. Final published-head CI is checked separately and cannot certify
the missing live scientific gates.

## Scientific, identity and lifecycle evidence

Normal matching HTTPS Hub/ENGINE certificate authentication was used, without a
server-owner key or disabled TLS. The only scientific submission in this pass was
the original tiny-region request. Its fixture releases only its own context and
session and treats cleanup failure as a test error. No other user's assets were
accessed or released; no shared services were restarted. The prior sleep request
returned success after wake; this task does not request another sleep.

Deployment service IDs are observations, not source/binary attestation. Independent
accuracy, two-user private/shared controls, conversions, scientific matrix and
throughput remain separately gated. The full adapter now adds one original tiny
request to its workload; future runs require explicit workload authorization.

## Contributor coordination

Python-only functional testing and documentation using current public contracts.
No assigned reviewer, approving self-review, maintainer impersonation or Java fix
is claimed. The exact server-side change responsible for the new behavior is not
established by these tests. Preserve historical failure evidence rather than
rewriting it as if it never occurred.

## Smallest next actions

1. Keep #102 unmerged for the user's later review; publish this reviewed batch and
   confirm exact-head CI.
2. Treat the reported hosted regressions as passing on the retested deployment,
   not as proof of the original local tuple or the complete milestone.
3. Resolve the independent fixture/deployment/permissions gates and obtain fresh
   explicit computation authorization before the full twelve-case milestone,
   separate scientific notebook or throughput runs.

## Suggested PR description

Adds the original worldview/unit/tiny-region compatibility checks to #102's required
functional scope. All four exact hosted checks now pass without a new runtime
workaround or Java change. The builder requires twelve cases and preserves explicit
mutation consent. Updated offline suite: 1,599 passes with existing coverage/lint.
Full scientific acceptance remains blocked; draft and explicitly not for merge.
