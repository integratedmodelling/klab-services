# Development-only operation attribution

The source inventory classifies declarations; body-line coverage measures executable
lines. Neither proves an operation was invoked, functionally asserted or accepted by
a native service. Keep all three claims separate.

The opt-in tools plugin records bounded public **call/resume presence** within pytest
test scopes. Coroutine resumes are deduplicated, not counted as invocations. It stores
function-only test references and anonymous per-run ordinals: parameter values,
arguments, return values, payloads and parameter hashes are never recorded.

Example (use a fresh output filename and the actual installed SDK directory):

```text
python -m pytest -p operation_trace_pytest TESTS --operation-trace-source INSTALLED/klab_client --operation-trace-output evidence.json
```

Add `python-client/tools` to the tooling import path without replacing the installed
SDK path. The plugin verifies the SDK import matches the explicit source and refuses
existing output, existing profilers and parallel pytest. Exact Python source bytes
are pinned before/after measurement. Unrelated replacement profilers are preserved
and invalidate profiler custody. Only the owning thread is measured; existing/future
threads, subprocesses and background/notebook kernels are explicitly unmeasured.

Passed outcomes require setup, call and teardown; teardown failures override a passed
call. Interrupted/unknown tests, truncated traces and unmeasured operations remain
missing evidence, never invented unsupported operations or acceptance. A separate
reviewed workflow/assertion mapping must establish correctness; this tool records
only presence and outcome, not what an assertion proves. Classification counts stay
unchanged. This tooling is outside production package sources and never shipped in
the SDK or worker.

`operation_attribution.py` reconciles one or more exact-source traces into all506
original callable classifications. Each operation retains its executable test scopes,
passed/failed/unknown outcomes and explicit missing functional/native evidence.
`--workflows docs/operation-workflow-mappings.json --repository REPO` optionally links
reviewed, bounded assertion descriptions. A mapping counts only where that operation
was actually observed in a passed test with the **same executed test source hash**.
Mappings state their local/mock/simulated/double authority; none declares native
acceptance. Unmapped operations remain missing assertion evidence even when called.

Test filenames are repository-relative, not ambiguous pytest-root-relative names.
Original development traces without executed test-source pins remain presence-only;
they cannot acquire reviewed assertion evidence from a later mapping or later run.
Runs with different executed test bytes refuse combination. Failed development runs
are retained separately, never silently overwritten by a green re-run.
