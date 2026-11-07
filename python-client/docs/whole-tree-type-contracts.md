# Whole-tree type checking

Run from the repository root with development dependencies and mypy installed:

```text
python -m mypy --check-untyped-defs --follow-imports=silent --ignore-missing-imports python-client/src/klab_client python-worker/src/klab_worker developer-tools/klab-service-simulator/src/klab_simulator
```

The whole-tree CI checks Windows and Linux. Missing external stubs are tolerated;
this is not strict typing of external libraries or a substitute for runtime tests.
The independent reference service stays outside production SDK/worker packages.

`Job` has a generic result type so synchronous values and native asynchronous
coroutines are not declared interchangeable. Existing unparameterized uses remain
supported. Async waits use task cancellation; synchronous callback cancellation is
explicitly rejected before network I/O. Runtime binding checks remain authoritative.

Dynamic JSON annotations describe heterogeneous wire values, not their validity.
Existing shape, identity, budget and authorization checks still apply. Transport error
metadata annotations do not create fields on unrelated local errors. Optional local
broker/database/import resources refuse impossible states rather than attempting
reconnect, replay or repair.

A clean type sweep proves neither installed artifact parity nor functional coverage,
native compatibility, scientific execution, security isolation or release approval.
Those retain separate candidate-bound tests and acceptance evidence.
