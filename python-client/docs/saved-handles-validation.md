# Saved handles validation history

See the [maintained API guide](saved-handles.md) for contracts, limits, behavioral
tests and reproducible acceptance commands.

The historical sections describe earlier source snapshots, before the refactor.
Hashes, results, source-status descriptions and merge findings are historical,
not claims about the current working tree. The original evidence text is retained;
references to "current", "now", "this guide" or an uncommitted patch refer to the
recorded validation stage. External evidence remains in its original location.

## Scoped issue #88 audit — 11 October 2026

This follow-up belongs to issue #88 and PR #86 only. The strict references,
credential checks, shared submission/capture binding and decoder/URL helpers
all implement this issue's own acceptance criteria. The guide/test matrix maps
those criteria to maintained checks; publication and published-candidate checks
remain separate completion requirements.

The pre-commit Windows Python 3.12 verification passed all 290 maintained tests,
unexcluded new-module and changed-code coverage gates, and targeted lint.
Production source/tests remain unchanged from the previously passing installed
Linux Python 3.11/3.12/3.13 matrix, 22 independent vectors, wheel checks and live
review below. No additional runtime abstraction was needed for this audit.

The same client source also passed fresh-process acceptance and independent
deletion/untrusted-origin controls against the newer isolated server integration
at 23:04:01–23:05:33 UTC on 10 October. The tested Java tree was
`158b1908e199bffec4296d2163c4f3c42996ee91`, foundation `ad2bc949c` plus fetched
develop `3b01ceaa9`; five JVM exits were confirmed. A graph-retaining Runtime
restart also passed the private/shared/public-exclusion and collaborator-first
restoration checks. That Java merge and its remaining federation rollout work
belong to #89, not this PR. Historical uncommitted-state descriptions below refer
to their earlier runs; commit provenance is recorded by Git and the issue update.

External records: `issue88-commit-tests.xml`, `issue88-commit-coverage.json`,
`pr86-federation-live`, `federation-integration-cold-live` and
`federation-integration-results.json`.

## Reviewed refactor — 10 October 2026

HEAD `278b6160bb15f6490be134d3a11548b2232d2509` plus uncommitted changes.
Tested tracked patch SHA-256: `34dcb537a5cd3a7b6539dcb609762ac8d8899c80d717c57cd34508489f89daa9`.
`handles.py` LF SHA-256: `143d04f6249386538f2df79e85dd5adcc00a8648aae7d34fab2d131230c61d21`.
Shared helper source/test hashes are recorded in `client-refactor-final-manifest.json`.
Subsequent edits record documentation evidence only; the new validation document
is untracked and recorded separately from the tracked patch hash.

- 290 maintained tests and 22 independent vectors passed on Windows Python 3.12
  and installed-package Linux Python 3.11/3.12/3.13; 5 live tests deselected offline.
- 316 statements and 134 branches in `handles.py` are fully covered without
  exclusions; changed-code gates cover the shared client/transport/service code.
  Targeted lint and wheel/sdist builds passed.
- Live `pr86-refactor-live`, 22:00:04–22:01:37 UTC: maintained fresh-process recovery
  and independent deletion/untrusted-origin controls passed; cleanup confirmed
  all five JVM exits.
- An independent reviewer confirmed the source-rebinding fix and subsequently
  caught a URL-equivalence regression. Shared explicit default-port normalization
  fixed it. The final review ran 53 targeted tests and 12 additional offline URL
  vectors and found no remaining code blocker. Maintainer acceptance remains
  their decision.

Evidence: external `client-refactor-final-manifest.json`,
`pr86-refactor-final-3.xx-*`, `pr86-refactor-live`, and `client-refactor-results.json`.
The earlier local matrix had Windows worktree-path and concurrent coverage-file
problems; the harness was corrected to evaluate captured diffs and isolate each
coverage file. The final installed-package matrix and all gates/builds passed.
Hosted CI was not rerun for these uncommitted changes.

Java source base remains `ad2bc949c`, not the unavailable recovered integration
or latest develop. The newer federation integration and streamed-export draft
remain outside this validation. No commit, push or PR reopening was performed.

## Recorded baseline and remediation context

Base: upstream integration **ad2bc949c167e1c2e370973ce4427fe67cb1cc30**.
This feature works with the original #84 objects and imports no typed-request
module. It changes no Java contract or federation-session policy. Baseline Python
97 and strict Java 56-case checks passed on the recorded base; the broader
promotion/federation gate remains unresolved and is not certified here.

Internal remediation is uncommitted on published head `278b6160b`. It closes the
additional known-source Runtime identity and encoded-credential omissions found
by internal review. Earlier candidate results below are historical; the patched
working-tree checks are recorded separately and no PR has been reopened.

## Historical exact-candidate acceptance

Executed candidate **ee96e7719**:

* 241 tests passed, 5 live deselected, on Windows Python 3.12 and the
  installed-package Ubuntu/WSL Python 3.11/3.12/3.13 matrix. Each Linux version
  built wheel/sdist and passed new-module/changed-code coverage gates.
* `handles.py`: 303 executable statements and 134 branches, all covered with
  zero exclusions. Changed existing production lines/arcs passed independently.
* Targeted Ruff checks passed. A fresh dependency-light Windows Python 3.11
  environment installed the wheel and exercised import/offline round-trip with
  HTTP forbidden and no optional tooling dependencies installed.
* Exact-candidate fresh deployment `klab-handles-live1`, **2026-10-08
  01:33:40–01:40:16 UTC**: 2 existing live tests, all four fresh-process example
  modes, Runtime and Resolver jobs, scoped observation reads, actual pending-job
  timeout/restoration/completion, collaborator positive/private-negative checks,
  cold ACL checks and smoke phases passed. All five JVM exits were confirmed.
  Processes A/B preserved job 15, observation 112 and pending job 16; the Resolver
  retained peer job 23. These are run-specific identities, not public defaults.

Evidence is retained externally under `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`handles-coverage.json`, per-version `handles-3.xx` CI/coverage/XML/distributions,
and `klab-handles-live1` source/compiled/model provenance and reports. No generated
credentials, handles or state files are tracked. Earlier failing tests exposed a
default-port normalization edge and fixture-label issues; all required gates were
rerun after correction. New production code is unchanged by this evidence update.

Review examined strict local parsing, bounds and identity invariants, configured
origin-only routing, capability versus access failures, observer/focus validation,
fresh graph membership, result decoder allowlists, secret diagnostics, atomic
publication/cleanup, mutation ambiguity and remote lifecycle. No extra server
contract or typed-feature dependency was needed; no auto-retry or ownership
transfer was introduced.

## Uncommitted internal remediation retest — 10 October 2026

Tested published HEAD `278b6160bb15f6490be134d3a11548b2232d2509` **plus the local
uncommitted fixes**, not a new immutable candidate. Production `handles.py`
SHA-256 (LF-normalized): `ebcc56871c967416703b513035f235929a377d3a637dab063a52d1510663e9cf`.
The tested pre-evidence patch hash is
`8a5039e999f43e939ce1cb460a08b1df11e2323202b46f77dfe2c8a157f944cd`;
subsequent guide changes record results only.

* Maintained suite: **270 passed, 5 live deselected** on Windows Python 3.12 and
  fresh installed-package Ubuntu/WSL Python 3.11/3.12/3.13.
* Independent vectors: **22 passed** on each version tested, including the
  formerly failing source-identity and encoded-credential controls.
* Production module: **322 statements / 146 branches, 100% covered**, zero
  exclusions; changed-code coverage gates passed separately on all Linux reports.
  A coverage-only unreachable for-loop exhaustion arc was removed by expressing
  the same fixed-budget inspection as an explicitly bounded loop; no exclusions
  or threshold weakening were used.
* Wheel/sdist builds, fresh core-only Python 3.11 installed-wheel offline smoke
  and targeted Ruff passed. An initial smoke command had a shell-quoting error;
  the actual script smoke was then executed successfully.
* Live run `pr86-internal-fix-live`, **20:25:09–20:26:31 UTC**: the maintained
  fresh-process example and independent deletion/untrusted-origin controls passed,
  including scoped readback, Runtime/Resolver routing, timeout recovery and
  collaborator positive/private-negative cases. Cleanup confirmed five JVM exits.

Evidence is external under `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`client-internal-fix-manifest.json`, `pr86-internal-fix.patch`, `pr86-fixed-coverage.json`,
per-version `pr86-fixed-3.xx` reports/distributions, and `pr86-internal-fix-live`.
No commit, push, hosted CI run or PR reopening was performed for the patches.
The Java source in the real deployment is integration target `ad2bc949c`, not
latest develop or unavailable recovered `df5517831`; updated authorization and
broader federation/promotion gates remain separate and blocked on that checkout.

## Independent merge handoff

Both branches start at the same SHA. Expected overlaps: additive package exports,
Client facade additions, README/API inventory, dev extras, coverage CI and the
local runner's feature flags. Coverage-check tooling is identical and independently
included; preserve both new-module arguments when combining. Neither feature
depends on the other or changes Java session authority.
An actual merge-tree check found four content conflicts when combining the two:
coverage CI, `docs/public-api.md`, package `__init__.py` and local-runner feature
flags. Resolve each additively. Client code, README, dev extras and identical
coverage tooling merge automatically; neither independent branch was merged/reset.
