# Python client resume note

Branch: `feature/python-client`; worktree:
`C:\Users\lumsd\Downloads\k1\klab-python-client`.
Integration base: develop `64754ea2b`; merge commit `7b4c292e0`.
Final ACL/disposal remediation: `34df815bd`.

## Review status

The 7 October re-review closed warm public ACL exclusion and foreign-Runtime
disposal defects. A further cold-header 500 found by live checks now returns
404 before controllers. No additional contribution-specific blocker was identified.
Current scope, verification commands and baseline failures are documented in
`python-client/docs/review.md`; reproduction and measurement boundaries are in
`python-client/docs/throughput.md`. Earlier drafts/audits are retained in Git history.

* Strict Java gate: 56 passed, zero failures/errors/skips.
* Broader Java selection: 436 passed, 11 skipped, 7 errors also reproduced on
  unchanged develop. Exact failing identities/exception types were compared.
* Python offline tests: 97 passed on Windows 3.11/3.12 and Ubuntu/WSL 3.11/3.12/3.13.
* Wheel/sdist and isolated installed-wheel tests passed.
* Integrated fresh live run `klab-pr-remediation-live2`, 7 October
  14:43:25–14:47:11 UTC: 2 tests, warm/cold ACL checks, full c1/c2/c4 and repeated
  reads passed; all owned JVMs/listeners stopped. No cleanup is pending.

## Retained external evidence

Under `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`python-pr-remediation-final-targeted.log`, `python-pr-remediation-broad.log`,
`python-pr-base-runtime.log`, `python-pr-base-storage.log`, per-version Linux
CI logs/XML/JSON, `klab-pr-remediation-live2`, and `klab-pr-final-dist`.
The first `klab-pr-remediation-live` attempt failed the stronger cold-header check
and cleaned up; it is retained as failed evidence, not a passing run.

## Publication and boundaries

Push the reviewed branch to writable fork `Dobbes/klab-services`; upstream
`integratedmodelling/klab-services` denies direct push for account `Dobbes`.
The PR targets upstream `develop`. Hosted CI status belongs on the branch/PR,
separate from the local Linux matrix evidence.

This is an initial synchronous client with issued-credential support and synthetic
live validation. Production hub onboarding, real-provider reference accuracy,
large/bulk/temporal/federated scientific workloads and sustained capacity remain
unverified. No real-provider reference is fabricated by the local fixture.
