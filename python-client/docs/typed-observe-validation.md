# Typed observation validation history

See the [maintained API guide](typed-observe.md) for contracts, limits, behavioral
tests and reproducible acceptance commands.

The historical sections describe earlier source snapshots, before the refactor.
Hashes, results, source-status descriptions and merge findings are historical,
not claims about the current working tree. The original evidence text is retained;
references to "current", "now", "this guide" or an uncommitted patch refer to the
recorded validation stage. External evidence remains in its original location.

## Scoped issue #87 audit — 11 October 2026

This follow-up belongs to issue #87 and PR #85 only. The implementation criteria
are covered by the maintained guide/test matrix; remote PR publication and
published-candidate checks remain separate completion requirements.

The issue audit added one maintained regression for the public observer-copy
contract: copies revalidate current ID, binding and AGENT semantics, while the
original validated submission snapshot remains unchanged. All 258 maintained
tests passed on Windows Python 3.12 and the existing hash-verified Linux
Python 3.11/3.12/3.13 package installs. Unexcluded new-module and changed-code
coverage gates passed; targeted lint passed. Production `requests.py` remains
byte-identical to the reviewed refactor below, so its independent vectors,
wheel checks and live acceptance remain applicable source-level evidence.

Both client sources were also tested against the newer isolated federation
integration: Java tree `158b1908e199bffec4296d2163c4f3c42996ee91`, foundation
`ad2bc949c` plus fetched develop `3b01ceaa9`. Typed acceptance and the independent
observer control passed at 23:02:36–23:04:01 UTC on 10 October, with five JVM exits
confirmed. Server implementation and remaining rollout checks belong to #89,
not this PR. Historical uncommitted-state descriptions below refer to those runs;
commit provenance is recorded by Git and the issue's storage update.

External records: `issue87-commit-tests.xml`, `issue87-commit-coverage.json`,
`issue87-commit-3.xx*`, `pr85-federation-live-retry` and
`federation-integration-results.json`.

## Reviewed refactor — 10 October 2026

HEAD `0f6a5a526ea3f23df86540445dad35da7a47bc93` plus uncommitted changes.
Tested tracked patch SHA-256: `4f2ee8e864e50ef9a3f6fa3c3bca32421898e4d7c02658818b30ac3ecffbe27c`.
`requests.py` LF SHA-256: `0d0c77ce0d7d5c80ad0e4cb63a1e84356718bbec07fe6d9b5bdbcf73d46c349e`.
Subsequent edits record documentation evidence only; the new validation document
is untracked and recorded separately from the tracked patch hash.

- 257 maintained tests and 21 independent vectors passed on Windows Python 3.12
  and installed-package Linux Python 3.11/3.12/3.13; 6 live tests deselected offline.
- 274 statements and 120 branches in `requests.py` are fully covered with no
  exclusions. Changed-code gates, targeted lint and wheel/sdist builds passed.
- Live `pr85-refactor-live`, 21:57:13–21:58:32 UTC: maintained acceptance and the
  independent zero-HTTP invalid-observer control passed; cleanup confirmed all
  five JVM exits.
- An independent read-only reviewer confirmed both reuse defects resolved,
  performed 160 targeted tests and additional copying/binding probes, and found
  no remaining code blocker. Actual maintainer acceptance is still their decision.

Evidence: external `client-refactor-manifest.json`, `pr85-refactor-3.xx-*`,
`pr85-refactor-live` and `client-refactor-results.json`. An initial Linux gate
could not resolve Windows Git-worktree paths, and concurrent coverage writes
collided. The local harness was corrected to evaluate a captured Windows diff
and isolate each coverage file; all Linux reports/gates/builds were rerun and
passed. Hosted CI was not rerun for these uncommitted changes.

Java source base remains `ad2bc949c`, not the unavailable recovered integration
or latest develop. The newer federation integration and streamed-export draft
remain outside this validation. No commit, push or PR reopening was performed.

## Recorded baseline and remediation context

Base: upstream integration **ad2bc949c167e1c2e370973ce4427fe67cb1cc30**.
This branch is independent of saved handles and changes no Java contract/session
policy. Baseline checks on that exact source: 97 Python tests and the strict
56-case Java selection passed. The non-local federation-session concern in
`docs/PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md` remains inherited and unresolved.

Internal remediation is currently uncommitted on published head `0f6a5a526`.
It closes the additional observer-type, known-Runtime-identity and selection-range
gaps found in internal review. The earlier immutable-candidate results below are
historical; working-tree retest evidence is recorded separately, without publishing.

## Historical exact-candidate acceptance

Executed final production candidate **3957f39fe** (implementation introduced at 0ca4fc4ce):

* 230 offline tests passed, 5 live deselected, on Windows Python 3.12 and the
  installed-package Ubuntu/WSL Python 3.11/3.12/3.13 matrix. Each Linux version
  built wheel/sdist and passed the same coverage/changed-code gates.
* `requests.py`: 242 executable statements and 100 branches, all covered, with
  zero excluded lines. Changed existing production lines/arcs passed separately.
* Ruff targeted checks passed. A fresh dependency-light Windows Python 3.11
  environment installed the wheel and exercised imports/construction without HTTP.
* Exact-candidate fresh run `klab-typed-review-final`, **2026-10-08
  01:57:27–02:00:03 UTC**: the typed example, 2 existing live tests, cold ACL checks
  and all smoke phases passed. Cleanup confirmed all five JVM exits. Java source
  is unchanged from the recorded baseline; compiled/source hashes are retained.

Evidence is external under `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`typed-coverage.json`, per-version `typed-3.xx` CI/coverage/XML/distributions,
`typed-handles-baseline-java.log`, and `klab-typed-review-final` reports/provenance.
The first attempted matrix rerun refused existing venv directories before tests;
fresh SHA-qualified environments were then created and the complete matrix passed.
The final Python 3.13 sdist build encountered a local shared-checkout staging race;
its tests/coverage had passed, and a serialized wheel/sdist rebuild then passed.
The external report preserves the failed attempt and successful retry. Hosted
matrix jobs use separate checkouts, unlike this local parallel runner.

Review: identity/scope and observer binding, immutable snapshots, model-option
rejection, mutation ambiguity, wire compatibility and negative controls were
checked against the Java consumers. The fixture confirms real option effects;
the bare integer observer form remains subject to server authorization/validation.
No saved-handles dependency or Java policy change is included.

## Uncommitted internal remediation retest — 10 October 2026

Tested published HEAD `0f6a5a526ea3f23df86540445dad35da7a47bc93` **plus the local
uncommitted fixes**, not a new immutable candidate. Production `requests.py`
SHA-256 (LF-normalized): `d85c9fe7fee757da2587da0867e7c2898f2de8600ff7540b15b55732734dc81e`.
The tested pre-evidence patch hash is
`b40eb0832abb5ff3aac2dce45337d2e0c7a91a9f28e65545617efdfb6c9b4b9d`;
subsequent changes in this guide record results only.

* Maintained suite: **253 passed, 6 live deselected** on Windows Python 3.12 and
  fresh installed-package Ubuntu/WSL Python 3.11/3.12/3.13.
* Additional independent vectors: **21 passed** on each version tested.
* Production module: **267 statements / 118 branches, 100% covered**, zero
  exclusions; changed-code gates passed separately on every Linux report.
* Fresh wheel/sdist builds and a fresh core-only Python 3.11 wheel smoke passed;
  construction/local rejection occurred with HTTP forbidden. Targeted Ruff passed.
* Live run `pr85-internal-fix-live`, **20:23:17–20:24:39 UTC**: two maintained live
  tests and the independent observer control passed. The invalid computed-quality
  observer now raises InvalidRequestError with **zero HTTP**, not server denial.
  Test-owned releases, five JVM exits and process cleanup were confirmed.

Evidence is under external `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`client-internal-fix-manifest.json`, `pr85-internal-fix.patch`, `pr85-fixed-coverage.json`,
per-version `pr85-fixed-3.xx` reports/distributions, and `pr85-internal-fix-live`.
The previous failing vectors remain available for comparison. No commit, push,
hosted CI rerun or PR reopening was performed for these uncommitted changes.

The Java production source used in this retest is the current integration target
`ad2bc949c`, with JDK 21.0.12.1+1. This is **not** updated-develop/recovered-merge
validation. `df5517831` remains unavailable and latest-develop authorization merge
conflicts were not resolved here. No broader federation/promotion result is claimed.

## Independent merge handoff

Shared additions with saved handles are expected in package exports, dev extras,
the CI coverage step and README/API inventory. Merge exports additively and run
the coverage checker for both new modules. Neither feature imports the other.
An actual merge-tree check found only these content conflicts: coverage CI,
`docs/public-api.md`, package `__init__.py` and local-runner feature flags.
Resolve each additively; Client implementation, README, dev extras and identical
coverage tooling merge automatically. This is a handoff, not a merged branch.
