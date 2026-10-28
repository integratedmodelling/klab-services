# Shared scientific workflow: development increment

`tools/workflows/discover_compare_explain.py` is shared acceptance/notebook tooling,
not a second SDK or an installed public package. Run with `python-client/tools` on
the import path, following the existing workflow convention. It reuses F1–F4 and
the native export, raster-analysis and saved-handle APIs.

## Inputs must come from an independently verified fixture

- Four `MatrixCase` objects, each containing a typed `Experiment`, resolution name
  and `RasterOracle`. The two variations must differ in actual semantic definition
  or supported scenario options; namespace/name changes alone are rejected.
- Two distinct reference grids, consistent across variations. Actual exported
  shape, CRS, affine transform, precision and all values/masks are compared with
  each reference. Valid zero is checked as data, not missingness.
- Each oracle supplies exact decimal values, units, tolerances and the SHA-256 of
  its independent fixture/reference source. A hash is traceability, not proof that
  a reference is independent. An all-missing oracle is rejected. Current scope is
  one north-up numeric raster band, at most one million cells per result.
- An existing model URN and independently specified recorded computation node IDs.
  Model inspection is an advertisement, not proof that Resolver selects it. The
  evidence check requires those node identities in a recorded plan belonging to a
  successful numerical activity linked to this result. It does not infer execution
  from a standalone plan or assert durable root commit.
- A factory returning an independently owned ready Connection/Client for each
  call. Acquire credentials via existing providers; do not put them in case data.

The workflow validates necessary request/grid distinctions, not whether the chosen
variations have scientifically justified effects. That remains a fixture-selection
and independently specified oracle prerequisite. No built-in four-cell fixture is
claimed compatible with current services yet.

## Execution and restoration

`run_matrix(factory, cases, fresh_directory, model_urn=..., expected_node_ids=...,
workers=1)` discovers definitions and inspects the model once, then runs four owned
experiments through `ExperimentRunner`. Worker limits 1 and 4 support the planned
sequential/parallel comparison. There is no implicit initialization, import,
restart, TEST control route or forced model selection. Calling this function is
explicitly computational and must be authorized for the selected deployment.

Each result is natively exported within a 64 MiB download budget, independently
validated, opened by the existing analysis adapter, and checked for preserved
values, masks, precision and cell centers. Recorded evidence is refreshed through
the existing scoped F3 retrieval path while the worker client is open. Failed
processing remains an individual runner outcome; siblings are preserved. Default
remote retention is required so original results can be restored later.

The returned batch contains individual processed validation records and handles.
Its bounded credential-free inspection manifest is saved as `batch.json` under the
fresh directory. Output directories are never silently reused or overwritten.
Setup and complete wall time are separate; neither is published as scientific
throughput. A setup failure propagates and leaves the new directory for diagnosis.

`restore_matrix(factory, cases, encoded_batch, fresh_directory,
expected_node_ids=...)` loads a batch, checks its experiment order and retained
successes, restores each original observation handle and reruns the same validator.
It never submits replacement observations. A separate Python process must invoke
this helper before claiming fresh-process restoration; an in-process call alone
does not meet that gate. The separate process launcher/evidence wrapper is pending.

`matrix_verified` means all four runner outcomes succeeded in this invocation.
`scientific_acceptance` is deliberately false: authority/hierarchy acceptance,
fresh-process proof, permission controls, executed notebook, environment/candidate
binding and final integrated delivery are not established by this helper.

## Validation and remaining delivery

Offline tests use an actual local GeoTIFF and real raster/analysis readers plus
deterministic service replacements. They prove validator behavior, not real Java
computation. Full offline suite: 1,322 tests pass; the shared workflow has unexcluded
100% line/branch coverage and the existing lint/coverage gates pass.

Named real-Java tests, deployment/fixture manifests, notebook source/execution,
throughput adapter and fail-closed deliverable builder remain to be implemented.
See `discover-compare-explain-status.md`; do not label this increment final delivery.
