# Python bridge and NDVI verification

For the September 30 expansion and **95 positive checks**, see
[bounded scalar prototype results](PYTHON_RUNTIME_PROTOTYPE.md#validation-and-reruns).
The record below preserves the original September 28 verification.

## Verified revision and environment

Verification date: **2026-09-28**.

- Source baseline: `75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`, plus the local
  test-only bridge/NDVI contribution and storage-test mock compatibility fix described below.
  The contribution was uncommitted during verification.
- Windows 11 amd64, PowerShell 5.1.
- Eclipse Temurin JDK and javac 21.0.12.1+1; Maven wrapper 3.9.5, verified to use that JDK.
- Python 3.12.7, repository-local virtual environment, pytest 8.3.5.

## Executed checks

Exact commands are in [Combined contribution checks](PYTHON_BRIDGE.md#combined-contribution-checks).
All four commands were executed against the updated baseline. Maven used `-q`.
The initial core invocation used `clean test` in place of `test` to discard stale
compiled classes; it exposed the mock compatibility issue below. The core invocation
then passed with `test` after that one-line fixture correction. Python verification
ran independently alongside the initial core build.

| Invocation | Exit | Results |
| --- | ---: | --- |
| Independent Python suite | 0 | 47 passed: 28 bridge + 19 NDVI |
| Core bridge, NDVI and storage selection | 0 | 37 passed: 6 bridge + 3 NDVI + 28 storage |
| Core/runtime storage selection with Python unset | 0 | 34 passed: 28 core + 6 runtime |
| Both integration classes with Python unset, `missing-python` report suffix | 1 (expected) | 2 class-setup errors with interpreter configuration instructions; no skips |

**90 unique positive checks passed**, with zero failures, errors, or skips. The 28 core
storage checks ran in both successful Maven selections. The selected storage baseline
passes after updating its mock for the upstream API change.
The negative invocation failed for the expected reason in both `PythonProcessIT` and
`PythonNdviDemoIT`: `Set KLAB_PYTHON_EXECUTABLE to an actual Python 3.11/3.12 executable`.

The three NDVI checks verified expected numerical/missing values through fresh storage
readback, rejected a shifted input grid, and rejected invalid reflectance without partial
output values. The generated six-row CSV showed NDVI approximately
`0.6, 0, -0.6, null, null, 0.8` in `D2_YX` order.

Reports inspected under `klab.core.services/target/surefire-reports/`:

- `org.integratedmodelling.klab.runtime.computation.PythonProcessIT.txt` — 6 passed.
- `org.integratedmodelling.klab.runtime.computation.PythonNdviDemoIT.txt` — 3 passed.
- `org.integratedmodelling.klab.runtime.storage.StorageScanTest.txt` — 9 passed.
- `org.integratedmodelling.klab.runtime.storage.SpatialScanTest.txt` — 13 passed.
- `org.integratedmodelling.klab.runtime.storage.StorageConsumerIntegrationTest.txt` — 6 passed.
- The two computation reports with `-missing-python` suffix — expected setup errors.

Runtime report under `klab.services.runtime/target/surefire-reports/`:

- `org.integratedmodelling.klab.services.runtime.StorageConsumerExecutionTest.txt` — 6 passed.

Generated CSV: `klab.core.services/target/ndvi-demo/ndvi.csv`. Reports, virtual environments,
Python caches and generated output are excluded from the contribution.
`git diff --check` and individual whitespace checks of all new contribution files passed.
Git ignore checks confirmed that the virtual environment, Python bytecode and generated
CSV remain ignored.

## Upstream integration and fixture compatibility

Fetched `origin/develop` and fast-forwarded `python-bridge-baseline` from `dcb5c609a`
to `75bf1f7d2` (eight commits). No incoming paths overlapped local modified/untracked
paths and no merge conflicts occurred. All 37 local files had identical Git content
hashes immediately before and after the fast-forward, including the existing IDE changes.

The initial clean core run executed 37 tests with one error in
`StorageConsumerIntegrationTest.reflectedExporterUsesItsCurveAndTransfersOrReleasesSessions`:
`ComponentRegistry.serviceFinder` was null. Upstream changed `LanguageService.execute`
to call `getFunctionDescriptor(call, scope)`, while the partial-mock fixture still
stubbed `getFunctionDescriptor(call)`. Updating that stub to the scope-aware overload
restores the intended exporter fixture. Production code and assertions are unchanged.
The core selection and Python-unset core/runtime selection subsequently passed.

## Scope and limitations

- This verifies the selected local test scenario, not the full repository suite, a live
  k.Actors testcase, service deployment, or a durable observation/digital-twin commit.
- Unix commands are documented but were not executed; Python 3.11 was not tested.
- The tested branch now matches fetched `origin/develop` at
  `75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`. Later upstream changes require fresh verification.
- The checked-in `.github/workflows/build.yml` runs Maven with `-DskipTests`.
  A successful build check is not evidence that these tests ran.
- No dependency/access blocker occurred for the selected modules. The earlier baseline
  dependency-tree inspection found reactor-owned `1.0.0-SNAPSHOT` API/common/services
  dependencies. The repository enables snapshot updates; this is a locally verified,
  source-pinned build using the Maven cache, not a hermetic dependency lock.

## Contribution structure

- `klab.core.services/src/test/java/org/integratedmodelling/klab/runtime/computation/`:
  package-private subprocess helper, bridge integration tests, NDVI helper and storage tests.
- `klab.core.services/src/test/resources/python-bridge/`: standard-library Python workers,
  separate fault fixtures, independent pytest tests and pinned test requirement.
- `klab.core.services/src/test/java/org/integratedmodelling/klab/runtime/storage/StorageConsumerIntegrationTest.java`:
  one-line mock update for upstream's scope-aware function lookup.
- `docs/TESTING.md`: scenario index and placement within the repository's testing layers.
- `docs/PYTHON_BRIDGE.md`: environment setup, protocol and reproducible commands.
- `docs/PYTHON_NDVI_DEMO.md`: scientific contract, case inventory, expected output and limitations.
- `.gitignore`: local Python environment and cache exclusions.

The test-scoped placement follows the existing real-storage/mocked-infrastructure fixtures.
The `IT` suffix is an explicit opt-in mechanism, not a new repository-wide test convention.
The next integration step is a registered computation path with dependency binding, units,
scanner lifetime, cancellation and output ownership; a k.Actors acceptance scenario can
then submit the computation through the normal context workflow.
