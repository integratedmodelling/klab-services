# Contributor review — Job result envelope and A+B reconciliation

6 November2026; self-review using `docs/contributor-guides/KLAB_CLI_Contribution_Review.md`.
Not an independent maintainer review, release approval or PR acceptance.

## Scope and architectural fit

Two-line production change in `Job._decode_result`, inherited by AsyncJob, checks
the optional `empty` member's actual JSON boolean type. Java ObservationImpl owns
`private boolean empty` and `setEmpty(boolean)`; strings/numbers/null/arrays/maps
are not valid alternative values. Missing flag remains supported for existing
mapping Jobs. No new endpoint, decoder schema, polling loop, automatic repair,
retry, service dependency or package was added.

The existing notification check remains first: real Error/Severe notifications
keep JobFailedError precedence and existing transport redaction. Actual boolean
true still means failed empty scientific computation, never successful FINISHED.
Malformed flag without an application error now means ProtocolError, not scientific
failure or success. This is protocol correctness, **not a live server bug report**:
the current IDE was not observed returning a nonboolean flag.

## Before/after acceptance

`tests/unit/test_job_result_envelope.py`:16 before-fix failures (eight invalid JSON
values ×sync/async),10 existing-behavior controls pass. Falsey malformed values
reached the decoder (assertion catches this); truthy malformed values raised the
wrong JobFailedError. Shared fix passes all26. Each scenario checks exact two GETs,
no retries/submissions/cancel/dispose and no decoder invocation on rejected payload.
Focused client/async/binding/integration regression:324PASS.

No new Modeler scientific Job is necessary or authorized. Native Java scanner
failure and research evidence are not Python defects fixed by this change.

## Evidence and review findings

- Full42-row reconciliation distinguishes accepted bounded slices from parent
  acceptance. Tracker open versus delivered implementation is not conflated.
- State checker v1 live receipt source hash remains immutable. v2 adds bounded
  local file/archive budgets and malformed/identity controls; v2 has offline tests,
  not a fabricated fresh live run. Generated state still attributes v1 correctly.
- Candidate validation INVALID/NOT_RUN and warning projection absence are explicitly
  documented, not presented as resolved accepted-generation warnings.
- Initial SDK semantic probe accessed nonexistent ObservableImpl.urn; original
  receipt retained, corrected semantics.urn probe passes. No SDK fix invented.
- Initial full test command lacked optional rasterio; equipped existing environment
  then exposed three expected noneditable-SDK preflight failures with source imports.
  A built, byte-parity checked wheel is tested by extraction, without installing any
  package or altering existing environments. These setup failures remain evidence;
  no tests/guards/coverage thresholds were relaxed.
- Local sdist/wheel build uses existing build tools with `--no-isolation`, no network
  dependency install. Wheel-extracted testing is not claimed to be a pip installation.

## Remaining acceptance and delivery boundaries

RC-P3.3/CAP07 remain PARTIAL; CAP18 remains its inherited safety invariant. No
retention/incarnation/production Hub/periodic worker/original study acceptance.
No PR/push/merge authorized or attempted; local reviewable change only. Normal
authorized PR, hosted CI and maintainer review are still needed for remote delivery.
Final suite/coverage/package receipts and exact commands are in the A+B ledger.
