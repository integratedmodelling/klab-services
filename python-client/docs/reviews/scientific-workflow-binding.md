# Bounded scientific manifest/adapter binding correction

Contributor review under KLAB_CLI_Contribution_Review.md, not human approval.
Base6a7d66968a612a16bb309ad0be6b8aa8d4f9243b; coherent TA-2 correctness follow-up
found during final TA-7 audit, not a new platform or a PR per fixture assertion.

## Reproduced local defect and correction

N manifest with expected_units changed fromm tokm still passed structural/hash
validation, although the fixed existing workflow checks metres. No Java was run
with the inconsistent manifest. Corrected preflight binds known workflow to its
actual oracle path, reviewed m/EPSG3857/initialization domain before wheel/service
execution. Unsupported units/CRS/time/unrelated oracle reject; no conversions,
surrogate inference, altered scientific expectations or Java/model/input changes.

Tests cover each inconsistent declaration and both actual versioned manifests;
controlled unit adapter fixture explicitly substitutes its local fixture oracle,
never claims Java/scientific acceptance. Original realN/S manifests already match,
so their actual passes remain valid and no scientific rerun is justified. Existing
schema still supports future versioned cases/L5records; only the two fixed runtime
adapters are restricted to the science they actually execute.

Recommendation: **Needs normal full regression/hostedCI/eligiblemerge**. Final
documentation PR236 held until this local correction is verified and merged. No
test deadline, coverage gate, oracle value, primary A+B status or historical failure
relaxed. Independent scheduledL4 worker gate remains unprovisioned.

## Capability binding and exact validation scope

The same fixed-adapter preflight now restricts declared capabilities to each
reviewed workflow's existing capability set. Excluded Target C/CAP-17 cannot be
claimed by the numerical case; a negative regression covers that declaration.
Both actual manifests remain compatible.75focused tests and Ruff PASS.
Initial962e6024e passed3484installedtests/all17gates/build/install/lint; that result
does not certify the newer capability delta. A fresh exact-head full regression
and normal latest-head hosted checks are required before merge. No live Java rerun
or scientific/input/expectation change is justified by this metadata correction.

## Retained coverage failure and deterministic regression

5d825f1f5 offline suite passed3485tests, but coverage gate0 failed: unchanged
async_client.py line87/arc86→87 was not covered. The existing timeout test uses
a15ms wall-clock wait and can reach an earlier deadline check instead. Added an
L1 test with a module-local controlled clock and status failure after the deadline
to exercise that exact TransportError→WaitTimeout path, retaining the same job
without HTTP/replay/cancel. No SDK code, real timeout, expectation or gate changed.
The failed receipt is retained; new-head full gates are required, not an unchanged
retry until green. This test is not L3/L4 evidence and does not rerun Java.
