# Installed safety regression

`tools/verify_safety_matrix.py` separates SDK precision/refresh/transport contracts,
fresh SDK-only offline/nooptional probes and independent HTTP partial-refresh,
revision, no-replay and export-failure workflows. It reuses exact installed modules,
test/helper/example/runner and binary/JSON oracle custody, with no skipped/empty or
failed group and no overwritten evidence. Counts come from actual JUnit results.

```text
python tools/verify_safety_matrix.py --artifact-receipt RECEIPT.json --output-directory NEW_DIRECTORY
```

A fresh isolated subprocess explicitly bootstraps the selected installed SDK and
blocks optional scientific/notebook/event imports before importing it. Core public
snapshot roundtrip preserves integers beyond2**53, Decimal scale, zero/null, duplicate
offsets/order and source identity without numpy/pandas/xarray/rasterio. Socket
connections refuse, an unsupported future schema refuses and the optional dataframe
view raises its actionable `[tabular]` error instead of silently converting/falling
back. This verifies guarded optional absence, not fresh dependency isolation or an
OS/container security boundary; the environment still has dependencies installed.

Other controls retain partial/stale graph states rather than inventing completeness,
distinguish local timeout from remote cancellation and unknown mutation outcomes from
safe replay, and keep interrupted/denied/oversize exports from damaging existing files.
Source credentials remain guarded and offline saved data does not silently reconnect.

These are Python/reference safety results, not complete native graph/history,
production permission/deployment compatibility, native science or representative
scale/soak evidence. Hostile-code isolation and hard total-disk limits still require
approved OS/container enforcement. No IDE/Java/OS repair, native fallback, release,
develop promotion or final original-row acceptance is authorized by this matrix.
