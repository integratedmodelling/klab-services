# Contributor review: current-tuple controlled onboarding

2026-11-05 UTC; author/CLI review, not independent human approval. Base d92dbdbde;
initial harnessab69eee3c; no production SDK/Java/model/component changes.

## Recommendation

Ready for bounded maintainer review after the corrected evidence below; not human
approval, production acceptance or release. Reuse existing credential/connection
facade, read-only native export and independent20cell oracle. Actual production
Hub/license/certificate onboarding remains external, not inferred from TEST.

## Source-preflight finding

**Important harness defect, found before live execution:**
`NonuniformExportFixture.java:24–26` deliberately exhausts its20cells after one
computation in a Runtime. Initial Q2 harness invokes two independent computations
(one per provider), so its second computation would exhaust this single-use
scientific fixture. This is source evidence, not an observed Java failure.

Do not weaken/reset/rewrite the fixture or infer a product limitation. Compute
once through certificate onboarding, close creator transport, then demonstrate
both certificate and token fresh authentication against the same original work
before creator cleanup. Token access must independently read all20 original cells,
not merely reuse certificate report values. Both fresh workers must audit against
scientific replay/disposal. Record this deliberate refinement of the initial local
contract; no user A/B requirement is removed and no second computation is claimed.

## Validation so far

196focused connection/guard/workflow tests and lintPASS. Initialinstalled2,963tests/
17gates/lint/build/sourceparityPASS before live execution. Correction reuses
original computation across both providers via an explicit post-recovery callback
before creator cleanup, with independent token20cellreadback. Reports/profile/
handles are checked using actual acquired-credential redactors as well as known
fixture tokens. Final corrected installed/live/permission/secret-free/cleanup/
author-review/hosted evidence remains required.

## First actual attempt and bounded correction

`q2-onboarding-live` FAILED before scientific computation: Connection correctly
refused Reasoner status `available=False,operational=True`. Owned processes and
listeners closed; unrelated Java unchanged. This is not an accepted journey.

Evidence-led diagnosis: the owned runner restarts Resources for component reindex
immediately before launching the functional suite. ServiceInstance380–424 polls
essential services and sets maintenance/available on its scheduler; Reasoner logged
a capabilities NPE during that owned restart. An open HTTP port is not readiness.
This supports a transient post-reindex readiness hypothesis, not a Java defect
conclusion. Add a bounded60s public-status preflight for the Q2 scenario only;
preserve every observed available/operational snapshot. It does not alter status,
weaken selection or replay a scientific write. One corrected fresh owned run is
required; if genuine readiness stays false, classify its exact blocker and continue
independent work rather than a fixture-repair loop.

## Corrected exact-candidate evidence and final review

Candidate `c19e319c1`, base `d92dbdbde`; later documentation-only changes must be
verified before reuse. `q2-onboarding-ready-live` PASS. Public snapshots start with
Reasoner unavailable / Resources nonoperational and end genuinely ready; the same
service identities are selected by both providers and creator cleanup connection.
The correction resolves this owned startup race; it does not fix/waive arbitrary
Reasoner NPEs or make readiness permanent. Earlier failed run remains evidence.

| Check / command | Exact environment/evidence | Result and limit |
|---|---|---|
| `run_gates.py --repo <checkout> --label ab-q2-readiness` | c19e319c1, installed noneditable Python3.12.7 | PASS:2,966tests/17coveragegates/lint/build/sourceparity; retained Rasterio warning |
| Focused connection/guard/workflow tests | initial and corrected one-compute harness | PASS:196tests;26focused preflight/recovery tests after readiness correction |
| `run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21> --components <corrected-components.json> --state-dir <q2-onboarding-ready-live> --scenario onboarding-journey` | Same pinned Java/core/components and synthetic consolidation worldview | PASS:two providers,four ready selected identities,one Java computation,independenttoken20cells/m,originalnativegeometry/ID in twofresh workers,no replay/disposal |
| Actual identity/permission/expiry | Owned current-tuple scopes | PASS:owner positive,validcollaboratorpositive,privateparentAuthorizationError,knownexpiredTESTJWTAuthorizationError; no generic401expiry inference |
| Secret-free/lifecycle | Actual acquired-credential redactors, savedprofiles/handles/reports, ownedrunner | PASS:creator localclose,freshworkerlocalclose,creator-only releases; allownedprocesses/listenersclosed,existingJavaunchanged |
| Hosted checks/normal merge | Final documentation head | Pending; required before eligible merge |

Wheel `795279deed9385c02c14bbe339d9822c601e468663f6042cee3b7d4fbaa056d6`;
sdist `08ac57100a6390784de28d6898d00cf523cef813f55464a146c9fac23211e4ba`.
External `ab-q2-readiness/identity.json`, `q2-onboarding-ready-live/result.json`
and public `onboarding-readiness.json` retain exact commands/hashes/observations.

Full diff reviewed for usefulness/architecture/contracts/scientific fidelity/
identity/lifecycle/state/performance/contributor fit/maintainability. No remaining
demonstrated important/blocking defect in this bounded harness. No production SDK,
Java, scientific fixture, dependency or worldview change; no contributor assigned
or competing ownership inferred. Existing public APIs and oracle are reused.
Sequential bounded workers are not concurrency/scale acceptance. The readiness
loop has a60s polling budget with per-probe5s bounds, not a hard whole-run60s SLA.
Source/Runtime incarnation/history and production issuer/license/certificate are
explicitly unproved; original studies remain blocked below execution. No full
RC-P1.1/CAP01/02 or A/B promotion. Smallest next action: normal exact-head CI/merge.

Suggested PR: current-tuple controlled native ENGINE/token onboarding, original
scientific readback and audited fresh-process recovery, with these limits and the
retained failure/correction evidence. CLI/author review is not maintainer approval.
