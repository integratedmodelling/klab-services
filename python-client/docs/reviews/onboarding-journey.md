# Contributor review: current-tuple controlled onboarding

2026-11-05 UTC; author/CLI review, not independent human approval. Base d92dbdbde;
initial harnessab69eee3c; no production SDK/Java/model/component changes.

## Recommendation

Needs correction/final validation before a PR. Reuse existing credential/connection
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
17gates/lint/build/sourceparityPASS. No Q2 live run launched. Correction now reuses
original computation across both providers via an explicit post-recovery callback
before creator cleanup, with independent token20cellreadback. Reports/profile/
handles are checked using actual acquired-credential redactors as well as known
fixture tokens. Final corrected installed/live/permission/secret-free/cleanup/
author-review/hosted evidence remains required.
