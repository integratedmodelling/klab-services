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
